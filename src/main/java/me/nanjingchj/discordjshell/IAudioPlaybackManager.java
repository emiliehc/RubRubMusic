package me.nanjingchj.discordjshell;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;

public interface IAudioPlaybackManager {
    /**
     * Adds the audio to a queue, waiting to be played later
     *
     * @param guild the channel
     * @param event the event that triggers the bot to play audio
     * @param args contains the url
     */
    void playQueued(Guild guild, MessageReceivedEvent event, String[] args);

    /**
     * Plays an audio right now
     *
     * @param event the event that triggers the bot to play audio
     * @param args contains the url
     */
    void playAudio(@NotNull MessageReceivedEvent event, String[] args);

    void skip(@NotNull MessageReceivedEvent event);
}
