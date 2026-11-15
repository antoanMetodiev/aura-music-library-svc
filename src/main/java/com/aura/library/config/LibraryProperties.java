package com.aura.library.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Per-user ceilings — a guard against runaway clients, not a product limit anyone should hit. */
@Validated
@ConfigurationProperties(prefix = "aura.library")
public record LibraryProperties(
        @Positive @DefaultValue("500") int maxPlaylistsPerUser,
        @Positive @DefaultValue("10000") int maxTracksPerPlaylist
) {
}
