package me.nanjingchj.discordjshell;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigurationManager implements Serializable {
    private static final long serialVersionUID = -7243879225592588617L;

    private Map<String, GuildConfigurationManager> configurationManagers;
    private transient volatile boolean modified = false;

    public ConfigurationManager() {
        configurationManagers = new HashMap<>();
    }

    public void resetModificationFlag() {
        modified = false;
    }

    public void modified() {
        modified = true;
        backup();
    }

    private void backup() {
        if (modified) {
            try {
                File backupFile = new File("config");
                if (!backupFile.exists()) {
                    if (!backupFile.createNewFile()) {
                        throw new Error("This should never be thrown");
                    }
                }
                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(backupFile))) {
                    oos.writeObject(this);
                    oos.flush();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        resetModificationFlag();
    }

    private static void initConfigurations(GuildConfigurationManager guildConfigurationManager) {
        List<@NotNull String> startupSounds = new ArrayList<>(), shutdownSounds = new ArrayList<>();
        startupSounds.add("https://www.youtube.com/watch?v=7nQ2oiVqKHw");
        shutdownSounds.add("https://www.youtube.com/watch?v=Gb2jGy76v0Y");
        guildConfigurationManager.setConfiguration("startupSounds", startupSounds);
        guildConfigurationManager.setConfiguration("shutdownSounds", shutdownSounds);
        SelectionFromListConfig startupSoundConfig = SelectionFromListConfig.Last;
        SelectionFromListConfig shutdownSoundConfig = SelectionFromListConfig.Last;
        guildConfigurationManager.setConfiguration("startupSoundConfig", startupSoundConfig);
        guildConfigurationManager.setConfiguration("shutdownSoundConfig", shutdownSoundConfig);
    }

    @SuppressWarnings("SynchronizeOnNonFinalField")
    private GuildConfigurationManager getConfigurationManager(@NotNull String guildID) {
        GuildConfigurationManager manager = configurationManagers.get(guildID);
        if (manager == null) {
            manager = new GuildConfigurationManager();
            initConfigurations(manager);
            synchronized (configurationManagers) {
                configurationManagers.put(guildID, manager);
                modified = true;
            }
        }
        return manager;
    }

    @SuppressWarnings({"unchecked", "unused"})
    public <T> T getConfiguration(@NotNull String guildID, @NotNull String name, @NotNull Class<T> type) {
        try {
            modified = true;
            return (T) getConfigurationManager(guildID).getConfiguration(name);
        } finally {
            backup();
        }
    }

    @Nullable
    public Object getConfiguration(@NotNull String guildID, @NotNull String name) {
        try {
            modified = true;
            return getConfigurationManager(guildID).getConfiguration(name);
        } finally {
            backup();
        }
    }

    public synchronized void setConfiguration(@NotNull String guildID, @NotNull String name, Object value) {
        try {
            modified = true;
            getConfigurationManager(guildID).setConfiguration(name, value);
        } finally {
            backup();
        }
    }

    @Nullable
    public synchronized String getExtConfiguration(@NotNull String guildID, @NotNull String name) {
        try {
            modified = true;
            return getConfigurationManager(guildID).getExtConfiguration(name);
        } finally {
            backup();
        }
    }

    public synchronized void setExtConfiguration(@NotNull String guildID, @NotNull String name, String value) {
        try {
            modified = true;
            getConfigurationManager(guildID).setExtConfiguration(name, value);
        } finally {
            backup();
        }
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        int size = ois.readInt();
        configurationManagers = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String key = (String) ois.readObject();
            GuildConfigurationManager value = (GuildConfigurationManager) ois.readObject();
            configurationManagers.put(key, value);
        }
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        int size = configurationManagers.size();
        oos.writeInt(size);
        for (Map.Entry<String, GuildConfigurationManager> entry : configurationManagers.entrySet()) {
            oos.writeObject(entry.getKey());
            oos.writeObject(entry.getValue());
        }
    }
}
