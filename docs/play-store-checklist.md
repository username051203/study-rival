# Play Store Submission Checklist

Everything below either needs your Play Console account directly, or your
judgment call — I can't submit these for you, but here's exactly what to
enter.

## 1. Data Safety form (Play Console → App content → Data safety)

Answer based on what the app actually does:

**Does your app collect or share any of the required user data types?**
→ Yes

**Data types to declare:**
| Data type | Collected? | Shared? | Purpose |
|---|---|---|---|
| Device or other IDs (Advertising ID) | Yes | Yes (with Google/AdMob) | Advertising |
| App activity (app interactions, in-app search history — from the quiz feature if used) | Optional/Yes | Yes (with Groq, only if user enables Practice Quiz) | App functionality |

**Is all user data encrypted in transit?** → Yes (HTTPS to Groq and AdMob)

**Do you provide a way for users to request data deletion?** → Since there's
no account/server-side storage, you can state data is stored locally and
deleted when the user uninstalls the app or clears app data. For the Groq
key specifically, clearing it is available in-app (the "Clear" button on
the quiz tab).

**Data safety summary text you can reuse:**
> This app stores study data locally on your device. It shows ads via
> Google AdMob, which may collect an advertising ID. An optional AI
> practice-question feature sends data to Groq's API only if you provide
> your own API key.

## 2. Content rating questionnaire (Play Console → App content → Content rating)

Expected answers for this app:
- Violence: None
- Sexual content: None
- Profanity: None
- Controlled substances: None
- Gambling: None
- User-generated content: No (no chat/social features between real users)
- Shares location: No
- Ads: **Yes** (must declare this — triggers different rating logic in
  some regions but doesn't push the rating up on its own)

Likely result: **Everyone / PEGI 3** — but the questionnaire result is
authoritative, not this guide.

## 3. Ads declaration
Play Console → App content → Ads → "Yes, my app contains ads."

## 4. Target audience
Since the app isn't directed at children: select the appropriate adult/teen
age range in Play Console, and answer "No" to "is this app primarily
directed at children." This affects which ad SDK behaviors are allowed
(e.g. no child-directed treatment needed for AdMob).

## 5. Privacy policy URL
Once you enable GitHub Pages for this repo (Settings → Pages → Source:
`main` branch, `/docs` folder), your privacy policy will be live at:

```
https://username051203.github.io/study-rival/privacy-policy.html
```

Paste that URL into Play Console → App content → Privacy policy, and into
the store listing.

## 6. Before you hit publish
- [ ] Real AdMob App ID in `AndroidManifest.xml` (currently Google's test ID)
- [ ] Real AdMob ad unit ID in `MainActivity.java` (currently Google's test ID)
- [ ] Build and test the `.aab` from CI installs and runs correctly
- [ ] App icon + feature graphic + screenshots uploaded
- [ ] Signed with your real release keystore (not debug)
- [ ] Version code/name bumped appropriately for the release you're submitting
