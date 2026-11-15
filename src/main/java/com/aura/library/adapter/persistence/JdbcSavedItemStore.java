package com.aura.library.adapter.persistence;

import com.aura.library.domain.model.SavedEntry;
import com.aura.library.domain.model.SavedKind;
import com.aura.library.domain.port.SavedItemStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The three saved collections share one shape, so table and column names come from {@link SavedKind}
 * — fixed enum constants, never user input, which is what makes splicing them into SQL safe.
 */
@Repository
public class JdbcSavedItemStore implements SavedItemStore {

    private final JdbcClient jdbc;

    public JdbcSavedItemStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(SavedKind kind, UUID userId, UUID catalogId, Instant at) {
        jdbc.sql("INSERT INTO %s (user_id, %s, %s) VALUES (?, ?, ?) ON CONFLICT DO NOTHING"
                        .formatted(kind.table(), kind.idColumn(), kind.timeColumn()))
                .params(userId, catalogId, Timestamp.from(at))
                .update();
    }

    @Override
    public void remove(SavedKind kind, UUID userId, UUID catalogId) {
        jdbc.sql("DELETE FROM %s WHERE user_id = ? AND %s = ?".formatted(kind.table(), kind.idColumn()))
                .params(userId, catalogId)
                .update();
    }

    @Override
    public List<SavedEntry> page(SavedKind kind, UUID userId, int limit, int offset) {
        return jdbc.sql("SELECT %2$s AS id, %3$s AS at FROM %1$s WHERE user_id = ? ORDER BY %3$s DESC, %2$s LIMIT ? OFFSET ?"
                        .formatted(kind.table(), kind.idColumn(), kind.timeColumn()))
                .params(userId, limit, offset)
                .query((rs, n) -> new SavedEntry(rs.getObject("id", UUID.class), rs.getTimestamp("at").toInstant()))
                .list();
    }

    @Override
    public int count(SavedKind kind, UUID userId) {
        return jdbc.sql("SELECT count(*) FROM %s WHERE user_id = ?".formatted(kind.table()))
                .param(userId)
                .query(Integer.class)
                .single();
    }

    @Override
    public Set<UUID> contained(SavedKind kind, UUID userId, Collection<UUID> catalogIds) {
        if (catalogIds.isEmpty()) return Set.of();
        return new HashSet<>(jdbc.sql("SELECT %s FROM %s WHERE user_id = :user AND %1$s IN (:ids)"
                        .formatted(kind.idColumn(), kind.table()))
                .param("user", userId)
                .param("ids", catalogIds)
                .query(UUID.class)
                .list());
    }
}
