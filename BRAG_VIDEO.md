# Making the Pack a Bunch video with /brag

Brag is already in this repository (`.claude/skills/brag` and `.claude/skills/brag-slim`,
MIT licence), so any Claude Code session opened on this repo can use it — nothing to install.

## 1. Start a new session

claude.ai/code → New session → repository **DrGitman/Pack_a_Bunch**, branch **main**.
(Optional, to have it everywhere: claude.ai → Customize → Plugins → Add marketplace →
`latent-spaces/brag`, then install **brag**.)

## 2. Paste this as the first message

```
let's /brag --full --duration 115 about this app.

Read PRODUCT.md first; it is the brief. The video must be between 1:50 and 2:00
(aim for 115 seconds) — longer than brag's usual 20 s, on purpose: plan the storyboard
so the scene durations sum to 115 s.

Structure, in this order:
- 0–8 s: the pain — a pile of things, one car boot, "Will it all fit?"
- by 25 s: the aha — point the phone, everything is measured where it stands.
- 25–95 s: ONE workflow end to end, as benefits: scan the car boot → scan the things
  (several at once) → the 3D plan → the step-by-step packing guide → it fits.
- 95–108 s: Pack Plus (unlimited packs, pieces, scans; weekly or monthly).
- 108–115 s: one CTA: "Try Pack Plus — code on the Pack Plus page".
Captions on every scene (works muted), clean music, no logo intro, no login/loading.

Use these real screens (all in the repo or the promo folder):
- promo/pack-a-bunch-demo.mp4 — the current cut; reuse its screens and order as reference.
- app/src/main/res/raw/*.json — the app's own Lottie icons, for motion accents.
Brand colours: #F7EFE6, #3F2718, #A65C34, #F2A03D.

Save the result as promo/brag-pack-a-bunch.mp4, plus the share copy, and commit it as
DrGitman <orilionaobeb@gmail.com> with no co-author, then push to main.
```

## 3. If it asks

- Full or slim: **full** (Hyperframes, with music). Slim is fine if full fails to render.
- Screens: add phone screenshots to `promo/screens/` in the repo before starting if you want
  real footage rather than recreated UI.
