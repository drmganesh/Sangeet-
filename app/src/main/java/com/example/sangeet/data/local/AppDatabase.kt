package com.example.sangeet.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.sangeet.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        AudioTrack::class,
        VideoTrack::class,
        Playlist::class,
        PlaylistTrack::class,
        PlaybackHistory::class,
        SearchHistory::class,
        EqualizerPreset::class,
        SpatialPreset::class,
        BlacklistEntry::class,
        WhitelistEntry::class,
        FolderRule::class,
        FolderPermission::class,
        AlbumArtworkEntry::class,
        LyricsMetadataEntry::class
    ],
    version = 3,
    exportSchema = false
)
@androidx.room.TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun audioTrackDao(): AudioTrackDao
    abstract fun videoTrackDao(): VideoTrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyAndStatsDao(): HistoryAndStatsDao
    abstract fun presetDao(): PresetDao
    abstract fun blacklistDao(): BlacklistDao
    abstract fun whitelistDao(): WhitelistDao
    abstract fun folderRuleDao(): FolderRuleDao
    abstract fun folderPermissionDao(): FolderPermissionDao
    abstract fun artworkDao(): ArtworkDao
    abstract fun lyricsDao(): LyricsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audio_tracks ADD COLUMN artworkUri TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `blacklist_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `identifier` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `whitelist_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `identifier` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `album_artwork_entries` (
                        `albumKey` TEXT PRIMARY KEY NOT NULL,
                        `artworkPath` TEXT NOT NULL,
                        `isCustom` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `lyrics_metadata_entries` (
                        `trackId` INTEGER PRIMARY KEY NOT NULL,
                        `lyricsPath` TEXT NOT NULL,
                        `customOffsetMs` INTEGER NOT NULL,
                        `isManual` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Preserve all existing audio and video tracks while adding SAF metadata columns
                db.execSQL("ALTER TABLE audio_tracks ADD COLUMN sourceTreeUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE audio_tracks ADD COLUMN documentId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE audio_tracks ADD COLUMN folderPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE audio_tracks ADD COLUMN isWhitelistOnly INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE video_tracks ADD COLUMN sourceTreeUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE video_tracks ADD COLUMN documentId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE video_tracks ADD COLUMN folderPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE video_tracks ADD COLUMN isWhitelistOnly INTEGER NOT NULL DEFAULT 0")

                // Preserve existing blacklist & whitelist entries while adding folder/status metadata
                db.execSQL("ALTER TABLE blacklist_entries ADD COLUMN displayPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE blacklist_entries ADD COLUMN treeUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE blacklist_entries ADD COLUMN documentId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE blacklist_entries ADD COLUMN affectedCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE blacklist_entries ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")

                db.execSQL("ALTER TABLE whitelist_entries ADD COLUMN displayPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE whitelist_entries ADD COLUMN treeUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE whitelist_entries ADD COLUMN documentId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE whitelist_entries ADD COLUMN affectedCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE whitelist_entries ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")

                // Create FolderRule & FolderPermission tables
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `folder_rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `listKind` TEXT NOT NULL,
                        `ruleType` TEXT NOT NULL,
                        `treeUri` TEXT NOT NULL,
                        `documentId` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `displayPath` TEXT NOT NULL,
                        `includeSubfolders` INTEGER NOT NULL,
                        `affectedMediaCount` INTEGER NOT NULL,
                        `subfolderCount` INTEGER NOT NULL,
                        `creationDate` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `lastScannedAt` INTEGER NOT NULL,
                        `lastError` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `folder_permissions` (
                        `treeUri` TEXT PRIMARY KEY NOT NULL,
                        `documentId` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `hasReadPermission` INTEGER NOT NULL,
                        `hasWritePermission` INTEGER NOT NULL,
                        `isPersisted` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `grantedAt` INTEGER NOT NULL,
                        `lastVerifiedAt` INTEGER NOT NULL,
                        `errorMessage` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sangeet_database.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                populateDefaultPresets(getInstance(context))
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun populateDefaultPresets(database: AppDatabase) {
            val presetDao = database.presetDao()
            val flatBands = "[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]"
            val bassBoostBands = "[6.0,5.5,5.0,4.0,2.5,1.0,0,0,0,0,0,0,0,0,0]"
            val deepBassBands = "[8.0,7.0,6.0,4.5,2.0,0,0,0,0,0,0,0,0,0,0]"
            val vocalBands = "[-2.0,-2.0,-1.0,0,1.5,3.0,4.0,4.5,4.0,3.0,2.0,1.0,0,-1.0,-2.0]"
            val classicalBands = "[4.5,4.0,3.5,2.5,1.0,-0.5,-1.0,0,1.5,2.5,3.5,4.0,4.5,4.0,3.5]"
            val rockBands = "[5.0,4.5,3.0,1.5,-0.5,-1.5,-1.0,0,1.5,2.5,4.0,5.0,5.5,5.0,4.5]"
            val popBands = "[-1.5,-1.0,0,2.0,3.5,4.5,4.0,3.0,1.5,0.5,1.5,3.0,4.0,3.5,2.0]"
            val jazzBands = "[3.5,3.0,2.0,1.5,1.0,0.5,0,0.5,1.0,2.0,3.0,3.5,4.0,3.5,3.0]"
            val electronicBands = "[6.5,5.5,4.5,2.0,0.5,-1.0,0,1.0,2.0,3.5,5.0,6.0,6.5,5.5,4.0]"
            val acousticBands = "[3.0,2.5,2.0,1.0,0.5,0.5,1.0,1.5,2.0,2.5,3.0,3.5,3.5,3.0,2.5]"
            val trebleBoostBands = "[0,0,0,0,0,0,0,0,1.0,2.0,3.5,5.0,6.5,7.0,7.5]"

            val defaultEqPresets = listOf(
                EqualizerPreset(name = "Flat", isCustom = false, bandsGainJson = flatBands),
                EqualizerPreset(name = "Bass Boost", isCustom = false, bandsGainJson = bassBoostBands, bassBoost = 4f),
                EqualizerPreset(name = "Deep Bass", isCustom = false, bandsGainJson = deepBassBands, bassBoost = 6f),
                EqualizerPreset(name = "Vocal", isCustom = false, bandsGainJson = vocalBands),
                EqualizerPreset(name = "Classical", isCustom = false, bandsGainJson = classicalBands),
                EqualizerPreset(name = "Rock", isCustom = false, bandsGainJson = rockBands),
                EqualizerPreset(name = "Pop", isCustom = false, bandsGainJson = popBands),
                EqualizerPreset(name = "Jazz", isCustom = false, bandsGainJson = jazzBands),
                EqualizerPreset(name = "Electronic", isCustom = false, bandsGainJson = electronicBands, bassBoost = 5f, trebleBoost = 3f),
                EqualizerPreset(name = "Acoustic", isCustom = false, bandsGainJson = acousticBands),
                EqualizerPreset(name = "Treble Boost", isCustom = false, bandsGainJson = trebleBoostBands, trebleBoost = 5f)
            )

            for (p in defaultEqPresets) {
                presetDao.insertEqualizerPreset(p)
            }

            val defaultSpatialPresets = listOf(
                SpatialPreset(name = "Natural Stereo", isCustom = false, isEnabled = false, spatialIntensity = 0.0f, rotationSpeed = 0f, rotationDirection = 2, soundstageWidth = 1.0f, soundstageDepth = 0.5f),
                SpatialPreset(name = "Wide Stereo", isCustom = false, isEnabled = true, spatialIntensity = 0.6f, rotationSpeed = 0f, rotationDirection = 2, soundstageWidth = 1.6f, soundstageDepth = 0.6f),
                SpatialPreset(name = "360° Surround", isCustom = false, isEnabled = true, spatialIntensity = 0.85f, rotationSpeed = 0.0f, rotationDirection = 2, soundstageWidth = 1.8f, soundstageDepth = 0.8f),
                SpatialPreset(name = "Rotating Surround", isCustom = false, isEnabled = true, spatialIntensity = 0.9f, rotationSpeed = 1.0f, rotationDirection = 2, soundstageWidth = 2.0f, soundstageDepth = 0.9f),
                SpatialPreset(name = "Cinema", isCustom = false, isEnabled = true, spatialIntensity = 0.75f, rotationSpeed = 0.0f, rotationDirection = 2, soundstageWidth = 1.5f, soundstageDepth = 1.0f),
                SpatialPreset(name = "Concert Hall", isCustom = false, isEnabled = true, spatialIntensity = 0.8f, rotationSpeed = 0.0f, rotationDirection = 2, soundstageWidth = 1.7f, soundstageDepth = 0.85f),
                SpatialPreset(name = "Immersive", isCustom = false, isEnabled = true, spatialIntensity = 0.95f, rotationSpeed = 0.5f, rotationDirection = 2, soundstageWidth = 2.0f, soundstageDepth = 0.95f),
                SpatialPreset(name = "Headphone Spatial", isCustom = false, isEnabled = true, spatialIntensity = 0.7f, rotationSpeed = 0.0f, rotationDirection = 2, soundstageWidth = 1.4f, soundstageDepth = 0.7f)
            )

            for (sp in defaultSpatialPresets) {
                presetDao.insertSpatialPreset(sp)
            }
        }
    }
}
