package me.bechberger;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * store of the traces
 */
public class Store {

    /** too large and browsers can't display it anymore */
    private final int MAX_FLAMEGRAPH_DEPTH = 100;

    /**
     * Trace tree node to create a JSON for <a href="https://github.com/spiermar/d3-flame-graph">d3-flame-graph</a>
     */
    private static class Node {
        private final String method;
        private final Map<String, Node> children = new HashMap<>();
        private long samples = 0;

        public Node(String method) {
            this.method = method;
        }

        private Node getChild(String method) {
            return children.computeIfAbsent(method, Node::new);
        }

        private void addTrace(List<String> trace, int end) {
            samples++;
            if (end > 0) {
                getChild(trace.get(end)).addTrace(trace, end - 1);
            }
        }

        public void addTrace(List<String> trace) {
            addTrace(trace, trace.size() - 1);
        }

        /**
         * Write in d3-flamegraph format
         */
        private void writeAsJson(PrintStream s, int maxDepth) {
            s.printf("{ \"name\": \"%s\", \"value\": %d, \"children\": [", method, samples);
            if (maxDepth > 1) {
                for (Node child : children.values()) {
                    child.writeAsJson(s, maxDepth - 1);
                    s.print(",");
                }
            }
            s.print("]}");
        }

        public void writeAsHTML(PrintStream s, int maxDepth) {
            s.print("""
                    <head>
                      <link rel="stylesheet" type="text/css" href="https://cdn.jsdelivr.net/npm/d3-flame-graph@4.1.3/dist/d3-flamegraph.css">
                      <link rel="stylesheet" type="text/css" href="misc/d3-flamegraph.css">
                    </head>
                    <body>
                      <div id="chart"></div>
                      <script type="text/javascript" src="https://d3js.org/d3.v7.js"></script>
                      <script type="text/javascript" src="misc/d3.v7.js"></script>
                      <script type="text/javascript" src="https://cdn.jsdelivr.net/npm/d3-flame-graph@4.1.3/dist/d3-flamegraph.js"></script>
                      <script type="text/javascript" src="misc/d3-flamegraph.js"></script>
                      <script type="text/javascript">
                      var chart = flamegraph().width(window.innerWidth);
                      d3.select("#chart").datum(""");
            writeAsJson(s, maxDepth);
            s.print("""
                    ).call(chart);
                      window.onresize = () => chart.width(window.innerWidth);
                      </script>
                    </body>
                    """);
        }
    }

    private final Optional<Path> flamePath;
    private final Map<String, Long> methodOnTopSampleCount = new HashMap<>();
    private final Map<String, Long> methodSampleCount = new HashMap<>();

    private long totalSampleCount = 0;

    /**
     * trace tree node, only populated if flamePath is present
     */
    private final Node rootNode = new Node("root");

    public Store(Optional<Path> flamePath) {
        this.flamePath = flamePath;
    }

    private String flattenStackTraceElement(StackTraceElement stackTraceElement) {
        // call intern to safe some memory
        return (stackTraceElement.getClassName() + "." + stackTraceElement.getMethodName()).intern();
    }

    private void updateMethodTables(String method, boolean onTop) {
        methodSampleCount.put(method, methodSampleCount.getOrDefault(method, 0L) + 1);
        if (onTop) {
            methodOnTopSampleCount.put(method, methodOnTopSampleCount.getOrDefault(method, 0L) + 1);
        }
    }

    private void updateMethodTables(List<String> trace) {
        Set<String> alreadyCounted = new HashSet<>();
        for (int i = 0; i < trace.size(); i++) {
            String method = trace.get(i);
            if (alreadyCounted.add(method)) {
                updateMethodTables(method, i == 0);
            }
        }
    }

    public void addSample(StackTraceElement[] stackTraceElements) {
        List<String> trace = Stream.of(stackTraceElements).map(this::flattenStackTraceElement).toList();
        updateMethodTables(trace);
        if (flamePath.isPresent()) {
            rootNode.addTrace(trace);
        }
        totalSampleCount++;
    }

    private record MethodTableEntry(String method, long sampleCount, long onTopSampleCount) {
    }

    private void printMethodTable(PrintStream s, List<MethodTableEntry> sortedEntries) {
        s.printf("===== method table ======%n");
        s.printf("Total samples: %d%n", totalSampleCount);
        s.printf("%-60s %10s %10s %10s %10s%n", "Method", "Samples", "Percentage", "On top", "Percentage");
        for (MethodTableEntry entry : sortedEntries) {
            String method = entry.method.substring(0, Math.min(entry.method.length(), 60));
            s.printf("%-60s %10d %10.2f %10d %10.2f%n", method, entry.sampleCount(),
                    entry.sampleCount() / (double) totalSampleCount * 100, entry.onTopSampleCount(),
                    entry.onTopSampleCount() / (double) totalSampleCount * 100);
        }
    }

    public void printMethodTable() {
        // sort methods by sample count
        List<MethodTableEntry> methodTable =
                methodSampleCount.entrySet().stream().map(entry -> new MethodTableEntry(entry.getKey(),
                        entry.getValue(), methodOnTopSampleCount.getOrDefault(entry.getKey(), 0L)))
                        .sorted((a, b) -> Long.compare(b.sampleCount, a.sampleCount)).toList();
        printMethodTable(System.out, methodTable);
    }

    public void storeFlameGraphIfNeeded() {
        flamePath.ifPresent(path -> {
            try (OutputStream os = new BufferedOutputStream(java.nio.file.Files.newOutputStream(path))) {
                PrintStream s = new PrintStream(os);
                rootNode.writeAsHTML(s, MAX_FLAMEGRAPH_DEPTH);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
