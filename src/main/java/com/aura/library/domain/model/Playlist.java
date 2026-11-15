package com.aura.library.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A playlist's own row. {@code trackCount} and {@code coverTrackId} (the first item, whose artwork
 * stands in for a cover until uploads exist) are read alongside it, never stored.
 */
public record Playlist(
        UUID id,
        UUID ownerId,
        String title,
        String description,
        boolean isPublic,
        int trackCount,
        UUID coverTrackId,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean visibleTo(UUID userId) {
        return isPublic || ownerId.equals(userId);
    }
}
