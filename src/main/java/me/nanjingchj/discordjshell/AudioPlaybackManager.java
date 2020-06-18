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
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public class AudioPlaybackManager {
    private final Map<Guild, Queue<Pair<MessageReceivedEvent, String>>> audioQueues = new HashMap<>();
    private final Map<Guild, Boolean> playing = new HashMap<>();
    private final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

    @SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter", "BusyWait"})
    public AudioPlaybackManager() {
        AudioSourceManagers.registerRemoteSources(playerManager);
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

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    public void play(Guild guild, MessageReceivedEvent event, String[] args) {
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
        String url = args[1];
        synchronized (queue) {
            queue.add(new Pair<>(event, url));
        }
    }

    public void playAudio(@NotNull MessageReceivedEvent event, String[] args) {
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
}
