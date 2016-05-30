/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.jitlogic.zorka.core.integ.zabbix;

import com.jitlogic.zorka.common.util.ZorkaConfig;
import com.jitlogic.zorka.common.util.ZorkaLogger;
import com.jitlogic.zorka.core.ZorkaBshAgent;
import com.jitlogic.zorka.core.integ.QueryTranslator;
import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;

/**
 *
 * @author omb
 */
public class ZabbixActiveAgentFixed extends ZabbixActiveAgent {

    public ZabbixActiveAgentFixed(ZorkaConfig config, ZorkaBshAgent agent, QueryTranslator translator, ScheduledExecutorService scheduledExecutorService) {
        super(config, agent, translator, scheduledExecutorService);
    }

    @Override
    public void run() {
        log.debug(ZorkaLogger.ZAG_DEBUG, "Tcp run...");
        log.debug(ZorkaLogger.ZAG_DEBUG, "Tcp Scheduling sender task");
        scheduleTasks();

        ActiveCheckQueryItem item = new ActiveCheckQueryItem();
        item.setKey("jvm.heaputil[\"HeapMemoryUsage\"]");
        item.setDelay(5);
        ArrayList<ActiveCheckQueryItem> itens = new ArrayList<ActiveCheckQueryItem>();
        itens.add(item);

        ActiveCheckResponse response = new ActiveCheckResponse();
        response.setData(itens);
        scheduleTasks(response);
    }

}
