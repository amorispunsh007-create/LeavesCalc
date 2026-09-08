# My Leave Tracker (Android)

A simple offline app to track your office leave balance and history, set up to match Goodyear's leave policy (EL 18 / CL 7 / SL 7). Everything is stored locally on your device — no internet or backend required.

## Build it in the cloud (no local install) — GitHub Codespaces

1. **Push this project to a GitHub repo**
   Create a new repo on GitHub, then upload all these files (keeping the folder structure — `.devcontainer/`, `app/`, etc.) either by dragging them into GitHub's web uploader or via `git push`.

2. **Open it in a Codespace**
   On the repo page: green **Code** button → **Codespaces** tab → **Create codespace on main**.
   This spins up a full Linux dev environment in your browser. The first launch will run the setup script automatically (installs the Android SDK) — this takes a few minutes, watch the terminal at the bottom.

3. **Generate the Gradle wrapper** (first time only)
   In the Codespace terminal:
   ```bash
   gradle wrapper --gradle-version 8.7
   ```

4. **Build the APK**
   ```bash
   ./gradlew assembleDebug
   ```
   When it finishes, your APK is at:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

5. **Get the APK onto your phone**
   In the VS Code file explorer (left sidebar in the Codespace), right-click `app-debug.apk` → **Download**. Then transfer it to your phone (email it to yourself, or use a cloud drive) and open it to install — you'll need to allow "install unknown apps" for whichever app you use to open it.

Gitpod works the same way if you prefer it: same repo, same commands, just open via gitpod.io/#your-repo-url instead of the Codespaces button — you'll need to install the Android SDK the same way (Gitpod also lets you commit a `.gitpod.yml` init script if you want that automated too, just ask).

## Project structure
```
settings.gradle.kts        — declares the app module
build.gradle.kts           — root plugin versions
app/build.gradle.kts       — dependencies (Compose, DataStore)
app/src/main/AndroidManifest.xml
app/src/main/java/com/example/leavetracker/MainActivity.kt  — all the app code
.devcontainer/             — Codespaces auto-setup (Android SDK)
```

## Features
- Balance cards for Earned Leave (EL, 18) / Casual Leave (CL, 7) / Sick Leave (SL, 7) — quotas are editable anytime
- Add a leave entry: type, start date, end date (days auto-calculated), optional reason
- Reminder shown when logging Sick Leave beyond 2 consecutive days (matches the Medical Certificate rule)
- History list with delete
- Data persists across app restarts (via DataStore)

## Not yet built in (tell me if you want these next)
- EL carry-forward cap (30 days) and CL/SL lapsing at year-end
- Pro-rata EL accrual (~1.5 days/month) instead of full quota from day one
- Leave encashment calculator (`Basic Salary / 26 × EL balance`) for final settlement
