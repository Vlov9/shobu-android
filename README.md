# Shobu for Android

A local two-player Shobu game. The four boards remain upright in a fixed 2×2 arrangement; players pass the device between turns. Switch between dark and light themes with the sun/moon button in the upper-left corner; the choice is remembered.

## Play

- Black starts and owns the two bottom homeboards; White owns the two top homeboards.
- A turn is a setup move of 1–2 clear spaces on one of your homeboards, then the same direction and distance on a board of the opposite shade.
- The push move may shove one opposing stone, including off the board. Stones cannot jump, and your own stones cannot be pushed.
- Remove all opposing stones from any one board to win.

The app works fully offline. Legal setup destinations pulse; pushed stones slide and shrink as they leave the board. A clear victory screen names the winner. Use **Undo** during the push step to reconsider the setup move.

## Build

Open this folder in Android Studio, or build from the command line with Java 17+ and Android SDK Platform 35 installed:

```sh
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
