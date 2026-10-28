# OpenRemote

**The Android companion app for OpenCode desktop.** Pair your phone with the OpenCode app on your computer once, and the sessions you're already running on your PC are available from your phone — anywhere, over an encrypted tunnel. Same sessions, same agents and models, same tool output. Your desktop keeps working exactly as it does now.

## Pair your computer (recommended)

<table>
<tr>
<td width="65%" valign="top">

<div align="center">

<img alt="Screenshot 2026-10-29 023139" src="https://github.com/user-attachments/assets/7c0ea354-09af-4ce6-9e9e-c828534de303" />


</div>

</td>
<td width="35%" valign="top">
   <img height="100%" alt="Screenshot 2026-10-29 023226" src="https://github.com/user-attachments/assets/21dc0f5d-282a-46cf-b4d5-034b4da9fdb8" />
</td>
</tr>
</table>


You need OpenCode desktop running on the machine that hosts your sessions, and Android 7.0 (API 24) or newer on the phone.

1. Open the **OpenCode desktop app** on your computer.
2. Go to **Settings → Pairing** and click **Pair device**.
3. A QR code and a link appear. Pairing sets up remote access automatically — leave the **Remote access** switch on if you want to reach this computer from outside your network.
4. On your phone, open OpenRemote and, under **Pair instantly**, tap **Scan** and point the camera at the QR code.
   Not scanning? Click **Copy link** in the desktop app, paste it into the **Pairing link or code** field, and tap **Pair**.
5. Your sessions appear. Tap one to follow it live, reply, approve permission requests, or start a new run.

Tapping a pairing link while the app is installed opens OpenRemote and connects immediately, so you can also send yourself the link.

Things worth knowing:

- Pairing codes are single-use and expire. If the code runs out, click **New code** in the dialog and scan again.
- The app keeps only the exchanged token, never the code.
- There is no account, address, or API key to set up: pairing is the whole setup.

## What you can do from your phone

- **Sessions** — browse, search, and create sessions with a project directory, agent and model; rename, archive, or delete. Busy sessions are marked live.
- **Conversation** — the full timeline the desktop shows: prompts, assistant text, reasoning, tool calls with input/result/error, shell commands, compaction, agent/model switches. Markdown with code blocks, and attached images inline.
- **Live updates** — server-sent events keep the timeline and the session list current while OpenCode works, with polling as a fallback and automatic reconnects.
- **Prompting** — send text and images/files (up to 6, 10 MB each), **queue** or **steer** while a run is active, stop a run, run slash commands, or run a shell command in the session directory.
- **Approvals** — answer permission requests and fill in server forms without going back to the desk.
- **Models and agents** — pick per session and switch on the fly.
- **Session actions** — fork, compact context, and jump into subagent sessions.
- **Settings** — dark by default, plus light and follow-system; default delivery, reasoning visibility, server details, reconnect and disconnect. The moon/sun button in the sessions top bar flips the theme instantly.

## How the connection works

OpenRemote is a client, not a second assistant. It talks to the same OpenCode server that powers the desktop app — the desktop process keeps owning the sessions and the model calls.

Pairing does two things: it turns on **Remote access** on the computer, and it hands the phone a one-time code plus the tunnel address. The phone exchanges the code for a token and keeps that token. From then on the phone connects to this computer's server through an end-to-end encrypted tunnel, which is what makes it work away from your home network.

Turning **Remote access** off in **Settings → Pairing** disconnects paired devices until you pair them again. The desktop app and the phone can be connected at the same time.

## Connect manually (advanced)

Use this only when you want to reach a server directly, for example on your own LAN or VPN. It skips pairing and uses the server password.

On the computer:

```sh
opencode serve --hostname 0.0.0.0 --port 4096
```

`--hostname 0.0.0.0` makes the server reachable from other devices; the printed `server password …` is what the phone needs. If you already run the background service, `opencode service get password` prints the same credential.

On the phone, under **Server**, enter the address (`http://192.168.1.20:4096`) and the password, then connect. Cleartext HTTP is allowed so a LAN server works; prefer HTTPS or a trusted network for anything beyond your own Wi-Fi.

## Troubleshooting

- **The QR code expired** — pairing codes are short-lived. Click **New code** in the desktop dialog and scan the fresh one.
- **The link says it expired or was already used** — pairing links are single-use. Create a new code.
- **"Pairing is unavailable right now"** — the desktop's background service is disabled. Run `opencode service unset disabled` and reopen the dialog.
- **Remote access won't turn on** — the tunnel takes up to a minute to connect. Leave the dialog open; if it fails, click **Try again**.
- **Sessions load slowly on mobile data** — every page comes over the tunnel, so the first refresh can take a few seconds; later loads are faster.

## Build from source

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

Tests run from the module directory; Gradle's configuration cache is disabled because AGP and the Kotlin serialization plugin cannot store it reliably.

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

The app also accepts the desktop's scanner payload (`{"code":"…","urls":["…"]}`), a pairing link (`https://host:4096/auth/connect/<code>`), or a bare code together with the server address you typed.

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

- Paired phones reach the computer through an end-to-end encrypted tunnel; turning **Remote access** off revokes that path until you pair again.
- The pairing token is stored in app-private preferences (`allowBackup` is off).
- The app never reads provider credentials from the catalog API, and providers' API keys are never requested, displayed, or saved.
- Manual connections use cleartext HTTP unless you put HTTPS in front of the server. Prefer pairing or a trusted network.

## Stack

Kotlin · Jetpack Compose · Material 3 · OkHttp · kotlinx.serialization · Coil · ZXing
