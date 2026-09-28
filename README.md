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

| | Free | Pack Plus |
|---|---|---|
| Pieces per pack | 20 | unlimited |
| Saved packs | 1 | unlimited |
| Scans per day | 3 | unlimited |
| Size of space you can scan | 120 L | any |
| Item library | — | ✓ |

"Unlimited" means no *product* limit. The engine can only search about 400 pieces, and
that ceiling applies to everyone — it must never be left out when describing Pro.

Tier limits live in `TierLimits`, deliberately outside the solver: the engine produces the
same arrangement whatever anyone paid, and there is a test asserting it.

**Prices by country.** Pack Plus is sold weekly and monthly (and yearly, if one is set up), and
the app never states a price of its own: it shows the one Google Play returns for the buyer's
Play account, in their currency — N$ in Namibia, € in Germany. Set each subscription's base
price once in the Play Console (Monetize → Subscriptions → the plan → Set prices) and let Play
convert it for every other country at current rates, so the prices differ on screen but are
the same once converted. No location permission is needed; Play already knows the country.
The offering itself (which of weekly, monthly and yearly exist) is set in RevenueCat.

## The measuring engine (Python)

Item scans are measured by `app/src/main/python/packscan.py` — NumPy and OpenCV, run on the phone
through [Chaquopy](https://chaquo.com/chaquopy/). ARCore supplies the scale (every depth pixel in
millimetres) and where the phone is; ML Kit boxes each thing and names it; the Python engine does
the rest:

1. **Outline** — about once a second each box's object is traced in the camera picture (OpenCV
   GrabCut, prompted by the box the way SAM is), neighbours kept apart.
2. **Body** — depth points outside that outline are dropped; the rest are sliced into a height
   map and labelled with connected components, and the piece that owns the middle of the box is
   the object.
3. **Size** — the points' footprint gets OpenCV's minimum-area rotated rectangle (the object's
   turn, as PCA gives it), faces are placed at the median of the points on them, and round things
   are fitted slice by slice as circles (cylinder, tapered or ball).

If Python cannot start, the original Kotlin engine (`packing/…/ScanMath.kt`) measures instead.
Building needs Python 3.11 on the computer (see `local.properties.example`). The JVM tests run the
same `packscan.py` through `python3` when it has NumPy and OpenCV (`pip install numpy
opencv-python`), and skip those two tests when it does not.

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

### The database

Every file in `supabase/migrations/` is run once, in name order, in the Supabase SQL editor
(Dashboard → SQL Editor → New query → paste the file → Run). A file that says a table or
constraint "already exists" has been run before; skip it. Never run `supabase/tests/` there —
those are for a throwaway local database only.

Three dashboard settings go with the migrations:

- **Authentication → Hooks → Before User Created** → `hook_block_disposable_email`
  (from `202609270003`), so throwaway inboxes cannot sign up.
- **Integrations → Cron** switched on *before* running `202609270004_account_deletion_grace.sql`,
  so the nightly sweep that deletes accounts 30 days after they asked is scheduled. If Cron
  was off when it ran, switch it on and run the last block of that file again.
- `202609270005_fit_reports.sql` keeps the "It doesn't fit" reports: the reason picked and the
  sizes, never names. Read them in Table Editor → `fit_reports`; the app cannot read them back.
- **Authentication → URL Configuration → Redirect URLs** → add `packabunch://reset-password`.
  The password-reset email sends people back into the app with it; without it the link opens
  the Site URL instead and the reset cannot be finished on the phone.

### Running it on your phone from VS Code (Windows PowerShell)

No Android Studio needed: VS Code's terminal (**Terminal → New Terminal**) and the Gradle
wrapper in this repo build and install the app.

**Once, on the phone:** Settings → About phone → Software information → tap **Build number**
seven times; then Settings → **Developer options** → turn on **USB debugging**. Plug the phone
in and tap **Allow** when it asks.

**Once per terminal window:** tell PowerShell where Java and `adb` are. The paths below are
the defaults; if yours differ, `Get-Content local.properties` shows the SDK folder on its
`sdk.dir=` line.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path += ";$env:LOCALAPPDATA\Android\Sdk\platform-tools"
adb devices                      # should list the phone, followed by "device"
```

**Every time, to update the app on the phone:**

```powershell
git pull                         # fetch the latest changes
.\gradlew.bat installStaging     # build and install the staging app
adb shell monkey -p com.packabunch.staging -c android.intent.category.LAUNCHER 1   # open it
```

The staging app (`com.packabunch.staging`) installs beside any debug copy and keeps your packs
between updates. `.\gradlew.bat installDebug` installs the debug build instead, for testing
purchases.

If something goes wrong:

- **`adb` is not recognised** — the `platform-tools` line above was not run in this window, or
  the SDK is elsewhere: use the folder from `sdk.dir` in `local.properties`.
- **SDK location not found** — `local.properties` needs a line like
  `sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk`.
- **JAVA_HOME is not set** — run the `$env:JAVA_HOME` line; it must point at a JDK 17 or newer.
- **The phone is listed as `unauthorized`** — unlock it and accept the USB debugging prompt.

## Before a Play release

- **A privacy policy and terms at a public URL.** Both exist in the app
  (`ui/screens/LegalScreens.kt`) but Play needs a web address as well.
- **Account deletion.** In the app it is done: the account is deleted 30 days after it is
  asked for, and signing in again within them stops it. Play also wants a web page where
  someone without the app can ask for the same.
- **Your own email sender.** Supabase's built-in mail sends about two messages an hour,
  which will not survive real sign-ups. Set SMTP in the Supabase dashboard.
- **Measurement accuracy.** The tape-measure check needs a phone that supports ARCore
  Depth; the P30 Lite used for testing does not.
- **The application id** `com.packabunch` is permanent once uploaded. Confirm it first.
- Target Android 16 / API 36, verify 16 KB page-size compatibility, and keep signing keys
  and service-account credentials out of this repo.
