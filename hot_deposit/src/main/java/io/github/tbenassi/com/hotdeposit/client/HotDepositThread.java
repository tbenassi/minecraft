package io.github.tbenassi.com.hotdeposit.client;

public class HotDepositThread extends Thread {
    public HotDepositThread(Runnable task) {
        super(task, "HotDepositThread");
    }

    public static boolean running(Thread thread) {
        return thread != null && !thread.isInterrupted();
    }

    public static void interruptCurrentOperation(Thread thread) {
        if (running(thread)) thread.interrupt();
    }
}
