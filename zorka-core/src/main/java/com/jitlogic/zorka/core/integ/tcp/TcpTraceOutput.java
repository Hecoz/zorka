/**
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.jitlogic.zorka.common.stats.AgentDiagnostics;
import com.jitlogic.zorka.common.tracedata.*;
import com.jitlogic.zorka.common.util.ZorkaAsyncThread;
import com.jitlogic.zorka.common.util.ZorkaLog;
import com.jitlogic.zorka.common.util.ZorkaLogger;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

/**
 * Tracer output sending data to remote ZICO collector. It automatically handles reconnections and
 * retransmissions, lumps data into bigger packets for better throughput, keeps track of symbols
 * already sent etc.
 *
 * @author rafal.lewczuk@jitlogic.com
 */
public class TcpTraceOutput extends ZorkaAsyncThread<SymbolicRecord> implements TraceStreamOutput {

    private static ZorkaLog log = ZorkaLogger.getLog(TcpTraceOutput.class);

    SymbolRegistry symbolRegistry;
    MetricsRegistry metricsRegistry;

    /**
     * Hostname this client will advertise itself as
     */
    private String hostname;

    /**
     * Connection Settings
     */
    private String serverAddr;
    private int serverPort;
    private Socket socket;

    /**
     * Output buffer - compatibility purposes
     */
    private ByteArrayOutputStream os;

    /**
     * Maximum retransmission retries
     */
    private int retries;

    /**
     * Retry wait timing parameters
     */
    private long retryTime, retryTimeExp;

    /**
     * Suggested maximum packet size
     */
    private long packetSize;

    /**
     * Package to generate performance counters for
     */
    private String performanceTargetPackage;

    /**
     * Maximum length for json large fields
     */
    private int maxStringLength;

    /**
     * Creates trace output object.
     *
     * @param metricsRegistry
     * @param symbolRegistry
     *
     * @param addr            host name or IP address of remote ZICO collector
     * @param port            port number of remote ZICO collector
     * @param hostname        name this client will advertise itself when connecting to ZICO
     *                        collector
     * @param qlen            output queue length
     * @param packetSize      maximum (recommended) packet size (actual packets might exceed this a
     *                        bit)
     * @throws IOException when connection to remote server cannot be established;
     */
    public TcpTraceOutput(
            SymbolRegistry symbolRegistry, MetricsRegistry metricsRegistry, String addr, int port,
            String hostname,
            int qlen, int packetSize, int retries, long retryTime, long retryTimeExp,
            int timeout, int interval, String performanceTargetPackage, int maxStringLength) throws
            IOException {

        super("tcp-output", qlen, packetSize, interval);

        log.debug(ZorkaLogger.ZAG_DEBUG, "Configured tracer output: host=" + hostname
                + ", addr=" + addr
                + ", port=" + port
                + ", qlen=" + qlen
                + ", packetSize=" + packetSize
                + ", interval=" + interval);

        this.symbolRegistry = symbolRegistry;
        this.metricsRegistry = metricsRegistry;

        this.serverAddr = addr;
        this.serverPort = port;
        this.hostname = hostname;

        this.packetSize = packetSize;
        this.retries = retries;
        this.retryTime = retryTime;
        this.retryTimeExp = retryTimeExp;

        this.performanceTargetPackage = performanceTargetPackage;
        this.maxStringLength = maxStringLength;

        /* compatibility purposes */
        this.os = new ByteArrayOutputStream();
    }

    @Override
    public OutputStream getOutputStream() {
        /* compatibility purposes */
        return os;
    }

    @Override
    public boolean submit(SymbolicRecord obj) {
        boolean submitted = false;
        try {
            submitted = submitQueue.offer(obj, 1, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        }

        if (!submitted) {
            AgentDiagnostics.inc(AgentDiagnostics.ZICO_PACKETS_DROPPED);
        }

        return submitted;
    }

    @Override
    protected void process(List<SymbolicRecord> records) {
        long clock;
        long rt = retryTime;

        // packet: avoid losing records taken from Queue
        List<SymbolicRecord> packet = new ArrayList<SymbolicRecord>();
        packet.addAll(records);

        for (int i = 0; i < retries; i++) {
            try {
                clock = (new Date()).getTime() / 1000L;

                log.debug(ZorkaLogger.ZTR_TRACER_DBG, "Opening connection to " + hostname
                        + " -> " + serverAddr + ":" + serverPort);
                socket = new Socket(serverAddr, serverPort);

                OutputStream out = socket.getOutputStream();
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(out,
                        StandardCharsets.UTF_8));
                for (SymbolicRecord symbolicRecord : packet) {
                    printRecord(writer, symbolicRecord);
                }
                writer.flush();
                out.flush();

                //AgentDiagnostics.inc(AgentDiagnostics.ZICO_PACKETS_SENT);

                /* Close Connection */
                writer.close();
                out.close();
                return;

            } catch (UnknownHostException e) {
                /* Error caused by unkown host -> failure with no retries */
                log.error(ZorkaLogger.ZCL_STORE, "Error sending trace record: " + e
                        + ". Trace will be lost.");
                AgentDiagnostics.inc(AgentDiagnostics.ZICO_PACKETS_LOST);
                return;
            } catch (IOException e) {
                /* Error while sending */
                log.error(ZorkaLogger.ZCL_STORE, "Error sending trace record: " + e
                        + ". Resetting connection.");
                close();
                AgentDiagnostics.inc(AgentDiagnostics.ZICO_RECONNECTS);
            } finally {
                close();
            }

            /* Wait before retry */
            try {
                log.debug(ZorkaLogger.ZTR_TRACER_DBG, "Will retry (wait=" + rt + ")");
                Thread.sleep(rt);
            } catch (InterruptedException e) {
                log.debug(ZorkaLogger.ZTR_TRACER_DBG, e.getMessage());
            }

            rt *= retryTimeExp;
        }

        AgentDiagnostics.inc(AgentDiagnostics.ZICO_PACKETS_LOST);
        log.error(ZorkaLogger.ZCL_STORE,
                "Too many errors while trying to send trace. Giving up. Trace will be lost.");
    }

    private void printRecord(PrintWriter writer, SymbolicRecord rec) throws CharacterCodingException {
        if (rec instanceof TraceRecord) {
            new TraceRecordOutputPrinter(hostname, symbolRegistry, writer, performanceTargetPackage,
                    maxStringLength).print((TraceRecord) rec);
        }
    }

    @Override
    public void open() {
    }

    @Override
    public synchronized void close() {
        log.debug(ZorkaLogger.ZAG_DEBUG, "Closing connection: " + hostname
                + " -> " + serverAddr + ":" + serverPort);

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

    @Override
    public void start() {
        log.debug(ZorkaLogger.ZAG_DEBUG, "### Start()");
        super.start();
    }

    @Override
    public void run() {
        log.debug(ZorkaLogger.ZAG_DEBUG, "### run()");
        super.run();
    }
}
