package me.nanjingchj.discordjshell;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main extends ListenerAdapter {
    private volatile MessageChannel jShellChannel = null;
    private volatile Process jShell;
    private volatile Scanner jShellInput;
    private volatile Scanner jShellError;
    private ConfigurationManager configurationManager;
    public final static IAudioPlaybackManager audioPlaybackManager;

    static {
        audioPlaybackManager = new MultiSourceAudioPlaybackManager();
    }

    @SuppressWarnings("BusyWait")
    public Main() throws IOException {
        File f = new File("config");
        // backup

        if (!f.exists()) {
            if (!f.createNewFile()) {
                throw new Error("This should never be thrown");
            }
            configurationManager = new ConfigurationManager();
        } else {
            try (ObjectInputStream oos = new ObjectInputStream(new FileInputStream(f))) {
                configurationManager = (ConfigurationManager) oos.readObject();
            } catch (Exception e) { // TODO : narrow this down
                e.printStackTrace();
                configurationManager = new ConfigurationManager();
                configurationManager.modified();
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
                } else {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();

        new Thread(() -> {
            Scanner sc = new Scanner(System.in);
            for (; ; ) {
                if (sc.hasNextLine()) {
                    var str = sc.nextLine();
                    try {
                        jShell.getOutputStream().write((str + "\n").getBytes());
                        jShell.getOutputStream().flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();


        new Thread(() -> {
            for (; ; ) {
                if (jShellChannel != null) {
                    if (jShellError.hasNextLine()) {
                        var msg = jShellError.nextLine();
                        System.out.println(msg);
                        if (!msg.trim().isEmpty()) {
                            printActiveChannel(msg.concat("\n"));
                        }
                    } else {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

        }).start();
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

    @SuppressWarnings("unchecked")
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
                    case "play" -> audioPlaybackManager.playQueued(event.getGuild(), args);
                    case "search" -> {
                        String queryTerms = msg.substring(7);
                        String url = "https://www.youtube.com/watch?v=" + YouTubeSearch.search(queryTerms);
                        event.getChannel().sendMessage("Found: " + url).queue();
                    }
                    case "playSearch" -> {
                        String queryTerms = msg.substring(11);
                        String url = "https://www.youtube.com/watch?v=" + YouTubeSearch.search(queryTerms);
                        event.getChannel().sendMessage("▶ Found: " + url + "\nAdded to queue").queue();
                        audioPlaybackManager.playQueued(event.getGuild(), new String[]{"", url});
                    }
                    case "skip" -> audioPlaybackManager.skip(event.getGuild());
                    case "set" -> {
                        switch (args[1]) {
                            case "startup" -> {
                                final List<@NotNull String> startupSounds = (List<String>) configurationManager.getConfiguration(event.getGuild().getId(), "startupSounds");
                                assert startupSounds != null;
                                startupSounds.clear();
                                startupSounds.add(args[2].replaceAll("\"", ""));
                                configurationManager.modified();
                            }
                            case "shutdown" -> {
                                final List<@NotNull String> shutdownSounds = (List<String>) configurationManager.getConfiguration(event.getGuild().getId(), "shutdownSounds");
                                assert shutdownSounds != null;
                                shutdownSounds.clear();
                                shutdownSounds.add(args[2].replaceAll("\"", ""));
                                configurationManager.modified();
                            }
                            default -> throw new UnsupportedOperationException();
                        }
                    }
                    case "configure" -> {
                        switch (args[1]) {
                            case "startup" -> configurationManager.setConfiguration(event.getGuild().getId(), "startupSoundConfig", SelectionFromListConfig.valueOf(args[2]));
                            case "shutdown" -> configurationManager.setConfiguration(event.getGuild().getId(), "shutdownSoundConfig", SelectionFromListConfig.valueOf(args[2]));
                            case "ext" -> configurationManager.setExtConfiguration(event.getGuild().getId(), args[2], args[3].equals("null") ? null : args[3]);
                            default -> throw new UnsupportedOperationException();
                        }
                    }
                    case "insert" -> {
                        switch (args[1]) {
                            case "startup" -> {
                                final List<@NotNull String> startupSounds = (List<String>) configurationManager.getConfiguration(event.getGuild().getId(), "startupSounds");
                                assert startupSounds != null;
                                startupSounds.add(args[2].replaceAll("\"", ""));
                                configurationManager.modified();
                            }
                            case "shutdown" -> {
                                final List<@NotNull String> shutdownSounds = (List<String>) configurationManager.getConfiguration(event.getGuild().getId(), "shutdownSounds");
                                assert shutdownSounds != null;
                                shutdownSounds.add(args[2].replaceAll("\"", ""));
                                configurationManager.modified();
                            }
                            default -> throw new UnsupportedOperationException();
                        }
                    }
                    case "readConfig" -> {
                        if ("ext".equals(args[1])) {
                            String value = configurationManager.getExtConfiguration(event.getGuild().getId(), args[2]);
                            event.getChannel().sendMessage(value == null ? "null" : value).queue();
                        } else {
                            throw new UnsupportedOperationException();
                        }
                    }
                    case "playlist" -> {
                        IPlaylistManager playlistManager = configurationManager.getConfiguration(event.getGuild().getId(), "playlistManager", IPlaylistManager.class);
                        if (playlistManager == null) {
                            playlistManager = new PlaylistManager();
                            configurationManager.setConfiguration(event.getGuild().getId(), "playlistManager", playlistManager);
                        }
                        switch (args[1]) {
                            case "load" -> playlistManager.setActivePlaylist(Objects.requireNonNull(playlistManager.getList(args[2])));
                            case "addItem" -> {
                                String url = args[2];
                                AudioTrack track = IAudioPlaybackManager.loadTrack(audioPlaybackManager.getAudioPlayerManger(), url);
                                Objects.requireNonNull(playlistManager.getActivePlaylist()).add(track);
                                configurationManager.modified();
                            }
                            case "removeItem" -> {
                                Objects.requireNonNull(playlistManager.getActivePlaylist()).removeCurrent();
                                configurationManager.modified();
                            }
                            case "insertItem" -> {
                                String url = args[2];
                                AudioTrack track = IAudioPlaybackManager.loadTrack(audioPlaybackManager.getAudioPlayerManger(), url);
                                Objects.requireNonNull(playlistManager.getActivePlaylist()).insert(track);
                                configurationManager.modified();
                            }
                            case "create" -> {
                                String name = args[2];
                                IPlaylist newPlaylist = new DefaultPlaylist(name);
                                playlistManager.addList(newPlaylist);
                                configurationManager.modified();
                            }
                            case "remove" -> {
                                String name = args[2];
                                playlistManager.removeList(name);
                                configurationManager.modified();
                            }
                            case "next" -> {
                                AudioTrack track = Objects.requireNonNull(playlistManager.getActivePlaylist()).next();
                                audioPlaybackManager.playQueued(event.getGuild(), track);
                                audioPlaybackManager.skip(event.getGuild());
                            }
                            case "previous" -> {
                                AudioTrack track = Objects.requireNonNull(playlistManager.getActivePlaylist()).previous();
                                audioPlaybackManager.playQueued(event.getGuild(), track);
                                configurationManager.modified();
                                audioPlaybackManager.skip(event.getGuild());
                            }
                            case "clear" -> {
                                Objects.requireNonNull(playlistManager.getActivePlaylist()).clear();
                                configurationManager.modified();
                            }
                            case "resetPos" -> Objects.requireNonNull(playlistManager.getActivePlaylist()).resetPosition();
                            case "addAll" -> Objects.requireNonNull(playlistManager.getActivePlaylist()).playAll(audioPlaybackManager, event.getGuild());
                            default -> throw new UnsupportedOperationException("Unknown command");
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
            String result = configurationManager.getExtConfiguration(guild.getId(), original);
            if (result != null) {
                input = input.replaceAll("\\{" + original + "}", result);
            }
        }
        return input;
    }

    @SuppressWarnings("unchecked")
    private void sendEnterChannelMessage(MessageReceivedEvent event, MessageChannel channel) {
        final List<@NotNull String> startupSounds = (List<String>) configurationManager.getConfiguration(event.getGuild().getId(), "startupSounds");
        assert startupSounds != null;
        String sound = getFromListWithConfig(configurationManager.getConfiguration(event.getGuild().getId(), "startupSoundConfig", SelectionFromListConfig.class), startupSounds);

        audioPlaybackManager.playAudio(event, new String[]{"", sound});
        channel.sendMessage("Entering channel").queue();
    }

    @SuppressWarnings("unchecked")
    private void sendLeaveChannelMessage(MessageReceivedEvent event) {
        if (jShellChannel != null) {
            final List<@NotNull String> shutdownSounds = (List<String>) configurationManager.getConfiguration(event.getGuild().getId(), "shutdownSounds");
            assert shutdownSounds != null;
            String sound = getFromListWithConfig(configurationManager.getConfiguration(event.getGuild().getId(), "shutdownSoundConfig", SelectionFromListConfig.class), shutdownSounds);
            audioPlaybackManager.playAudio(event, new String[]{"", sound});
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
