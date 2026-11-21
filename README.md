# Shobu for Android

An offline Shobu game for local two-player pass-and-play or a single player against the AI. Four boards stay in a fixed 2×2 layout. Includes pulsing legal-move markers, simple push/ejection animation, undo during the push step, a clear named winner screen, and a persistent dark/light theme switch.

Choose **LOCAL TWO-PLAYER** or **PLAY VS AI** from the button at the bottom; choosing AI opens a second menu for **Easy**, **Medium**, or **Hard**. In AI games, you play Black and the AI plays White. The AI searches complete setup-plus-push turns and uses UCT Monte-Carlo tree search at the two higher levels.

## Play

- Black starts and owns the two bottom home boards; White owns the two top boards.
- Each turn begins with a 1–2 space move on one of your home boards, then repeats the same direction and distance on a board of the opposite shade.
- The second move may push one opposing stone, including off the edge. Stones cannot jump, and your own stones cannot be pushed.
- If an opposing stone is on the final square of a two-space move, it is pushed one square beyond the destination and leaves the board only when that square is off the edge.
- Remove all opposing stones from any one board to win.

The app and AI work offline. It does not include accounts, ads, analytics, or in-app purchases.

## Build

Open the project folder in Android Studio or use Java 17+, Android SDK Platform 36, Android Gradle Plugin 8.10.0, and Gradle 8.11.1:

```sh
./gradlew testDebugUnitTest assembleDebug
```

For a Play upload, build a release Android App Bundle (`bundleRelease`) and sign it with a private upload key. Keep signing credentials outside this public repository and use Play App Signing.

The project targets Android 16 / API 36. The [v1.3.2 debug APK](releases/Shobu-1.3.2.apk) is installable for direct testing; it is not the Play upload package. The **Android build** GitHub Actions workflow runs the unit tests and builds the APK. For Play, build a release APK or signed AAB and keep the upload key outside this public repository.

## Google Play preparation

Draft listing copy, privacy policy, icon, feature graphic, and portrait preview images are in [`playstore/`](playstore/). The preview screenshots are rendered from the current interface and sample states; capture or verify them against the final release build before production submission.

## License

MIT. See [LICENSE](LICENSE).
