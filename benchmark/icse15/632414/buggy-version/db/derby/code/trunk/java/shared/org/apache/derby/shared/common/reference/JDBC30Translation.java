/*

   Derby - Class org.apache.derby.shared.common.reference.JDBC30Translation

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to you under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derby.shared.common.reference;
import java.sql.ParameterMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
/**
        This class contains public statics that map directly
        to the new public statics in the jdbc 3.0 classes.
        By providing an intermediary class, we can use the
        same statics without having to import the jdbc 3.0 classes
        into other classes.


        <P>
        This class should not be shipped with the product.

        <P>
        This class has no methods, all it contains are constants
        are public, static and final since they are declared in an interface.
*/

public interface JDBC30Translation {

        /*
        ** public statics from 3.0 version of java.sql.ParameterMetaData
        */
        public static final int PARAMETER_NO_NULLS = ParameterMetaData.parameterNoNulls;
        public static final int PARAMETER_NULLABLE = ParameterMetaData.parameterNullable;
        public static final int PARAMETER_NULLABLE_UNKNOWN = ParameterMetaData.parameterNullableUnknown;
        public static final int PARAMETER_MODE_UNKNOWN = ParameterMetaData.parameterModeUnknown;
        public static final int PARAMETER_MODE_IN = ParameterMetaData.parameterModeIn;
        public static final int PARAMETER_MODE_IN_OUT = ParameterMetaData.parameterModeInOut;
        public static final int PARAMETER_MODE_OUT = ParameterMetaData.parameterModeOut;

        /*
        ** public statics from 3.0 version of java.sql.ResultSet
        */
        public static final int HOLD_CURSORS_OVER_COMMIT = ResultSet.HOLD_CURSORS_OVER_COMMIT;
        public static final int CLOSE_CURSORS_AT_COMMIT = ResultSet.CLOSE_CURSORS_AT_COMMIT;

        /*
        ** public statics from 3.0 version of java.sql.Statement
        */
        public static final int CLOSE_CURRENT_RESULT = Statement.CLOSE_CURRENT_RESULT;
        public static final int KEEP_CURRENT_RESULT = Statement.KEEP_CURRENT_RESULT;
        public static final int CLOSE_ALL_RESULTS = Statement.CLOSE_ALL_RESULTS;
        public static final int SUCCESS_NO_INFO = Statement.SUCCESS_NO_INFO;
        public static final int EXECUTE_FAILED = Statement.EXECUTE_FAILED;
        public static final int RETURN_GENERATED_KEYS = Statement.RETURN_GENERATED_KEYS;
        public static final int NO_GENERATED_KEYS = Statement.NO_GENERATED_KEYS;
}
