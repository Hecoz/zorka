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
import java.nio.charset.CharacterCodingException;
import java.util.Map;

/**
 *
 * @author ur50
 */
public class TraceRecordOutputPrinter {

    private final String app;

    private final SymbolRegistry symbolRegistry;

    private final PrintWriter writer;

//    private final String performanceTargetPackage;
//
//    private final int performanceTargetPackageCropIndex;

    private SymbolicException exception;

    private final int maxFieldLength;

    public TraceRecordOutputPrinter(String app, SymbolRegistry symbolRegistry, PrintWriter writer,
            String performanceTargetPackage, int maxFieldLength) {
        this.app = app;
        this.symbolRegistry = symbolRegistry;
        this.writer = writer;
//        this.performanceTargetPackage = performanceTargetPackage;
//        this.performanceTargetPackageCropIndex = performanceTargetPackage == null ? 0
//                : performanceTargetPackage.length() + 1;
        this.maxFieldLength = maxFieldLength;
    }

    public void print(TraceRecord traceRecord) throws CharacterCodingException {
        long clock = traceRecord.getClock() / 1000l;
        writer.printf("{ \"app\":\"%s\", \"type\":\"%s\", \"clock\" : %d, \"time\": %d", app,
               symbolRegistry.symbolName(traceRecord.getTraceId()), clock, traceRecord.getTime() / 1000000l);
        if (traceRecord.getAttrs() != null) {
            for (Map.Entry<Integer, Object> entry : traceRecord.getAttrs().entrySet()) {
                String attr = symbolRegistry.symbolName(entry.getKey()).toLowerCase().replace('.',
                        '_');
                String value = TcpUtils.removeInvalidCharacters(entry.getValue());
                if (value.length() > maxFieldLength) {
                    writer.printf(",\"%s\":\"%s\" ", attr, TcpUtils.ellipsize(value, maxFieldLength));
                } else {
                    writer.printf(",\"%s\":\"%s\"", attr, value);
                }

            }
        }

        if (traceRecord.getErrors() > 0) {
            getException(traceRecord);
            if (exception != null) {
                //get root cause
                while (exception.getCause() != null) {
                    exception = exception.getCause();
                }
                SymbolicStackElement stack
                        = exception.getStackTrace()[0];
                String className = symbolRegistry.symbolName(stack.getClassId());
                String methodName = symbolRegistry.symbolName(stack.getMethodId());
                String cause = className + "." + methodName + "(): line " + stack.getLineNum();
                writer.printf(",\"exception\" : \"%s\"", symbolRegistry.symbolName(exception.
                        getClassId()));
                writer.printf(",\"description\" : \"%s\"", exception.getMessage() == null ? ""
                        : exception.getMessage().
                        replaceAll("\"", "'").replaceAll("[\n\r]", ""));
                writer.printf(",\"location\" : \"%s\"", cause);
            }
        }
        writer.printf(" }\n");

//        if (performanceTargetPackage != null && performanceTargetPackage.length() > 0) {
//            printStatistics(traceRecord);
//        }
    }

    public void getException(TraceRecord traceRecord) {
        if (traceRecord.getException() != null) {
            exception = (SymbolicException) traceRecord.getException();
        }
        if (traceRecord.getChildren() != null) {
            for (TraceRecord child : traceRecord.getChildren()) {
                getException(child);
            }
        }
    }

//    public void printStatistics(TraceRecord traceRecord) {
//        String klass = symbolRegistry.symbolName(traceRecord.getClassId());
//        if (klass != null && klass.startsWith(performanceTargetPackage)) {
//            String method = symbolRegistry.symbolName(traceRecord.getMethodId());
//            long time = traceRecord.getTime() / 1000000l;
//            writer.printf(
//                    "{ \"app\":\"%s\", \"type\":\"perf\", \"method\": \"%s.%s()\",\"time\":%d}\n",
//                    app, klass.substring(performanceTargetPackageCropIndex), method, time);
//        }
//        if (traceRecord.getChildren() != null) {
//            for (TraceRecord child : traceRecord.getChildren()) {
//                printStatistics(child);
//            }
//        }
//    }
    
}
