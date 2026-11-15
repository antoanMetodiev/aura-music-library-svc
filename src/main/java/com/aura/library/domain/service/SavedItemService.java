package com.aura.library.domain.service;

import com.aura.library.domain.model.SavedEntry;
import com.aura.library.domain.model.SavedKind;
import com.aura.library.domain.port.CatalogLookup;
import com.aura.library.domain.port.SavedItemStore;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Liked tracks, saved albums, saved artists — one implementation, three collections. */
@Service
public class SavedItemService {

    public static final int MAX_PAGE = 100;

    private final SavedItemStore store;
    private final CatalogLookup catalog;
    private final Clock clock;

    public SavedItemService(SavedItemStore store, CatalogLookup catalog, Clock clock) {
        this.store = store;
        this.catalog = catalog;
        this.clock = clock;
    }

    /** An entry with the catalog entity it points at. */
    public record Hydrated(Map<String, Object> item, Instant savedAt) {
    }

    public record Page(List<Hydrated> items, int total, int limit, int offset) {
    }

    /** Only ids the catalog knows can be saved — the client may hold a stale or made-up one. */
    public void save(SavedKind kind, UUID userId, UUID catalogId) {
        if (!catalog.find(kind, List.of(catalogId)).containsKey(catalogId)) {
            throw notFound(kind, catalogId);
        }
        store.save(kind, userId, catalogId, Instant.now(clock));
    }

    public void remove(SavedKind kind, UUID userId, UUID catalogId) {
        store.remove(kind, userId, catalogId);
    }

    public Page page(SavedKind kind, UUID userId, int limit, int offset) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_PAGE);
        int safeOffset = Math.max(offset, 0);
        List<SavedEntry> entries = store.page(kind, userId, safeLimit, safeOffset);
        Map<UUID, Map<String, Object>> found = entries.isEmpty()
                ? Map.of()
                : catalog.find(kind, entries.stream().map(SavedEntry::catalogId).toList());
        // An entity the catalog has since dropped (a merged duplicate artist, say) is skipped, not shown blank.
        List<Hydrated> items = entries.stream()
                .filter(e -> found.containsKey(e.catalogId()))
                .map(e -> new Hydrated(found.get(e.catalogId()), e.savedAt()))
                .toList();
        return new Page(items, store.count(kind, userId), safeLimit, safeOffset);
    }

    /** For each id, in order, whether the user saved it — what a heart button needs. */
    public List<Boolean> contains(SavedKind kind, UUID userId, List<UUID> catalogIds) {
        Set<UUID> saved = store.contained(kind, userId, catalogIds);
        return catalogIds.stream().map(saved::contains).toList();
    }

    static LibraryExceptions.NotFound notFound(SavedKind kind, UUID id) {
        String name = switch (kind) {
            case TRACK -> "Track";
            case ALBUM -> "Album";
            case ARTIST -> "Artist";
        };
        return new LibraryExceptions.NotFound(name.toUpperCase() + "_NOT_FOUND", name + " " + id + " is not in the catalog");
    }
}
