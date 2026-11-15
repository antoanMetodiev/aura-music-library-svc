package com.aura.library.domain.model;

/** The three "save this for me" collections — same shape, different catalog entity (Project-Info.md §23). */
public enum SavedKind {
    TRACK("library.liked_tracks", "track_id", "liked_at"),
    ALBUM("library.saved_albums", "album_id", "saved_at"),
    ARTIST("library.saved_artists", "artist_id", "saved_at");

    private final String table;
    private final String idColumn;
    private final String timeColumn;

    SavedKind(String table, String idColumn, String timeColumn) {
        this.table = table;
        this.idColumn = idColumn;
        this.timeColumn = timeColumn;
    }

    public String table() { return table; }
    public String idColumn() { return idColumn; }
    public String timeColumn() { return timeColumn; }
}
