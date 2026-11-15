package com.aura.library.domain.port;

import com.aura.library.domain.model.Playlist;
import com.aura.library.domain.model.PlaylistItem;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaylistStore {

    void insert(UUID id, UUID ownerId, String title, String description, boolean isPublic, Instant at);

    Optional<Playlist> find(UUID id);

    /** Takes a row lock — every item change goes through this first, so positions stay dense under concurrency. */
    Optional<Playlist> findForUpdate(UUID id);

    /** Newest first. */
    List<Playlist> findByOwner(UUID ownerId);

    int countByOwner(UUID ownerId);

    void update(UUID id, String title, String description, boolean isPublic, Instant at);

    void touch(UUID id, Instant at);

    void delete(UUID id);

    List<PlaylistItem> items(UUID playlistId);

    Optional<PlaylistItem> findItem(UUID playlistId, UUID itemId);

    /** Moves every item at {@code position >= from} down by {@code by} (may be negative). */
    void shiftFrom(UUID playlistId, int from, int by);

    /** Shifts items in the inclusive range {@code [from, to]} by {@code by}. */
    void shiftRange(UUID playlistId, int from, int to, int by);

    void insertItem(UUID playlistId, UUID itemId, UUID trackId, int position, Instant at);

    void setItemPosition(UUID itemId, int position);

    void deleteItem(UUID itemId);
}
