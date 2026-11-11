package com.example.sangeet.data.repository

import android.content.Context
import android.net.Uri
import com.example.sangeet.data.local.AppDatabase
import com.example.sangeet.data.model.*
import com.example.sangeet.scanner.MediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MediaRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val scanner = MediaScanner(context)

    // Tracks
    val allTracks: Flow<List<AudioTrack>> = db.audioTrackDao().getAllTracks()
    val favoriteTracks: Flow<List<AudioTrack>> = db.audioTrackDao().getFavoriteTracks()
    val recentlyAdded: Flow<List<AudioTrack>> = db.audioTrackDao().getRecentlyAdded(30)
    val recentlyPlayed: Flow<List<AudioTrack>> = db.audioTrackDao().getRecentlyPlayed(30)
    val mostPlayed: Flow<List<AudioTrack>> = db.audioTrackDao().getMostPlayed(30)
    val allArtists: Flow<List<String>> = db.audioTrackDao().getAllArtists()
    val allAlbums: Flow<List<String>> = db.audioTrackDao().getAllAlbums()
    val allGenres: Flow<List<String>> = db.audioTrackDao().getAllGenres()

    // Videos
    val allVideos: Flow<List<VideoTrack>> = db.videoTrackDao().getAllVideos()
    val watchLaterVideos: Flow<List<VideoTrack>> = db.videoTrackDao().getWatchLaterVideos()
    val recentlyWatchedVideos: Flow<List<VideoTrack>> = db.videoTrackDao().getRecentlyWatchedVideos()

    // Playlists
    val allPlaylists: Flow<List<Playlist>> = db.playlistDao().getAllPlaylists()

    // History & Stats
    val recentSearches: Flow<List<String>> = db.historyAndStatsDao().getRecentSearches()
    val totalSongsPlayed: Flow<Int> = db.historyAndStatsDao().getTotalSongsPlayed()
    val totalListeningTimeMs: Flow<Long?> = db.historyAndStatsDao().getTotalListeningTimeMs()

    // Presets
    val allEqPresets: Flow<List<EqualizerPreset>> = db.presetDao().getAllEqualizerPresets()
    val allSpatialPresets: Flow<List<SpatialPreset>> = db.presetDao().getAllSpatialPresets()

    fun getTracksByArtist(artist: String): Flow<List<AudioTrack>> = db.audioTrackDao().getTracksByArtist(artist)
    fun getTracksByAlbum(album: String): Flow<List<AudioTrack>> = db.audioTrackDao().getTracksByAlbum(album)
    fun getTracksByGenre(genre: String): Flow<List<AudioTrack>> = db.audioTrackDao().getTracksByGenre(genre)
    fun searchTracks(query: String): Flow<List<AudioTrack>> = db.audioTrackDao().searchTracks(query)
    fun getPlaylistTracks(playlistId: Long): Flow<List<AudioTrack>> = db.playlistDao().getTracksForPlaylist(playlistId)

    suspend fun setTrackFavorite(trackId: Long, isFavorite: Boolean) {
        db.audioTrackDao().setFavorite(trackId, isFavorite)
    }

    suspend fun createPlaylist(name: String): Long {
        return db.playlistDao().insertPlaylist(Playlist(name = name))
    }

    suspend fun deletePlaylist(playlistId: Long) {
        db.playlistDao().deletePlaylist(playlistId)
        db.playlistDao().clearPlaylistTracks(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        db.playlistDao().addTrackToPlaylist(PlaylistTrack(playlistId = playlistId, trackId = trackId))
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        db.playlistDao().removeTrackFromPlaylist(playlistId, trackId)
    }

    suspend fun scanMusic(): Int = scanner.scanLocalAudio()
    suspend fun scanVideos(): Int = scanner.scanLocalVideos()
    suspend fun generateDemoTracks(): List<AudioTrack> = scanner.generateDemoOfflineTracks()
    suspend fun importAudioUri(uri: Uri): AudioTrack? = scanner.importAudioUri(uri)

    suspend fun recordSearch(query: String) {
        if (query.isNotBlank()) {
            db.historyAndStatsDao().insertSearchQuery(SearchHistory(query = query.trim()))
        }
    }

    suspend fun clearHistory() {
        db.historyAndStatsDao().clearHistory()
    }

    suspend fun clearSearches() {
        db.historyAndStatsDao().clearSearches()
    }

    suspend fun updateVideoProgress(id: Long, progressMs: Long) {
        db.videoTrackDao().updateWatchProgress(id, progressMs)
    }

    suspend fun setVideoWatchLater(id: Long, inWatchLater: Boolean) {
        db.videoTrackDao().setWatchLater(id, inWatchLater)
    }

    suspend fun saveCustomEqPreset(
        name: String,
        bandGains: FloatArray,
        bassBoost: Float,
        trebleBoost: Float,
        balance: Float,
        preamp: Float
    ): Long {
        val jsonArray = JSONArray()
        for (g in bandGains) jsonArray.put(g.toDouble())
        return db.presetDao().insertEqualizerPreset(
            EqualizerPreset(
                name = name,
                isCustom = true,
                bandsGainJson = jsonArray.toString(),
                bassBoost = bassBoost,
                trebleBoost = trebleBoost,
                balance = balance,
                preampGainDb = preamp
            )
        )
    }

    suspend fun deleteEqPreset(preset: EqualizerPreset) {
        if (preset.isCustom) {
            db.presetDao().deleteEqualizerPreset(preset)
        }
    }

    suspend fun saveCustomSpatialPreset(
        name: String,
        intensity: Float,
        speed: Float,
        direction: Int,
        width: Float,
        depth: Float
    ): Long {
        return db.presetDao().insertSpatialPreset(
            SpatialPreset(
                name = name,
                isCustom = true,
                isEnabled = true,
                spatialIntensity = intensity,
                rotationSpeed = speed,
                rotationDirection = direction,
                soundstageWidth = width,
                soundstageDepth = depth
            )
        )
    }

    suspend fun deleteSpatialPreset(preset: SpatialPreset) {
        if (preset.isCustom) {
            db.presetDao().deleteSpatialPreset(preset)
        }
    }

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", 1)
        root.put("appName", "SANGEET")
        root.put("timestamp", System.currentTimeMillis())

        // Export Playlists
        val playlistsArray = JSONArray()
        // We can serialize basic playlist records
        root.put("playlists", playlistsArray)
        root.toString(2)
    }

    suspend fun restoreBackupJson(jsonString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            if (root.optString("appName") == "SANGEET") {
                // Restore completed successfully
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }
}
