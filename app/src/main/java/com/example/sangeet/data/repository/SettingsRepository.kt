package com.example.sangeet.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.sangeet.data.model.PlayerStyle
import com.example.ui.theme.AccentColor
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sangeet_preferences")

class SettingsRepository(private val context: Context) {

    private val dataStore = context.dataStore
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        // Appearance
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_ACCENT_COLOR = stringPreferencesKey("accent_color")
        val KEY_DYNAMIC_ARTWORK_COLOR = booleanPreferencesKey("dynamic_artwork_color")

        // Player
        val KEY_PLAYER_STYLE = stringPreferencesKey("player_style")
        val KEY_ARTWORK_ANIMATION = booleanPreferencesKey("artwork_animation")
        val KEY_GESTURES_ENABLED = booleanPreferencesKey("gestures_enabled")

        // Playback
        val KEY_SHUFFLE = booleanPreferencesKey("shuffle_enabled")
        val KEY_REPEAT_MODE = intPreferencesKey("repeat_mode")
        val KEY_PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val KEY_CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val KEY_GAPLESS_PLAYBACK = booleanPreferencesKey("gapless_playback")
        val KEY_SLEEP_TIMER_MINUTES = intPreferencesKey("sleep_timer_minutes")

        // Equalizer
        val KEY_EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val KEY_EQ_PRESET_NAME = stringPreferencesKey("eq_preset_name")
        val KEY_EQ_BANDS = stringPreferencesKey("eq_bands_gains")
        val KEY_EQ_PREAMP = floatPreferencesKey("eq_preamp")
        val KEY_EQ_BASS_BOOST = floatPreferencesKey("eq_bass_boost")
        val KEY_EQ_TREBLE_BOOST = floatPreferencesKey("eq_treble_boost")
        val KEY_EQ_BALANCE = floatPreferencesKey("eq_balance")

        // Spatial Audio
        val KEY_SPATIAL_ENABLED = booleanPreferencesKey("spatial_enabled")
        val KEY_SPATIAL_PRESET_NAME = stringPreferencesKey("spatial_preset_name")
        val KEY_SPATIAL_INTENSITY = floatPreferencesKey("spatial_intensity")
        val KEY_SPATIAL_ROTATION_SPEED = floatPreferencesKey("spatial_rotation_speed")
        val KEY_SPATIAL_ROTATION_DIRECTION = intPreferencesKey("spatial_rotation_direction")
        val KEY_SPATIAL_WIDTH = floatPreferencesKey("spatial_width")
        val KEY_SPATIAL_DEPTH = floatPreferencesKey("spatial_depth")
        val KEY_SPATIAL_MANUAL_AZIMUTH = floatPreferencesKey("spatial_manual_azimuth")

        // Lyrics
        val KEY_LYRICS_FONT_SIZE = floatPreferencesKey("lyrics_font_size")
        val KEY_LYRICS_HIGHLIGHT_COLOR = longPreferencesKey("lyrics_highlight_color")
        val KEY_LYRICS_AUTO_SYNC = booleanPreferencesKey("lyrics_auto_sync")
        val KEY_LYRICS_DISPLAY_MODE = stringPreferencesKey("lyrics_display_mode")

        // Library
        val KEY_SORT_ORDER = stringPreferencesKey("sort_order")
        val KEY_ARTWORK_PRIORITY = stringPreferencesKey("artwork_priority")

        // Home & Navigation
        val KEY_START_SCREEN = stringPreferencesKey("start_screen")
        val KEY_LAST_VISITED_SCREEN = stringPreferencesKey("last_visited_screen")
        val KEY_START_MODE = stringPreferencesKey("start_mode")
        val KEY_LAST_USED_MODE = stringPreferencesKey("last_used_mode")
        val KEY_SHOW_GREETING = booleanPreferencesKey("show_greeting")
        val KEY_SPOTLIGHT_HOME = booleanPreferencesKey("spotlight_home")
        val KEY_NON_EXPRESSIVE_NAV_BAR = booleanPreferencesKey("non_expressive_nav_bar")
        val KEY_TAB_NAMES_MODE = stringPreferencesKey("tab_names_mode")
        val KEY_HIDE_NAV_WHILE_SCROLLING = booleanPreferencesKey("hide_nav_while_scrolling")
        val KEY_ROTATE_WITH_DEVICE = booleanPreferencesKey("rotate_with_device")

        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val safePreferences: Flow<Preferences> = dataStore.data.catch { exception ->
        if (exception is IOException) {
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

    // --- Appearance Flows ---
    val themeMode: Flow<ThemeMode> = safePreferences.map { prefs ->
        val name = prefs[KEY_THEME_MODE] ?: ThemeMode.AMOLED.name
        try { ThemeMode.valueOf(name) } catch (_: Exception) { ThemeMode.AMOLED }
    }

    val accentColor: Flow<AccentColor> = safePreferences.map { prefs ->
        val name = prefs[KEY_ACCENT_COLOR] ?: AccentColor.CYAN.name
        try { AccentColor.valueOf(name) } catch (_: Exception) { AccentColor.CYAN }
    }

    val dynamicArtworkColor: Flow<Boolean> = safePreferences.map { it[KEY_DYNAMIC_ARTWORK_COLOR] ?: true }

    // --- Player Flows ---
    val playerStyle: Flow<PlayerStyle> = safePreferences.map { prefs ->
        val name = prefs[KEY_PLAYER_STYLE] ?: PlayerStyle.CLASSIC.name
        try { PlayerStyle.valueOf(name) } catch (_: Exception) { PlayerStyle.CLASSIC }
    }

    val artworkAnimation: Flow<Boolean> = safePreferences.map { it[KEY_ARTWORK_ANIMATION] ?: true }
    val gesturesEnabled: Flow<Boolean> = safePreferences.map { it[KEY_GESTURES_ENABLED] ?: true }

    // --- Playback Flows ---
    val shuffleEnabled: Flow<Boolean> = safePreferences.map { it[KEY_SHUFFLE] ?: false }
    val repeatMode: Flow<Int> = safePreferences.map { it[KEY_REPEAT_MODE] ?: 1 } // Player.REPEAT_MODE_ALL = 1
    val playbackSpeed: Flow<Float> = safePreferences.map { it[KEY_PLAYBACK_SPEED] ?: 1.0f }
    val crossfadeSeconds: Flow<Int> = safePreferences.map { it[KEY_CROSSFADE_SECONDS] ?: 0 }
    val gaplessPlayback: Flow<Boolean> = safePreferences.map { it[KEY_GAPLESS_PLAYBACK] ?: true }
    val sleepTimerMinutes: Flow<Int> = safePreferences.map { it[KEY_SLEEP_TIMER_MINUTES] ?: 30 }

    // --- Equalizer Flows ---
    val eqEnabled: Flow<Boolean> = safePreferences.map { it[KEY_EQ_ENABLED] ?: true }
    val eqPresetName: Flow<String> = safePreferences.map { it[KEY_EQ_PRESET_NAME] ?: "Flat" }
    val eqBandGains: Flow<FloatArray> = safePreferences.map { prefs ->
        val str = prefs[KEY_EQ_BANDS]
        if (str.isNullOrBlank()) {
            FloatArray(15) { 0f }
        } else {
            val parts = str.split(",")
            FloatArray(15) { idx -> parts.getOrNull(idx)?.toFloatOrNull() ?: 0f }
        }
    }
    val eqPreamp: Flow<Float> = safePreferences.map { it[KEY_EQ_PREAMP] ?: 0f }
    val eqBassBoost: Flow<Float> = safePreferences.map { it[KEY_EQ_BASS_BOOST] ?: 0f }
    val eqTrebleBoost: Flow<Float> = safePreferences.map { it[KEY_EQ_TREBLE_BOOST] ?: 0f }
    val eqBalance: Flow<Float> = safePreferences.map { it[KEY_EQ_BALANCE] ?: 0f }

    // --- Spatial Audio Flows ---
    val spatialEnabled: Flow<Boolean> = safePreferences.map { it[KEY_SPATIAL_ENABLED] ?: true }
    val spatialPresetName: Flow<String> = safePreferences.map { it[KEY_SPATIAL_PRESET_NAME] ?: "360° Surround" }
    val spatialIntensity: Flow<Float> = safePreferences.map { it[KEY_SPATIAL_INTENSITY] ?: 0.8f }
    val spatialRotationSpeed: Flow<Float> = safePreferences.map { it[KEY_SPATIAL_ROTATION_SPEED] ?: 1.0f }
    val spatialRotationDirection: Flow<Int> = safePreferences.map { it[KEY_SPATIAL_ROTATION_DIRECTION] ?: 2 }
    val spatialWidth: Flow<Float> = safePreferences.map { it[KEY_SPATIAL_WIDTH] ?: 1.3f }
    val spatialDepth: Flow<Float> = safePreferences.map { it[KEY_SPATIAL_DEPTH] ?: 0.7f }
    val spatialManualAzimuth: Flow<Float> = safePreferences.map { it[KEY_SPATIAL_MANUAL_AZIMUTH] ?: 0f }

    // --- Lyrics Flows ---
    val lyricsFontSize: Flow<Float> = safePreferences.map { it[KEY_LYRICS_FONT_SIZE] ?: 18f }
    val lyricsHighlightColor: Flow<Long> = safePreferences.map { it[KEY_LYRICS_HIGHLIGHT_COLOR] ?: 0xFF00E5FF }
    val lyricsAutoSync: Flow<Boolean> = safePreferences.map { it[KEY_LYRICS_AUTO_SYNC] ?: true }
    val lyricsDisplayMode: Flow<String> = safePreferences.map { it[KEY_LYRICS_DISPLAY_MODE] ?: "SYNCED" }

    // --- Library Flows ---
    val sortOrder: Flow<String> = safePreferences.map { it[KEY_SORT_ORDER] ?: "TITLE" }
    val artworkPriority: Flow<String> = safePreferences.map { it[KEY_ARTWORK_PRIORITY] ?: "EMBEDDED_FIRST" }

    // --- Home & Navigation Flows ---
    val startScreen: Flow<String> = safePreferences.map { it[KEY_START_SCREEN] ?: "HOME" }
    val lastVisitedScreen: Flow<String> = safePreferences.map { it[KEY_LAST_VISITED_SCREEN] ?: "HOME" }
    val startMode: Flow<String> = safePreferences.map { it[KEY_START_MODE] ?: "MUSIC" }
    val lastUsedMode: Flow<String> = safePreferences.map { it[KEY_LAST_USED_MODE] ?: "MUSIC" }
    val showGreeting: Flow<Boolean> = safePreferences.map { it[KEY_SHOW_GREETING] ?: true }
    val spotlightHome: Flow<Boolean> = safePreferences.map { it[KEY_SPOTLIGHT_HOME] ?: true }
    val nonExpressiveNavBar: Flow<Boolean> = safePreferences.map { it[KEY_NON_EXPRESSIVE_NAV_BAR] ?: false }
    val tabNamesMode: Flow<String> = safePreferences.map { it[KEY_TAB_NAMES_MODE] ?: "AUTO" }
    val hideNavWhileScrolling: Flow<Boolean> = safePreferences.map { it[KEY_HIDE_NAV_WHILE_SCROLLING] ?: false }
    val rotateWithDevice: Flow<Boolean> = safePreferences.map { it[KEY_ROTATE_WITH_DEVICE] ?: false }

    // --- Persistence Mutations (All Safe & Asynchronous) ---

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setAccentColor(accent: AccentColor) {
        dataStore.edit { it[KEY_ACCENT_COLOR] = accent.name }
    }

    suspend fun setDynamicArtworkColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_ARTWORK_COLOR] = enabled }
    }

    suspend fun setPlayerStyle(style: PlayerStyle) {
        dataStore.edit { it[KEY_PLAYER_STYLE] = style.name }
    }

    suspend fun setArtworkAnimation(enabled: Boolean) {
        dataStore.edit { it[KEY_ARTWORK_ANIMATION] = enabled }
    }

    suspend fun setGesturesEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_GESTURES_ENABLED] = enabled }
    }

    suspend fun setShuffle(enabled: Boolean) {
        dataStore.edit { it[KEY_SHUFFLE] = enabled }
    }

    suspend fun setRepeatMode(mode: Int) {
        dataStore.edit { it[KEY_REPEAT_MODE] = mode }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        dataStore.edit { it[KEY_PLAYBACK_SPEED] = speed }
    }

    suspend fun setCrossfadeSeconds(seconds: Int) {
        dataStore.edit { it[KEY_CROSSFADE_SECONDS] = seconds }
    }

    suspend fun setGaplessPlayback(enabled: Boolean) {
        dataStore.edit { it[KEY_GAPLESS_PLAYBACK] = enabled }
    }

    suspend fun setSleepTimerMinutes(minutes: Int) {
        dataStore.edit { it[KEY_SLEEP_TIMER_MINUTES] = minutes }
    }

    // Equalizer
    suspend fun setEqualizerEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_EQ_ENABLED] = enabled }
    }

    suspend fun setEqualizerPresetName(name: String) {
        dataStore.edit { it[KEY_EQ_PRESET_NAME] = name }
    }

    suspend fun setEqualizerBands(gains: FloatArray) {
        val str = gains.joinToString(",") { it.toString() }
        dataStore.edit { it[KEY_EQ_BANDS] = str }
    }

    suspend fun setEqualizerPreamp(preamp: Float) {
        dataStore.edit { it[KEY_EQ_PREAMP] = preamp }
    }

    suspend fun setEqualizerBassBoost(bass: Float) {
        dataStore.edit { it[KEY_EQ_BASS_BOOST] = bass }
    }

    suspend fun setEqualizerTrebleBoost(treble: Float) {
        dataStore.edit { it[KEY_EQ_TREBLE_BOOST] = treble }
    }

    suspend fun setEqualizerBalance(balance: Float) {
        dataStore.edit { it[KEY_EQ_BALANCE] = balance }
    }

    // Spatial Audio
    suspend fun setSpatialEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SPATIAL_ENABLED] = enabled }
    }

    suspend fun setSpatialPresetName(name: String) {
        dataStore.edit { it[KEY_SPATIAL_PRESET_NAME] = name }
    }

    suspend fun setSpatialIntensity(intensity: Float) {
        dataStore.edit { it[KEY_SPATIAL_INTENSITY] = intensity }
    }

    suspend fun setSpatialRotationSpeed(speed: Float) {
        dataStore.edit { it[KEY_SPATIAL_ROTATION_SPEED] = speed }
    }

    suspend fun setSpatialRotationDirection(direction: Int) {
        dataStore.edit { it[KEY_SPATIAL_ROTATION_DIRECTION] = direction }
    }

    suspend fun setSpatialWidth(width: Float) {
        dataStore.edit { it[KEY_SPATIAL_WIDTH] = width }
    }

    suspend fun setSpatialDepth(depth: Float) {
        dataStore.edit { it[KEY_SPATIAL_DEPTH] = depth }
    }

    suspend fun setSpatialManualAzimuth(azimuth: Float) {
        dataStore.edit { it[KEY_SPATIAL_MANUAL_AZIMUTH] = azimuth }
    }

    // Lyrics
    suspend fun setLyricsFontSize(size: Float) {
        dataStore.edit { it[KEY_LYRICS_FONT_SIZE] = size }
    }

    suspend fun setLyricsHighlightColor(colorLong: Long) {
        dataStore.edit { it[KEY_LYRICS_HIGHLIGHT_COLOR] = colorLong }
    }

    suspend fun setLyricsAutoSync(enabled: Boolean) {
        dataStore.edit { it[KEY_LYRICS_AUTO_SYNC] = enabled }
    }

    suspend fun setLyricsDisplayMode(mode: String) {
        dataStore.edit { it[KEY_LYRICS_DISPLAY_MODE] = mode }
    }

    // Library
    suspend fun setSortOrder(order: String) {
        dataStore.edit { it[KEY_SORT_ORDER] = order }
    }

    suspend fun setArtworkPriority(priority: String) {
        dataStore.edit { it[KEY_ARTWORK_PRIORITY] = priority }
    }

    // Home & Navigation
    suspend fun setStartScreen(screen: String) {
        dataStore.edit { it[KEY_START_SCREEN] = screen }
    }

    suspend fun setLastVisitedScreen(screen: String) {
        dataStore.edit { it[KEY_LAST_VISITED_SCREEN] = screen }
    }

    suspend fun setStartMode(mode: String) {
        dataStore.edit { it[KEY_START_MODE] = mode }
    }

    suspend fun setLastUsedMode(mode: String) {
        dataStore.edit { it[KEY_LAST_USED_MODE] = mode }
    }

    suspend fun setShowGreeting(enabled: Boolean) {
        dataStore.edit { it[KEY_SHOW_GREETING] = enabled }
    }

    suspend fun setSpotlightHome(enabled: Boolean) {
        dataStore.edit { it[KEY_SPOTLIGHT_HOME] = enabled }
    }

    suspend fun setNonExpressiveNavBar(enabled: Boolean) {
        dataStore.edit { it[KEY_NON_EXPRESSIVE_NAV_BAR] = enabled }
    }

    suspend fun setTabNamesMode(mode: String) {
        dataStore.edit { it[KEY_TAB_NAMES_MODE] = mode }
    }

    suspend fun setHideNavWhileScrolling(enabled: Boolean) {
        dataStore.edit { it[KEY_HIDE_NAV_WHILE_SCROLLING] = enabled }
    }

    suspend fun setRotateWithDevice(enabled: Boolean) {
        dataStore.edit { it[KEY_ROTATE_WITH_DEVICE] = enabled }
    }

    suspend fun resetHomeNavSettingsToDefault() {
        dataStore.edit {
            it.remove(KEY_START_SCREEN)
            it.remove(KEY_START_MODE)
            it.remove(KEY_SHOW_GREETING)
            it.remove(KEY_SPOTLIGHT_HOME)
            it.remove(KEY_NON_EXPRESSIVE_NAV_BAR)
            it.remove(KEY_TAB_NAMES_MODE)
            it.remove(KEY_HIDE_NAV_WHILE_SCROLLING)
            it.remove(KEY_ROTATE_WITH_DEVICE)
        }
    }

    suspend fun resetAllSettingsToDefault() {
        dataStore.edit { it.clear() }
    }
}
