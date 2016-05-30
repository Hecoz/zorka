/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.jitlogic.zorka.core.integ.elasticsearch;

import com.jitlogic.zorka.common.ZorkaService;
import com.jitlogic.zorka.common.util.ZorkaConfig;
import com.jitlogic.zorka.common.util.ZorkaLog;
import com.jitlogic.zorka.common.util.ZorkaLogger;
import com.jitlogic.zorka.core.ZorkaBshAgent;
import com.jitlogic.zorka.core.integ.QueryTranslator;
import com.jitlogic.zorka.core.integ.zabbix.ActiveCheckQueryItem;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.JestResult;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.indices.CreateIndex;
import io.searchbox.indices.IndicesExists;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * ElasticSearch Active Agent integrates Zorka with ElasticSearch server. It
 * handles pre-selected metrics, forwards to BSH agent and send the response to
 * an elasticsearch database.
 *
 * @author
 */
public class ElasticSearchAgent implements /*Runnable,*/ ZorkaService {

    /* Logger */
    private final ZorkaLog log = ZorkaLogger.getLog(ElasticSearchAgent.class);

    /* Agent Settings */
    private String prefix;
    private ZorkaConfig config;

    /**
     * Interval between sender cycles
     */
    private long senderInterval;
    private int maxBatchSize;
    private int maxCacheSize;

    /* Connection Settings */
    private String address;
    private String index;
    private JestClient elasticsearch;

    /* Scheduler Management */
    private ScheduledExecutorService scheduler;
    private HashMap<ActiveCheckQueryItem, ScheduledFuture<?>> runningTasks;
    private ConcurrentLinkedQueue<ElasticSearchCheckResult> resultsQueue;
    private ScheduledFuture<?> senderTask;

    /* BSH agent */
    private QueryTranslator translator;
    private ZorkaBshAgent agent;

    /**
     * Creates zabbix active agent.
     */
    public ElasticSearchAgent(ZorkaConfig config, ZorkaBshAgent agent, QueryTranslator translator, ScheduledExecutorService scheduledExecutorService) {
        this.prefix = "elasticsearch";
        this.config = config;
        this.scheduler = scheduledExecutorService;
        this.agent = agent;
        this.translator = translator;
        setup();
    }

    protected void setup() {
        log.debug(ZorkaLogger.ZAG_DEBUG, "ElasticSearch setup...");

        //ElasticSearch Index
        index = config.stringCfg("zorka.hostname", "zorka");

        /* ElasticSearch Server */
        address = config.stringCfg(prefix + ".server.addr", "http://127.0.0.1:9200");
        senderInterval = config.intCfg(prefix + ".sender.interval", 60);
        maxBatchSize = config.intCfg(prefix + ".batch.size", 10);
        maxCacheSize = config.intCfg(prefix + ".cache.size", 150);
        log.info(ZorkaLogger.ZAG_INFO, "ElasticSearch Agent will send up to " + maxBatchSize + " metrics every "
                + senderInterval + " seconds. Agent will persist up to " + maxCacheSize + " metrics per " + (senderInterval * 2)
                + " seconds, exceeding records will be discarded.");

        /* scheduler's infra */
        runningTasks = new HashMap<ActiveCheckQueryItem, ScheduledFuture<?>>();
        resultsQueue = new ConcurrentLinkedQueue<ElasticSearchCheckResult>();
    }

    public void start() {
        try {
            log.debug(ZorkaLogger.ZAG_DEBUG, "ElasticSearch start...");
            JestClientFactory factory = new JestClientFactory();
            factory.setHttpClientConfig(new HttpClientConfig.Builder(address)
                    .multiThreaded(true).build());
            JestClient client = factory.getObject();

            boolean indexExists = client.execute(new IndicesExists.Builder(index).build()).isSucceeded();
            if (!indexExists) {
                JestResult result = client.execute(new CreateIndex.Builder(index).build());
                if (!result.isSucceeded()) {
                    log.error(ZorkaLogger.ZAG_ERRORS, "Failed to create agent index in elasticsearch");
                }
            }
        } catch (IOException ex) {
            log.error(ZorkaLogger.ZAG_ERRORS, "Failed to create agent index in elasticsearch");
        }
    }

    @SuppressWarnings("deprecation")
    public void stop() {
        log.debug(ZorkaLogger.ZAG_DEBUG, "ElasticSearch stop...");
        if (elasticsearch != null) {
            elasticsearch.shutdownClient();
        }
    }

    public void restart() {
        log.debug(ZorkaLogger.ZAG_DEBUG, "ElasticSearch restart...");
        stop();
        setup();
        start();
    }

    @Override
    public void shutdown() {
        log.info(ZorkaLogger.ZAG_CONFIG, "Shutting down " + prefix + " agent ...");
        stop();
    }

    private void scheduleTasks(ArrayList<ActiveCheckQueryItem> tasksToInsert) {
        log.debug(ZorkaLogger.ZAG_DEBUG, "ElasticSearch - schedule Tasks: " + tasksToInsert.toString());

        // Insert Tasks
        for (ActiveCheckQueryItem task : tasksToInsert) {
            ElasticSearchTask elasticActiveTask = new ElasticSearchTask(index, task, agent, translator, resultsQueue);
            ScheduledFuture<?> taskHandler = scheduler.scheduleAtFixedRate(elasticActiveTask, 5, task.getDelay(), TimeUnit.SECONDS);
            log.debug(ZorkaLogger.ZAG_DEBUG, "ElasticSearch - task: " + task.toString());
            runningTasks.put(task, taskHandler);
        }
        log.debug(ZorkaLogger.ZAG_DEBUG, "ElasticSearch - scheduled tasks: " + tasksToInsert.size());
    }

    private void scheduleTasks() {
        ElasticSearchSenderTask sender = new ElasticSearchSenderTask(elasticsearch, index, resultsQueue, maxBatchSize, config);
        senderTask = scheduler.scheduleAtFixedRate(sender, senderInterval, senderInterval, TimeUnit.SECONDS);

        ElasticSearchCleanerTask cleaner = new ElasticSearchCleanerTask(resultsQueue, maxCacheSize);
        senderTask = scheduler.scheduleAtFixedRate(cleaner, senderInterval * 2, senderInterval * 2, TimeUnit.SECONDS);
    }
}
