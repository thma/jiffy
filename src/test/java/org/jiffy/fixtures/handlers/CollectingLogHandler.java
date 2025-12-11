package org.jiffy.fixtures.handlers;

import org.jiffy.core.EffectHandler;
import org.jiffy.fixtures.effects.LogEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler that collects log messages for later verification in tests.
 */
public class CollectingLogHandler implements EffectHandler<LogEffect> {

    public enum LogLevel { INFO, WARNING, ERROR, DEBUG }

    public record LogEntry(LogLevel level, String message) {}

    private final List<LogEntry> logs = new ArrayList<>();

    @Override
    //@SuppressWarnings("unchecked")
    public <T> T handle(LogEffect effect) {
        LogLevel level = switch (effect) {
            case LogEffect.Info ignored -> LogLevel.INFO;
            case LogEffect.Warning ignored -> LogLevel.WARNING;
            case LogEffect.Error ignored -> LogLevel.ERROR;
            case LogEffect.Debug ignored -> LogLevel.DEBUG;
        };
        logs.add(new LogEntry(level, effect.message()));
        return null; // LogEffect returns Void
    }

//    public List<LogEntry> getLogs() {
//        return new ArrayList<>(logs);
//    }

//    public boolean containsMessage(LogLevel level, String substring) {
//        return logs.stream()
//            .anyMatch(entry -> entry.level() == level && entry.message().contains(substring));
//    }

    public boolean containsMessagePart(String substring) {
        return logs.stream().anyMatch(entry -> entry.message().contains(substring));
    }

//    public int getCountByLevel(LogLevel level) {
//        return (int) logs.stream().filter(entry -> entry.level() == level).count();
//    }

//    public List<String> getMessages() {
//        return logs.stream().map(LogEntry::message).collect(Collectors.toList());
//    }

//    public void clear() {
//        logs.clear();
//    }

    public int size() {
        return logs.size();
    }
}
