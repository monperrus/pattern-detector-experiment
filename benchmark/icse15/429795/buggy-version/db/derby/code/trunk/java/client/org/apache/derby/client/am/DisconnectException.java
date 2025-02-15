/*

   Derby - Class org.apache.derby.client.am.DisconnectException

   Copyright (c) 2001, 2005 The Apache Software Foundation or its licensors, where applicable.

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

package org.apache.derby.client.am;

import org.apache.derby.shared.common.reference.SQLState;

public class DisconnectException extends SqlException {
    public DisconnectException(Agent agent, ClientMessageId msgid,
        Object[] args, SqlCode sqlcode, Throwable t)  {
        super(agent != null ? agent.logWriter_ : null, msgid,
            args, sqlcode, t);
        
        // make the call to close the streams and socket.
        if (agent != null) {
            agent.disconnectEvent();
        }
    }
    
    public DisconnectException(Agent agent, ClientMessageId msgid,
        Object[] args, SqlCode sqlcode) {
        this(agent, msgid, args, sqlcode, (Throwable)null);
    }

    public DisconnectException(Agent agent, ClientMessageId msgid, SqlCode sqlcode) {
        this(agent, msgid, (Object[]) null, sqlcode);
    }

        
    public DisconnectException(Agent agent, ClientMessageId msgid,
        Object[] args) {
        this(agent, msgid, args, SqlCode.disconnectError);
    }
    
    public DisconnectException(Agent agent, ClientMessageId msgid,
        Object[] args, Throwable t) {
        this(agent, msgid, args, SqlCode.disconnectError, (Throwable)t);
    }
    
    public DisconnectException(Agent agent, ClientMessageId msgid,
        Object arg1, Throwable t) {
        this(agent, msgid, new Object[] { arg1 }, t);
    }

    public DisconnectException(Agent agent, ClientMessageId msgid) {
        this(agent, msgid, (Object[])null);
    }
    
    public DisconnectException(Agent agent, ClientMessageId msgid, Object arg1) {
        this(agent, msgid, new Object[] { arg1 });
    }
        
    public DisconnectException(Agent agent, ClientMessageId msgid, Object arg1,
        Object arg2) {
        this(agent, msgid, new Object[] { arg1, arg2 });
    }
    
    public DisconnectException(Agent agent, SqlException e) {
        super(agent.logWriter_,
            new ClientMessageId(SQLState.DRDA_CONNECTION_TERMINATED),
            e.getMessage(), e);
    }
}
