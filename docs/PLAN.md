# Pack a Bunch — 5-Day Build & Google Play Launch Plan

<aside>


**Build the trustworthy packing assistant that is also an universal space scanner.**

Recommended first release: an Android app that helps people measure a space, add rigid items, find a practical packing arrangement, and follow a visual packing sequence. Manual measurement is always available. AR assists measurement on supported devices. Local-first storage and on-device packing keep operating costs low.

Budget: **60 focused working hours across five days**, including testing and contingency. A test-ready build and public-store launch is the target.

</aside>

## 1. Product promise and honest boundaries

**Working name:** Pack a Bunch.
**Positioning:** “Know what fits. See where it goes.”
**First audience:** people packing rigid objects or small boxes into an open storage box, crate, rectangular compartment, or any space they scan.
**First-session success:** create a space, add some items, see a plausible arrangement, and follow the first placement without a tutorial or account signup.

The full idea combines several difficult systems: metric measurement, object recognition, geometry reconstruction, constrained 3D packing, and AR guidance. These are separate problems. Recognizing a shoe does not reveal its dimensions, unless we use logic to look up that shoe or specific objects dimensions on the internet which is what we also will need to add. Knowing total item volume does not prove that the items fit. A visually convincing AR overlay does not prove that the packing arrangement is physically achievable. But remember it is still achievable with we well executed plan.

### What ships, and what waits

| Capability | Five-day decision | Honest user-facing wording |
| --- | --- | --- |
| Rectangular container dimensions | Required: manual entry; AR-assisted entry behind a quality gate | “Measure the inside of your space” |
| Item dimensions | Required: editable width, depth, height; rigid cuboid approximation | “Confirm the space this item needs” |
| Item photo and name | Local photo/name; optional suggested category if time permits | “Suggested label — tap to change” |
| Packing arrangement | Required: deterministic heuristic, allowed rotations, boundary and overlap checks | “Suggested arrangement” |
| Visualization | Required: labeled 3D cuboids or a polished isometric fallback plus layer view | “See where each item goes” |
| Guided packing | Required: numbered diagram steps, next/back, mark packed | “Place item 2 at the back left” |
| Live AR placement | Stretch only after the core is stable | “Experimental AR guide” |
| Saved projects and item reuse | Required, stored locally | “Saved on this device” |
| Subscription | One simple paid tier via Google Play and RevenueCat; must pass purchase tests | Clearly priced recurring subscription |
| Arbitrary mesh scans and exact dimensions | If possible in this release | We aim to help users with “exact” or “scan anything” |
| Backpacks, pencil cases, soft clothing | If possible in this release manual approximations may be explored, along with advertised supported scanning | “Flexible spaces are supported” |
| Cupboards and vehicle trunks | If possible in this release: openings, fixed obstacles, shelves, and access paths complicate fit | There should be vehicle-loading in this v1 if possible |
| Trucks/commercial loading | If possible in this release as a specialist product | If possible having weight balance, axle-load, or load-securing advice |

**Why the specific scope matters:** ARCore Depth is most accurate around 0.5–5 metres from the scene, with movement improving results. Tiny objects, reflective surfaces, thin edges, occlusion and low-texture interiors are poor foundations for an “exact scanner" so interventions for this will need to be added as logic to the overall functionality of the application. Depth is not available on every ARCore-capable phone. These are reasons for a manual fallback and device testing, not reasons to abandon the idea. [ARCore Depth](https://developers.google.com/ar/develop/depth), [device support](https://developers.google.com/ar/devices). Also if the are other workarounds or soultions we can use for this same goal we can use that.

### Two definitions of launch

- **Engineering target:** a signed Android App Bundle, tested core journey, configured subscription, store assets, and a submitted internal/closed-test release by the end of the sprint, subject to account readiness.
- **Public launch:** available to general Google Play users after applicable testing, verification, production access, and review. This timeline is outside the 60-hour coding budget.

For personal accounts created after 13 November 2023, Google requires at least **12 testers opted in continuously for 14 days** before applying for production access. Internal testing does not replace that closed test. An existing eligible account may submit to production sooner, but approval is not guaranteed within five days. [Official testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).

## 2. Design direction from the supplied references

**Take the calm utility aesthetic, not the marketing exaggeration.** The room-scanning examples supply camera-first interaction, crisp measurement handles, blue accents, and spatial previews. The nutrition example supplies clean cards, strong hierarchy, and an obvious capture action. The furniture example supplies restrained outlines over a real camera image.

### Visual references

![Camera-first measurement and spatial preview inspiration](65165126-3413-44d9-94e3-13d257fdd1ae.webp)

Camera-first measurement and spatial preview inspiration

![Card hierarchy and capture-flow inspiration](6ac45072-e79a-4932-92a5-1758be331fbc.webp)

Card hierarchy and capture-flow inspiration

Use these as inspiration only. Do not copy another app’s brand, screenshot assets, icons, ratings, or unsupported claims. In particular, avoid percentages such as “145% optimization” without a defined baseline and measurement.

### Starter design tokens — proposals, not extracted measurements

- Background: warm off-white `#F7F7F2`; surfaces: white `#FFFFFF`.
- Main text: charcoal `#17201C`; primary action: blue `#245BC4`.
- Success: dark green `#216449`; caution: dark amber `#8C5700`; errors: dark red `#B42318`.
- System Roboto typography. Suggested sizes: headline 28 sp, section title 20 sp, body 16 sp, supporting text 14 sp.
- Spacing scale: 4 / 8 / 12 / 16 / 24 / 32 dp; screen gutters 20 dp.
- Cards: approximately 20 dp corner radius; buttons 52 dp high; interactive targets at least 48 dp.
- Short 150–250 ms transitions. Haptics on a successfully placed measurement point and completed packing step, not continuously while scanning.
- Distinguish items by **number + label + color**, never color alone. Check contrast, TalkBack labels, large text, keyboard behavior, and Android system back navigation.
- Respect Android edge-to-edge insets; no simulated iPhone notch or iOS-only navigation in Android screens.
- I would like to go with brown as the main app color as boxes are brown

The following is also a suggested brown app design:

![Suggested app designs that can be used](fc248c96c6a0879135144ddb8492bc31.webp)


![Suggested app designs that can be used](original-760f8f0937f45448a4e491dace2e4afa.webp)

Suggested brown app design

The following is also a suggested logo and theme app design:

![Suggested app designs that can be used](Screenshot1.png)

![Suggested app designs that can be used](Screenshot2.png)

![Suggested app designs that can be used](Screenshot3.png)

![Suggested app designs that can be used](Screenshot4.png)

![Suggested app designs that can be used](Screenshot5.png)



### Screen map and acceptance criteria

1. **Welcome / demo**
    - One sentence: “Make the most of the space you have.”
    - Primary action: “Plan a pack.” Secondary: “Try a sample.” No signup wall.
    - Camera permission is not requested until scanning or taking a photo.
2. **Projects**
    - Saved projects with thumbnail, space name, item count, and last edited time.
    - Obvious “New pack” action; useful empty state and sample project.
3. **Create space**
    - Rectangular box/crate/compartment naming presets; dimensions are never invented from a preset name.
    - “Enter measurements” and “Measure with camera,” with supported-device explanation.
    - Width/depth/height diagram; emphasize **inside dimensions** and an unobstructed opening.
4. **Measurement / review**
    - Full-screen camera, single reticle, sparse dimension chips, and bottom instruction card.
    - One instruction at a time. “Undo point” and “Enter manually” remain available.
    - Confirmed measurements show units and provenance: manually entered or AR estimate.
5. **Items**
    - Photo or color placeholder, name, width/depth/height, quantity, rotation setting, and optional “nothing on top.”
    - Duplicate an item instead of repeating the whole entry process. Validate positive dimensions.
6. **Plan result**
    - Spatial preview dominates. Below it: items placed, modeled occupied volume, and items not placed.
    - View controls: overview / layer view; primary action: “Start packing.”
    - Explicit “No arrangement found for these items” rather than an unsupported “impossible.”
7. **Packing guide**
    - One highlighted item, numbered step, orientation, reference edges, and next/back.
    - “This doesn’t fit” returns to dimension review without erasing the project.
    - Distances reference the container’s labeled front/back/left/right, not ambiguous camera directions.
8. **Upgrade and settings**
    - Upgrade only after experiencing a result or requesting a paid feature.
    - Settings: units, restore purchases, manage subscription, delete local data, privacy, terms, support.
    - Unavailable products show a retry/help state, never a fake purchase success.

**Critical recovery states:** camera denied; AR unavailable; AR services need installation; low light; tracking lost; no reliable measurement; invalid dimensions; oversize item; solver timeout; no arrangement found; purchase pending/cancelled/failed; offline billing; deleted project; interrupted session.

## 3. Recommended low-cost technology stack

**Default recommendation: native Android with Kotlin and Jetpack Compose.** This optimizes for Android hardware access and Google Play delivery, rather than adding a cross-platform AR bridge during a short sprint.

| Layer | Choice | Cost and rationale |
| --- | --- | --- |
| UI and navigation | Kotlin or Flutter, Jetpack Compose, Material 3, Navigation Compose | Free tooling; customize tokens for the reference aesthetic |
| State | ViewModel, StateFlow, coroutines; simple dependency injection | Minimal moving parts; no elaborate framework required |
| Local persistence or online real time database | Room for projects/items/plans; DataStore for settings | No application server or database hosting bill |
| Photos | CameraX, app-private files, Android photo picker if importing | Photos stay local; avoid broad media permissions |
| Measurement | ARCore with AR Optional configuration | Local tracking/hit tests; no cloud AR service in v1 |
| Spatial renderer | SceneView/Filament for cuboids and optional AR | Open-source; pin a tested release and inspect dependencies/licence |
| Rendering fallback | Compose Canvas isometric/layer diagrams | Use if the SceneView spike fails; do not build two full renderers |
| Packing engine | Pure Kotlin heuristic, run off the UI thread | No cloud compute, LLM fees, or solver service |
| Category suggestions | Optional bundled ML Kit image-labeling model | On-device suggestions; never a dimension source |
| Subscription | Paddle or RevenueCat Android SDK + Google Play Billing | RevenueCat currently free up to $2,500 monthly tracked revenue, then 1% of tracked revenue at the threshold |
| Design | Penpot or Figma Starter; Compose previews; supplied references | Free path; check plan limits before relying on paid-only features use also a mcp for Modin because it allows Claude to learn from many designs and see what really works best |
| Coding | Android Studio + Git; Or rather what id like VS code Claude Code  | IDE is free; Claude Code is not an unlimited free development service |
| Repo/build | GitHub Free repository, local Gradle builds | Avoid paid build pipelines; optional CI within free allowances |
| Public privacy/support page | Static page on a free host such as Cloudflare Pages | No custom domain needed initially; verify host terms/limits |
| Distribution | Google Play Console | US$25 one-time developer registration; store fees apply to sales |

Sources: [SceneView](https://github.com/sceneview/sceneview), [ML Kit](https://developers.google.com/ml-kit/vision/image-labeling), [RevenueCat pricing](https://www.revenuecat.com/pricing), [Play registration](https://support.google.com/googleplay/android-developer/answer/6112435?hl=en).

The following is also a suggested free stack:

![Suggested stack that can be used](suggested_stack.jpeg)

Suggested stack that can be used

### Why not automatically use React Native, Flutter, or Unity?

- **React Native/Expo:** sensible if already your strongest stack and you reduce v1 to manual measurements + diagrams. Native AR integration introduces custom native builds and bridge/plugin risks; do not assume it works in a stock preview environment.
- **Flutter:** sensible with existing Flutter and AR experience. For a newcomer, plugin lifecycle and device compatibility can consume the sprint.
- **Unity:** choose it if you already know Unity and the product is primarily an immersive 3D experience. Otherwise its mobile UI, package size, and workflow are unnecessary overhead for this utility.
- **Native Kotlin:** selected here because Android-only delivery and AR access are priorities. If Kotlin is entirely new, the realistic five-day outcome may be a smaller prototype or manual-first beta. AI assistance does not remove hardware debugging or platform learning.

### Architecture that stays small

```
Compose screens → ViewModels → domain operations
                            ├─ MeasurementProvider (manual / ARCore)
                            ├─ PackingEngine (pure Kotlin)
                            ├─ ProjectRepository (Room + private image files)
                            └─ EntitlementRepository (RevenueCat)

PackingPlan → renderer + numbered packing guide
```

Use package boundaries rather than a large multi-module architecture on day one. Keep the packing engine independent of Android classes so it can be unit tested without a device.

### Core data contracts

```
Space: id, name, widthMm, depthMm, heightMm, marginMm,
       measurementSource, updatedAt
Item: id, projectId, name, photoPath?, widthMm, depthMm, heightMm,
      quantity, allowedRotations, maySupportItems, measurementSource
Placement: itemInstanceId, xMm, yMm, zMm, orientedWidthMm,
           orientedDepthMm, orientedHeightMm, rotation, sequenceIndex
PackingPlan: id, spaceId, inputRevision, solverVersion,
             placements[], unplacedInstances[], metrics
Project: id, spaceId, name, currentPlanId?, updatedAt
```

- Store lengths as integer millimetres; convert only at the UI boundary. Integer units are a calculation convention, not a claim of millimetre measurement accuracy.
- Use `Long` or safe numeric conversion before volume multiplication to avoid overflow.
- Solver axes: X left-to-right, Y front-to-back, Z upward. Renderer/AR conversion must be explicit because the graphics engine may use Y-up and metres.
- Any dimension or rotation edit invalidates the old plan. Persist enough inputs to recompute and test versioning.
- Persist project progress after each confirmed action; do not depend on the camera session surviving Android process death.
- Keep signing credentials and service-account secrets out of Git and the app. RevenueCat’s client SDK key is distinct from a private server credential.

## 4. Measurement implementation: assisted, not magical

### Mandatory manual path

Show a small annotated cuboid diagram beside the three inputs. Accept centimetres or inches in the UI and normalize to millimetres. Ask the user to measure the container’s interior and the item’s **maximum external envelope**, including handles or protrusions. A photo helps identification and should be able to supply scale if possible.

### AR path, gated on day-one evidence

1. Check camera permission, ARCore availability, and AR-services installation at runtime. Keep the app installable without AR.
2. Guide the user to move slowly in good light until tracking is stable, if possible allow that torch of flashlight is enabled in bad lighting.
3. Measure **three separate edge lengths** using user-selected endpoint pairs on visible surfaces. Use valid hit tests, not arbitrary 2D pixel distances.
4. For the first implementation, measuring width, depth and height separately is preferable and if possible we can also have complex automatic box reconstruction. It avoids pretending that occluded interior corners are visible and provide a starting ground we can expand upon.
5. Display the proposed numbers for editing and confirmation. If an edge cannot be observed reliably, request a tape-measure value.
6. Reject/redo measurements when tracking is paused or the endpoints are unreliable; do not fabricate a confidence percentage.
7. Save only confirmed lengths. An AR tracking state is not an experimentally calibrated accuracy guarantee.

**AR ship gate:** on the available phones, compare several real containers/items against a tape measure at different distances and in different lighting. Record absolute and relative errors. A proposed exploratory target is no worse than 2 cm or 5%, whichever is larger, for supported test objects; this is a validation target, and a core product feature, hopefully not too loose for tight packing. If results are unstable,  we must re-iterate until we get it right and can ship manual measurements and AR functionalities.

**Small-object policy:** pencil cases and tiny objects stay manual-only for now if the space automatically will allow for that. Do not ask users to fit tiny objects into an AR workflow designed for larger visible surfaces if the space will allow for that.

**Optional recognition:** take a single-item photo, run the bundled ML Kit labeler, and offer an editable category. The default model has 400+ broad labels; it is not a product/SKU recognition engine. “Item 1” is a valid fallback. If possible we can spend some time in the sprint training a model or calling an LLM for geometry.

**Camera ownership:** do not run independent CameraX and ARCore camera sessions simultaneously. Explicitly release one before entering the other flow.

**Optional live placement:** users define an origin, floor plane and container orientation in the current AR session; placements become wireframe cuboids under one shared anchor. Showing saved solver coordinates alone is insufficient without that transform. On drift, tracking loss, or reopening, require realignment. Never silently reuse a stale world origin.

References: [AR Optional setup](https://developers.google.com/ar/develop/java/enable-arcore), [Depth support and enablement](https://developers.google.com/ar/develop/java/depth/developer-guide), [ML Kit Android labeling](https://developers.google.com/ml-kit/vision/image-labeling/android).

## 5. Packing engine: useful geometry without an AI dependency if possible

**Use a bounded heuristic, not a claim of mathematical optimality.** Three-dimensional packing becomes much harder with rotations, irregular shapes, stability, and access constraints. A deterministic box-packing engine is enough to demonstrate the core value with added AI processing logic as a means to even more improve the in-flexibilities of a rigid deterministic box-packing engine.

### Supported model

- One empty, rectangular, unobstructed container, loaded through an open top.
- Rigid cuboid items or deliberately conservative cuboid envelopes.
- Up to 20 item instances for the initial supported performance scope, in both free and paid tiers.
- Axis-aligned placements, at most six unique orientations; “keep upright” restricts these.
- No deformability, nesting inside items, liquid behaviour, irregular mesh collision, or moving obstacles.
- Treat packing safety and load capacity as user responsibilities; no inference of weight or structural strength from a photograph.
- Spaces of different spaces if possible
- Items of different shapes if possible too

### Algorithm outline

1. Validate dimensions and expand quantity into unique item instances.
2. Apply a configurable container boundary margin. Make the effect visible; do not present a universal tolerance as a calibrated guarantee.
3. Try several deterministic orderings, such as descending volume, longest edge, and footprint.
4. Maintain candidate positions at the origin and exposed corners of placed boxes; prune duplicates and invalid points.
5. For each candidate/orientation, check container bounds and pairwise axis-aligned overlap.
6. Reject floating placements. For the simplest conservative rule, an item rests on the floor or has its whole base supported by one flat-topped item explicitly allowed to support another. Treat this as a geometry rule, not a physical load certification.
7. Prefer lower, supported placements and compact use of space; select among feasible candidates.
8. Rank finished attempts by number of items placed, then packed volume, then compactness. Document the objective; it is not the same as a proven optimum.
9. Apply a runtime budget and cancellation. Return the best valid plan found so far, with unplaced items and clear reasons where known.
10. Revalidate the final plan independently before displaying it. Generate a support-respecting bottom-up sequence for the supported open-top loading model.

```
for each deterministic item order:
    plan = empty
    for each item instance:
        candidates = valid positions × allowed rotations
        candidates = keep(boundary-safe, non-overlapping, supported)
        place the best candidate, or record unplaced
    independently validate plan
return best validated plan found within the time budget
```

### Metrics that do not mislead

- **Modeled occupied volume:** sum of placed item-envelope volumes divided by usable modeled container volume.
- **Items placed:** placed instances / requested instances.
- **Unplaced:** specific items the current search did not place. A heuristic failing to place an item is not proof that it can never fit.
- **Geometric empty volume:** arithmetic remainder, **not** necessarily usable space for another item.
- Show “approximate” when dimensions or shapes are estimates. Do not compute “space saved” without a measured alternative arrangement.

### Unit tests before visual polish

- One item fits; oversized item; exact boundary contact; margin makes an exact fit invalid.
- Two boxes touch but do not overlap; a one-unit overlap is rejected.
- Item fits only after rotation; upright restriction prevents a forbidden rotation.
- Duplicate items receive different instance IDs; quantities and units round-trip correctly.
- No negative/zero/invalid dimensions; no integer overflow in volume calculation.
- Every placement lies within bounds and respects support rules.
- At least one fixture where total volume is small enough but shapes still cannot fit.
- At least one fixture where a naïve ordering fails but another heuristic ordering succeeds.
- Determinism, cancellation, stale-plan invalidation, and persistence after app restart.
- Proposed performance target: return a valid result for the 20-item fixture within two seconds on the actual mid-range test phone. Benchmark rather than advertise this unmeasured target.

## 6. Subscription model without destroying the first experience

**Recommendation:** prove one packing result before asking for money. This is an episodic utility, so willingness to subscribe is uncertain. Begin with one monthly product, not a complex weekly/annual/trial ladder.

### Initial packaging — hypotheses to validate

**Free**

- Manual measurements and any validated AR measurement mode.
- One saved project, up to 20 item instances, result visualization and packing guide.
- Existing data stays viewable when a subscription expires.

**Pack Plus**

- Multiple saved projects and reusable item entries across projects.
- Scanning of any space and even more items
- Same validated item-count and geometry limits as Free; payment must not imply unsupported capability.
- Test a US$2.99–4.99 monthly price range; choose one actual price for the first cohort and let Google Play supply regional/localized prices. These are proposals, not validated pricing.

If recurring use is weak, consider a non-expiring one-time unlock later instead of forcing a subscription. Do not sell cloud sync, unlimited precision, or “AI optimization” before those features exist.

### Implementation checklist

- [ ]  Create a Google Play subscription, active monthly base plan and intended market availability.
- [ ]  Connect Play service credentials to RevenueCat securely; map the product to one `plus` entitlement and one offering.
- [ ]  Start setup on day one because account/product propagation can take time.
- [ ]  Use a current RevenueCat SDK whose resolved Play Billing dependency satisfies Google’s requirement; inspect the dependency tree, not just the top-level version.
- [ ]  Show localized product price, billing period, auto-renewal terms, cancellation information, and links to privacy/terms.
- [ ]  Purchase unlocks follow verified entitlement state, not a local “payment clicked” boolean.
- [ ]  Include restore purchases and Google Play subscription management access.
- [ ]  Test purchased, pending, cancelled, failed, renewal, expiration, refund/revocation, restore/reinstall, and temporary network loss with test accounts.
- [ ]  Use the SDK’s documented cached-entitlement behavior; allow local free functionality offline, and do not treat inability to refresh as a successful purchase.
- [ ]  Confirm anonymous-user/restore behavior and explain that restoring purchases does not restore locally deleted projects.
- [ ]  Hide purchasing if products are unavailable or testing has failed. A non-monetized beta is better than a broken paid release.

RevenueCat handles subscription infrastructure; it does not replace Google Play Billing or exempt the app from store policies. Use Play Billing for this straightforward digital-feature subscription rather than adding an external checkout. [Payments policy explanation](https://support.google.com/googleplay/android-developer/answer/10281818?hl=en).

## 7. Mobbin MCP + Claude Code workflow

**Mobbin MCP is optional and paid.** The official page lists MCP on paid plans. Mobbin Pro is advertised at **US$10/month billed yearly**—approximately US$120 before taxes paid for the year, not a US$10 one-month purchase. Check quarterly checkout if preferred. Claude Pro currently lists US$20 month-to-month and includes Claude Code with usage limits; 12-hour workdays do not imply unlimited AI usage. [Mobbin pricing](https://mobbin.com/pricing), [Claude pricing](https://claude.com/pricing).

### Setup

1. Use the verified Mobbin connector in Claude Desktop/Web and authorize with your Mobbin account.
2. Mobbin’s official Claude Code guide recommends this shared-connector route; confirm access from the CLI before relying on it.
3. Keep design research time-boxed. No Mobbin connection has been configured as part of this plan.

Official instructions: [Claude Desktop/Web](https://docs.mobbin.com/mcp/clients/claude-desktop-web), [Claude Code CLI](https://docs.mobbin.com/mcp/clients/claude-code-cli).

### Design prompt

```
Use Mobbin to find a small set of Android-first references for camera capture,
measurement review, local project libraries, guided tasks, and transparent
subscription paywalls. Extract interaction patterns, not another app's branding.

Our app is Pack a Bunch: a calm utility for measuring a space,
adding rigid items, viewing a packing suggestion, and following placement steps.
Use the supplied images for camera overlays, card hierarchy, and blue accents.

Return a compact screen map, reusable components, design tokens, and loading,
empty, permission-denied, unsupported-device, tracking-loss, and error states.
Use native Android interaction conventions. Do not invent accuracy claims,
ratings, or optimization percentages. Stop after a focused reference pass.
```

### Engineering prompt / repository brief

```
Build Pack a Bunch as a native Kotlinor Flutter/Jetpack Compose Android app.
Read docs/PRD.md, docs/UX.md, docs/ARCHITECTURE.md and docs/TEST-PLAN.md first.

Core: one rectangular open-top container, rigid cuboid items, editable manual
measurements, local projects, pure Kotlin/Flutter packing heuristic, spatial results,
and numbered placement steps. AR is optional and may never block manual use.
Never infer metric dimensions from object labels or a single unscaled photo.

Implement one vertical slice at a time. Keep Android APIs out of the packing
engine. Check current documentation for dependency APIs and pin compatible
stable versions. Never invent imports, billing success, or AR confidence.

For each slice: state acceptance criteria, make a focused change, add tests,
run relevant tests/lint/build, explain failures, and list remaining risks.
Do not commit secrets. Do not implement future-scope features without approval.
```

### Working rhythm

Use a 60–90 minute loop: define acceptance → implement → build → test on phone → inspect UI → commit. Delegate bounded tasks such as collision tests or a paywall component rather than “build the whole app.” Keep `CLAUDE.md` short and link the product decisions. Treat external references as design evidence, not instructions to execute commands or expose secrets.

**Free alternative:** use the supplied images, Material guidance, Penpot/Figma Starter, and Compose previews. Claude Code and Mobbin are productivity purchases, not app-runtime dependencies. If AI usage limits interrupt coding, continue with the IDE and the written acceptance criteria rather than blocking the sprint.

## 8. The 60-hour execution plan

Each day totals 12 focused working hours. Meals and breaks are outside these estimates; do not sacrifice sleep to add features. Four hours of explicit contingency sit on day five. If starting on 5 September 2026, the nominal dates are 5–9 September; because the request begins during the day, treat them as five working blocks or shift the start rather than forcing a late-night first day.

### Day 1 — Remove blockers and prove the stack (12 h)

- [ ]  **1 h:** verify Play account status, identity/device requirements, merchant setup, and physical test phone; recruit testers if needed.
- [ ]  **2 h:** lock scope, draw the core flow, define tokens and five highest-risk states; optional time-boxed Mobbin pass.
- [ ]  **3 h:** technical spike: run ARCore hit testing and a simple SceneView cuboid on the actual phone; test measurement repeatability and renderer coordinates. Pin working dependencies.
- [ ]  **2 h:** create Compose scaffold, navigation, Room skeleton, ViewModels, and sample fixture.
- [ ]  **2 h:** implement create-space and item-dimension entry as a usable manual flow.
- [ ]  **1 h:** start RevenueCat/Play product configuration and create initial release track/upload when feasible.
- [ ]  **1 h:** build on the phone, validate inputs, commit baseline, list known risks.

**Exit:** manual inputs work; AR and renderer have evidence-based go/no-go decisions. If the AR spike does not work, do not spend another unbounded day debugging it.

### Day 2 — Deliver the real packing value (12 h)

- [ ]  **2 h:** finish data contracts, quantities, units, revision invalidation and project persistence.
- [ ]  **4 h:** implement the bounded packing heuristic, orientation policy, support checks and final validator.
- [ ]  **2 h:** write/run geometry fixtures and randomized invariant tests.
- [ ]  **2 h:** show placements using the chosen renderer; add labels and a basic layer view.
- [ ]  **1 h:** create a support-respecting numbered packing guide.
- [ ]  **1 h:** save/reopen plans and test full manual journey.

**Exit:** one real box and five measured objects produce a valid, saved, understandable packing suggestion. This must work before optional AI or AR polish.

### Day 3 — Measurement and visual clarity (12 h)

- [ ]  **4 h:** integrate guided AR measurement if the spike passed; otherwise improve manual measurement help and reuse flows.
- [ ]  **2 h:** implement measurement review, manual correction, unavailable-device and tracking-loss recovery.
- [ ]  **1 h:** item photo/name flow; add label suggestions only if photo handling is already stable.
- [ ]  **2 h:** refine cuboid colors, transparency, camera framing, selected-item highlight and visual guide.
- [ ]  **2 h:** compare measurements against a tape measure; test physical placement and non-AR fallback.
- [ ]  **1 h:** integrate changes, regression check, publish updated test build where available.

**Exit:** no feature requires trusting an uneditable camera estimate. No invalid geometry is shown as a successful plan. Live AR placement remains optional, not a hidden extra day of work.

### Day 4 — Billing, usability and reliability (12 h)

- [ ]  **3 h:** finish subscription offering, localized paywall, restore/manage flow, and entitlement handling.
- [ ]  **2 h:** polish empty/error states, typography, large text, TalkBack, insets and navigation.
- [ ]  **2 h:** billing lifecycle tests and offline/permission/process-death QA.
- [ ]  **2 h:** observe three people creating a plan without coaching; record confusion and time to first useful result.
- [ ]  **2 h:** fix the most consequential usability and correctness issues.
- [ ]  **1 h:** draft privacy/support/terms pages and accurate store text; inventory SDK data use.

**Exit:** a stranger can finish the core journey, and purchases work through verified entitlements or are disabled for the beta. Freeze feature scope.

### Day 5 — Release readiness and contingency (12 h)

- [ ]  **3 h:** run end-to-end regression on available devices, benchmark the 20-item case, inspect memory/camera lifecycle and release build.
- [ ]  **2 h:** final UI correction and real app screenshots, icon, feature graphic, and short demo.
- [ ]  **2 h:** complete Console declarations, validate the signed bundle, submit the appropriate test/production track.
- [ ]  **1 h:** prepare support responses, tester instructions, release notes and recovery/hotfix process.
- [ ]  **4 h:** reserved for build, billing, camera, measurement and submission blockers—not new features.

**Exit:** reproducible signed build, version tag, accessible tester instructions, known-limitations list, and release submission where account readiness permits. Store review or a closed-testing period may continue after the sprint.

### Cut order when time runs short

1. Remove live AR placement.
2. Remove automatic label suggestions.
3. Remove custom animations, extra themes and export.
4. Reduce visual sophistication to the tested diagram renderer.
5. Hide unreliable AR measurement and keep manual measurement.
6. If billing fails its gate, release a clearly non-monetized beta and finish billing before paid launch.

**Never cut:** geometry validation, editable dimensions, saved progress, a usable manual path, truthful claims, privacy disclosures, billing correctness for any enabled purchases, and release signing/security.

## 9. QA, privacy and Google Play deployment

### Current release requirements to verify in Console

Research snapshot: **5 September 2026**. Recheck the final Console requirements at submission.

- New ordinary Android phone/tablet apps must target **Android 16 / API 36** from 31 August 2026, unless an applicable approved extension exists. Set `compileSdk` appropriately and choose a tested `minSdk` based on dependencies; API 26 is a reasonable starting proposal, not the Play target requirement. [Target API policy](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en).
- New submissions need **Play Billing Library 8 or later** under the current deadline; check the version pulled in by RevenueCat. [Billing release notes](https://developer.android.com/google/play/billing/release-notes).
- Validate **16 KB memory-page compatibility**, particularly because AR/rendering SDKs can bundle native libraries. Test the release bundle and inspect dependency binaries rather than assuming a Kotlin application is exempt. [Android compatibility guide](https://developer.android.com/guide/practices/page-sizes).
- Register and verify the Play developer account early; US$25 is a one-time registration fee, not a hosting subscription. New personal accounts also have device-verification requirements.
- If registering from Namibia, Google’s supported-locations table currently lists both developer and merchant registration as supported, with USD as the listed merchant default currency. Individual account, bank, identity and payout approval still need confirmation. [Supported registration locations](https://support.google.com/googleplay/android-developer/answer/9306917?hl=en).

### Store checklist

- [ ]  Choose a permanent package name and final app name after a basic brand-conflict check.
- [ ]  Enable Play App Signing, securely back up the upload key, exclude credentials from Git, and record version code/name.
- [ ]  Build and install a release variant; upload signed AAB, not just a debug APK.
- [ ]  Inspect bundle compatibility, supported devices, native libraries and pre-launch reports where available.
- [ ]  Supply icon, feature graphic, real Android screenshots, short/long descriptions and support contact; validate current Console asset requirements.
- [ ]  Complete content rating, target audience, ads declaration, app access instructions, and all applicable permission declarations.
- [ ]  Publish a reachable privacy policy and accurately complete Data safety based on actual code and every third-party SDK.
- [ ]  If no account creation exists, do not add it just for launch. If accounts are added later, implement required account-deletion routes and disclosures.
- [ ]  Confirm active subscription product/base plan, country availability, tester access and licence-testing setup.
- [ ]  Provide reviewers a clear route through the manual path on non-AR devices. Never list live AR guidance if it did not ship.
- [ ]  Move from internal testing to the required closed track; recruit more than the minimum number of willing testers to absorb dropouts.
- [ ]  Record genuine test feedback and fixes. Apply for production access only when eligible and answer readiness questions truthfully.
- [ ]  After approval, release cautiously to the intended audience and monitor quality; use staged update rollouts where available.

### Privacy-first implementation

Keep photos and dimensions in app-private storage. No cloud photo upload or account signup in v1. Decide Android backup rules explicitly—exclude photos/projects from automatic cloud backup if promising device-only storage. Keep a delete-project/delete-all-local-data action and remove associated photo files.

Billing SDKs still communicate purchase/customer information externally; “local-first” does **not** mean “the app collects no data.” Review SDK disclosures and network behavior. Do not log photographs, raw purchase tokens, or unnecessarily identifying data. Use a free static support page and a working support email. Avoid adding analytics SDKs merely for vanity metrics during the sprint.

### Release quality gates — proposed acceptance criteria

- [ ]  Five-item manual workflow completes after a fresh install without requiring an account or AR.
- [ ]  No overlaps, out-of-bounds or unsupported floating placements in all test fixtures.
- [ ]  Every item that cannot be placed is visible in a clear result state.
- [ ]  Denied camera permission and unavailable AR never block manual planning.
- [ ]  Projects survive app restart and a simulated interrupted session.
- [ ]  Actual physical test packing agrees with the supported geometric model; disagreements trigger a limitation or algorithm fix, not a marketing workaround.
- [ ]  Twenty-item solve completes within the measured device budget without freezing the UI.
- [ ]  AR is validated on available hardware or explicitly omitted/experimental.
- [ ]  Every enabled purchase lifecycle passes tester validation; expiry does not erase projects.
- [ ]  At least three observed first-time users can reach a result; documented critical confusion is fixed.
- [ ]  No reproducible launch-blocking crash in the release build; limitations are documented.
- [ ]  One non-AR path, one physical AR-capable phone if AR ships, and a current Android/16 KB test environment have been checked. Broader device coverage continues in closed testing.

### Post-submission operations

For a new personal account, continue the closed test for the required period after the sprint. Watch Android vitals and Console feedback, collect a short report from testers, and fix reproducible issues. Time in review or testing is calendar time, not something twelve-hour coding days can compress.

Keep the previous known-good source tag and release artifacts. If a shipped build fails, stop further rollout where available and publish a higher-version-code hotfix; do not assume Google Play can instantly downgrade installed apps. Measure first-plan completion, measurement correction frequency, actual successful packing, repeat use and upgrade interest before adding more scanning technology.

## 10. Budget and commercial reality

### Minimum cash path

- Free design/IDE/local builds/local application storage.
- No metered production AI API and no application backend in v1.
- **US$25 one-time Google Play registration** if no account exists.
- Existing computer, compatible test device, internet and tester access assumed; hardware, tax and connectivity are not included.

### Optional speed-up spending

- Claude Pro: advertised **US$20 month-to-month**, subject to tax, usage limits and plan changes.
- Mobbin Pro: advertised **US$10/month billed yearly**, approximately **US$120 upfront** before tax; quarterly pricing must be checked at checkout.
- With those specific annual/monthly purchases plus a new Play account, the example initial outlay is approximately **US$165 before taxes**, not US$55. Existing subscriptions reduce incremental cost.
- RevenueCat: currently $0 below its advertised free threshold, then its published percentage fee; this is separate from Google’s fees.
- For this Play-billed auto-renewing subscription, model roughly **15% total store/billing fees under the cited current schedule**, while checking regional/program details. Taxes, refunds, currency conversion and RevenueCat charges affect net proceeds. [Google service fees](https://support.google.com/googleplay/android-developer/answer/112622?hl=en-EN).

**Conclusion:** app infrastructure can begin near zero monthly cost. A Google Play launch is not strictly free, and the requested Mobbin + Claude Code workflow is not entirely free either. Paid tools should accelerate the work, not become requirements for the app to run.