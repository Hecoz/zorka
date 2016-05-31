/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.jitlogic.zorka.core.integ.tcp;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author omb
 */
public class BulkResult {

    private String host;

    private Map<String, StringBuilder> data = new HashMap<String, StringBuilder>();

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Map<String, StringBuilder> getData() {
        return data;
    }

    public void setData(Map<String, StringBuilder> data) {
        this.data = data;
    }

    public void add(CheckResult result) {
        if (result.getValue() == null) {
            return;
        }

        StringBuilder builder;
        if (!data.containsKey(result.getKey())) {
            builder = new StringBuilder();
            data.put(result.getKey(), builder);
        } else {
            builder = data.get(result.getKey());
            builder.append("|");
        }
        builder.append(result.getClock()).append("@").append(result.getValue());
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        for (Map.Entry<String, StringBuilder> entry : data.entrySet()) {
            str.append(host);
            str.append("\t");
            str.append(entry.getKey());
            str.append("\t");
            str.append(entry.getValue());
            str.append("\n");
        }
        return str.toString();
    }
}
