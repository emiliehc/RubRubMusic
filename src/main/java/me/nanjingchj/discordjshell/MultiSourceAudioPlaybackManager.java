package me.nanjingchj.discordjshell;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
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
    public void playQueued(Guild guild, String[] args) {
        String url = args[1];

        AudioTrack track = IAudioPlaybackManager.loadTrack(playerManager, url);
        playQueued(guild, track);
    }

    @Override
    public void playQueued(Guild guild, AudioTrack track) {
        var audioManager = guild.getAudioManager();
        ChannelWideAudioSendHandler handler = (ChannelWideAudioSendHandler) audioManager.getSendingHandler();
        if (handler == null) {
            handler = new ChannelWideAudioSendHandler();
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
            handler = new ChannelWideAudioSendHandler();
            audioManager.setSendingHandler(handler);
            audioManager.openAudioConnection(getMusicChannel(event.getGuild()));
        }

        handler.forcePlay(IAudioPlaybackManager.loadTrack(playerManager, url));
    }

    @Override
    public void skip(@NotNull Guild guild) {
        ChannelWideAudioSendHandler handler = (ChannelWideAudioSendHandler) guild.getAudioManager().getSendingHandler();
        if (handler == null) {
            // first time
            handler = new ChannelWideAudioSendHandler();
            guild.getAudioManager().setSendingHandler(handler);
            guild.getAudioManager().openAudioConnection(getMusicChannel(guild));
        }
        handler.skip();
    }

    @Override
    public AudioPlayerManager getAudioPlayerManger() {
        return playerManager;
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
        private final AudioPlayer audioPlayer;
        private final Queue<AudioTrack> audioTracks;
        private AudioTrack backupTrack = null;
        private boolean playingInjectedTrack = false;
        private AudioTrack activeTrack = null;
        private AudioFrame lastFrame;

        // ctor
        public ChannelWideAudioSendHandler() {
            audioTracks = new ArrayDeque<>(10);
            audioPlayer = playerManager.createPlayer();
        }

        public void addTrack(AudioTrack track) {
            audioTracks.offer(track);
        }

        public void forcePlay(AudioTrack track) {
            if (!playingInjectedTrack) {
                playingInjectedTrack = true;
                backupTrack = activeTrack;
            }
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
                // no track to play, or finished
                if (activeTrack == null || activeTrack.getState() == AudioTrackState.FINISHED) {
                    if (playingInjectedTrack) {
                        // restore previous track
                        if (backupTrack != null) {
                            activeTrack = backupTrack.makeClone();
                            activeTrack.setPosition(backupTrack.getPosition());
                            backupTrack = null;
                            playingInjectedTrack = false;
                        } else {
                            // no previous track
                            return false;
                        }
                    } else {
                        // play the next available track
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
