  Merged /lucene/dev/trunk/lucene/test-framework:r1434109
  Merged /lucene/dev/trunk/lucene/README.txt:r1434109
  Merged /lucene/dev/trunk/lucene/queries:r1434109
  Merged /lucene/dev/trunk/lucene/module-build.xml:r1434109
  Merged /lucene/dev/trunk/lucene/facet:r1434109
  Merged /lucene/dev/trunk/lucene/queryparser:r1434109
  Merged /lucene/dev/trunk/lucene/common-build.xml:r1434109
  Merged /lucene/dev/trunk/lucene/demo:r1434109
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.cfs.zip:r1434109
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.optimized.nocfs.zip:r1434109
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/TestBackwardsCompatibility.java:r1434109
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.optimized.cfs.zip:r1434109
  Merged /lucene/dev/trunk/lucene/core/src/test/org/apache/lucene/index/index.40.nocfs.zip:r1434109
  Merged /lucene/dev/trunk/lucene/core:r1434109
  Merged /lucene/dev/trunk/lucene/benchmark:r1434109
  Merged /lucene/dev/trunk/lucene/spatial:r1434109
  Merged /lucene/dev/trunk/lucene/build.xml:r1434109
  Merged /lucene/dev/trunk/lucene/join:r1434109
  Merged /lucene/dev/trunk/lucene/tools:r1434109
  Merged /lucene/dev/trunk/lucene/backwards:r1434109
  Merged /lucene/dev/trunk/lucene/site:r1434109
  Merged /lucene/dev/trunk/lucene/licenses:r1434109
  Merged /lucene/dev/trunk/lucene/memory:r1434109
  Merged /lucene/dev/trunk/lucene/JRE_VERSION_MIGRATION.txt:r1434109
  Merged /lucene/dev/trunk/lucene/BUILD.txt:r1434109
  Merged /lucene/dev/trunk/lucene/suggest:r1434109
  Merged /lucene/dev/trunk/lucene/analysis/icu/src/java/org/apache/lucene/collation/ICUCollationKeyFilterFactory.java:r1434109
  Merged /lucene/dev/trunk/lucene/analysis:r1434109
  Merged /lucene/dev/trunk/lucene/CHANGES.txt:r1434109
  Merged /lucene/dev/trunk/lucene/grouping:r1434109
  Merged /lucene/dev/trunk/lucene/misc:r1434109
  Merged /lucene/dev/trunk/lucene/sandbox:r1434109
  Merged /lucene/dev/trunk/lucene/highlighter:r1434109
  Merged /lucene/dev/trunk/lucene/NOTICE.txt:r1434109
  Merged /lucene/dev/trunk/lucene/codecs:r1434109
  Merged /lucene/dev/trunk/lucene/LICENSE.txt:r1434109
  Merged /lucene/dev/trunk/lucene/ivy-settings.xml:r1434109
  Merged /lucene/dev/trunk/lucene/SYSTEM_REQUIREMENTS.txt:r1434109
  Merged /lucene/dev/trunk/lucene/MIGRATE.txt:r1434109
  Merged /lucene/dev/trunk/lucene:r1434109
  Merged /lucene/dev/trunk/dev-tools:r1434109
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
package org.apache.solr.client.solrj.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.solr.client.solrj.ResponseParser;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.RequestWriter;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.ContentStream;
import org.apache.solr.common.util.NamedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpSolrServer extends SolrServer {
  private static final String UTF_8 = "UTF-8";
  private static final String DEFAULT_PATH = "/select";
  private static final long serialVersionUID = -946812319974801896L;
  /**
   * User-Agent String.
   */
  public static final String AGENT = "Solr[" + HttpSolrServer.class.getName()
      + "] 1.0";
  
  private static Logger log = LoggerFactory.getLogger(HttpSolrServer.class);
  
  /**
   * The URL of the Solr server.
   */
  protected String baseUrl;
  
  /**
   * Default value: null / empty.
   * <p/>
   * Parameters that are added to every request regardless. This may be a place
   * to add something like an authentication token.
   */
  protected ModifiableSolrParams invariantParams;
  
  /**
   * Default response parser is BinaryResponseParser
   * <p/>
   * This parser represents the default Response Parser chosen to parse the
   * response if the parser were not specified as part of the request.
   * 
   * @see org.apache.solr.client.solrj.impl.BinaryResponseParser
   */
  protected ResponseParser parser;
  
  /**
   * The RequestWriter used to write all requests to Solr
   * 
   * @see org.apache.solr.client.solrj.request.RequestWriter
   */
  protected RequestWriter requestWriter = new RequestWriter();
  
  private final HttpClient httpClient;
  
  private boolean followRedirects = false;
  
  private int maxRetries = 0;
  
  private boolean useMultiPartPost;
  private final boolean internalClient;

  
  /**
   * @param baseURL
   *          The URL of the Solr server. For example, "
   *          <code>http://localhost:8983/solr/</code>" if you are using the
   *          standard distribution Solr webapp on your local machine.
   */
  public HttpSolrServer(String baseURL) {
    this(baseURL, null, new BinaryResponseParser());
  }
  
  public HttpSolrServer(String baseURL, HttpClient client) {
    this(baseURL, client, new BinaryResponseParser());
  }
  
  public HttpSolrServer(String baseURL, HttpClient client, ResponseParser parser) {
    this.baseUrl = baseURL;
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }
    if (baseUrl.indexOf('?') >= 0) {
      throw new RuntimeException(
          "Invalid base url for solrj.  The base URL must not contain parameters: "
              + baseUrl);
    }
    
    if (client != null) {
      httpClient = client;
      internalClient = false;
    } else {
      internalClient = true;
      ModifiableSolrParams params = new ModifiableSolrParams();
      params.set(HttpClientUtil.PROP_MAX_CONNECTIONS, 128);
      params.set(HttpClientUtil.PROP_MAX_CONNECTIONS_PER_HOST, 32);
      params.set(HttpClientUtil.PROP_FOLLOW_REDIRECTS, followRedirects);
      httpClient =  HttpClientUtil.createClient(params);
    }
    
    this.parser = parser;
  }
  
  /**
   * Process the request. If
   * {@link org.apache.solr.client.solrj.SolrRequest#getResponseParser()} is
   * null, then use {@link #getParser()}
   * 
   * @param request
   *          The {@link org.apache.solr.client.solrj.SolrRequest} to process
   * @return The {@link org.apache.solr.common.util.NamedList} result
   * @throws IOException If there is a low-level I/O error.
   * 
   * @see #request(org.apache.solr.client.solrj.SolrRequest,
   *      org.apache.solr.client.solrj.ResponseParser)
   */
  @Override
  public NamedList<Object> request(final SolrRequest request)
      throws SolrServerException, IOException {
    ResponseParser responseParser = request.getResponseParser();
    if (responseParser == null) {
      responseParser = parser;
    }
    return request(request, responseParser);
  }
  
  public NamedList<Object> request(final SolrRequest request,
      final ResponseParser processor) throws SolrServerException, IOException {
    HttpRequestBase method = null;
    InputStream is = null;
    SolrParams params = request.getParams();
    Collection<ContentStream> streams = requestWriter.getContentStreams(request);
    String path = requestWriter.getPath(request);
    if (path == null || !path.startsWith("/")) {
      path = DEFAULT_PATH;
    }
    
    ResponseParser parser = request.getResponseParser();
    if (parser == null) {
      parser = this.parser;
    }
    
    // The parser 'wt=' and 'version=' params are used instead of the original
    // params
    ModifiableSolrParams wparams = new ModifiableSolrParams(params);
    if (parser != null) {
      wparams.set(CommonParams.WT, parser.getWriterType());
      wparams.set(CommonParams.VERSION, parser.getVersion());
    }
    if (invariantParams != null) {
      wparams.add(invariantParams);
    }
    params = wparams;
    
    int tries = maxRetries + 1;
    try {
      while( tries-- > 0 ) {
        // Note: since we aren't do intermittent time keeping
        // ourselves, the potential non-timeout latency could be as
        // much as tries-times (plus scheduling effects) the given
        // timeAllowed.
        try {
          if( SolrRequest.METHOD.GET == request.getMethod() ) {
            if( streams != null ) {
              throw new SolrException( SolrException.ErrorCode.BAD_REQUEST, "GET can't send streams!" );
            }
            method = new HttpGet( baseUrl + path + ClientUtils.toQueryString( params, false ) );
          }
          else if( SolrRequest.METHOD.POST == request.getMethod() ) {

            String url = baseUrl + path;
            boolean isMultipart = ( streams != null && streams.size() > 1 );

            LinkedList<NameValuePair> postParams = new LinkedList<NameValuePair>();
            if (streams == null || isMultipart) {
              HttpPost post = new HttpPost(url);
              post.setHeader("Content-Charset", "UTF-8");
              if (!this.useMultiPartPost && !isMultipart) {
                post.addHeader("Content-Type",
                    "application/x-www-form-urlencoded; charset=UTF-8");
              }

              List<FormBodyPart> parts = new LinkedList<FormBodyPart>();
              Iterator<String> iter = params.getParameterNamesIterator();
              while (iter.hasNext()) {
                String p = iter.next();
                String[] vals = params.getParams(p);
                if (vals != null) {
                  for (String v : vals) {
                    if (this.useMultiPartPost || isMultipart) {
                      parts.add(new FormBodyPart(p, new StringBody(v, Charset.forName("UTF-8"))));
                    } else {
                      postParams.add(new BasicNameValuePair(p, v));
                    }
                  }
                }
              }

              if (isMultipart) {
                for (ContentStream content : streams) {
                  String contentType = content.getContentType();
                  if(contentType==null) {
                    contentType = "application/octet-stream"; // default
                  }
                  parts.add(new FormBodyPart(content.getName(), 
                       new InputStreamBody(
                           content.getStream(), 
                           contentType, 
                           content.getName())));
                }
              }
              
              if (parts.size() > 0) {
                MultipartEntity entity = new MultipartEntity(HttpMultipartMode.STRICT);
                for(FormBodyPart p: parts) {
                  entity.addPart(p);
                }
                post.setEntity(entity);
              } else {
                //not using multipart
                post.setEntity(new UrlEncodedFormEntity(postParams, "UTF-8"));
              }

              method = post;
            }
            // It is has one stream, it is the post body, put the params in the URL
            else {
              String pstr = ClientUtils.toQueryString(params, false);
              HttpPost post = new HttpPost(url + pstr);

              // Single stream as body
              // Using a loop just to get the first one
              final ContentStream[] contentStream = new ContentStream[1];
              for (ContentStream content : streams) {
                contentStream[0] = content;
                break;
              }
              if (contentStream[0] instanceof RequestWriter.LazyContentStream) {
                post.setEntity(new InputStreamEntity(contentStream[0].getStream(), -1) {
                  @Override
                  public Header getContentType() {
                    return new BasicHeader("Content-Type", contentStream[0].getContentType());
                  }
                  
                  @Override
                  public boolean isRepeatable() {
                    return false;
                  }
                  
                });
              } else {
                post.setEntity(new InputStreamEntity(contentStream[0].getStream(), -1) {
                  @Override
                  public Header getContentType() {
                    return new BasicHeader("Content-Type", contentStream[0].getContentType());
                  }
                  
                  @Override
                  public boolean isRepeatable() {
                    return false;
                  }
                });
              }
              method = post;
            }
          }
          else {
            throw new SolrServerException("Unsupported method: "+request.getMethod() );
          }
        }
        catch( NoHttpResponseException r ) {
          method = null;
          if(is != null) {
            is.close();
          }
          // If out of tries then just rethrow (as normal error).
          if (tries < 1) {
            throw r;
          }
        }
      }
    } catch (IOException ex) {
      throw new SolrServerException("error reading streams", ex);
    }
    
    // XXX client already has this set, is this needed?
    method.getParams().setParameter(ClientPNames.HANDLE_REDIRECTS,
        followRedirects);
    method.addHeader("User-Agent", AGENT);
    
    InputStream respBody = null;
    
    try {
      // Execute the method.
      final HttpResponse response = httpClient.execute(method);
      int httpStatus = response.getStatusLine().getStatusCode();
      
      // Read the contents
      respBody = response.getEntity().getContent();
      
      // handle some http level checks before trying to parse the response
      switch (httpStatus) {
        case HttpStatus.SC_OK:
        case HttpStatus.SC_BAD_REQUEST:
        case HttpStatus.SC_CONFLICT:  // 409
          break;
        case HttpStatus.SC_MOVED_PERMANENTLY:
        case HttpStatus.SC_MOVED_TEMPORARILY:
          if (!followRedirects) {
            throw new SolrServerException("Server at " + getBaseURL()
                + " sent back a redirect (" + httpStatus + ").");
          }
          break;
        default:
          throw new SolrException(SolrException.ErrorCode.getErrorCode(httpStatus), "Server at " + getBaseURL()
              + " returned non ok status:" + httpStatus + ", message:"
              + response.getStatusLine().getReasonPhrase());
          
      }
      if (processor == null) {
        // no processor specified, return raw stream
        NamedList<Object> rsp = new NamedList<Object>();
        rsp.add("stream", respBody);
        return rsp;
      }
      String charset = EntityUtils.getContentCharSet(response.getEntity());
      NamedList<Object> rsp = processor.processResponse(respBody, charset);
      if (httpStatus != HttpStatus.SC_OK) {
        String reason = null;
        try {
          NamedList err = (NamedList) rsp.get("error");
          if (err != null) {
            reason = (String) err.get("msg");
            // TODO? get the trace?
          }
        } catch (Exception ex) {}
        if (reason == null) {
          StringBuilder msg = new StringBuilder();
          msg.append(response.getStatusLine().getReasonPhrase());
          msg.append("\n\n");
          msg.append("request: " + method.getURI());
          reason = java.net.URLDecoder.decode(msg.toString(), UTF_8);
        }
        throw new SolrException(
            SolrException.ErrorCode.getErrorCode(httpStatus), reason);
      }
      return rsp;
    } catch (ConnectException e) {
      throw new SolrServerException("Server refused connection at: "
          + getBaseURL(), e);
    } catch (SocketTimeoutException e) {
      throw new SolrServerException(
          "Timeout occured while waiting response from server at: "
              + getBaseURL(), e);
    } catch (IOException e) {
      throw new SolrServerException(
          "IOException occured when talking to server at: " + getBaseURL(), e);
    } finally {
      if (respBody != null && processor!=null) {
        try {
          respBody.close();
        } catch (Throwable t) {} // ignore
      }
    }
  }
  
  // -------------------------------------------------------------------
  // -------------------------------------------------------------------
  
  /**
   * Retrieve the default list of parameters are added to every request
   * regardless.
   * 
   * @see #invariantParams
   */
  public ModifiableSolrParams getInvariantParams() {
    return invariantParams;
  }
  
  public String getBaseURL() {
    return baseUrl;
  }
  
  public void setBaseURL(String baseURL) {
    this.baseUrl = baseURL;
  }
  
  public ResponseParser getParser() {
    return parser;
  }
  
  /**
   * Note: This setter method is <b>not thread-safe</b>.
   * 
   * @param processor
   *          Default Response Parser chosen to parse the response if the parser
   *          were not specified as part of the request.
   * @see org.apache.solr.client.solrj.SolrRequest#getResponseParser()
   */
  public void setParser(ResponseParser processor) {
    parser = processor;
  }
  
  /**
   * Return the HttpClient this instance uses.
   */
  public HttpClient getHttpClient() {
    return httpClient;
  }
  
  /**
   * HttpConnectionParams.setConnectionTimeout
   * 
   * @param timeout
   *          Timeout in milliseconds
   **/
  public void setConnectionTimeout(int timeout) {
    HttpClientUtil.setConnectionTimeout(httpClient, timeout);
  }
  
  /**
   * Set SoTimeout (read timeout). This is desirable
   * for queries, but probably not for indexing.
   * 
   * @param timeout
   *          Timeout in milliseconds
   **/
  public void setSoTimeout(int timeout) {
    HttpClientUtil.setSoTimeout(httpClient, timeout);
  }
  
  /**
   * Configure whether the client should follow redirects or not.
   * <p>
   * This defaults to false under the assumption that if you are following a
   * redirect to get to a Solr installation, something is misconfigured
   * somewhere.
   * </p>
   */
  public void setFollowRedirects(boolean followRedirects) {
    this.followRedirects = true;
    HttpClientUtil.setFollowRedirects(httpClient,  followRedirects);
  }
  
  /**
   * Allow server->client communication to be compressed. Currently gzip and
   * deflate are supported. If the server supports compression the response will
   * be compressed. This method is only allowed if the http client is of type
   * DefatulHttpClient.
   */
  public void setAllowCompression(boolean allowCompression) {
    if (httpClient instanceof DefaultHttpClient) {
      HttpClientUtil.setAllowCompression((DefaultHttpClient) httpClient, allowCompression);
    } else {
      throw new UnsupportedOperationException(
          "HttpClient instance was not of type DefaultHttpClient");
    }
  }
  
  /**
   * Set maximum number of retries to attempt in the event of transient errors.
   * <p>
   * Maximum number of retries to attempt in the event of transient errors.
   * Default: 0 (no) retries. No more than 1 recommended.
   * </p>
   * @param maxRetries
   *          No more than 1 recommended
   */
  public void setMaxRetries(int maxRetries) {
    if (maxRetries > 1) {
      log.warn("HttpSolrServer: maximum Retries " + maxRetries
          + " > 1. Maximum recommended retries is 1.");
    }
    this.maxRetries = maxRetries;
  }
  
  public void setRequestWriter(RequestWriter requestWriter) {
    this.requestWriter = requestWriter;
  }
  
  /**
   * Adds the documents supplied by the given iterator.
   * 
   * @param docIterator
   *          the iterator which returns SolrInputDocument instances
   * 
   * @return the response from the SolrServer
   */
  public UpdateResponse add(Iterator<SolrInputDocument> docIterator)
      throws SolrServerException, IOException {
    UpdateRequest req = new UpdateRequest();
    req.setDocIterator(docIterator);
    return req.process(this);
  }
  
  /**
   * Adds the beans supplied by the given iterator.
   * 
   * @param beanIterator
   *          the iterator which returns Beans
   * 
   * @return the response from the SolrServer
   */
  public UpdateResponse addBeans(final Iterator<?> beanIterator)
      throws SolrServerException, IOException {
    UpdateRequest req = new UpdateRequest();
    req.setDocIterator(new Iterator<SolrInputDocument>() {
      
      @Override
      public boolean hasNext() {
        return beanIterator.hasNext();
      }
      
      @Override
      public SolrInputDocument next() {
        Object o = beanIterator.next();
        if (o == null) return null;
        return getBinder().toSolrInputDocument(o);
      }
      
      @Override
      public void remove() {
        beanIterator.remove();
      }
    });
    return req.process(this);
  }
  
  /**
   * Close the {@link ClientConnectionManager} from the internal client.
   */
  @Override
  public void shutdown() {
    if (httpClient != null && internalClient) {
      httpClient.getConnectionManager().shutdown();
    }
  }

  /**
   * Set the maximum number of connections that can be open to a single host at
   * any given time. If http client was created outside the operation is not
   * allowed.
   */
  public void setDefaultMaxConnectionsPerHost(int max) {
    if (internalClient) {
      HttpClientUtil.setMaxConnectionsPerHost(httpClient, max);
    } else {
      throw new UnsupportedOperationException(
          "Client was created outside of HttpSolrServer");
    }
  }
  
  /**
   * Set the maximum number of connections that can be open at any given time.
   * If http client was created outside the operation is not allowed.
   */
  public void setMaxTotalConnections(int max) {
    if (internalClient) {
      HttpClientUtil.setMaxConnections(httpClient, max);
    } else {
      throw new UnsupportedOperationException(
          "Client was created outside of HttpSolrServer");
    }
  }
}
