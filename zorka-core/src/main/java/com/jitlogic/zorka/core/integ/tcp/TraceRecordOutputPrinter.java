/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.jitlogic.zorka.core.integ.tcp;

import com.jitlogic.zorka.common.tracedata.SymbolRegistry;
import com.jitlogic.zorka.common.tracedata.TraceRecord;
import java.io.PrintWriter;
import java.util.Map;

/**
 *
 * @author ur50
 */
public class TraceRecordOutputPrinter {

    private final SymbolRegistry symbolRegistry;

    private final PrintWriter writer;

    public TraceRecordOutputPrinter(SymbolRegistry symbolRegistry, PrintWriter writer) {
        this.symbolRegistry = symbolRegistry;
        this.writer = writer;
    }

    public void print(TraceRecord traceRecord) {
        long clock = traceRecord.getClock() / 1000l;
        writer.printf("{ \"clock\" : %d ", clock);
        if (traceRecord.getAttrs() != null) {
            for (Map.Entry<Integer, Object> entry : traceRecord.getAttrs().entrySet()) {
                String attr = symbolRegistry.symbolName(entry.getKey());
                writer.printf(",\"%s\":\"%s\"", attr, entry.getValue());
            }
        }
        writer.printf(",\"stack\":\"");
        if (traceRecord.getChildren() != null) {
            for (TraceRecord child : traceRecord.getChildren()) {
                printStack(child, 0);
            }
        }
        writer.printf("\" }");
    }

    public void printStack(TraceRecord traceRecord, int level) {
        //long clock = traceRecord.getClock() / 1000l;
        if (level > 0) {
            writer.printf("%s" + level * 4, " ");
        }
        String className = symbolRegistry.symbolName(traceRecord.getClassId());
        String methodName = symbolRegistry.symbolName(traceRecord.getMethodId());
        writer.printf("\\n%s.%s", className, methodName);
        if (traceRecord.getChildren() != null) {
            for (TraceRecord child : traceRecord.getChildren()) {
                printStack(child, level + 1);
            }
        }
    }

}
