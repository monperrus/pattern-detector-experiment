/*

   Derby - Class org.apache.derbyTesting.functionTests.harness.ibm15

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derbyTesting.functionTests.harness;

import java.util.Vector;
import java.util.StringTokenizer;

/**
  <p>This class is for IBM's jdk 1.6.

 */
public class ibm16 extends jvm {

	public String getName(){return "ibm16";}
    public ibm16(boolean noasyncgc, boolean verbosegc, boolean noclassgc,
    long ss, long oss, long ms, long mx, String classpath, String prof,
    boolean verify, boolean noverify, boolean nojit, Vector D) {
        super(noasyncgc,verbosegc,noclassgc,ss,oss,ms,mx,classpath,prof,
		verify,noverify,nojit,D);
    }
    // more typical use:
    public ibm16(String classpath, Vector D) {
        super(classpath,D);
    }
    // more typical use:
    public ibm16(long ms, long mx, String classpath, Vector D) {
        super(ms,mx,classpath,D);
    }
    // actual use
    public ibm16() { }

    // return the command line to invoke this VM.  The caller then adds
    // the class and program arguments.
    public Vector getCommandLine()
    {
        StringBuffer sb = new StringBuffer();
        Vector v = super.getCommandLine();
        appendOtherFlags(sb);
        String s = sb.toString();
        StringTokenizer st = new StringTokenizer(s);
        while (st.hasMoreTokens())
        {
            v.addElement(st.nextToken());
        }
        return v;
	}

	public void appendOtherFlags(StringBuffer sb)
	{
        if (noasyncgc) warn("ibm16 does not support noasyncgc");
        if (verbosegc) sb.append(" -verbose:gc");
        if (noclassgc) sb.append(" -Xnoclassgc");
        if (ss>=0) warn("ibm16 does not support ss");
        if (oss>=0) warn("ibm16 does not support oss");
        if (ms>=0) {
          sb.append(" -ms");
          sb.append(ms);
        }
        if (mx>=0) {
          sb.append(" -mx");
          sb.append(mx);
        }
        if (classpath!=null) {
          sb.append(" -classpath ");
          sb.append(classpath);
        }
        if (prof!=null) warn("ibm16 does not support prof");
        if (verify) warn("ibm16 does not support verify");
        if (noverify) warn("ibm16 does not support noverify");
        if (nojit) sb.append(" -Djava.compiler=NONE");
        if (D != null)
          for (int i=0; i<D.size();i++) {
	        sb.append(" -D");
	        sb.append((String)(D.elementAt(i)));
          }
    }
	public String getDintro() { return "-D"; }
}

