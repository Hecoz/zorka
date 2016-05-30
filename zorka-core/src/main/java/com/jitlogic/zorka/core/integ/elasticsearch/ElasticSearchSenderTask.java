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
package com.jitlogic.zorka.core.integ.elasticsearch;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.jitlogic.zorka.common.util.ZorkaConfig;
import com.jitlogic.zorka.common.util.ZorkaLog;
import com.jitlogic.zorka.common.util.ZorkaLogger;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.core.Bulk;
import io.searchbox.core.Index;
import java.io.IOException;

public class ElasticSearchSenderTask implements Runnable {

    /**
     * Logger
     */
    private static final ZorkaLog log = ZorkaLogger.getLog(ElasticSearchSenderTask.class);

    private final JestClient elasticsearch;

    private final String index;

    private final ConcurrentLinkedQueue<ElasticSearchCheckResult> responseQueue;

    //private long clock;
    private final int maxBatchSize;

    private final ZorkaConfig config;

    public ElasticSearchSenderTask(JestClient elasticsearch, String index, ConcurrentLinkedQueue<ElasticSearchCheckResult> responseQueue, int maxBatchSize, ZorkaConfig config) {
        this.elasticsearch = elasticsearch;
        this.index = index;
        this.responseQueue = responseQueue;
        this.maxBatchSize = maxBatchSize;
        this.config = config;
    }

    @Override
    public void run() {
        log.debug(ZorkaLogger.ZAG_DEBUG, "ElasticSearchSender run...");
        Bulk.Builder bulkRequest = new Bulk.Builder();
        try {
            //clock = (new Date()).getTime() / 1000L;

            /* copy cache */
            int endIndex = responseQueue.size();
            endIndex = (endIndex > maxBatchSize) ? maxBatchSize : endIndex;

            Iterator<ElasticSearchCheckResult> iterator = responseQueue.iterator();
            for (int i = 0; i < endIndex; i++) {
                ElasticSearchCheckResult result = iterator.next();
                bulkRequest.addAction(new Index.Builder(result).index(index)
                        .type(result.getKey()).id(Long.toString(result.getClock())).build());
            }

            log.debug(ZorkaLogger.ZAG_DEBUG, "ElasticSearchSender " + endIndex + " items cached");

            JestResult bulkResponse = elasticsearch.execute(bulkRequest.build());
            if (bulkResponse.isSucceeded()) {
                /* remove AgentData from cache */
                for (int count = 0; count < endIndex; count++) {
                    responseQueue.poll();
                }
                log.debug(ZorkaLogger.ZAG_DEBUG, "ElasticSearchSender " + endIndex + " items removed from cache");
            }
        } catch (IOException ex) {
            log.error(ZorkaLogger.ZAG_ERRORS, "ElasticSearchSender bulk index failed", ex);
        } finally {
            log.debug(ZorkaLogger.ZAG_DEBUG, "ElasticSearchSender finished");
        }
    }

}
