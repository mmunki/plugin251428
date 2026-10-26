package com.mmunki.plugin251428.core;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-process ring buffer for the status screen. Nothing is written to disk. */
public final class BotLog {
    public interface Listener {
        void onLogChanged();
    }

    private static final int CAPACITY = 120;
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final SimpleDateFormat TIME =
            new SimpleDateFormat("HH:mm:ss", Locale.US);

    private BotLog() {
    }

    public static void post(String message) {
        String line;
        synchronized (LINES) {
            line = TIME.format(new Date()) + "  " + message;
            LINES.addLast(line);
            while (LINES.size() > CAPACITY) {
                LINES.removeFirst();
            }
        }
        for (Listener listener : LISTENERS) {
            listener.onLogChanged();
        }
    }

    public static List<String> snapshot() {
        synchronized (LINES) {
            return new ArrayList<>(LINES);
        }
    }

    public static void addListener(Listener listener) {
        LISTENERS.addIfAbsent(listener);
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }
}
