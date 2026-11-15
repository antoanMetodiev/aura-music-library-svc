-- Library & Playlist Service (Project-Info.md §23). Owns schema `library`; every other service's
-- data is referenced by id only — tracks, albums and artists live in `catalog`, users in Supabase Auth
-- (`user_id` / `owner_id` = the JWT `sub`). No foreign keys across schemas on purpose.

CREATE TABLE playlists (
    id          UUID         PRIMARY KEY,
    owner_id    UUID         NOT NULL,
    title       VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    -- Room to grow (§23): shared / collaborative playlists and share links add columns, not tables.
    is_public   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX playlists_owner_idx ON playlists (owner_id, created_at);

-- One row per occurrence: the same track may appear twice in a playlist, so the row has its own id.
-- `position` is dense (0..n-1). The unique constraint is DEFERRABLE so a reorder can shift a whole
-- range in one statement without tripping over itself midway.
CREATE TABLE playlist_items (
    id          UUID        PRIMARY KEY,
    playlist_id UUID        NOT NULL REFERENCES playlists (id) ON DELETE CASCADE,
    track_id    UUID        NOT NULL,
    position    INTEGER     NOT NULL CHECK (position >= 0),
    added_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT playlist_items_position_uq UNIQUE (playlist_id, position) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE liked_tracks (
    user_id  UUID        NOT NULL,
    track_id UUID        NOT NULL,
    liked_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, track_id)
);

CREATE INDEX liked_tracks_recent_idx ON liked_tracks (user_id, liked_at DESC);

CREATE TABLE saved_albums (
    user_id  UUID        NOT NULL,
    album_id UUID        NOT NULL,
    saved_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, album_id)
);

CREATE INDEX saved_albums_recent_idx ON saved_albums (user_id, saved_at DESC);

CREATE TABLE saved_artists (
    user_id   UUID        NOT NULL,
    artist_id UUID        NOT NULL,
    saved_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, artist_id)
);

CREATE INDEX saved_artists_recent_idx ON saved_artists (user_id, saved_at DESC);
