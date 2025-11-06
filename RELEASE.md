# Release APK Build Guide

This guide explains how to build a release APK for the SMA Alerts Android app for personal use.

## Building the Release APK

### Command

From the project root:
```bash
cd android
./gradlew :app:assembleRelease
```

Or as a single command:
```bash
cd android && ./gradlew :app:assembleRelease
```

### Output Location

After building, the release APK can be found at:
```
android/app/build/outputs/apk/release/SMA-Alerts-v{VERSION}-release.apk
```

**Example:** `SMA-Alerts-v0.1.0-release.apk`

### Quick Access

Open the APK file directly:
```bash
open android/app/build/outputs/apk/release/SMA-Alerts-v0.1.0-release.apk
```

Or use wildcard to find the latest:
```bash
open android/app/build/outputs/apk/release/SMA-Alerts-*.apk
```

## APK Details

- **File Name:** `SMA-Alerts-v{VERSION}-release.apk` (includes version number)
- **Signed:** Yes (with debug keystore for personal use)
- **Optimized:** Yes (release build)

## Debug vs Release APK

### Debug APK
- **Location:** `android/app/build/outputs/apk/debug/app-debug.apk`
- **Build Command:** `./gradlew :app:assembleDebug`
- **Includes:** Debug symbols, not optimized

### Release APK
- **Location:** `android/app/build/outputs/apk/release/SMA-Alerts-v{VERSION}-release.apk`
- **Build Command:** `./gradlew :app:assembleRelease`
- **Includes:** Optimized, signed, production-ready
- **Filename Format:** `SMA-Alerts-v{versionName}-release.apk`

## Installation on Android Device

### Option 1: Using ADB (Android Debug Bridge)

1. **Enable Developer Options** on your Android device:
   - Go to **Settings** → **About Phone**
   - Tap **"Build Number"** 7 times
   
2. **Enable USB Debugging**:
   - Go to **Settings** → **Developer Options**
   - Enable **"USB Debugging"**

3. **Connect device via USB** and install:
   ```bash
   cd android
   adb install app/build/outputs/apk/release/SMA-Alerts-v0.1.0-release.apk
   ```
   
   Or use wildcard to install the latest version:
   ```bash
   adb install app/build/outputs/apk/release/SMA-Alerts-*-release.apk
   ```

### Option 2: Transfer and Install Manually

1. **Transfer the APK** to your device:
   - Email it to yourself
   - Upload to cloud storage (Google Drive, Dropbox, etc.)
   - Use USB file transfer
   - Use AirDrop or similar file sharing

2. **Install on device**:
   - Open the APK file on your Android device
   - If prompted, allow **"Install from Unknown Sources"**
   - Tap **"Install"**
   - Wait for installation to complete
   - Tap **"Open"** to launch the app

## Versioning

### Current Version

The app version is configured in `android/app/build.gradle`:

```gradle
defaultConfig {
    versionCode 1
    versionName "1.0"
}
```

### Understanding Version Numbers

- **versionCode** (integer): Internal version number used by Android
  - Must be incremented for each release
  - Used by Google Play Store and Android system to determine which version is newer
  - Example: 1, 2, 3, 4, ...
  
- **versionName** (string): Human-readable version displayed to users
  - Can be any string (e.g., "1.0", "1.0.1", "2.0-beta")
  - Follows semantic versioning (Major.Minor.Patch) recommended
  - Example: "1.0", "1.1", "1.2.3", "2.0"

### How to Update Version

1. **Edit `android/app/build.gradle`**:
   ```gradle
   defaultConfig {
       versionCode 2        // Increment this number
       versionName "1.1"    // Update this string
   }
   ```

2. **Rebuild the APK**:
   ```bash
   cd android
   ./gradlew :app:assembleRelease
   ```

### Versioning Best Practices

- **Increment versionCode** for every release (even bug fixes)
- **Update versionName** following semantic versioning:
  - **Major version** (1.0 → 2.0): Breaking changes, major features
  - **Minor version** (1.0 → 1.1): New features, backward compatible
  - **Patch version** (1.0 → 1.0.1): Bug fixes, small improvements

**Example Progression:**
```
versionCode 1, versionName "1.0"   → Initial release
versionCode 2, versionName "1.0.1" → Bug fix
versionCode 3, versionName "1.1"   → New feature
versionCode 4, versionName "1.1.1" → Bug fix
versionCode 5, versionName "2.0"   → Major update
```

### Checking Current Version

You can check the installed app version on your device:
- **Settings** → **Apps** → **SMA Alerts** → **App Info**

Or programmatically, the version is displayed in the app's about section (if implemented).

### Custom APK Filename

The APK filename is automatically customized based on the version. The format is:
```
SMA-Alerts-v{versionName}-{buildType}.apk
```

**Examples:**
- Release: `SMA-Alerts-v0.1.0-release.apk`
- Debug: `SMA-Alerts-v0.1.0-debug.apk`

To customize the filename format, edit `android/app/build.gradle` and modify the `applicationVariants.all` block:
```gradle
applicationVariants.all { variant ->
    variant.outputs.all { output ->
        def versionName = variant.versionName
        def buildType = variant.buildType.name
        def appName = "SMA-Alerts"  // Change this to customize app name
        
        outputFileName = "${appName}-v${versionName}-${buildType}.apk"
    }
}
```

## Signing Configuration

The release APK is signed with a debug keystore located at:
```
android/debug.keystore
```

**Keystore Details:**
- **Store Password:** `android`
- **Key Alias:** `androiddebugkey`
- **Key Password:** `android`

> **Note:** This keystore is suitable for personal use only. For Google Play Store distribution, you'll need to create a proper release keystore and configure it in `android/app/build.gradle`.

## Building Both APKs

To build both debug and release APKs:
```bash
cd android
./gradlew :app:assembleDebug :app:assembleRelease
```

## Troubleshooting

### Build Fails with "Signing Config Not Found"
- Ensure `android/debug.keystore` exists
- If missing, the keystore will be automatically created on first build

### "Installation Blocked" Error
- Enable "Install from Unknown Sources" in Android settings
- Go to **Settings** → **Security** → **Unknown Sources** (varies by Android version)

### APK Not Found
- Run `./gradlew clean` and rebuild
- Check that the build completed successfully
- Verify the path: `android/app/build/outputs/apk/release/`
- Note: APK filename includes version (e.g., `SMA-Alerts-v0.1.0-release.apk`)

## Clean Build

To perform a clean build (removes all build artifacts):
```bash
cd android
./gradlew clean
./gradlew :app:assembleRelease
```

## GitHub Actions (CI/CD)

### Automatic Builds

The repository includes a GitHub Actions workflow that automatically builds and releases the APK whenever you push to the `main` branch.

**Workflow File:** `.github/workflows/build-and-release.yml`

### What It Does

1. **Triggers automatically** on pushes to `main` branch
2. **Builds the release APK** using the same process as local builds
3. **Creates a GitHub Release** with the APK attached
4. **Tags the release** with the version number (e.g., `v0.1.0`)
5. **Uploads the APK** as a downloadable asset

### Manual Trigger

You can also trigger the workflow manually:
1. Go to **Actions** tab in GitHub
2. Select **"Build and Release APK"** workflow
3. Click **"Run workflow"**

### Release Process

1. Push your changes to `main` branch
2. GitHub Actions automatically:
   - Builds the APK
   - Extracts version info from `build.gradle`
   - Creates a release tag (e.g., `v0.1.0`)
   - Creates a GitHub Release with the APK attached
3. Download the APK from the **Releases** page on GitHub

### Accessing Releases

- Go to your repository on GitHub
- Click **"Releases"** in the right sidebar
- Find the latest release and download the APK

### Workflow Features

- ✅ Automatic version detection from `build.gradle`
- ✅ Automatic release tagging
- ✅ APK uploaded as GitHub Release asset
- ✅ APK also available as workflow artifact (30 days retention)
- ✅ Release notes include version info and commit links
- ✅ Detects if version hasn't changed and updates existing release
- ✅ Warns when updating existing release with same version

### Important: Version Management

**If you don't change the version before pushing:**
- The existing release with the same version will be **updated** (not replaced)
- The APK file will be **replaced** with the new build
- A warning will be shown in the release notes
- Users who already downloaded won't know there's a new build

**Best Practice:**
- Always increment `versionCode` in `build.gradle` before pushing
- Update `versionName` if you want a new release tag
- This ensures each build gets its own release and is trackable

**Example:**
```gradle
// Before pushing new changes
versionCode 1  → versionCode 2  ✅
versionName "0.1.0" → versionName "0.1.1"  ✅
```

## Additional Resources

- **Lint Report:** `android/app/build/reports/lint-results-debug.html`
- **Test Reports:** `android/app/build/reports/tests/`
- **Build Logs:** Check console output for any warnings or errors
- **GitHub Actions:** `.github/workflows/build-and-release.yml`

