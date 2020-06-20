package me.nanjingchj.discordjshell;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;

public interface IAudioPlaybackManager {
    /**
     * Adds the audio to a queue, waiting to be played later
     *
     * @param guild the channel
     * @param args contains the url
     */
    void playQueued(Guild guild, String[] args);

    void playQueued(Guild guild, AudioTrack track);

    /**
     * Plays an audio right now
     *
     * @param event the event that triggers the bot to play audio
     * @param args contains the url
     */
    void playAudio(@NotNull MessageReceivedEvent event, String[] args);

    void skip(@NotNull Guild event);

    AudioPlayerManager getAudioPlayerManger();

    static AudioTrack loadTrack(AudioPlayerManager playerManager, String url) {
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
}
