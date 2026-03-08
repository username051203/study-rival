# Study Rival — Android WebView APK Wrapper

A guide for wrapping any single-file HTML/JS web app as a native Android APK using GitHub Actions for automated builds. No Android Studio required. Built and debugged entirely from Termux on Android.

---

## What This Is

This repo wraps a single `index.html` file into a native Android APK using a minimal Java WebView shell. GitHub Actions automatically builds and publishes the APK to GitHub Releases on every push. You download the APK directly to your phone.

**Stack:**
- Single-file HTML/CSS/JS app (no framework, no bundler)
- Android WebView (Java, no Kotlin)
- IndexedDB for persistent storage
- GitHub Actions for CI/CD builds
- Signed release APK (same signature every build = no reinstall conflicts)

---

## Project Structure

```
study-rival/
├── app/
│   └── src/main/
│       ├── assets/
│       │   └── index.html          ← Your entire app goes here
│       ├── java/com/studyrival/omega/
│       │   └── MainActivity.java   ← WebView host + JS bridge
│       ├── AndroidManifest.xml
│       └── res/
│           ├── layout/activity_main.xml
│           ├── values/styles.xml
│           ├── xml/file_provider_paths.xml
│           └── mipmap-*/           ← App icons (all densities)
├── .github/
│   └── workflows/
│       └── build.yml               ← CI/CD pipeline
├── app/build.gradle
├── build.gradle
├── settings.gradle
├── gradle.properties
└── gradle/wrapper/
    └── gradle-wrapper.properties
```

---

## Setup From Scratch (Termux)

### Prerequisites

```bash
pkg update -y && pkg upgrade -y
pkg install -y git gh curl python
pip install Pillow --break-system-packages
termux-setup-storage   # tap Allow
```

### 1. Configure Git

```bash
git config --global user.name "YourName"
git config --global user.email "your@email.com"
git config --global credential.helper store
git config --global init.defaultBranch main
```

### 2. Get a GitHub Personal Access Token

1. GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Generate new token → check `repo` and `workflow`
3. Copy it — shown only once

```bash
export GH_TOKEN=ghp_YOURTOKEN
export GH_USER=YOURUSERNAME
echo "export GH_TOKEN=ghp_YOURTOKEN" >> ~/.bashrc
echo "export GH_USER=YOURUSERNAME" >> ~/.bashrc
```

### 3. Create GitHub Repo via CLI

```bash
curl -s -X POST \
  -H "Authorization: token $GH_TOKEN" \
  https://api.github.com/user/repos \
  -d '{"name":"study-rival","private":false}' | grep full_name
```

### 4. Clone / Extract Project

```bash
cp /sdcard/Download/studyrival2.zip ~
unzip ~/studyrival2.zip
cd ~/studyrival2
```

### 5. Generate a Signing Keystore (Once Only)

This gives your APK a permanent identity. Every update will be signed with the same key, so Android won't ask you to uninstall before updating.

```bash
keytool -genkeypair -v \
  -keystore ~/studyrival.keystore \
  -alias studyrival \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -storepass studyrival123 \
  -keypass studyrival123 \
  -dname "CN=StudyRival, O=StudyRival, C=US"
```

> ⚠️ Keep `studyrival.keystore` safe. If you lose it you can't update your app — you'd need to uninstall and reinstall fresh.

### 6. Upload Keystore as GitHub Secrets

```bash
base64 ~/studyrival.keystore | tr -d '\n' | \
  gh secret set KEYSTORE_BASE64 --repo ${GH_USER}/study-rival

gh secret set KEYSTORE_PASS --repo ${GH_USER}/study-rival --body "studyrival123"
gh secret set KEY_ALIAS      --repo ${GH_USER}/study-rival --body "studyrival"
gh secret set KEY_PASS       --repo ${GH_USER}/study-rival --body "studyrival123"
echo "Secrets set"
```

### 7. Push and Build

```bash
git init
git add .
git commit -m "init"
git branch -M main
git remote add origin https://${GH_USER}:${GH_TOKEN}@github.com/${GH_USER}/study-rival.git
git push -u origin main
```

Watch the build:
```bash
sleep 30 && gh run list --repo ${GH_USER}/study-rival --limit 1
```

### 8. Download APK

```bash
curl -L -H "Authorization: token $GH_TOKEN" \
  "https://github.com/${GH_USER}/study-rival/releases/download/latest/StudyRival.apk" \
  -o /sdcard/Download/StudyRival.apk && echo "DONE"
```

Open Files → Downloads → StudyRival.apk → Install.
*(Allow "Install from unknown sources" if prompted)*

---

## Updating the App

Whenever you have a new `index.html`:

```bash
cp /sdcard/Download/index.html ~/studyrival2/app/src/main/assets/index.html

# Always validate JS before pushing
python3 -c "
import re
with open('app/src/main/assets/index.html') as f: html = f.read()
scripts = re.findall(r'<script(?![^>]*src)[^>]*>(.*?)</script>', html, re.DOTALL)
with open('/tmp/test.js', 'w') as f: f.write('\n'.join(scripts))
"
node /tmp/test.js 2>&1 | head -5
# If blank output = no JS errors. Then push:

git add .
git commit -m "update app"
git push
```

Wait ~3 minutes, then download with the same curl command above.

---

## Changing the App Icon

```bash
# Save your icon image to ~/studyrival2/appicon.png first, then:
python3 << 'EOF'
from PIL import Image
import os

img = Image.open(os.path.expanduser('~/studyrival2/appicon.png')).convert("RGBA")
sizes = {
    'mipmap-mdpi': 48, 'mipmap-hdpi': 72, 'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144, 'mipmap-xxxhdpi': 192
}
base = os.path.expanduser('~/studyrival2/app/src/main/res')
for folder, size in sizes.items():
    r = img.resize((size, size), Image.LANCZOS)
    r.save(f'{base}/{folder}/ic_launcher.png')
    r.save(f'{base}/{folder}/ic_launcher_round.png')
print("Icons done")
EOF

git add . && git commit -m "update icon" && git push
```

---

## Android JS Bridge

`MainActivity.java` exposes a native `window.Android` object to your HTML/JS with these methods:

| Method | What it does |
|---|---|
| `Android.openImagePicker(idsJson)` | Opens system photo picker. Calls `onPhotoPicked(dataUrl, idsJson)` when done |
| `Android.openFilePicker(mimeType)` | Opens file picker. Calls `onFileImported(content)` when done |
| `Android.shareText(text, filename)` | Opens Android share sheet |
| `Android.showToast(message)` | Shows a toast notification |

Your HTML should check before calling:
```javascript
if (typeof Android !== 'undefined') {
    Android.showToast('Hello from JS!');
} else {
    // fallback for browser
}
```

Back button is handled by adding this function to your JS:
```javascript
function onAndroidBack() {
    // called when user presses hardware back button
    show('home'); // or whatever your nav function is
}
```

---

## Debugging Build Failures

```bash
# Get the latest run ID
RUN_ID=$(gh run list --repo ${GH_USER}/study-rival --limit 1 --json databaseId -q '.[0].databaseId')

# Get the error
gh run view ${RUN_ID} --repo ${GH_USER}/study-rival --log-failed 2>&1 | \
  grep "What went wrong" -A3
```

### Common Errors and Fixes

| Error | Fix |
|---|---|
| `Cannot mutate dependencies` | Wrong Gradle version. Use `gradle/actions/setup-gradle@v3` in workflow |
| `DependencyHandler.module() NoSuchMethodError` | System Gradle 9.x being used. Make sure `gradle-wrapper.jar` is not empty |
| `Duplicate class kotlin.collections` | `androidx.core` pulling in conflicting Kotlin stdlib. Remove it — `appcompat` includes it |
| `Tag number over 30 is not supported` | Keystore corrupted during base64. Regenerate and re-upload |
| `useAndroidX not enabled` | Missing `gradle.properties`. Add `android.useAndroidX=true` |
| JS syntax error / nothing works | A JS syntax error crashes ALL scripts. Extract and test with `node` before pushing |

---

## Critical Lessons Learned

**Always validate JS before pushing:**
```bash
python3 -c "
import re
with open('app/src/main/assets/index.html') as f: h = f.read()
scripts = re.findall(r'<script(?![^>]*src)[^>]*>(.*?)</script>', h, re.DOTALL)
open('/tmp/t.js','w').write('\n'.join(scripts))
" && node /tmp/t.js 2>&1 | grep -v "not defined" | head -5
```
A single syntax error in any `<script>` block kills all JavaScript — buttons, menus, everything stops working.

**Avoid `confirm()` and `alert()` in WebView:**
Android WebView blocks `confirm()` and `alert()` dialogs by default — they silently return `false`/`undefined`. Never use them for critical logic like delete confirmations.

**Template literals with quotes are dangerous in HTML attributes:**
```javascript
// ❌ BROKEN — escaping breaks inside template literals in HTML attributes
`<button onclick="fn('${s.replace(/'/g, "\\'")}')">` 

// ✅ SAFE — use data attributes instead
`<button onclick="fn(this.dataset.name)" data-name="${s}">`
```

**Keep `gradle-wrapper.jar` non-empty OR use `gradle/actions`:**
If `gradle-wrapper.jar` is 0 bytes, GitHub Actions falls back to system Gradle (currently 9.x) which breaks AGP 8.x in various ways. Either commit a real jar or use the `gradle/actions/setup-gradle` action.

**Don't use `backdrop-filter` in Android WebView:**
`backdrop-filter: blur()` can block touch events on elements beneath it even when the element is hidden. Remove it entirely from overlays.

---

## Build Configuration

Working combination as of March 2026:

| Component | Version |
|---|---|
| AGP (Android Gradle Plugin) | 8.1.0 |
| Gradle | 8.0 (via `gradle/actions/setup-gradle@v3`) |
| JDK | 17 |
| `compileSdk` / `targetSdk` | 34 |
| `minSdk` | 24 (Android 7.0+, covers ~97% of devices) |
| `androidx.appcompat` | 1.6.1 |
| `android.enableJetifier` | false (pure AndroidX, no legacy support lib) |

---

## GitHub Actions Workflow Summary

On every push to `main`:
1. Checks out code (~5s)
2. Sets up JDK 17 (~15s)
3. Sets up Gradle 8.0 (~30s, cached after first run)
4. Decodes keystore from secret
5. Builds signed release APK (~90s)
6. Deletes old `latest` release
7. Creates new `latest` release with APK attached
8. Runner shuts down

Total: ~3–4 minutes. Public repos get unlimited free minutes.

Your APK is always available at:
```
https://github.com/YOURUSERNAME/study-rival/releases/download/latest/StudyRival.apk
```
