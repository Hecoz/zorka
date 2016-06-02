/**
 * Copyright 2014 Daniel Makoto Iguchi <daniel.iguchi@gmail.com>
 * Copyright 2012-2015 Rafal Lewczuk <rafal.lewczuk@jitlogic.com>
 * <p/>
 * This is free software. You can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * <p/>
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License along with this software. If
 * not, see <http://www.gnu.org/licenses/>.
 */
package com.jitlogic.zorka.core.integ.tcp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import com.jitlogic.zorka.common.util.ZorkaLog;
import com.jitlogic.zorka.common.util.ZorkaLogger;

public class TcpSenderTask implements Runnable {

    /**
     * Logger
     */
    private static final ZorkaLog LOG = ZorkaLogger.getLog(TcpSenderTask.class);

    private final InetAddress serverAddr;

    private final int serverPort;

    private final BulkResult results;

    private final int maxRetries;

    private int retries = 0;

    public TcpSenderTask(InetAddress serverAddr, int serverPort, int maxRetries, BulkResult results) {
        this.serverAddr = serverAddr;
        this.serverPort = serverPort;
        this.maxRetries = maxRetries;
        this.results = results;
    }

    @Override
    public void run() {
        LOG.debug(ZorkaLogger.ZAG_DEBUG, "TcpSender run...");
        Socket socket = null;
        try {
            socket = new Socket(serverAddr, serverPort);
            TcpRequest request = new TcpRequest(socket);
            request.send(results.retrieve());
            retries = 0;
        } catch (IOException e) {
            LOG.error(ZorkaLogger.ZAG_ERRORS, "TcpSender send failure", e);
            
            //clear buffer after max retries
            if (++retries >= maxRetries) {
                results.clear();
            }
        } finally {
            LOG.debug(ZorkaLogger.ZAG_DEBUG, "TcpSender finished");
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    socket = null;
                }
            }
        }
    }

}
