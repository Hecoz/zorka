/**
 * Copyright 2012-2015 Rafal Lewczuk <rafal.lewczuk@jitlogic.com>
 *
 * ZORKA is free software. You can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * ZORKA is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * ZORKA. If not, see <http://www.gnu.org/licenses/>.
 */
package com.jitlogic.zorka.core.integ.tcp;

import com.jitlogic.zorka.core.integ.zabbix.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import com.jitlogic.zorka.common.stats.AgentDiagnostics;
import com.jitlogic.zorka.common.util.ZorkaLog;
import com.jitlogic.zorka.common.util.ZorkaLogger;

/**
 * Zabbix Active Request is used by Zabbix Active Agent to send a request to a
 * Zabbix Server and receive a response
 */
public class TcpRequest {

    /**
     * Logger
     */
    private static final ZorkaLog log = ZorkaLogger.getLog(TcpRequest.class);

    /**
     * Socket with established server connection.
     */
    private Socket socket;

    /**
     * Standard constructor
     *
     * @param socket open socket (result from ServerSocket.accept())
     */
    public TcpRequest(Socket socket) {
        this.socket = socket;
        AgentDiagnostics.inc(AgentDiagnostics.TCP_REQUESTS);
    }

    /**
     * Sends message
     *
     * @param message response value
     * @throws IOException if I/O error occurs
     */
    public void send(String message) throws IOException {
        byte[] buf = TcpUtils.encode(message);
        //log.debug(ZorkaLogger.ZAG_DEBUG, "Zorka send: " + new String(buf));
        OutputStream out = socket.getOutputStream();
        out.write(buf);
        out.flush();
    }

}
