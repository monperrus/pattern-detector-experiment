/**
 * Autogenerated by Thrift
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 */
package org.apache.cassandra.service;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

import org.apache.thrift.*;
import org.apache.thrift.meta_data.*;
import org.apache.thrift.protocol.*;

public class batch_mutation_super_t implements TBase, java.io.Serializable, Cloneable {
  private static final TStruct STRUCT_DESC = new TStruct("batch_mutation_super_t");
  private static final TField TABLE_FIELD_DESC = new TField("table", TType.STRING, (short)1);
  private static final TField KEY_FIELD_DESC = new TField("key", TType.STRING, (short)2);
  private static final TField CFMAP_FIELD_DESC = new TField("cfmap", TType.MAP, (short)3);

  public String table;
  public static final int TABLE = 1;
  public String key;
  public static final int KEY = 2;
  public Map<String,List<superColumn_t>> cfmap;
  public static final int CFMAP = 3;

  private final Isset __isset = new Isset();
  private static final class Isset implements java.io.Serializable {
  }

  public static final Map<Integer, FieldMetaData> metaDataMap = Collections.unmodifiableMap(new HashMap<Integer, FieldMetaData>() {{
    put(TABLE, new FieldMetaData("table", TFieldRequirementType.DEFAULT, 
        new FieldValueMetaData(TType.STRING)));
    put(KEY, new FieldMetaData("key", TFieldRequirementType.DEFAULT, 
        new FieldValueMetaData(TType.STRING)));
    put(CFMAP, new FieldMetaData("cfmap", TFieldRequirementType.DEFAULT, 
        new FieldValueMetaData(TType.MAP)));
  }});

  static {
    FieldMetaData.addStructMetaDataMap(batch_mutation_super_t.class, metaDataMap);
  }

  public batch_mutation_super_t() {
  }

  public batch_mutation_super_t(
    String table,
    String key,
    Map<String,List<superColumn_t>> cfmap)
  {
    this();
    this.table = table;
    this.key = key;
    this.cfmap = cfmap;
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public batch_mutation_super_t(batch_mutation_super_t other) {
    if (other.isSetTable()) {
      this.table = other.table;
    }
    if (other.isSetKey()) {
      this.key = other.key;
    }
    if (other.isSetCfmap()) {
      this.cfmap = other.cfmap;
    }
  }

  @Override
  public batch_mutation_super_t clone() {
    return new batch_mutation_super_t(this);
  }

  public String getTable() {
    return this.table;
  }

  public void setTable(String table) {
    this.table = table;
  }

  public void unsetTable() {
    this.table = null;
  }

  // Returns true if field table is set (has been asigned a value) and false otherwise
  public boolean isSetTable() {
    return this.table != null;
  }

  public void setTableIsSet(boolean value) {
    if (!value) {
      this.table = null;
    }
  }

  public String getKey() {
    return this.key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public void unsetKey() {
    this.key = null;
  }

  // Returns true if field key is set (has been asigned a value) and false otherwise
  public boolean isSetKey() {
    return this.key != null;
  }

  public void setKeyIsSet(boolean value) {
    if (!value) {
      this.key = null;
    }
  }

  public int getCfmapSize() {
    return (this.cfmap == null) ? 0 : this.cfmap.size();
  }

  public void putToCfmap(String key, List<superColumn_t> val) {
    if (this.cfmap == null) {
      this.cfmap = new HashMap<String,List<superColumn_t>>();
    }
    this.cfmap.put(key, val);
  }

  public Map<String,List<superColumn_t>> getCfmap() {
    return this.cfmap;
  }

  public void setCfmap(Map<String,List<superColumn_t>> cfmap) {
    this.cfmap = cfmap;
  }

  public void unsetCfmap() {
    this.cfmap = null;
  }

  // Returns true if field cfmap is set (has been asigned a value) and false otherwise
  public boolean isSetCfmap() {
    return this.cfmap != null;
  }

  public void setCfmapIsSet(boolean value) {
    if (!value) {
      this.cfmap = null;
    }
  }

  public void setFieldValue(int fieldID, Object value) {
    switch (fieldID) {
    case TABLE:
      if (value == null) {
        unsetTable();
      } else {
        setTable((String)value);
      }
      break;

    case KEY:
      if (value == null) {
        unsetKey();
      } else {
        setKey((String)value);
      }
      break;

    case CFMAP:
      if (value == null) {
        unsetCfmap();
      } else {
        setCfmap((Map<String,List<superColumn_t>>)value);
      }
      break;

    default:
      throw new IllegalArgumentException("Field " + fieldID + " doesn't exist!");
    }
  }

  public Object getFieldValue(int fieldID) {
    switch (fieldID) {
    case TABLE:
      return getTable();

    case KEY:
      return getKey();

    case CFMAP:
      return getCfmap();

    default:
      throw new IllegalArgumentException("Field " + fieldID + " doesn't exist!");
    }
  }

  // Returns true if field corresponding to fieldID is set (has been asigned a value) and false otherwise
  public boolean isSet(int fieldID) {
    switch (fieldID) {
    case TABLE:
      return isSetTable();
    case KEY:
      return isSetKey();
    case CFMAP:
      return isSetCfmap();
    default:
      throw new IllegalArgumentException("Field " + fieldID + " doesn't exist!");
    }
  }

  @Override
  public boolean equals(Object that) {
    if (that == null)
      return false;
    if (that instanceof batch_mutation_super_t)
      return this.equals((batch_mutation_super_t)that);
    return false;
  }

  public boolean equals(batch_mutation_super_t that) {
    if (that == null)
      return false;

    boolean this_present_table = true && this.isSetTable();
    boolean that_present_table = true && that.isSetTable();
    if (this_present_table || that_present_table) {
      if (!(this_present_table && that_present_table))
        return false;
      if (!this.table.equals(that.table))
        return false;
    }

    boolean this_present_key = true && this.isSetKey();
    boolean that_present_key = true && that.isSetKey();
    if (this_present_key || that_present_key) {
      if (!(this_present_key && that_present_key))
        return false;
      if (!this.key.equals(that.key))
        return false;
    }

    boolean this_present_cfmap = true && this.isSetCfmap();
    boolean that_present_cfmap = true && that.isSetCfmap();
    if (this_present_cfmap || that_present_cfmap) {
      if (!(this_present_cfmap && that_present_cfmap))
        return false;
      if (!this.cfmap.equals(that.cfmap))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return 0;
  }

  public void read(TProtocol iprot) throws TException {
    TField field;
    iprot.readStructBegin();
    while (true)
    {
      field = iprot.readFieldBegin();
      if (field.type == TType.STOP) { 
        break;
      }
      switch (field.id)
      {
        case TABLE:
          if (field.type == TType.STRING) {
            this.table = iprot.readString();
          } else { 
            TProtocolUtil.skip(iprot, field.type);
          }
          break;
        case KEY:
          if (field.type == TType.STRING) {
            this.key = iprot.readString();
          } else { 
            TProtocolUtil.skip(iprot, field.type);
          }
          break;
        case CFMAP:
          if (field.type == TType.MAP) {
            {
              TMap _map13 = iprot.readMapBegin();
              this.cfmap = new HashMap<String,List<superColumn_t>>(2*_map13.size);
              for (int _i14 = 0; _i14 < _map13.size; ++_i14)
              {
                String _key15;
                List<superColumn_t> _val16;
                _key15 = iprot.readString();
                {
                  TList _list17 = iprot.readListBegin();
                  _val16 = new ArrayList<superColumn_t>(_list17.size);
                  for (int _i18 = 0; _i18 < _list17.size; ++_i18)
                  {
                    superColumn_t _elem19;
                    _elem19 = new superColumn_t();
                    _elem19.read(iprot);
                    _val16.add(_elem19);
                  }
                  iprot.readListEnd();
                }
                this.cfmap.put(_key15, _val16);
              }
              iprot.readMapEnd();
            }
          } else { 
            TProtocolUtil.skip(iprot, field.type);
          }
          break;
        default:
          TProtocolUtil.skip(iprot, field.type);
          break;
      }
      iprot.readFieldEnd();
    }
    iprot.readStructEnd();


    // check for required fields of primitive type, which can't be checked in the validate method
    validate();
  }

  public void write(TProtocol oprot) throws TException {
    validate();

    oprot.writeStructBegin(STRUCT_DESC);
    if (this.table != null) {
      oprot.writeFieldBegin(TABLE_FIELD_DESC);
      oprot.writeString(this.table);
      oprot.writeFieldEnd();
    }
    if (this.key != null) {
      oprot.writeFieldBegin(KEY_FIELD_DESC);
      oprot.writeString(this.key);
      oprot.writeFieldEnd();
    }
    if (this.cfmap != null) {
      oprot.writeFieldBegin(CFMAP_FIELD_DESC);
      {
        oprot.writeMapBegin(new TMap(TType.STRING, TType.LIST, this.cfmap.size()));
        for (Map.Entry<String, List<superColumn_t>> _iter20 : this.cfmap.entrySet())        {
          oprot.writeString(_iter20.getKey());
          {
            oprot.writeListBegin(new TList(TType.STRUCT, _iter20.getValue().size()));
            for (superColumn_t _iter21 : _iter20.getValue())            {
              _iter21.write(oprot);
            }
            oprot.writeListEnd();
          }
        }
        oprot.writeMapEnd();
      }
      oprot.writeFieldEnd();
    }
    oprot.writeFieldStop();
    oprot.writeStructEnd();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("batch_mutation_super_t(");
    boolean first = true;

    sb.append("table:");
    if (this.table == null) {
      sb.append("null");
    } else {
      sb.append(this.table);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("key:");
    if (this.key == null) {
      sb.append("null");
    } else {
      sb.append(this.key);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("cfmap:");
    if (this.cfmap == null) {
      sb.append("null");
    } else {
      sb.append(this.cfmap);
    }
    first = false;
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws TException {
    // check for required fields
    // check that fields of type enum have valid values
  }

}

