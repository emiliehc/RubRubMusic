package me.nanjingchj.discordjshell;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class ConfigurationManager implements Serializable {
    private final Map<String, Object> configurations;
    private final Map<String, String> extConfigurations;
    private transient Runnable callback;

    public void setCallback(Runnable callback) {
        this.callback = callback;
    }

    public ConfigurationManager(Runnable callback) {
        configurations = new HashMap<>();
        extConfigurations = new HashMap<>();
        this.callback = callback;
    }

    @SuppressWarnings("unchecked")
    public synchronized <T> T getConfiguration(@NotNull String name, @NotNull Class<T> type) {
        return (T) getConfiguration(name);
    }

    @Nullable
    public synchronized Object getConfiguration(@NotNull String name) {
        return configurations.get(name);
    }

    public synchronized void setConfiguration(@NotNull String name, Object value) {
        configurations.put(name, value);
        callback.run();
    }

    @Nullable
    public synchronized String getExtConfiguration(@NotNull String name) {
        return extConfigurations.get(name);
    }

    public synchronized void setExtConfiguration(@NotNull String name, String value) {
        extConfigurations.put(name, value);
        callback.run();
    }
}
