package org.jiffy.handlers;

import org.jiffy.core.EffectHandler;
import org.jiffy.definitions.LogEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test implementation of LogEffect handler that collects log messages.
 * Useful for verifying that correct log messages were produced during tests.
 */
public class CollectingLogHandler implements EffectHandler<LogEffect> {

    private final List<LogEntry> logs = new ArrayList<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T handle(LogEffect effect) {
        if (effect instanceof LogEffect.Info info) {
            logs.add(new LogEntry(LogLevel.INFO, info.message(), null));
        } else if (effect instanceof LogEffect.Error error) {
            logs.add(new LogEntry(LogLevel.ERROR, error.message(), error.error()));
        } else if (effect instanceof LogEffect.Warning warning) {
            logs.add(new LogEntry(LogLevel.WARNING, warning.message(), null));
        } else if (effect instanceof LogEffect.Debug debug) {
            logs.add(new LogEntry(LogLevel.DEBUG, debug.message(), null));
        }
        return null; // LogEffect always returns Void
    }

    /**
     * Get all collected log entries.
     */
    public List<LogEntry> getLogs() {
        return Collections.unmodifiableList(logs);
    }

    /**
     * Get log messages as formatted strings.
     */
    public List<String> getFormattedLogs() {
        List<String> formatted = new ArrayList<>();
        for (LogEntry entry : logs) {
            String message = String.format("[%s] %s", entry.level, entry.message);
            if (entry.error != null) {
                message += " - " + entry.error.getMessage();
            }
            formatted.add(message);
        }
        return formatted;
    }

    /**
     * Check if a specific message was logged at a specific level.
     */
    public boolean containsMessage(LogLevel level, String message) {
        return logs.stream()
            .anyMatch(entry -> entry.level == level && entry.message.equals(message));
    }

    /**
     * Check if a message containing the substring was logged.
     */
    public boolean containsMessagePart(String messagePart) {
        return logs.stream()
            .anyMatch(entry -> entry.message.contains(messagePart));
    }

    /**
     * Get count of messages at a specific level.
     */
    public int getCountByLevel(LogLevel level) {
        return (int) logs.stream()
            .filter(entry -> entry.level == level)
            .count();
    }

    /**
     * Clear all collected logs.
     */
    public void clear() {
        logs.clear();
    }

    /**
     * Log entry record.
     */
    public static class LogEntry {
        public final LogLevel level;
        public final String message;
        public final Throwable error;

        public LogEntry(LogLevel level, String message, Throwable error) {
            this.level = level;
            this.message = message;
            this.error = error;
        }
    }

    /**
     * Log level enum.
     */
    public enum LogLevel {
        DEBUG, INFO, WARNING, ERROR
    }
}