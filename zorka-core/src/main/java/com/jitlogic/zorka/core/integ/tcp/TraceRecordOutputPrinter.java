/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.jitlogic.zorka.core.integ.tcp;

import com.jitlogic.zorka.common.tracedata.SymbolRegistry;
import com.jitlogic.zorka.common.tracedata.SymbolicException;
import com.jitlogic.zorka.common.tracedata.SymbolicStackElement;
import com.jitlogic.zorka.common.tracedata.TraceRecord;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author ur50
 */
public class TraceRecordOutputPrinter {

    private final SymbolRegistry symbolRegistry;

    private final PrintWriter writer;

    private final String performanceTargetPackage;

    private final int performanceTargetPackageCropIndex;

    private SymbolicException exception;

    private List<MethodPerfCounter> performance = new ArrayList<MethodPerfCounter>();

    public TraceRecordOutputPrinter(SymbolRegistry symbolRegistry, PrintWriter writer,
            String performanceTargetPackage) {
        this.symbolRegistry = symbolRegistry;
        this.writer = writer;
        this.performanceTargetPackage = performanceTargetPackage;
        this.performanceTargetPackageCropIndex = performanceTargetPackage == null ? 0
                : performanceTargetPackage.length() + 1;
    }

    public void print(TraceRecord traceRecord) {
        long clock = traceRecord.getClock() / 1000l;
        writer.printf("{ \"type\":\"trace\", \"clock\" : %d, \"time\": %d", clock, traceRecord.getTime() / 1000000l);
        if (traceRecord.getAttrs() != null) {
            for (Map.Entry<Integer, Object> entry : traceRecord.getAttrs().entrySet()) {
                String attr = symbolRegistry.symbolName(entry.getKey()).toLowerCase();
                writer.printf(",\"%s\":\"%s\"", attr, entry.getValue() == null ? "null" : entry.
                        getValue().toString().replaceAll("\"", "'").replaceAll("[\n\r]", ""));
            }
        }

        if (traceRecord.getErrors() > 0) {
            getExceptionCause(traceRecord);
            if (exception != null) {
                SymbolicStackElement stack
                        = exception.getStackTrace()[0];
                String className = symbolRegistry.symbolName(stack.getClassId());
                String methodName = symbolRegistry.symbolName(stack.getMethodId());
                String cause = className + "." + methodName + "(): line " + stack.getLineNum();
                writer.printf(",\"exception\" : \"%s: %s\"", symbolRegistry.symbolName(exception.
                        getClassId()), exception.getMessage() == null ? "" : exception.getMessage().
                                replaceAll("\"", "'").replaceAll("[\n\r]", ""));
                writer.printf(",\"cause\" : \"%s\"", cause);
            }
        }
        writer.printf(" }\n");

        if (performanceTargetPackage != null && performanceTargetPackage.length() > 0) {
            printStatistics(traceRecord);
        }
    }

    public void getExceptionCause(TraceRecord traceRecord) {
        if (traceRecord.getException() != null) {
            exception = (SymbolicException) traceRecord.getException();
            while (exception.getCause() != null) {
                exception = exception.getCause();
            }
        } else if (traceRecord.getChildren() != null) {
            for (TraceRecord child : traceRecord.getChildren()) {
                getExceptionCause(child);
            }
        }
    }

    public void printStatistics(TraceRecord traceRecord) {
        String klass = symbolRegistry.symbolName(traceRecord.getClassId());
        if (klass != null && klass.startsWith(performanceTargetPackage)) {
            String method = symbolRegistry.symbolName(traceRecord.getMethodId());
            long time = traceRecord.getTime() / 1000000l;
            writer.printf("{ \"type\":\"perf\", \"method\": \"%s.%s()\",\"time\":%d}\n", klass.substring(
                    performanceTargetPackageCropIndex), method, time);
        }
        if (traceRecord.getChildren() != null) {
            for (TraceRecord child : traceRecord.getChildren()) {
                printStatistics(child);
            }
        }
    }
}