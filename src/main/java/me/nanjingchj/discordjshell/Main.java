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
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

enum SoundEffectPlayingConfiguration {
    Last, First, Random
}

public class Main extends ListenerAdapter {
    public volatile MessageChannel channel = null;
    public volatile Process jShell;
    public volatile Scanner jShellInput;
    public volatile Scanner jShellError;
    private final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

    private final List<@NotNull String> startupSounds = new ArrayList<>();
    private SoundEffectPlayingConfiguration startupSoundConfig = SoundEffectPlayingConfiguration.Last;
    private final List<@NotNull String> shutdownSounds = new ArrayList<>();
    private SoundEffectPlayingConfiguration shutdownSoundConfig = SoundEffectPlayingConfiguration.Last;

    private final Queue<Pair<MessageReceivedEvent, String>> audios = new ArrayDeque<>(100);
    private volatile boolean playing = false;

    public Main() throws IOException {
        startupSounds.add("https://www.youtube.com/watch?v=7nQ2oiVqKHw");
        shutdownSounds.add("https://www.youtube.com/watch?v=Gb2jGy76v0Y");

        AudioSourceManagers.registerRemoteSources(playerManager);

        var processBuilder = new ProcessBuilder("jshell");
        jShell = processBuilder.start();
        jShellError = new Scanner(jShell.getErrorStream());
        jShellInput = new Scanner(jShell.getInputStream());

        new Thread(() -> {
            for (; ; ) {
                if (channel != null) {
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
                if (channel != null) {
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
                Pair<MessageReceivedEvent, String> audio = null;
                synchronized (audios) {
                    audio = audios.peek();
                }
                if (audio == null || playing) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }
                playAudio(audio.getFirst(), new String[]{"", audio.getSecond()});
                synchronized (audios) {
                    audios.poll();
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
        if (channel != null) {
            channel.sendMessage(t.toString()).queue();
        }
    }

    private void playAudio(MessageReceivedEvent event, String[] args) {
        var guild = event.getGuild();
        var musicChannels = guild.getVoiceChannelsByName("Music", true);

        if (musicChannels.isEmpty()) {
            guild.createVoiceChannel("Music").queue();
            musicChannels = guild.getVoiceChannelsByName("Music", true);
        }
        var musicChannel = musicChannels.get(0);
        var audioManager = guild.getAudioManager();
        final AudioTrack[] audioTrackToPlay = new AudioTrack[1];
        playerManager.loadItem(args[1], new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                System.out.println(audioTrack.getInfo().uri);
                audioTrackToPlay[0] = audioTrack;
            }

            @Override
            public void playlistLoaded(AudioPlaylist audioPlaylist) {
                audioTrackToPlay[0] = audioPlaylist.getTracks().get(0);
                System.out.println(audioTrackToPlay[0].getInfo().uri);
            }

            @Override
            public void noMatches() {
                System.err.println("No Matches");
            }

            @Override
            public void loadFailed(FriendlyException e) {
                e.printStackTrace();
            }
        });
        audioManager.setSendingHandler(new AudioSendHandler() {
            private final AudioPlayer audioPlayer;
            private AudioFrame lastFrame;

            {
                audioPlayer = playerManager.createPlayer();
                while (audioTrackToPlay[0] == null) {
                    Thread.onSpinWait();
                }
                audioPlayer.playTrack(audioTrackToPlay[0]);
                playing = true;
            }

            @Override
            public boolean canProvide() {
                lastFrame = audioPlayer.provide();
                boolean result = lastFrame != null;
                playing = result;
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

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        try {
            var msg = event.getMessage().getContentRaw();

            if (msg.startsWith("#")) {
                msg = msg.substring(1);
                String[] args = msg.split(" ");
                switch (args[0]) {
                    case "unbind" -> sendLeaveChannelMessage(event);
                    case "bind" -> {
                        sendLeaveChannelMessage(event);
                        Thread.sleep(2000);
                        channel = event.getChannel();
                        sendEnterChannelMessage(event, channel);
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
                        synchronized (audios) {
                            audios.add(new Pair<>(event, args[1]));
                        }
                    }
                    case "skip" -> {
                        playAudio(event, new String[]{"", "https://www.youtube.com/watch?v=Wch3gJG2GJ4"});
                    }
                    case "set" -> {
                        switch (args[1]) {
                            case "startup" -> {
                                startupSounds.clear();
                                startupSounds.add(args[2].replaceAll("\"", ""));
                            }
                            case "shutdown" -> {
                                shutdownSounds.clear();
                                shutdownSounds.add(args[2].replaceAll("\"", ""));
                            }
                            default -> throw new UnsupportedOperationException();
                        }
                    }
                    case "configure" -> {
                        switch (args[1]) {
                            case "startup" -> startupSoundConfig = SoundEffectPlayingConfiguration.valueOf(args[2]);
                            case "shutdown" -> shutdownSoundConfig = SoundEffectPlayingConfiguration.valueOf(args[2]);
                            default -> throw new UnsupportedOperationException();
                        }
                    }
                    case "add" -> {
                        switch (args[1]) {
                            case "startup" -> startupSounds.add(args[2].replaceAll("\"", ""));
                            case "shutdown" -> shutdownSounds.add(args[2].replaceAll("\"", ""));
                            default -> throw new UnsupportedOperationException();
                        }
                    }
                    default -> throw new UnsupportedOperationException("Unknown command");
                }

            } else {
                try {
                    if (channel != null) {
                        if (!event.getMessage().getAuthor().isBot() && channel.equals(event.getChannel())) {
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
                printActiveChannel(line);
            }
        }
    }

    private void sendEnterChannelMessage(MessageReceivedEvent event, MessageChannel channel) {
        String sound = getSoundStringFromListAndConfig(startupSoundConfig, startupSounds);
        playAudio(event, new String[]{"", sound});
        channel.sendMessage("Entering channel").queue();
    }

    private void sendLeaveChannelMessage(MessageReceivedEvent event) {
        if (channel != null) {
            String sound = getSoundStringFromListAndConfig(shutdownSoundConfig, shutdownSounds);
            playAudio(event, new String[]{"", sound});
            channel.sendMessage("Leaving channel").queue();
            channel = null;
        }
    }

    @NotNull
    private String getSoundStringFromListAndConfig(SoundEffectPlayingConfiguration soundConfig, List<String> sound) {
        return switch (soundConfig) {
            case Last -> sound.get(sound.size() - 1);
            case First -> sound.get(0);
            case Random -> sound.get((int) (new Random().nextFloat() * sound.size()));
        };
    }
}
