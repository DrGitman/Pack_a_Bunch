# Pack a Bunch

**Know what fits. See where it goes.**

An Android app for packing things into a space. You measure the space, add your items, and the
app works out where everything goes. Then it walks you through packing it, one piece at a time.

All you need is your phone's camera. Or type the sizes in. Both give you the same plan.

---

## What it does

1. **Get a space.** Take one photo of it, or type its inside sizes. Typing is always there.
   A measured space can have an odd shape, like a car boot with wheel arches or a cupboard
   with a shelf.
2. **Add items.** Take one photo of them, or type them in. Each one has a name, a photo,
   its width, depth and height, how many there are, and whether it can be turned or have
   things stacked on it.
3. **Get a plan.** The app places what it can. If something does not fit, it says so plainly
   and says why.
4. **Pack it.** Numbered steps, one item at a time, from the bottom up.

Packs are kept on the phone and backed up to your account: names, sizes, the shapes scans
measured, and plans. Item and scan photos are not backed up; they stay on the phone, and the
whole photo is never uploaded. The two things that do leave the phone: a profile picture, if
someone adds one, and, with "Look up items online" on, each found item's small cut-out, sent
to find its real size (see below).

## What it will not do

These are choices, not things we forgot:

* It never claims a size is exact. Every size says where it came from: the camera, or typed in.
* It never guesses at what the camera did not see. Unseen space counts as full until someone
  looks.
* It does not handle soft bags, clothes or weight. Items are treated as solid boxes, or as
  the smallest box they fit in.
* It never says something is impossible to pack. It says "no arrangement found", because the
  planner not finding one is not proof there is none.
* It never hides how good a size is. A size from a photo is an estimate, and you check each one
  before it is saved.
* It shows how full the space is, not a made up "optimisation score".

## How the camera measures

You take **one photo**. That is a scan. Nothing else is needed: no special phone and no card.
A bank card or a sheet of A4 in the photo makes the sizes better, but it is never required.

While you line up the photo, the app checks the light, how steady the phone is and how it is
tilted, and tells you in plain words if something needs fixing.

For items, the app then:

1. **Finds the things in the photo.** Two object finders look at it: YOLOX Tiny, which knows
   80 everyday things like cups, bottles, mice and books, and ML Kit, which finds objects it
   cannot name. A depth step also finds anything standing up off the table that both missed,
   the way the reference project does it (Object Volume Detector by HarshdeepJ). A thing found
   only by depth must also stand out by colour or by its edge, so a wall or a bright patch is
   not counted as an item.
2. **Traces each one's outline.** This follows the OpenCV steps from the tutorials, written in
   Kotlin because there is no OpenCV on the phone:
   * the thing is told apart from the table by colour, compared with the colours just around
     it, so the threshold suits each object;
   * the colour guess is refined twice, the way GrabCut does it, so shadows go back to the table;
   * if the colours are too alike (a white cup on a white table), its edges are used instead
     (Canny edge detection);
   * small specks are removed and small gaps closed;
   * the edge is traced pixel by pixel round the outside (like `findContours`) and cut down to
     the corners that matter (like `approxPolyDP`), so a mug keeps its handle.
3. **Works out the table.** The phone's gravity sensor says which way is up. A small depth
   model (MiDaS) says which parts are nearer. Together they place the table and everything on
   it in 3D. A photo picked from the gallery has no gravity reading, so the tilt is worked out
   from how the table's depth changes down the picture.
4. **Sets the scale.** One number is still missing: how high the phone was above the table.
   The app takes it from the best thing in the photo:
   * a bank card or A4 sheet, lying flat or standing up;
   * otherwise something it recognises whose size hardly changes, like a computer mouse
     (about 11 cm long) or a phone, measured by its longest side so it works however it lies.
     Things that come in many sizes, like bottles and bowls, are never used for this;
   * otherwise a typical height for that kind of photo.
5. **Measures each thing.** A 3D box is fitted to its points. The few stray points at the edges
   are dropped first, and each thing is set down on the table, since nothing floats. A fit that
   comes out wider than the thing's own points is thrown away.
6. **Names it.** YOLOX's name is used. It knows no "can", so a short "bottle" is called a can.
   Anything YOLOX could not name gets a name from ML Kit's labeller.

Spaces work the same way. The floor is found first, then the walls and the opening, then the
inside size.

Every scanned item and space gets an outline that is drawn in piece by piece, whatever its
shape. You then check each size, and change any that look wrong, before anything is saved.

All the measuring runs on the phone. The models are inside the app, so nothing is downloaded,
and it works with no internet connection.

**Looking items up online.** A photo alone cannot tell a toy car from a real one: with nothing
of known size in it, every size can be off by the same amount. So, when "Look up items online"
is on in Settings (it is by default, for signed-in people), each found item's cut-out, never the
whole photo, is sent to the `identify-item` Supabase function. That asks Google's Gemini what
the item is and its real size: the maker's size when it recognises the exact product, otherwise
the usual size of that kind of thing. The app then:

* gives a recognised product its real size, laid onto the sides the photo measured;
* rescales everything else in the photo by how far off the photo was for that product, since
  every size in one photo is off by the same factor;
* keeps the card's scale when a card is in the photo, and only swaps in exact products.

The badge beside a size says where it came from: **PHOTO**, or **LOOKED UP**. The function keeps
nothing. Set it up once: get a key at aistudio.google.com, then
`supabase secrets set GEMINI_API_KEY=...` and `supabase functions deploy identify-item`.
Optionally `GEMINI_MODEL` picks the model (`gemini-2.5-flash` by default).

### What photo measuring is good and not so good at

* **Good:** solid things on a table, with a card, or something like a mouse or phone, in the
  photo.
* **Fine:** cupboards and car boots.
* **Rough:** a photo with nothing in it to set the scale. The shapes come out right, but every
  size can be too big or too small by the same amount. Check those sizes carefully.
* **Hardest:** a whole room. Typing a room's size is often quicker.

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
| Measuring | Outline tracing, card finding and photo geometry (Kotlin), MiDaS depth and YOLOX names (ONNX Runtime), ML Kit, a Python engine through Chaquopy |
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

### Checking the photo engine with test pictures

The staging build has a hidden test screen that runs the app's own photo engine on pictures you
give it. It draws what it found onto each one: outlines in green, with the name and sizes. Use
it to see how a change does on real photos.

```powershell
.\gradlew.bat installStaging
adb push my_photo.jpg /sdcard/Android/data/com.packabunch.staging/files/probe-in/
adb shell am start -n com.packabunch.staging/com.packabunch.debug.PhotoProbeActivity
adb pull /sdcard/Android/data/com.packabunch.staging/files/probe-out/ .
```

Add `--es kind space --es name "Car boot"` to the `am start` line to measure a space instead.
`adb logcat -s PhotoProbe PackScan` shows each result and why it came out that way: what each
finder saw, where the scale came from, and the tilt. The test screen is in the staging build,
so the website's preview download has it too, but it only runs when started over USB with
`adb`. The Play release never has it.

## Before a Play release

* **The app id** `com.packabunch` can never change once it is uploaded. Confirm it first.

## Licence

Pack a Bunch is open source under the [MIT licence](LICENSE). The models inside the app keep
their own licences: MiDaS small (MIT) and YOLOX Tiny (Apache 2.0), with their notices in
`app/src/main/assets`.
