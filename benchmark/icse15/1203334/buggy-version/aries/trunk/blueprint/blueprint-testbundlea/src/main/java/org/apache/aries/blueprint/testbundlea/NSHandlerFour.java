  + Rev Date
  + native
/**
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.aries.blueprint.testbundlea;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.aries.blueprint.NamespaceHandler;
import org.apache.aries.blueprint.ParserContext;
import org.apache.aries.blueprint.PassThroughMetadata;
import org.apache.aries.blueprint.mutable.MutableBeanMetadata;
import org.apache.aries.blueprint.mutable.MutableRefMetadata;
import org.osgi.service.blueprint.reflect.BeanMetadata;
import org.osgi.service.blueprint.reflect.ComponentMetadata;
import org.osgi.service.blueprint.reflect.Metadata;
import org.osgi.service.blueprint.reflect.RefMetadata;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class NSHandlerFour implements NamespaceHandler{
    public static String NSURI = "http://ns.handler.four";
    private static String ELT_NAME = "nshandlerfour";
    private static String ATTRIB_ID = "id";


    public static class Four {
        public Four() {
        }
    }
    //process elements
    public Metadata parse(Element element, ParserContext context) {
        Metadata retval = null;       
        if( element.getLocalName().equals(ELT_NAME) ) {
            final String id = element.getAttributeNS(NSURI, ATTRIB_ID);
            MutableBeanMetadata bm = context.createMetadata(MutableBeanMetadata.class);
            bm.setId(id);
            bm.setClassName(Four.class.getName());
            retval = bm;
        }
        return retval;
    }    

    //supply schema back to blueprint.
    public URL getSchemaLocation(String namespace) {
        return this.getClass().getResource("nshandlerfour.xsd");
    }

    public Set<Class> getManagedClasses() {
        Class cls = Four.class;
        return Collections.singleton(cls);
    }

    public ComponentMetadata decorate(Node node, ComponentMetadata component,
                                      ParserContext context) {
        return component;
    }

}
