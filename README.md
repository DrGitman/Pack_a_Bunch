# Pack a Bunch

**Know what fits. See where it goes.**

An Android app for packing things into a space. You measure the space, add your items, and
the app works out an arrangement and walks you through placing it one piece at a time.

Status: **the app runs end to end on a phone.** Onboarding, accounts, the packing engine,
the plan and the packing guide all work, and packs sync to the account. What is left before
a Play release is listed at the bottom.

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

Packs are stored on the phone and backed up to the signed-in account. Photos stay on the
phone and are never uploaded.

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

The full copy rules are in `docs/UX.md` and are requirements, not suggestions.

## Stack

| Layer | Choice |
|---|---|
| UI | Kotlin, Jetpack Compose, Material 3, Navigation Compose |
| State | ViewModel, StateFlow, coroutines |
| Storage | Room for packs, SharedPreferences for settings, app-private files for photos |
| Accounts and sync | Supabase auth and Postgres, with row level security |
| Camera | CameraX for photos; ARCore (AR Optional) for measurement and scanning |
| 3D | Compose Canvas isometric renderer, driven by real solver output |
| Motion | Lottie for the designer's exported animations, Compose for everything else |
| Packing engine | Pure Kotlin, no Android dependencies, unit-testable off-device |
| Billing | Google Play Billing via RevenueCat |

The packing engine stays free of Android imports so it can be tested without a device —
that is a module boundary, so the compiler enforces it. Solver axes are X left→right,
Y front→back, Z up; lengths are integer millimetres and are converted only at the UI
boundary.

## Layout

```
Pack_a_bunch_App/
├── packing/                    ← the engine. Pure Kotlin, no Android
├── app/                        ← Compose UI, theme, components, renderer
│   └── src/main/res/raw/       ← the designer's Lottie animations
├── animations/                 ← Lottie sources and previews as exported from Figma
├── supabase/
│   ├── CLOUD-SETUP.md          ← what is deployed, and how to apply a migration
│   └── migrations/             ← SQL, applied through the Supabase SQL editor
├── docs/
│   ├── UX.md                   ← screen map, acceptance notes, copy rules
│   ├── design-tokens.json      ← colour, type, spacing — source of truth
│   └── PLAN.md                 ← the build and Play launch plan
├── design/artboards/           ← mockups, one .dc.html per screen
└── logos/                      ← official app mark and wordmark
```

Read `docs/UX.md` before building a screen, and lift exact values from that screen's
artboard. Never hardcode a hex value in a composable — colour, type and spacing come from
`app/.../ui/theme/`.

## Plans

| | Free | Pro |
|---|---|---|
| Pieces per pack | 20 | unlimited |
| Saved packs | 1 | unlimited |
| Scans per day | 3 | unlimited |
| Size of space you can scan | 120 L | any |
| Item library | — | ✓ |
| Plan comparison | — | ✓ |

"Unlimited" means no *product* limit. The engine can only search about 400 pieces, and
that ceiling applies to everyone — it must never be left out when describing Pro.

Tier limits live in `TierLimits`, deliberately outside the solver: the engine produces the
same arrangement whatever anyone paid, and there is a test asserting it.

## Getting started

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"

./gradlew :packing:test          # engine tests, no device needed
./gradlew :app:installDebug      # debug build: RevenueCat test purchases work here
./gradlew :app:installStaging    # optimised build: judge animations and speed here
```

**Use `staging` to judge how the app feels.** Debug builds of Compose drop frames on older
phones, which makes good motion look broken. Staging is the release build signed with the
debug key, so it installs directly.

Configuration lives in `local.properties`, which is never committed. See
`local.properties.example` for the keys: the Supabase URL and publishable key, the Google
web client id, and the RevenueCat key. A `test_` RevenueCat key works only in debug builds;
RevenueCat shuts the app down if one reaches a release build.

## Before a Play release

- **A privacy policy and terms at a public URL.** Both exist in the app
  (`ui/screens/LegalScreens.kt`) but Play needs a web address as well.
- **Account deletion.** Play requires it in-app and on the web; deleting the account row
  needs a Supabase edge function.
- **Your own email sender.** Supabase's built-in mail sends about two messages an hour,
  which will not survive real sign-ups. Set SMTP in the Supabase dashboard.
- **Measurement accuracy.** The tape-measure check needs a phone that supports ARCore
  Depth; the P30 Lite used for testing does not.
- **The application id** `com.packabunch` is permanent once uploaded. Confirm it first.
- Target Android 16 / API 36, verify 16 KB page-size compatibility, and keep signing keys
  and service-account credentials out of this repo.
