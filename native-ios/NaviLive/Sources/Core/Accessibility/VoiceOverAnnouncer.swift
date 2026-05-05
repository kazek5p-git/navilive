import AVFoundation
import UIKit

enum NavigationSoundCue {
  case countdown
  case turnNow
  case pedestrianCrossing
  case warning
  case success
  case arrival
}

@MainActor
final class VoiceOverAnnouncer: NSObject {
  private struct QueuedSoundCue {
    let data: Data
    let volume: Float
    let duration: TimeInterval
  }

  private let synthesizer = AVSpeechSynthesizer()
  private var soundCueQueue: [QueuedSoundCue] = []
  private var soundCuePlaybackTask: Task<Void, Never>?
  private var activeCuePlayer: AVAudioPlayer?
  private var nextSoundCuePlaybackDate = Date.distantPast
  private var navigationSpeechSessionActive = false
  private static let soundCueQueueGapSeconds: TimeInterval = 0.08

  override init() {
    super.init()
    synthesizer.delegate = self
  }

  func announce(_ message: String, settings: AppSettings) {
    guard !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
      return
    }

    let shouldUseVoiceOver: Bool
    switch settings.speechMode {
    case .automatic:
      shouldUseVoiceOver = UIAccessibility.isVoiceOverRunning
    case .voiceOver:
      shouldUseVoiceOver = true
    case .speechSynthesizer:
      shouldUseVoiceOver = false
    }

    if shouldUseVoiceOver {
      UIAccessibility.post(notification: .announcement, argument: message)
      return
    }

    speakWithSynthesizer(message, settings: settings, usesNavigationAudioSession: false)
  }

  func announceNavigation(_ message: String, settings: AppSettings) {
    guard !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
      return
    }

    if settings.speechMode == .voiceOver {
      UIAccessibility.post(notification: .announcement, argument: message)
      return
    }

    speakWithSynthesizer(message, settings: settings, usesNavigationAudioSession: true)
  }

  func previewSynthesizer(_ message: String, settings: AppSettings) {
    guard !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
      return
    }

    speakWithSynthesizer(message, settings: settings, usesNavigationAudioSession: false)
  }

  func stopSpeech() {
    synthesizer.stopSpeaking(at: .immediate)
    deactivateNavigationAudioSession()
  }

  private func speakWithSynthesizer(
    _ message: String,
    settings: AppSettings,
    usesNavigationAudioSession: Bool
  ) {
    if usesNavigationAudioSession {
      prepareNavigationAudioSession()
    }

    let utterance = AVSpeechUtterance(string: message)
    utterance.rate = Float(max(0.35, min(settings.speechRate, 1.6)))
    utterance.volume = Float(max(0.1, min(settings.speechVolume, 1.0)))
    if let voiceIdentifier = settings.selectedSpeechVoiceIdentifier,
       let selectedVoice = AVSpeechSynthesisVoice(identifier: voiceIdentifier) {
      utterance.voice = selectedVoice
    } else {
      utterance.voice = AVSpeechSynthesisVoice(language: Locale.current.identifier)
    }
    synthesizer.stopSpeaking(at: .immediate)
    synthesizer.speak(utterance)
  }

  private func prepareNavigationAudioSession() {
    let session = AVAudioSession.sharedInstance()
    do {
      try session.setCategory(.playback, mode: .spokenAudio, options: [.duckOthers])
      try session.setActive(true)
      navigationSpeechSessionActive = true
    } catch {
      navigationSpeechSessionActive = false
    }
  }

  private func deactivateNavigationAudioSession() {
    guard navigationSpeechSessionActive else { return }
    do {
      try AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    } catch {
      // Leave audio session as-is when deactivation fails.
    }
    navigationSpeechSessionActive = false
  }

  func hapticSuccess() {
    UINotificationFeedbackGenerator().notificationOccurred(.success)
  }

  func hapticWarning() {
    UINotificationFeedbackGenerator().notificationOccurred(.warning)
  }

  @discardableResult
  func playSoundCue(
    _ cue: NavigationSoundCue,
    volume: Double = 0.85,
    theme: SoundCueTheme = .standard
  ) -> TimeInterval {
    let duration = cue.durationSeconds(theme: theme)
    let startDelay = reserveSoundCueStartDelay(duration: duration)
    soundCueQueue.append(
      QueuedSoundCue(
        data: cue.wavData(theme: theme),
        volume: Float(min(max(volume, 0.0), 1.0)),
        duration: duration
      )
    )
    startSoundCueQueueIfNeeded()
    return startDelay
  }

  private func reserveSoundCueStartDelay(duration: TimeInterval) -> TimeInterval {
    let now = Date()
    let startDate = nextSoundCuePlaybackDate > now ? nextSoundCuePlaybackDate : now
    let startDelay = max(0.0, startDate.timeIntervalSince(now))
    nextSoundCuePlaybackDate = startDate.addingTimeInterval(duration + Self.soundCueQueueGapSeconds)
    return startDelay
  }

  private func startSoundCueQueueIfNeeded() {
    guard soundCuePlaybackTask == nil else { return }
    soundCuePlaybackTask = Task { @MainActor [weak self] in
      await self?.runSoundCueQueue()
    }
  }

  private func runSoundCueQueue() async {
    while !soundCueQueue.isEmpty {
      let item = soundCueQueue.removeFirst()
      prepareNavigationAudioSession()
      guard let player = try? AVAudioPlayer(data: item.data) else { continue }
      activeCuePlayer = player
      player.volume = item.volume
      player.prepareToPlay()
      player.play()
      let playbackDuration = max(item.duration, player.duration) + Self.soundCueQueueGapSeconds
      do {
        try await Task.sleep(nanoseconds: UInt64(playbackDuration * 1_000_000_000))
      } catch {
        break
      }
      player.stop()
      activeCuePlayer = nil
    }
    activeCuePlayer = nil
    soundCuePlaybackTask = nil
    if soundCueQueue.isEmpty {
      nextSoundCuePlaybackDate = .distantPast
    }
  }
}

private extension NavigationSoundCue {
  func tones(theme: SoundCueTheme) -> [ToneSpec] {
    switch theme {
    case .standard:
      return standardTones()
    case .tetris:
      return tetrisTones()
    case .cosmic:
      return cosmicTones()
    }
  }

  func standardTones() -> [ToneSpec] {
    switch self {
    case .countdown:
      return [
        ToneSpec(frequency: 620, duration: 0.058, gapAfter: 0.042, amplitude: 0.30, waveShape: .triangle, majorThirdGain: 0.22),
        ToneSpec(frequency: 620, duration: 0.058, amplitude: 0.30, waveShape: .triangle, majorThirdGain: 0.22)
      ]
    case .turnNow:
      return [
        ToneSpec(frequency: 1280, duration: 0.055, gapAfter: 0.018, amplitude: 0.27, waveShape: .softSquare, majorThirdGain: 0.06, transientGain: 0.16),
        ToneSpec(frequency: 980, duration: 0.180, gapAfter: 0.018, amplitude: 0.29, waveShape: .softSquare, majorThirdGain: 0.18, endFrequency: 540, shimmerGain: 0.02),
        ToneSpec(frequency: 760, duration: 0.090, amplitude: 0.21, waveShape: .triangle, majorThirdGain: 0.16)
      ]
    case .pedestrianCrossing:
      return [
        ToneSpec(frequency: 760, duration: 0.060, gapAfter: 0.024, amplitude: 0.30, waveShape: .pulse, majorThirdGain: 0.18),
        ToneSpec(frequency: 960, duration: 0.060, gapAfter: 0.024, amplitude: 0.30, waveShape: .pulse, majorThirdGain: 0.18),
        ToneSpec(frequency: 760, duration: 0.060, gapAfter: 0.024, amplitude: 0.30, waveShape: .pulse, majorThirdGain: 0.18),
        ToneSpec(frequency: 960, duration: 0.084, amplitude: 0.30, waveShape: .pulse, majorThirdGain: 0.18)
      ]
    case .warning:
      return [
        ToneSpec(frequency: 500, duration: 0.088, gapAfter: 0.030, amplitude: 0.28, waveShape: .saw, majorThirdGain: 0.16),
        ToneSpec(frequency: 370, duration: 0.105, amplitude: 0.28, waveShape: .saw, majorThirdGain: 0.16)
      ]
    case .success:
      return [
        ToneSpec(frequency: 660, duration: 0.078, gapAfter: 0.024, amplitude: 0.32, waveShape: .sine, majorThirdGain: 0.42),
        ToneSpec(frequency: 880, duration: 0.096, amplitude: 0.32, waveShape: .sine, majorThirdGain: 0.42)
      ]
    case .arrival:
      return [
        ToneSpec(frequency: 740, duration: 0.078, gapAfter: 0.024, amplitude: 0.29, waveShape: .sine, majorThirdGain: 0.56, shimmerGain: 0.075),
        ToneSpec(frequency: 930, duration: 0.084, gapAfter: 0.024, amplitude: 0.29, waveShape: .sine, majorThirdGain: 0.58, shimmerGain: 0.080),
        ToneSpec(frequency: 1175, duration: 0.094, gapAfter: 0.026, amplitude: 0.29, waveShape: .sine, majorThirdGain: 0.60, shimmerGain: 0.085),
        ToneSpec(frequency: 1480, duration: 0.130, amplitude: 0.27, waveShape: .sine, majorThirdGain: 0.52, shimmerGain: 0.070)
      ]
    }
  }

  func tetrisTones() -> [ToneSpec] {
    switch self {
    case .countdown:
      return [
        ToneSpec(frequency: 659.25, duration: 0.046, gapAfter: 0.034, amplitude: 0.31, waveShape: .pulse, majorThirdGain: 0.0, dutyCycle: 0.125, decayAmount: 0.34, quantizeSteps: 14, transientGain: 0.10),
        ToneSpec(frequency: 783.99, duration: 0.046, amplitude: 0.31, waveShape: .pulse, majorThirdGain: 0.0, dutyCycle: 0.125, decayAmount: 0.34, quantizeSteps: 14, transientGain: 0.10)
      ]
    case .turnNow:
      return [
        ToneSpec(frequency: 1318.51, duration: 0.048, gapAfter: 0.014, amplitude: 0.30, waveShape: .pulse, majorThirdGain: 0.0, dutyCycle: 0.25, decayAmount: 0.30, quantizeSteps: 14, transientGain: 0.08),
        ToneSpec(frequency: 987.77, duration: 0.058, gapAfter: 0.014, amplitude: 0.31, waveShape: .pulse, majorThirdGain: 0.0, dutyCycle: 0.125, decayAmount: 0.32, quantizeSteps: 14),
        ToneSpec(frequency: 1046.50, duration: 0.090, amplitude: 0.29, waveShape: .pulse, majorThirdGain: 0.0, dutyCycle: 0.25, decayAmount: 0.22, quantizeSteps: 14)
      ]
    case .pedestrianCrossing:
      return [
        ToneSpec(frequency: 783.99, duration: 0.042, gapAfter: 0.018, amplitude: 0.28, waveShape: .pulse, majorThirdGain: 0.0, dutyCycle: 0.50, decayAmount: 0.28, quantizeSteps: 12),
        ToneSpec(frequency: 987.77, duration: 0.042, gapAfter: 0.018, amplitude: 0.28, waveShape: .pulse, majorThirdGain: 0.0, dutyCycle: 0.125, decayAmount: 0.28, quantizeSteps: 12),
        ToneSpec(frequency: 783.99, duration: 0.042, gapAfter: 0.018, amplitude: 0.28, waveShape: .pulse, majorThirdGain: 0.0, dutyCycle: 0.50, decayAmount: 0.28, quantizeSteps: 12),
        ToneSpec(frequency: 987.77, duration: 0.064, amplitude: 0.28, waveShape: .pulse, majorThirdGain: 0.0, dutyCycle: 0.125, decayAmount: 0.22, quantizeSteps: 12)
      ]
    case .warning:
      return [
        ToneSpec(frequency: 493.88, duration: 0.074, gapAfter: 0.026, amplitude: 0.30, waveShape: .pulse, majorThirdGain: 0.0, dutyCycle: 0.50, decayAmount: 0.36, quantizeSteps: 10),
        ToneSpec(frequency: 440, duration: 0.074, gapAfter: 0.026, amplitude: 0.30, waveShape: .pulse, majorThirdGain: 0.0, dutyCycle: 0.25, decayAmount: 0.36, quantizeSteps: 10),
        ToneSpec(frequency: 392, duration: 0.106, amplitude: 0.30, waveShape: .pulse, majorThirdGain: 0.0, dutyCycle: 0.125, decayAmount: 0.34, quantizeSteps: 10)
      ]
    case .success:
      return [
        ToneSpec(frequency: 440, duration: 0.046, gapAfter: 0.020, amplitude: 0.29, waveShape: .pulse, majorThirdGain: 0.0, dutyCycle: 0.25, decayAmount: 0.30, quantizeSteps: 14),
        ToneSpec(frequency: 523.25, duration: 0.052, gapAfter: 0.020, amplitude: 0.29, waveShape: .pulse, majorThirdGain: 0.0, dutyCycle: 0.25, decayAmount: 0.28, quantizeSteps: 14),
        ToneSpec(frequency: 659.25, duration: 0.076, amplitude: 0.29, waveShape: .pulse, majorThirdGain: 0.0, dutyCycle: 0.125, decayAmount: 0.20, quantizeSteps: 14)
      ]
    case .arrival:
      return [
        ToneSpec(frequency: 659.25, duration: 0.052, gapAfter: 0.016, amplitude: 0.28, waveShape: .pulse, majorThirdGain: 0.0, dutyCycle: 0.25, decayAmount: 0.30, quantizeSteps: 14),
        ToneSpec(frequency: 493.88, duration: 0.052, gapAfter: 0.016, amplitude: 0.28, waveShape: .pulse, majorThirdGain: 0.0, dutyCycle: 0.125, decayAmount: 0.30, quantizeSteps: 14),
        ToneSpec(frequency: 523.25, duration: 0.056, gapAfter: 0.016, amplitude: 0.28, waveShape: .pulse, majorThirdGain: 0.0, dutyCycle: 0.25, decayAmount: 0.28, quantizeSteps: 14),
        ToneSpec(frequency: 587.33, duration: 0.058, gapAfter: 0.018, amplitude: 0.28, waveShape: .pulse, majorThirdGain: 0.0, dutyCycle: 0.125, decayAmount: 0.26, quantizeSteps: 14),
        ToneSpec(frequency: 659.25, duration: 0.118, amplitude: 0.27, waveShape: .pulse, majorThirdGain: 0.0, dutyCycle: 0.25, decayAmount: 0.18, quantizeSteps: 14)
      ]
    }
  }

  func cosmicTones() -> [ToneSpec] {
    switch self {
    case .countdown:
      return [
        ToneSpec(frequency: 1560, duration: 0.084, gapAfter: 0.048, amplitude: 0.20, waveShape: .sine, majorThirdGain: 0.05, endFrequency: 2180, decayAmount: 0.48, fmFrequency: 185, fmDepth: 0.018, ringModFrequency: 42, ringModDepth: 0.46, shimmerGain: 0.090),
        ToneSpec(frequency: 2180, duration: 0.096, amplitude: 0.19, waveShape: .sine, majorThirdGain: 0.04, endFrequency: 1460, decayAmount: 0.42, fmFrequency: 225, fmDepth: 0.015, ringModFrequency: 57, ringModDepth: 0.40, shimmerGain: 0.075)
      ]
    case .turnNow:
      return [
        ToneSpec(frequency: 360, duration: 0.088, gapAfter: 0.012, amplitude: 0.24, waveShape: .saw, majorThirdGain: 0.0, endFrequency: 1280, decayAmount: 0.24, quantizeSteps: 30, fmFrequency: 96, fmDepth: 0.030, ringModFrequency: 73, ringModDepth: 0.55, transientGain: 0.06),
        ToneSpec(frequency: 1280, duration: 0.138, gapAfter: 0.016, amplitude: 0.25, waveShape: .softSquare, majorThirdGain: 0.08, midFrequency: 620, endFrequency: 1720, decayAmount: 0.18, fmFrequency: 143, fmDepth: 0.024, ringModFrequency: 119, ringModDepth: 0.52, shimmerGain: 0.070),
        ToneSpec(frequency: 940, duration: 0.096, amplitude: 0.18, waveShape: .sine, majorThirdGain: 0.16, endFrequency: 1240, decayAmount: 0.32, fmFrequency: 260, fmDepth: 0.018, ringModFrequency: 31, ringModDepth: 0.34, shimmerGain: 0.095)
      ]
    case .pedestrianCrossing:
      return [
        ToneSpec(frequency: 760, duration: 0.054, gapAfter: 0.024, amplitude: 0.21, waveShape: .triangle, majorThirdGain: 0.0, endFrequency: 1460, decayAmount: 0.34, fmFrequency: 210, fmDepth: 0.020, ringModFrequency: 88, ringModDepth: 0.44, shimmerGain: 0.060),
        ToneSpec(frequency: 1680, duration: 0.054, gapAfter: 0.024, amplitude: 0.20, waveShape: .sine, majorThirdGain: 0.0, endFrequency: 1040, decayAmount: 0.34, fmFrequency: 250, fmDepth: 0.018, ringModFrequency: 101, ringModDepth: 0.44, shimmerGain: 0.060),
        ToneSpec(frequency: 760, duration: 0.054, gapAfter: 0.024, amplitude: 0.21, waveShape: .triangle, majorThirdGain: 0.0, endFrequency: 1460, decayAmount: 0.34, fmFrequency: 210, fmDepth: 0.020, ringModFrequency: 88, ringModDepth: 0.44, shimmerGain: 0.060),
        ToneSpec(frequency: 1980, duration: 0.096, amplitude: 0.18, waveShape: .sine, majorThirdGain: 0.10, endFrequency: 1320, decayAmount: 0.24, fmFrequency: 330, fmDepth: 0.014, ringModFrequency: 63, ringModDepth: 0.36, shimmerGain: 0.095)
      ]
    case .warning:
      return [
        ToneSpec(frequency: 880, duration: 0.100, gapAfter: 0.020, amplitude: 0.24, waveShape: .saw, majorThirdGain: 0.0, endFrequency: 470, decayAmount: 0.34, quantizeSteps: 22, fmFrequency: 122, fmDepth: 0.020, ringModFrequency: 137, ringModDepth: 0.62),
        ToneSpec(frequency: 540, duration: 0.130, amplitude: 0.22, waveShape: .pulse, majorThirdGain: 0.0, endFrequency: 260, dutyCycle: 0.18, decayAmount: 0.44, quantizeSteps: 18, fmFrequency: 74, fmDepth: 0.026, ringModFrequency: 91, ringModDepth: 0.68)
      ]
    case .success:
      return [
        ToneSpec(frequency: 520, duration: 0.078, gapAfter: 0.020, amplitude: 0.21, waveShape: .triangle, majorThirdGain: 0.10, endFrequency: 1140, decayAmount: 0.18, fmFrequency: 160, fmDepth: 0.018, ringModFrequency: 47, ringModDepth: 0.32, shimmerGain: 0.070),
        ToneSpec(frequency: 1460, duration: 0.102, amplitude: 0.19, waveShape: .sine, majorThirdGain: 0.18, endFrequency: 2040, decayAmount: 0.20, fmFrequency: 290, fmDepth: 0.012, ringModFrequency: 23, ringModDepth: 0.28, shimmerGain: 0.110)
      ]
    case .arrival:
      return [
        ToneSpec(frequency: 420, duration: 0.078, gapAfter: 0.020, amplitude: 0.20, waveShape: .triangle, majorThirdGain: 0.14, endFrequency: 880, decayAmount: 0.22, fmFrequency: 132, fmDepth: 0.016, ringModFrequency: 35, ringModDepth: 0.30, shimmerGain: 0.060),
        ToneSpec(frequency: 1180, duration: 0.082, gapAfter: 0.022, amplitude: 0.20, waveShape: .sine, majorThirdGain: 0.18, endFrequency: 1760, decayAmount: 0.20, fmFrequency: 240, fmDepth: 0.014, ringModFrequency: 53, ringModDepth: 0.36, shimmerGain: 0.090),
        ToneSpec(frequency: 1980, duration: 0.092, gapAfter: 0.028, amplitude: 0.18, waveShape: .sine, majorThirdGain: 0.12, endFrequency: 1440, decayAmount: 0.18, fmFrequency: 310, fmDepth: 0.016, ringModFrequency: 71, ringModDepth: 0.42, shimmerGain: 0.110),
        ToneSpec(frequency: 2640, duration: 0.162, amplitude: 0.16, waveShape: .sine, majorThirdGain: 0.10, endFrequency: 3120, decayAmount: 0.24, fmFrequency: 420, fmDepth: 0.010, ringModFrequency: 17, ringModDepth: 0.24, shimmerGain: 0.130)
      ]
    }
  }

  func durationSeconds(theme: SoundCueTheme) -> TimeInterval {
    tones(theme: theme).reduce(0) { $0 + ($1.duration + $1.gapAfter) * soundCueDurationScale }
  }

  func wavData(sampleRate: Int = 44_100, theme: SoundCueTheme) -> Data {
    var samples: [Int16] = []
    var phase = 0.0
    var shimmerPhase = 0.0
    var bassPhase = 0.0
    var fmPhase = 0.0
    var ringPhase = 0.0
    tones(theme: theme).forEach { tone in
      let toneSampleCount = Int(Double(sampleRate) * tone.duration * soundCueDurationScale)
      for index in 0..<toneSampleCount {
        let progress = Double(index) / Double(max(toneSampleCount, 1))
        let envelope: Double
        if progress < 0.12 {
          envelope = progress / 0.12
        } else if progress > 0.82 {
          envelope = (1.0 - progress) / 0.18
        } else {
          envelope = 1.0
        }
        let frequency = tone.frequency(at: progress)
        let modulatedFrequency: Double
        if tone.fmDepth > 0.0 {
          fmPhase = (fmPhase + tone.fmFrequency / Double(sampleRate)).truncatingRemainder(dividingBy: 1.0)
          modulatedFrequency = max(1.0, frequency * (1.0 + (sineWave(phase: fmPhase) * tone.fmDepth)))
        } else {
          modulatedFrequency = frequency
        }
        phase = (phase + modulatedFrequency / Double(sampleRate)).truncatingRemainder(dividingBy: 1.0)
        let fundamental = waveValue(shape: tone.waveShape, phase: phase, dutyCycle: tone.dutyCycle)
        let third = waveValue(
          shape: tone.waveShape,
          phase: (phase * majorThirdRatio).truncatingRemainder(dividingBy: 1.0),
          dutyCycle: tone.dutyCycle
        ) * tone.majorThirdGain
        let leadValue = min(max((fundamental + third) / (1.0 + tone.majorThirdGain), -1.0), 1.0) * tone.amplitude
        let shimmerValue: Double
        if tone.shimmerGain > 0.0 {
          shimmerPhase = (shimmerPhase + (modulatedFrequency * 2.0) / Double(sampleRate)).truncatingRemainder(dividingBy: 1.0)
          shimmerValue = sineWave(phase: shimmerPhase) * tone.shimmerGain * pow(max(0.0, 1.0 - progress), 1.4)
        } else {
          shimmerValue = 0.0
        }
        let transientValue: Double
        if tone.transientGain > 0.0 {
          let transientPhase = (tone.transientFrequency * Double(index) / Double(sampleRate)).truncatingRemainder(dividingBy: 1.0)
          transientValue = tone.transientGain * exp(-progress * 38.0) * sineWave(phase: transientPhase)
        } else {
          transientValue = 0.0
        }
        let bassValue: Double
        if let bassFrequency = tone.bassFrequency {
          bassPhase = (bassPhase + bassFrequency / Double(sampleRate)).truncatingRemainder(dividingBy: 1.0)
          bassValue = sineWave(phase: bassPhase) * tone.bassAmplitude
        } else {
          bassValue = 0.0
        }
        let clampedEnvelope = max(0.0, min(envelope, 1.0))
        let mixedValue = leadValue + shimmerValue + transientValue + bassValue
        let ringedValue: Double
        if let ringModFrequency = tone.ringModFrequency {
          ringPhase = (ringPhase + ringModFrequency / Double(sampleRate)).truncatingRemainder(dividingBy: 1.0)
          let ring = (1.0 - tone.ringModDepth) + (sineWave(phase: ringPhase) * tone.ringModDepth)
          ringedValue = mixedValue * ring
        } else {
          ringedValue = mixedValue
        }
        let pluckEnvelope = max(0.0, min(clampedEnvelope * max(0.16, min(1.0, 1.0 - (tone.decayAmount * progress))), 1.0))
        let rawSample = min(max(ringedValue * pluckEnvelope * soundCueOutputGain, -1.0), 1.0)
        let normalizedSample: Double
        if let quantizeSteps = tone.quantizeSteps {
          normalizedSample = min(max((rawSample * Double(quantizeSteps)).rounded() / Double(quantizeSteps), -1.0), 1.0)
        } else {
          normalizedSample = rawSample
        }
        let sample = Int16(normalizedSample * Double(Int16.max))
        samples.append(sample)
      }
      samples.append(contentsOf: Array(repeating: 0, count: Int(Double(sampleRate) * tone.gapAfter * soundCueDurationScale)))
    }
    return WavDataBuilder.makeMonoPcm16(samples: samples, sampleRate: sampleRate)
  }

  func waveValue(shape: SoundWaveShape, phase: Double, dutyCycle: Double = 0.32) -> Double {
    switch shape {
    case .sine:
      return sineWave(phase: phase)
    case .triangle:
      return triangleWave(phase: phase)
    case .softSquare:
      return softSquareWave(phase: phase)
    case .pulse:
      return pulseWave(phase: phase, dutyCycle: dutyCycle)
    case .saw:
      return sawWave(phase: phase)
    }
  }

  func sineWave(phase: Double) -> Double {
    sin(2.0 * .pi * phase)
  }

  func triangleWave(phase: Double) -> Double {
    return 4.0 * abs(phase - 0.5) - 1.0
  }

  func softSquareWave(phase: Double) -> Double {
    let angle = 2.0 * .pi * phase
    return min(max((sin(angle) + sin(angle * 3.0) / 3.0 + sin(angle * 5.0) / 5.0) / 1.53, -1.0), 1.0)
  }

  func pulseWave(phase: Double, dutyCycle: Double) -> Double {
    return phase < min(max(dutyCycle, 0.08), 0.75) ? 1.0 : -0.72
  }

  func sawWave(phase: Double) -> Double {
    return (2.0 * phase) - 1.0
  }

  var majorThirdRatio: Double { 5.0 / 4.0 }

  var soundCueOutputGain: Double { 2.83 }

  var soundCueDurationScale: Double { 1.5 }
}

private struct ToneSpec {
  let frequency: Double
  let duration: TimeInterval
  let gapAfter: TimeInterval
  let amplitude: Double
  let waveShape: SoundWaveShape
  let majorThirdGain: Double
  let endFrequency: Double?
  let midFrequency: Double?
  let dutyCycle: Double
  let decayAmount: Double
  let quantizeSteps: Int?
  let fmFrequency: Double
  let fmDepth: Double
  let ringModFrequency: Double?
  let ringModDepth: Double
  let shimmerGain: Double
  let transientGain: Double
  let transientFrequency: Double
  let bassFrequency: Double?
  let bassAmplitude: Double

  init(
    frequency: Double,
    duration: TimeInterval,
    gapAfter: TimeInterval = 0,
    amplitude: Double = 0.32,
    waveShape: SoundWaveShape = .triangle,
    majorThirdGain: Double = 0.38,
    midFrequency: Double? = nil,
    endFrequency: Double? = nil,
    dutyCycle: Double = 0.32,
    decayAmount: Double = 0.0,
    quantizeSteps: Int? = nil,
    fmFrequency: Double = 0.0,
    fmDepth: Double = 0.0,
    ringModFrequency: Double? = nil,
    ringModDepth: Double = 0.0,
    shimmerGain: Double = 0.0,
    transientGain: Double = 0.0,
    transientFrequency: Double = 1900.0,
    bassFrequency: Double? = nil,
    bassAmplitude: Double = 0.0
  ) {
    self.frequency = frequency
    self.duration = duration
    self.gapAfter = gapAfter
    self.amplitude = amplitude
    self.waveShape = waveShape
    self.majorThirdGain = majorThirdGain
    self.midFrequency = midFrequency
    self.endFrequency = endFrequency
    self.dutyCycle = dutyCycle
    self.decayAmount = decayAmount
    self.quantizeSteps = quantizeSteps
    self.fmFrequency = fmFrequency
    self.fmDepth = fmDepth
    self.ringModFrequency = ringModFrequency
    self.ringModDepth = ringModDepth
    self.shimmerGain = shimmerGain
    self.transientGain = transientGain
    self.transientFrequency = transientFrequency
    self.bassFrequency = bassFrequency
    self.bassAmplitude = bassAmplitude
  }

  func frequency(at progress: Double) -> Double {
    guard let endFrequency else { return frequency }
    if let midFrequency {
      if progress < 0.54 {
        let local = max(0.0, min(progress / 0.54, 1.0))
        return frequency + ((midFrequency - frequency) * smoothStep(local))
      }
      let local = max(0.0, min((progress - 0.54) / 0.46, 1.0))
      return midFrequency + ((endFrequency - midFrequency) * smoothStep(local))
    }
    let smoothed = smoothStep(progress)
    return frequency + ((endFrequency - frequency) * smoothed)
  }

  private func smoothStep(_ value: Double) -> Double {
    value * value * (3.0 - 2.0 * value)
  }
}

private enum SoundWaveShape {
  case sine
  case triangle
  case softSquare
  case pulse
  case saw
}

private enum WavDataBuilder {
  static func makeMonoPcm16(samples: [Int16], sampleRate: Int) -> Data {
    let bytesPerSample = 2
    let dataSize = UInt32(samples.count * bytesPerSample)
    var data = Data()

    data.appendAscii("RIFF")
    data.appendLittleEndian(UInt32(36) + dataSize)
    data.appendAscii("WAVE")
    data.appendAscii("fmt ")
    data.appendLittleEndian(UInt32(16))
    data.appendLittleEndian(UInt16(1))
    data.appendLittleEndian(UInt16(1))
    data.appendLittleEndian(UInt32(sampleRate))
    data.appendLittleEndian(UInt32(sampleRate * bytesPerSample))
    data.appendLittleEndian(UInt16(bytesPerSample))
    data.appendLittleEndian(UInt16(16))
    data.appendAscii("data")
    data.appendLittleEndian(dataSize)
    samples.forEach { data.appendLittleEndian($0) }

    return data
  }
}

private extension Data {
  mutating func appendAscii(_ string: String) {
    if let value = string.data(using: .ascii) {
      append(value)
    }
  }

  mutating func appendLittleEndian(_ value: UInt16) {
    var littleEndian = value.littleEndian
    Swift.withUnsafeBytes(of: &littleEndian) { append(contentsOf: $0) }
  }

  mutating func appendLittleEndian(_ value: UInt32) {
    var littleEndian = value.littleEndian
    Swift.withUnsafeBytes(of: &littleEndian) { append(contentsOf: $0) }
  }

  mutating func appendLittleEndian(_ value: Int16) {
    var littleEndian = value.littleEndian
    Swift.withUnsafeBytes(of: &littleEndian) { append(contentsOf: $0) }
  }
}

extension VoiceOverAnnouncer: AVSpeechSynthesizerDelegate {
  nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
    Task { @MainActor in
      deactivateNavigationAudioSession()
    }
  }

  nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
    Task { @MainActor in
      deactivateNavigationAudioSession()
    }
  }
}
