/**
 * Copyright 2014 Daniel Makoto Iguchi <daniel.iguchi@gmail.com>
 * Copyright 2012-2015 Rafal Lewczuk <rafal.lewczuk@jitlogic.com>
 * <p/>
 * This is free software. You can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p/>
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p/>
 * You should have received a copy of the GNU General Public License along with
 * this software. If not, see <http://www.gnu.org/licenses/>.
 */
package com.jitlogic.zorka.core.integ.tcp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.jitlogic.zorka.common.util.ZorkaConfig;
import com.jitlogic.zorka.common.util.ZorkaLog;
import com.jitlogic.zorka.common.util.ZorkaLogger;

public class TcpSenderTask implements Runnable {

    /**
     * Logger
     */
    private static final ZorkaLog log = ZorkaLogger.getLog(TcpSenderTask.class);

    private String host;

    private InetAddress serverAddr;

    private int serverPort;

    private ConcurrentLinkedQueue<CheckResult> responseQueue;

    private long clock;

    private int maxBatchSize;

    private ZorkaConfig config;

    public TcpSenderTask(String host, InetAddress serverAddr, int serverPort, ConcurrentLinkedQueue<CheckResult> responseQueue, int maxBatchSize, ZorkaConfig config) {
        this.host = host;
        this.serverAddr = serverAddr;
        this.serverPort = serverPort;
        this.responseQueue = responseQueue;
        this.maxBatchSize = maxBatchSize;
        this.config = config;
    }

    @Override
    public void run() {
        log.debug(ZorkaLogger.ZAG_DEBUG, "TcpSender run...");
        Socket socket = null;
        try {
            socket = new Socket(serverAddr, serverPort);
            TcpRequest request = new TcpRequest(socket);

            /* copy cache */
            int endIndex = responseQueue.size();
            endIndex = (endIndex > maxBatchSize) ? maxBatchSize : endIndex;
            Iterator<CheckResult> iterator = responseQueue.iterator();

            if (iterator.hasNext()) {
                BulkResult result = new BulkResult();
                result.setHost(host);
                for (int i = 0; i < endIndex; i++) {
                    result.add(iterator.next());
                }

                log.debug(ZorkaLogger.ZAG_DEBUG, "TcpSender " + endIndex + " items cached");

                /* send message */
                String message = TcpUtils.createAgentData(result);

                request.send(message);
                log.debug(ZorkaLogger.ZAG_DEBUG, "TcpSender message sent: " + message);

                /* remove AgentData from cache */
                for (int count = 0; count < endIndex; count++) {
                    responseQueue.poll();
                }
                log.debug(ZorkaLogger.ZAG_DEBUG, "TcpSender " + endIndex + " items removed from cache");
            }
        } catch (IOException e) {
            log.error(ZorkaLogger.ZAG_ERRORS, "TcpSender send failure", e);
        } finally {
            log.debug(ZorkaLogger.ZAG_DEBUG, "TcpSender finished");
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
