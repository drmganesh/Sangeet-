package com.example.sangeet.data.local

import androidx.room.*
import com.example.sangeet.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioTrackDao {
    @Query("SELECT * FROM audio_tracks ORDER BY title ASC")
    fun getAllTracks(): Flow<List<AudioTrack>>

    @Query("SELECT * FROM audio_tracks WHERE id = :id")
    suspend fun getTrackById(id: Long): AudioTrack?

    @Query("SELECT * FROM audio_tracks WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteTracks(): Flow<List<AudioTrack>>

    @Query("SELECT * FROM audio_tracks ORDER BY dateAdded DESC LIMIT :limit")
    fun getRecentlyAdded(limit: Int = 30): Flow<List<AudioTrack>>

    @Query("SELECT * FROM audio_tracks WHERE lastPlayedTimestamp > 0 ORDER BY lastPlayedTimestamp DESC LIMIT :limit")
    fun getRecentlyPlayed(limit: Int = 30): Flow<List<AudioTrack>>

    @Query("SELECT * FROM audio_tracks WHERE playCount > 0 ORDER BY playCount DESC LIMIT :limit")
    fun getMostPlayed(limit: Int = 30): Flow<List<AudioTrack>>

    @Query("SELECT DISTINCT artist FROM audio_tracks WHERE artist != '' ORDER BY artist ASC")
    fun getAllArtists(): Flow<List<String>>

    @Query("SELECT DISTINCT album FROM audio_tracks WHERE album != '' ORDER BY album ASC")
    fun getAllAlbums(): Flow<List<String>>

    @Query("SELECT DISTINCT genre FROM audio_tracks WHERE genre != '' AND genre != 'Unknown' ORDER BY genre ASC")
    fun getAllGenres(): Flow<List<String>>

    @Query("SELECT * FROM audio_tracks WHERE artist = :artist ORDER BY album ASC, trackNumber ASC")
    fun getTracksByArtist(artist: String): Flow<List<AudioTrack>>

    @Query("SELECT * FROM audio_tracks WHERE album = :album ORDER BY trackNumber ASC, title ASC")
    fun getTracksByAlbum(album: String): Flow<List<AudioTrack>>

    @Query("SELECT * FROM audio_tracks WHERE genre = :genre ORDER BY title ASC")
    fun getTracksByGenre(genre: String): Flow<List<AudioTrack>>

    @Query("SELECT * FROM audio_tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'")
    fun searchTracks(query: String): Flow<List<AudioTrack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<AudioTrack>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: AudioTrack)

    @Update
    suspend fun updateTrack(track: AudioTrack)

    @Query("UPDATE audio_tracks SET isFavorite = :isFav WHERE id = :id")
    suspend fun setFavorite(id: Long, isFav: Boolean)

    @Query("UPDATE audio_tracks SET playCount = playCount + 1, lastPlayedTimestamp = :timestamp WHERE id = :id")
    suspend fun incrementPlayCount(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM audio_tracks WHERE id = :id")
    suspend fun deleteTrack(id: Long)

    @Query("SELECT COUNT(*) FROM audio_tracks")
    suspend fun getTrackCount(): Int
}

@Dao
interface VideoTrackDao {
    @Query("SELECT * FROM video_tracks ORDER BY dateAdded DESC")
    fun getAllVideos(): Flow<List<VideoTrack>>

    @Query("SELECT * FROM video_tracks WHERE id = :id")
    suspend fun getVideoById(id: Long): VideoTrack?

    @Query("SELECT * FROM video_tracks WHERE inWatchLater = 1 ORDER BY dateAdded DESC")
    fun getWatchLaterVideos(): Flow<List<VideoTrack>>

    @Query("SELECT * FROM video_tracks WHERE lastWatchedTimestamp > 0 ORDER BY lastWatchedTimestamp DESC")
    fun getRecentlyWatchedVideos(): Flow<List<VideoTrack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoTrack>)

    @Update
    suspend fun updateVideo(video: VideoTrack)

    @Query("UPDATE video_tracks SET watchProgressMs = :progressMs, lastWatchedTimestamp = :timestamp WHERE id = :id")
    suspend fun updateWatchProgress(id: Long, progressMs: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE video_tracks SET inWatchLater = :inWatchLater WHERE id = :id")
    suspend fun setWatchLater(id: Long, inWatchLater: Boolean)

    @Query("DELETE FROM video_tracks WHERE id = :id")
    suspend fun deleteVideo(id: Long)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT a.* FROM audio_tracks a INNER JOIN playlist_tracks p ON a.id = p.trackId WHERE p.playlistId = :playlistId ORDER BY p.sortOrder ASC")
    fun getTracksForPlaylist(playlistId: Long): Flow<List<AudioTrack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(playlistTrack: PlaylistTrack)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: Long)
}

@Dao
interface HistoryAndStatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaybackHistory(history: PlaybackHistory)

    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 100): Flow<List<PlaybackHistory>>

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(search: SearchHistory)

    @Query("SELECT DISTINCT `query` FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun getRecentSearches(): Flow<List<String>>

    @Query("DELETE FROM search_history")
    suspend fun clearSearches()

    @Query("SELECT COUNT(*) FROM playback_history")
    fun getTotalSongsPlayed(): Flow<Int>

    @Query("SELECT SUM(durationMs) FROM playback_history")
    fun getTotalListeningTimeMs(): Flow<Long?>
}

@Dao
interface PresetDao {
    @Query("SELECT * FROM equalizer_presets ORDER BY isCustom ASC, name ASC")
    fun getAllEqualizerPresets(): Flow<List<EqualizerPreset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEqualizerPreset(preset: EqualizerPreset): Long

    @Delete
    suspend fun deleteEqualizerPreset(preset: EqualizerPreset)

    @Query("SELECT * FROM spatial_presets ORDER BY isCustom ASC, name ASC")
    fun getAllSpatialPresets(): Flow<List<SpatialPreset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpatialPreset(preset: SpatialPreset): Long

    @Delete
    suspend fun deleteSpatialPreset(preset: SpatialPreset)
}
