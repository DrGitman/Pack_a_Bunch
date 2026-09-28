# Brag Plan: Pack a Bunch

## What is this app?
An Android app that measures a space and the things going into it with the phone's camera, plans where every piece goes in 3D, then talks you through packing it — "lay it flat, push it into the back right corner".

## The angle
The first time you pack a car boot, it doesn't fit, and you unpack everything and start again. This video skips that: the phone measures, the app plans, and it all fits the first time. The tone is warm and exact, never shouty, and it is always about one real boot and one real table of stuff.

## Hook (first 2–3 seconds)
The user's real desk photo, full bleed and dimmed. It holds a display, a speaker, a puck, a vase and a book. One line lands on it: "One car boot. A table full of stuff." Then a beat later comes the question every packer asks: "Will it all fit?"

## Key moments (the middle)
- The aha, by 25 s: the same desk inside a phone. Outlines draw around each object, and measurement pills pop in (W 16.4 cm, H 14.8 cm, D 8.8 cm).
- Scan the space: the car boot's edges draw in while "mapped" counts from 0% to 82%. W 104 cm, D 80 cm and H 59 cm land one by one, then "Use this space".
- Scan the things: five items tick from scanning to measured, one at a time. The counter reads 5 of 5 and the button says "Use 5 items".
- The 3D plan: an isometric boot where pieces drop in one by one, with the stats "11/11 pieces placed" and "36% filled".
- The guide: step cards reading "Step 3 of 11 — Lay it flat. Push it into the back right corner." Then "It all fits."

## Outro / punchline
"Pack it once. It fits." The Pack a Bunch lockup, then "Try Pack Plus — code on the Pack Plus page".

## User flow worth showing
Scan the car boot → scan the things on the table (several at once, each outlined and measured) → the 3D plan → the step-by-step guide → it fits.

## Tone
- Preset: app-store
- Creative direction: a calm, warm product film. Real screens, one workflow, captions on everything.
- Interpretation: clean feature-card reveals and smooth slides. There's a light, consistent sound layer, with long enough holds to read every caption with the sound off. The video runs to the user's requested 1:55 instead of Brag's default 15–25 s.

## Format: landscape — 1920x1080
## Duration: 115 s (the user asked for 1:50–2:00, which overrides Brag's 15–25 s default)

## Visual identity (from the project)
- Background: #F7EFE6 (warm ground)
- Text: #3F2718 (deep brown)
- Accent: #A65C34 (terracotta), plus camera amber #F2A03D for scanning states
- Display font: Plus Jakarta Sans (the app's `res/font/plus_jakarta_sans.ttf`)
- Body/numeric font: DM Mono (the app's `res/font/dm_mono_*.ttf`), used for every measurement, as in the app
- Strongest visual element: the camera overlay, with dark translucent pills, white measurement pills, the amber H pill and the terracotta "Use …" button

## Share copy (draft)
Point your phone at the car boot, then at the pile. Pack a Bunch measures both, plans every piece in 3D and walks you through it — it fits the first time.

## Audio direction
- Role: a warm bed plus light UI accents
- Music: `happy-beats-business-moves-vol-12-by-ende-dot-app.mp3` (117.4 s, about 110 BPM). It runs almost exactly the length of the video.
- Music treatment: it starts at 0 at about 0.55 volume, sits a little lower under the reading-heavy scenes, and fades out over 111–115 s.
- Music cue guidance: preset `cues/happy-beats-business-moves-vol-12-by-ende-dot-app.music-cues.json`.
  - Strong cues: 8.74 s (the title reveal), 22.93 s (the aha payoff), 96.55 s (Pack Plus).
  - Beat grid at about 0.545 s. Sequential text holds use every other beat.
- Audio-reactive treatment: subtle. The terracotta glow behind the phone breathes with the music's RMS. No waveform visuals.
- SFX posture: moderate and motion-matched:
  - soft drops for measurement pills
  - clicks for taps
  - a soft impact on scene changes
  - one bell for "It all fits"
- Audio-coupled moments: pills landing, the item tick sequence, boxes dropping into the plan, a button tap on "Use this space" and "Use 5 items", the bell on the fit.
- Restraint rule: no more than one SFX per half second, and nothing bright or glassy.

## Storyboard

### Scene 1 — The pile — 0–8 s
The desk photo, full bleed with a warm dim. It reads "One car boot. A table full of stuff.", then "Will it all fit?"
Sequential/interaction: the two lines arrive one after another.
Audio intent: a gentle start as the music comes in.
Transition mood: soft → Scene 2

### Scene 2 — Point the phone — 8–25 s
The Pack a Bunch title lands at 8.74 s, then clears. The phone slides in over the warm ground with the desk in its camera view. Outlines draw around the display, speaker and puck, then the W, H and D pills pop in.
- Side copy: "Point the phone. It's measured."
- Aha payoff at about 22.9 s: "No tape measure."

Sequential/interaction: the outlines draw, then the pills arrive one by one.
Audio-coupled idea: a soft drop per pill.
Transition mood: slide → Scene 3

### Scene 3 — Scan the space — 25–45 s
The phone shows the car boot with its wireframe drawing in. The top pill reads "Car boot · 0→82% mapped". The pills arrive in order: W 104 cm, D 80 cm, H 59 cm (amber). A tap on "Use this space" follows.
Side copy: "1 · Scan the space" / "Walk the phone round the boot. Width, depth and height, in seconds."
Sequential/interaction: a counter and pills, then a simulated tap.
Transition mood: slide → Scene 4

### Scene 4 — Scan the things — 45–65 s
The phone shows the desk again. The five item chips (Display, Speaker, Puck, Vase, Book) tick from amber "scanning" to green "measured", one every beat pair. The counter goes from "0 of 5" to "5 of 5 measured", then a tap on "Use 5 items".
Side copy: "2 · Scan the things" / "Several at once. Each one outlined and measured."
Transition mood: slide → Scene 5

### Scene 5 — The plan — 65–82 s
An isometric boot. Eleven pieces drop in one by one, then the stat cards arrive: "11/11 pieces placed", "36% filled", "0 left over".
Side copy: "3 · See the plan" / "Every piece placed in 3D before you lift a thing."
Transition mood: slide → Scene 6

### Scene 6 — The guide — 82–95 s
Step cards swap:
- "Step 1 of 11 — Stand it upright against the left wall"
- "Step 3 of 11 — Lay it flat. Push it into the back right corner."

The screen then resolves to a big "It all fits." with a tick (bell).
Side copy: "4 · Pack it, step by step"
Transition mood: soft → Scene 7

### Scene 7 — Pack Plus — 95–108 s
A recreation of the Pack Plus card, "Keep every pack you plan", with its three benefit rows and a Weekly/Monthly toggle that switches. There's no price, so the video works in every region.
Side copy: "Free: up to 5 packs, up to 20 pieces each. Pack Plus: unlimited packs, pieces and any size of space."
Transition mood: clean → Scene 8

### Scene 8 — CTA — 108–115 s
The Pack a Bunch lockup, with "Pack it once. It fits." and "Try Pack Plus — code on the Pack Plus page · Android". The music fades.

Captions: every scene carries a caption strip at the bottom, so the video works with the sound off.

**Music mood for this video:** upbeat, warm
**Audio summary:** a warm bed that starts under the pile, lifts at the title, ticks with each measurement, blooms on "It all fits" and fades under the CTA.
