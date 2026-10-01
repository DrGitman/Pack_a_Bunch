# Pack a Bunch

**Know what fits. See where it goes.**

An Android app for packing things into a space. You measure the space, add your items, and the
app works out where everything goes. Then it walks you through packing it, one piece at a time.

All you need is your phone's camera. Or type the sizes in. Both give you the same plan.

---

## What it does

1. **Get a space.** Scan it with the camera, or type its inside sizes. Typing is always there.
   A scanned space can have an odd shape, like a car boot with wheel arches or a cupboard
   with a shelf.
2. **Add items.** Scan them, take a photo, or type them in. Each one has a name, a photo,
   its width, depth and height, how many there are, and whether it can be turned or have
   things stacked on it.
3. **Get a plan.** The app places what it can. If something does not fit, it says so plainly
   and says why.
4. **Pack it.** Numbered steps, one item at a time, from the bottom up.

Packs are kept on the phone and backed up to your account. Photos stay on the phone. They are
never uploaded.

## What it will not do

These are choices, not things we forgot:

* It never claims a size is exact. Every size says where it came from: the camera, or typed in.
* It never guesses at what the camera did not see. Unseen space counts as full until someone
  looks.
* It does not handle soft bags, clothes or weight. Items are treated as solid boxes, or as
  the smallest box they fit in.
* It never says something is impossible to pack. It says "no arrangement found", because the
  planner not finding one is not proof there is none.
* It never takes a size from a name or from one photo with nothing in it for scale.
* It shows how full the space is, not a made up "optimisation score".

## How the camera measures

You lay **a bank card** (or, for a big space, **a sheet of A4 paper**) flat beside the things
you are measuring. Every bank card in the world is the same size, 85.6 by 54 mm, and A4 is
210 by 297 mm. That is all the app needs to turn the picture into real sizes.

Each moment, the app:

1. **Finds the card.** It looks for edges in the picture and picks the shape with four corners
   that fits a card exactly. It places each edge to a fraction of a pixel, because every size
   the app gives depends on the card.
2. **Works out where the phone is.** From the card's four corners and the camera's focal length,
   it knows exactly where the phone is and which way it points. The card stays still, so as you
   walk around the items, every view joins up into one picture of the table.
3. **Checks the card is lying flat**, using the phone's gravity sensor. A card leaning on a wall
   would tilt every size.
4. **Sees depth.** A small depth model (MiDaS) guesses how far away every part of the picture is.
   Its guess has no units, so the app fixes it to the table: it knows the true distance to every
   point on the table from where the card is.
5. **Finds each object.** Anything standing up off the table is an object. It is found from
   sudden changes in depth, the same way as the reference project this is built on
   (Object Volume Detector by HarshdeepJ).
6. **Measures it.** Width and height come from the formula `size = pixels × distance ÷ focal
   length`. Depth comes from looking at it again from the side. A size is only "measured" once
   it has stayed steady across many views.
7. **Names it.** A small object detector (YOLOX Tiny) recognises things like cups, bottles,
   books and bowls. A name only shows once it has been seen the same way three times out of
   five.

If the picture is too dark, blurred, or the card is missing or not flat, the app says so and
asks you to fix it. It does not guess. Something it cannot measure, like glass or a mirror, gets
no size, and you are asked to type it.

Everything runs on the phone. The depth model and the object detector are inside the app, so
nothing is downloaded and nothing is sent anywhere. It works with no internet connection.

### What camera measuring is good and not so good at

* **Good:** boxes, cups, tins and other solid things on a table, with the card in view.
* **Fine:** cupboards and car boots, especially with a sheet of A4 on the floor.
* **Hardest:** a whole room. The card is small that far away. The app asks for A4 there, and
  typing a room's size is often quicker.

The models used are free to use in an app like this: MiDaS small (MIT licence) and YOLOX Tiny
(Apache 2.0 licence). Their notices are in `app/src/main/assets`.

## What it is built with

| Part | Choice |
|---|---|
| Screens | Kotlin, Jetpack Compose, Material 3, Navigation Compose |
| State | ViewModel, StateFlow, coroutines |
| Storage | Room for packs, SharedPreferences for settings, private app files for photos |
| Accounts and sync | Supabase sign in and Postgres, with row level security |
| Camera | CameraX |
| Measuring | Card finding and pose (Kotlin), MiDaS depth and YOLOX names (ONNX Runtime), ML Kit, a Python engine through Chaquopy |
| 3D | A Compose drawing of the plan, made from the planner's real output |
| Motion | Lottie for the designer's animations, Compose for the rest |
| Planner | Plain Kotlin with no Android parts, so it can be tested on a computer |
| Payments | Google Play Billing through RevenueCat |

The planner has no Android code in it, and the build makes sure it stays that way. Its axes are
X left to right, Y front to back and Z up. All lengths are whole millimetres, and they are only
turned into centimetres or inches on screen.

## Folders

```
Pack_a_bunch_App/
├── packing/                    the planner and the measuring maths, plain Kotlin
├── app/                        the screens, the camera and the scan
│   ├── src/main/assets/        the depth model and the object detector
│   ├── src/main/python/        the Python measuring engine
│   └── src/main/res/raw/       the designer's Lottie animations
├── animations/                 Lottie files and previews from Figma
├── supabase/
│   ├── CLOUD-SETUP.md          what is set up online, and how to add a database change
│   └── migrations/             database changes, run in the Supabase SQL editor
├── docs/
│   ├── UX.md                   every screen, and the rules for the wording
│   ├── design-tokens.json      colours, text sizes and spacing
│   └── PLAN.md                 the build and launch plan
├── design/artboards/           designs, one file per screen
└── logos/                      the app's logo files
```

## Plans

| | Free | Pack Plus |
|---|---|---|
| Pieces per pack | 20 | no limit |
| Saved packs | 5 | no limit |
| Scans per day | 3 | no limit |
| Size of space you can scan | 120 L | any |
| Item library | no | yes |

"No limit" means the app sets no limit. The planner itself can only handle about 400 pieces,
for everyone. That must always be said when describing Pack Plus.

The limits live in `TierLimits`, away from the planner. The planner gives the same arrangement
whatever someone paid, and a test checks that.

**Prices by country.** Pack Plus is sold weekly and monthly, and yearly if that is set up. The
app never shows a price of its own. It shows the one Google Play gives for the buyer's account,
in their money: N$ in Namibia, € in Germany. Set each subscription's price once in the Play
Console (Monetize, then Subscriptions, then the plan, then Set prices) and let Play work out
every other country. Which of weekly, monthly and yearly exist is set in RevenueCat.

## The Python measuring engine

`app/src/main/python/packscan.py` runs on the phone through
[Chaquopy](https://chaquo.com/chaquopy/), with NumPy. For each object it:

1. **Traces its outline** in the camera picture, keeping neighbours apart.
2. **Keeps its body.** Depth points outside the outline are dropped. The rest are cut into
   layers by height, and the piece that owns the middle of the object is kept.
3. **Measures it.** The smallest turned rectangle that fits its footprint gives width and
   depth. Round things are fitted as circles, layer by layer, so a cup, a tin or a ball gets
   its true shape.

If Python cannot start, the same steps run in Kotlin instead, so a scan never stops. Building
needs Python 3.11 on the computer (see `local.properties.example`).

## Getting started

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"

./gradlew :packing:test          # planner and measuring tests, no phone needed
./gradlew :app:installDebug      # debug build: RevenueCat test purchases work here
./gradlew :app:installStaging    # fast build: judge animations and speed here
```

**Use `staging` to judge how the app feels.** Debug builds drop frames on older phones, which
makes good animation look broken. Staging is the release build signed with the debug key, so it
installs straight onto a phone.

Settings live in `local.properties`, which is never committed. See `local.properties.example`
for what goes in it: the Supabase address and key, the Google web client id, and the RevenueCat
key. A `test_` RevenueCat key only works in debug builds. RevenueCat closes the app if one gets
into a release build.

### The database

Every file in `supabase/migrations/` is run once, in name order, in the Supabase SQL editor
(Dashboard, then SQL Editor, then New query, paste the file, Run). If a file says something
"already exists", it has been run before, so skip it. Never run `supabase/tests/` there. Those
are for a throwaway database on your own computer only.

Settings in the dashboard that go with them:

* **Authentication, Hooks, Before User Created:** `hook_block_disposable_email` (from
  `202609270003`), so throwaway inboxes cannot sign up.
* **Integrations, Cron:** switched on *before* running `202609270004_account_deletion_grace.sql`,
  so accounts are deleted 30 days after someone asks. If Cron was off when it ran, switch it on
  and run the last part of that file again.
* `202609270005_fit_reports.sql` keeps the "It doesn't fit" reports: the reason picked and the
  sizes, never names. Read them in Table Editor, `fit_reports`. The app cannot read them back.
* `202609280001_app_feedback.sql` keeps the "How are we doing?" answers: the stars, any words,
  packs finished and the app version. Read them in Table Editor, `app_feedback`.
* **Authentication, URL Configuration, Redirect URLs:** add `packabunch://reset-password`.
  The password reset email sends people back into the app with it. Without it the link opens
  the website instead, and the reset cannot be finished on the phone.

### Running it on your phone from VS Code (Windows PowerShell)

You do not need Android Studio. VS Code's terminal (**Terminal, New Terminal**) and the Gradle
files in this folder build and install the app.

**Once, on the phone:** Settings, About phone, Software information, then tap **Build number**
seven times. Then Settings, **Developer options**, and turn on **USB debugging**. Plug the phone
in and tap **Allow** when it asks.

**Once per terminal window:** tell PowerShell where Java and `adb` are. These are the usual
places. If yours are different, `Get-Content local.properties` shows the SDK folder on the
`sdk.dir=` line.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path += ";$env:LOCALAPPDATA\Android\Sdk\platform-tools"
adb devices                      # should list the phone, then "device"
```

**Every time, to update the app on the phone:**

```powershell
git pull                         # get the latest changes
.\gradlew.bat installStaging     # build and install the staging app
adb shell monkey -p com.packabunch.staging -c android.intent.category.LAUNCHER 1   # open it
```

The staging app (`com.packabunch.staging`) installs next to any debug copy and keeps your packs
between updates. `.\gradlew.bat installDebug` installs the debug build instead, for testing
payments.

If something goes wrong:

* **`adb` is not recognised:** the `platform-tools` line above was not run in this window, or
  the SDK is somewhere else. Use the folder from `sdk.dir` in `local.properties`.
* **SDK location not found:** `local.properties` needs a line like
  `sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk`.
* **JAVA_HOME is not set:** run the `$env:JAVA_HOME` line. It must point at JDK 17 or newer.
* **The phone shows as `unauthorized`:** unlock it and accept the USB debugging question.

## Before a Play release

* **The app id** `com.packabunch` can never change once it is uploaded. Confirm it first.
