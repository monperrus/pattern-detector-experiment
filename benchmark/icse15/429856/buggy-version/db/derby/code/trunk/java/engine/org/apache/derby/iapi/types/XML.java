/*

   Derby - Class org.apache.derby.iapi.types.XML

   Copyright 2005 The Apache Software Foundation or its licensors, as applicable.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derby.iapi.types;

import org.apache.derby.iapi.error.StandardException;

import org.apache.derby.iapi.services.cache.ClassSize;
import org.apache.derby.iapi.services.io.ArrayInputStream;
import org.apache.derby.iapi.services.io.StoredFormatIds;
import org.apache.derby.iapi.services.io.StreamStorable;
import org.apache.derby.iapi.services.io.Storable;
import org.apache.derby.iapi.services.io.TypedFormat;
import org.apache.derby.iapi.services.sanity.SanityManager;

import org.apache.derby.iapi.types.DataValueDescriptor;
import org.apache.derby.iapi.types.StringDataValue;
import org.apache.derby.iapi.types.BooleanDataValue;

import org.apache.derby.iapi.reference.SQLState;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import java.io.InputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectInput;
import java.io.StringReader;

import java.util.ArrayList;

/**
 * This type implements the XMLDataValue interface and thus is
 * the type on which all XML related operations are executed.
 *
 * The first and simplest XML store implementation is a UTF-8
 * based one--all XML data is stored on disk as a UTF-8 string,
 * just like the other Derby string types.  In order to make
 * it possible for smarter XML implementations to exist in
 * the future, this class always writes an "XML implementation
 * id" to disk before writing the rest of its data.  When
 * reading the data, the impl id is read first and serves
 * as an indicator of how the rest of the data should be
 * read.
 *
 * So long as there's only one implementation (UTF-8)
 * the impl id can be ignored; but when smarter implementations
 * are written, the impl id will be the key to figuring out
 * how an XML value should be read, written, and processed.
 */
public class XML
    extends DataType implements XMLDataValue, StreamStorable
{
    // Id for this implementation.  Should be unique
    // across all XML type implementations.
    protected static final short UTF8_IMPL_ID = 0;

    // Guess at how much memory this type will take.
    private static final int BASE_MEMORY_USAGE =
        ClassSize.estimateBaseFromCatalog(XML.class);

    // Some syntax-related constants used to determine
    // operator behavior.
    public static final short XQ_PASS_BY_REF = 1;
    public static final short XQ_PASS_BY_VALUE = 2;
    public static final short XQ_RETURN_SEQUENCE = 3;
    public static final short XQ_RETURN_CONTENT = 4;
    public static final short XQ_EMPTY_ON_EMPTY = 5;
    public static final short XQ_NULL_ON_EMPTY = 6;

    /* Per SQL/XML[2006] 4.2.2, there are several different
     * XML "types" defined through use of primary and secondary
     * "type modifiers".  For Derby we only support two kinds:
     *
     * XML(DOCUMENT(ANY)) : A valid and well-formed XML
     *  document as defined by W3C, meaning that there is
     *  exactly one root element node.  This is the only
     *  type of XML that can be stored into a Derby XML
     *  column.  This is also the type returned by a call
     *  to XMLPARSE since we require the DOCUMENT keyword.
     *
     * XML(SEQUENCE): A sequence of items (could be nodes or
     *  atomic values).  This is the type returned from an
     *  XMLQUERY operation.  Any node that is XML(DOCUMENT(ANY))
     *  is also XML(SEQUENCE).  Note that an XML(SEQUENCE)
     *  value is *only* storable into a Derby XML column
     *  if it is also an XML(DOCUMENT(ANY)).  See the
     *  normalize method below for the code that enforces
     *  this.
     */
    public static final int XML_DOC_ANY = 0;
    public static final int XML_SEQUENCE = 1;

    // The fully-qualified type for this XML value.
    private int xType;

    // The actual XML data in this implementation is just a simple
    // string, so this class really just wraps a SQLChar and
    // defers most calls to the corresponding calls on that
    // SQLChar.  Note that, even though a SQLChar is the
    // underlying implementation, an XML value is nonetheless
    // NOT considered comparable nor compatible with any of
    // Derby string types.
    private SQLChar xmlStringValue;

    /**
      Loaded at execution time, this holds XML-related objects
      that were created once during compilation but can be re-used
      for each row in the target result set for the current
      SQL statement.  In other words, we create the objects
      once per SQL statement, instead of once per row.  In the
      case of XMLEXISTS, one of the "objects" is the compiled
      query expression, which means we don't have to compile
      the expression for each row and thus we save some time.
     */
    private SqlXmlUtil sqlxUtil;

    /**
     * Default constructor.
     */
    public XML()
    {
        xmlStringValue = null;
        xType = -1;
    }

    /**
     * Private constructor used for the getClone() method.
     * Takes a SQLChar and clones it.
     * @param val A SQLChar instance to clone and use for
     *  this XML value.
     */
    private XML(SQLChar val)
    {
        xmlStringValue = (val == null ? null : (SQLChar)val.getClone());
        xType = -1;
    }

    /**
     * Private constructor used for the getClone() method.
     * Takes a SQLChar and clones it and also takes a
     * qualified XML type and stores that as this XML
     * object's qualified type.
     * @param val A SQLChar instance to clone and use for
     *  this XML value.
     * @param qualXType Qualified XML type.
     */
    private XML(SQLChar val, int xmlType)
    {
        xmlStringValue = (val == null ? null : (SQLChar)val.getClone());
        setXType(xmlType);
    }

    /* ****
     * DataValueDescriptor interface.
     * */

    /**
     * @see DataValueDescriptor#getClone
     */
    public DataValueDescriptor getClone()
    {
        return new XML(xmlStringValue, getXType());
    }

    /**
     * @see DataValueDescriptor#getNewNull
     */
    public DataValueDescriptor getNewNull()
    {
        return new XML();
    }

    /**
     * @see DataValueDescriptor#getTypeName
     */
    public String getTypeName()
    {
        return TypeId.XML_NAME;
    }

    /**
     * @see DataValueDescriptor#typePrecedence
     */
    public int typePrecedence()
    {
        return TypeId.XML_PRECEDENCE;
    }

    /**
     * @see DataValueDescriptor#getString
     */
    public String getString() throws StandardException
    {
        return (xmlStringValue == null) ? null : xmlStringValue.getString();
    }

    /**
     * @see DataValueDescriptor#getLength
     */
    public int    getLength() throws StandardException
    {
        return ((xmlStringValue == null) ? 0 : xmlStringValue.getLength());
    }

    /** 
     * @see DataValueDescriptor#estimateMemoryUsage
     */
    public int estimateMemoryUsage()
    {
        int sz = BASE_MEMORY_USAGE;
        if (xmlStringValue != null)
            sz += xmlStringValue.estimateMemoryUsage();
        return sz;
    }

    /**
     * @see DataValueDescriptor#readExternalFromArray
     */
    public void readExternalFromArray(ArrayInputStream in)
        throws IOException
    {
        if (xmlStringValue == null)
            xmlStringValue = new SQLChar();

        // Read the XML implementation id.  Right now there's
        // only one implementation (UTF-8 based), so we don't
        // use this value.  But if better implementations come
        // up in the future, we'll have to use this impl id to
        // figure out how to read the data.
        in.readShort();

        // Now just read the XML data as UTF-8.
        xmlStringValue.readExternalFromArray(in);

        // If we read it from disk then it must have type
        // XML_DOC_ANY because that's all we allow to be
        // written into an XML column.
        setXType(XML_DOC_ANY);
    }

    /**
     * @see DataValueDescriptor#setFrom
     *
     * Note: 
     */
    protected void setFrom(DataValueDescriptor theValue)
        throws StandardException
    {
        String strVal = theValue.getString();
        if (strVal == null)
        {
            xmlStringValue = null;

            // Null is a valid value for DOCUMENT(ANY)
            setXType(XML_DOC_ANY);
            return;
        }

        // Here we just store the received value locally.
        if (xmlStringValue == null)
            xmlStringValue = new SQLChar();
        xmlStringValue.setValue(strVal);

        /*
         * Assumption is that if theValue is not an XML
         * value then the caller is aware of whether or
         * not theValue constitutes a valid XML(DOCUMENT(ANY))
         * and will behave accordingly (see in particular the
         * XMLQuery method of this class, which calls the
         * setValue() method of XMLDataValue which in turn
         * brings us to this method).
         */
        if (theValue instanceof XMLDataValue)
        	setXType(((XMLDataValue)theValue).getXType());
    }

    /** 
     * @see DataValueDescriptor#setValueFromResultSet 
     */
    public final void setValueFromResultSet(
        ResultSet resultSet, int colNumber, boolean isNullable)
        throws SQLException
    {
        if (xmlStringValue == null)
            xmlStringValue = new SQLChar();
        xmlStringValue.setValue(resultSet.getString(colNumber));
    }

    /**
     * Compare two XML DataValueDescriptors.  NOTE: This method
     * should only be used by the database store for the purpose of
     * index positioning--comparisons of XML type are not allowed
     * from the language side of things.  That said, all store
     * wants to do is order the NULLs, so we don't actually
     * have to do a full comparison.  Just return an order
     * value based on whether or not this XML value and the
     * other XML value are null.  As mentioned in the "compare"
     * method of DataValueDescriptor, nulls are considered
     * equal to other nulls and less than all other values.
     *
     * An example of when this method might be used is if the
     * user executed a query like:
     *
     * select i from x_table where x_col is not null
     *
     * @see DataValueDescriptor#compare
     */
    public int compare(DataValueDescriptor other)
        throws StandardException
    {
        if (SanityManager.DEBUG) {
            SanityManager.ASSERT(other instanceof XMLDataValue,
                "Store should NOT have tried to compare an XML value " +
                "with a non-XML value.");
        }

        if (isNull()) {
            if (other.isNull())
            // both null, so call them 'equal'.
                return 0;
            // This XML is 'less than' the other.
            return -1;
        }

        if (other.isNull())
        // This XML is 'greater than' the other.
            return 1;

        // Two non-null values: we shouldn't ever get here,
        // since that would necessitate a comparsion of XML
        // values, which isn't allowed.
        if (SanityManager.DEBUG) {
            SanityManager.THROWASSERT(
                "Store tried to compare two non-null XML values, " +
                "which isn't allowed.");
        }
        return 0;
    }

    /**
     * Normalization method - this method will always be called when
     * storing an XML value into an XML column, for example, when
     * inserting/updating.  We always force normalization in this
     * case because we need to make sure the qualified type of the
     * value we're trying to store is XML_DOC_ANY--we don't allow
     * anything else.
     *
     * @param desiredType   The type to normalize the source column to
     * @param source        The value to normalize
     *
     * @exception StandardException Thrown if source is not
     *  XML_DOC_ANY.
     */
    public void normalize(
                DataTypeDescriptor desiredType,
                DataValueDescriptor source)
                    throws StandardException
    {
        if (SanityManager.DEBUG) {
            SanityManager.ASSERT(source instanceof XMLDataValue,
                "Tried to store non-XML value into XML column; " +
                "should have thrown error at compile time.");
        }

        if (((XMLDataValue)source).getXType() != XML_DOC_ANY) {
            throw StandardException.newException(
                SQLState.LANG_INVALID_XML_COLUMN_ASSIGN);
        }

        ((DataValueDescriptor) this).setValue(source);
        return;

    }

    /* ****
     * Storable interface, implies Externalizable, TypedFormat
     */

    /**
     * @see TypedFormat#getTypeFormatId
     *
     * From the engine's perspective, all XML implementations share
     * the same format id.
     */
    public int getTypeFormatId() {
        return StoredFormatIds.XML_ID;
    }

    /**
     * @see Storable#isNull
     */
    public boolean isNull()
    {
        return ((xmlStringValue == null) || xmlStringValue.isNull());
    }

    /**
     * @see Storable#restoreToNull
     */
    public void restoreToNull()
    {
        if (xmlStringValue != null)
            xmlStringValue.restoreToNull();
    }

    /**
     * Read an XML value from an input stream.
     * @param in The stream from which we're reading.
     */
    public void readExternal(ObjectInput in) throws IOException
    {
        if (xmlStringValue == null)
            xmlStringValue = new SQLChar();

        // Read the XML implementation id.  Right now there's
        // only one implementation (UTF-8 based), so we don't
        // use this value.  But if better implementations come
        // up in the future, we'll have to use this impl id to
        // figure out how to read the data.
        in.readShort();

        // Now just read the XML data as UTF-8.
        xmlStringValue.readExternal(in);

        // If we read it from disk then it must have type
        // XML_DOC_ANY because that's all we allow to be
        // written into an XML column.
        setXType(XML_DOC_ANY);
    }

    /**
     * Write an XML value. 
     * @param out The stream to which we're writing.
     */
    public void writeExternal(ObjectOutput out) throws IOException
    {
        // never called when value is null
        if (SanityManager.DEBUG)
            SanityManager.ASSERT(!isNull());

        // Write out the XML store impl id.
        out.writeShort(UTF8_IMPL_ID);

        // Now write out the data.
        xmlStringValue.writeExternal(out);
    }

    /* ****
     * StreamStorable interface
     * */

    /**
     * @see StreamStorable#returnStream
     */
    public InputStream returnStream()
    {
        return
            (xmlStringValue == null) ? null : xmlStringValue.returnStream();
    }

    /**
     * @see StreamStorable#setStream
     */
    public void setStream(InputStream newStream)
    {
        if (xmlStringValue == null)
            xmlStringValue = new SQLChar();

        // The stream that we receive is for an XML data value,
        // which means it has an XML implementation id stored
        // at the front (we put it there when we wrote it out).
        // If we leave that there we'll get a failure when
        // our underlying SQLChar tries to read from the
        // stream, because the extra impl id will throw
        // off the UTF format.  So we need to read in (and
        // ignore) the impl id before using the stream.
        try {
            // 2 bytes equal a short, which is what an impl id is.
            newStream.read();
            newStream.read();
        } catch (Exception e) {
            if (SanityManager.DEBUG)
                SanityManager.THROWASSERT("Failed to read impl id" +
                    "bytes in setStream.");
        }

        // Now go ahead and use the stream.
        xmlStringValue.setStream(newStream);

        // If we read it from disk then it must have type
        // XML_DOC_ANY because that's all we allow to be
        // written into an XML column.
        setXType(XML_DOC_ANY);
    }

    /**
     * @see StreamStorable#loadStream
     */
    public void loadStream() throws StandardException
    {
        getString();
    }

    /* ****
     * XMLDataValue interface.
     * */

    /**
     * Method to parse an XML string and, if it's valid,
     * store the _serialized_ version locally and then return
     * this XMLDataValue.
     *
     * @param text The string value to check.
     * @param preserveWS Whether or not to preserve
     *  ignorable whitespace.
     * @param sqlxUtil Contains SQL/XML objects and util
     *  methods that facilitate execution of XML-related
     *  operations
     * @return If 'text' constitutes a valid XML document,
     *  it has been stored in this XML value and this XML
     *  value is returned; otherwise, an exception is thrown. 
     * @exception StandardException Thrown on error.
     */
    public XMLDataValue XMLParse(String text, boolean preserveWS,
        SqlXmlUtil sqlxUtil) throws StandardException
    {
        try {

            if (preserveWS) {
            // Currently the only way a user can view the contents of
            // an XML value is by explicitly calling XMLSERIALIZE.
            // So do a serialization now and just store the result,
            // so that we don't have to re-serialize every time a
            // call is made to XMLSERIALIZE.
                text = sqlxUtil.serializeToString(text);
            }
            else {
            // We don't support this yet, so we shouldn't
            // get here.
                if (SanityManager.DEBUG)
                    SanityManager.THROWASSERT("Tried to STRIP whitespace " +
                        "but we shouldn't have made it this far");
            }

        } catch (Exception xe) {
        // Couldn't parse the XML document.  Throw a StandardException
        // with the parse exception nested in it.
            throw StandardException.newException(
                SQLState.LANG_NOT_AN_XML_DOCUMENT, xe);
        }

        // If we get here, the text is valid XML so go ahead
        // and load/store it.
        setXType(XML_DOC_ANY);
        if (xmlStringValue == null)
            xmlStringValue = new SQLChar();
        xmlStringValue.setValue(text);
        return this;
    }

    /**
     * The SQL/XML XMLSerialize operator.
     * Serializes this XML value into a string with a user-specified
     * character type, and returns that string via the received
     * StringDataValue (if the received StringDataValue is non-null
     * and of the correct type; else, a new StringDataValue is
     * returned).
     *
     * @param result The result of a previous call to this method,
     *    null if not called yet.
     * @param targetType The string type to which we want to serialize.
     * @param targetWidth The width of the target type.
     * @return A serialized (to string) version of this XML object,
     *  in the form of a StringDataValue object.
     * @exception StandardException    Thrown on error
     */
    public StringDataValue XMLSerialize(StringDataValue result,
        int targetType, int targetWidth) throws StandardException
    {
        if (result == null) {
            switch (targetType)
            {
                case Types.CHAR:        result = new SQLChar(); break;
                case Types.VARCHAR:     result = new SQLVarchar(); break;
                case Types.LONGVARCHAR: result = new SQLLongvarchar(); break;
                case Types.CLOB:        result = new SQLClob(); break;
                default:
                // Shouldn't ever get here, as this check was performed
                // at bind time.

                    if (SanityManager.DEBUG) {
                        SanityManager.THROWASSERT(
                            "Should NOT have made it to XMLSerialize " +
                            "with a non-string target type: " + targetType);
                    }
                    return null;
            }
        }

        // Else we're reusing a StringDataValue.  We only reuse
        // the result if we're executing the _same_ XMLSERIALIZE
        // call on multiple rows.  That means that all rows
        // must have the same result type (targetType) and thus
        // we know that the StringDataValue already has the
        // correct type.  So we're set.

        if (this.isNull()) {
        // Attempts to serialize a null XML value lead to a null
        // result (SQL/XML[2003] section 10.13).
            result.setToNull();
            return result;
        }

        // Get the XML value as a string.  For this UTF-8 impl,
        // we already have it as a UTF-8 string, so just use
        // that.
        result.setValue(getString());

        // Seems wrong to trunc an XML document, as it then becomes non-
        // well-formed and thus useless.  So we throw an error (that's
        // what the "true" in the next line says).
        result.setWidth(targetWidth, 0, true);
        return result;
    }

    /**
     * The SQL/XML XMLExists operator.
     * Checks to see if evaluation of the query expression contained
     * within the received util object against this XML value returns
     * at least one item. NOTE: For now, the query expression must be
     * XPath only (XQuery not supported) because that's what Xalan
     * supports.
     *
     * @param sqlxUtil Contains SQL/XML objects and util
     *  methods that facilitate execution of XML-related
     *  operations
     * @return True if evaluation of the query expression stored
     *  in sqlxUtil returns at least one node for this XML value;
     *  unknown if the xml value is NULL; false otherwise.
     * @exception StandardException Thrown on error
     */
    public BooleanDataValue XMLExists(SqlXmlUtil sqlxUtil)
        throws StandardException
    {
        if (this.isNull()) {
        // if the user specified a context node and that context
        // is null, result of evaluating the query is null
        // (per SQL/XML 6.17:General Rules:1.a), which means that we
        // return "unknown" here (per SQL/XML 8.4:General Rules:2.a).
            return SQLBoolean.unknownTruthValue();
        }

        // Make sure we have a compiled query (and associated XML
        // objects) to evaluate.
        if (SanityManager.DEBUG) {
            SanityManager.ASSERT(
                sqlxUtil != null,
                "Tried to evaluate XML xquery, but no XML objects were loaded.");
        }

        try {

            return new SQLBoolean(null !=
                sqlxUtil.evalXQExpression(this, false, new int[1]));

        } catch (Exception xe) {
        // We don't expect to get here.  Turn it into a StandardException
        // (if needed), then throw it.
            if (xe instanceof StandardException)
                throw (StandardException)xe;
            else {
                throw StandardException.newException(
                    SQLState.LANG_UNEXPECTED_XML_EXCEPTION, xe);
            }
        }
    }

    /**
     * Evaluate the XML query expression contained within the received
     * util object against this XML value and store the results into
     * the received XMLDataValue "result" param (assuming "result" is
     * non-null; else create a new XMLDataValue).
     *
     * @param result The result of a previous call to this method; null
     *  if not called yet.
     * @param sqlxUtil Contains SQL/XML objects and util methods that
     *  facilitate execution of XML-related operations
     * @return An XMLDataValue whose content corresponds to the serialized
     *  version of the results from evaluation of the query expression.
     *  Note: this XMLDataValue may not be storable into Derby XML
     *  columns.
     * @exception Exception thrown on error (and turned into a
     *  StandardException by the caller).
     */
    public XMLDataValue XMLQuery(XMLDataValue result,
        SqlXmlUtil sqlxUtil) throws StandardException
    {
        if (this.isNull()) {
        // if the context is null, we return null,
        // per SQL/XML[2006] 6.17:GR.1.a.ii.1.
            if (result == null)
                result = (XMLDataValue)getNewNull();
            else
                result.setToNull();
            return result;
        }

        try {
 
            // Return an XML data value whose contents are the
            // serialized version of the query results.
            int [] xType = new int[1];
            ArrayList itemRefs = sqlxUtil.evalXQExpression(
                this, true, xType);

            String strResult = sqlxUtil.serializeToString(itemRefs);
            if (result == null)
                result = new XML(new SQLChar(strResult));
            else
                result.setValue(new SQLChar(strResult));

            // Now that we've set the result value, make sure
            // to indicate what kind of XML value we have.
            result.setXType(xType[0]);

            // And finally we return the query result as an XML value.
            return result;

        } catch (StandardException se) {

            // Just re-throw it.
            throw se;

        } catch (Exception xe) {
        // We don't expect to get here.  Turn it into a
        // StandardException and throw it.

            throw StandardException.newException(
                SQLState.LANG_UNEXPECTED_XML_EXCEPTION, xe);
        }
    }

    /* ****
     * Helper classes and methods.
     * */

    /**
     * Set this XML value's qualified type.
     */
    public void setXType(int xtype)
    {
        this.xType = xtype;
    }

    /**
     * Retrieve this XML value's qualified type.
     */
    public int getXType()
    {
        return xType;
    }

}
