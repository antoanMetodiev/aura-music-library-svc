package com.aura.library.adapter.persistence;

import com.aura.library.domain.model.Playlist;
import com.aura.library.domain.model.PlaylistItem;
import com.aura.library.domain.port.PlaylistStore;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPlaylistStore implements PlaylistStore {

    /** Count and cover (first item) ride along with every playlist read. */
    private static final String SELECT_PLAYLIST = """
            SELECT p.*,
                   (SELECT count(*) FROM library.playlist_items i WHERE i.playlist_id = p.id) AS track_count,
                   (SELECT i.track_id FROM library.playlist_items i WHERE i.playlist_id = p.id ORDER BY i.position LIMIT 1) AS cover_track_id
            FROM library.playlists p
            """;

    private static final RowMapper<Playlist> PLAYLIST = (rs, n) -> new Playlist(
            rs.getObject("id", UUID.class),
            rs.getObject("owner_id", UUID.class),
            rs.getString("title"),
            rs.getString("description"),
            rs.getBoolean("is_public"),
            rs.getInt("track_count"),
            rs.getObject("cover_track_id", UUID.class),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private static final RowMapper<PlaylistItem> ITEM = (rs, n) -> new PlaylistItem(
            rs.getObject("id", UUID.class),
            rs.getObject("track_id", UUID.class),
            rs.getInt("position"),
            rs.getTimestamp("added_at").toInstant());

    private final JdbcClient jdbc;

    public JdbcPlaylistStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(UUID id, UUID ownerId, String title, String description, boolean isPublic, Instant at) {
        jdbc.sql("INSERT INTO library.playlists (id, owner_id, title, description, is_public, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)")
                .params(id, ownerId, title, description, isPublic, Timestamp.from(at), Timestamp.from(at))
                .update();
    }

    @Override
    public Optional<Playlist> find(UUID id) {
        return jdbc.sql(SELECT_PLAYLIST + " WHERE p.id = ?").param(id).query(PLAYLIST).optional();
    }

    @Override
    public Optional<Playlist> findForUpdate(UUID id) {
        // Lock the bare row first (FOR UPDATE can't sit on a query with aggregates), then read it whole.
        boolean exists = jdbc.sql("SELECT id FROM library.playlists WHERE id = ? FOR UPDATE").param(id).query(UUID.class).optional().isPresent();
        return exists ? find(id) : Optional.empty();
    }

    @Override
    public List<Playlist> findByOwner(UUID ownerId) {
        return jdbc.sql(SELECT_PLAYLIST + " WHERE p.owner_id = ? ORDER BY p.created_at DESC").param(ownerId).query(PLAYLIST).list();
    }

    @Override
    public int countByOwner(UUID ownerId) {
        return jdbc.sql("SELECT count(*) FROM library.playlists WHERE owner_id = ?").param(ownerId).query(Integer.class).single();
    }

    @Override
    public void update(UUID id, String title, String description, boolean isPublic, Instant at) {
        jdbc.sql("UPDATE library.playlists SET title = ?, description = ?, is_public = ?, updated_at = ? WHERE id = ?")
                .params(title, description, isPublic, Timestamp.from(at), id)
                .update();
    }

    @Override
    public void touch(UUID id, Instant at) {
        jdbc.sql("UPDATE library.playlists SET updated_at = ? WHERE id = ?").params(Timestamp.from(at), id).update();
    }

    @Override
    public void delete(UUID id) {
        jdbc.sql("DELETE FROM library.playlists WHERE id = ?").param(id).update();
    }

    @Override
    public List<PlaylistItem> items(UUID playlistId) {
        return jdbc.sql("SELECT * FROM library.playlist_items WHERE playlist_id = ? ORDER BY position").param(playlistId).query(ITEM).list();
    }

    @Override
    public Optional<PlaylistItem> findItem(UUID playlistId, UUID itemId) {
        return jdbc.sql("SELECT * FROM library.playlist_items WHERE playlist_id = ? AND id = ?").params(playlistId, itemId).query(ITEM).optional();
    }

    @Override
    public void shiftFrom(UUID playlistId, int from, int by) {
        jdbc.sql("UPDATE library.playlist_items SET position = position + ? WHERE playlist_id = ? AND position >= ?")
                .params(by, playlistId, from)
                .update();
    }

    @Override
    public void shiftRange(UUID playlistId, int from, int to, int by) {
        jdbc.sql("UPDATE library.playlist_items SET position = position + ? WHERE playlist_id = ? AND position BETWEEN ? AND ?")
                .params(by, playlistId, from, to)
                .update();
    }

    @Override
    public void insertItem(UUID playlistId, UUID itemId, UUID trackId, int position, Instant at) {
        jdbc.sql("INSERT INTO library.playlist_items (id, playlist_id, track_id, position, added_at) VALUES (?, ?, ?, ?, ?)")
                .params(itemId, playlistId, trackId, position, Timestamp.from(at))
                .update();
    }

    @Override
    public void setItemPosition(UUID itemId, int position) {
        jdbc.sql("UPDATE library.playlist_items SET position = ? WHERE id = ?").params(position, itemId).update();
    }

    @Override
    public void deleteItem(UUID itemId) {
        jdbc.sql("DELETE FROM library.playlist_items WHERE id = ?").param(itemId).update();
    }
}
