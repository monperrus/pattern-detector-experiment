package org.apache.solr.request;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Fieldable;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.schema.TextField;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.util.NamedList;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * @author yonik
 * @version $Id$
 */

public class JSONResponseWriter implements QueryResponseWriter {
  static String CONTENT_TYPE_JSON_UTF8="text/x-json;charset=UTF-8";


  public void write(Writer writer, SolrQueryRequest req, SolrQueryResponse rsp) throws IOException {
    JSONWriter w = new JSONWriter(writer, req, rsp);
    w.writeResponse();
  }

  public String getContentType(SolrQueryRequest request, SolrQueryResponse response) {
    // using the text/plain allows this to be viewed in the browser easily
    return CONTENT_TYPE_TEXT_UTF8;
  }
}


class JSONWriter extends TextResponseWriter {

  // cache the calendar instance in case we are writing many dates...
  private Calendar cal;

  private String namedListStyle;

  private static final String JSON_NL_STYLE="json.nl";
  private static final String JSON_NL_MAP="map";
  private static final String JSON_NL_ARROFARR="arrarr";
  private static final String JSON_NL_ARROFMAP="arrmap";


  public JSONWriter(Writer writer, SolrQueryRequest req, SolrQueryResponse rsp) {
    super(writer, req, rsp);
    namedListStyle = req.getParam(JSON_NL_STYLE);
    namedListStyle = namedListStyle==null ? JSON_NL_MAP : namedListStyle.intern();
  }

  public void writeResponse() throws IOException {
    int qtime=(int)(rsp.getEndTime() - req.getStartTime());
    NamedList nl = new NamedList();
    HashMap header = new HashMap(1);
    header.put("qtime",qtime);
    nl.add("header", header);
    nl.addAll(rsp.getValues());
    // give the main response a name it it doesn't have one
    if (nl.size()>1 && nl.getVal(1) instanceof DocList && nl.getName(1)==null) {
      nl.setName(1,"response");
    }
    writeNamedList(null, nl);
  }

  protected void writeKey(String fname, boolean needsEscaping) throws IOException {
    writeStr(null, fname, needsEscaping);
    writer.write(':');
  }

  // Represents a NamedList directly as a JSON Object (essentially a Map)
  // more natural but potentially problematic since order is not maintained and keys
  // can't be repeated.
  protected void writeNamedListAsMap(String name, NamedList val) throws IOException {
    int sz = val.size();
    writer.write('{');
    incLevel();

    // In JSON objects (maps) we can't have null keys or duplicates...
    // map null to "" and append a qualifier to duplicates.
    //
    // a=123,a=456 will be mapped to {a=1,a__1=456}
    // Disad: this is ambiguous since a real key could be called a__1
    //
    // Another possible mapping could aggregate multiple keys to an array:
    // a=123,a=456 maps to a=[123,456]
    // Disad: this is ambiguous with a real single value that happens to be an array
    //
    // Both of these mappings have ambiguities.
    HashMap<String,Integer> repeats = new HashMap<String,Integer>(4);

    boolean first=true;
    for (int i=0; i<sz; i++) {
      String key = val.getName(i);
      if (key==null) key="";

      if (first) {
        first=false;
        repeats.put(key,0);
      } else {
        writer.write(',');

        Integer repeatCount = repeats.get(key);
        if (repeatCount==null) {
          repeats.put(key,0);
        } else {
          String newKey = key;
          int newCount = repeatCount;
          do {  // avoid generated key clashing with a real key
            newKey = key + ' ' + (++newCount);
            repeatCount = repeats.get(newKey);
          } while (repeatCount != null);

          repeats.put(key,newCount);
          key = newKey;
        }
      }

      indent();
      writeKey(key, true);
      writeVal(key,val.getVal(i));
    }

    decLevel();
    writer.write('}');
  }

  // Represents a NamedList directly as an array of JSON objects...
  // NamedList("a"=1,"b"=2,null=3) => [{"a":1},{"b":2},3]
  protected void writeNamedListAsArrMap(String name, NamedList val) throws IOException {
    int sz = val.size();
    indent();
    writer.write('[');
    incLevel();

    boolean first=true;
    for (int i=0; i<sz; i++) {
      String key = val.getName(i);

      if (first) {
        first=false;
      } else {
        writer.write(',');
      }

      indent();

      if (key==null) {
        writeVal(null,val.getVal(i));
      } else {
        writer.write('{');
        writeKey(key, true);
        writeVal(key,val.getVal(i));
        writer.write('}');
      }

    }

    decLevel();
    writer.write(']');
  }

  // Represents a NamedList directly as an array of JSON objects...
  // NamedList("a"=1,"b"=2,null=3) => [["a",1],["b",2],[null,3]]
  protected void writeNamedListAsArrArr(String name, NamedList val) throws IOException {
    int sz = val.size();
    indent();
    writer.write('[');
    incLevel();

    boolean first=true;
    for (int i=0; i<sz; i++) {
      String key = val.getName(i);

      if (first) {
        first=false;
      } else {
        writer.write(',');
      }

      indent();

      /*** if key is null, just write value???
      if (key==null) {
        writeVal(null,val.getVal(i));
      } else {
     ***/

        writer.write('[');
        incLevel();
        writeStr(null,key,true);
        writer.write(',');
        writeVal(key,val.getVal(i));
        decLevel();
        writer.write(']');


    }

    decLevel();
    writer.write(']');
  }


  public void writeNamedList(String name, NamedList val) throws IOException {
    if (namedListStyle==JSON_NL_ARROFMAP) {
      writeNamedListAsArrMap(name,val);
    } else if (namedListStyle==JSON_NL_ARROFARR) {
      writeNamedListAsArrArr(name,val);
    } else {
      writeNamedListAsMap(name,val);
    }
  }


  private static class MultiValueField {
    final SchemaField sfield;
    final ArrayList<Fieldable> fields;
    MultiValueField(SchemaField sfield, Fieldable firstVal) {
      this.sfield = sfield;
      this.fields = new ArrayList<Fieldable>(4);
      this.fields.add(firstVal);
    }
  }

  public void writeDoc(String name, Collection<Fieldable> fields, Set<String> returnFields, Map pseudoFields) throws IOException {
    writer.write('{');
    incLevel();

    HashMap<String, MultiValueField> multi = new HashMap<String, MultiValueField>();

    boolean first=true;

    for (Fieldable ff : fields) {
      String fname = ff.name();
      if (returnFields!=null && !returnFields.contains(fname)) {
        continue;
      }

      // if the field is multivalued, it may have other values further on... so
      // build up a list for each multi-valued field.
      SchemaField sf = schema.getField(fname);
      if (sf.multiValued()) {
        MultiValueField mf = multi.get(fname);
        if (mf==null) {
          mf = new MultiValueField(sf, ff);
          multi.put(fname, mf);
        } else {
          mf.fields.add(ff);
        }
      } else {
        // not multi-valued, so write it immediately.
        if (first) {
          first=false;
        } else {
          writer.write(',');
        }
        indent();
        writeKey(fname,true);
        sf.write(this, fname, ff);
      }
    }

    for(MultiValueField mvf : multi.values()) {
      if (first) {
        first=false;
      } else {
        writer.write(',');
      }

      indent();
      writeKey(mvf.sfield.getName(), true);

      boolean indentArrElems=false;
      if (doIndent) {
        // heuristic... TextField is probably the only field type likely to be long enough
        // to warrant indenting individual values.
        indentArrElems = (mvf.sfield.getType() instanceof TextField);
      }

      writer.write('[');
      boolean firstArrElem=true;
      incLevel();

      for (Fieldable ff : mvf.fields) {
        if (firstArrElem) {
          firstArrElem=false;
        } else {
          writer.write(',');
        }
        if (indentArrElems) indent();
        mvf.sfield.write(this, null, ff);
      }
      writer.write(']');
      decLevel();
    }

    if (pseudoFields !=null && pseudoFields.size()>0) {
      writeMap(null,pseudoFields,true,first);
    }

    decLevel();
    writer.write('}');
  }

  // reusable map to store the "score" pseudo-field.
  // if a Doc can ever contain another doc, this optimization would have to go.
  private final HashMap scoreMap = new HashMap(1);

  public void writeDoc(String name, Document doc, Set<String> returnFields, float score, boolean includeScore) throws IOException {
    Map other = null;
    if (includeScore) {
      other = scoreMap;
      scoreMap.put("score",score);
    }
    writeDoc(name, (List<Fieldable>)(doc.getFields()), returnFields, other);
  }

  public void writeDocList(String name, DocList ids, Set<String> fields, Map otherFields) throws IOException {
    boolean includeScore=false;
    if (fields!=null) {
      includeScore = fields.contains("score");
      if (fields.size()==0 || (fields.size()==1 && includeScore) || fields.contains("*")) {
        fields=null;  // null means return all stored fields
      }
    }

    int sz=ids.size();

    writer.write('{');
    incLevel();
    writeKey("numFound",false);
    writeInt(null,ids.matches());
    writer.write(',');
    writeKey("start",false);
    writeInt(null,ids.offset());

    if (includeScore) {
      writer.write(',');
      writeKey("maxScore",false);
      writeFloat(null,ids.maxScore());
    }
    writer.write(',');
    // indent();
    writeKey("docs",false);
    writer.write('[');

    incLevel();
    boolean first=true;

    DocIterator iterator = ids.iterator();
    for (int i=0; i<sz; i++) {
      int id = iterator.nextDoc();
      Document doc = searcher.doc(id);

      if (first) {
        first=false;
      } else {
        writer.write(',');
      }
      indent();
      writeDoc(null, doc, fields, (includeScore ? iterator.score() : 0.0f), includeScore);
    }
    decLevel();
    writer.write(']');

    if (otherFields !=null) {
      writeMap(null, otherFields, true, false);
    }

    decLevel();
    indent();
    writer.write('}');
  }




  public void writeStr(String name, String val, boolean needsEscaping) throws IOException {
    writer.write('"');
    // it might be more efficient to use a stringbuilder or write substrings
    // if writing chars to the stream is slow.
    if (needsEscaping) {
      for (int i=0; i<val.length(); i++) {
        char ch = val.charAt(i);
        switch(ch) {
          case '"':
          case '\\':
            writer.write('\\');
            writer.write(ch);
            break;
            /*** the following are not required to be escaped
             case '\r':
             case '\n':
             case '\t':
             case '\b':
             case '\f':
             case '/':
             ***/
          default: writer.write(ch);
        }
      }
    } else {
      writer.write(val);
    }
    writer.write('"');
  }


  public void writeMap(String name, Map val, boolean excludeOuter, boolean isFirstVal) throws IOException {
    if (!excludeOuter) {
      writer.write('{');
      incLevel();
      isFirstVal=true;
    }

    for (Map.Entry entry : (Set<Map.Entry>)val.entrySet()) {
      Object e = entry.getKey();
      String k = e==null ? null : e.toString();
      Object v = entry.getValue();

      if (isFirstVal) {
        isFirstVal=false;
      } else {
        writer.write(',');
      }

      indent();
      writeKey(k,true);
      writeVal(k,v);
    }

    if (!excludeOuter) {
      decLevel();
      writer.write('}');
    }
  }


  public void writeArray(String name, Object[] val) throws IOException {
    writeArray(name, Arrays.asList(val));
  }

  public void writeArray(String name, Collection val) throws IOException {
    writer.write('[');
    int sz = val.size();
    incLevel();
    boolean first=true;
    for (Object o : val) {
      if (first) {
        first=false;
      } else {
        writer.write(',');
      }
      if (sz>0) indent();
      writeVal(null, o);
    }
    decLevel();
    writer.write(']');
  }

  //
  // Primitive types
  //
  public void writeNull(String name) throws IOException {
    writeStr(name,"null",false);
  }

  public void writeInt(String name, String val) throws IOException {
    writer.write(val);
  }

  public void writeLong(String name, String val) throws IOException {
    writer.write(val);
  }

  public void writeBool(String name, String val) throws IOException {
    writer.write(val);
  }

  public void writeFloat(String name, String val) throws IOException {
    writer.write(val);
  }

  public void writeDouble(String name, String val) throws IOException {
    writer.write(val);
  }

  // TODO: refactor this out to a DateUtils class or something...
  public void writeDate(String name, Date val) throws IOException {
    // using a stringBuilder for numbers can be nice since
    // a temporary string isn't used (it's added directly to the
    // builder's buffer.

    StringBuilder sb = new StringBuilder();
    if (cal==null) cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
    cal.setTime(val);

    int i = cal.get(Calendar.YEAR);
    sb.append(i);
    sb.append('-');
    i = cal.get(Calendar.MONTH) + 1;  // 0 based, so add 1
    if (i<10) sb.append('0');
    sb.append(i);
    sb.append('-');
    i=cal.get(Calendar.DAY_OF_MONTH);
    if (i<10) sb.append('0');
    sb.append(i);
    sb.append('T');
    i=cal.get(Calendar.HOUR_OF_DAY); // 24 hour time format
    if (i<10) sb.append('0');
    sb.append(i);
    sb.append(':');
    i=cal.get(Calendar.MINUTE);
    if (i<10) sb.append('0');
    sb.append(i);
    sb.append(':');
    i=cal.get(Calendar.SECOND);
    if (i<10) sb.append('0');
    sb.append(i);
    i=cal.get(Calendar.MILLISECOND);
    if (i != 0) {
      sb.append('.');
      if (i<100) sb.append('0');
      if (i<10) sb.append('0');
      sb.append(i);

      // handle canonical format specifying fractional
      // seconds shall not end in '0'.  Given the slowness of
      // integer div/mod, simply checking the last character
      // is probably the fastest way to check.
      int lastIdx = sb.length()-1;
      if (sb.charAt(lastIdx)=='0') {
        lastIdx--;
        if (sb.charAt(lastIdx)=='0') {
          lastIdx--;
        }
        sb.setLength(lastIdx+1);
      }

    }
    sb.append('Z');
    writeDate(name, sb.toString());
  }

  public void writeDate(String name, String val) throws IOException {
    writeStr(name, val, false);
  }

  protected static void unicodeEscape(Appendable sb, int ch) throws IOException {
    String str = Integer.toHexString(ch & 0xffff);
    switch (str.length()) {
      case 1: sb.append("\\u000"); break;
      case 2: sb.append("\\u00"); break;
      case 3: sb.append("\\u0");  break;
      default: sb.append("\\u");  break;
    }
    sb.append(str);
  }

}

class PythonWriter extends JSONWriter {
  public PythonWriter(Writer writer, SolrQueryRequest req, SolrQueryResponse rsp) {
    super(writer, req, rsp);
  }

  @Override
  public void writeNull(String name) throws IOException {
    writer.write("None");
  }

  @Override
  public void writeBool(String name, boolean val) throws IOException {
    writer.write(val ? "True" : "False");
  }

  @Override
  public void writeBool(String name, String val) throws IOException {
    writeBool(name,val.charAt(0)=='t');
  }

  /* optionally use a unicode python string if necessary */
  @Override
  public void writeStr(String name, String val, boolean needsEscaping) throws IOException {
    if (!needsEscaping) {
      writer.write('\'');
      writer.write(val);
      writer.write('\'');
      return;
    }

    // use python unicode strings...
    // python doesn't tolerate newlines in strings in it's eval(), so we must escape them.

    StringBuilder sb = new StringBuilder(val.length());
    boolean needUnicode=false;

    for (int i=0; i<val.length(); i++) {
      char ch = val.charAt(i);
      switch(ch) {
        case '\'':
        case '\\': sb.append('\\'); sb.append(ch); break;
        case '\r': sb.append("\\r"); break;
        case '\n': sb.append("\\n"); break;
          default:
            // we don't strictly have to escape these chars, but it will probably increase
            // portability to stick to visible ascii
            if (ch<' ' || ch>127) {
              unicodeEscape(sb, ch);
              needUnicode=true;
            } else {
              sb.append(ch);
            }
        }
      }

    writer.write( needUnicode ? "u'" : "'");
    writer.append(sb);
    writer.write('\'');
  }

  /*
  old version that always used unicode
  public void writeStr(String name, String val, boolean needsEscaping) throws IOException {
    // use python unicode strings...
    // python doesn't tolerate newlines in strings in it's eval(), so we must escape them.
    writer.write("u'");
    // it might be more efficient to use a stringbuilder or write substrings
    // if writing chars to the stream is slow.
    if (needsEscaping) {
      for (int i=0; i<val.length(); i++) {
        char ch = val.charAt(i);
        switch(ch) {
          case '\'':
          case '\\': writer.write('\\'); writer.write(ch); break;
          case '\r': writer.write("\\r"); break;
          case '\n': writer.write("\\n"); break;
          default:
            // we don't strictly have to escape these chars, but it will probably increase
            // portability to stick to visible ascii
            if (ch<' ' || ch>127) {
              unicodeChar(ch);
            } else {
              writer.write(ch);
            }
        }
      }
    } else {
      writer.write(val);
    }
    writer.write('\'');
  }
  */

}


class RubyWriter extends JSONWriter {
  public RubyWriter(Writer writer, SolrQueryRequest req, SolrQueryResponse rsp) {
    super(writer, req, rsp);
  }

  @Override
  public void writeNull(String name) throws IOException {
    writer.write("nil");
  }

  @Override
  protected void writeKey(String fname, boolean needsEscaping) throws IOException {
    writeStr(null, fname, needsEscaping);
    writer.write("=>");
  }

  @Override
  public void writeStr(String name, String val, boolean needsEscaping) throws IOException {
    // Ruby doesn't do unicode escapes... so let the servlet container write raw UTF-8
    // bytes into the string.
    //
    // Use single quoted strings for safety since no evaluation is done within them.
    // Also, there are very few escapes recognized in a singe quoted string, so
    // only escape the backspace and single quote.
    writer.write('\'');
    // it might be more efficient to use a stringbuilder or write substrings
    // if writing chars to the stream is slow.
    if (needsEscaping) {
      for (int i=0; i<val.length(); i++) {
        char ch = val.charAt(i);
        switch(ch) {
          case '\'':
          case '\\': writer.write('\\'); writer.write(ch); break;
          default: writer.write(ch); break;
        }
      }
    } else {
      writer.write(val);
    }
    writer.write('\'');
  }
}
