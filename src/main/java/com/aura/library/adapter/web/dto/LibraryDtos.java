package com.aura.library.adapter.web.dto;

import com.aura.library.domain.model.Playlist;
import com.aura.library.domain.model.PlaylistItem;
import com.aura.library.domain.service.PlaylistService;
import com.aura.library.domain.service.SavedItemService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request and response bodies of the library API. Catalog entities pass through as catalog-svc's own JSON. */
public final class LibraryDtos {

    private LibraryDtos() {
    }

    /** Uniform error body for every 4xx/5xx response (Project-Info.md §49). Never a stack trace. */
    public record ApiError(String code, String message, String traceId) {
    }

    // ── Saved collections ────────────────────────────────────────────────────────────────

    public record SavedItemResponse(Map<String, Object> item, Instant savedAt) {
    }

    public record SavedPageResponse(List<SavedItemResponse> items, int total, int limit, int offset) {
        public static SavedPageResponse from(SavedItemService.Page page) {
            return new SavedPageResponse(
                    page.items().stream().map(h -> new SavedItemResponse(h.item(), h.savedAt())).toList(),
                    page.total(), page.limit(), page.offset());
        }
    }

    // ── Playlists ────────────────────────────────────────────────────────────────────────

    public record CreatePlaylistRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 1000) String description,
            Boolean isPublic) {
    }

    /** Every field optional — only what's sent changes. */
    public record UpdatePlaylistRequest(
            @Size(max = 200) String title,
            @Size(max = 1000) String description,
            Boolean isPublic) {
    }

    public record AddTracksRequest(
            @NotEmpty @Size(max = 100) List<@NotNull UUID> trackIds,
            @Min(0) Integer position) {
    }

    public record MoveItemRequest(@NotNull @Min(0) Integer position) {
    }

    public record PlaylistResponse(
            UUID id, UUID ownerId, String title, String description, boolean isPublic,
            int trackCount, Object artwork, Instant createdAt, Instant updatedAt) {

        public static PlaylistResponse from(PlaylistService.View view) {
            Playlist p = view.playlist();
            return new PlaylistResponse(p.id(), p.ownerId(), p.title(), p.description(), p.isPublic(),
                    p.trackCount(), view.artwork(), p.createdAt(), p.updatedAt());
        }
    }

    public record PlaylistItemResponse(UUID id, int position, Instant addedAt, Map<String, Object> track) {
    }

    public record PlaylistDetailResponse(PlaylistResponse playlist, List<PlaylistItemResponse> items) {
        public static PlaylistDetailResponse from(PlaylistService.Detail detail) {
            return new PlaylistDetailResponse(
                    PlaylistResponse.from(detail.view()),
                    detail.items().stream()
                            .map(i -> new PlaylistItemResponse(i.item().id(), i.item().position(), i.item().addedAt(), i.track()))
                            .toList());
        }
    }

    /** Just the new rows — the client already has the tracks it added. */
    public record AddedItemResponse(UUID id, UUID trackId, int position, Instant addedAt) {
        public static AddedItemResponse from(PlaylistItem item) {
            return new AddedItemResponse(item.id(), item.trackId(), item.position(), item.addedAt());
        }
    }
}
