  + Date Author Id Revision HeadURL
  + native
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
package org.apache.solr.search;

import org.apache.lucene.index.IndexReader.AtomicReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.spell.JaroWinklerDistance;
import org.apache.lucene.search.spell.LevensteinDistance;
import org.apache.lucene.search.spell.NGramDistance;
import org.apache.lucene.search.spell.StringDistance;
import org.apache.lucene.spatial.DistanceUtils;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.UnicodeUtil;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.schema.*;
import org.apache.solr.search.function.*;

import org.apache.solr.search.function.distance.*;
import org.apache.solr.util.plugin.NamedListInitializedPlugin;

import java.io.IOException;
import java.util.*;

/**
 * A factory that parses user queries to generate ValueSource instances.
 * Intended usage is to create pluggable, named functions for use in function queries.
 */
public abstract class ValueSourceParser implements NamedListInitializedPlugin {
  /**
   * Initialize the plugin.
   */
  public void init(NamedList args) {}

  /**
   * Parse the user input into a ValueSource.
   *
   * @param fp
   * @throws ParseException
   */
  public abstract ValueSource parse(FunctionQParser fp) throws ParseException;

  /* standard functions */
  public static Map<String, ValueSourceParser> standardValueSourceParsers = new HashMap<String, ValueSourceParser>();

  /** Adds a new parser for the name and returns any existing one that was overriden.
   *  This is not thread safe.
   */
  public static ValueSourceParser addParser(String name, ValueSourceParser p) {
    return standardValueSourceParsers.put(name, p);
  }

  /** Adds a new parser for the name and returns any existing one that was overriden.
   *  This is not thread safe.
   */
  public static ValueSourceParser addParser(NamedParser p) {
    return standardValueSourceParsers.put(p.name(), p);
  }

  private static void alias(String source, String dest) {
    standardValueSourceParsers.put(dest, standardValueSourceParsers.get(source));
  }

  static {
    addParser("ord", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        String field = fp.parseId();
        return new OrdFieldSource(field);
      }
    });
    addParser("literal", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        return new LiteralValueSource(fp.getString());
      }
    });
    addParser("rord", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        String field = fp.parseId();
        return new ReverseOrdFieldSource(field);
      }
    });
    addParser("top", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        // top(vs) is now a no-op
        ValueSource source = fp.parseValueSource();
        return source;
      }
    });
    addParser("linear", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        ValueSource source = fp.parseValueSource();
        float slope = fp.parseFloat();
        float intercept = fp.parseFloat();
        return new LinearFloatFunction(source, slope, intercept);
      }
    });
    addParser("recip", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        ValueSource source = fp.parseValueSource();
        float m = fp.parseFloat();
        float a = fp.parseFloat();
        float b = fp.parseFloat();
        return new ReciprocalFloatFunction(source, m, a, b);
      }
    });
    addParser("scale", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        ValueSource source = fp.parseValueSource();
        float min = fp.parseFloat();
        float max = fp.parseFloat();
        return new ScaleFloatFunction(source, min, max);
      }
    });
    addParser("div", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        ValueSource a = fp.parseValueSource();
        ValueSource b = fp.parseValueSource();
        return new DivFloatFunction(a, b);
      }
    });
    addParser("map", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        ValueSource source = fp.parseValueSource();
        float min = fp.parseFloat();
        float max = fp.parseFloat();
        float target = fp.parseFloat();
        Float def = fp.hasMoreArguments() ? fp.parseFloat() : null;
        return new RangeMapFloatFunction(source, min, max, target, def);
      }
    });

    addParser("abs", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        ValueSource source = fp.parseValueSource();
        return new SimpleFloatFunction(source) {
          @Override
          protected String name() {
            return "abs";
          }

          @Override
          protected float func(int doc, DocValues vals) {
            return Math.abs(vals.floatVal(doc));
          }
        };
      }
    });
    addParser("sum", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        List<ValueSource> sources = fp.parseValueSourceList();
        return new SumFloatFunction(sources.toArray(new ValueSource[sources.size()]));
      }
    });
    alias("sum","add");    

    addParser("product", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        List<ValueSource> sources = fp.parseValueSourceList();
        return new ProductFloatFunction(sources.toArray(new ValueSource[sources.size()]));
      }
    });
    alias("product","mul");

    addParser("sub", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        ValueSource a = fp.parseValueSource();
        ValueSource b = fp.parseValueSource();
        return new DualFloatFunction(a, b) {
          @Override
          protected String name() {
            return "sub";
          }

          @Override
          protected float func(int doc, DocValues aVals, DocValues bVals) {
            return aVals.floatVal(doc) - bVals.floatVal(doc);
          }
        };
      }
    });
    addParser("vector", new ValueSourceParser(){
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException{
        return new VectorValueSource(fp.parseValueSourceList());
      }
    });
    addParser("query", new ValueSourceParser() {
      // boost(query($q),rating)
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        Query q = fp.parseNestedQuery();
        float defVal = 0.0f;
        if (fp.hasMoreArguments()) {
          defVal = fp.parseFloat();
        }
        return new QueryValueSource(q, defVal);
      }
    });
    addParser("boost", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        Query q = fp.parseNestedQuery();
        ValueSource vs = fp.parseValueSource();
        BoostedQuery bq = new BoostedQuery(q, vs);
        return new QueryValueSource(bq, 0.0f);
      }
    });
    addParser("joindf", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        String f0 = fp.parseArg();
        String qf = fp.parseArg();
        return new JoinDocFreqValueSource( f0, qf );
      }
    });

    addParser("geodist", HaversineConstFunction.parser);

    addParser("hsin", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {

        double radius = fp.parseDouble();
        //SOLR-2114, make the convert flag required, since the parser doesn't support much in the way of lookahead or the ability to convert a String into a ValueSource
        boolean convert = Boolean.parseBoolean(fp.parseArg());
        
        MultiValueSource pv1;
        MultiValueSource pv2;

        ValueSource one = fp.parseValueSource();
        ValueSource two = fp.parseValueSource();
        if (fp.hasMoreArguments()) {


          List<ValueSource> s1 = new ArrayList<ValueSource>();
          s1.add(one);
          s1.add(two);
          pv1 = new VectorValueSource(s1);
          ValueSource x2 = fp.parseValueSource();
          ValueSource y2 = fp.parseValueSource();
          List<ValueSource> s2 = new ArrayList<ValueSource>();
          s2.add(x2);
          s2.add(y2);
          pv2 = new VectorValueSource(s2);
        } else {
          //check to see if we have multiValue source
          if (one instanceof MultiValueSource && two instanceof MultiValueSource){
            pv1 = (MultiValueSource) one;
            pv2 = (MultiValueSource) two;
          } else {
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                    "Input must either be 2 MultiValueSources, or there must be 4 ValueSources");
          }
        }

        return new HaversineFunction(pv1, pv2, radius, convert);
      }
    });

    addParser("ghhsin", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        double radius = fp.parseDouble();

        ValueSource gh1 = fp.parseValueSource();
        ValueSource gh2 = fp.parseValueSource();

        return new GeohashHaversineFunction(gh1, gh2, radius);
      }
    });

    addParser("geohash", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {

        ValueSource lat = fp.parseValueSource();
        ValueSource lon = fp.parseValueSource();

        return new GeohashFunction(lat, lon);
      }
    });
    addParser("strdist", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {

        ValueSource str1 = fp.parseValueSource();
        ValueSource str2 = fp.parseValueSource();
        String distClass = fp.parseArg();

        StringDistance dist = null;
        if (distClass.equalsIgnoreCase("jw")) {
          dist = new JaroWinklerDistance();
        } else if (distClass.equalsIgnoreCase("edit")) {
          dist = new LevensteinDistance();
        } else if (distClass.equalsIgnoreCase("ngram")) {
          int ngram = 2;
          if (fp.hasMoreArguments()) {
            ngram = fp.parseInt();
          }
          dist = new NGramDistance(ngram);
        } else {
          dist = (StringDistance) fp.req.getCore().getResourceLoader().newInstance(distClass);
        }
        return new StringDistanceFunction(str1, str2, dist);
      }
    });
    addParser("field", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {

        String fieldName = fp.parseArg();
        SchemaField f = fp.getReq().getSchema().getField(fieldName);
        return f.getType().getValueSource(f, fp);
      }
    });

    addParser(new DoubleParser("rad") {
      @Override
      public double func(int doc, DocValues vals) {
        return vals.doubleVal(doc) * DistanceUtils.DEGREES_TO_RADIANS;
      }
    });
    addParser(new DoubleParser("deg") {
      @Override
      public double func(int doc, DocValues vals) {
        return vals.doubleVal(doc) * DistanceUtils.RADIANS_TO_DEGREES;
      }
    });
    addParser(new DoubleParser("sqrt") {
      @Override
      public double func(int doc, DocValues vals) {
        return Math.sqrt(vals.doubleVal(doc));
      }
    });
    addParser(new DoubleParser("cbrt") {
      @Override
      public double func(int doc, DocValues vals) {
        return Math.cbrt(vals.doubleVal(doc));
      }
    });
    addParser(new DoubleParser("log") {
      @Override
      public double func(int doc, DocValues vals) {
        return Math.log10(vals.doubleVal(doc));
      }
    });
    addParser(new DoubleParser("ln") {
      @Override
      public double func(int doc, DocValues vals) {
        return Math.log(vals.doubleVal(doc));
      }
    });
    addParser(new DoubleParser("exp") {
      @Override
      public double func(int doc, DocValues vals) {
        return Math.exp(vals.doubleVal(doc));
      }
    });
    addParser(new DoubleParser("sin") {
      @Override
      public double func(int doc, DocValues vals) {
        return Math.sin(vals.doubleVal(doc));
      }
    });
    addParser(new DoubleParser("cos") {
      @Override
      public double func(int doc, DocValues vals) {
        return Math.cos(vals.doubleVal(doc));
      }
    });
    addParser(new DoubleParser("tan") {
      @Override
      public double func(int doc, DocValues vals) {
        return Math.tan(vals.doubleVal(doc));
      }
    });
    addParser(new DoubleParser("asin") {
      @Override
      public double func(int doc, DocValues vals) {
        return Math.asin(vals.doubleVal(doc));
      }
    });
    addParser(new DoubleParser("acos") {
      @Override
      public double func(int doc, DocValues vals) {
        return Math.acos(vals.doubleVal(doc));
      }
    });
    addParser(new DoubleParser("atan") {
      @Override
      public double func(int doc, DocValues vals) {
        return Math.atan(vals.doubleVal(doc));
      }
    });
    addParser(new DoubleParser("sinh") {
      @Override
      public double func(int doc, DocValues vals) {
        return Math.sinh(vals.doubleVal(doc));
      }
    });
    addParser(new DoubleParser("cosh") {
      @Override
      public double func(int doc, DocValues vals) {
        return Math.cosh(vals.doubleVal(doc));
      }
    });
    addParser(new DoubleParser("tanh") {
      @Override
      public double func(int doc, DocValues vals) {
        return Math.tanh(vals.doubleVal(doc));
      }
    });
    addParser(new DoubleParser("ceil") {
      @Override
      public double func(int doc, DocValues vals) {
        return Math.ceil(vals.doubleVal(doc));
      }
    });
    addParser(new DoubleParser("floor") {
      @Override
      public double func(int doc, DocValues vals) {
        return Math.floor(vals.doubleVal(doc));
      }
    });
    addParser(new DoubleParser("rint") {
      @Override
      public double func(int doc, DocValues vals) {
        return Math.rint(vals.doubleVal(doc));
      }
    });
    addParser(new Double2Parser("pow") {
      @Override
      public double func(int doc, DocValues a, DocValues b) {
        return Math.pow(a.doubleVal(doc), b.doubleVal(doc));
      }
    });
    addParser(new Double2Parser("hypot") {
      @Override
      public double func(int doc, DocValues a, DocValues b) {
        return Math.hypot(a.doubleVal(doc), b.doubleVal(doc));
      }
    });
    addParser(new Double2Parser("atan2") {
      @Override
      public double func(int doc, DocValues a, DocValues b) {
        return Math.atan2(a.doubleVal(doc), b.doubleVal(doc));
      }
    });
    addParser(new Double2Parser("max") {
      @Override
      public double func(int doc, DocValues a, DocValues b) {
        return Math.max(a.doubleVal(doc), b.doubleVal(doc));
      }
    });
    addParser(new Double2Parser("min") {
      @Override
      public double func(int doc, DocValues a, DocValues b) {
        return Math.min(a.doubleVal(doc), b.doubleVal(doc));
      }
    });

    addParser("sqedist", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        List<ValueSource> sources = fp.parseValueSourceList();
        MVResult mvr = getMultiValueSources(sources);

        return new SquaredEuclideanFunction(mvr.mv1, mvr.mv2);
      }
    });

    addParser("dist", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        float power = fp.parseFloat();
        List<ValueSource> sources = fp.parseValueSourceList();
        MVResult mvr = getMultiValueSources(sources);
        return new VectorDistanceFunction(power, mvr.mv1, mvr.mv2);
      }
    });
    addParser("ms", new DateValueSourceParser());

    
    addParser("pi", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        return new DoubleConstValueSource(Math.PI);
      }
    });
    addParser("e", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        return new DoubleConstValueSource(Math.E);
      }
    });


    addParser("docfreq", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        TInfo tinfo = parseTerm(fp);
        return new DocFreqValueSource(tinfo.field, tinfo.val, tinfo.indexedField, tinfo.indexedBytes);
      }
    });

    addParser("idf", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        TInfo tinfo = parseTerm(fp);
        return new IDFValueSource(tinfo.field, tinfo.val, tinfo.indexedField, tinfo.indexedBytes);
      }
    });

    addParser("termfreq", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        TInfo tinfo = parseTerm(fp);
        return new TermFreqValueSource(tinfo.field, tinfo.val, tinfo.indexedField, tinfo.indexedBytes);
      }
    });

    addParser("tf", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        TInfo tinfo = parseTerm(fp);
        return new TFValueSource(tinfo.field, tinfo.val, tinfo.indexedField, tinfo.indexedBytes);
      }
    });

    addParser("norm", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        String field = fp.parseArg();
        return new NormValueSource(field);
      }
    });

    addParser("maxdoc", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        return new MaxDocValueSource();
      }
    });

    addParser("numdocs", new ValueSourceParser() {
      @Override
      public ValueSource parse(FunctionQParser fp) throws ParseException {
        return new NumDocsValueSource();
      }
    });
  }

  private static TInfo parseTerm(FunctionQParser fp) throws ParseException {
    TInfo tinfo = new TInfo();

    tinfo.indexedField = tinfo.field = fp.parseArg();
    tinfo.val = fp.parseArg();
    tinfo.indexedBytes = new BytesRef();

    FieldType ft = fp.getReq().getSchema().getFieldTypeNoEx(tinfo.field);
    if (ft == null) ft = new StrField();

    if (ft instanceof TextField) {
      // need to do analysis on the term
      String indexedVal = tinfo.val;
      Query q = ft.getFieldQuery(fp, fp.getReq().getSchema().getFieldOrNull(tinfo.field), tinfo.val);
      if (q instanceof TermQuery) {
        Term term = ((TermQuery)q).getTerm();
        tinfo.indexedField = term.field();
        indexedVal = term.text();
      }
      UnicodeUtil.UTF16toUTF8(indexedVal, 0, indexedVal.length(), tinfo.indexedBytes);
    } else {
      ft.readableToIndexed(tinfo.val, tinfo.indexedBytes);
    }

    return tinfo;
  }

  private static void splitSources(int dim, List<ValueSource> sources, List<ValueSource> dest1, List<ValueSource> dest2) {
    //Get dim value sources for the first vector
    for (int i = 0; i < dim; i++) {
      dest1.add(sources.get(i));
    }
    //Get dim value sources for the second vector
    for (int i = dim; i < sources.size(); i++) {
      dest2.add(sources.get(i));
    }
  }

  private static MVResult getMultiValueSources(List<ValueSource> sources) {
    MVResult mvr = new MVResult();
    if (sources.size() % 2 != 0) {
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Illegal number of sources.  There must be an even number of sources");
    }
    if (sources.size() == 2) {

      //check to see if these are MultiValueSource
      boolean s1MV = sources.get(0) instanceof MultiValueSource;
      boolean s2MV = sources.get(1) instanceof MultiValueSource;
      if (s1MV && s2MV) {
        mvr.mv1 = (MultiValueSource) sources.get(0);
        mvr.mv2 = (MultiValueSource) sources.get(1);
      } else if (s1MV ||
              s2MV) {
        //if one is a MultiValueSource, than the other one needs to be too.
        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Illegal number of sources.  There must be an even number of sources");
      } else {
        mvr.mv1 = new VectorValueSource(Collections.singletonList(sources.get(0)));
        mvr.mv2 = new VectorValueSource(Collections.singletonList(sources.get(1)));
      }
    } else {
      int dim = sources.size() / 2;
      List<ValueSource> sources1 = new ArrayList<ValueSource>(dim);
      List<ValueSource> sources2 = new ArrayList<ValueSource>(dim);
      //Get dim value sources for the first vector
      splitSources(dim, sources, sources1, sources2);
      mvr.mv1 = new VectorValueSource(sources1);
      mvr.mv2 = new VectorValueSource(sources2);
    }

    return mvr;
  }

  private static class MVResult {
    MultiValueSource mv1;
    MultiValueSource mv2;
  }

  private static class TInfo {
    String field;
    String val;
    String indexedField;
    BytesRef indexedBytes;
  }

}


class DateValueSourceParser extends ValueSourceParser {
  DateField df = new TrieDateField();

  @Override
  public void init(NamedList args) {
  }

  public Date getDate(FunctionQParser fp, String arg) {
    if (arg == null) return null;
    if (arg.startsWith("NOW") || (arg.length() > 0 && Character.isDigit(arg.charAt(0)))) {
      return df.parseMathLenient(null, arg, fp.req);
    }
    return null;
  }

  public ValueSource getValueSource(FunctionQParser fp, String arg) {
    if (arg == null) return null;
    SchemaField f = fp.req.getSchema().getField(arg);
    if (f.getType().getClass() == DateField.class) {
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Can't use ms() function on non-numeric legacy date field " + arg);
    }
    return f.getType().getValueSource(f, fp);
  }

  @Override
  public ValueSource parse(FunctionQParser fp) throws ParseException {
    String first = fp.parseArg();
    String second = fp.parseArg();
    if (first == null) first = "NOW";

    Date d1 = getDate(fp, first);
    ValueSource v1 = d1 == null ? getValueSource(fp, first) : null;

    Date d2 = getDate(fp, second);
    ValueSource v2 = d2 == null ? getValueSource(fp, second) : null;

    // d     constant
    // v     field
    // dd    constant
    // dv    subtract field from constant
    // vd    subtract constant from field
    // vv    subtract fields

    final long ms1 = (d1 == null) ? 0 : d1.getTime();
    final long ms2 = (d2 == null) ? 0 : d2.getTime();

    // "d,dd" handle both constant cases

    if (d1 != null && v2 == null) {
      return new LongConstValueSource(ms1 - ms2);
    }

    // "v" just the date field
    if (v1 != null && v2 == null && d2 == null) {
      return v1;
    }


    // "dv"
    if (d1 != null && v2 != null)
      return new DualFloatFunction(new LongConstValueSource(ms1), v2) {
        @Override
        protected String name() {
          return "ms";
        }

        @Override
        protected float func(int doc, DocValues aVals, DocValues bVals) {
          return ms1 - bVals.longVal(doc);
        }
      };

    // "vd"
    if (v1 != null && d2 != null)
      return new DualFloatFunction(v1, new LongConstValueSource(ms2)) {
        @Override
        protected String name() {
          return "ms";
        }

        @Override
        protected float func(int doc, DocValues aVals, DocValues bVals) {
          return aVals.longVal(doc) - ms2;
        }
      };

    // "vv"
    if (v1 != null && v2 != null)
      return new DualFloatFunction(v1, v2) {
        @Override
        protected String name() {
          return "ms";
        }

        @Override
        protected float func(int doc, DocValues aVals, DocValues bVals) {
          return aVals.longVal(doc) - bVals.longVal(doc);
        }
      };

    return null; // shouldn't happen
  }

}


// Private for now - we need to revisit how to handle typing in function queries
class LongConstValueSource extends ConstNumberSource {
  final long constant;
  final double dv;
  final float fv;

  public LongConstValueSource(long constant) {
    this.constant = constant;
    this.dv = constant;
    this.fv = constant;
  }

  @Override
  public String description() {
    return "const(" + constant + ")";
  }

  @Override
  public DocValues getValues(Map context, AtomicReaderContext readerContext) throws IOException {
    return new LongDocValues(this) {
      @Override
      public float floatVal(int doc) {
        return fv;
      }

      @Override
      public int intVal(int doc) {
        return (int) constant;
      }

      @Override
      public long longVal(int doc) {
        return constant;
      }

      @Override
      public double doubleVal(int doc) {
        return dv;
      }

      @Override
      public String toString(int doc) {
        return description();
      }
    };
  }

  @Override
  public int hashCode() {
    return (int) constant + (int) (constant >>> 32);
  }

  @Override
  public boolean equals(Object o) {
    if (LongConstValueSource.class != o.getClass()) return false;
    LongConstValueSource other = (LongConstValueSource) o;
    return this.constant == other.constant;
  }

  @Override
  public int getInt() {
    return (int)constant;
  }

  @Override
  public long getLong() {
    return constant;
  }

  @Override
  public float getFloat() {
    return fv;
  }

  @Override
  public double getDouble() {
    return dv;
  }

  @Override
  public Number getNumber() {
    return constant;
  }
}


abstract class NamedParser extends ValueSourceParser {
  private final String name;
  public NamedParser(String name) {
    this.name = name;
  }
  public String name() {
    return name;
  }
}


abstract class DoubleParser extends NamedParser {
  public DoubleParser(String name) {
    super(name);
  }

  public abstract double func(int doc, DocValues vals);

  @Override
  public ValueSource parse(FunctionQParser fp) throws ParseException {
    return new Function(fp.parseValueSource());
  }

  class Function extends SingleFunction {
    public Function(ValueSource source) {
      super(source);
    }

    @Override
    public String name() {
      return DoubleParser.this.name();
    }

    @Override
    public DocValues getValues(Map context, AtomicReaderContext readerContext) throws IOException {
      final DocValues vals =  source.getValues(context, readerContext);
      return new DoubleDocValues(this) {
        @Override
        public double doubleVal(int doc) {
          return func(doc, vals);
        }
        @Override
        public String toString(int doc) {
          return name() + '(' + vals.toString(doc) + ')';
        }
      };
    }
  }
}


abstract class Double2Parser extends NamedParser {
  public Double2Parser(String name) {
    super(name);
  }

  public abstract double func(int doc, DocValues a, DocValues b);

  @Override
  public ValueSource parse(FunctionQParser fp) throws ParseException {
    return new Function(fp.parseValueSource(), fp.parseValueSource());
  }

  class Function extends ValueSource {
    private final ValueSource a;
    private final ValueSource b;

   /**
     * @param   a  the base.
     * @param   b  the exponent.
     */
    public Function(ValueSource a, ValueSource b) {
      this.a = a;
      this.b = b;
    }

    @Override
    public String description() {
      return name() + "(" + a.description() + "," + b.description() + ")";
    }

    @Override
    public DocValues getValues(Map context, AtomicReaderContext readerContext) throws IOException {
      final DocValues aVals =  a.getValues(context, readerContext);
      final DocValues bVals =  b.getValues(context, readerContext);
      return new DoubleDocValues(this) {
         @Override
        public double doubleVal(int doc) {
          return func(doc, aVals, bVals);
        }
        @Override
        public String toString(int doc) {
          return name() + '(' + aVals.toString(doc) + ',' + bVals.toString(doc) + ')';
        }
      };
    }

    @Override
    public void createWeight(Map context, IndexSearcher searcher) throws IOException {
    }

    @Override
    public int hashCode() {
      int h = a.hashCode();
      h ^= (h << 13) | (h >>> 20);
      h += b.hashCode();
      h ^= (h << 23) | (h >>> 10);
      h += name().hashCode();
      return h;
    }

    @Override
    public boolean equals(Object o) {
      if (this.getClass() != o.getClass()) return false;
      Function other = (Function)o;
      return this.a.equals(other.a)
          && this.b.equals(other.b);
    }
  }

}
