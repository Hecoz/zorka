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
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.jitlogic.zorka.core.integ.QueryTranslator;
import com.jitlogic.zorka.common.util.ZorkaLog;
import com.jitlogic.zorka.common.util.ZorkaLogger;
import com.jitlogic.zorka.core.ZorkaBshAgent;
import com.jitlogic.zorka.core.integ.ZorkaRequestHandler;

public class TcpTask implements Runnable, ZorkaRequestHandler {

    /* Logger */
    private final ZorkaLog log = ZorkaLogger.getLog(TcpTask.class);

    private final String agentHost;
    private final CheckQueryItem item;
    private final ZorkaBshAgent agent;
    //private final QueryTranslator translator;
    private final ConcurrentLinkedQueue<CheckResult> responseQueue;

    private long clock;

    public TcpTask(String agentHost, CheckQueryItem item, ZorkaBshAgent agent, QueryTranslator translator, ConcurrentLinkedQueue<CheckResult> responseQueue) {
        this.agentHost = agentHost;
        this.item = item;
        this.agent = agent;
        //this.translator = translator;
        this.responseQueue = responseQueue;
    }

    @Override
    public void run() {
        log.debug(ZorkaLogger.ZAG_DEBUG, "Running task: " + item.getKey());

        //String expr = translator.translate(item.getExpression());
        String expr = item.getExpression();
        log.debug(ZorkaLogger.ZAG_DEBUG, "Translated task: " + expr);

        clock = (new Date()).getTime() / 1000L;
        agent.exec(expr, this);
    }

    @Override
    public String getReq() throws IOException {
        return null;
    }

    @Override
    public void handleResult(Object rslt) {
        String key = item.getKey();
        String value = serialize(rslt);
        log.debug(ZorkaLogger.ZAG_DEBUG, "Task response: " + key + " -> " + value);

        if (value != null) {
            CheckResult response = new CheckResult();
            response.setKey(key);
            response.setValue(value);
            response.setClock(clock);
            responseQueue.offer(response);
            log.debug(ZorkaLogger.ZAG_DEBUG, "Cache size: " + responseQueue.size());
        }
    }

    private String serialize(Object obj) {
        return obj != null ? obj.toString() : null;
    }

    @Override
    public void handleError(Throwable e) {
        //
    }

}
