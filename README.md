# Pack a Bunch

**Know what fits. See where it goes.**

An Android app for packing rigid things into a rectangular space. You measure the
space, add your items, and the app suggests an arrangement and walks you through
placing it one item at a time.

Status: **not started.** This repo is the build target for the plan and design
handoff that already exist alongside it.

---

## What it does

1. **Measure a space** — type the inside dimensions, or measure with the camera on
   supported devices. The typed path is always available and never hidden.
2. **Add items** — name, photo, width/depth/height, quantity, and whether the item
   may be rotated or have something stacked on it.
3. **Get an arrangement** — a deterministic packing heuristic places what it can and
   tells you plainly what it couldn't place.
4. **Follow the pack** — numbered steps, one highlighted item at a time, bottom-up.

Everything lives on the device. Projects, photos and dimensions are stored locally.

## What it deliberately does not do

These are product decisions, not gaps to fill in later without a conversation:

- It does not claim exact measurements. Every stored dimension carries its
  provenance — *camera estimate* or *typed in* — and shows it wherever it appears.
- It does not model soft bags, clothing, irregular meshes, deformation, nesting,
  or weight. Items are rigid cuboids or conservative cuboid envelopes.
- It does not say an arrangement is *impossible*. A heuristic failing to place an
  item is not a proof, and the copy says "no arrangement found for these items".
- It does not infer dimensions from an object label or a single unscaled photo.
- It does not report an "optimisation percentage". The metric is **modelled fill**,
  with a stated basis.

The full copy rules are in the UX spec and are requirements, not suggestions.

## Stack

| Layer | Choice |
|---|---|
| UI | Kotlin, Jetpack Compose, Material 3, Navigation Compose |
| State | ViewModel, StateFlow, coroutines |
| Storage | Room for projects/items/plans, DataStore for settings, app-private files for photos |
| Camera | CameraX for photos; ARCore (AR Optional) for measurement |
| 3D | SceneView/Filament, with a Compose Canvas isometric renderer as fallback |
| Packing engine | Pure Kotlin, no Android dependencies, unit-testable off-device |
| Billing | Google Play Billing via RevenueCat |

The packing engine stays free of Android imports so it can be tested without a
device. Solver axes are X left→right, Y front→back, Z up; lengths are integer
millimetres and are converted only at the UI boundary.

## Layout

```
Pack a bunch/
├── Pack_a_bunch_App/           ← this repo, the Android app
├── pack-a-bunch-design/        ← design handoff
│   ├── UX.md                   ← screen map, acceptance notes, copy rules
│   ├── design-tokens.json      ← colour, type, spacing — source of truth
│   ├── theme/                  ← the same tokens as Compose (Color/Type/Dimens.kt)
│   └── artboards/              ← 54 mockups, one .dc.html per screen
├── Project Development Plan/   ← the 5-day build & Play launch plan
└── Ref designs/
```

Read `UX.md` before building a screen, and lift exact values from that screen's
artboard rather than rounding them to a 4/8dp grid. Never hardcode a hex value in
a composable — colour, type and spacing come from `theme/`.

## Getting started

Nothing to run yet. First commits, in order:

1. Compose scaffold, navigation, Room skeleton, theme copied from the handoff.
2. Manual create-space and item-entry flow — the whole journey with no camera.
3. Packing engine and its tests, before any visualisation work.
4. Result and packing-guide screens.
5. Billing.
6. AR measurement, gated on measuring real containers against a tape measure.

Recovery states (camera denied, AR unavailable, tracking lost, no arrangement
found, solver timeout, purchase pending/failed) belong with the screen they
attach to, not batched at the end. Most of the real work hides there.

## Open decisions

- **Accounts.** The design handoff assumes accounts are required with no anonymous
  mode; the development plan assumes no signup wall. Pick one before writing the
  first screen — accounts pull in a backend, a privacy policy covering personal
  data, a matching Data safety declaration, and a **web-reachable** account
  deletion route that Google Play requires.
- **Sign-in merge.** The login screen promises that signing in on a phone with
  existing packs merges them into the account. Merge, replace, or ask?

## Release notes to self

- Target Android 16 / API 36 for new submissions.
- Play Billing Library 8+ — check what RevenueCat actually resolves to, not just
  the top-level version.
- Verify 16 KB page-size compatibility; AR and rendering SDKs ship native libraries.
- Keep signing keys and service-account credentials out of this repo.
