# Google Play listing draft

## Main store listing

- **App name:** Shobu — Local Strategy
- **Short description (75/80):** Local two-player Shobu: move stones, push strategically, and claim the win.
- **Category:** Games · Board
- **Pricing:** Free; no ads and no in-app purchases.
- **Default language:** English (United States)
- **Privacy policy URL:** https://github.com/Vlov9/shobu-android/blob/main/playstore/privacy-policy.md
- **Support email:** Add a monitored email address in Play Console before submission.

### Full description

Play Shobu face to face on one phone. Take turns, pass the device, and outthink your opponent in a quiet game of movement and pushes.

Shobu is a local two-player strategy game inspired by the classic rules. Four fixed boards stay in the same orientation throughout the match. There are no accounts, ads, in-app purchases, or network features.

**How a turn works**

1. Move one of your stones one or two clear spaces on either of your home boards.
2. Repeat the same direction and distance on a board of the opposite shade.
3. Push at most one opposing stone with the second move. Stones pushed beyond an edge leave play.

Clear all opposing stones from any one board to win. The app highlights legal setup moves, animates pushes and ejections, and names the winner clearly. Undo is available during the push step. Switch between dark and light themes at any time.

Designed for two people sharing a device. Shobu works offline and keeps the boards static so both players can read the position from their own side of the table.

## Preview assets

- App icon: `app-icon-512.png` (512 × 512 PNG)
- Feature graphic: `feature-graphic-1024x500.png` (1024 × 500 PNG)
- Phone screenshots: three portrait PNGs in `screenshots/` (1080 × 2400 each)

The screenshots are high-resolution UI renders built from the current screen layout and sample game states. Replace or confirm them with captures from the final release build before production submission.

## Suggested content-rating answers

The app is a non-violent abstract board game with no chat, user-generated content, location, or online interaction. Complete the IARC questionnaire in Play Console and use its assigned rating; do not treat this note as a rating decision.

## Release package notes

The v1.2 source is configured for target API 36. Build a **signed Android App Bundle (AAB)** from that source for Play Console. The repository's v1.1 APK is an installable debug build for direct testing and is not the Play upload package. Configure a release upload key outside the public repository and enroll in Play App Signing.
