package me.nanjingchj.discordjshell;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import kotlin.Pair;
import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main extends ListenerAdapter {
    private volatile MessageChannel jShellChannel = null;
    private volatile Process jShell;
    private volatile Scanner jShellInput;
    private volatile Scanner jShellError;
    private final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

    private final Map<Guild, Queue<Pair<MessageReceivedEvent, String>>> audioQueues = new HashMap<>();
    private final Map<Guild, Boolean> playing = new HashMap<>();
    private final Map<String, ConfigurationManager> configurations;
    private final Runnable backupCallback = () -> {
        // backup
        try {
            File backupFile = new File("config");
            if (!backupFile.exists()) {
                if (!backupFile.createNewFile()) {
                    throw new Error("This should never be thrown");
                }
            }
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(backupFile))) {
                oos.writeObject(getConfigurations());
                oos.flush();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    };

    private Map<String, ConfigurationManager> getConfigurations() {
        return configurations;
    }

    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter", "BusyWait", "unchecked"})
    public Main() throws IOException, ClassNotFoundException {
        AudioSourceManagers.registerRemoteSources(playerManager);

        File f = new File("config");
        // backup

        if (!f.exists()) {
            if (!f.createNewFile()) {
                throw new Error("This should never be thrown");
            }
            configurations = new CallbackHashMap<>(backupCallback);
        } else {
            try (ObjectInputStream oos = new ObjectInputStream(new FileInputStream(f))) {
                configurations = (Map<String, ConfigurationManager>) oos.readObject();
                ((CallbackHashMap<?, ?>) configurations).setCallback(backupCallback);
                configurations.forEach((s, configurationManager) -> {
                    configurationManager.setCallback(backupCallback);
                });
            }
        }

        var processBuilder = new ProcessBuilder("jshell");
        jShell = processBuilder.start();
        jShellError = new Scanner(jShell.getErrorStream());
        jShellInput = new Scanner(jShell.getInputStream());

        new Thread(() -> {
            for (; ; ) {
                if (jShellChannel != null) {
                    var msg = jShellInput.nextLine();
                    System.out.println(msg);
                    if (!msg.trim().isEmpty()) {
                        printActiveChannel(msg);
                    }
                }
            }
        }).start();

        new Thread(() -> {
            Scanner sc = new Scanner(System.in);
            for (; ; ) {
                var str = sc.nextLine();
                try {
                    jShell.getOutputStream().write((str + "\n").getBytes());
                    jShell.getOutputStream().flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();


        new Thread(() -> {
            for (; ; ) {
                if (jShellChannel != null) {
                    var msg = jShellError.nextLine();
                    System.out.println(msg);
                    if (!msg.trim().isEmpty()) {
                        printActiveChannel(msg.concat("\n"));
                    }

                }
            }

        }).start();

        // music playing thread
        new Thread(() -> {
            for (; ; ) {
                synchronized (audioQueues) {
                    audioQueues.forEach((guild, queue) -> {
                        Pair<MessageReceivedEvent, String> audio;
                        synchronized (queue) {
                            audio = queue.peek();
                        }
                        boolean isPlaying;
                        synchronized (playing) {
                            isPlaying = playing.get(guild);
                        }
                        if (audio != null && !isPlaying) {
                            try {
                                playAudio(audio.getFirst(), new String[]{"", audio.getSecond()});
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            synchronized (queue) {
                                queue.poll();
                            }
                        }
                    });
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private static void initConfigurations(ConfigurationManager configurationManager) {
        List<@NotNull String> startupSounds = new ArrayList<>(), shutdownSounds = new ArrayList<>();
        startupSounds.add("https://www.youtube.com/watch?v=7nQ2oiVqKHw");
        shutdownSounds.add("https://www.youtube.com/watch?v=Gb2jGy76v0Y");
        configurationManager.setConfiguration("startupSounds", startupSounds);
        configurationManager.setConfiguration("shutdownSounds", shutdownSounds);
        SelectionFromListConfig startupSoundConfig = SelectionFromListConfig.Last;
        SelectionFromListConfig shutdownSoundConfig = SelectionFromListConfig.Last;
        configurationManager.setConfiguration("startupSoundConfig", startupSoundConfig);
        configurationManager.setConfiguration("shutdownSoundConfig", shutdownSoundConfig);
    }

    private synchronized ConfigurationManager getConfigurationManager(@NotNull Guild guild) {
        ConfigurationManager manager = configurations.get(guild.getId());
        if (manager == null) {
            manager = new ConfigurationManager(backupCallback);
            initConfigurations(manager);
            configurations.put(guild.getId(), manager);
        }
        return manager;
    }

    @SuppressWarnings("deprecation")
    public static void main(String[] args) throws Exception {
        Scanner sysIn = new Scanner(new File("token.txt"));
        var builder = new JDABuilder(AccountType.BOT);
        builder.setToken(sysIn.nextLine());
        sysIn.close();
        builder.addEventListeners(new Main());
        builder.build();
    }

    private <T> void printActiveChannel(T t) {
        if (jShellChannel != null) {
            jShellChannel.sendMessage(t.toString()).queue();
        }
    }

    private void playAudio(@NotNull MessageReceivedEvent event, String[] args) {
        var guild = event.getGuild();
        var musicChannels = guild.getVoiceChannelsByName("Music", true);

        if (musicChannels.isEmpty()) {
            guild.createVoiceChannel("Music").queue();
            musicChannels = guild.getVoiceChannelsByName("Music", true);
        }
        var musicChannel = musicChannels.get(0);
        var audioManager = guild.getAudioManager();
        final AudioTrack[] audioTrackToPlay = new AudioTrack[1];
        final boolean[] loadingError = {false};
        playerManager.loadItem(args[1], new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                System.out.println(audioTrack.getInfo().uri);
                audioTrackToPlay[0] = audioTrack;
                //event.getChannel().sendMessage("Playing " + audioTrackToPlay[0].getIdentifier()).queue();
            }

            @Override
            public void playlistLoaded(AudioPlaylist audioPlaylist) {
                audioTrackToPlay[0] = audioPlaylist.getTracks().get(0);
                System.out.println(audioTrackToPlay[0].getInfo().uri);
                //event.getChannel().sendMessage("Playing " + audioTrackToPlay[0].getIdentifier()).queue();
            }

            @Override
            public void noMatches() {
                System.err.println("No matches");
                event.getChannel().sendMessage("No matches").queue();
                playing.put(guild, false);
                loadingError[0] = true;
            }

            @Override
            public void loadFailed(FriendlyException e) {
                e.printStackTrace();
                loadingError[0] = true;
            }
        });
        audioManager.setSendingHandler(new AudioSendHandler() {
            private final AudioPlayer audioPlayer;
            private AudioFrame lastFrame;

            {
                audioPlayer = playerManager.createPlayer();
                while (audioTrackToPlay[0] == null) {
                    Thread.onSpinWait();
                    if (loadingError[0]) {
                        throw new RuntimeException();
                    }
                }
                audioPlayer.playTrack(audioTrackToPlay[0]);
                playing.put(guild, true);
            }

            @Override
            public boolean canProvide() {
                lastFrame = audioPlayer.provide();
                boolean result = lastFrame != null;
                playing.put(guild, result);
                return result;
            }

            @Override
            public @NotNull ByteBuffer provide20MsAudio() {
                return ByteBuffer.wrap(lastFrame.getData());
            }

            @Override
            public boolean isOpus() {
                return true;
            }
        });
        audioManager.openAudioConnection(musicChannel);
    }

    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter", "unchecked"})
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        try {
            var msg = event.getMessage().getContentRaw();

            if (msg.startsWith("#")) {
                msg = msg.substring(1);
                // preprocess
                msg = preprocessUserInput(event.getGuild(), msg);
                String[] args = msg.split(" ");
                switch (args[0]) {
                    case "unbind" -> sendLeaveChannelMessage(event);
                    case "bind" -> {
                        sendLeaveChannelMessage(event);
                        Thread.sleep(2000);
                        jShellChannel = event.getChannel();
                        sendEnterChannelMessage(event, jShellChannel);
                    }
                    case "restart" -> {
                        jShell.destroy();
                        try {
                            jShell = new ProcessBuilder("jshell").start();
                            jShellInput = new Scanner(jShell.getInputStream());
                            jShellError = new Scanner(jShell.getErrorStream());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    case "play" -> {
                        Guild guild = event.getGuild();
                        Queue<Pair<MessageReceivedEvent, String>> queue = audioQueues.get(guild);
                        if (queue == null) {
                            queue = new ArrayDeque<>();
                            synchronized (audioQueues) {
                                audioQueues.put(guild, queue);
                            }
                        }
                        if (playing.get(guild) == null) {
                            synchronized (playing) {
                                playing.put(guild, false);
                            }
                        }
                        String url;
                        if (args[1].equals("ext")) {
                            url = getConfigurationManager(guild).getExtConfiguration(args[2]);
                        } else {
                            url = args[1];
                        }
                        synchronized (queue) {
                            queue.add(new Pair<>(event, url));
                        }
                    }
                    case "skip" -> playAudio(event, new String[]{"", "https://www.youtube.com/watch?v=Wch3gJG2GJ4"});
                    case "set" -> {
                        switch (args[1]) {
                            case "startup" -> {
                                final List<@NotNull String> startupSounds = (List<String>) getConfigurationManager(event.getGuild()).getConfiguration("startupSounds");
                                assert startupSounds != null;
                                startupSounds.clear();
                                startupSounds.add(args[2].replaceAll("\"", ""));
                            }
                            case "shutdown" -> {
                                final List<@NotNull String> shutdownSounds = (List<String>) getConfigurationManager(event.getGuild()).getConfiguration("shutdownSounds");
                                assert shutdownSounds != null;
                                shutdownSounds.clear();
                                shutdownSounds.add(args[2].replaceAll("\"", ""));
                            }
                            default -> throw new UnsupportedOperationException();
                        }
                    }
                    case "configure" -> {
                        switch (args[1]) {
                            case "startup" -> getConfigurationManager(event.getGuild()).setConfiguration("startupSoundConfig", SelectionFromListConfig.valueOf(args[2]));
                            case "shutdown" -> getConfigurationManager(event.getGuild()).setConfiguration("shutdownSoundConfig", SelectionFromListConfig.valueOf(args[2]));
                            case "ext" -> getConfigurationManager(event.getGuild()).setExtConfiguration(args[2], args[3].equals("null") ? null : args[3]);
                            default -> throw new UnsupportedOperationException();
                        }
                    }
                    case "add" -> {
                        switch (args[1]) {
                            case "startup" -> {
                                final List<@NotNull String> startupSounds = (List<String>) getConfigurationManager(event.getGuild()).getConfiguration("startupSounds");
                                assert startupSounds != null;
                                startupSounds.add(args[2].replaceAll("\"", ""));
                            }
                            case "shutdown" -> {
                                final List<@NotNull String> shutdownSounds = (List<String>) getConfigurationManager(event.getGuild()).getConfiguration("shutdownSounds");
                                assert shutdownSounds != null;
                                shutdownSounds.add(args[2].replaceAll("\"", ""));
                            }
                            default -> throw new UnsupportedOperationException();
                        }
                    }
                    case "readConfig" -> {
                        if ("ext".equals(args[1])) {
                            String value = getConfigurationManager(event.getGuild()).getExtConfiguration(args[2]);
                            event.getChannel().sendMessage(value == null ? "null" : value).queue();
                        } else {
                            throw new UnsupportedOperationException();
                        }
                    }
                    default -> throw new UnsupportedOperationException("Unknown command");
                }

            } else {
                try {
                    if (jShellChannel != null) {
                        if (!event.getMessage().getAuthor().isBot() && jShellChannel.equals(event.getChannel())) {
                            jShell.getOutputStream().write((msg + "\n").getBytes());
                            jShell.getOutputStream().flush();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            t.printStackTrace();
            for (String line : t.toString().split("\n")) {
                event.getChannel().sendMessage(line).queue();
            }
        }
    }

    private String preprocessUserInput(Guild guild, String input) {
        Pattern inputPattern = Pattern.compile("\\{(.*?)}");
        Matcher matchPattern = inputPattern.matcher(input);
        while (matchPattern.find()) {
            String original = matchPattern.group(1);
            String result = configurations.get(guild.getId()).getExtConfiguration(original);
            if (result != null) {
                input = input.replaceAll("\\{" + original + "}", result);
            }
        }
        return input;
    }

    @SuppressWarnings("unchecked")
    private void sendEnterChannelMessage(MessageReceivedEvent event, MessageChannel channel) {
        final List<@NotNull String> startupSounds = (List<String>) getConfigurationManager(event.getGuild()).getConfiguration("startupSounds");
        assert startupSounds != null;
        String sound = getFromListWithConfig(getConfigurationManager(event.getGuild()).getConfiguration("startupSoundConfig", SelectionFromListConfig.class), startupSounds);

        playAudio(event, new String[]{"", sound});
        channel.sendMessage("Entering channel").queue();
    }

    @SuppressWarnings("unchecked")
    private void sendLeaveChannelMessage(MessageReceivedEvent event) {
        if (jShellChannel != null) {
            final List<@NotNull String> shutdownSounds = (List<String>) getConfigurationManager(event.getGuild()).getConfiguration("shutdownSounds");
            assert shutdownSounds != null;
            String sound = getFromListWithConfig(getConfigurationManager(event.getGuild()).getConfiguration("shutdownSoundConfig", SelectionFromListConfig.class), shutdownSounds);
            playAudio(event, new String[]{"", sound});
            jShellChannel.sendMessage("Leaving channel").queue();
            jShellChannel = null;
        }
    }

    @NotNull
    private <T> T getFromListWithConfig(SelectionFromListConfig selectionConfig, List<T> list) {
        return switch (selectionConfig) {
            case Last -> list.get(list.size() - 1);
            case First -> list.get(0);
            case Random -> list.get((int) (new Random().nextFloat() * list.size()));
        };
    }
}
