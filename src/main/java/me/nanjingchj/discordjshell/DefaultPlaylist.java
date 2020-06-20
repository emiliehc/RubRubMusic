package me.nanjingchj.discordjshell;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class DefaultPlaylist implements IPlaylist, Serializable {
    private String name;
    private List<String> audioTrackStrings;
    private transient List<AudioTrack> audioTracks;
    private transient int index;

    public DefaultPlaylist(String name) {
        this.name = name;
        audioTrackStrings = new ArrayList<>();
        audioTracks = new LinkedList<>();
        index = 0;
    }

    @Override
    public void insert(AudioTrack track) {
        audioTrackStrings.add(index, track.getInfo().uri);
        audioTracks.add(index, track);
    }

    @Override
    public void add(AudioTrack track) {
        audioTrackStrings.add(track.getInfo().uri);
        audioTracks.add(track);
    }

    @Override
    public void remove(AudioTrack track) {
        audioTrackStrings.remove(track.getInfo().uri);
        audioTracks.remove(track);
    }

    @Override
    public void removeCurrent() {
        audioTrackStrings.remove(index - 1);
        audioTracks.remove(index - 1);
    }

    @Override
    public void resetPosition() {
        index = 0;
    }

    @Override
    public void reload(AudioPlayerManager manager) {
        audioTracks = new LinkedList<>();
        for (String trackString : audioTrackStrings) {
            try {
                audioTracks.add(IAudioPlaybackManager.loadTrack(manager, trackString));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        resetPosition();
    }

    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream inputStream) throws ClassNotFoundException, IOException {
        name = (String) inputStream.readObject();
        audioTrackStrings = (List<String>) inputStream.readObject();
        reload(Main.audioPlaybackManager.getAudioPlayerManger());
    }

    private void writeObject(ObjectOutputStream outputStream) throws IOException {
        outputStream.writeObject(name);
        outputStream.writeObject(audioTrackStrings);
    }

    @Override
    public void clear() {
        audioTrackStrings.clear();
        audioTracks.clear();
    }

    @Override
    public AudioTrack previous() {
        index--;
        return audioTracks.get(index - 1).makeClone();
    }

    @Override
    public AudioTrack next() {
        try {
            return audioTracks.get(index).makeClone();
        } finally {
            index++;
        }
    }

    @Override
    public void playAll(IAudioPlaybackManager manager, Guild guild) {
        audioTracks.forEach(audioTrack -> manager.playQueued(guild, audioTrack.makeClone()));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }
}
