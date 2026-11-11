package com.example.sangeet.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
        SpatialPreset::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun audioTrackDao(): AudioTrackDao
    abstract fun videoTrackDao(): VideoTrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyAndStatsDao(): HistoryAndStatsDao
    abstract fun presetDao(): PresetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sangeet_database.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                populateDefaultPresets(getInstance(context))
                            }
                        }
                    })
                    .fallbackToDestructiveMigration()
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
