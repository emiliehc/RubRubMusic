package me.nanjingchj.discordjshell;

import org.jetbrains.annotations.Nullable;

public interface IPlaylistManager {
    @Nullable IPlaylist getActivePlaylist();

    void setActivePlaylist(IPlaylist playlist);

    @Nullable IPlaylist getList(String name);

    void addList(IPlaylist list);

    void removeList(String name);

    void removeList(IPlaylist list);
}
