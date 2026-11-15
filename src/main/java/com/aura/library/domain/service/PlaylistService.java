package com.aura.library.domain.service;

import com.aura.library.config.LibraryProperties;
import com.aura.library.domain.model.Playlist;
import com.aura.library.domain.model.PlaylistItem;
import com.aura.library.domain.model.SavedKind;
import com.aura.library.domain.port.CatalogLookup;
import com.aura.library.domain.port.PlaylistStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Playlists and their items. Positions are dense (0..n-1) and every item change locks the playlist
 * row first, so two tabs adding at once can't collide. Catalog calls (validating ids, hydrating
 * tracks) happen outside the transaction — no connection is held while waiting on the network.
 */
@Service
public class PlaylistService {

    private final PlaylistStore store;
    private final CatalogLookup catalog;
    private final LibraryProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PlaylistService(PlaylistStore store, CatalogLookup catalog, LibraryProperties properties,
                           TransactionTemplate tx, Clock clock) {
        this.store = store;
        this.catalog = catalog;
        this.properties = properties;
        this.tx = tx;
        this.clock = clock;
    }

    /** A playlist plus its cover: the first track's artwork (catalog JSON), or null when empty. */
    public record View(Playlist playlist, Object artwork) {
    }

    public record Item(PlaylistItem item, Map<String, Object> track) {
    }

    public record Detail(View view, List<Item> items) {
    }

    public List<View> listOwn(UUID userId) {
        return withCovers(store.findByOwner(userId));
    }

    public View create(UUID userId, String title, String description, boolean isPublic) {
        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(s -> {
            if (store.countByOwner(userId) >= properties.maxPlaylistsPerUser()) {
                throw new LibraryExceptions.LimitExceeded("At most " + properties.maxPlaylistsPerUser() + " playlists per user");
            }
            store.insert(id, userId, clean(title), cleanOrNull(description), isPublic, Instant.now(clock));
        });
        return new View(store.find(id).orElseThrow(), null);
    }

    public Detail get(UUID userId, UUID playlistId) {
        Playlist playlist = visible(userId, playlistId);
        List<PlaylistItem> items = store.items(playlistId);
        Map<UUID, Map<String, Object>> tracks = items.isEmpty()
                ? Map.of()
                : catalog.find(SavedKind.TRACK, items.stream().map(PlaylistItem::trackId).distinct().toList());
        List<Item> hydrated = items.stream()
                .filter(i -> tracks.containsKey(i.trackId()))
                .map(i -> new Item(i, tracks.get(i.trackId())))
                .toList();
        Object cover = hydrated.isEmpty() ? null : hydrated.getFirst().track().get("artwork");
        return new Detail(new View(playlist, cover), hydrated);
    }

    /** {@code null} fields are left as they are. */
    public View update(UUID userId, UUID playlistId, String title, String description, Boolean isPublic) {
        tx.executeWithoutResult(s -> {
            Playlist p = owned(userId, playlistId, true);
            store.update(playlistId,
                    title == null ? p.title() : clean(title),
                    description == null ? p.description() : cleanOrNull(description),
                    isPublic == null ? p.isPublic() : isPublic,
                    Instant.now(clock));
        });
        return withCovers(List.of(store.find(playlistId).orElseThrow())).getFirst();
    }

    public void delete(UUID userId, UUID playlistId) {
        tx.executeWithoutResult(s -> {
            owned(userId, playlistId, true);
            store.delete(playlistId);
        });
    }

    /** Inserts at {@code position} (clamped), or appends when null. Returns the new items. */
    public List<PlaylistItem> addTracks(UUID userId, UUID playlistId, List<UUID> trackIds, Integer position) {
        owned(userId, playlistId, false); // fail fast before the catalog round trip
        Map<UUID, Map<String, Object>> known = catalog.find(SavedKind.TRACK, new HashSet<>(trackIds));
        trackIds.stream().filter(id -> !known.containsKey(id)).findFirst().ifPresent(id -> {
            throw SavedItemService.notFound(SavedKind.TRACK, id);
        });

        return tx.execute(s -> {
            Playlist p = owned(userId, playlistId, true);
            if (p.trackCount() + trackIds.size() > properties.maxTracksPerPlaylist()) {
                throw new LibraryExceptions.LimitExceeded("At most " + properties.maxTracksPerPlaylist() + " tracks per playlist");
            }
            int at = position == null ? p.trackCount() : Math.clamp(position, 0, p.trackCount());
            Instant now = Instant.now(clock);
            store.shiftFrom(playlistId, at, trackIds.size());
            List<PlaylistItem> added = new java.util.ArrayList<>();
            for (int i = 0; i < trackIds.size(); i++) {
                UUID itemId = UUID.randomUUID();
                store.insertItem(playlistId, itemId, trackIds.get(i), at + i, now);
                added.add(new PlaylistItem(itemId, trackIds.get(i), at + i, now));
            }
            store.touch(playlistId, now);
            return added;
        });
    }

    public void removeItem(UUID userId, UUID playlistId, UUID itemId) {
        tx.executeWithoutResult(s -> {
            owned(userId, playlistId, true);
            PlaylistItem item = item(playlistId, itemId);
            store.deleteItem(itemId);
            store.shiftFrom(playlistId, item.position() + 1, -1);
            store.touch(playlistId, Instant.now(clock));
        });
    }

    /** Moves one item to {@code position} (clamped); everything in between slides one place. */
    public void moveItem(UUID userId, UUID playlistId, UUID itemId, int position) {
        tx.executeWithoutResult(s -> {
            Playlist p = owned(userId, playlistId, true);
            PlaylistItem item = item(playlistId, itemId);
            int from = item.position();
            int to = Math.clamp(position, 0, Math.max(p.trackCount() - 1, 0));
            if (from == to) return;
            if (to < from) store.shiftRange(playlistId, to, from - 1, 1);
            else store.shiftRange(playlistId, from + 1, to, -1);
            store.setItemPosition(itemId, to);
            store.touch(playlistId, Instant.now(clock));
        });
    }

    private Playlist visible(UUID userId, UUID playlistId) {
        return store.find(playlistId)
                .filter(p -> p.visibleTo(userId))
                .orElseThrow(() -> playlistNotFound(playlistId));
    }

    private Playlist owned(UUID userId, UUID playlistId, boolean lock) {
        Playlist p = (lock ? store.findForUpdate(playlistId) : store.find(playlistId))
                .filter(x -> x.visibleTo(userId))
                .orElseThrow(() -> playlistNotFound(playlistId));
        if (!p.ownerId().equals(userId)) {
            throw new LibraryExceptions.Forbidden("Only the owner can change this playlist");
        }
        return p;
    }

    private PlaylistItem item(UUID playlistId, UUID itemId) {
        return store.findItem(playlistId, itemId)
                .orElseThrow(() -> new LibraryExceptions.NotFound("PLAYLIST_ITEM_NOT_FOUND", "No item " + itemId + " in this playlist"));
    }

    private List<View> withCovers(List<Playlist> playlists) {
        List<UUID> coverIds = playlists.stream().map(Playlist::coverTrackId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, Map<String, Object>> covers;
        try {
            covers = coverIds.isEmpty() ? Map.of() : catalog.find(SavedKind.TRACK, coverIds);
        } catch (LibraryExceptions.CatalogUnavailable e) {
            covers = Map.of(); // the sidebar list still works without pictures
        }
        Map<UUID, Map<String, Object>> found = covers;
        return playlists.stream()
                .map(p -> new View(p, p.coverTrackId() == null || !found.containsKey(p.coverTrackId())
                        ? null : found.get(p.coverTrackId()).get("artwork")))
                .toList();
    }

    private static LibraryExceptions.NotFound playlistNotFound(UUID id) {
        return new LibraryExceptions.NotFound("PLAYLIST_NOT_FOUND", "Playlist " + id + " not found");
    }

    private static String clean(String title) {
        String t = title == null ? "" : title.strip();
        if (t.isEmpty()) throw new IllegalArgumentException("'title' must not be blank");
        if (t.length() > 200) throw new IllegalArgumentException("'title' is longer than 200 characters");
        return t;
    }

    private static String cleanOrNull(String description) {
        if (description == null) return null;
        String d = description.strip();
        if (d.length() > 1000) throw new IllegalArgumentException("'description' is longer than 1000 characters");
        return d.isEmpty() ? null : d;
    }
}
