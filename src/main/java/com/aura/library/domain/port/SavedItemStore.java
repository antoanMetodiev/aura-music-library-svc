package com.aura.library.domain.port;

import com.aura.library.domain.model.SavedEntry;
import com.aura.library.domain.model.SavedKind;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface SavedItemStore {

    /** Idempotent — saving twice keeps the first timestamp. */
    void save(SavedKind kind, UUID userId, UUID catalogId, Instant at);

    void remove(SavedKind kind, UUID userId, UUID catalogId);

    /** Most recently saved first. */
    List<SavedEntry> page(SavedKind kind, UUID userId, int limit, int offset);

    int count(SavedKind kind, UUID userId);

    /** Which of {@code catalogIds} the user has saved. */
    Set<UUID> contained(SavedKind kind, UUID userId, Collection<UUID> catalogIds);
}
