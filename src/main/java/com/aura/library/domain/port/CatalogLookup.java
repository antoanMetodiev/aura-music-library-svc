package com.aura.library.domain.port;

import com.aura.library.domain.model.SavedKind;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Turns our ids into catalog entities. The library owns no catalog data (§23), so the entity comes
 * back as catalog-svc's own JSON, passed through to the client untouched — the frontend already
 * knows that shape from every catalog endpoint. Ids the catalog doesn't know are absent from the map.
 */
public interface CatalogLookup {

    Map<UUID, Map<String, Object>> find(SavedKind kind, Collection<UUID> ids);
}
