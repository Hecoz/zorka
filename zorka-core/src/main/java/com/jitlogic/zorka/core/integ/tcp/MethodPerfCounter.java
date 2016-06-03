/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.jitlogic.zorka.core.integ.tcp;

/**
 *
 * @author ur50
 */
public class MethodPerfCounter {

    private final String klass;

    private final String method;

    private final long time;

    public MethodPerfCounter(String klass, String method, long time) {
        this.klass = klass;
        this.method = method;
        this.time = time;
    }

    public String getKlass() {
        return klass;
    }

    public String getMethod() {
        return method;
    }

    public long getTime() {
        return time;
    }

}
