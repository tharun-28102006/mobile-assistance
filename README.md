<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/33aa3317-901d-4ad6-9346-c3be514a4da9

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Register the app with Firebase Authentication and enable the sign-in method you want to use.
5. Enable Firebase App Check for project `gen-lang-client-0441900106`.
6. For a debug APK, the App Check SDK generates a local debug token. In Android Studio Logcat, search for `Firebase App Check debug token`, then register that token in Firebase Console > App Check > Apps > the Android app with package `com.aistudio.auraassist.qvmxkr` in project `gen-lang-client-0441900106`.
7. For a release APK, configure the production attestation provider (reCAPTCHA Enterprise or Play Integrity) and add it as an allowed App Check provider for this Android app.
8. Enable Firebase AI Logic/App Check enforcement for the services used by the app, then run it on an emulator or physical device.
9. If you have already published the app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.
