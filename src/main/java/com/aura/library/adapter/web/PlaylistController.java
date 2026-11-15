package com.aura.library.adapter.web;

import com.aura.library.adapter.web.dto.LibraryDtos.AddTracksRequest;
import com.aura.library.adapter.web.dto.LibraryDtos.AddedItemResponse;
import com.aura.library.adapter.web.dto.LibraryDtos.CreatePlaylistRequest;
import com.aura.library.adapter.web.dto.LibraryDtos.MoveItemRequest;
import com.aura.library.adapter.web.dto.LibraryDtos.PlaylistDetailResponse;
import com.aura.library.adapter.web.dto.LibraryDtos.PlaylistResponse;
import com.aura.library.adapter.web.dto.LibraryDtos.UpdatePlaylistRequest;
import com.aura.library.domain.service.PlaylistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * {@code /api/v1/library/playlists}. Reads allow the owner or anyone for a public playlist; every
 * change is the owner's alone. Someone else's private playlist answers 404, not 403.
 */
@RestController
@RequestMapping("/api/v1/library/playlists")
public class PlaylistController {

    private final PlaylistService service;

    public PlaylistController(PlaylistService service) {
        this.service = service;
    }

    /** The caller's own playlists, newest first — the sidebar list. */
    @GetMapping
    public List<PlaylistResponse> listOwn(@AuthenticationPrincipal Jwt jwt) {
        return service.listOwn(CurrentUser.id(jwt)).stream().map(PlaylistResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlaylistResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePlaylistRequest body) {
        return PlaylistResponse.from(service.create(CurrentUser.id(jwt), body.title(), body.description(),
                Boolean.TRUE.equals(body.isPublic())));
    }

    @GetMapping("/{id}")
    public PlaylistDetailResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return PlaylistDetailResponse.from(service.get(CurrentUser.id(jwt), id));
    }

    @PatchMapping("/{id}")
    public PlaylistResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                   @Valid @RequestBody UpdatePlaylistRequest body) {
        return PlaylistResponse.from(service.update(CurrentUser.id(jwt), id, body.title(), body.description(), body.isPublic()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(CurrentUser.id(jwt), id);
    }

    /** Appends, or inserts at {@code position}. Up to 100 tracks per call; the same track may appear twice. */
    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public List<AddedItemResponse> addTracks(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                             @Valid @RequestBody AddTracksRequest body) {
        return service.addTracks(CurrentUser.id(jwt), id, body.trackIds(), body.position()).stream()
                .map(AddedItemResponse::from).toList();
    }

    /** Items are addressed by their own id, not the track's — the same track can be in a playlist twice. */
    @DeleteMapping("/{id}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID itemId) {
        service.removeItem(CurrentUser.id(jwt), id, itemId);
    }

    /** Reorder: moves the item to {@code position}; the rest slide to make room. */
    @PatchMapping("/{id}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void moveItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID itemId,
                         @Valid @RequestBody MoveItemRequest body) {
        service.moveItem(CurrentUser.id(jwt), id, itemId, body.position());
    }
}
