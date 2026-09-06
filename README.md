# Pack a Bunch

**Know what fits. See where it goes.**

An Android app for packing things into a space. You scan or measure the space, add
your items, and the app suggests an arrangement and walks you through placing it one
item at a time.

Status: **engine built, no screens yet.** The packing engine and the UI foundation
are done and tested; none of the 65 designed screens are built, so the app does not
run beyond a placeholder.

---

## What it does

1. **Get a space** — scan it, or type the inside dimensions. The typed path is always
   available and never hidden. Scanned spaces can be irregular: a car boot with wheel
   arches, a cupboard with a shelf.
2. **Add items** — name, photo, width/depth/height, quantity, and whether the item may
   be rotated or have something stacked on it.
3. **Get an arrangement** — a deterministic heuristic places what it can and tells you
   plainly what it couldn't place, and why.
4. **Follow the pack** — numbered steps, one highlighted item at a time, bottom-up.

Everything lives on the device. Projects, photos and dimensions are stored locally.

## What it deliberately does not do

These are product decisions, not gaps to fill in later without a conversation:

- It does not claim exact measurements. Every stored dimension carries its
  provenance — *camera estimate* or *typed in* — and shows it wherever it appears.
- It does not guess at what the scan never saw. Unseen space is counted, reported as
  named patches with volumes, and treated as solid until somebody looks.
- It does not model soft bags, clothing, deformation, nesting, or weight. Items are
  rigid cuboids or conservative cuboid envelopes.
- It does not say an arrangement is *impossible*. A heuristic failing to place an item
  is not a proof, and the copy says "no arrangement found for these items".
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
| Camera | CameraX for photos; ARCore (AR Optional) for measurement and scanning |
| 3D | Compose Canvas isometric renderer, driven by real solver output |
| Packing engine | Pure Kotlin, no Android dependencies, unit-testable off-device |
| Billing | Google Play Billing via RevenueCat |

The packing engine stays free of Android imports so it can be tested without a
device — that is a module boundary, so the compiler enforces it. Solver axes are
X left→right, Y front→back, Z up; lengths are integer millimetres and are converted
only at the UI boundary.

## Layout

```
Pack_a_bunch_App/
├── packing/                    ← the engine. Pure Kotlin, 63 tests, no Android
├── app/                        ← Compose UI, theme, components, renderer
├── docs/
│   ├── UX.md                   ← screen map, acceptance notes, copy rules
│   ├── design-tokens.json      ← colour, type, spacing — source of truth
│   └── PLAN.md                 ← the 5-day build & Play launch plan
├── design/artboards/           ← 65 mockups, one .dc.html per screen
└── logos/                      ← official app mark and wordmark
```

Read `docs/UX.md` before building a screen, and lift exact values from that screen's
artboard rather than rounding them to a 4/8dp grid. Never hardcode a hex value in a
composable — colour, type and spacing come from `app/.../ui/theme/`.

## Plans

| | Free | Plus |
|---|---|---|
| Pieces per pack | 20 | unlimited |
| Saved packs | 1 | unlimited |
| Scans per day | 3 | unlimited |
| Size of space you can scan | 120 L | any |
| Item library | — | ✓ |
| Plan comparison | — | ✓ |

"Unlimited" means no *product* limit. The engine can only search about 400 pieces, and
that ceiling applies to everyone — it must never be left out when describing Plus.

Tier limits live in `TierLimits`, deliberately outside the solver: the engine produces
the same arrangement whatever anyone paid, and there is a test asserting it.

## Built so far

- **The engine.** Deterministic bounded heuristic, six orientations, support rules, an
  independent validator every plan must pass before it can be shown, and honest reasons
  for anything it couldn't place. Handles rectangular crates exactly and scanned
  irregular spaces as an occupancy grid, including obstructions, unseen patches, and
  whether an item will fit through the opening at all.
- **The UI foundation.** Theme and real fonts, motion system, icon set lifted from the
  artboard SVGs, component library, isometric renderer, and the app icon.

## Not built yet

- All 65 screens, and navigation between them.
- Persistence. Room is a dependency with no entities — nothing saves.
- Camera, ARCore scanning, billing, accounts.

## Getting started

```
./gradlew :packing:test        # engine tests, no device needed
./gradlew :app:assembleDebug   # build the app
```

Gradle runs on the Android Studio JBR:
`export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`.

Build screens in this order: the manual flow first (create space → items), then the
packing engine's result screens, then billing, then the camera. Recovery states belong
with the screen they attach to, not batched at the end — most of the real work hides there.

## Open decisions

- **Accounts.** `docs/UX.md` Band 1 requires them; `docs/PLAN.md` says no signup wall.
  Accounts pull in a backend, a privacy policy covering personal data, a matching Data
  safety form, and a web-reachable deletion route Play requires.
- **Sign-in merge.** `LogIn` promises local packs merge into the account. Merge, replace,
  or ask?
- **`LimitPieces.dc.html` must be rewritten before release.** Free stops at 20 pieces and
  Plus lifts it, so the cap is commercial — but that screen still says "This is not a
  paywall. Pack Plus has the same twenty." That is now false and cannot ship as drawn.
  See `TierLimits.PIECE_LIMIT_SCREEN_NEEDS_REWRITE`.
- **Application id.** `com.packabunch` is a placeholder; it is permanent once uploaded.
- **Brand colour.** The logo brown (`#5C2626`) is not the token primary (`#A65C34`).
  The mark uses its own colour; the app interior uses the tokens.

## Release notes to self

- Target Android 16 / API 36 for new submissions.
- Play Billing Library 8+ — check what RevenueCat actually resolves to.
- Verify 16 KB page-size compatibility; AR and rendering SDKs ship native libraries.
- Keep signing keys and service-account credentials out of this repo.
