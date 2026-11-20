# Shobu for Android

An offline, local two-player Shobu game. Four boards stay in a fixed 2×2 layout, and players pass one phone or tablet between turns. Includes pulsing legal-move markers, simple push/ejection animation, undo during the push step, a clear named winner screen, and a persistent dark/light theme switch.

## Play

- Black starts and owns the two bottom home boards; White owns the two top boards.
- Each turn begins with a 1–2 space move on one of your home boards, then repeats the same direction and distance on a board of the opposite shade.
- The second move may push one opposing stone, including off the edge. Stones cannot jump, and your own stones cannot be pushed.
- Remove all opposing stones from any one board to win.

The app works offline. It does not include accounts, ads, analytics, or in-app purchases.

## Build

Open the project folder in Android Studio or use Java 17+, Android SDK Platform 36, Android Gradle Plugin 8.10.0, and Gradle 8.11.1:

```sh
./gradlew testDebugUnitTest assembleDebug
```

For a Play upload, build a release Android App Bundle (`bundleRelease`) and sign it with a private upload key. Keep signing credentials outside this public repository and use Play App Signing.

The project targets Android 16 / API 36. The current repository APK is an installable v1.1 debug build; it is for direct testing and is not the Play upload package.

## Google Play preparation

Draft listing copy, privacy policy, icon, feature graphic, and portrait preview images are in [`playstore/`](playstore/). The preview screenshots are rendered from the current interface and sample states; capture or verify them against the final release build before production submission.

## License

MIT. See [LICENSE](LICENSE).


## Complete Play Store asset pack

Download the [complete asset pack ZIP](playstore/Shobu-Play-Store-assets.zip), containing the app icon, feature graphic, phone screenshots, listing copy, privacy policy, and release checklist.
