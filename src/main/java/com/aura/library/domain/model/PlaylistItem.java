package com.aura.library.domain.model;

import java.time.Instant;
import java.util.UUID;

/** One occurrence of a track in a playlist — a track id reference, never a copy of the track (§23). */
public record PlaylistItem(UUID id, UUID trackId, int position, Instant addedAt) {
}
