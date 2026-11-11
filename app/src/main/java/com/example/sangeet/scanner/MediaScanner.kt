package com.example.sangeet.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.example.sangeet.data.local.AppDatabase
import com.example.sangeet.data.model.AudioTrack
import com.example.sangeet.data.model.VideoTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class MediaScanner(private val context: Context) {

    private val db = AppDatabase.getInstance(context)

    suspend fun scanLocalAudio(): Int = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<AudioTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.YEAR
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown Track"
                    val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val album = cursor.getString(albumCol) ?: "Unknown Album"
                    val duration = cursor.getLong(durationCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val dateAdded = cursor.getLong(dateCol) * 1000L
                    val trackNum = cursor.getInt(trackCol)
                    val mime = cursor.getString(mimeCol) ?: "audio/mpeg"
                    val size = cursor.getLong(sizeCol)
                    val year = cursor.getInt(yearCol)

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    var genre = "Music"
                    var embeddedLyrics = ""
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(context, Uri.parse(contentUri))
                        val extractedGenre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                        if (!extractedGenre.isNullOrBlank()) genre = extractedGenre
                        retriever.release()
                    } catch (_: Exception) {}

                    tracks.add(
                        AudioTrack(
                            id = id,
                            title = title,
                            artist = if (artist == "<unknown>") "Unknown Artist" else artist,
                            album = if (album == "<unknown>") "Unknown Album" else album,
                            genre = genre,
                            durationMs = duration,
                            trackNumber = trackNum,
                            path = path,
                            contentUri = contentUri,
                            dateAdded = dateAdded,
                            year = year,
                            mimeType = mime,
                            fileSize = size,
                            embeddedLyrics = embeddedLyrics
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MediaScanner", "Error querying MediaStore audio", e)
        }

        if (tracks.isNotEmpty()) {
            db.audioTrackDao().insertTracks(tracks)
        }
        tracks.size
    }

    suspend fun scanLocalVideos(): Int = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoTrack>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.RESOLUTION,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val resCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RESOLUTION)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Video $id"
                    val duration = cursor.getLong(durationCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val dateAdded = cursor.getLong(dateCol) * 1000L
                    val res = cursor.getString(resCol) ?: "1080p"
                    val mime = cursor.getString(mimeCol) ?: "video/mp4"
                    val size = cursor.getLong(sizeCol)

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    videos.add(
                        VideoTrack(
                            id = id,
                            title = title,
                            durationMs = duration,
                            path = path,
                            contentUri = contentUri,
                            dateAdded = dateAdded,
                            resolution = res,
                            mimeType = mime,
                            fileSize = size
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MediaScanner", "Error querying MediaStore video", e)
        }

        if (videos.isNotEmpty()) {
            db.videoTrackDao().insertVideos(videos)
        }
        videos.size
    }

    suspend fun importAudioUri(uri: Uri): AudioTrack? = withContext(Dispatchers.IO) {
        try {
            var fileName = "Imported Track"
            var fileSize = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx != -1) fileName = cursor.getString(nameIdx) ?: fileName
                    if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                }
            }

            var duration = 0L
            var artist = "Local Storage"
            var album = "Imported"
            var title = fileName.substringBeforeLast(".")
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { if (it.isNotBlank()) title = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { if (it.isNotBlank()) artist = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { if (it.isNotBlank()) album = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { duration = it }
                retriever.release()
            } catch (_: Exception) {}

            val track = AudioTrack(
                id = System.currentTimeMillis() xor uri.hashCode().toLong(),
                title = title,
                artist = artist,
                album = album,
                genre = "Offline File",
                durationMs = if (duration > 0) duration else 180000L,
                path = uri.toString(),
                contentUri = uri.toString(),
                dateAdded = System.currentTimeMillis(),
                fileSize = fileSize
            )
            db.audioTrackDao().insertTrack(track)
            track
        } catch (e: Exception) {
            Log.e("MediaScanner", "Failed to import audio URI", e)
            null
        }
    }

    /**
     * Synthesizes 3 high-fidelity lossless demo audio tracks locally with real musical notes,
     * bass, arpeggios, and synced lyrics for testing 15-band EQ and 360° Spatial Audio offline.
     */
    suspend fun generateDemoOfflineTracks(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val demoDir = File(context.filesDir, "demo_music").apply { mkdirs() }
        val generatedTracks = mutableListOf<AudioTrack>()

        val trackSpecs = listOf(
            Triple("Celestial Echoes", "Aura & Spatial Wave", "SANGEET 360° Immersive"),
            Triple("Midnight Groove", "HyperBass & Rhythm", "15-Band EQ Showcase"),
            Triple("Acoustic Horizon", "Binaural Strings", "Pure Studio Master")
        )

        val chords = listOf(
            // C minor 9 chord frequencies
            floatArrayOf(130.81f, 155.56f, 196.00f, 233.08f, 293.66f, 523.25f),
            // Ab major 7 chord frequencies
            floatArrayOf(103.83f, 130.81f, 155.56f, 207.65f, 261.63f, 415.30f),
            // F minor 7 chord frequencies
            floatArrayOf(87.31f, 130.81f, 174.61f, 207.65f, 261.63f, 349.23f)
        )

        for (i in trackSpecs.indices) {
            val (title, artist, album) = trackSpecs[i]
            val wavFile = File(demoDir, "demo_${i + 1}.wav")
            val id = 9000000L + i

            if (!wavFile.exists() || wavFile.length() < 1000) {
                generateSynthesizedWav(wavFile, durationSeconds = 45, chordProgression = chords[i], trackIndex = i)
            }

            val lyrics = """
                [00:01.00]Welcome to SANGEET Music & Video Player
                [00:04.50]100% Offline with zero internet required
                [00:08.20]Experience our professional 15-Band Equalizer
                [00:12.80]Feel the 360° Rotating Spatial Audio soundstage
                [00:18.00]Deep bass enhancement and crisp high treble
                [00:23.50]Binaural crossfeed and headphone stereo widening
                [00:28.00]Nine custom player styles crafted for you
                [00:33.20]Smooth transitions, lyrics sync, and offline privacy
                [00:38.00]SANGEET: Your Music, Your World, Unplugged
            """.trimIndent()

            val track = AudioTrack(
                id = id,
                title = title,
                artist = artist,
                album = album,
                genre = if (i == 0) "Ambient Spatial" else if (i == 1) "Deep Funk" else "Acoustic",
                durationMs = 45000L,
                path = wavFile.absolutePath,
                contentUri = Uri.fromFile(wavFile).toString(),
                dateAdded = System.currentTimeMillis() - (i * 3600000L),
                year = 2026,
                mimeType = "audio/wav",
                fileSize = wavFile.length(),
                embeddedLyrics = lyrics
            )
            generatedTracks.add(track)
        }

        db.audioTrackDao().insertTracks(generatedTracks)
        generatedTracks
    }

    private fun generateSynthesizedWav(
        outputFile: File,
        durationSeconds: Int,
        chordProgression: FloatArray,
        trackIndex: Int
    ) {
        val sampleRate = 44100
        val channels = 2
        val totalSamples = sampleRate * durationSeconds
        val bytesPerSample = 2 // 16-bit
        val dataSize = totalSamples * channels * bytesPerSample

        FileOutputStream(outputFile).use { fos ->
            // RIFF WAV Header
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16) // Subchunk1Size
            header.putShort(1.toShort()) // AudioFormat = PCM
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(sampleRate * channels * bytesPerSample) // ByteRate
            header.putShort((channels * bytesPerSample).toShort()) // BlockAlign
            header.putShort((bytesPerSample * 8).toShort()) // BitsPerSample
            header.put("data".toByteArray())
            header.putInt(dataSize)
            fos.write(header.array())

            val bufferSize = 4096
            val byteBuffer = ByteBuffer.allocate(bufferSize * channels * bytesPerSample).order(ByteOrder.LITTLE_ENDIAN)

            var sampleCount = 0
            while (sampleCount < totalSamples) {
                byteBuffer.clear()
                val chunkSamples = min(bufferSize, totalSamples - sampleCount)

                for (s in 0 until chunkSamples) {
                    val t = (sampleCount + s).toDouble() / sampleRate.toDouble()
                    val beat = t * 2.0 // 120 BPM

                    // Bassline (40 Hz - 80 Hz)
                    val bassFreq = chordProgression[0] / 2.0
                    val bassEnv = (1.0 - (beat % 1.0)).coerceIn(0.0, 1.0).pow(2.0)
                    val bassSample = sin(2.0 * Math.PI * bassFreq * t) * bassEnv * 0.45

                    // Lush Harmonic Chord Pad
                    var padSample = 0.0
                    for (f in chordProgression) {
                        padSample += sin(2.0 * Math.PI * f.toDouble() * t) * 0.12
                        padSample += sin(2.0 * Math.PI * (f * 2.01).toDouble() * t) * 0.04
                    }

                    // Arpeggio Melody
                    val arpIndex = ((t * 4.0).toInt()) % chordProgression.size
                    val arpFreq = chordProgression[arpIndex] * 2.0
                    val arpEnv = (1.0 - ((t * 4.0) % 1.0)).coerceIn(0.0, 1.0).pow(3.0)
                    val arpSample = sin(2.0 * Math.PI * arpFreq * t) * arpEnv * 0.25

                    // Subtle stereo movement
                    val panL = 0.5 + 0.4 * sin(2.0 * Math.PI * 0.2 * t)
                    val panR = 1.0 - panL

                    val mixL = (bassSample * 0.8 + padSample * panL + arpSample * 0.8)
                    val mixR = (bassSample * 0.8 + padSample * panR + arpSample * 0.5)

                    val shortL = (mixL.coerceIn(-1.0, 1.0) * 30000.0).toInt().toShort()
                    val shortR = (mixR.coerceIn(-1.0, 1.0) * 30000.0).toInt().toShort()

                    byteBuffer.putShort(shortL)
                    byteBuffer.putShort(shortR)
                }

                sampleCount += chunkSamples
                fos.write(byteBuffer.array(), 0, chunkSamples * channels * bytesPerSample)
            }
        }
    }
}
