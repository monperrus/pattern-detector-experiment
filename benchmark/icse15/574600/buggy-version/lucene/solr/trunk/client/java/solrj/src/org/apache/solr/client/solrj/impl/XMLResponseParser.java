/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.client.solrj.impl;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.solr.client.solrj.ResponseParser;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.util.NamedList;

/**
 * 
 * @version $Id$
 * @since solr 1.3
 */
public class XMLResponseParser implements ResponseParser
{
  XMLInputFactory factory;
  
  public XMLResponseParser()
  {
    factory = XMLInputFactory.newInstance();
  }
  
  public String getWriterType()
  {
    return "xml";
  }

  /**
   * parse the text into a named list...
   */
  public NamedList<Object> processResponse( Reader in )
  {
    XMLStreamReader parser = null;
    try { 
//      String txt = IOUtils.toString( in );
//      in = new StringReader( txt );
//      System.out.println( "TEXT:"+txt );
      
      parser = factory.createXMLStreamReader(in);
      
      NamedList<Object> response = null;
      for (int event = parser.next();  
       event != XMLStreamConstants.END_DOCUMENT;
       event = parser.next()) 
      {
        switch (event) {
          case XMLStreamConstants.START_ELEMENT:

            if( response != null ) {
              throw new Exception( "already read the response!" );
            }
            
            // only top-level element is "response
            String name = parser.getLocalName();
            if( name.equals( "response" ) || name.equals( "result" ) ) {
              response = readNamedList( parser );
            }
            else {
              throw new Exception( "really needs to be response or result.  " +
                  "not:"+parser.getLocalName() );
            }
            break;
        } 
      } 
      return response;
    }
    catch( Exception ex ) {
      throw new SolrException( SolrException.ErrorCode.SERVER_ERROR, "parsing error", ex );
    }
    finally {
      try {
        parser.close();
      }
      catch( Exception ex ){}
    }
  }

  
  protected enum KnownType {
    STR    (true)  { @Override public String  read( String txt ) { return txt;                  } },
    INT    (true)  { @Override public Integer read( String txt ) { return Integer.valueOf(txt); } },
    FLOAT  (true)  { @Override public Float   read( String txt ) { return Float.valueOf(txt);   } },
    DOUBLE (true)  { @Override public Double  read( String txt ) { return Double.valueOf(txt);  } },
    LONG   (true)  { @Override public Long    read( String txt ) { return Long.valueOf(txt);    } },
    BOOL   (true)  { @Override public Boolean read( String txt ) { return Boolean.valueOf(txt); } },
    NULL   (true)  { @Override public Object  read( String txt ) { return null;                 } },
    DATE   (true)  { 
      @Override 
      public Date read( String txt ) { 
        try {
          return ClientUtils.parseDate(txt);      
        }
        catch( Exception ex ) {
          ex.printStackTrace();
        }
        return null;
      } 
    },
    
    ARR    (false) { @Override public Object read( String txt ) { return null; } },
    LST    (false) { @Override public Object read( String txt ) { return null; } },
    RESULT (false) { @Override public Object read( String txt ) { return null; } },
    DOC    (false) { @Override public Object read( String txt ) { return null; } };
    
    final boolean isLeaf;
    
    KnownType( boolean isLeaf )
    {
      this.isLeaf = isLeaf;
    }
    
    public abstract Object read( String txt );
    
    public static KnownType get( String v )
    {
      if( v != null ) {
        try {
          return KnownType.valueOf( v.toUpperCase() );
        }
        catch( Exception ex ) {}
      }
      return null;
    }
  };
  
  protected NamedList<Object> readNamedList( XMLStreamReader parser ) throws XMLStreamException
  {
    if( XMLStreamConstants.START_ELEMENT != parser.getEventType() ) {
      throw new RuntimeException( "must be start element, not: "+parser.getEventType() );
    }

    StringBuilder builder = new StringBuilder();
    NamedList<Object> nl = new NamedList<Object>();
    KnownType type = null;
    String name = null;
    
    // just eat up the events...
    int depth = 0;
    while( true ) 
    {
      switch (parser.next()) {
      case XMLStreamConstants.START_ELEMENT:
        depth++;
        builder.setLength( 0 ); // reset the text
        type = KnownType.get( parser.getLocalName() );
        if( type == null ) {
          throw new RuntimeException( "this must be known type! not: "+parser.getLocalName() );
        }
        
        name = null;
        int cnt = parser.getAttributeCount();
        for( int i=0; i<cnt; i++ ) {
          if( "name".equals( parser.getAttributeLocalName( i ) ) ) {
            name = parser.getAttributeValue( i );
            break;
          }
        }
        
        if( name == null ) {
          throw new XMLStreamException( "requires 'name' attribute: "+parser.getLocalName(), parser.getLocation() );
        }
        
        if( !type.isLeaf ) {
          switch( type ) {
          case LST:    nl.add( name, readNamedList( parser ) ); depth--; continue;
          case ARR:    nl.add( name, readArray(     parser ) ); depth--; continue;
          case RESULT: nl.add( name, readDocuments( parser ) ); depth--; continue;
          case DOC:    nl.add( name, readDocument(  parser ) ); depth--; continue;
          }
          throw new XMLStreamException( "branch element not handled!", parser.getLocation() );
        }
        break;
        
      case XMLStreamConstants.END_ELEMENT:
        if( --depth < 0 ) {
          return nl;
        }
        //System.out.println( "NL:ELEM:"+type+"::"+name+"::"+builder );
        nl.add( name, type.read( builder.toString().trim() ) );
        break;

      case XMLStreamConstants.SPACE: // TODO?  should this be trimmed? make sure it only gets one/two space?
      case XMLStreamConstants.CDATA:
      case XMLStreamConstants.CHARACTERS:
        builder.append( parser.getText() );
        break;
      }
    }
  }

  protected List<Object> readArray( XMLStreamReader parser ) throws XMLStreamException
  {
    if( XMLStreamConstants.START_ELEMENT != parser.getEventType() ) {
      throw new RuntimeException( "must be start element, not: "+parser.getEventType() );
    }
    if( !"arr".equals( parser.getLocalName().toLowerCase() ) ) {
      throw new RuntimeException( "must be 'arr', not: "+parser.getLocalName() );
    }
    
    StringBuilder builder = new StringBuilder();
    KnownType type = null;

    List<Object> vals = new ArrayList<Object>();

    int depth = 0;
    while( true ) 
    {
      switch (parser.next()) {
      case XMLStreamConstants.START_ELEMENT:
        depth++;
        KnownType t = KnownType.get( parser.getLocalName() );
        if( t == null ) {
          throw new RuntimeException( "this must be known type! not: "+parser.getLocalName() );
        }
        if( type == null ) {
          type = t;
        }
        else if( type != t ) {
          throw new RuntimeException( "arrays must have the same type! ("+type+"!="+t+") "+parser.getLocalName() );
        }

        builder.setLength( 0 ); // reset the text
        
        if( !type.isLeaf ) {
          switch( type ) {
          case LST:    vals.add( readNamedList( parser ) ); continue;
          case ARR:    vals.add( readArray( parser ) ); continue;
          case RESULT: vals.add( readDocuments( parser ) ); continue;
          case DOC:    vals.add( readDocument( parser ) ); continue;
          }
          throw new XMLStreamException( "branch element not handled!", parser.getLocation() );
        }
        break;
        
      case XMLStreamConstants.END_ELEMENT:
        if( --depth < 0 ) {
          return vals; // the last element is itself
        }
        //System.out.println( "ARR:"+type+"::"+builder );
        Object val = type.read( builder.toString().trim() );
        if( val == null ) {
          throw new XMLStreamException( "error reading value:"+type, parser.getLocation() );
        }
        vals.add( val );
        break;

      case XMLStreamConstants.SPACE: // TODO?  should this be trimmed? make sure it only gets one/two space?
      case XMLStreamConstants.CDATA:
      case XMLStreamConstants.CHARACTERS:
        builder.append( parser.getText() );
        break;
    }
    }
  }
  
  protected SolrDocumentList readDocuments( XMLStreamReader parser ) throws XMLStreamException
  {
    SolrDocumentList docs = new SolrDocumentList();

    // Parse the attributes
    for( int i=0; i<parser.getAttributeCount(); i++ ) {
      String n = parser.getAttributeLocalName( i );
      String v = parser.getAttributeValue( i );
      if( "numFound".equals( n ) ) {
        docs.setNumFound( Integer.parseInt( v ) );
      }
      else if( "start".equals( n ) ) {
        docs.setStart( Integer.parseInt( v ) );
      }
      else if( "maxScore".equals( n ) ) {
        docs.setMaxScore( Float.parseFloat( v ) );
      }
    }
    
    // Read through each document
    int event;
    while( true ) {
      event = parser.next();
      if( XMLStreamConstants.START_ELEMENT == event ) {
        if( !"doc".equals( parser.getLocalName() ) ) {
          throw new RuntimeException( "shoudl be doc! "+parser.getLocalName() + " :: " + parser.getLocation() );
        }
        docs.add( readDocument( parser ) );
      }
      else if ( XMLStreamConstants.END_ELEMENT == event ) {
        return docs;  // only happens once
      }
    }
  }

  protected SolrDocument readDocument( XMLStreamReader parser ) throws XMLStreamException
  {
    if( XMLStreamConstants.START_ELEMENT != parser.getEventType() ) {
      throw new RuntimeException( "must be start element, not: "+parser.getEventType() );
    }
    if( !"doc".equals( parser.getLocalName().toLowerCase() ) ) {
      throw new RuntimeException( "must be 'lst', not: "+parser.getLocalName() );
    }

    SolrDocument doc = new SolrDocument();
    StringBuilder builder = new StringBuilder();
    KnownType type = null;
    String name = null;
    
    // just eat up the events...
    int depth = 0;
    while( true ) 
    {
      switch (parser.next()) {
      case XMLStreamConstants.START_ELEMENT:
        depth++;
        builder.setLength( 0 ); // reset the text
        type = KnownType.get( parser.getLocalName() );
        if( type == null ) {
          throw new RuntimeException( "this must be known type! not: "+parser.getLocalName() );
        }
        
        name = null;
        int cnt = parser.getAttributeCount();
        for( int i=0; i<cnt; i++ ) {
          if( "name".equals( parser.getAttributeLocalName( i ) ) ) {
            name = parser.getAttributeValue( i );
            break;
          }
        }
        
        if( name == null ) {
          throw new XMLStreamException( "requires 'name' attribute: "+parser.getLocalName(), parser.getLocation() );
        }
        
        // Handle multi-valued fields
        if( type == KnownType.ARR ) {
          for( Object val : readArray( parser ) ) {
            doc.addField( name, val );
          }
          depth--; // the array reading clears out the 'endElement'
        }
        else if( !type.isLeaf ) {
          throw new XMLStreamException( "must be value or array", parser.getLocation() );
        }
        break;
        
      case XMLStreamConstants.END_ELEMENT:
        if( --depth < 0 ) {
          return doc;
        }
        //System.out.println( "FIELD:"+type+"::"+name+"::"+builder );
        Object val = type.read( builder.toString().trim() );
        if( val == null ) {
          throw new XMLStreamException( "error reading value:"+type, parser.getLocation() );
        }
        doc.addField( name, val );
        break;

      case XMLStreamConstants.SPACE: // TODO?  should this be trimmed? make sure it only gets one/two space?
      case XMLStreamConstants.CDATA:
      case XMLStreamConstants.CHARACTERS:
        builder.append( parser.getText() );
        break;
      }
    }
  }

  
}
