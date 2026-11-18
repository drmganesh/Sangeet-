package com.example.sangeet.playback.crossfade

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.example.sangeet.data.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * 100% offline local audio analyzer for beat, BPM, intro entry, and outro phrase detection.
 *
 * Uses stratified short-window sampling (intro + steady mid section + outro) to keep CPU and
 * memory tiny even on lower-end Android 8.0+ devices, reading local WAV directly or decoding
 * MP3/AAC/FLAC/OGG streams cleanly via MediaExtractor + MediaCodec.
 */
class LocalAudioBeatAnalyzer(private val context: Context) {

    private data class EnvelopeWindow(
        val startOffsetMs: Long,
        val hopMs: Float,
        val rmsEnvelope: FloatArray,
        val lowBandEnvelope: FloatArray
    )

    private val cache = ConcurrentHashMap<String, BeatAnalysisResult>()

    fun getCached(track: AudioTrack): BeatAnalysisResult? = cache[cacheKey(track)]

    suspend fun analyzeTrack(track: AudioTrack): BeatAnalysisResult = withContext(Dispatchers.IO) {
        val key = cacheKey(track)
        cache[key]?.let { return@withContext it }

        if (track.durationMs > 0L && track.durationMs < 5_000L) {
            return@withContext BeatAnalysisResult(
                trackId = track.id,
                isReliable = false,
                statusReason = "Track too short for beat grid"
            ).also { cache[key] = it }
        }

        val result = runCatching {
            val wavFile = resolveReadableFile(track)
            if (wavFile != null && wavFile.extension.equals("wav", ignoreCase = true)) {
                analyzeWavFile(track, wavFile)
            } else {
                analyzeCompressedTrack(track)
            }
        }.getOrElse { error ->
            Log.d(TAG, "Beat analysis unavailable for ${track.title}: ${error.message}")
            BeatAnalysisResult(
                trackId = track.id,
                isReliable = false,
                statusReason = "Audio stream not decodable offline"
            )
        }

        if (cache.size > MAX_CACHE_ENTRIES) {
            cache.clear()
        }
        cache[key] = result
        result
    }

    private fun cacheKey(track: AudioTrack): String {
        return "${track.id}_${track.durationMs}_${track.fileSize}"
    }

    private fun resolveReadableFile(track: AudioTrack): File? {
        if (track.path.isNotBlank()) {
            val f = File(track.path)
            if (f.exists() && f.canRead() && f.length() > 64L) return f
        }
        if (track.contentUri.startsWith("file://")) {
            val path = Uri.parse(track.contentUri).path
            if (!path.isNullOrBlank()) {
                val f = File(path)
                if (f.exists() && f.canRead() && f.length() > 64L) return f
            }
        }
        return null
    }

    private fun analyzeWavFile(track: AudioTrack, file: File): BeatAnalysisResult {
        FileInputStream(file).use { fis ->
            val header = ByteArray(44)
            if (fis.read(header) < 44) {
                return BeatAnalysisResult(track.id, isReliable = false, statusReason = "Invalid WAV header")
            }
            val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val riff = String(header, 0, 4)
            val wave = String(header, 8, 4)
            if (riff != "RIFF" || wave != "WAVE") {
                return analyzeCompressedTrack(track)
            }
            val audioFormat = bb.getShort(20).toInt()
            val channels = bb.getShort(22).toInt().coerceIn(1, 8)
            val sampleRate = bb.getInt(24).coerceIn(8000, 192000)
            val bitsPerSample = bb.getShort(34).toInt()
            if (audioFormat != 1 || bitsPerSample != 16) {
                return analyzeCompressedTrack(track)
            }

            val bytesPerFrame = channels * 2
            val dataOffset = 44L
            val totalDataBytes = (file.length() - dataOffset).coerceAtLeast(0L)
            val totalDurationMs = if (track.durationMs > 0L) track.durationMs else {
                (totalDataBytes * 1000L) / (sampleRate.toLong() * bytesPerFrame)
            }

            val introWindow = readWavWindow(file, dataOffset, totalDataBytes, sampleRate, channels, startMs = 0L, windowMs = 20_000L)
            val midStartMs = (totalDurationMs * 0.30).roundToLong().coerceAtLeast(0L)
            val midWindow = if (totalDurationMs > 25_000L) {
                readWavWindow(file, dataOffset, totalDataBytes, sampleRate, channels, startMs = midStartMs, windowMs = 22_000L)
            } else {
                introWindow
            }
            val outroStartMs = (totalDurationMs - 20_000L).coerceAtLeast(0L)
            val outroWindow = if (totalDurationMs > 28_000L) {
                readWavWindow(file, dataOffset, totalDataBytes, sampleRate, channels, startMs = outroStartMs, windowMs = 20_000L)
            } else {
                midWindow
            }

            return buildResultFromWindows(track.id, totalDurationMs, introWindow, midWindow, outroWindow)
        }
    }

    private fun readWavWindow(
        file: File,
        dataOffset: Long,
        totalDataBytes: Long,
        sampleRate: Int,
        channels: Int,
        startMs: Long,
        windowMs: Long
    ): EnvelopeWindow {
        val bytesPerFrame = channels * 2
        val hopSamples = max(1, (sampleRate * HOP_MS / 1000f).roundToInt())
        val startByte = ((startMs * sampleRate / 1000L) * bytesPerFrame).coerceIn(0L, totalDataBytes)
        val maxBytes = ((windowMs * sampleRate / 1000L) * bytesPerFrame).coerceAtMost(totalDataBytes - startByte)
        val hopCount = (maxBytes / (hopSamples * bytesPerFrame)).toInt().coerceAtLeast(0)

        val rms = FloatArray(hopCount)
        val low = FloatArray(hopCount)
        val chunkBytes = hopSamples * bytesPerFrame
        val rawBuf = ByteArray(chunkBytes)

        // 1-pole low-pass ~160 Hz for sub/kick transient emphasis
        val rc = 1.0 / (2.0 * PI * 160.0)
        val dt = 1.0 / sampleRate
        val lpAlpha = (dt / (rc + dt)).toFloat()
        var lpState = 0f

        FileInputStream(file).use { fis ->
            var skipped = 0L
            val targetSkip = dataOffset + startByte
            while (skipped < targetSkip) {
                val step = fis.skip(targetSkip - skipped)
                if (step <= 0L) break
                skipped += step
            }

            for (h in 0 until hopCount) {
                var readTotal = 0
                while (readTotal < chunkBytes) {
                    val r = fis.read(rawBuf, readTotal, chunkBytes - readTotal)
                    if (r <= 0) break
                    readTotal += r
                }
                if (readTotal < bytesPerFrame) break

                val bb = ByteBuffer.wrap(rawBuf, 0, readTotal).order(ByteOrder.LITTLE_ENDIAN)
                val frames = readTotal / bytesPerFrame
                var sumSq = 0.0
                var lowSumSq = 0.0
                for (f in 0 until frames) {
                    var mono = 0f
                    for (c in 0 until channels) {
                        mono += bb.short.toFloat() / 32768f
                    }
                    mono /= channels.toFloat()
                    lpState += lpAlpha * (mono - lpState)
                    sumSq += (mono * mono).toDouble()
                    lowSumSq += (lpState * lpState).toDouble()
                }
                rms[h] = sqrt(sumSq / max(1, frames)).toFloat()
                low[h] = sqrt(lowSumSq / max(1, frames)).toFloat()
            }
        }

        return EnvelopeWindow(startOffsetMs = startMs, hopMs = HOP_MS, rmsEnvelope = rms, lowBandEnvelope = low)
    }

    private fun analyzeCompressedTrack(track: AudioTrack): BeatAnalysisResult {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            val uri = track.contentUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
            val readableFile = resolveReadableFile(track)
            if (readableFile != null) {
                extractor.setDataSource(readableFile.absolutePath)
            } else if (uri != null) {
                extractor.setDataSource(context, uri, null)
            } else {
                return BeatAnalysisResult(track.id, isReliable = false, statusReason = "No readable audio source")
            }

            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = fmt
                    break
                }
            }
            if (audioTrackIndex == -1 || format == null) {
                return BeatAnalysisResult(track.id, isReliable = false, statusReason = "No audio track found")
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return BeatAnalysisResult(
                track.id, isReliable = false, statusReason = "Unknown audio MIME type"
            )
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                track.durationMs * 1000L
            }
            val durationMs = max(track.durationMs, durationUs / 1000L)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val introWindow = decodeExtractorWindow(extractor, codec, format, seekUs = 0L, decodeDurationMs = 18_000L)
            val midSeekMs = if (durationMs > 28_000L) (durationMs * 0.30).roundToLong() else 0L
            val midWindow = if (midSeekMs > 0L) {
                decodeExtractorWindow(extractor, codec, format, seekUs = midSeekMs * 1000L, decodeDurationMs = 18_000L)
            } else {
                introWindow
            }
            val outroSeekMs = if (durationMs > 30_000L) (durationMs - 18_000L).coerceAtLeast(0L) else 0L
            val outroWindow = if (outroSeekMs > midSeekMs + 4_000L) {
                decodeExtractorWindow(extractor, codec, format, seekUs = outroSeekMs * 1000L, decodeDurationMs = 18_000L)
            } else {
                midWindow
            }

            return buildResultFromWindows(track.id, durationMs, introWindow, midWindow, outroWindow)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun decodeExtractorWindow(
        extractor: MediaExtractor,
        codec: MediaCodec,
        initialFormat: MediaFormat,
        seekUs: Long,
        decodeDurationMs: Long
    ): EnvelopeWindow {
        codec.flush()
        extractor.seekTo(seekUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        var sampleRate = if (initialFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            initialFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceIn(8000, 192000)
        } else 44100
        var channels = if (initialFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            initialFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
        } else 2

        val rmsList = ArrayList<Float>((decodeDurationMs / HOP_MS).toInt() + 16)
        val lowList = ArrayList<Float>((decodeDurationMs / HOP_MS).toInt() + 16)

        val hopTargetSamples = max(1, (sampleRate * HOP_MS / 1000f).roundToInt())
        var hopAccumSamples = 0
        var sumSq = 0.0
        var lowSumSq = 0.0
        var lpState = 0f

        val rc = 1.0 / (2.0 * PI * 160.0)
        val dt = 1.0 / sampleRate
        val lpAlpha = (dt / (rc + dt)).toFloat()

        val info = MediaCodec.BufferInfo()
        val endLimitUs = seekUs + decodeDurationMs * 1000L
        var sawInputEos = false
        var sawOutputEos = false
        var guardIterations = 0

        while (!sawOutputEos && guardIterations < 1600) {
            guardIterations++
            if (!sawInputEos) {
                val inIdx = codec.dequeueInputBuffer(4000L)
                if (inIdx >= 0) {
                    val inBuf = codec.getInputBuffer(inIdx)
                    if (inBuf != null) {
                        val size = extractor.readSampleData(inBuf, 0)
                        val pts = extractor.sampleTime
                        if (size < 0 || pts > endLimitUs) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, pts, 0)
                            extractor.advance()
                        }
                    }
                }
            }

            val outIdx = codec.dequeueOutputBuffer(info, 4000L)
            if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFmt = codec.outputFormat
                if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    sampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceIn(8000, 192000)
                }
                if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    channels = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
                }
            } else if (outIdx >= 0) {
                val outBuf = codec.getOutputBuffer(outIdx)
                if (outBuf != null && info.size > 0) {
                    outBuf.position(info.offset)
                    outBuf.limit(info.offset + info.size)
                    val bb = outBuf.slice().order(ByteOrder.LITTLE_ENDIAN)
                    val totalShorts = info.size / 2
                    var idx = 0
                    while (idx + channels <= totalShorts) {
                        var mono = 0f
                        for (c in 0 until channels) {
                            mono += bb.short.toFloat() / 32768f
                        }
                        idx += channels
                        mono /= channels.toFloat()
                        lpState += lpAlpha * (mono - lpState)
                        sumSq += (mono * mono).toDouble()
                        lowSumSq += (lpState * lpState).toDouble()
                        hopAccumSamples++
                        if (hopAccumSamples >= hopTargetSamples) {
                            rmsList.add(sqrt(sumSq / hopAccumSamples).toFloat())
                            lowList.add(sqrt(lowSumSq / hopAccumSamples).toFloat())
                            hopAccumSamples = 0
                            sumSq = 0.0
                            lowSumSq = 0.0
                        }
                    }
                }
                codec.releaseOutputBuffer(outIdx, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEos = true
                }
            }
        }

        return EnvelopeWindow(
            startOffsetMs = seekUs / 1000L,
            hopMs = HOP_MS,
            rmsEnvelope = rmsList.toFloatArray(),
            lowBandEnvelope = lowList.toFloatArray()
        )
    }

    private fun buildResultFromWindows(
        trackId: Long,
        totalDurationMs: Long,
        intro: EnvelopeWindow,
        mid: EnvelopeWindow,
        outro: EnvelopeWindow
    ): BeatAnalysisResult {
        val midAnalysis = analyzeEnergyEnvelope(
            trackId = trackId,
            rmsEnvelope = mid.rmsEnvelope,
            lowBandEnvelope = mid.lowBandEnvelope,
            hopMs = mid.hopMs,
            windowOffsetMs = mid.startOffsetMs,
            totalDurationMs = totalDurationMs
        )

        val introEntryMs = detectIntroMusicalEntryMs(intro)
        val outroPhraseStartMs = detectOutroPhraseBoundaryMs(outro, midAnalysis.beatIntervalMs, totalDurationMs)

        return midAnalysis.copy(
            introEntryMs = introEntryMs,
            outroPhraseStartMs = outroPhraseStartMs
        )
    }

    private fun detectIntroMusicalEntryMs(intro: EnvelopeWindow): Long {
        val rms = intro.rmsEnvelope
        if (rms.size < 10) return 0L
        var peak = 0f
        for (v in rms) peak = max(peak, v)
        if (peak < 0.005f) return 0L

        val threshold = max(0.006f, peak * 0.14f)
        for (i in rms.indices) {
            if (rms[i] >= threshold) {
                // Step back by 1 hop so we don't chop the transient attack
                val entryMs = (max(0, i - 1) * intro.hopMs).roundToLong()
                return if (entryMs in 120L..5000L) entryMs else 0L
            }
        }
        return 0L
    }

    private fun detectOutroPhraseBoundaryMs(
        outro: EnvelopeWindow,
        beatIntervalMs: Long,
        totalDurationMs: Long
    ): Long {
        val rms = outro.rmsEnvelope
        if (rms.size < 20 || beatIntervalMs <= 0L) {
            return (totalDurationMs - 10_000L).coerceAtLeast(1000L)
        }
        var peak = 0f
        for (v in rms) peak = max(peak, v)
        if (peak < 0.005f) {
            return (totalDurationMs - 10_000L).coerceAtLeast(1000L)
        }
        // Locate last non-silent audible frame before trailing tail drops below 10%
        val tailThreshold = max(0.005f, peak * 0.10f)
        var lastAudibleIdx = rms.lastIndex
        while (lastAudibleIdx > 0 && rms[lastAudibleIdx] < tailThreshold) {
            lastAudibleIdx--
        }
        val activeEndMs = outro.startOffsetMs + (lastAudibleIdx * outro.hopMs).roundToLong()
        val beatsPerPhrase = 8L
        val targetPhraseMs = activeEndMs - beatsPerPhrase * beatIntervalMs
        return targetPhraseMs.coerceIn(1000L, (totalDurationMs - 2000L).coerceAtLeast(1000L))
    }

    companion object {
        private const val TAG = "LocalAudioBeatAnalyzer"
        private const val MAX_CACHE_ENTRIES = 128
        const val HOP_MS = 10f
        const val RELIABLE_CONFIDENCE_THRESHOLD = 0.45f

        /**
         * Pure DSP onset-flux autocorrelation + octave disambiguation + beat phase estimator.
         * Usable directly in JVM unit tests as well as real on-device PCM windows.
         */
        fun analyzeEnergyEnvelope(
            trackId: Long,
            rmsEnvelope: FloatArray,
            lowBandEnvelope: FloatArray,
            hopMs: Float = HOP_MS,
            windowOffsetMs: Long = 0L,
            totalDurationMs: Long = 30_000L
        ): BeatAnalysisResult {
            if (rmsEnvelope.size < 200 || lowBandEnvelope.size != rmsEnvelope.size) {
                return BeatAnalysisResult(
                    trackId = trackId,
                    isReliable = false,
                    statusReason = "Insufficient audio frames"
                )
            }

            // Verify signal has actual musical dynamic energy
            var maxEnergy = 0f
            var meanEnergy = 0.0
            for (v in rmsEnvelope) {
                maxEnergy = max(maxEnergy, v)
                meanEnergy += v.toDouble()
            }
            meanEnergy /= rmsEnvelope.size
            if (maxEnergy < 0.01f) {
                return BeatAnalysisResult(
                    trackId = trackId,
                    isReliable = false,
                    statusReason = "Audio level too quiet for beat detection"
                )
            }

            // Weighted onset strength emphasizing kick/sub-bass transients (70% low-band, 30% full-band)
            val onset = FloatArray(rmsEnvelope.size)
            for (i in 1 until rmsEnvelope.size) {
                val dLow = max(0f, lowBandEnvelope[i] - lowBandEnvelope[i - 1])
                val dFull = max(0f, rmsEnvelope[i] - rmsEnvelope[i - 1])
                onset[i] = dLow * 0.70f + dFull * 0.30f
            }

            // Adaptive local mean subtraction to sharpen percussive transients
            val halfWin = 6
            val novelty = FloatArray(onset.size)
            var noveltySum = 0.0
            var noveltySqSum = 0.0
            for (i in onset.indices) {
                val lo = max(0, i - halfWin)
                val hi = min(onset.lastIndex, i + halfWin)
                var localMean = 0f
                for (j in lo..hi) localMean += onset[j]
                localMean /= (hi - lo + 1).toFloat()
                val n = max(0f, onset[i] - localMean * 1.15f)
                novelty[i] = n
                noveltySum += n
                noveltySqSum += n * n
            }

            val noveltyMean = noveltySum / novelty.size
            val noveltyStd = sqrt(max(0.0, noveltySqSum / novelty.size - noveltyMean * noveltyMean))
            if (noveltyStd < 1e-4) {
                return BeatAnalysisResult(
                    trackId = trackId,
                    confidence = 0.1f,
                    isReliable = false,
                    statusReason = "Low percussive contrast (ambient/steady tone)"
                )
            }

            // Autocorrelation over candidate BPMs [60..180]
            val minBpm = 60
            val maxBpm = 180
            val scores = FloatArray(maxBpm - minBpm + 1)
            for (bpm in minBpm..maxBpm) {
                val lagMs = 60_000.0 / bpm
                val lagHops = (lagMs / hopMs).roundToInt().coerceAtLeast(1)
                if (lagHops >= novelty.size / 2) continue

                var corr = 0.0
                var count = 0
                for (i in 0 until novelty.size - lagHops) {
                    corr += novelty[i].toDouble() * novelty[i + lagHops].toDouble()
                    count++
                }
                val baseScore = if (count > 0) corr / count else 0.0

                // Reinforce octave multiples (2x lag = half-tempo bar coherence)
                val doubleLag = lagHops * 2
                var doubleScore = 0.0
                if (doubleLag < novelty.size - 10) {
                    var dc = 0.0
                    var dn = 0
                    for (i in 0 until novelty.size - doubleLag) {
                        dc += novelty[i].toDouble() * novelty[i + doubleLag].toDouble()
                        dn++
                    }
                    if (dn > 0) doubleScore = dc / dn
                }

                // Subtle perceptual weighting favoring classic 85..150 BPM groove center
                val tempoBias = 1.0 - 0.12 * abs(bpm - 118) / 60.0
                scores[bpm - minBpm] = ((baseScore * 0.72 + doubleScore * 0.28) * tempoBias).toFloat()
            }

            var bestIdx = 0
            var bestScore = Float.NEGATIVE_INFINITY
            val sortedScores = scores.sorted()
            val medianScore = sortedScores[sortedScores.size / 2].coerceAtLeast(1e-7f)

            for (i in scores.indices) {
                if (scores[i] > bestScore) {
                    bestScore = scores[i]
                    bestIdx = i
                }
            }

            // Estimate periodicity confidence via peak-to-median prominence normalized to [0..1]
            val prominenceRatio = bestScore / medianScore
            val confidence = ((prominenceRatio - 1.35f) / 3.2f).coerceIn(0f, 1f)
            val estimatedBpm = (minBpm + bestIdx).toFloat()
            val beatIntervalMs = (60_000.0 / estimatedBpm).roundToLong()

            if (confidence < RELIABLE_CONFIDENCE_THRESHOLD || bestScore <= 1e-7f) {
                return BeatAnalysisResult(
                    trackId = trackId,
                    bpm = null,
                    confidence = confidence,
                    isReliable = false,
                    statusReason = "Low beat confidence (${(confidence * 100).roundToInt()}%) — using time-based fade"
                )
            }

            // Phase-align downbeat within the window using modulo beat-period phase histogram
            val periodHops = max(1, (beatIntervalMs.toFloat() / hopMs).roundToInt())
            var bestPhaseHop = 0
            var bestPhaseEnergy = -1.0
            for (phase in 0 until periodHops) {
                var sum = 0.0
                var idx = phase
                while (idx < novelty.size) {
                    sum += novelty[idx]
                    idx += periodHops
                }
                if (sum > bestPhaseEnergy) {
                    bestPhaseEnergy = sum
                    bestPhaseHop = phase
                }
            }

            val phaseMs = (bestPhaseHop * hopMs).roundToLong()
            val globalDownbeat = (windowOffsetMs + phaseMs) % beatIntervalMs
            val defaultOutroPhrase = (totalDurationMs - (beatIntervalMs * 8L)).coerceAtLeast(1000L)

            return BeatAnalysisResult(
                trackId = trackId,
                bpm = estimatedBpm,
                confidence = confidence,
                isReliable = true,
                beatIntervalMs = beatIntervalMs,
                firstDownbeatMs = globalDownbeat,
                outroPhraseStartMs = defaultOutroPhrase,
                introEntryMs = 0L,
                statusReason = "Beat Grid Locked (${estimatedBpm.toInt()} BPM)"
            )
        }
    }
}
