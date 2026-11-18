package com.example.sangeet.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.sangeet.data.model.CassetteStyle
import com.example.sangeet.ui.components.ArtworkAnimationIntensity
import com.example.sangeet.ui.components.ArtworkAnimationPreferencesSnapshot
import com.example.sangeet.ui.components.ArtworkAnimationStyle
import com.example.sangeet.ui.components.SpotlightTransitionDirection
import com.example.sangeet.ui.components.SpotlightTransitionSpeed
import com.example.sangeet.ui.components.SpotlightTransitionStyle
import com.example.sangeet.ui.motion.MotionPreferencesSnapshot
import com.example.sangeet.ui.motion.MotionQuality
import com.example.sangeet.ui.motion.MotionSpeed
import com.example.sangeet.ui.startup.StartupAnimationPreferencesSnapshot
import com.example.sangeet.ui.startup.StartupAnimationSpeed
import com.example.sangeet.ui.startup.StartupAnimationStyle
import com.example.sangeet.playback.crossfade.CrossfadeMode
import com.example.sangeet.playback.crossfade.CrossfadePreferencesSnapshot
import com.example.sangeet.playback.crossfade.DjTransitionStyle
import com.example.ui.theme.AccentColor
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sangeet_preferences")

enum class NotificationArtworkSize(val label: String, val pixelSize: Int) {
    SMALL("Small", 256),
    MEDIUM("Medium", 384),
    LARGE("Large", 512)
}

enum class LockScreenMediaMode(val label: String, val description: String) {
    FULL("Full", "Show artwork, title, artist, album, and controls"),
    BASIC("Basic", "Show title and controls only"),
    HIDDEN("Hidden", "Hide media info on lock screen")
}

enum class NotificationPrivacyMode(val label: String, val description: String) {
    PUBLIC("Public", "Display media details normally everywhere"),
    HIDE_SENSITIVE("Hide Sensitive", "Redact track details on lock screen"),
    PRIVATE("Private", "Mask track details across notification shade")
}

data class NotificationPreferencesSnapshot(
    val customizationEnabled: Boolean = true,
    val showArtwork: Boolean = true,
    val showArtist: Boolean = true,
    val showAlbum: Boolean = true,
    val showProgress: Boolean = true,
    val showPrevNext: Boolean = true,
    val showSeekButtons: Boolean = true,
    val showStopButton: Boolean = true,
    val artworkSize: NotificationArtworkSize = NotificationArtworkSize.LARGE,
    val lockScreenMode: LockScreenMediaMode = LockScreenMediaMode.FULL,
    val privacyMode: NotificationPrivacyMode = NotificationPrivacyMode.PUBLIC
) {
    fun effective(): NotificationPreferencesSnapshot {
        return if (customizationEnabled) this else NotificationPreferencesSnapshot(customizationEnabled = false)
    }
}

data class LastPlaybackSession(
    val trackId: Long,
    val queueIds: List<Long>,
    val positionMs: Long
)

data class UserProfileSnapshot(
    val displayName: String = "SANGEET Listener",
    val avatarUriOrPath: String = "",
    val tagline: String = "Offline Walkman Collector",
    val createdAtEpochMs: Long = 0L,
    val showListeningStats: Boolean = true
)

enum class LyricsFontStyle(val label: String) {
    SANS("Modern Sans"),
    SERIF("Classic Serif"),
    MONO("Tape Mono"),
    DISPLAY("Studio Bold");

    companion object {
        fun fromString(value: String?): LyricsFontStyle {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: SANS
        }
    }
}

enum class LyricsHighlightPreset(val label: String, val argbHex: Long) {
    SANGEET_GOLD("SANGEET Gold", 0xFFE8B429L),
    BRIGHT_AMBER("Bright Amber", 0xFFFFD666L),
    WARM_IVORY("Warm Ivory", 0xFFF9FAFB),
    COPPER_ORANGE("Copper Ember", 0xFFF59E0BL),
    NEON_CYAN("Electric Cyan", 0xFF22D3EEL),
    ROSE_GOLD("Rose Gold", 0xFFF472B6L),
    EMERALD("Emerald Glow", 0xFF34D399L)
}

data class LyricsDisplayPreferencesSnapshot(
    val fontSizeSp: Float = 19f,
    val fontStyle: LyricsFontStyle = LyricsFontStyle.SANS,
    val lineSpacingDp: Float = 18f,
    val highlightColorHex: Long = LyricsHighlightPreset.SANGEET_GOLD.argbHex,
    val inactiveOpacity: Float = 0.42f,
    val artworkBlurRadius: Float = 18f,
    val backgroundDimming: Float = 0.72f,
    val currentLineScale: Float = 1.12f,
    val autoScrollEnabled: Boolean = true,
    val wordByWordEnabled: Boolean = true,
    val reducedMotion: Boolean = false
)

class SettingsRepository(private val context: Context) {

    private val dataStore = context.dataStore
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        // Appearance
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_ACCENT_COLOR = stringPreferencesKey("accent_color")
        val KEY_DYNAMIC_ARTWORK_COLOR = booleanPreferencesKey("dynamic_artwork_color")

        // Player / Walkman
        val KEY_CASSETTE_STYLE = stringPreferencesKey("cassette_style")
        val KEY_WALKMAN_MECHANICAL_SOUNDS = booleanPreferencesKey("walkman_mechanical_sounds")
        val KEY_ARTWORK_ANIMATION = booleanPreferencesKey("artwork_animation")
        val KEY_GESTURES_ENABLED = booleanPreferencesKey("gestures_enabled")

        // Playback
        val KEY_SHUFFLE = booleanPreferencesKey("shuffle_enabled")
        val KEY_REPEAT_MODE = intPreferencesKey("repeat_mode")
        val KEY_PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val KEY_CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val KEY_CROSSFADE_MODE = stringPreferencesKey("crossfade_mode")
        val KEY_CROSSFADE_AUTO_ENABLED = booleanPreferencesKey("crossfade_auto_enabled")
        val KEY_CROSSFADE_EQUAL_POWER = booleanPreferencesKey("crossfade_equal_power")
        val KEY_DJ_DURATION_SECONDS = intPreferencesKey("dj_duration_seconds")
        val KEY_DJ_BEAT_SYNC = booleanPreferencesKey("dj_beat_sync")
        val KEY_DJ_AUTO_BPM = booleanPreferencesKey("dj_auto_bpm")
        val KEY_DJ_TRANSITION_STYLE = stringPreferencesKey("dj_transition_style")
        val KEY_DJ_INCOMING_VOL = floatPreferencesKey("dj_incoming_volume")
        val KEY_DJ_OUTGOING_VOL = floatPreferencesKey("dj_outgoing_volume")
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
        val KEY_LYRICS_FONT_STYLE = stringPreferencesKey("lyrics_font_style")
        val KEY_LYRICS_LINE_SPACING = floatPreferencesKey("lyrics_line_spacing")
        val KEY_LYRICS_HIGHLIGHT_COLOR = longPreferencesKey("lyrics_highlight_color")
        val KEY_LYRICS_INACTIVE_OPACITY = floatPreferencesKey("lyrics_inactive_opacity")
        val KEY_LYRICS_ARTWORK_BLUR = floatPreferencesKey("lyrics_artwork_blur")
        val KEY_LYRICS_BG_DIMMING = floatPreferencesKey("lyrics_bg_dimming")
        val KEY_LYRICS_CURRENT_SCALE = floatPreferencesKey("lyrics_current_scale")
        val KEY_LYRICS_AUTO_SYNC = booleanPreferencesKey("lyrics_auto_sync")
        val KEY_LYRICS_WORD_BY_WORD = booleanPreferencesKey("lyrics_word_by_word")
        val KEY_LYRICS_REDUCED_MOTION = booleanPreferencesKey("lyrics_reduced_motion")
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

        // Now Playing Notification Settings
        val KEY_NOTIF_CUSTOMIZATION_ENABLED = booleanPreferencesKey("notif_customization_enabled")
        val KEY_NOTIF_SHOW_ARTWORK = booleanPreferencesKey("notif_show_artwork")
        val KEY_NOTIF_SHOW_ARTIST = booleanPreferencesKey("notif_show_artist")
        val KEY_NOTIF_SHOW_ALBUM = booleanPreferencesKey("notif_show_album")
        val KEY_NOTIF_SHOW_PROGRESS = booleanPreferencesKey("notif_show_progress")
        val KEY_NOTIF_SHOW_PREV_NEXT = booleanPreferencesKey("notif_show_prev_next")
        val KEY_NOTIF_SHOW_SEEK = booleanPreferencesKey("notif_show_seek")
        val KEY_NOTIF_SHOW_STOP = booleanPreferencesKey("notif_show_stop")
        val KEY_NOTIF_ARTWORK_SIZE = stringPreferencesKey("notif_artwork_size")
        val KEY_NOTIF_LOCKSCREEN_MODE = stringPreferencesKey("notif_lockscreen_mode")
        val KEY_NOTIF_PRIVACY_MODE = stringPreferencesKey("notif_privacy_mode")

        // Last Playback Session (for Process Recreation & Media Resumption)
        val KEY_LAST_PLAYBACK_TRACK_ID = longPreferencesKey("last_playback_track_id")
        val KEY_LAST_PLAYBACK_QUEUE_IDS = stringPreferencesKey("last_playback_queue_ids")
        val KEY_LAST_PLAYBACK_POSITION_MS = longPreferencesKey("last_playback_position_ms")

        // Home & Cover Artwork Animations
        val KEY_ART_ANIM_STYLE = stringPreferencesKey("art_anim_style")
        val KEY_ART_ANIM_INTENSITY = stringPreferencesKey("art_anim_intensity")
        val KEY_ART_ANIM_KEN_BURNS = booleanPreferencesKey("art_anim_ken_burns")
        val KEY_ART_ANIM_PARALLAX = booleanPreferencesKey("art_anim_parallax")
        val KEY_ART_ANIM_AMBIENT_LIGHT = booleanPreferencesKey("art_anim_ambient_light")
        val KEY_ART_ANIM_BREATHING = booleanPreferencesKey("art_anim_breathing")
        val KEY_ART_ANIM_TRACK_TRANSITIONS = booleanPreferencesKey("art_anim_track_transitions")
        val KEY_ART_ANIM_RESPECT_REDUCED_MOTION = booleanPreferencesKey("art_anim_respect_reduced_motion")
        val KEY_SPOTLIGHT_TRANSITION_STYLE = stringPreferencesKey("spotlight_transition_style")
        val KEY_SPOTLIGHT_TRANSITION_SPEED = stringPreferencesKey("spotlight_transition_speed")
        val KEY_SPOTLIGHT_TRANSITION_DIRECTION = stringPreferencesKey("spotlight_transition_direction")

        // Walkman Startup Animation Preferences
        val KEY_STARTUP_ANIM_STYLE = stringPreferencesKey("startup_anim_style")
        val KEY_STARTUP_ANIM_SPEED = stringPreferencesKey("startup_anim_speed")
        val KEY_STARTUP_ANIM_SOUNDS = booleanPreferencesKey("startup_anim_sounds")
        val KEY_STARTUP_ANIM_VIBRATION = booleanPreferencesKey("startup_anim_vibration")

        // Global Motion & Gestures Preferences
        val KEY_MOTION_QUALITY = stringPreferencesKey("motion_quality")
        val KEY_MOTION_SPEED = stringPreferencesKey("motion_speed")
        val KEY_MOTION_HORIZONTAL_SWIPES = booleanPreferencesKey("motion_horizontal_swipes")
        val KEY_MOTION_VERTICAL_SWIPES = booleanPreferencesKey("motion_vertical_swipes")
        val KEY_MOTION_PLAYER_GESTURES = booleanPreferencesKey("motion_player_gestures")
        val KEY_MOTION_CASSETTE_TRANSITIONS = booleanPreferencesKey("motion_cassette_transitions")
        val KEY_MOTION_SWIPE_DISMISS = booleanPreferencesKey("motion_swipe_dismiss")
        val KEY_MOTION_REDUCED_MOTION = booleanPreferencesKey("motion_reduced_motion")
        val KEY_MOTION_DISABLE_DECORATIVE = booleanPreferencesKey("motion_disable_decorative")

        // Local User Profile Preferences
        val KEY_PROFILE_DISPLAY_NAME = stringPreferencesKey("profile_display_name")
        val KEY_PROFILE_AVATAR_PATH = stringPreferencesKey("profile_avatar_path")
        val KEY_PROFILE_TAGLINE = stringPreferencesKey("profile_tagline")
        val KEY_PROFILE_CREATED_AT = longPreferencesKey("profile_created_at")
        val KEY_PROFILE_SHOW_STATS = booleanPreferencesKey("profile_show_stats")

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
        val name = prefs[KEY_ACCENT_COLOR] ?: AccentColor.GOLD.name
        try { AccentColor.valueOf(name) } catch (_: Exception) { AccentColor.GOLD }
    }

    val dynamicArtworkColor: Flow<Boolean> = safePreferences.map { it[KEY_DYNAMIC_ARTWORK_COLOR] ?: true }

    // --- Walkman Player Flows ---
    val cassetteStyle: Flow<CassetteStyle> = safePreferences.map { prefs ->
        val name = prefs[KEY_CASSETTE_STYLE] ?: CassetteStyle.BLACK_AND_GOLD.name
        when (name) {
            "PREMIUM_METALLIC" -> CassetteStyle.GOLDEN
            else -> try { CassetteStyle.valueOf(name) } catch (_: Exception) { CassetteStyle.BLACK_AND_GOLD }
        }
    }

    val walkmanMechanicalSounds: Flow<Boolean> = safePreferences.map {
        it[KEY_WALKMAN_MECHANICAL_SOUNDS] ?: false
    }

    val artworkAnimation: Flow<Boolean> = safePreferences.map { it[KEY_ARTWORK_ANIMATION] ?: true }
    val gesturesEnabled: Flow<Boolean> = safePreferences.map { it[KEY_GESTURES_ENABLED] ?: true }

    // --- Playback Flows ---
    val shuffleEnabled: Flow<Boolean> = safePreferences.map { it[KEY_SHUFFLE] ?: false }
    val repeatMode: Flow<Int> = safePreferences.map { it[KEY_REPEAT_MODE] ?: 1 } // Player.REPEAT_MODE_ALL = 1
    val playbackSpeed: Flow<Float> = safePreferences.map { it[KEY_PLAYBACK_SPEED] ?: 1.0f }
    val crossfadeSeconds: Flow<Int> = safePreferences.map { it[KEY_CROSSFADE_SECONDS] ?: 5 }
    val gaplessPlayback: Flow<Boolean> = safePreferences.map { it[KEY_GAPLESS_PLAYBACK] ?: true }
    val sleepTimerMinutes: Flow<Int> = safePreferences.map { it[KEY_SLEEP_TIMER_MINUTES] ?: 30 }

    val crossfadePreferences: Flow<CrossfadePreferencesSnapshot> = safePreferences.map { prefs ->
        val legacySeconds = prefs[KEY_CROSSFADE_SECONDS]
        val explicitMode = prefs[KEY_CROSSFADE_MODE]?.let { CrossfadeMode.fromString(it) }
        val resolvedMode = explicitMode ?: if ((legacySeconds ?: 0) > 0) CrossfadeMode.ADVANCED else CrossfadeMode.OFF
        val advDuration = (legacySeconds?.takeIf { it > 0 } ?: 5).coerceIn(1, 15)
        CrossfadePreferencesSnapshot(
            mode = resolvedMode,
            autoCrossfadeEnabled = prefs[KEY_CROSSFADE_AUTO_ENABLED] ?: true,
            advancedDurationSeconds = advDuration,
            equalPowerCurvesEnabled = prefs[KEY_CROSSFADE_EQUAL_POWER] ?: true,
            djDurationSeconds = (prefs[KEY_DJ_DURATION_SECONDS] ?: 8).coerceIn(4, 20),
            djBeatSyncEnabled = prefs[KEY_DJ_BEAT_SYNC] ?: true,
            djAutoBpmDetectionEnabled = prefs[KEY_DJ_AUTO_BPM] ?: true,
            djTransitionStyle = DjTransitionStyle.fromString(prefs[KEY_DJ_TRANSITION_STYLE]),
            djIncomingVolumeCap = (prefs[KEY_DJ_INCOMING_VOL] ?: 1.0f).coerceIn(0.2f, 1.0f),
            djOutgoingVolumeCap = (prefs[KEY_DJ_OUTGOING_VOL] ?: 1.0f).coerceIn(0.2f, 1.0f)
        ).sanitized()
    }.distinctUntilChanged()

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
    val lyricsFontSize: Flow<Float> = safePreferences.map { (it[KEY_LYRICS_FONT_SIZE] ?: 19f).coerceIn(14f, 30f) }
    val lyricsHighlightColor: Flow<Long> = safePreferences.map {
        it[KEY_LYRICS_HIGHLIGHT_COLOR] ?: LyricsHighlightPreset.SANGEET_GOLD.argbHex
    }
    val lyricsAutoSync: Flow<Boolean> = safePreferences.map { it[KEY_LYRICS_AUTO_SYNC] ?: true }
    val lyricsDisplayMode: Flow<String> = safePreferences.map { it[KEY_LYRICS_DISPLAY_MODE] ?: "SYNCED" }

    val lyricsPreferences: Flow<LyricsDisplayPreferencesSnapshot> = safePreferences.map { prefs ->
        LyricsDisplayPreferencesSnapshot(
            fontSizeSp = (prefs[KEY_LYRICS_FONT_SIZE] ?: 19f).coerceIn(14f, 30f),
            fontStyle = LyricsFontStyle.fromString(prefs[KEY_LYRICS_FONT_STYLE]),
            lineSpacingDp = (prefs[KEY_LYRICS_LINE_SPACING] ?: 18f).coerceIn(8f, 36f),
            highlightColorHex = prefs[KEY_LYRICS_HIGHLIGHT_COLOR] ?: LyricsHighlightPreset.SANGEET_GOLD.argbHex,
            inactiveOpacity = (prefs[KEY_LYRICS_INACTIVE_OPACITY] ?: 0.42f).coerceIn(0.15f, 0.85f),
            artworkBlurRadius = (prefs[KEY_LYRICS_ARTWORK_BLUR] ?: 18f).coerceIn(0f, 30f),
            backgroundDimming = (prefs[KEY_LYRICS_BG_DIMMING] ?: 0.72f).coerceIn(0.30f, 0.95f),
            currentLineScale = (prefs[KEY_LYRICS_CURRENT_SCALE] ?: 1.12f).coerceIn(1.0f, 1.30f),
            autoScrollEnabled = prefs[KEY_LYRICS_AUTO_SYNC] ?: true,
            wordByWordEnabled = prefs[KEY_LYRICS_WORD_BY_WORD] ?: true,
            reducedMotion = prefs[KEY_LYRICS_REDUCED_MOTION] ?: false
        )
    }.distinctUntilChanged()

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

    // --- Now Playing Notification Flows ---
    val notificationPreferences: Flow<NotificationPreferencesSnapshot> = safePreferences.map { prefs ->
        val artSize = prefs[KEY_NOTIF_ARTWORK_SIZE]?.let {
            try { NotificationArtworkSize.valueOf(it) } catch (_: Exception) { NotificationArtworkSize.LARGE }
        } ?: NotificationArtworkSize.LARGE
        val lockMode = prefs[KEY_NOTIF_LOCKSCREEN_MODE]?.let {
            try { LockScreenMediaMode.valueOf(it) } catch (_: Exception) { LockScreenMediaMode.FULL }
        } ?: LockScreenMediaMode.FULL
        val privacy = prefs[KEY_NOTIF_PRIVACY_MODE]?.let {
            try { NotificationPrivacyMode.valueOf(it) } catch (_: Exception) { NotificationPrivacyMode.PUBLIC }
        } ?: NotificationPrivacyMode.PUBLIC

        NotificationPreferencesSnapshot(
            customizationEnabled = prefs[KEY_NOTIF_CUSTOMIZATION_ENABLED] ?: true,
            showArtwork = prefs[KEY_NOTIF_SHOW_ARTWORK] ?: true,
            showArtist = prefs[KEY_NOTIF_SHOW_ARTIST] ?: true,
            showAlbum = prefs[KEY_NOTIF_SHOW_ALBUM] ?: true,
            showProgress = prefs[KEY_NOTIF_SHOW_PROGRESS] ?: true,
            showPrevNext = prefs[KEY_NOTIF_SHOW_PREV_NEXT] ?: true,
            showSeekButtons = prefs[KEY_NOTIF_SHOW_SEEK] ?: true,
            showStopButton = prefs[KEY_NOTIF_SHOW_STOP] ?: true,
            artworkSize = artSize,
            lockScreenMode = lockMode,
            privacyMode = privacy
        )
    }.distinctUntilChanged()

    // --- Home & Cover Artwork Animation Flows ---
    val artworkAnimationPreferences: Flow<ArtworkAnimationPreferencesSnapshot> = safePreferences.map { prefs ->
        val style = prefs[KEY_ART_ANIM_STYLE]?.let {
            try { ArtworkAnimationStyle.valueOf(it) } catch (_: Exception) { ArtworkAnimationStyle.SMOOTH }
        } ?: if (prefs[KEY_ARTWORK_ANIMATION] == false) ArtworkAnimationStyle.NONE else ArtworkAnimationStyle.SMOOTH
        val intensity = prefs[KEY_ART_ANIM_INTENSITY]?.let {
            try { ArtworkAnimationIntensity.valueOf(it) } catch (_: Exception) { ArtworkAnimationIntensity.MEDIUM }
        } ?: ArtworkAnimationIntensity.MEDIUM

        val spotlightStyle = prefs[KEY_SPOTLIGHT_TRANSITION_STYLE]?.let {
            try { SpotlightTransitionStyle.valueOf(it) } catch (_: Exception) { SpotlightTransitionStyle.SLIDE }
        } ?: SpotlightTransitionStyle.SLIDE
        val spotlightSpeed = prefs[KEY_SPOTLIGHT_TRANSITION_SPEED]?.let {
            try { SpotlightTransitionSpeed.valueOf(it) } catch (_: Exception) { SpotlightTransitionSpeed.NORMAL }
        } ?: SpotlightTransitionSpeed.NORMAL
        val spotlightDir = prefs[KEY_SPOTLIGHT_TRANSITION_DIRECTION]?.let {
            try { SpotlightTransitionDirection.valueOf(it) } catch (_: Exception) { SpotlightTransitionDirection.AUTOMATIC }
        } ?: SpotlightTransitionDirection.AUTOMATIC

        ArtworkAnimationPreferencesSnapshot(
            style = style,
            intensity = intensity,
            enableKenBurns = prefs[KEY_ART_ANIM_KEN_BURNS] ?: true,
            enableParallax = prefs[KEY_ART_ANIM_PARALLAX] ?: true,
            enableAmbientLighting = prefs[KEY_ART_ANIM_AMBIENT_LIGHT] ?: true,
            enableCardBreathing = prefs[KEY_ART_ANIM_BREATHING] ?: true,
            enableTrackTransitions = prefs[KEY_ART_ANIM_TRACK_TRANSITIONS] ?: true,
            respectReducedMotion = prefs[KEY_ART_ANIM_RESPECT_REDUCED_MOTION] ?: true,
            spotlightTransitionStyle = spotlightStyle,
            spotlightTransitionSpeed = spotlightSpeed,
            spotlightTransitionDirection = spotlightDir
        )
    }.distinctUntilChanged()

    // --- Walkman Startup Animation Flows ---
    val startupAnimationPreferences: Flow<StartupAnimationPreferencesSnapshot> = safePreferences.map { prefs ->
        val style = prefs[KEY_STARTUP_ANIM_STYLE]?.let {
            try { StartupAnimationStyle.valueOf(it) } catch (_: Exception) { StartupAnimationStyle.FULL_WALKMAN }
        } ?: StartupAnimationStyle.FULL_WALKMAN
        val speed = prefs[KEY_STARTUP_ANIM_SPEED]?.let {
            try { StartupAnimationSpeed.valueOf(it) } catch (_: Exception) { StartupAnimationSpeed.NORMAL }
        } ?: StartupAnimationSpeed.NORMAL

        StartupAnimationPreferencesSnapshot(
            style = style,
            speed = speed,
            mechanicalSoundsEnabled = prefs[KEY_STARTUP_ANIM_SOUNDS] ?: false,
            vibrationEnabled = prefs[KEY_STARTUP_ANIM_VIBRATION] ?: true
        )
    }.distinctUntilChanged()

    // --- Global Motion & Gesture Flows ---
    val motionPreferences: Flow<MotionPreferencesSnapshot> = safePreferences.map { prefs ->
        val quality = prefs[KEY_MOTION_QUALITY]?.let {
            try { MotionQuality.valueOf(it) } catch (_: Exception) { MotionQuality.FLUID }
        } ?: MotionQuality.FLUID
        val speed = prefs[KEY_MOTION_SPEED]?.let {
            try { MotionSpeed.valueOf(it) } catch (_: Exception) { MotionSpeed.NORMAL }
        } ?: MotionSpeed.NORMAL

        MotionPreferencesSnapshot(
            quality = quality,
            speed = speed,
            horizontalSwipesEnabled = prefs[KEY_MOTION_HORIZONTAL_SWIPES] ?: true,
            verticalSwipesEnabled = prefs[KEY_MOTION_VERTICAL_SWIPES] ?: true,
            playerGesturesEnabled = prefs[KEY_MOTION_PLAYER_GESTURES] ?: (prefs[KEY_GESTURES_ENABLED] ?: true),
            cassetteTransitionsEnabled = prefs[KEY_MOTION_CASSETTE_TRANSITIONS] ?: true,
            swipeToDismissEnabled = prefs[KEY_MOTION_SWIPE_DISMISS] ?: true,
            reducedMotion = prefs[KEY_MOTION_REDUCED_MOTION] ?: false,
            disableDecorativeAnimations = prefs[KEY_MOTION_DISABLE_DECORATIVE] ?: false
        )
    }.distinctUntilChanged()

    // --- Local User Profile Flow ---
    val userProfile: Flow<UserProfileSnapshot> = safePreferences.map { prefs ->
        UserProfileSnapshot(
            displayName = prefs[KEY_PROFILE_DISPLAY_NAME]?.takeIf { it.isNotBlank() } ?: "SANGEET Listener",
            avatarUriOrPath = prefs[KEY_PROFILE_AVATAR_PATH] ?: "",
            tagline = prefs[KEY_PROFILE_TAGLINE]?.takeIf { it.isNotBlank() } ?: "Offline Walkman Collector",
            createdAtEpochMs = prefs[KEY_PROFILE_CREATED_AT] ?: 0L,
            showListeningStats = prefs[KEY_PROFILE_SHOW_STATS] ?: true
        )
    }.distinctUntilChanged()

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

    suspend fun setCassetteStyle(style: CassetteStyle) {
        dataStore.edit { it[KEY_CASSETTE_STYLE] = style.name }
    }

    suspend fun setWalkmanMechanicalSounds(enabled: Boolean) {
        dataStore.edit { it[KEY_WALKMAN_MECHANICAL_SOUNDS] = enabled }
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
        dataStore.edit { it[KEY_LYRICS_FONT_SIZE] = size.coerceIn(14f, 30f) }
    }

    suspend fun setLyricsFontStyle(style: LyricsFontStyle) {
        dataStore.edit { it[KEY_LYRICS_FONT_STYLE] = style.name }
    }

    suspend fun setLyricsLineSpacing(spacingDp: Float) {
        dataStore.edit { it[KEY_LYRICS_LINE_SPACING] = spacingDp.coerceIn(8f, 36f) }
    }

    suspend fun setLyricsHighlightColor(colorLong: Long) {
        dataStore.edit { it[KEY_LYRICS_HIGHLIGHT_COLOR] = colorLong }
    }

    suspend fun setLyricsInactiveOpacity(opacity: Float) {
        dataStore.edit { it[KEY_LYRICS_INACTIVE_OPACITY] = opacity.coerceIn(0.15f, 0.85f) }
    }

    suspend fun setLyricsArtworkBlur(radius: Float) {
        dataStore.edit { it[KEY_LYRICS_ARTWORK_BLUR] = radius.coerceIn(0f, 30f) }
    }

    suspend fun setLyricsBackgroundDimming(dimming: Float) {
        dataStore.edit { it[KEY_LYRICS_BG_DIMMING] = dimming.coerceIn(0.30f, 0.95f) }
    }

    suspend fun setLyricsCurrentLineScale(scale: Float) {
        dataStore.edit { it[KEY_LYRICS_CURRENT_SCALE] = scale.coerceIn(1.0f, 1.30f) }
    }

    suspend fun setLyricsAutoSync(enabled: Boolean) {
        dataStore.edit { it[KEY_LYRICS_AUTO_SYNC] = enabled }
    }

    suspend fun setLyricsWordByWordEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_LYRICS_WORD_BY_WORD] = enabled }
    }

    suspend fun setLyricsReducedMotion(enabled: Boolean) {
        dataStore.edit { it[KEY_LYRICS_REDUCED_MOTION] = enabled }
    }

    suspend fun setLyricsDisplayMode(mode: String) {
        dataStore.edit { it[KEY_LYRICS_DISPLAY_MODE] = mode }
    }

    suspend fun resetLyricsSettingsToDefault() {
        dataStore.edit {
            it.remove(KEY_LYRICS_FONT_SIZE)
            it.remove(KEY_LYRICS_FONT_STYLE)
            it.remove(KEY_LYRICS_LINE_SPACING)
            it.remove(KEY_LYRICS_HIGHLIGHT_COLOR)
            it.remove(KEY_LYRICS_INACTIVE_OPACITY)
            it.remove(KEY_LYRICS_ARTWORK_BLUR)
            it.remove(KEY_LYRICS_BG_DIMMING)
            it.remove(KEY_LYRICS_CURRENT_SCALE)
            it.remove(KEY_LYRICS_AUTO_SYNC)
            it.remove(KEY_LYRICS_WORD_BY_WORD)
            it.remove(KEY_LYRICS_REDUCED_MOTION)
            it.remove(KEY_LYRICS_DISPLAY_MODE)
        }
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

    // --- Now Playing Notification Mutations ---

    suspend fun setNotificationCustomizationEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIF_CUSTOMIZATION_ENABLED] = enabled }
    }

    suspend fun setNotificationShowArtwork(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIF_SHOW_ARTWORK] = enabled }
    }

    suspend fun setNotificationShowArtist(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIF_SHOW_ARTIST] = enabled }
    }

    suspend fun setNotificationShowAlbum(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIF_SHOW_ALBUM] = enabled }
    }

    suspend fun setNotificationShowProgress(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIF_SHOW_PROGRESS] = enabled }
    }

    suspend fun setNotificationShowPrevNext(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIF_SHOW_PREV_NEXT] = enabled }
    }

    suspend fun setNotificationShowSeek(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIF_SHOW_SEEK] = enabled }
    }

    suspend fun setNotificationShowStop(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIF_SHOW_STOP] = enabled }
    }

    suspend fun setNotificationArtworkSize(size: NotificationArtworkSize) {
        dataStore.edit { it[KEY_NOTIF_ARTWORK_SIZE] = size.name }
    }

    suspend fun setNotificationLockScreenMode(mode: LockScreenMediaMode) {
        dataStore.edit { it[KEY_NOTIF_LOCKSCREEN_MODE] = mode.name }
    }

    suspend fun setNotificationPrivacyMode(mode: NotificationPrivacyMode) {
        dataStore.edit { it[KEY_NOTIF_PRIVACY_MODE] = mode.name }
    }

    suspend fun resetNotificationSettingsToDefault() {
        dataStore.edit {
            it.remove(KEY_NOTIF_CUSTOMIZATION_ENABLED)
            it.remove(KEY_NOTIF_SHOW_ARTWORK)
            it.remove(KEY_NOTIF_SHOW_ARTIST)
            it.remove(KEY_NOTIF_SHOW_ALBUM)
            it.remove(KEY_NOTIF_SHOW_PROGRESS)
            it.remove(KEY_NOTIF_SHOW_PREV_NEXT)
            it.remove(KEY_NOTIF_SHOW_SEEK)
            it.remove(KEY_NOTIF_SHOW_STOP)
            it.remove(KEY_NOTIF_ARTWORK_SIZE)
            it.remove(KEY_NOTIF_LOCKSCREEN_MODE)
            it.remove(KEY_NOTIF_PRIVACY_MODE)
        }
    }

    // --- Last Playback Session Persistence ---

    suspend fun saveLastPlaybackSession(trackId: Long, queueIds: List<Long>, positionMs: Long) {
        val serializedQueue = queueIds.take(500).joinToString(",")
        dataStore.edit {
            it[KEY_LAST_PLAYBACK_TRACK_ID] = trackId
            it[KEY_LAST_PLAYBACK_QUEUE_IDS] = serializedQueue
            it[KEY_LAST_PLAYBACK_POSITION_MS] = positionMs.coerceAtLeast(0L)
        }
    }

    suspend fun getLastPlaybackSession(): LastPlaybackSession? {
        val prefs = safePreferences.first()
        val trackId = prefs[KEY_LAST_PLAYBACK_TRACK_ID] ?: return null
        val queueIds = prefs[KEY_LAST_PLAYBACK_QUEUE_IDS]
            ?.split(",")
            ?.mapNotNull { it.toLongOrNull() }
            ?: emptyList()
        val positionMs = (prefs[KEY_LAST_PLAYBACK_POSITION_MS] ?: 0L).coerceAtLeast(0L)
        return LastPlaybackSession(trackId = trackId, queueIds = queueIds, positionMs = positionMs)
    }

    suspend fun clearLastPlaybackSession() {
        dataStore.edit {
            it.remove(KEY_LAST_PLAYBACK_TRACK_ID)
            it.remove(KEY_LAST_PLAYBACK_QUEUE_IDS)
            it.remove(KEY_LAST_PLAYBACK_POSITION_MS)
        }
    }

    // --- Home & Cover Artwork Animation Mutations ---

    suspend fun setArtworkAnimationStyle(style: ArtworkAnimationStyle) {
        dataStore.edit {
            it[KEY_ART_ANIM_STYLE] = style.name
            it[KEY_ARTWORK_ANIMATION] = style != ArtworkAnimationStyle.NONE
        }
    }

    suspend fun setArtworkAnimationIntensity(intensity: ArtworkAnimationIntensity) {
        dataStore.edit { it[KEY_ART_ANIM_INTENSITY] = intensity.name }
    }

    suspend fun setArtworkKenBurnsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ART_ANIM_KEN_BURNS] = enabled }
    }

    suspend fun setArtworkParallaxEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ART_ANIM_PARALLAX] = enabled }
    }

    suspend fun setArtworkAmbientLightingEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ART_ANIM_AMBIENT_LIGHT] = enabled }
    }

    suspend fun setArtworkCardBreathingEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ART_ANIM_BREATHING] = enabled }
    }

    suspend fun setArtworkTrackTransitionsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ART_ANIM_TRACK_TRANSITIONS] = enabled }
    }

    suspend fun setArtworkRespectReducedMotion(enabled: Boolean) {
        dataStore.edit { it[KEY_ART_ANIM_RESPECT_REDUCED_MOTION] = enabled }
    }

    suspend fun setSpotlightTransitionStyle(style: SpotlightTransitionStyle) {
        dataStore.edit { it[KEY_SPOTLIGHT_TRANSITION_STYLE] = style.name }
    }

    suspend fun setSpotlightTransitionSpeed(speed: SpotlightTransitionSpeed) {
        dataStore.edit { it[KEY_SPOTLIGHT_TRANSITION_SPEED] = speed.name }
    }

    suspend fun setSpotlightTransitionDirection(direction: SpotlightTransitionDirection) {
        dataStore.edit { it[KEY_SPOTLIGHT_TRANSITION_DIRECTION] = direction.name }
    }

    suspend fun resetArtworkAnimationSettingsToDefault() {
        dataStore.edit {
            it.remove(KEY_ART_ANIM_STYLE)
            it.remove(KEY_ART_ANIM_INTENSITY)
            it.remove(KEY_ART_ANIM_KEN_BURNS)
            it.remove(KEY_ART_ANIM_PARALLAX)
            it.remove(KEY_ART_ANIM_AMBIENT_LIGHT)
            it.remove(KEY_ART_ANIM_BREATHING)
            it.remove(KEY_ART_ANIM_TRACK_TRANSITIONS)
            it.remove(KEY_ART_ANIM_RESPECT_REDUCED_MOTION)
            it.remove(KEY_SPOTLIGHT_TRANSITION_STYLE)
            it.remove(KEY_SPOTLIGHT_TRANSITION_SPEED)
            it.remove(KEY_SPOTLIGHT_TRANSITION_DIRECTION)
            it[KEY_ARTWORK_ANIMATION] = true
        }
    }

    // --- Walkman Startup Animation Mutations ---

    suspend fun setStartupAnimationStyle(style: StartupAnimationStyle) {
        dataStore.edit { it[KEY_STARTUP_ANIM_STYLE] = style.name }
    }

    suspend fun setStartupAnimationSpeed(speed: StartupAnimationSpeed) {
        dataStore.edit { it[KEY_STARTUP_ANIM_SPEED] = speed.name }
    }

    suspend fun setStartupAnimationSoundsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_STARTUP_ANIM_SOUNDS] = enabled }
    }

    suspend fun setStartupAnimationVibrationEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_STARTUP_ANIM_VIBRATION] = enabled }
    }

    suspend fun resetStartupAnimationSettingsToDefault() {
        dataStore.edit {
            it.remove(KEY_STARTUP_ANIM_STYLE)
            it.remove(KEY_STARTUP_ANIM_SPEED)
            it.remove(KEY_STARTUP_ANIM_SOUNDS)
            it.remove(KEY_STARTUP_ANIM_VIBRATION)
        }
    }

    // --- Global Motion & Gesture Mutations ---

    suspend fun setMotionQuality(quality: MotionQuality) {
        dataStore.edit { it[KEY_MOTION_QUALITY] = quality.name }
    }

    suspend fun setMotionSpeed(speed: MotionSpeed) {
        dataStore.edit { it[KEY_MOTION_SPEED] = speed.name }
    }

    suspend fun setMotionHorizontalSwipesEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_MOTION_HORIZONTAL_SWIPES] = enabled }
    }

    suspend fun setMotionVerticalSwipesEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_MOTION_VERTICAL_SWIPES] = enabled }
    }

    suspend fun setMotionPlayerGesturesEnabled(enabled: Boolean) {
        dataStore.edit {
            it[KEY_MOTION_PLAYER_GESTURES] = enabled
            it[KEY_GESTURES_ENABLED] = enabled
        }
    }

    suspend fun setMotionCassetteTransitionsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_MOTION_CASSETTE_TRANSITIONS] = enabled }
    }

    suspend fun setMotionSwipeToDismissEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_MOTION_SWIPE_DISMISS] = enabled }
    }

    suspend fun setMotionReducedMotion(enabled: Boolean) {
        dataStore.edit { it[KEY_MOTION_REDUCED_MOTION] = enabled }
    }

    suspend fun setMotionDisableDecorativeAnimations(enabled: Boolean) {
        dataStore.edit { it[KEY_MOTION_DISABLE_DECORATIVE] = enabled }
    }

    suspend fun resetMotionSettingsToDefault() {
        dataStore.edit {
            it.remove(KEY_MOTION_QUALITY)
            it.remove(KEY_MOTION_SPEED)
            it.remove(KEY_MOTION_HORIZONTAL_SWIPES)
            it.remove(KEY_MOTION_VERTICAL_SWIPES)
            it.remove(KEY_MOTION_PLAYER_GESTURES)
            it.remove(KEY_MOTION_CASSETTE_TRANSITIONS)
            it.remove(KEY_MOTION_SWIPE_DISMISS)
            it.remove(KEY_MOTION_REDUCED_MOTION)
            it.remove(KEY_MOTION_DISABLE_DECORATIVE)
            it.remove(KEY_GESTURES_ENABLED)
        }
    }

    // --- Local User Profile Mutations ---

    suspend fun ensureProfileInitialized(nowMs: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            if ((prefs[KEY_PROFILE_CREATED_AT] ?: 0L) <= 0L) {
                prefs[KEY_PROFILE_CREATED_AT] = nowMs
            }
        }
    }

    suspend fun setProfileDisplayName(name: String) {
        val sanitized = name.trim().take(48).ifEmpty { "SANGEET Listener" }
        dataStore.edit { prefs ->
            prefs[KEY_PROFILE_DISPLAY_NAME] = sanitized
            if ((prefs[KEY_PROFILE_CREATED_AT] ?: 0L) <= 0L) {
                prefs[KEY_PROFILE_CREATED_AT] = System.currentTimeMillis()
            }
        }
    }

    suspend fun setProfileTagline(tagline: String) {
        val sanitized = tagline.trim().take(80).ifEmpty { "Offline Walkman Collector" }
        dataStore.edit { prefs ->
            prefs[KEY_PROFILE_TAGLINE] = sanitized
            if ((prefs[KEY_PROFILE_CREATED_AT] ?: 0L) <= 0L) {
                prefs[KEY_PROFILE_CREATED_AT] = System.currentTimeMillis()
            }
        }
    }

    suspend fun setProfileAvatarPath(pathOrUri: String) {
        dataStore.edit { prefs ->
            prefs[KEY_PROFILE_AVATAR_PATH] = pathOrUri
            if ((prefs[KEY_PROFILE_CREATED_AT] ?: 0L) <= 0L) {
                prefs[KEY_PROFILE_CREATED_AT] = System.currentTimeMillis()
            }
        }
    }

    suspend fun setProfileShowListeningStats(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_PROFILE_SHOW_STATS] = show
        }
    }

    // --- Crossfade & DJ Mixing Mutations ---

    suspend fun setCrossfadeMode(mode: CrossfadeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_CROSSFADE_MODE] = mode.name
            if (mode == CrossfadeMode.ADVANCED && (prefs[KEY_CROSSFADE_SECONDS] ?: 0) <= 0) {
                prefs[KEY_CROSSFADE_SECONDS] = 5
            }
        }
    }

    suspend fun setCrossfadeAutoEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_CROSSFADE_AUTO_ENABLED] = enabled }
    }

    suspend fun setCrossfadeEqualPower(enabled: Boolean) {
        dataStore.edit { it[KEY_CROSSFADE_EQUAL_POWER] = enabled }
    }

    suspend fun setDjDurationSeconds(seconds: Int) {
        dataStore.edit { it[KEY_DJ_DURATION_SECONDS] = seconds.coerceIn(4, 20) }
    }

    suspend fun setDjBeatSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_DJ_BEAT_SYNC] = enabled }
    }

    suspend fun setDjAutoBpmDetectionEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_DJ_AUTO_BPM] = enabled }
    }

    suspend fun setDjTransitionStyle(style: DjTransitionStyle, applyRecommendedDuration: Boolean = false) {
        dataStore.edit {
            it[KEY_DJ_TRANSITION_STYLE] = style.name
            if (applyRecommendedDuration) {
                it[KEY_DJ_DURATION_SECONDS] = style.recommendedDurationSeconds.coerceIn(4, 20)
            }
        }
    }

    suspend fun setDjIncomingVolume(volume: Float) {
        dataStore.edit { it[KEY_DJ_INCOMING_VOL] = volume.coerceIn(0.2f, 1.0f) }
    }

    suspend fun setDjOutgoingVolume(volume: Float) {
        dataStore.edit { it[KEY_DJ_OUTGOING_VOL] = volume.coerceIn(0.2f, 1.0f) }
    }

    suspend fun resetCrossfadeSettingsToDefault() {
        dataStore.edit {
            it.remove(KEY_CROSSFADE_MODE)
            it.remove(KEY_CROSSFADE_AUTO_ENABLED)
            it.remove(KEY_CROSSFADE_SECONDS)
            it.remove(KEY_CROSSFADE_EQUAL_POWER)
            it.remove(KEY_DJ_DURATION_SECONDS)
            it.remove(KEY_DJ_BEAT_SYNC)
            it.remove(KEY_DJ_AUTO_BPM)
            it.remove(KEY_DJ_TRANSITION_STYLE)
            it.remove(KEY_DJ_INCOMING_VOL)
            it.remove(KEY_DJ_OUTGOING_VOL)
        }
    }

    suspend fun resetAllSettingsToDefault() {
        dataStore.edit { it.clear() }
    }
}
