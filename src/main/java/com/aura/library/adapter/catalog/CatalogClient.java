package com.aura.library.adapter.catalog;

import com.aura.library.config.CatalogClientProperties;
import com.aura.library.domain.model.SavedKind;
import com.aura.library.domain.port.CatalogLookup;
import com.aura.library.domain.service.LibraryExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * catalog-svc's batch reads ({@code GET /api/v1/catalog/{tracks|albums|artists}?ids=…}) —
 * service-to-service, not through the gateway. Local catalog only on that side, so this is fast.
 */
@Component
public class CatalogClient implements CatalogLookup {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);
    /** catalog-svc's own per-request ceiling. */
    private static final int CHUNK = 200;
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_OBJECTS = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public CatalogClient(RestClient.Builder builder, CatalogClientProperties properties,
                         CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.restClient = builder.clone().baseUrl(properties.baseUrl()).build();
        this.circuitBreaker = circuitBreakerFactory.create("catalog");
    }

    @Override
    public Map<UUID, Map<String, Object>> find(SavedKind kind, Collection<UUID> ids) {
        List<UUID> all = new ArrayList<>(ids);
        Map<UUID, Map<String, Object>> result = new HashMap<>();
        for (int from = 0; from < all.size(); from += CHUNK) {
            List<UUID> chunk = all.subList(from, Math.min(from + CHUNK, all.size()));
            for (Map<String, Object> entity : fetch(kind, chunk)) {
                Object id = entity.get("id");
                if (id != null) result.put(UUID.fromString(id.toString()), entity);
            }
        }
        return result;
    }

    private List<Map<String, Object>> fetch(SavedKind kind, List<UUID> ids) {
        String path = switch (kind) {
            case TRACK -> "/api/v1/catalog/tracks";
            case ALBUM -> "/api/v1/catalog/albums";
            case ARTIST -> "/api/v1/catalog/artists";
        };
        String joined = ids.stream().map(UUID::toString).collect(Collectors.joining(","));
        try {
            List<Map<String, Object>> body = circuitBreaker.run(() -> restClient.get()
                    .uri(b -> b.path(path).queryParam("ids", joined).build())
                    .retrieve()
                    .body(LIST_OF_OBJECTS));
            return body == null ? List.of() : body;
        } catch (RuntimeException e) {
            log.warn("Unable to reach catalog-svc for {} {} ids", ids.size(), kind, e);
            throw new LibraryExceptions.CatalogUnavailable(e);
        }
    }
}
