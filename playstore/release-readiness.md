# Play Store release readiness

## Prepared in this project

- MIT open-source license and public source repository
- Draft listing name, description, category, and free/no-ads/no-IAP pricing
- 512 × 512 app icon and 1024 × 500 feature graphic
- Four 1080 × 2400 portrait listing preview images
- Privacy policy text for the current offline-only behavior
- Source configured for target API 36 and version 1.4.0, including a playable English story episode, sequential AI move animations and delayed winner reveal, offline AI at three difficulties, system-bar safe layout, and corrected push distance for two-space moves
- Unit tests for core game rules, edge cases, and AI turn legality
- GitHub Actions workflow that runs the tests and builds a debug APK

## Still required in Play Console

1. Register or use an existing Play Console developer account; complete identity and device verification.
2. Add the support email, privacy policy URL, app access and content-rating answers, target audience, data-safety answers, and any declarations shown by Play Console.
3. Build and upload a release-signed AAB, enroll in Play App Signing, and complete the Play pre-launch report.
4. If this is a new personal developer account subject to Google's testing rule, run a closed test with at least 12 opted-in testers continuously for 14 days, then apply for production access.
5. Review the listing assets on actual device sizes and submit the app for review.

A Play Console registration has a one-time US$25 fee. The app itself is intended to be free. A debug APK is available for direct testing; a Play-ready signed AAB still requires a release upload key and a Play Console account.

Google Play currently requires new apps and updates to target Android 16 / API 36. Confirm the live Play Console checklist before submission.
