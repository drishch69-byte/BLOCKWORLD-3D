package com.blockworld.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/** Writes runtime diagnostics beside the project launcher log. */
public final class GameLog {
    private static final Path LOG_FILE = Path.of(".logs", "latest-run.log");

    private GameLog() { }

    public static synchronized void info(String message) {
        append("INFO", message, null);
    }

    public static synchronized void error(String message, Throwable error) {
        append("ERROR", message, error);
    }

    private static void append(String level, String message, Throwable error) {
        StringBuilder text = new StringBuilder()
                .append('[').append(LocalDateTime.now()).append("] [")
                .append(level).append("] ").append(message).append(System.lineSeparator());
        if (error != null) {
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            text.append(trace).append(System.lineSeparator());
        }
        try {
            Files.createDirectories(LOG_FILE.getParent());
            Files.writeString(LOG_FILE, text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Runtime diagnostics must never crash the game.
        }
    }
}
