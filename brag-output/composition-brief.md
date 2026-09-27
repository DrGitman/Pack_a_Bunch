# Hyperframes Composition Brief: Pack a Bunch

## Objective
Create a 1:55 launch-style brag video for Pack a Bunch, an Android packing app.

## Output
- Composition directory: `brag-output/composition/`
- Rendered video: `brag-output/brag.mp4`, delivered as `promo/brag-pack-a-bunch.mp4`
- Format: landscape, 1920x1080
- Duration: 115 s. The user asked for 1:50–2:00, which overrides Brag's 15–25 s default.

## Source Material
- Project root: the repository root
- Primary files read:
  - `PRODUCT.md`
  - `README.md`
  - the app's screens (`ItemScanScreen`, `SpaceScanScreen`, `CameraOverlay`, `PackingGuideScreen`, `UpgradeScreen`)
  - the fonts in `app/src/main/res/font/`
  - the user's desk photo
- Product name: Pack a Bunch
- Strongest claim: "Pack it once. It fits."
- Key UI to recreate: the camera overlay, with its dark pills, white measurement pills, amber H pill and terracotta "Use …" button. Also the Pack Plus card.
- Copy that must appear verbatim:
  - "Lay it flat. Push it into the back right corner."
  - "Keep every pack you plan"
  - "Try Pack Plus — code on the Pack Plus page"

## Creative Direction
- Tone preset: app-store
- Creative direction: a calm, warm product film
- Angle: see `brag-plan.md`
- Hook: the real desk photo, then "One car boot. A table full of stuff." and "Will it all fit?"
- Outro: "Pack it once. It fits."
- Avoid:
  - generic SaaS language
  - blue tech palettes
  - stock movers
  - login and loading screens

## Visual Identity
- Background: #F7EFE6
- Text: #3F2718
- Accent: #A65C34, with #F2A03D for scanning
- Fonts: Plus Jakarta Sans for display and body, DM Mono for measurements. Both are loaded locally with @font-face.

## Storyboard
See `brag-plan.md`, Scenes 1–8: pile, point the phone, scan the space, scan the things, plan, guide, Pack Plus, CTA.

## Audio
- Music: `assets/music/happy-beats-business-moves-vol-12-by-ende-dot-app.mp3`, at about 0.55 volume, fading out from 111 s to 115 s.
- Strong-cue locks: 8.74 s (title), 22.93 s (aha), 96.55 s (Pack Plus).
- SFX: soft drops, clicks, soft impacts and one bell. They are chosen after the animation and sit at about 0.6–0.7 volume.
- Audio-reactive: the terracotta glow behind the phone breathes subtly with the music's RMS, if the extraction runs.
- Captions are burned in on every scene.
