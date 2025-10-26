# Quest Network Shaper & Logger

Quest Network Shaper is an Android/Meta Quest oriented utility built on top of the ToyVpn-PcapPlusPlus fork. It runs a local `VpnService` to capture device traffic, apply synthetic latency/jitter/loss/bandwidth constraints, and export one second aggregates for post-session analysis alongside OVR metrics.

## Features

- Foreground-only local VPN service that keeps running while VR titles are active.
- Default per-app VPN targeting for `quest.eleven.forfunlabs`, with an editable package field in the dashboard.
- Real-time shaping controls for latency, jitter, packet loss, and directional bandwidth caps with preset profiles.
- Periodic UDP/ICMP-style reachability probe with rolling RTT/jitter/loss calculations.
- CSV logging at 1 Hz including Wi‑Fi signal telemetry, shaping configuration, and probe results. Logs rotate under `/sdcard/Android/data/com.questnetshaper.app/files/logs/` (latest 10 files or 100 MB).
- Material 3 Compose UI optimised for quick adjustments from a single screen, including optional notes and probe host entry.
- Kotlin-first architecture (StateFlow + coroutines) with headless service and metrics repository for UI synchronisation.

> **Note:** The reference implementation focuses on instrumentation and shaping timing. Transparent IP forwarding for all protocols requires additional work beyond the scope of this sample.

## Architecture Overview

```
[TUN Interface]
     ↓
[PacketReader]
     ↓
[PacketShaper] ← [ConfigStore/StateFlow]
     ↓
[MetricsAggregator] → [CSV Writer]
            ↑
      [ActiveProbe]
```

### Core Components

| Component | Responsibility |
| --- | --- |
| `MainActivity` | Hosts the Compose dashboard with start/stop, shaping sliders, presets, probe host, and live stats. |
| `ConfigViewModel` | Bridges UI events to a shared `ConfigStore` and exposes a combined `UiState` via `StateFlow`. |
| `ConfigStore` / `MetricsStore` | Global state holders accessed from the service and UI without binding. |
| `NetShapeVpnService` | Foreground `VpnService` that establishes the TUN interface, listens for configuration updates, executes the shaping pipeline, and coordinates probes/notifications. |
| `PacketShaper` | Applies latency + jitter delay, Bernoulli loss, and token-bucket bandwidth limiting using `BandwidthLimiter`. |
| `MetricsAggregator` | Counts packets/bytes per second, collects Wi‑Fi telemetry, merges probe stats, and appends CSV rows. |
| `ActiveProbe` | Coroutine-based reachability probe using `InetAddress.isReachable`, updated at 500 ms cadence. |
| `CsvWriter` | Lightweight writer with rotation guard to ensure logs remain accessible over MTP/ADB pull. |

### Preset Profiles

| Name | Latency | Jitter | Loss | Up/Down (kbps) | Use case |
| --- | --- | --- | --- | --- | --- |
| None | 0 | 0 | 0 | 10 000 / 10 000 | Transparent monitoring |
| High Ping | 120 | 20 | 0 | 10 000 / 10 000 | Remote server feel |
| Jittery Wi‑Fi | 40 | 60 | 1 | 10 000 / 10 000 | Spiky latency |
| Packet Loss | 40 | 10 | 3 | 10 000 / 10 000 | Mid-session loss bursts |
| Low Bandwidth | 20 | 10 | 0 | 512 / 1024 | Throttled uplink/downlink |

## Build & Install

1. **Clone**
   ```bash
   git clone https://github.com/seladb/ToyVpn-PcapPlusPlus.git
   cd ToyVpn-PcapPlusPlus
   ```
2. **Open in Android Studio Giraffe+** (or use `./gradlew assembleDebug`).
   The project is now pure Kotlin/Java; no native submodules need to be initialised.
3. **Target**: API 33 (Quest runtime) / minimum API 29.
4. **Deploy** the `app` module to the Quest 3 via USB (developer mode must be enabled).
5. **Grant** VPN and storage permissions on first launch.

## Usage

1. Launch the Quest Network Shaper app on the headset (or paired device) and grant the VPN permission prompt.
2. Tap **Start** to establish the local VPN tunnel; a persistent notification confirms the foreground service.
3. Use the toggles and sliders to enable shaping or logging, or pick a preset profile to apply saved values instantly.
4. Optionally configure:
   - Probe host (default `8.8.8.8`).
   - Target application package for VPN capture (default `quest.eleven.forfunlabs`).
   - Notes string recorded in the CSV output.
5. Live metrics update every second. Shaping/logging switches propagate immediately without restarting the service.
6. Tap **Stop** to tear down the tunnel and terminate the foreground notification.

## Logging & Data

- Logs reside at `/sdcard/Android/data/com.questnetshaper.app/files/logs/netlog_YYYYMMDD_HHMMSS.csv`.
- Each row contains timestamp, elapsed seconds, byte/packet rates, probe RTT statistics, shaping config snapshot, target package, and Wi‑Fi RSSI/link speed.
- Example snippet: [`docs/netlog_sample.csv`](docs/netlog_sample.csv).

## Limitations & Future Work

- The shaping engine currently focuses on timing and loss simulation. Implementing a full IP stack/NAT to forward payloads is a recommended follow-up.
- Advanced loss models (Gilbert–Elliott), PCAP export, REST remote control, and VR overlay visualisations are earmarked as future enhancements.
- Battery reminder notifications for sessions exceeding 60 minutes are planned but not yet implemented.
- The original ToyVpn JNI shim and its associated instrumentation/unit tests were removed when moving to the Kotlin-only stack; new coverage should target the current architecture.

## License

This project inherits the original [ToyVpn-PcapPlusPlus license](LICENSE).
