/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.jitlogic.zorka.core.spy.plugins;

/**
 *
 * @author ur50
 */
public class UriStore {

    private static final ThreadLocal<String> THREAD_LOCAL = new ThreadLocal<String>();

    public static void set(String uri) {
        THREAD_LOCAL.set(uri);
    }

    public static String get() {
        return THREAD_LOCAL.get();
    }

    public static void clear() {
        THREAD_LOCAL.remove();
    }

}
