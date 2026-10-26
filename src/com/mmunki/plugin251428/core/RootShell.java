package com.mmunki.plugin251428.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Tiny root helper. Every command is executed as {@code su -c "<command>"}. */
public final class RootShell {
    public static final class Result {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        Result(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public boolean ok() {
            return exitCode == 0;
        }
    }

    private static volatile Boolean rooted;

    private RootShell() {
    }

    public static boolean isRooted() {
        Boolean cached = rooted;
        if (cached != null) {
            return cached;
        }
        boolean value = false;
        try {
            Result result = exec("id", 3500);
            value = result.ok() && result.stdout.contains("uid=0");
        } catch (Throwable ignored) {
            value = false;
        }
        rooted = value;
        return value;
    }

    public static void refreshRoot() {
        rooted = null;
    }

    /** Last known value without starting a root process on the caller thread. */
    public static Boolean cachedRoot() {
        return rooted;
    }

    public static Result exec(String command, int timeoutMs) throws IOException {
        Process process = new ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start();
        byte[] out = readAll(process.getInputStream());
        try {
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroy();
                return new Result(-1, new String(out, StandardCharsets.UTF_8),
                        "timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroy();
            return new Result(-1, new String(out, StandardCharsets.UTF_8), "interrupted");
        }
        return new Result(process.exitValue(), new String(out, StandardCharsets.UTF_8), "");
    }

    public static byte[] execBytes(String command, int timeoutMs) throws IOException {
        Process process = new ProcessBuilder("su", "-c", command)
                .redirectErrorStream(false)
                .start();
        final ByteArrayOutputStream errors = new ByteArrayOutputStream();
        Thread drain = new Thread(() -> {
            try {
                copy(process.getErrorStream(), errors);
            } catch (IOException ignored) {
                // the exit code is what matters
            }
        });
        drain.setDaemon(true);
        drain.start();
        byte[] out = readAll(process.getInputStream());
        try {
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroy();
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroy();
            return null;
        }
        return process.exitValue() == 0 ? out : null;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return output.toByteArray();
    }

    private static void copy(InputStream input, ByteArrayOutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }
}
