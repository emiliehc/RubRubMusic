package me.nanjingchj.discordjshell;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class GuildConfigurationManager implements Serializable {
    private static final long serialVersionUID = 15215608478978828L;

    private Map<String, Object> configurations;
    private Map<String, String> extConfigurations;

    public GuildConfigurationManager() {
        configurations = new HashMap<>();
        extConfigurations = new HashMap<>();
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
    }

    @Nullable
    public synchronized String getExtConfiguration(@NotNull String name) {
        return extConfigurations.get(name);
    }

    public synchronized void setExtConfiguration(@NotNull String name, String value) {
        extConfigurations.put(name, value);
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        int configSize = ois.readInt();
        configurations = new HashMap<>(configSize);
        for (int i = 0; i < configSize; i++) {
            String key = (String) ois.readObject();
            Object value = ois.readObject();
            configurations.put(key, value);
        }

        int extSize = ois.readInt();
        extConfigurations = new HashMap<>(extSize);
        for (int i = 0; i < extSize; i++) {
            String key = (String) ois.readObject();
            String value = (String) ois.readObject();
            extConfigurations.put(key, value);
        }
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        int configSize = configurations.size();
        oos.writeInt(configSize);
        for (Map.Entry<String, Object> entry : configurations.entrySet()) {
            oos.writeObject(entry.getKey());
            oos.writeObject(entry.getValue());
        }
        int extSize = extConfigurations.size();
        oos.writeInt(extSize);
        for (Map.Entry<String, String> entry : extConfigurations.entrySet()) {
            oos.writeObject(entry.getKey());
            oos.writeObject(entry.getValue());
        }
    }
}
