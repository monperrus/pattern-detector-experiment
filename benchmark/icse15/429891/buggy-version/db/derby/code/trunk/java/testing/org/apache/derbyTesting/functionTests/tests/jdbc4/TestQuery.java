/*
 
   Derby - Class org.apache.derbyTesting.functionTests.tests.jdbc4.TestQuery
 
   Copyright 2006 The Apache Software Foundation or its licensors, as applicable.
 
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

package org.apache.derbyTesting.functionTests.tests.jdbc4;

import java.sql.BaseQuery;
import java.sql.DataSet;
import java.sql.Select;

/**
 * This interface is used by TestQueryObject test
 * This interface will be implemented at run time
 * by QueryObjectGenerator
 */

public interface TestQuery extends BaseQuery {
    /**
     * Method defnition is generated by QueryObjectGenerator
     * It executes query specified in Select annotation and generates 
     * data set object populated with TestData objects
     * @return DataSet populated with TestData Object
     */
    @Select(sql="SELECT id, data FROM querytable") 
	DataSet <TestData> getAllData();    
}
