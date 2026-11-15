package com.aura.library.adapter.web;

import com.aura.library.adapter.web.dto.LibraryDtos.SavedPageResponse;
import com.aura.library.domain.model.SavedKind;
import com.aura.library.domain.service.LibraryExceptions;
import com.aura.library.domain.service.SavedItemService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The signed-in user's liked tracks, saved albums and saved artists:
 * {@code /api/v1/library/{liked-tracks|saved-albums|saved-artists}}.
 */
@RestController
@RequestMapping("/api/v1/library/{collection:liked-tracks|saved-albums|saved-artists}")
public class SavedItemController {

    private static final int MAX_CONTAINS = 200;

    private final SavedItemService service;

    public SavedItemController(SavedItemService service) {
        this.service = service;
    }

    /** Most recently saved first. */
    @GetMapping
    public SavedPageResponse list(@AuthenticationPrincipal Jwt jwt, @PathVariable String collection,
                                  @RequestParam(value = "limit", defaultValue = "50") int limit,
                                  @RequestParam(value = "offset", defaultValue = "0") int offset) {
        return SavedPageResponse.from(service.page(kind(collection), CurrentUser.id(jwt), limit, offset));
    }

    /** {@code ?ids=a,b,c} → {@code [true,false,true]} in the same order — for heart buttons. */
    @GetMapping("/contains")
    public List<Boolean> contains(@AuthenticationPrincipal Jwt jwt, @PathVariable String collection,
                                  @RequestParam("ids") List<UUID> ids) {
        if (ids.isEmpty() || ids.size() > MAX_CONTAINS) {
            throw new IllegalArgumentException("'ids' must hold 1–" + MAX_CONTAINS + " ids");
        }
        return service.contains(kind(collection), CurrentUser.id(jwt), ids);
    }

    /** Idempotent. 404 when the id isn't in the catalog. */
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@AuthenticationPrincipal Jwt jwt, @PathVariable String collection, @PathVariable UUID id) {
        service.save(kind(collection), CurrentUser.id(jwt), id);
    }

    /** Idempotent — removing something not saved is not an error. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable String collection, @PathVariable UUID id) {
        service.remove(kind(collection), CurrentUser.id(jwt), id);
    }

    private static SavedKind kind(String collection) {
        return switch (collection) {
            case "liked-tracks" -> SavedKind.TRACK;
            case "saved-albums" -> SavedKind.ALBUM;
            case "saved-artists" -> SavedKind.ARTIST;
            default -> throw new LibraryExceptions.NotFound("NOT_FOUND", "Unknown collection " + collection);
        };
    }
}
