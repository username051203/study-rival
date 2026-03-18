# Study Rival — Protocol Omega: App Documentation

## What the App Does

Study Rival is a **competitive study tracker** where you race against a simulated rival called **Alicia**. You log your study sessions, and Alicia automatically accumulates progress in the background based on a configurable engine. The dashboard shows who is ahead in chapters completed, by how much, and whether you're winning or losing the day.

The entire app is a single `index.html` file — ~2000 lines of vanilla HTML, CSS, and JavaScript with no frameworks or dependencies except LZString for compression. All data is stored locally in **IndexedDB** and never leaves your device.

---

## Pages

### 📊 Dashboard (`dash`)
The main screen. Shows:
- Your total chapters and hours vs Alicia's
- Who is winning today and by how much
- A live updating rival status (ahead / behind / tied)
- Today's study time breakdown
- A random motivational quote

### 📝 Log Session (`log`)
Form to commit a study session:
- Subject (text input with autocomplete from your registry)
- Chapters completed
- Hours spent
- Start/end time
- Auto-registers new subjects you type

### 🧠 Mastery Matrix (`mastery`)
Analytics view showing:
- Per-subject chapters and hours totals
- Progress bars and efficiency metrics
- Year/month/day breakdown of your study history

### 📁 Chronicle Archive (`archive` → `drill`)
Calendar-style history. Tap any day to drill into:
- Every session logged that day
- Alicia's sessions that day
- Win/loss result
- Option to edit or delete sessions
- Option to delete the entire day

### ⚙️ Engine Config (`strat`)
Configure the rival simulation:
- **Subject Registry** — add/remove subjects you track
- **Rival speed** — chapters per hour Alicia does
- **Engine speed** — real-time (1×) or simulation (up to 3600×)
- **Multi-subject mode** — split Alicia's time across multiple subjects with custom percentages
- **Routine constraints** — Alicia's sleep schedule, meal breaks, buffer times

### 👤 Profile (`profile`)
Customize names, taglines, and photos for:
- Yourself
- Alicia (default, winning, and losing photos)

### 🔄 Kernel Sync (`sync`)
Export and import your entire state as a compressed base64 string. On Android uses native share sheet / file picker.

---

## How the Rival Engine Works

Alicia's progress is driven by a `update()` loop that runs every second (or faster in sim mode).

**Key concepts:**
- **Study day anchor**: 03:30 AM. A "day" runs from 3:30 AM to 3:30 AM the next morning so late-night sessions count for the same day.
- **Active hours**: Alicia only studies during non-sleep, non-meal, non-buffer windows defined in her routine.
- **Rate**: Configured as hours-per-chapter, stored internally as chapters-per-hour.
- **Elastic boost**: When Alicia is behind you, her speed increases up to 1.30× to keep the competition tight.
- **Multi-subject mode**: Alicia's time is split across subjects by percentage. Each subject accumulates independently.

**Engine modes:**
- `spd = 1` → real-time. Alicia progresses at her natural rate.
- `spd > 1` → simulation. Time is compressed. Useful for testing.

---

## State Structure

All app data lives in a single object stored in IndexedDB (store: `state`, key: `1`):

```javascript
{
  u: {},                    // per-subject totals: { SubjectName: { ch, h } }
  subjects: [],             // subject registry: ['Physics', 'Maths', ...]
  logs: [],                 // your sessions (packed format)
  aLogs: [],                // alicia's sessions (packed format)
  history: {},              // per-day results: { "2026-03-08": { win, diff, uCh, aCh } }

  rival: {
    s: 'Physics',           // primary subject
    g: 10,                  // goal chapters
    cur: 0,                 // alicia's current chapter count today
    rate: 0.2,              // chapters per hour
    multiSub: false,
    multiSubjects: [],      // [{ s, rate, pct }]
    _mCur: {}               // per-subject today accumulator
  },

  engine: {
    start: <timestamp>,
    spd: 60,                // simulation speed multiplier
    uBase: 0,
    lastResetDay: ""
  },

  rt: {                     // routine constraints
    slp: [{ s, e }],        // sleep windows
    ml: [hrs],              // meal times
    bf: [{ s, e }]          // buffer windows
  },

  tick: <timestamp>,
  lastDayReport: "",
  aliciaStats: { dailyTotal, activeHours, efficiency },

  profiles: {
    user: { name, tagline, photo },
    alicia: { name, tagline, photoDefault, photoWin, photoLoss }
  }
}
```

**Session log format (packed):**
```javascript
{ s: "Physics", h: 1.5, c: 3, tS: "08:00", tE: "09:30", d: "2026-03-08" }
```

---

## Android Bridge

On Android, the app detects `window.Android` and uses native features:

```javascript
// Pick a photo from gallery
Android.openImagePicker('["imgId1","imgId2"]');
// → calls back: onPhotoPicked(dataUrl, idsJson)

// Pick a file
Android.openFilePicker('text/plain');
// → calls back: onFileImported(content)

// Share text via Android share sheet
Android.shareText(text, 'filename.txt');

// Toast notification
Android.showToast('Saved!');
```

All methods fall back gracefully to browser equivalents when `window.Android` is undefined.

---

## Key Functions Reference

| Function | Purpose |
|---|---|
| `boot()` | App entry point — opens DB, loads state, starts engine |
| `update()` | Core loop — advances Alicia's progress, checks day resets |
| `commitData()` | Saves a user study session to logs and state |
| `recalcAlicia()` | Reconstructs Alicia's correct total from aLogs on boot |
| `recomputeSubjectTotals()` | Rebuilds `state.u` from scratch from all logs |
| `getStudyDay(ts)` | Returns study-day string for a timestamp (anchored at 03:30) |
| `isAliciaActive(decH)` | Returns true if Alicia should be studying at given decimal hour |
| `renderStratPage()` | Renders entire Engine Config page |
| `refreshDatalist()` | Syncs all subject autocomplete inputs from `state.subjects` |
| `show(page)` | Navigate to a page |
| `save()` | Debounced IndexedDB write (400ms) |
| `expB64_native()` | Export state — uses Android share or clipboard |
| `impB64_native()` | Import state — uses Android file picker or text input |

---

## Important Implementation Notes

**`confirm()` does not work in Android WebView.** It silently returns `false`. Never use it for delete confirmations or any critical logic. Use a custom modal or delete directly.

**Template literals with quotes in HTML `onclick` attributes will break.** Use `data-*` attributes instead:
```html
<!-- ❌ Breaks -->
<button onclick="fn('${s.replace(/'/g, "\\'")}')">

<!-- ✅ Safe -->
<button onclick="fn(this.dataset.name)" data-name="${s}">
```

**One JS syntax error kills the entire app.** Always validate before pushing:
```bash
python3 -c "
import re
with open('index.html') as f: h = f.read()
scripts = re.findall(r'<script(?![^>]*src)[^>]*>(.*?)</script>', h, re.DOTALL)
open('/tmp/t.js','w').write('\n'.join(scripts))
" && node /tmp/t.js 2>&1 | grep -v "not defined"
```

**`backdrop-filter: blur()` blocks touches in Android WebView** even when the element is hidden. Remove it from overlays.

---

## Customizing for Your Own App

To use this wrapper for a different HTML app:

1. Replace `app/src/main/assets/index.html` with your app
2. Change `applicationId` in `app/build.gradle` to your own package name
3. Replace icons in `app/src/main/res/mipmap-*/`
4. Change `android:label` in `AndroidManifest.xml` to your app name
5. Optionally add/remove JS bridge methods in `MainActivity.java`
6. Push — GitHub Actions builds and releases automatically
