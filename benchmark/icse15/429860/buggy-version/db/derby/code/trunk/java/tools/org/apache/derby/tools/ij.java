/*

   Derby - Class org.apache.derby.tools.ij

   Copyright 1998, 2004 The Apache Software Foundation or its licensors, as applicable.

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

package org.apache.derby.tools;

import org.apache.derby.iapi.services.info.JVMInfo;
import org.apache.derby.iapi.tools.i18n.LocalizedInput;
import org.apache.derby.iapi.tools.i18n.LocalizedOutput;
import org.apache.derby.iapi.tools.i18n.LocalizedResource;

import org.apache.derby.impl.tools.ij.Main;
import org.apache.derby.impl.tools.ij.utilMain;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.util.Properties;

/**
	
	ij is Derby's interactive JDBC scripting tool.
	It is a simple utility for running scripts against a Derby database.
	You can also use it interactively to run ad hoc queries.
	ij provides several commands for ease in accessing a variety of JDBC features.
	<P>

	To run from the command line enter the following:
	<p>
	java [options] org.apache.derby.tools.ij [arguments]
	<P>
	ij is can also be used with any database server that supports a JDBC driver.
*/
public class ij {

  /**
  	@exception IOException thrown if cannot access input or output files.
   */
  static public void main(String[] args) throws IOException {

	  /* We decide which verion of ij (2.0 or 4.0) to
	   * load based on the same criteria that the JDBC driver
	   * uses.
	   */
	  if (JVMInfo.JDK_ID == JVMInfo.J2SE_13)
	  {
		  Main.main(args);
	  }
	  else
	  {
		  org.apache.derby.impl.tools.ij.Main14.main(args);
	  }
  }
  
  /**
   * Run a SQL script from an InputStream and write
   * the resulting output to the provided PrintStream.
   * 
   * @param conn Connection to be used as the script's default connection. 
   * @param sqlIn InputStream for the script.
   * @param inputEncoding Encoding of the script.
   * @param sqlOut PrintStream for the script's output
   * @param outputEncoding Output encoding to use.
   * @return Number of SQLExceptions thrown during the execution, -1 if not known.
   * @throws UnsupportedEncodingException
   */
  public static int runScript(
		  Connection conn,
		  InputStream sqlIn,
		  String inputEncoding,
		  PrintStream sqlOut,
		  String outputEncoding)
		  throws UnsupportedEncodingException
  {
	  LocalizedOutput lo = 
		  outputEncoding == null ?
				  LocalizedResource.getInstance().
		            getNewOutput(sqlOut)
	             :  
		          LocalizedResource.getInstance().
                    getNewEncodedOutput(sqlOut, outputEncoding);

	  Main ijE;
	  if (JVMInfo.JDK_ID == JVMInfo.J2SE_13)
	  {
		  ijE = new Main(false);
	  }
	  else
	  {
		  // temp - allow ij to continue to work under jdk131
		  // will resolve as part of DEBRY-1609
		  // jdk13 gets error loading Main14 due to the
		  // class now being built with the jdk14 target flag.
		  // ijE = new org.apache.derby.impl.tools.ij.Main14(false);
		  ijE = new Main(false);
	  }	  
	  
	  LocalizedInput li = LocalizedResource.getInstance().
	            getNewEncodedInput(sqlIn, inputEncoding);
	  
	  utilMain um = ijE.getutilMain(1, lo);

	  um.goScript(conn, li);
	  
	  return -1;
  }

  private ij() { // no instances allowed
  }
  
  public static String getArg(String param, String[] args)
  {
	  return org.apache.derby.impl.tools.ij.util.getArg(param, args);
  }

  public static void getPropertyArg(String[] args) throws IOException
  {
	  org.apache.derby.impl.tools.ij.util.getPropertyArg(args);
  }

  public static java.sql.Connection startJBMS()
	  throws java.sql.SQLException, IllegalAccessException, ClassNotFoundException, InstantiationException
  {			
		return org.apache.derby.impl.tools.ij.util.startJBMS();
  }
}
