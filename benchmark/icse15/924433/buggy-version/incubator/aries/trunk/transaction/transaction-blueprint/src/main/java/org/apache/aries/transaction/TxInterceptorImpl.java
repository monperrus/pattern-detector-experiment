/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.aries.transaction;

import java.lang.reflect.Method;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

import org.apache.aries.blueprint.Interceptor;
import org.apache.aries.transaction.exception.TransactionRollbackException;
import org.osgi.service.blueprint.reflect.ComponentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TxInterceptorImpl implements Interceptor {
    private static final Logger LOGGER =
        LoggerFactory.getLogger(TxInterceptorImpl.class);

    private TransactionManager tm;
    private TxComponentMetaDataHelper metaDataHelper;

    public int getRank()
    {
      // TODO Auto-generated method stub
      return 0;
    }

    public void postCallWithException(ComponentMetadata cm, Method m,
        Exception ex, Object preCallToken)
     {
       if (preCallToken instanceof TransactionToken)
       {
         final TransactionToken token = (TransactionToken)preCallToken;
         try { 
             Transaction tran = token.getActiveTransaction();
             if (tran != null) {
                 Class<?> exceptionClass = ex.getClass();
                 boolean isAppException = false;

                 for (Class<?> cls : m.getExceptionTypes()) {
                     isAppException = cls.isAssignableFrom(exceptionClass);
                     
                     if (isAppException)
                         break;
                 }

                 if (!isAppException)
                     tran.setRollbackOnly();
             }

             token.getTransactionStrategy().finish(tm, token);
         }
         catch (Exception e)
         {
           // we do not throw the exception since there already is one, but we need to log it
           LOGGER.error("An exception has occured.", e);
         }
       } else {
         // TODO: what now?
       }
    }

    public void postCallWithReturn(ComponentMetadata cm, Method m,
        Object returnType, Object preCallToken) throws Exception
    {
      if (preCallToken instanceof TransactionToken)
      {
        final TransactionToken token = (TransactionToken)preCallToken;
        try { 
           token.getTransactionStrategy().finish(tm, token);
        }
        catch (Exception e)
        {
          LOGGER.error("An exception has occured.", e);
          throw new TransactionRollbackException(e);
        }
      }
      else {
        // TODO: what now?
      }
    }

    public Object preCall(ComponentMetadata cm, Method m,
        Object... parameters) throws Throwable  {
      final String methodName = m.getName();
      final String strategy = metaDataHelper.getComponentMethodTxStrategy(cm, methodName);
      
      TransactionStrategy txStrategy = TransactionStrategy.fromValue(strategy);
      
      if (LOGGER.isDebugEnabled())
          LOGGER.debug("Method: " + m + ", has transaction strategy: " + txStrategy);

      return txStrategy.begin(tm);
    }

    public final void setTransactionManager(TransactionManager manager)
    {
      tm = manager;
    }

    public final void setTxMetaDataHelper(TxComponentMetaDataHelper transactionEnhancer)
    {
      this.metaDataHelper = transactionEnhancer;
    }
}
