package com.aura.library.domain.model;

import java.time.Instant;
import java.util.UUID;

/** One liked track / saved album / saved artist: the catalog id and when it was saved. */
public record SavedEntry(UUID catalogId, Instant savedAt) {
}
