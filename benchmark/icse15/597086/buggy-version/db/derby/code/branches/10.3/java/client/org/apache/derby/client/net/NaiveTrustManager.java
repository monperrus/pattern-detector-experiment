/*

   Derby - Class org.apache.derby.client.net.NaiveTrustManager

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

package org.apache.derby.client.net;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;

/**
 * This is a naive trust manager we use when we don't want server
 * authentication. Any certificate will be accepted. 
 **/
public class NaiveTrustManager
    implements X509TrustManager
{

    /**
     * We don't want more than one instence of this TrustManager
     */
    private NaiveTrustManager()
    {
    }

    static private TrustManager[] thisManager = null;

    /** 
     * Generate a socket factory with this trust manager. Derby
     * Utility routine which is not part of the X509TrustManager
     * interface.
     **/
    public static SocketFactory getSocketFactory()
        throws java.security.NoSuchAlgorithmException,
               java.security.KeyManagementException
    {
        if (thisManager == null) {
            thisManager = new TrustManager [] {new NaiveTrustManager()};
        }
        
        SSLContext ctx = SSLContext.getInstance("SSL");
        ctx.init(null, // Use default key manager
                 thisManager,
                 null); // Use default random source
        return ctx.getSocketFactory();
    }
    
    /** 
     * Checks wether the we trust the client. Since this trust manager
     * is just for the Derby clients, this routine is actually never
     * called, but need to be here when we implement X509TrustManager.
     * @param chain The client's certificate chain
     * @param authType authorization type (e.g. "RSA" or "DHE_DSS")
     **/
    public void checkClientTrusted(X509Certificate[] chain, 
                                   String authType)
        throws CertificateException
    {
        // Reject all attemtpts to truts a client. We should never end
        // up here.
        throw new CertificateException();
    }
    
    /** 
     * Checks wether the we trust the server, which we allways will.
     * @param chain The server's certificate chain
     * @param authType authorization type (e.g. "RSA" or "DHE_DSS")
     **/
    public void checkServerTrusted(X509Certificate[] chain, 
                                   String authType)
        throws CertificateException
    {
        // Do nothing. We trust everyone.
    }
    
    /**
     * Return an array of certificate authority certificates which are
     * trusted for authenticating peers. Not relevant for this trust
     * manager.
     */
    public X509Certificate[] getAcceptedIssuers()
    {
        return new X509Certificate[0];
    }
    
}
