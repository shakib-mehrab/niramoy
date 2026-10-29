# Releasing NIRAMOY

## One-time GitHub setup
1. Create an empty public repo `niramoy` on GitHub (no README / licence, they're already here).
2. Add the repository secrets listed in README section 7 (signing key, google-services.json, Supabase).
3. Supabase: run `supabase/storage-policies.sql` and enable Third-party auth → Firebase (README section 2).
4. Firebase: add both SHA-1 fingerprints (debug + release) to the Android app, and publish `firestore.rules`.

## Every release
1. Run `./gradlew :app:testDebugUnitTest`, then commit and push `main`.
2. Make an annotated tag. Its message is shown to users as "what's new":
   `git tag -a v1.1.0 -m "নতুন ডিজাইন"` then `git push origin v1.1.0`.
3. The **Release APK** workflow sets the version from the tag (v1.1.0 → versionCode 10100), then tests, builds, signs,
   and publishes `niramoy-v1.1.0.apk` + `version.json`. Installed apps pick it up automatically.

Versions must always go up (v1.1.0 → v1.1.1 → v1.2.0). Each part must stay below 100.

## Never commit
`.env`, `app/google-services.json`, `keystore.properties`, `*.jks`. All are git-ignored.
Keep a backup of `niramoy-release.jks` and `keystore.properties` somewhere safe (e.g. a password manager).
If the key is lost, installed apps can't be updated.
