# নিরাময় · NIRAMOY

**A free, open-source, Bangla-first medicine reminder for elderly people who may not read or write well.**

The app speaks: at medicine time it says *"সকালের খাবারের পরে ওষুধ খান"* and shows big
**খেয়েছি** (taken) and **পরে বলো** (later) buttons, even on a locked phone. Family members can set
everything up, and everything is backed up to the cloud.

> ⚠️ NIRAMOY only reminds you. It is **not medical advice**.

[![Release APK](https://github.com/shakib-mehrab/niramoy/actions/workflows/release.yml/badge.svg)](https://github.com/shakib-mehrab/niramoy/actions/workflows/release.yml)
License: MIT (code). See [Third-party licences](#third-party-licences).

---

## Download

Get the latest **`niramoy-vX.Y.Z.apk`** from [Releases](https://github.com/shakib-mehrab/niramoy/releases).
On the phone, allow "Install unknown apps" for your browser or file manager, open the APK, and then follow
**ফোন ঠিক করে নিন** (phone setup) inside the app.

Needs Android 7.0 or newer. It's built to work well on small, low-end phones.

## Features

- **Bangla by default** with a বাংলা / EN switch on every screen, Bangla numerals, large text (20sp+),
  56dp+ buttons, and icons together with words. Colour is never the only signal.
- **Medicines:** add one, add many at once, or take a **photo of a prescription**. The photo is read on the phone and
  you always check the result before anything is saved. Each medicine has a dose, morning / noon / night,
  before / after meal, and runs either ongoing or as a course of N days that ends automatically. You can stop, resume, edit or delete it.
- **Automatic reminder times** from her routine (meal time ± minutes). Alarms are exact, show full screen
  on the lock screen, and **repeat until answered** (count and gap are configurable). Unanswered doses are logged as **missed**.
- **Spoken Bangla / English tones** for each meal slot, a generic phrase, a beep, or the phone's alarm sound.
- **Adherence:** taken / missed / skipped with times, weekly dots, and 7 / 30-day percentages.
- **Body log:** daily mood (5 faces), blood sugar (fasting / after meal), blood pressure + pulse, weight
  and temperature, with simple charts and low / normal / high labels. **Speak instead of typing:** "সুগার ১২০, প্রেশার ১৩০ বাই ৮০".
- **Records:** doctor visits (with a next-visit reminder), a prescription and report gallery (photos / PDF),
  a doctor phone book, blood group, allergies and emergency contacts.
- **Emergency button:** opens the phone's own dialer, a WhatsApp message in Bangla (optionally with a map link),
  or 999.
- **Offline first + cloud backup:** everything works with no internet and syncs later. Reinstall, sign in,
  and all data and history come back.
- **Automatic updates:** when a new version is released here, the app shows a big "নতুন আপডেট এসেছে" dialog.
  One tap downloads it, checks it (SHA-256, same package, same signing key) and opens Android's installer.

## Privacy: please read

The APK published here uses **the maintainer's free Firebase and Supabase projects**:

- Each user's data lives under their own account. Security rules (`firestore.rules`,
  `supabase/storage-policies.sql`) block every other *user* from reading it.
- The project owner can technically see stored data through the Firebase/Supabase consoles. Data isn't
  end-to-end encrypted. If that isn't acceptable, **build your own copy with your own free backend**
  (steps below), which takes about 20 minutes.
- No ads, no analytics, no tracking. Location is only read when you press the WhatsApp emergency button with
  "send location" turned on.
- Using it without an account is fine; data then stays only on that phone.

**Shared free-tier limits.** Firestore Spark allows 50k reads and 20k writes a day and 1 GiB of storage.
Supabase Free allows 1 GB of files and 5 GB of downloads a month. That's plenty for a few hundred families.
If a limit is reached, the app keeps working on the phone and syncs once the quota resets.

**Supabase's 7-day pause** (free projects pause after a week with no activity) is handled automatically:
- every installed app calls a tiny `ping()` function once a day (`KeepAliveWorker`), and
- a GitHub Action ([supabase-keepalive.yml](.github/workflows/supabase-keepalive.yml)) pings every 3 days as a backup.

If it ever pauses anyway, files wait safely on the phone and upload after the project is restored.
Nothing is lost, and reminders never depend on the internet.

---

## How it works

| Area | Implementation |
|---|---|
| UI | Kotlin, Jetpack Compose (Material 3), one `MainViewModel`, `navigation-compose` |
| Data | **Room** on the phone is the source of truth, mirrored to **Firestore** `users/{uid}/…`. The offline queue and live listeners keep them in step, and the newest edit wins per row |
| Auth | Firebase email/password + Google (Credential Manager). Phone OTP isn't free on new projects |
| Files | **Supabase Storage**, authenticated with the user's Firebase ID token. Since Feb 2026, Firebase Storage needs the paid Blaze plan. Images are compressed to at most 1600 px, JPEG quality 75, and uploaded by WorkManager when online |
| Reminders | `AlarmManager.setAlarmClock` (unaffected by Doze), one alarm per dose group (date × slot × before/after meal), a repeat-until-answered state that survives reboots, rebuilt on boot / update / time change / midnight / any data change. A foreground service plays the tone on the **alarm** stream, plus a full-screen lock-screen activity |
| OCR | **Tesseract** (eng + ben, Apache-2.0) on the phone, a rule-based prescription parser, and a mandatory review screen |
| Voice | Android's built-in speech recognizer (`bn-BD`), parsed by fixed rules (Bangla/English digits, সুগার / প্রেশার / বাই …), then a confirmation screen |
| Tones | Generated at build time with **Meta MMS-TTS** (`facebook/mms-tts-ben`, `-eng`) by `tools/tts/generate_tones.py` |
| Strings | `i18n/StringsBn.kt` + `StringsEn.kt`. A unit test fails if any key is missing |

```
app/src/main/java/com/niramoy/
  NiramoyApp.kt        reschedules alarms on any data change; schedules the keep-alive
  MainActivity.kt      navigation + 4 big tabs (আজ, ওষুধ, শরীর, কাগজপত্র)
  alarm/               ReminderScheduler, ReminderNotifier, channels, tones
  receiver/            ReminderReceiver (fire/taken/snooze/missed/visit/test), BootReceiver
  service/             ReminderSoundService (foreground, alarm stream)
  data/                AppRepository (Room <-> Firestore, auth), FileStorage + UploadWorker, KeepAliveWorker
  data/local/          Room entities + DAOs
  model/               DoseSchedule (pure, unit-tested scheduling rules)
  parse/               VoiceParser, PrescriptionParser
  ocr/                 OcrEngine (Tesseract)
  i18n/                Strings (bn / en)
  ui/                  screens, components, theme, ReminderActivity
tools/tts/             tone generator
supabase/              bucket, policies, ping()
.github/workflows/     release on tag, Supabase keep-alive
```

---

## Build your own copy (all free, no credit card)

Requirements: **JDK 17**, the **Android SDK** (Android Studio, or command-line tools), and Python 3.10+ (only for the tones).

### 1. Firebase (sign-in + database), Spark plan
1. Go to https://console.firebase.google.com and choose **Add project** (Analytics off).
2. Under **Authentication → Sign-in method**, enable **Email/Password** and **Google**.
3. Under **Firestore Database → Create database**, choose **Production mode** and a nearby region.
4. Under **Project settings → Your apps → Add Android app**, use package `com.niramoy.app`. Add the **SHA-1** of your
   debug key and of your release key (get them with `./gradlew signingReport` or `keytool -list -v -keystore <file>`).
   Google sign-in only works for keys listed here.
5. Download **`google-services.json`** to `app/google-services.json` (it's git-ignored).
6. Publish the rules. You can paste `firestore.rules` into **Firestore → Rules**, or use the CLI:
   ```bash
   npx firebase-tools login
   ```
   ```bash
   npx firebase-tools deploy --only firestore:rules --project YOUR_FIREBASE_PROJECT_ID
   ```

Without `google-services.json` the app still builds and runs in phone-only mode.

### 2. Supabase (prescription and report files), Free plan
1. Go to https://supabase.com and create a **New project**.
2. Under **Authentication → Sign In / Providers → Third-party auth**, choose **Add provider → Firebase** and enter your Firebase project ID.
3. Open the **SQL Editor**, paste [`supabase/storage-policies.sql`](supabase/storage-policies.sql), replace `niramoy-5377a`
   with your Firebase project ID (2 places), and press **Run**. This creates the private bucket, the per-user policy and `ping()`.
4. Copy `.env.example` to `.env` and fill in **Project URL** and the **anon public** key from **Project Settings → API**.

The anon key is public by design. The bucket policy only lets a signed-in user touch the `<their uid>/` folder.

### 3. Voice tones (optional; already committed)
```bash
python -m venv tools/tts/.venv
```
```bash
tools/tts/.venv/Scripts/pip install torch --index-url https://download.pytorch.org/whl/cpu
```
```bash
tools/tts/.venv/Scripts/pip install "transformers>=4.40" numpy
```
```bash
tools/tts/.venv/Scripts/python tools/tts/generate_tones.py
```
On macOS / Linux, use `.venv/bin/` instead of `.venv/Scripts/`. Edit the phrases in `PHRASES` in the script.
To use a family member's own recorded voice, drop WAV/OGG files with the **same names** into `app/src/main/res/raw/`.

### 4. Offline OCR from day one (optional)
```powershell
pwsh scripts/fetch-tessdata.ps1
```
This bundles the eng + ben models (about 5 MB) into the APK. Without it, they download once on first use.

### 5. Build and run
```bash
./gradlew :app:testDebugUnitTest
```
```bash
./gradlew :app:installDebug
```

### 6. Signed release
Create a key once and **back it up**. If you lose it, users can't update the app; they have to uninstall and reinstall.
```bash
keytool -genkeypair -v -keystore niramoy-release.jks -alias niramoy -keyalg RSA -keysize 2048 -validity 10000
```
Then create `keystore.properties` in the project root (git-ignored):
```
storeFile=niramoy-release.jks
storePassword=...
keyAlias=niramoy
keyPassword=...
```
```bash
./gradlew :app:assembleRelease
```
The APK is written to `app/build/outputs/apk/release/app-release.apk`.

### 7. Automatic GitHub releases ([RELEASING.md](RELEASING.md))
[`release.yml`](.github/workflows/release.yml) tests, builds, signs and publishes the APK whenever a `v*` tag is pushed.
Add these **repository secrets** (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 niramoy-release.jks` |
| `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | from `keystore.properties` |
| `GOOGLE_SERVICES_JSON_BASE64` | `base64 -w0 app/google-services.json` |
| `SUPABASE_URL`, `SUPABASE_ANON_KEY` | from `.env` (also used by the keep-alive workflow) |

You never edit version numbers. They come from the tag: `v1.2.3` gives versionName `1.2.3` and versionCode `10203`.
The tag message becomes the "what's new" text in the update dialog. To release:
```bash
git tag -a v1.0.1 -m "নতুন ডিজাইন, আরও সহজ"
```
```bash
git push origin v1.0.1
```
CI then publishes `niramoy-v1.0.1.apk` and `version.json`. Every installed app finds the update within 6 hours of
being opened, or right away from **Settings → আপডেট আছে কিনা দেখুন**.
To force everyone older than some version to update, put that versionCode in `update/min-version-code.txt`.

**Forks:** the update URL is built from `-PupdateRepo=owner/repo` (CI fills it in automatically). Never change the
signing key after the first public release, or existing users can't update.

---

## Setting it up for an elderly user
1. **Sign in** (one account per person; a family member's email is fine).
2. **Answer the routine questions.** Every one is skippable.
3. **Phone setup:** press every button until each card shows a green tick, then press **পরীক্ষা**, lock the phone, and check
   it rings within 15 seconds. On Xiaomi / Oppo / Vivo / Realme, also turn on **Autostart**.
4. **Add medicines:** one, many, or a prescription photo.
5. **Add emergency contacts:** কাগজপত্র → আমার তথ্য.

## Testing

Unit tests (`./gradlew :app:testDebugUnitTest`) cover the dose schedule, missed-dose logic, course end dates,
Asia/Dhaka times, adherence %, both parsers, Bangla numerals, and missing strings.

On a real phone, ideally a small, low-end one:
- **Reminders:** the full-screen alarm and voice play; খেয়েছি stops it; ignoring it repeats N times and then marks it missed;
  পরে বলো snoozes. It still rings after **swiping the app away**, after a **reboot** (including mid-sequence),
  in **Doze** (`adb shell dumpsys deviceidle force-idle`), and after an **app update**.
- **Sync:** add data in airplane mode, reconnect, and check it in Firestore. Then reinstall, sign in, and check everything is back and the alarms are rebuilt.
- **Course / stop:** a 3-day course ends by itself; stopping keeps the history.
- **Voice:** "আজ শরীরটা ভালো না, সুগার ১২০, প্রেশার ১৩০ বাই ৮০" fills mood, sugar and BP, and nothing is saved before you confirm.
- **OCR:** a printed prescription gives a review screen; you edit, save, and the photo is kept.
- **Emergency:** Call opens the dialer; WhatsApp opens the chat with a Bangla message.
- **Accessibility:** works at the largest font size; read-aloud works (install the Bangla voice in Google TTS if it's silent).

## Known limits
- **Handwritten prescriptions:** OCR is weak on handwriting, which is why the review screen is mandatory.
- **Aggressive battery savers** (some Xiaomi / Oppo / Vivo / Realme phones) need *Autostart* allowed once. The setup screen opens the right page.
- **Android 14+:** the full-screen alarm needs the "full screen" permission. Without it, a big notification shows instead.
- **Bangla speech recognition** needs the Google app.

## Contributing
Issues and pull requests are welcome, especially Bangla wording, testing on more phone brands, and better
prescription parsing. Please keep every user-facing text in `StringsBn.kt` and `StringsEn.kt` and run the unit tests.

## Third-party licences
- Code: **MIT** ([LICENSE](LICENSE)).
- Fonts: Hind Siliguri, Poppins (SIL OFL 1.1, `app/src/main/assets/licenses/`).
- Tesseract / Tesseract4Android, tessdata_fast models: Apache-2.0.
- Voice tones in `res/raw/*.wav` were generated with Meta MMS-TTS, whose model weights are **CC-BY-NC-4.0**.
  The generated audio is for **non-commercial** use, matching this free app. Replace the tones if you redistribute commercially.
- Firebase, Supabase: used on their free tiers under their own terms.
