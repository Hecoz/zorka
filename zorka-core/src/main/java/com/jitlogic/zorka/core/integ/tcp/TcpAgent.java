/**
 * Copyright 2014 Daniel Makoto Iguchi <daniel.iguchi@gmail.com>
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

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.jitlogic.zorka.common.ZorkaService;
import com.jitlogic.zorka.core.integ.QueryTranslator;
import com.jitlogic.zorka.common.stats.AgentDiagnostics;
import com.jitlogic.zorka.common.util.ZorkaConfig;
import com.jitlogic.zorka.common.util.ZorkaLog;
import com.jitlogic.zorka.common.util.ZorkaLogger;
import com.jitlogic.zorka.core.ZorkaBshAgent;

/**
 * Zabbix Active Agent integrates Zorka with Zabbix server. It handles incoming
 * zabbix requests and forwards to BSH agent.
 *
 * @author
 */
public class TcpAgent implements ZorkaService {

    /* Logger */
    private final ZorkaLog log = ZorkaLogger.getLog(TcpAgent.class);
    private volatile boolean running;


    /* Agent Settings */
    private String prefix;
    private ZorkaConfig config;

    /**
     * Hostname agent advertises itself to zabbix.
     */
    private String agentHost;
    private String activeIpPort;

    /**
     * Interval between fetching new item list from Zabbix.
     */
    //private long activeCheckInterval;
    /**
     * Interval between sender cycles
     */
    private long senderInterval;

    private int maxBatchSize;

    private int maxCacheSize;


    /* Connection Settings */
    private InetAddress activeAddr;
    private String defaultAddr;
    private int activePort;
    private int defaultPort;
    private Socket socket;


    /* Scheduler Management */
    private ScheduledExecutorService scheduler;
    private HashMap<CheckQueryItem, ScheduledFuture<?>> runningTasks;
    private ConcurrentLinkedQueue<CheckResult> resultsQueue;
    private ScheduledFuture<?> senderTask;

    /* BSH agent */
    private ZorkaBshAgent agent;

    /* Query translator */
    protected QueryTranslator translator;

    /**
     * Creates zabbix active agent.
     */
    public TcpAgent(ZorkaConfig config, ZorkaBshAgent agent, QueryTranslator translator, ScheduledExecutorService scheduledExecutorService) {
        this.prefix = "tcp";
        this.config = config;
        this.defaultPort = 10051;
        this.defaultAddr = "127.0.0.1:" + this.defaultPort;

        this.scheduler = scheduledExecutorService;

        this.agent = agent;
        this.translator = translator;

        setup();
    }

    protected void setup() {
        log.debug(ZorkaLogger.ZAG_DEBUG, "Tcp setup...");

        /* Zabbix Server IP:Port */
        activeIpPort = config.stringCfg(prefix + ".server.addr", defaultAddr);
        String[] ipPort = activeIpPort.split(":");
        String activeIp = ipPort[0];
        activePort = (ipPort.length < 2 || ipPort[1].length() == 0) ? defaultPort : Integer.parseInt(ipPort[1]);

        /* Zabbix Server address */
        try {
            activeAddr = InetAddress.getByName(activeIp.trim());
        } catch (UnknownHostException e) {
            log.error(ZorkaLogger.ZAG_ERRORS, "Cannot parse " + prefix + ".server.addr in zorka.properties", e);
            AgentDiagnostics.inc(AgentDiagnostics.CONFIG_ERRORS);
        }

        agentHost = config.stringCfg("zorka.hostname", "zorka");
        senderInterval = config.intCfg(prefix + ".sender.interval", 60);
        maxBatchSize = config.intCfg(prefix + ".batch.size", 10);
        maxCacheSize = config.intCfg(prefix + ".cache.size", 150);
        log.info(ZorkaLogger.ZAG_INFO, "Tcp Agent (" + agentHost + ") will send up to " + maxBatchSize + " metrics every "
                + senderInterval + " seconds. Agent will persist up to " + maxCacheSize + " metrics per " + (senderInterval * 2)
                + " seconds, exceeding records will be discarded.");

        /* scheduler's infra */
        runningTasks = new HashMap<CheckQueryItem, ScheduledFuture<?>>();
        resultsQueue = new ConcurrentLinkedQueue<CheckResult>();
    }

    public void start() {
        log.debug(ZorkaLogger.ZAG_DEBUG, "Tcp start...");

        if (!running) {
            try {
                socket = new Socket(activeAddr, activePort);
                log.info(ZorkaLogger.ZAG_ERRORS, "Successfuly connected to " + activeIpPort);
            } catch (IOException e) {
                log.error(ZorkaLogger.ZAG_ERRORS, "Failed to connect to " + activeIpPort + ". Will try to connect later.", e);
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        socket = null;
                    }
                }

                log.debug(ZorkaLogger.ZAG_DEBUG, "Tcp Scheduling sender task");
                scheduleTasks();

                CheckQueryItem item = new CheckQueryItem();
                item.setKey("jvm.heaputil[\"HeapMemoryUsage\"]");
                item.setDelay(5);
                ArrayList<CheckQueryItem> itens = new ArrayList<CheckQueryItem>();
                itens.add(item);
                scheduleTasks(itens);

                running = true;
            }
        }

    }

    @SuppressWarnings("deprecation")
    public void stop() {
        log.debug(ZorkaLogger.ZAG_DEBUG, "Tcp stop...");
        if (running) {
            running = false;
            try {
                log.debug(ZorkaLogger.ZAG_DEBUG, "Tcp cancelling sender task...");
                senderTask.cancel(true);

                log.debug(ZorkaLogger.ZAG_DEBUG, "Tcp cancelling all ZorkaBsh tasks...");
                for (Map.Entry<CheckQueryItem, ScheduledFuture<?>> e : runningTasks.entrySet()) {
                    e.getValue().cancel(true);
                }
                runningTasks.clear();

                log.debug(ZorkaLogger.ZAG_DEBUG, "Tcp clearing dataQueue...");
                resultsQueue.clear();

                log.debug(ZorkaLogger.ZAG_DEBUG, "Tcp closing socket...");
                if (socket != null) {
                    socket.close();
                    socket = null;
                }
            } catch (IOException e) {
                log.error(ZorkaLogger.ZAG_ERRORS, "I/O error in zabbix core main loop: " + e.getMessage());
            }
        }
    }

    public void restart() {
        log.debug(ZorkaLogger.ZAG_DEBUG, "Tcp restart...");
        setup();
        start();
    }

    @Override
    public void shutdown() {
        log.info(ZorkaLogger.ZAG_CONFIG, "Shutting down " + prefix + " agent ...");
        stop();
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            socket = null;
        }
    }

    private void scheduleTasks() {
        TcpSenderTask sender = new TcpSenderTask(agentHost, activeAddr, activePort, resultsQueue, maxBatchSize, config);
        senderTask = scheduler.scheduleAtFixedRate(sender, senderInterval, senderInterval, TimeUnit.SECONDS);

        TcpCleanerTask cleaner = new TcpCleanerTask(resultsQueue, maxCacheSize);
        senderTask = scheduler.scheduleAtFixedRate(cleaner, senderInterval * 2, senderInterval * 2, TimeUnit.SECONDS);
    }

    private void scheduleTasks(ArrayList<CheckQueryItem> tasks) {

        log.debug(ZorkaLogger.ZAG_DEBUG, "Tcp - schedule Tasks: " + tasks.toString());

        // Insert Tasks
        for (CheckQueryItem task : tasks) {
            TcpTask zabbixActiveTask = new TcpTask(agentHost, task, agent, translator, resultsQueue);
            ScheduledFuture<?> taskHandler = scheduler.scheduleAtFixedRate(zabbixActiveTask, 5, task.getDelay(), TimeUnit.SECONDS);
            log.debug(ZorkaLogger.ZAG_DEBUG, "Tcp - task: " + task.toString());
            runningTasks.put(task, taskHandler);
        }
        log.debug(ZorkaLogger.ZAG_DEBUG, "Tcp - scheduled tasks: " + tasks.size());
    }

}
