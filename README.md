# OpenRemote

A phone-first Android client for the OpenCode server. OpenRemote talks to the same HTTP API as the OpenCode desktop app, so sessions, models, agents, permissions and tool output are the same ones you see on your computer — just from your pocket.

Kotlin · Jetpack Compose · Material 3 · OkHttp · kotlinx.serialization · Coil · ZXing

## What you need

- OpenCode installed on the computer that runs your sessions.
- Android 7.0 (API 24) or newer on the phone.
- Both devices on the same network (or a trusted VPN).

## Start the server

```sh
opencode serve --hostname 0.0.0.0 --port 4096
```

`--hostname 0.0.0.0` makes the server reachable from the phone. The desktop client keeps working at the same time.

## Connect

Two ways:

1. **Pairing (recommended)** — run `opencode pair` on the computer and scan the QR code from the app, or paste the pairing link / bare code. Tapping a pairing link on the phone opens OpenRemote and connects immediately. Pairing links are single-use and expire, and the app keeps only the exchanged token.
2. **Manual** — enter the computer's LAN address (for example `http://192.168.1.20:4096`) and the server password.

The address is stored on the phone together with the token. Providers' API keys are never requested, displayed, or saved by the app.

## Features

- **Sessions** — list, search, load more, create with a project directory, agent and model, rename, archive/restore, delete. Live busy indicators per session.
- **Conversation** — full timeline: user prompts, assistant text, reasoning, tool calls with input/result/error, shell commands, compaction, agent/model/location switches. Markdown with headings, lists, quotes, links, inline code, and copyable code blocks; attached images render inline.
- **Live updates** — server-sent events keep the timeline and session list current while OpenCode works, with polling as a fallback. Reconnects automatically.
- **Prompting** — send text and image/file attachments (up to 6, 10 MB each), steer or queue while a run is active, stop a run, run slash commands with suggestions, run shell commands in the session directory.
- **Approvals** — answer permission requests inline, and fill in server forms with conditional fields, selects, multiselects, numbers and booleans.
- **Models and agents** — pick from the location's models and agents, per session, and switch on the fly.
- **Session actions** — fork, compact context, open subagent sessions from the subagent tool.
- **Settings** — theme (dark by default, plus light and follow-system), default delivery, reasoning visibility, server details, reconnect and disconnect. A moon/sun button in the sessions top bar switches theme instantly.

## Build

Requirements: JDK 21, Android SDK with platform 37 and build-tools installed.

```sh
./gradlew assembleDebug        # or .\gradlew.bat on Windows
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

Unit tests:

```sh
cd app
./gradlew testDebugUnitTest
```

Tests run from the module directory; Gradle's configuration cache is disabled for the project because AGP and the Kotlin serialization plugin cannot store it reliably.

If Gradle cannot reach Maven Central or the plugin portal on this machine, point the Gradle user home at the local proxy in `~/.gradle/gradle.properties`:

```properties
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=18888
```

## Run on the emulator

The repository ships two PowerShell helpers. Both fall back to `%LOCALAPPDATA%\Android\Sdk` when `ANDROID_HOME` is not set.

```powershell
.\scripts\run-emulator.ps1              # window, quick-boot snapshot
.\scripts\run-emulator.ps1 -Headless    # no window, software GPU
.\scripts\run-emulator.ps1 -ColdBoot    # ignore the saved snapshot

.\scripts\launch-app.ps1 -Build         # build, install, launch
.\scripts\launch-app.ps1 -Build -Reset  # also clear the saved connection
.\scripts\launch-app.ps1 -Build -Reset -PairCode <code>
```

`launch-app.ps1` targets whatever device `adb` sees first, so it also works on a USB phone.

### One-time emulator setup

Requires the Android command-line tools, `platform-tools`, `emulator`, and a system image:

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
& "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" "platform-tools" "emulator" "system-images;android-36;default;x86_64"
& "$sdk\cmdline-tools\latest\bin\avdmanager.bat" create avd -n openremote -k "system-images;android-36;default;x86_64" -d pixel_6
```

The AVD uses the Pixel 6 hardware profile with the generic AOSP Android 16 (API 36) image, so Android reports the model as **Android SDK built for x86_64** (`sdk_phone64_x86_64`, 1080×2400, 420 dpi). On Windows, check hardware acceleration with `emulator.exe -accel-check`; WHPX must be enabled.

### Reaching the server from the emulator

The emulator reaches the host at `10.0.2.2`, so a server bound to loopback is enough:

```powershell
opencode serve --port 4096
# in the app: http://10.0.2.2:4096
```

A physical phone needs the LAN address instead, and the server must listen on all interfaces:

```sh
opencode serve --hostname 0.0.0.0 --port 4096
```

### Pairing without scanning

`opencode pair` prints a link and QR code. For scripted testing, create a code through the API and hand it to the launcher:

```powershell
$code = (curl.exe -s -u "opencode:<password>" -X POST http://127.0.0.1:4096/api/pair | ConvertFrom-Json).code
.\scripts\launch-app.ps1 -Reset -PairCode $code
```

### Manual commands

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd openremote -no-audio
& $adb wait-for-device
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb shell am start -n com.tom7.openremote/.MainActivity

# screenshot (use cmd /c: PowerShell redirection would corrupt the PNG)
cmd /c "`"$adb`" exec-out screencap -p > shot.png"

# app logs
& $adb logcat --pid=$(& $adb shell pidof com.tom7.openremote)

# pairing deep link
& $adb shell am start -a android.intent.action.VIEW -d "http://10.0.2.2:4096/auth/connect/<code>" com.tom7.openremote

# stop the emulator
& $adb emu kill
```

On macOS or Linux the same steps work with `./gradlew`, `$ANDROID_HOME/emulator/emulator`, and `$ANDROID_HOME/platform-tools/adb`.

## Project layout

```
app/src/main/java/com/tom7/openremote
├── AppViewModel.kt          connection lifecycle, realtime stream, app settings
├── MainActivity.kt          root navigation (connect ↔ sessions ↔ chat ↔ settings)
├── OpenRemoteApp.kt         application container (client, store, view models' deps)
├── data/                    API client, DTOs, pairing, event stream, connection store
└── ui/
    ├── chat/                timeline, composer, pickers, form sheet
    ├── sessions/            session list and new-session flow
    ├── connect/             pairing and manual connection
    ├── settings/            server and app preferences
    ├── common/              markdown, formatting, clipboard
    └── theme/               ink/amber Material 3 theme

scripts/
├── run-emulator.ps1         boot the AVD and wait until Android is ready
└── launch-app.ps1           build, install, launch, optional reset/pairing
```

## Security notes

- Cleartext HTTP is enabled so the app can talk to a LAN server; prefer HTTPS or a trusted network for anything beyond your own Wi-Fi.
- The pairing token is stored in app-private preferences (`allowBackup` is off).
- The app never reads provider credentials from the catalog API.
