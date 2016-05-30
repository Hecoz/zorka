/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.jitlogic.zorka.core.integ.tcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author omb
 */
public class BulkResult {

    private String host;

    private Map<String, ArrayList<BulkResultItem>> data = new HashMap<String, ArrayList<BulkResultItem>>();

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Map<String, ArrayList<BulkResultItem>> getData() {
        return data;
    }

    public void setData(Map<String, ArrayList<BulkResultItem>> data) {
        this.data = data;
    }

    public void add(CheckResult result) {
        if (!data.containsKey(result.getKey())) {
            data.put(result.getKey(), new ArrayList<BulkResultItem>());
        }
        data.get(result.getKey()).add(new BulkResultItem(result.getValue(), result.getClock()));
    }

}
