/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.jitlogic.zorka.core.integ.tcp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author omb
 */
public class BulkResult {

    private final String host;

    private final Map<String, StringBuffer> data = new ConcurrentHashMap<String, StringBuffer>();

    public BulkResult(String host) {
        this.host = host;
    }

    public String getHost() {
        return host;
    }

    public void add(CheckResult result) {
        if (result.getValue() == null) {
            return;
        }

        StringBuffer buffer;
        if (!data.containsKey(result.getKey())) {
            buffer = new StringBuffer();
            data.put(result.getKey(), buffer);
        } else {
            buffer = data.get(result.getKey());
            buffer.append("|");
        }
        buffer.append(result.getClock()).append("@").append(result.getValue());
    }

    public String retrieve() {
        String msg = this.toString();
        this.clear();
        return msg;
    }

    public void clear() {
        for (StringBuffer buffer : data.values()) {
            buffer.delete(0, buffer.length());
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        for (Map.Entry<String, StringBuffer> entry : data.entrySet()) {
            if (entry.getValue().length() == 0) {
                continue;
            }
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
