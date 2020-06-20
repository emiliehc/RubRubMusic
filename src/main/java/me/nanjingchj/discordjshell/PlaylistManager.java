package me.nanjingchj.discordjshell;

import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

public class PlaylistManager implements IPlaylistManager, Serializable {
    private final List<IPlaylist> playlists;
    private transient IPlaylist activePlaylist;

    public PlaylistManager() {
        playlists = new LinkedList<>();
    }

    @Override
    public @Nullable IPlaylist getActivePlaylist() {
        return activePlaylist;
    }

    @Override
    public void setActivePlaylist(IPlaylist playlist) {
        activePlaylist = playlist;
    }

    @Override
    public @Nullable IPlaylist getList(String name) {
        for (IPlaylist playlist : playlists) {
            if (playlist.getName().equals(name)) {
                return playlist;
            }
        }
        return null;
    }

    @Override
    public void addList(IPlaylist list) {
        playlists.add(list);
    }

    @Override
    public void removeList(String name) {
        playlists.remove(getList(name));
    }

    @Override
    public void removeList(IPlaylist list) {
        playlists.remove(list);
    }
}
