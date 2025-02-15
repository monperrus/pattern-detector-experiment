/*

   Derby - Class org.apache.derbyTesting.functionTests.harness.CurrentTime

   Copyright 1999, 2004 The Apache Software Foundation or its licensors, as applicable.

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

package org.apache.derbyTesting.functionTests.harness;

import java.sql.Timestamp;
import java.lang.String;

/**
  Return the current system time as a String
  Used to print a timestamp for suite/test runs
*/
public class CurrentTime
{

	public static String getTime()
	{
        // Get the current time and convert to a String
        long millis = System.currentTimeMillis();
        Timestamp ts = new Timestamp(millis);
        String s = ts.toString();
        s = s.substring(0, s.lastIndexOf("."));
        return s;
	}

	// no instances permitted.
	private CurrentTime() {}
}
