# Pack a Bunch — UX spec

Every screen below has a matching mockup in `artboards/<File>.dc.html`. Open one in a
browser to see it; the file is plain HTML with inline styles, so exact values can be
lifted straight out of it.

Design tokens live in `design-tokens.json` and, for Compose, in `theme/`.

---

## Copy rules — these are not decoration

They are the reason several screens exist. Breaking one turns an honest utility into
a product that oversells.

- **"Modelled fill"**, never "optimised", "efficiency" or a percentage without a stated basis.
- **"Camera estimate"** vs **"Typed in"** — every stored dimension carries its provenance and
  shows it wherever the number is shown. Never invent a confidence score.
- **"No arrangement found for these items"**, never "impossible" or "won't fit". A heuristic
  failing to place something is not a proof.
- **Every camera path has a typed path beside it**, on the same screen, at equal prominence.
- Items are identified by **number + name + colour**, never colour alone.
- Where a value is an estimate, the word **approximate** appears near it.

---

## Band 1 — Onboarding & account

Accounts are **required**. There is no anonymous mode.

| # | Screen | File | Notes |
|---|--------|------|-------|
| O1–O3 | First run, 3 slides | `OnbMeasure`, `OnbPlan`, `OnbPack` | Skippable. Pagination is 4 dots — O4 is the last slide. |
| O4 | Honest limits | `Onboarding` | What the app can't do: soft bags, odd shapes, weight. Ships as a slide, not a dialog. |
| O5 | Units & typical use | `OnbSetup` | cm/in, and what you mostly pack. Only changes which presets are suggested first. |
| O6 | Get started | `SignIn` | Google or email. No skip, no "not now". |
| O7 | Log in | `LogIn` | Google, or email + password. Forgot-password link. Route to sign-up. |
| O8 | Create account | `CreateAccount` | Email + password only. Backup checkbox on by default; marketing checkbox off by default. |
| O9 | Reset password | `ForgotPassword` | **Same response whether or not the address exists** — the form must not reveal which emails are registered. |
| O10 | Notification rationale | `OnbNotifPrompt` | Shown before Android's `POST_NOTIFICATIONS` prompt, and **never on first launch**. Lists every notification type that exists. |
| O11 | Account | `Profile` | Plan, backup state, stats, change email/password, sign out, delete. |
| O12 | Delete account | `AccountDelete` | Typed-email confirm. Names what goes and what doesn't (the Play subscription). |

### Open decision — sign-in merge

`LogIn` promises: *"Logging in on a phone that already has packs on it will merge them into
your account."* Decide what that actually does before building: merge, replace, or ask.
It is the first thing a returning user hits and there is no good silent answer.

### What accounts pull in

- A backend, and a privacy policy that covers stored personal data.
- A Data safety declaration that matches the code and every third-party SDK.
- **A web-reachable account-deletion route.** Google Play requires one; the in-app screen
  alone does not satisfy it.
- Sync conflicts become real — see R20.

---

## Band 2 — Core screens

| # | Screen | File | Acceptance notes |
|---|--------|------|------------------|
| 1 | Welcome | `Main` | "Plan a pack" and "Try a sample". Camera permission is **not** requested here. |
| 2 | Projects | `Projects` | Thumbnail, name, piece count, last edited. Metric chips carry honest labels. |
| 2b | Projects, empty | `ProjectsEmpty` | Useful empty state plus the sample pack. |
| 2c | Project menu | `ProjectMenu` | Rename, duplicate, re-measure, share list, delete. |
| 3 | Create space | `CreateSpace` | Presets name the space and **never** fill in dimensions. Inside measurements only. |
| 3b | Camera rationale | `CameraRationale` | Precedes the system prompt. Declining leaves everything working. |
| 4a | Finding the surface | `MeasureStart` | ARCore warm-up. Shutter disabled until tracking is stable. "Type it" always available. |
| 4 | Measure | `Measure` | One instruction at a time. Three edges measured separately. Undo point. Provenance recorded per edge. |
| 4b | Review measurements | `MeasureReview` | Every value editable. Provenance badge on each. Edge-gap setting reachable. |
| 5 | Items | `Items` | Number + name + colour + dims + quantity. Rotation and "nothing on top" flags visible. |
| 5b | Add an item | `ItemEditor` | Dimensions are the item's widest envelope, handles included. Suggested label is editable and never a size source. |
| 5c | Item photo | `ItemPhoto` | States outright that a photo does not set the size. Skippable. |
| 5d | Saved items | `ItemLibrary` | Pack Plus. Measure once, reuse anywhere. |
| 6 | Plan result | `PlanResult` | Preview dominates. Pieces placed / modelled fill / left over. Unplaced items get a reason and a caveat. |
| 6b | Layer view | `PlanLayers` | Top-down per layer, with free space marked. |
| 7 | Packing guide | `PackingGuide` | One item, numbered step, orientation, reference edges named front/back/left/right — never camera-relative. |
| 7b | Packed | `PackingDone` | Result summary, the honest "did it actually fit?" question, then the upgrade moment. |
| 8 | Pack Plus | `Upgrade` | Shown only after a result. Localised price, billing period, renewal terms. |
| 9 | Settings | `Settings` | Account row, measuring, notifications, subscription, destructive delete. Nav bar floats over content. |
| 10 | Notification centre | `Notifications` | |
| 10b | Notification settings | `NotificationSettings` | Three types. No marketing, no streaks. |

---

## Band 3 — Recovery states

All twenty are real screens, not toasts. Each keeps a way forward that does not need the camera.

**Measuring** — R1 camera denied · R2 AR unsupported (hardware, permanent) · R3 AR services
missing · R4 low light · R5 tracking lost · R6 no reliable measurement (refuses to guess,
asks for a tape measure).

**Input** — R7 invalid dimensions (inline, blocks Next) · R8 oversize item (compares longest
edges, offers to save it unplaced).

**Solver & guide** — R9 timeout (offers best-so-far) · R10 no arrangement found (three
concrete suggestions) · R11 it doesn't fit (four causes, each routing somewhere, nothing erased).

**Billing** — R12 succeeded · R13 pending · R14 cancelled · R15 failed (with a ref code) ·
R16 offline. Entitlement follows verified state, never a local "payment clicked" boolean.

**Data & session** — R17 delete confirmation · R18 deleted, with undo · R19 interrupted
session · R20 sync conflict (**never merge silently** — the user picks, or keeps both).

---

## Layout conventions

- Frame 412 × 916 dp.
- Top 30 dp and the bottom gesture strip are left empty for real system bars. Do not draw
  a status bar or a keyboard.
- Screen gutter 20 dp. Nothing tappable under 44 dp.
- Bottom nav is a floating dark pill with a centre FAB — it sits *over* content, so long
  screens scroll behind it rather than pushing it off.
- Sheets: 30 dp top corners, grabber, scrim `rgba(43,29,20,0.48)`.
- Camera screens are full-bleed with glass circular controls and a white bottom sheet.
