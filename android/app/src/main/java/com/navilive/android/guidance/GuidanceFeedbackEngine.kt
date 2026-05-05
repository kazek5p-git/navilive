package com.navilive.android.guidance

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.media.AudioFormat
import android.media.AudioAttributes
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import com.navilive.android.model.SpeechOutputMode
import com.navilive.android.model.SoundCueTheme
import com.navilive.android.model.SystemTtsEngineOption
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

data class SpeechRuntimeStatus(
    val isScreenReaderActive: Boolean,
    val activeScreenReaderName: String? = null,
    val availableSystemTtsEngines: List<SystemTtsEngineOption> = emptyList(),
    val defaultSystemTtsEngineLabel: String? = null,
    val activeSystemTtsEngineLabel: String? = null,
)

enum class NavigationSoundCue {
    Countdown,
    TurnNow,
    PedestrianCrossing,
    Warning,
    Success,
    Arrival,
}

class GuidanceFeedbackEngine(context: Context) {
    private val appContext = context.applicationContext
    private val accessibilityManager =
        appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager

    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var speechOutputMode = SpeechOutputMode.System
    private var preferredSystemTtsEnginePackage: String? = null
    private var speechRate = 1.0f
    private var speechVolume = 1.0f
    private var availableSystemTtsEngines: List<SystemTtsEngineOption> = emptyList()
    private var defaultSystemTtsEngineLabel: String? = null
    private var activeSystemTtsEngineLabel: String? = null
    private val soundExecutor = Executors.newSingleThreadExecutor()
    private val soundQueueLock = Any()
    private var soundQueueAvailableAtMs = 0L

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    init {
        recreateTextToSpeech()
    }

    fun updateSpeechPreferences(
        outputMode: SpeechOutputMode,
        systemTtsEnginePackage: String?,
        ratePercent: Int,
        volumePercent: Int,
    ) {
        speechOutputMode = outputMode
        val normalizedEnginePackage = systemTtsEnginePackage?.takeIf { it.isNotBlank() }
        val engineChanged = normalizedEnginePackage != preferredSystemTtsEnginePackage
        preferredSystemTtsEnginePackage = normalizedEnginePackage
        speechRate = (ratePercent.coerceIn(50, 200) / 100f)
        speechVolume = (volumePercent.coerceIn(0, 100) / 100f)

        if (engineChanged || textToSpeech == null) {
            recreateTextToSpeech()
        } else {
            ensureTextToSpeech()
            configureTextToSpeech()
            refreshEngineMetadata()
        }
    }

    fun snapshotSpeechRuntimeStatus(): SpeechRuntimeStatus {
        refreshEngineMetadata()
        val services = activeSpokenFeedbackServices()
        val label = services.firstOrNull()
            ?.resolveInfo
            ?.loadLabel(appContext.packageManager)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
        return SpeechRuntimeStatus(
            isScreenReaderActive = services.isNotEmpty(),
            activeScreenReaderName = label,
            availableSystemTtsEngines = availableSystemTtsEngines,
            defaultSystemTtsEngineLabel = defaultSystemTtsEngineLabel,
            activeSystemTtsEngineLabel = activeSystemTtsEngineLabel,
        )
    }

    fun speak(text: String, flush: Boolean = true) {
        if (text.isBlank()) return
        if (speechOutputMode == SpeechOutputMode.ScreenReader && announceThroughScreenReader(text)) {
            return
        }
        speakThroughSystemTts(text, flush)
    }

    fun speakNavigation(text: String, flush: Boolean = true) {
        if (text.isBlank()) return
        if (speechOutputMode == SpeechOutputMode.ScreenReader && announceThroughScreenReader(text)) {
            return
        }
        if (speakThroughSystemTts(text, flush)) {
            return
        }
        if (speechOutputMode == SpeechOutputMode.ScreenReader) {
            announceThroughScreenReader(text)
        }
    }

    fun vibrateShort() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        vib.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun vibrateDouble() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val timings = longArrayOf(0, 90, 70, 90)
        vib.vibrate(VibrationEffect.createWaveform(timings, -1))
    }

    fun playSoundCue(
        cue: NavigationSoundCue,
        volumePercent: Int = DefaultSoundCueVolumePercent,
        theme: SoundCueTheme = SoundCueTheme.Standard,
    ): Long {
        val sequence = cue.toneSequence(theme)
        if (sequence.isEmpty()) return 0L
        val startDelayMs = reserveSoundCueStartDelay(sequence.soundCueDurationMillis())
        soundExecutor.execute {
            playToneSequence(sequence, volumePercent)
        }
        return startDelayMs
    }

    fun shutdown() {
        shutdownTextToSpeech()
        soundExecutor.shutdownNow()
    }

    private fun shutdownTextToSpeech() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        ttsReady = false
    }

    private fun recreateTextToSpeech() {
        shutdownTextToSpeech()
        ensureTextToSpeech()
    }

    private fun ensureTextToSpeech() {
        if (textToSpeech != null) return
        val requestedEnginePackage = preferredSystemTtsEnginePackage
        textToSpeech = if (requestedEnginePackage == null) {
            TextToSpeech(appContext) { status ->
                handleTextToSpeechInit(status, requestedEnginePackage)
            }
        } else {
            TextToSpeech(appContext, { status ->
                handleTextToSpeechInit(status, requestedEnginePackage)
            }, requestedEnginePackage)
        }
    }

    private fun handleTextToSpeechInit(status: Int, requestedEnginePackage: String?) {
        ttsReady = status == TextToSpeech.SUCCESS
        refreshEngineMetadata(requestedEnginePackage)
        if (ttsReady) {
            configureTextToSpeech()
            return
        }
        if (requestedEnginePackage != null) {
            preferredSystemTtsEnginePackage = requestedEnginePackage
            textToSpeech?.shutdown()
            textToSpeech = null
            TextToSpeech(appContext) { fallbackStatus ->
                handleTextToSpeechInit(fallbackStatus, null)
            }.also {
                textToSpeech = it
            }
        }
    }

    private fun configureTextToSpeech() {
        val tts = textToSpeech ?: return
        if (!ttsReady) return
        tts.language = Locale.getDefault()
        tts.setSpeechRate(speechRate)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
        }
    }

    private fun refreshEngineMetadata(requestedEnginePackage: String? = preferredSystemTtsEnginePackage) {
        val tts = textToSpeech ?: return
        val options = tts.engines
            .map { engine ->
                SystemTtsEngineOption(
                    packageName = engine.name,
                    displayName = engine.label.takeIf { it.isNotBlank() } ?: engine.name,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.displayName.lowercase(Locale.getDefault()) }
        availableSystemTtsEngines = options

        val defaultPackage = tts.defaultEngine?.takeIf { it.isNotBlank() }
        defaultSystemTtsEngineLabel = resolveEngineLabel(defaultPackage, options)

        val activePackage = requestedEnginePackage?.takeIf { pkg ->
            options.any { it.packageName == pkg }
        } ?: defaultPackage
        activeSystemTtsEngineLabel = resolveEngineLabel(activePackage, options) ?: defaultSystemTtsEngineLabel
    }

    private fun resolveEngineLabel(
        packageName: String?,
        options: List<SystemTtsEngineOption> = availableSystemTtsEngines,
    ): String? {
        if (packageName.isNullOrBlank()) return null
        return options.firstOrNull { it.packageName == packageName }?.displayName
    }

    private fun speakThroughSystemTts(text: String, flush: Boolean): Boolean {
        ensureTextToSpeech()
        val tts = textToSpeech ?: return false
        if (!ttsReady) return false
        configureTextToSpeech()
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, speechVolume)
        }
        return tts.speak(text, queueMode, params, UUID.randomUUID().toString()) != TextToSpeech.ERROR
    }

    private fun playToneSequence(sequence: List<ToneSpec>, volumePercent: Int) {
        if (sequence.isEmpty()) return
        val pcm = generatePcm(sequence, volumePercent)
        if (pcm.isEmpty()) return
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SoundSampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(pcm.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        runCatching {
            track.write(pcm, 0, pcm.size)
            track.play()
            Thread.sleep(pcmDurationMillis(pcm) + SoundCueQueueGapMs)
        }
        track.release()
    }

    private fun reserveSoundCueStartDelay(durationMs: Long): Long {
        return synchronized(soundQueueLock) {
            val now = SystemClock.uptimeMillis()
            val startAt = maxOf(now, soundQueueAvailableAtMs)
            soundQueueAvailableAtMs = startAt + durationMs + SoundCueQueueGapMs
            startAt - now
        }
    }

    private fun List<ToneSpec>.soundCueDurationMillis(): Long {
        return sumOf { scaledSoundCueMillis(it.durationMs) + scaledSoundCueMillis(it.gapAfterMs) }.toLong()
    }

    private fun pcmDurationMillis(pcm: ByteArray): Long {
        val sampleCount = pcm.size / 2
        return ((sampleCount * 1000L) / SoundSampleRate).coerceAtLeast(1L)
    }

    private fun generatePcm(sequence: List<ToneSpec>, volumePercent: Int): ByteArray {
        val samples = mutableListOf<Short>()
        val cueVolume = volumePercent.coerceIn(0, 100) / 100.0
        var phase = 0.0
        var shimmerPhase = 0.0
        var bassPhase = 0.0
        var fmPhase = 0.0
        var ringPhase = 0.0
        sequence.forEach { spec ->
            val toneSamples = SoundSampleRate * scaledSoundCueMillis(spec.durationMs) / 1000
            for (sampleIndex in 0 until toneSamples) {
                val progress = sampleIndex.toDouble() / toneSamples.coerceAtLeast(1)
                val envelope = when {
                    progress < 0.12 -> progress / 0.12
                    progress > 0.82 -> (1.0 - progress) / 0.18
                    else -> 1.0
                }.coerceIn(0.0, 1.0)
                val frequency = spec.frequencyAt(progress)
                val modulatedFrequency = if (spec.fmDepth > 0.0) {
                    fmPhase = (fmPhase + spec.fmFrequencyHz / SoundSampleRate) % 1.0
                    frequency * (1.0 + (sineWave(fmPhase) * spec.fmDepth))
                } else {
                    frequency
                }.coerceAtLeast(1.0)
                phase = (phase + modulatedFrequency / SoundSampleRate) % 1.0
                val fundamental = waveValue(spec.waveShape, phase, spec.dutyCycle)
                val third = waveValue(
                    spec.waveShape,
                    (phase * MajorThirdRatio) % 1.0,
                    spec.dutyCycle,
                ) * spec.majorThirdGain
                val leadValue = ((fundamental + third) / (1.0 + spec.majorThirdGain)).coerceIn(-1.0, 1.0) * spec.amplitude
                val shimmerValue = if (spec.shimmerGain > 0.0) {
                    shimmerPhase = (shimmerPhase + (modulatedFrequency * 2.0) / SoundSampleRate) % 1.0
                    sineWave(shimmerPhase) * spec.shimmerGain * (1.0 - progress).coerceIn(0.0, 1.0).pow(1.4)
                } else {
                    0.0
                }
                val transientValue = if (spec.transientGain > 0.0) {
                    spec.transientGain * exp(-progress * 38.0) *
                        sineWave((spec.transientFrequencyHz * sampleIndex / SoundSampleRate) % 1.0)
                } else {
                    0.0
                }
                val bassValue = spec.bassFrequencyHz?.let { bassFrequency ->
                    bassPhase = (bassPhase + bassFrequency / SoundSampleRate) % 1.0
                    sineWave(bassPhase) * spec.bassAmplitude
                } ?: 0.0
                val mixedValue = leadValue + shimmerValue + transientValue + bassValue
                val ringedValue = spec.ringModFrequencyHz?.let { ringFrequency ->
                    ringPhase = (ringPhase + ringFrequency / SoundSampleRate) % 1.0
                    val ring = (1.0 - spec.ringModDepth) + (sineWave(ringPhase) * spec.ringModDepth)
                    mixedValue * ring
                } ?: mixedValue
                val pluckEnvelope = (envelope * (1.0 - (spec.decayAmount * progress)).coerceIn(0.16, 1.0))
                    .coerceIn(0.0, 1.0)
                val rawSample = (ringedValue *
                    pluckEnvelope * cueVolume * SoundCueOutputGain).coerceIn(-1.0, 1.0)
                val normalizedSample = spec.quantizeSteps?.let { steps ->
                    ((rawSample * steps).roundToInt() / steps.toDouble()).coerceIn(-1.0, 1.0)
                } ?: rawSample
                samples += (normalizedSample * Short.MAX_VALUE).toInt().toShort()
            }
            repeat(SoundSampleRate * scaledSoundCueMillis(spec.gapAfterMs) / 1000) {
                samples += 0
            }
        }
        val bytes = ByteArray(samples.size * 2)
        samples.forEachIndexed { index, sample ->
            val value = sample.toInt()
            bytes[index * 2] = (value and 0xFF).toByte()
            bytes[index * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun waveValue(shape: WaveShape, phase: Double, dutyCycle: Double = 0.32): Double {
        return when (shape) {
            WaveShape.Sine -> sineWave(phase)
            WaveShape.Triangle -> triangleWave(phase)
            WaveShape.SoftSquare -> softSquareWave(phase)
            WaveShape.Pulse -> pulseWave(phase, dutyCycle)
            WaveShape.Saw -> sawWave(phase)
        }
    }

    private fun sineWave(phase: Double): Double {
        return sin(2.0 * PI * phase)
    }

    private fun triangleWave(phase: Double): Double {
        return 4.0 * abs(phase - 0.5) - 1.0
    }

    private fun softSquareWave(phase: Double): Double {
        val angle = 2.0 * PI * phase
        return ((sin(angle) + sin(angle * 3.0) / 3.0 + sin(angle * 5.0) / 5.0) / 1.53)
            .coerceIn(-1.0, 1.0)
    }

    private fun pulseWave(phase: Double, dutyCycle: Double): Double {
        return if (phase < dutyCycle.coerceIn(0.08, 0.75)) 1.0 else -0.72
    }

    private fun sawWave(phase: Double): Double {
        return (2.0 * phase) - 1.0
    }

    private fun scaledSoundCueMillis(durationMs: Int): Int {
        return (durationMs * SoundCueDurationScale).roundToInt()
    }

    @Suppress("DEPRECATION")
    private fun announceThroughScreenReader(text: String): Boolean {
        val manager = accessibilityManager ?: return false
        if (!manager.isEnabled || activeSpokenFeedbackServices().isEmpty()) return false
        return runCatching {
            val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT).apply {
                packageName = appContext.packageName
                className = GuidanceFeedbackEngine::class.java.name
                eventTime = System.currentTimeMillis()
                this.text.add(text)
                contentDescription = text
            }
            manager.sendAccessibilityEvent(event)
        }.isSuccess
    }

    private fun activeSpokenFeedbackServices(): List<AccessibilityServiceInfo> {
        val manager = accessibilityManager ?: return emptyList()
        if (!manager.isEnabled) return emptyList()
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN)
    }

    private data class ToneSpec(
        val frequencyHz: Double,
        val durationMs: Int,
        val gapAfterMs: Int = 0,
        val amplitude: Double = 0.32,
        val waveShape: WaveShape = WaveShape.Triangle,
        val majorThirdGain: Double = MajorThirdGain,
        val endFrequencyHz: Double? = null,
        val midFrequencyHz: Double? = null,
        val dutyCycle: Double = 0.32,
        val decayAmount: Double = 0.0,
        val quantizeSteps: Int? = null,
        val fmFrequencyHz: Double = 0.0,
        val fmDepth: Double = 0.0,
        val ringModFrequencyHz: Double? = null,
        val ringModDepth: Double = 0.0,
        val shimmerGain: Double = 0.0,
        val transientGain: Double = 0.0,
        val transientFrequencyHz: Double = 1900.0,
        val bassFrequencyHz: Double? = null,
        val bassAmplitude: Double = 0.0,
    )

    private fun ToneSpec.frequencyAt(progress: Double): Double {
        val end = endFrequencyHz ?: return frequencyHz
        val mid = midFrequencyHz
        if (mid != null) {
            return if (progress < 0.54) {
                val local = (progress / 0.54).coerceIn(0.0, 1.0)
                frequencyHz + ((mid - frequencyHz) * smoothStep(local))
            } else {
                val local = ((progress - 0.54) / 0.46).coerceIn(0.0, 1.0)
                mid + ((end - mid) * smoothStep(local))
            }
        }
        val smoothed = progress * progress * (3.0 - 2.0 * progress)
        return frequencyHz + ((end - frequencyHz) * smoothed)
    }

    private fun smoothStep(value: Double): Double {
        return value * value * (3.0 - 2.0 * value)
    }

    private enum class WaveShape {
        Sine,
        Triangle,
        SoftSquare,
        Pulse,
        Saw,
    }

    private fun NavigationSoundCue.toneSequence(theme: SoundCueTheme): List<ToneSpec> {
        return when (theme) {
            SoundCueTheme.Standard -> standardToneSequence()
            SoundCueTheme.Tetris -> tetrisToneSequence()
            SoundCueTheme.Cosmic -> cosmicToneSequence()
        }
    }

    private fun NavigationSoundCue.standardToneSequence(): List<ToneSpec> {
        return when (this) {
            NavigationSoundCue.Countdown -> listOf(
                ToneSpec(620.0, 58, gapAfterMs = 42, amplitude = 0.30, waveShape = WaveShape.Triangle, majorThirdGain = 0.22),
                ToneSpec(620.0, 58, amplitude = 0.30, waveShape = WaveShape.Triangle, majorThirdGain = 0.22),
            )
            NavigationSoundCue.TurnNow -> listOf(
                ToneSpec(1280.0, 55, gapAfterMs = 18, amplitude = 0.27, waveShape = WaveShape.SoftSquare, majorThirdGain = 0.06, transientGain = 0.16),
                ToneSpec(980.0, 180, gapAfterMs = 18, amplitude = 0.29, waveShape = WaveShape.SoftSquare, majorThirdGain = 0.18, endFrequencyHz = 540.0, shimmerGain = 0.02),
                ToneSpec(760.0, 90, amplitude = 0.21, waveShape = WaveShape.Triangle, majorThirdGain = 0.16),
            )
            NavigationSoundCue.PedestrianCrossing -> listOf(
                ToneSpec(760.0, 60, gapAfterMs = 24, amplitude = 0.30, waveShape = WaveShape.Pulse, majorThirdGain = 0.18),
                ToneSpec(960.0, 60, gapAfterMs = 24, amplitude = 0.30, waveShape = WaveShape.Pulse, majorThirdGain = 0.18),
                ToneSpec(760.0, 60, gapAfterMs = 24, amplitude = 0.30, waveShape = WaveShape.Pulse, majorThirdGain = 0.18),
                ToneSpec(960.0, 84, amplitude = 0.30, waveShape = WaveShape.Pulse, majorThirdGain = 0.18),
            )
            NavigationSoundCue.Warning -> listOf(
                ToneSpec(500.0, 88, gapAfterMs = 30, amplitude = 0.28, waveShape = WaveShape.Saw, majorThirdGain = 0.16),
                ToneSpec(370.0, 105, amplitude = 0.28, waveShape = WaveShape.Saw, majorThirdGain = 0.16),
            )
            NavigationSoundCue.Success -> listOf(
                ToneSpec(660.0, 78, gapAfterMs = 24, amplitude = 0.32, waveShape = WaveShape.Sine, majorThirdGain = 0.42),
                ToneSpec(880.0, 96, amplitude = 0.32, waveShape = WaveShape.Sine, majorThirdGain = 0.42),
            )
            NavigationSoundCue.Arrival -> listOf(
                ToneSpec(740.0, 78, gapAfterMs = 24, amplitude = 0.29, waveShape = WaveShape.Sine, majorThirdGain = 0.56, shimmerGain = 0.075),
                ToneSpec(930.0, 84, gapAfterMs = 24, amplitude = 0.29, waveShape = WaveShape.Sine, majorThirdGain = 0.58, shimmerGain = 0.080),
                ToneSpec(1175.0, 94, gapAfterMs = 26, amplitude = 0.29, waveShape = WaveShape.Sine, majorThirdGain = 0.60, shimmerGain = 0.085),
                ToneSpec(1480.0, 130, amplitude = 0.27, waveShape = WaveShape.Sine, majorThirdGain = 0.52, shimmerGain = 0.070),
            )
        }
    }

    private fun NavigationSoundCue.tetrisToneSequence(): List<ToneSpec> {
        return when (this) {
            NavigationSoundCue.Countdown -> listOf(
                ToneSpec(659.25, 46, gapAfterMs = 34, amplitude = 0.31, waveShape = WaveShape.Pulse, majorThirdGain = 0.0, dutyCycle = 0.125, decayAmount = 0.34, quantizeSteps = 14, transientGain = 0.10),
                ToneSpec(783.99, 46, amplitude = 0.31, waveShape = WaveShape.Pulse, majorThirdGain = 0.0, dutyCycle = 0.125, decayAmount = 0.34, quantizeSteps = 14, transientGain = 0.10),
            )
            NavigationSoundCue.TurnNow -> listOf(
                ToneSpec(1318.51, 48, gapAfterMs = 14, amplitude = 0.30, waveShape = WaveShape.Pulse, majorThirdGain = 0.0, dutyCycle = 0.25, decayAmount = 0.30, quantizeSteps = 14, transientGain = 0.08),
                ToneSpec(987.77, 58, gapAfterMs = 14, amplitude = 0.31, waveShape = WaveShape.Pulse, majorThirdGain = 0.0, dutyCycle = 0.125, decayAmount = 0.32, quantizeSteps = 14),
                ToneSpec(1046.50, 90, amplitude = 0.29, waveShape = WaveShape.Pulse, majorThirdGain = 0.0, dutyCycle = 0.25, decayAmount = 0.22, quantizeSteps = 14),
            )
            NavigationSoundCue.PedestrianCrossing -> listOf(
                ToneSpec(783.99, 42, gapAfterMs = 18, amplitude = 0.28, waveShape = WaveShape.Pulse, majorThirdGain = 0.0, dutyCycle = 0.50, decayAmount = 0.28, quantizeSteps = 12),
                ToneSpec(987.77, 42, gapAfterMs = 18, amplitude = 0.28, waveShape = WaveShape.Pulse, majorThirdGain = 0.0, dutyCycle = 0.125, decayAmount = 0.28, quantizeSteps = 12),
                ToneSpec(783.99, 42, gapAfterMs = 18, amplitude = 0.28, waveShape = WaveShape.Pulse, majorThirdGain = 0.0, dutyCycle = 0.50, decayAmount = 0.28, quantizeSteps = 12),
                ToneSpec(987.77, 64, amplitude = 0.28, waveShape = WaveShape.Pulse, majorThirdGain = 0.0, dutyCycle = 0.125, decayAmount = 0.22, quantizeSteps = 12),
            )
            NavigationSoundCue.Warning -> listOf(
                ToneSpec(493.88, 74, gapAfterMs = 26, amplitude = 0.30, waveShape = WaveShape.Pulse, majorThirdGain = 0.0, dutyCycle = 0.50, decayAmount = 0.36, quantizeSteps = 10),
                ToneSpec(440.0, 74, gapAfterMs = 26, amplitude = 0.30, waveShape = WaveShape.Pulse, majorThirdGain = 0.0, dutyCycle = 0.25, decayAmount = 0.36, quantizeSteps = 10),
                ToneSpec(392.0, 106, amplitude = 0.30, waveShape = WaveShape.Pulse, majorThirdGain = 0.0, dutyCycle = 0.125, decayAmount = 0.34, quantizeSteps = 10),
            )
            NavigationSoundCue.Success -> listOf(
                ToneSpec(440.0, 46, gapAfterMs = 20, amplitude = 0.29, waveShape = WaveShape.Pulse, majorThirdGain = 0.0, dutyCycle = 0.25, decayAmount = 0.30, quantizeSteps = 14),
                ToneSpec(523.25, 52, gapAfterMs = 20, amplitude = 0.29, waveShape = WaveShape.Pulse, majorThirdGain = 0.0, dutyCycle = 0.25, decayAmount = 0.28, quantizeSteps = 14),
                ToneSpec(659.25, 76, amplitude = 0.29, waveShape = WaveShape.Pulse, majorThirdGain = 0.0, dutyCycle = 0.125, decayAmount = 0.20, quantizeSteps = 14),
            )
            NavigationSoundCue.Arrival -> listOf(
                ToneSpec(659.25, 52, gapAfterMs = 16, amplitude = 0.28, waveShape = WaveShape.Pulse, majorThirdGain = 0.0, dutyCycle = 0.25, decayAmount = 0.30, quantizeSteps = 14),
                ToneSpec(493.88, 52, gapAfterMs = 16, amplitude = 0.28, waveShape = WaveShape.Pulse, majorThirdGain = 0.0, dutyCycle = 0.125, decayAmount = 0.30, quantizeSteps = 14),
                ToneSpec(523.25, 56, gapAfterMs = 16, amplitude = 0.28, waveShape = WaveShape.Pulse, majorThirdGain = 0.0, dutyCycle = 0.25, decayAmount = 0.28, quantizeSteps = 14),
                ToneSpec(587.33, 58, gapAfterMs = 18, amplitude = 0.28, waveShape = WaveShape.Pulse, majorThirdGain = 0.0, dutyCycle = 0.125, decayAmount = 0.26, quantizeSteps = 14),
                ToneSpec(659.25, 118, amplitude = 0.27, waveShape = WaveShape.Pulse, majorThirdGain = 0.0, dutyCycle = 0.25, decayAmount = 0.18, quantizeSteps = 14),
            )
        }
    }

    private fun NavigationSoundCue.cosmicToneSequence(): List<ToneSpec> {
        return when (this) {
            NavigationSoundCue.Countdown -> listOf(
                ToneSpec(1560.0, 84, gapAfterMs = 48, amplitude = 0.20, waveShape = WaveShape.Sine, majorThirdGain = 0.05, endFrequencyHz = 2180.0, decayAmount = 0.48, fmFrequencyHz = 185.0, fmDepth = 0.018, ringModFrequencyHz = 42.0, ringModDepth = 0.46, shimmerGain = 0.090),
                ToneSpec(2180.0, 96, amplitude = 0.19, waveShape = WaveShape.Sine, majorThirdGain = 0.04, endFrequencyHz = 1460.0, decayAmount = 0.42, fmFrequencyHz = 225.0, fmDepth = 0.015, ringModFrequencyHz = 57.0, ringModDepth = 0.40, shimmerGain = 0.075),
            )
            NavigationSoundCue.TurnNow -> listOf(
                ToneSpec(360.0, 88, gapAfterMs = 12, amplitude = 0.24, waveShape = WaveShape.Saw, majorThirdGain = 0.0, endFrequencyHz = 1280.0, decayAmount = 0.24, fmFrequencyHz = 96.0, fmDepth = 0.030, ringModFrequencyHz = 73.0, ringModDepth = 0.55, quantizeSteps = 30, transientGain = 0.06),
                ToneSpec(1280.0, 138, gapAfterMs = 16, amplitude = 0.25, waveShape = WaveShape.SoftSquare, majorThirdGain = 0.08, midFrequencyHz = 620.0, endFrequencyHz = 1720.0, decayAmount = 0.18, fmFrequencyHz = 143.0, fmDepth = 0.024, ringModFrequencyHz = 119.0, ringModDepth = 0.52, shimmerGain = 0.070),
                ToneSpec(940.0, 96, amplitude = 0.18, waveShape = WaveShape.Sine, majorThirdGain = 0.16, endFrequencyHz = 1240.0, decayAmount = 0.32, fmFrequencyHz = 260.0, fmDepth = 0.018, ringModFrequencyHz = 31.0, ringModDepth = 0.34, shimmerGain = 0.095),
            )
            NavigationSoundCue.PedestrianCrossing -> listOf(
                ToneSpec(760.0, 54, gapAfterMs = 24, amplitude = 0.21, waveShape = WaveShape.Triangle, majorThirdGain = 0.0, endFrequencyHz = 1460.0, decayAmount = 0.34, fmFrequencyHz = 210.0, fmDepth = 0.020, ringModFrequencyHz = 88.0, ringModDepth = 0.44, shimmerGain = 0.060),
                ToneSpec(1680.0, 54, gapAfterMs = 24, amplitude = 0.20, waveShape = WaveShape.Sine, majorThirdGain = 0.0, endFrequencyHz = 1040.0, decayAmount = 0.34, fmFrequencyHz = 250.0, fmDepth = 0.018, ringModFrequencyHz = 101.0, ringModDepth = 0.44, shimmerGain = 0.060),
                ToneSpec(760.0, 54, gapAfterMs = 24, amplitude = 0.21, waveShape = WaveShape.Triangle, majorThirdGain = 0.0, endFrequencyHz = 1460.0, decayAmount = 0.34, fmFrequencyHz = 210.0, fmDepth = 0.020, ringModFrequencyHz = 88.0, ringModDepth = 0.44, shimmerGain = 0.060),
                ToneSpec(1980.0, 96, amplitude = 0.18, waveShape = WaveShape.Sine, majorThirdGain = 0.10, endFrequencyHz = 1320.0, decayAmount = 0.24, fmFrequencyHz = 330.0, fmDepth = 0.014, ringModFrequencyHz = 63.0, ringModDepth = 0.36, shimmerGain = 0.095),
            )
            NavigationSoundCue.Warning -> listOf(
                ToneSpec(880.0, 100, gapAfterMs = 20, amplitude = 0.24, waveShape = WaveShape.Saw, majorThirdGain = 0.0, endFrequencyHz = 470.0, decayAmount = 0.34, fmFrequencyHz = 122.0, fmDepth = 0.020, ringModFrequencyHz = 137.0, ringModDepth = 0.62, quantizeSteps = 22),
                ToneSpec(540.0, 130, amplitude = 0.22, waveShape = WaveShape.Pulse, majorThirdGain = 0.0, dutyCycle = 0.18, endFrequencyHz = 260.0, decayAmount = 0.44, fmFrequencyHz = 74.0, fmDepth = 0.026, ringModFrequencyHz = 91.0, ringModDepth = 0.68, quantizeSteps = 18),
            )
            NavigationSoundCue.Success -> listOf(
                ToneSpec(520.0, 78, gapAfterMs = 20, amplitude = 0.21, waveShape = WaveShape.Triangle, majorThirdGain = 0.10, endFrequencyHz = 1140.0, decayAmount = 0.18, fmFrequencyHz = 160.0, fmDepth = 0.018, ringModFrequencyHz = 47.0, ringModDepth = 0.32, shimmerGain = 0.070),
                ToneSpec(1460.0, 102, amplitude = 0.19, waveShape = WaveShape.Sine, majorThirdGain = 0.18, endFrequencyHz = 2040.0, decayAmount = 0.20, fmFrequencyHz = 290.0, fmDepth = 0.012, ringModFrequencyHz = 23.0, ringModDepth = 0.28, shimmerGain = 0.110),
            )
            NavigationSoundCue.Arrival -> listOf(
                ToneSpec(420.0, 78, gapAfterMs = 20, amplitude = 0.20, waveShape = WaveShape.Triangle, majorThirdGain = 0.14, endFrequencyHz = 880.0, decayAmount = 0.22, fmFrequencyHz = 132.0, fmDepth = 0.016, ringModFrequencyHz = 35.0, ringModDepth = 0.30, shimmerGain = 0.060),
                ToneSpec(1180.0, 82, gapAfterMs = 22, amplitude = 0.20, waveShape = WaveShape.Sine, majorThirdGain = 0.18, endFrequencyHz = 1760.0, decayAmount = 0.20, fmFrequencyHz = 240.0, fmDepth = 0.014, ringModFrequencyHz = 53.0, ringModDepth = 0.36, shimmerGain = 0.090),
                ToneSpec(1980.0, 92, gapAfterMs = 28, amplitude = 0.18, waveShape = WaveShape.Sine, majorThirdGain = 0.12, endFrequencyHz = 1440.0, decayAmount = 0.18, fmFrequencyHz = 310.0, fmDepth = 0.016, ringModFrequencyHz = 71.0, ringModDepth = 0.42, shimmerGain = 0.110),
                ToneSpec(2640.0, 162, amplitude = 0.16, waveShape = WaveShape.Sine, majorThirdGain = 0.10, endFrequencyHz = 3120.0, decayAmount = 0.24, fmFrequencyHz = 420.0, fmDepth = 0.010, ringModFrequencyHz = 17.0, ringModDepth = 0.24, shimmerGain = 0.130),
            )
        }
    }

    private companion object {
        const val SoundSampleRate = 44_100
        const val DefaultSoundCueVolumePercent = 85
        const val MajorThirdRatio = 5.0 / 4.0
        const val MajorThirdGain = 0.38
        const val SoundCueOutputGain = 2.83
        const val SoundCueDurationScale = 1.5
        const val SoundCueQueueGapMs = 80L
    }
}
