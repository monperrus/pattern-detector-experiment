  Merged /lucene/dev/trunk/lucene/analysis:r1460039
  Merged /lucene/dev/trunk/lucene:r1460039
/*
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
package org.apache.solr.client.solrj.beans;

import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;

import java.lang.reflect.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.ByteBuffer;

/**
 * A class to map objects to and from solr documents.
 * 
 *
 * @since solr 1.3
 */
public class DocumentObjectBinder {
  
  private final Map<Class, List<DocField>> infocache = new ConcurrentHashMap<Class, List<DocField>>();

  public DocumentObjectBinder() {
  }

  public <T> List<T> getBeans(Class<T> clazz, SolrDocumentList solrDocList) {
    List<DocField> fields = getDocFields(clazz);
    List<T> result = new ArrayList<T>(solrDocList.size());

    for (SolrDocument sdoc : solrDocList) {
      result.add(getBean(clazz, fields, sdoc));
    }
    return result;
  }

  public <T> T getBean(Class<T> clazz, SolrDocument solrDoc) {
    return getBean(clazz, null, solrDoc);
  }
  
  private <T> T getBean(Class<T> clazz, List<DocField> fields, SolrDocument solrDoc) {
    if (fields == null) {
      fields = getDocFields(clazz);
    }

    try {
      T obj = clazz.newInstance();
      for (DocField docField : fields) {
        docField.inject(obj, solrDoc);
      }
      return obj;
    } catch (Exception e) {
      throw new BindingException("Could not instantiate object of " + clazz, e);
    }
  }
  
  public SolrInputDocument toSolrInputDocument(Object obj) {
    List<DocField> fields = getDocFields(obj.getClass());
    if (fields.isEmpty()) {
      throw new BindingException("class: " + obj.getClass() + " does not define any fields.");
    }

    SolrInputDocument doc = new SolrInputDocument();
    for (DocField field : fields) {
      if (field.dynamicFieldNamePatternMatcher != null &&
          field.get(obj) != null &&
          field.isContainedInMap) {
        Map<String, Object> mapValue = (Map<String, Object>) field.get(obj);

        for (Map.Entry<String, Object> e : mapValue.entrySet()) {
          doc.setField(e.getKey(), e.getValue(), 1.0f);
        }
      } else {
        doc.setField(field.name, field.get(obj), 1.0f);
      }
    }
    return doc;
  }
  
  private List<DocField> getDocFields(Class clazz) {
    List<DocField> fields = infocache.get(clazz);
    if (fields == null) {
      synchronized(infocache) {
        infocache.put(clazz, fields = collectInfo(clazz));
      }
    }
    return fields;
  }

  private List<DocField> collectInfo(Class clazz) {
    List<DocField> fields = new ArrayList<DocField>();
    Class superClazz = clazz;
    List<AccessibleObject> members = new ArrayList<AccessibleObject>();

    while (superClazz != null && superClazz != Object.class) {
      members.addAll(Arrays.asList(superClazz.getDeclaredFields()));
      members.addAll(Arrays.asList(superClazz.getDeclaredMethods()));
      superClazz = superClazz.getSuperclass();
    }

    for (AccessibleObject member : members) {
      // TODO: Fix below code to use c.isAnnotationPresent(). It was changed
      // to the null check to work around a bug in JDK 8 b78 (see LUCENE-4808).
      if (member.getAnnotation(Field.class) != null) {
        member.setAccessible(true);
        fields.add(new DocField(member));
      }
    }
    return fields;
  }

  private static class DocField {
    private String name;
    private java.lang.reflect.Field field;
    private Method setter;
    private Method getter;
    private Class type;
    private boolean isArray;
    private boolean isList;

    /*
     * dynamic fields may use a Map based data structure to bind a given field.
     * if a mapping is done using, "Map<String, List<String>> foo", <code>isContainedInMap</code>
     * is set to <code>TRUE</code> as well as <code>isList</code> is set to <code>TRUE</code>
     */
    private boolean isContainedInMap;
    private Pattern dynamicFieldNamePatternMatcher;

    public DocField(AccessibleObject member) {
      if (member instanceof java.lang.reflect.Field) {
        field = (java.lang.reflect.Field) member;
      } else {
        setter = (Method) member;
      }
      Field annotation = member.getAnnotation(Field.class);
      storeName(annotation);
      storeType();
      
      // Look for a matching getter
      if (setter != null) {
        String gname = setter.getName();
        if (gname.startsWith("set")) {
          gname = "get" + gname.substring(3);
          try {
            getter = setter.getDeclaringClass().getMethod(gname, (Class[]) null);
          } catch (Exception ex) {
            // no getter -- don't worry about it...
            if (type == Boolean.class) {
              gname = "is" + setter.getName().substring(3);
              try {
                getter = setter.getDeclaringClass().getMethod(gname, (Class[]) null);
              } catch(Exception ex2) {
                // no getter -- don't worry about it...
              }
            }
          }
        }
      }
    }

    private void storeName(Field annotation) {
      if (annotation.value().equals(Field.DEFAULT)) {
        if (field != null) {
          name = field.getName();
        } else {
          String setterName = setter.getName();
          if (setterName.startsWith("set") && setterName.length() > 3) {
            name = setterName.substring(3, 4).toLowerCase(Locale.ROOT) + setterName.substring(4);
          } else {
            name = setter.getName();
          }
        }
      } else if (annotation.value().indexOf('*') >= 0) { //dynamic fields are annotated as @Field("categories_*")
        //if the field was annotated as a dynamic field, convert the name into a pattern
        //the wildcard (*) is supposed to be either a prefix or a suffix, hence the use of replaceFirst
        name = annotation.value().replaceFirst("\\*", "\\.*");
        dynamicFieldNamePatternMatcher = Pattern.compile("^"+name+"$");
      } else {
        name = annotation.value();
      }
    }

    private void storeType() {
      if (field != null) {
        type = field.getType();
      } else {
        Class[] params = setter.getParameterTypes();
        if (params.length != 1) {
          throw new BindingException("Invalid setter method. Must have one and only one parameter");
        }
        type = params[0];
      }

      if(type == Collection.class || type == List.class || type == ArrayList.class) {
        type = Object.class;
        isList = true;
      } else if (type == byte[].class) {
        //no op
      } else if (type.isArray()) {
        isArray = true;
        type = type.getComponentType();
      } else if (type == Map.class || type == HashMap.class) { //corresponding to the support for dynamicFields
        isContainedInMap = true;
        //assigned a default type
        type = Object.class;
        if (field != null) {
          if (field.getGenericType() instanceof ParameterizedType) {
            //check what are the generic values
            ParameterizedType parameterizedType = (ParameterizedType) field.getGenericType();
            Type[] types = parameterizedType.getActualTypeArguments();
            if (types != null && types.length == 2 && types[0] == String.class) {
              //the key should always be String
              //Raw and primitive types
              if (types[1] instanceof Class) {
                //the value could be multivalued then it is a List, Collection, ArrayList
                if (types[1]== Collection.class || types[1] == List.class || types[1] == ArrayList.class) {
                  type = Object.class;
                  isList = true;
                } else {
                  //else assume it is a primitive and put in the source type itself
                  type = (Class) types[1];
                }
              } else if (types[1] instanceof ParameterizedType) { //Of all the Parameterized types, only List is supported
                Type rawType = ((ParameterizedType)types[1]).getRawType();
                if(rawType== Collection.class || rawType == List.class || rawType == ArrayList.class){
                  type = Object.class;
                  isList = true;
                }
              } else if (types[1] instanceof GenericArrayType) { //Array types
                type = (Class) ((GenericArrayType) types[1]).getGenericComponentType();
                isArray = true;
              } else { //Throw an Exception if types are not known
                throw new BindingException("Allowed type for values of mapping a dynamicField are : " +
                    "Object, Object[] and List");
              }
            }
          }
        }
      }
    }

    /**
     * Called by the {@link #inject} method to read the value(s) for a field
     * This method supports reading of all "matching" fieldName's in the <code>SolrDocument</code>
     *
     * Returns <code>SolrDocument.getFieldValue</code> for regular fields,
     * and <code>Map<String, List<Object>></code> for a dynamic field. The key is all matching fieldName's.
     */
    @SuppressWarnings("unchecked")
    private Object getFieldValue(SolrDocument solrDocument) {
      Object fieldValue = solrDocument.getFieldValue(name);
      if (fieldValue != null) {
        //this is not a dynamic field. so return the value
        return fieldValue;
      }

      if (dynamicFieldNamePatternMatcher == null) {
        return null;
      }

      //reading dynamic field values
      Map<String, Object> allValuesMap = null;
      List allValuesList = null;
      if (isContainedInMap) {
        allValuesMap = new HashMap<String, Object>();
      } else {
        allValuesList = new ArrayList();
      }

      for (String field : solrDocument.getFieldNames()) {
        if (dynamicFieldNamePatternMatcher.matcher(field).find()) {
          Object val = solrDocument.getFieldValue(field);
          if (val == null) {
            continue;
          }

          if (isContainedInMap) {
            if (isList) {
              if (!(val instanceof List)) {
                List al = new ArrayList();
                al.add(val);
                val = al;
              }
            } else if (isArray) {
              if (!(val instanceof List)) {
                Object[] arr = (Object[]) Array.newInstance(type, 1);
                arr[0] = val;
                val = arr;
              } else {
                val = Array.newInstance(type, ((List) val).size());
              }
            }
            allValuesMap.put(field, val);
          } else {
            if (val instanceof Collection) {
              allValuesList.addAll((Collection) val);
            } else {
              allValuesList.add(val);
            }
          }
        }
      }
      if (isContainedInMap) {
        return allValuesMap.isEmpty() ? null : allValuesMap;
      } else {
        return allValuesList.isEmpty() ? null : allValuesList;
      }
    }

    <T> void inject(T obj, SolrDocument sdoc) {
      Object val = getFieldValue(sdoc);
      if(val == null) {
        return;
      }

      if (isArray && !isContainedInMap) {
        List list;
        if (val.getClass().isArray()) {
          set(obj, val);
          return;
        } else if (val instanceof List) {
          list = (List) val;
        } else {
          list = new ArrayList();
          list.add(val);
        }
        set(obj, list.toArray((Object[]) Array.newInstance(type, list.size())));
      } else if (isList && !isContainedInMap) {
        if (!(val instanceof List)) {
          List list = new ArrayList();
          list.add(val);
          val =  list;
        }
        set(obj, val);
      } else if (isContainedInMap) {
        if (val instanceof Map) {
          set(obj,  val);
        }
      } else {
        set(obj, val);
      }

    }

    private void set(Object obj, Object v) {
      if (v != null && type == ByteBuffer.class && v.getClass() == byte[].class) {
        v = ByteBuffer.wrap((byte[]) v);
      }
      try {
        if (field != null) {
          field.set(obj, v);
        } else if (setter != null) {
          setter.invoke(obj, v);
        }
      } 
      catch (Exception e) {
        throw new BindingException("Exception while setting value : " + v + " on " + (field != null ? field : setter), e);
      }
    }

    public Object get(final Object obj) {
      if (field != null) {
        try {
          return field.get(obj);
        } catch (Exception e) {
          throw new BindingException("Exception while getting value: " + field, e);
        }
      } else if (getter == null) {
        throw new BindingException("Missing getter for field: " + name + " -- You can only call the 'get' for fields that have a field of 'get' method");
      }

      try {
        return getter.invoke(obj, (Object[]) null);
      } catch (Exception e) {
        throw new BindingException("Exception while getting value: " + getter, e);
      }
    }
  }
}
