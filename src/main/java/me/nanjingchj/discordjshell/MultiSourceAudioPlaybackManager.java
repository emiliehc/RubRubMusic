package me.nanjingchj.discordjshell;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackState;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;

public class MultiSourceAudioPlaybackManager implements IAudioPlaybackManager {
    private final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

    public MultiSourceAudioPlaybackManager() {
        AudioSourceManagers.registerRemoteSources(playerManager);
    }

    /**
     * The music will be resolved immediately and the {@link AudioTrack} object
     * will be immediately obtained from there. Then an {@link AudioPlayer} will
     * be created and the {@link AudioTrack} will be used played. However,
     * the {@link AudioPlayer#provide()} method will not be called until the turn
     * comes. There will only be one {@link AudioSendHandler} for each channel!
     * When JDA prompts the {@link AudioSendHandler} to provide the last frame,
     * it checks the active audio source and gets the last frame from there.
     * Coordination between the {@link AudioSendHandler#canProvide()} method and
     * the {@link AudioSendHandler#provide20MsAudio()} should be carefully
     * implemented.
     * After finishing all of this, try to use a thread pool to offload the work
     * so that this function stays responsive.
     */
    @Override
    public void playQueued(Guild guild, MessageReceivedEvent event, String[] args) {
        String url = args[1];

        AudioTrack track = loadTrack(event, guild, url);
        var audioManager = guild.getAudioManager();
        ChannelWideAudioSendHandler handler = (ChannelWideAudioSendHandler) audioManager.getSendingHandler();
        if (handler == null) {
            // first time
            handler = new ChannelWideAudioSendHandler(guild);
            audioManager.setSendingHandler(handler);
            audioManager.openAudioConnection(getMusicChannel(guild));
        }

        handler.addTrack(track);
    }

    // "hijacks" the current audio stream and forces the new audio to be played
    @Override
    public void playAudio(@NotNull MessageReceivedEvent event, String[] args) {
        String url = args[1];
        var audioManager = event.getGuild().getAudioManager();
        ChannelWideAudioSendHandler handler = (ChannelWideAudioSendHandler) audioManager.getSendingHandler();
        if (handler == null) {
            // first time
            handler = new ChannelWideAudioSendHandler(event.getGuild());
            audioManager.setSendingHandler(handler);
            audioManager.openAudioConnection(getMusicChannel(event.getGuild()));
        }

        handler.forcePlay(loadTrack(event, event.getGuild(), url));
    }

    private AudioTrack loadTrack(MessageReceivedEvent event, Guild guild, String url) {
        final AudioTrack[] audioTrackToPlay = new AudioTrack[1];
        final boolean[] loadingError = {false};
        playerManager.loadItem(url, new AudioLoadResultHandler() {
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
                System.err.println("No matches");
                event.getChannel().sendMessage("No matches").queue();
                loadingError[0] = true;
            }

            @Override
            public void loadFailed(FriendlyException e) {
                e.printStackTrace();
                loadingError[0] = true;
            }
        });
        while (audioTrackToPlay[0] == null) {
            Thread.onSpinWait();
            if (loadingError[0]) {
                throw new RuntimeException();
                // method returns
            }
        }
        return audioTrackToPlay[0];
    }

    @Override
    public void skip(@NotNull MessageReceivedEvent event) {
        ChannelWideAudioSendHandler handler = (ChannelWideAudioSendHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (handler == null) {
            // first time
            handler = new ChannelWideAudioSendHandler(event.getGuild());
            event.getGuild().getAudioManager().setSendingHandler(handler);
            event.getGuild().getAudioManager().openAudioConnection(getMusicChannel(event.getGuild()));
        }
        handler.skip();
    }

    private VoiceChannel getMusicChannel(Guild guild) {
        var musicChannels = guild.getVoiceChannelsByName("Music", true);

        if (musicChannels.isEmpty()) {
            guild.createVoiceChannel("Music").queue();
            musicChannels = guild.getVoiceChannelsByName("Music", true);
        }
        return musicChannels.get(0);
    }

    private class ChannelWideAudioSendHandler implements AudioSendHandler {
        private final Guild guild;
        private final AudioPlayer audioPlayer;
        private final Queue<AudioTrack> audioTracks;
        private AudioTrack backupTrack = null;
        private boolean playingInjectedTrack = false;
        private AudioTrack activeTrack = null;
        private AudioFrame lastFrame;

        // ctor
        public ChannelWideAudioSendHandler(Guild guild) {
            this.guild = guild;
            audioTracks = new ArrayDeque<>(10);
            audioPlayer = playerManager.createPlayer();
        }

        public Guild getGuild() {
            return guild;
        }

        public void addTrack(AudioTrack track) {
            audioTracks.offer(track);
        }

        public void forcePlay(AudioTrack track) {
            playingInjectedTrack = true;
            backupTrack = activeTrack;
            activeTrack = track;
            audioPlayer.playTrack(activeTrack);
        }

        public void skip() {
            AudioTrack next = audioTracks.poll();
            if (next == null) {
                activeTrack = null;
                audioPlayer.stopTrack();
                return;
            }
            activeTrack = next;
            audioPlayer.playTrack(activeTrack);
        }

        @Override
        public boolean canProvide() {
            lastFrame = audioPlayer.provide();
            if (lastFrame == null) {
                if (activeTrack == null || activeTrack.getState() == AudioTrackState.FINISHED) {
                    if (playingInjectedTrack) {
                        // restore previous track
                        activeTrack = backupTrack.makeClone();
                        activeTrack.setPosition(backupTrack.getPosition());
                        backupTrack = null;
                        playingInjectedTrack = false;
                    } else {
                        AudioTrack next = audioTracks.poll();
                        if (next == null) {
                            return false;
                        }
                        activeTrack = next;
                    }

                    audioPlayer.playTrack(activeTrack);
                }
            }
            return lastFrame != null;
        }

        @Nullable
        @Override
        public ByteBuffer provide20MsAudio() {
            return ByteBuffer.wrap(lastFrame.getData());
        }

        @Override
        public boolean isOpus() {
            return true;
        }
    }
}
