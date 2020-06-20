package me.nanjingchj.discordjshell;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;

public interface IPlaylist {
    void insert(AudioTrack track);

    void add(AudioTrack track);

    void remove(AudioTrack track);

    void removeCurrent();

    void resetPosition();

    void reload(AudioPlayerManager manager);

    void clear();

    AudioTrack previous();

    AudioTrack next();

    void playAll(IAudioPlaybackManager manager, Guild guild);

    void setName(String name);

    String getName();
}

