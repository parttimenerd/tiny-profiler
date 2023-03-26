package me.bechberger;

/**
 * Agent entry
 */
public class Main {
    public static void agentmain(String agentArgs) {
        premain(agentArgs);
    }

    public static void premain(String agentArgs) {
        Main main = new Main();
        main.run(new Options(agentArgs));
    }

    private void run(Options options) {
        Thread t = new Thread(new Profiler(options));
        t.setDaemon(true);
        t.setName("Profiler");
        t.start();
    }
}
