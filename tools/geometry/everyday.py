"""
Pack a Bunch — everyday objects, the second library of item shapes.

`families.py` holds the first 120 shapes, each written out by hand. This file adds a few
hundred more everyday things — furniture, tableware and utensils, clothing and accessories,
electronics and appliances, hygiene and grooming — built from a small set of parametric
builders: turned shapes (anything round), cabinets, tables, seats, bodies with fronts, bags
and garments, and long hand-held things. Every one is modelled at real size in millimetres,
then scaled into the same 1000 mm box and checked against the same contract as `families.py`
(four points per face, planar, counter-clockwise from outside, fills the box).

All geometry is original and authored here. Nothing is downloaded, imported, traced or
converted from any third-party asset.

Run from the project root:
    python3 tools/geometry/everyday.py app/src/main/res/raw
It writes family_<id>.json for each shape, `everyday_preview.png`-ready JSON, and regenerates
`app/src/main/java/com/packabunch/ui/render/EverydayFamilies.kt` — the names each shape
answers to — and the enum block in `FamilyMeshes.kt`.
"""

import json
import math
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import families as F  # noqa: E402

Mesh, box, lathe, sphere, torus, sweep, arc, bar, leg, soft_box, prism, hexahedron = (
    F.Mesh, F.box, F.lathe, F.sphere, F.torus, F.sweep, F.arc, F.bar, F.leg, F.soft_box, F.prism, F.hexahedron)

REGISTRY = []


def shape(name, words=(), phrases=(), kind=None, facing=False):
    """
    Registers one shape. `words` are single words an item's name can end in ("spoon"),
    `phrases` whole names matched anywhere ("soap dispenser"). `kind` tells the app how to lay
    it into the item's box: "round", "long" (runs along the longer side), "thin" (stands on
    its thinnest side when that is not its height).
    """
    def deco(fn):
        REGISTRY.append(dict(name=name, fn=F.facing_viewer(fn) if facing else fn, words=list(words),
                             phrases=list(phrases), kind=kind, doc=(fn.__doc__ or name.replace("_", " ")).strip()))
        return fn
    return deco


# ---------------------------------------------------------------------------------------------
# small helpers, all in millimetres


def rbox(x0, x1, y0, y1, z0, z1, r=None, edge=None):
    """A box with softened corners and edges: most moulded and upholstered things."""
    r = min(x1 - x0, y1 - y0) * 0.12 if r is None else r
    return soft_box(x0, x1, y0, y1, z0, z1, r, None, edge, segs=2, arc=1)


def cyl(cx, cy, r, z0, z1, sides=8, ry=None):
    return lathe([(1, z0), (1, z1)], sides, "z", (cx, cy), (r, ry or r))


def cylx(y, z, r, x0, x1, sides=8):
    return lathe([(1, x0), (1, x1)], sides, "x", (y, z), (r, r))


def cyly(x, z, r, y0, y1, sides=8):
    return lathe([(1, y0), (1, y1)], sides, "y", (x, z), (r, r))


def turned(profile, r, sides=12, cx=None, cy=None, ry=None):
    """A surface of revolution about the upright axis: profile is [(fraction of r, z mm), …]."""
    return lathe(profile, sides, "z", (cx if cx is not None else r, cy if cy is not None else (ry or r)), (r, ry or r))


def d_handle(x, y, z_mid, reach, span, thick, plane="xz", side=1):
    """A mug-style loop handle on the +X side (side=1) or −X side (side=−1)."""
    a0, a1 = (80, -80) if side > 0 else (100, 260)
    return sweep(arc((x, y, z_mid), max(reach, span / 2), a0, a1, 10, plane), thick, 8)


def legs4(x0, x1, y0, y1, z0, z1, r, inset, taper=0.8):
    m = []
    for x in (x0 + inset, x1 - inset):
        for y in (y0 + inset, y1 - inset):
            m += leg(x, y, r, z0, z1, taper)
    return m


def square_legs(x0, x1, y0, y1, z0, z1, s, inset=0):
    m = []
    for x in (x0 + inset, x1 - inset - s):
        for y in (y0 + inset, y1 - inset - s):
            m += box(x, x + s, y, y + s, z0, z1)
    return m


# ---------------------------------------------------------------------------------------------
# builders


def cabinet(w, d, h, rows, legs=0, plinth=0, top=0, handle="bar", leg_style="square"):
    """
    A carcass with its front at y = 0 (turned to face the viewer on registration).
    `rows` bottom to top: each (height share, [cell, …]) where a cell is "drawer", "door",
    "open" (a shelf opening), "glass" (a glazed door) or "blank".
    """
    m = Mesh()
    base = legs or plinth
    if legs:
        if leg_style == "round":
            m.add(legs4(0, w, 0, d, 0, legs, min(w, d) * 0.03, min(w, d) * 0.07))
        else:
            s = min(w, d) * 0.06
            m.add(square_legs(0, w, 0, d, 0, legs, s, s * 0.4))
    elif plinth:
        m.add(box(w * 0.02, w * 0.98, d * 0.06, d, 0, plinth))
    body_top = h - top
    t = max(12.0, min(w, d) * 0.04)
    # Carcass as separate panels, so the fronts are not painted over by one big box.
    m.add(box(0, t, 8, d, base, body_top)).add(box(w - t, w, 8, d, base, body_top))
    m.add(box(t, w - t, d - t, d, base, body_top))
    m.add(box(t, w - t, 8, d - t, base, base + t))
    if top:
        m.add(box(-top * 0.8, w + top * 0.8, -top * 0.4, d + top * 0.4, body_top, h))
    else:
        m.add(box(t, w - t, 8, d - t, body_top - t, body_top))
    total = sum(r[0] for r in rows)
    z = base + t
    usable = body_top - t - (base + t)
    gap = 4.0
    for share, cells in rows:
        rh = usable * share / total
        cw = (w - 2 * t) / len(cells)
        for i, cell in enumerate(cells):
            x0, x1 = t + i * cw + gap / 2, t + (i + 1) * cw - gap / 2
            z0, z1 = z + gap / 2, z + rh - gap / 2
            if cell == "open":
                m.add(box(x0, x1, 30, d - t, z0, z0 + t * 0.6))
                continue
            if cell == "glass":
                m.add(box(x0, x0 + 20, 0, 10, z0, z1)).add(box(x1 - 20, x1, 0, 10, z0, z1))
                m.add(box(x0 + 20, x1 - 20, 0, 10, z0, z0 + 20)).add(box(x0 + 20, x1 - 20, 0, 10, z1 - 20, z1))
                m.add(box(x0 + 20, x1 - 20, 6, 12, z0 + 20, z1 - 20))
            else:
                m.add(box(x0, x1, 0, 12, z0, z1))
            if cell in ("drawer", "door", "glass") and handle:
                hx = (x0 + x1) / 2 if cell == "drawer" else (x1 - 40 if i % 2 == 0 else x0 + 40)
                hz = (z0 + z1) / 2 if cell == "drawer" else min(z1 - 60, z0 + (z1 - z0) * 0.6)
                if handle == "knob":
                    m.add(lathe([(1, 0), (1, -14), (0.6, -18), (0.6, -22)], 6, "y", (hx, hz), (9, 9)))
                elif cell == "drawer":
                    m.add(box(hx - min(60, cw * 0.18), hx + min(60, cw * 0.18), -12, 0, hz - 5, hz + 5))
                else:
                    m.add(box(hx - 5, hx + 5, -12, 0, hz - 50, hz + 50))
        z += rh
    return m


def table(w, d, h, top_t=28, legs="four", shelf=False, round_top=False, drawer=False, apron=True):
    m = Mesh()
    if round_top:
        m.add(turned([(0.97, h - top_t), (1, h - top_t * 0.6), (1, h - top_t * 0.3), (0.97, h)], w / 2, 12, ry=d / 2))
    else:
        m.add(rbox(0, w, 0, d, h - top_t, h, min(w, d) * 0.03, top_t * 0.3))
    lz = h - top_t
    if legs == "four":
        s = max(30, min(w, d) * 0.06)
        m.add(square_legs(0, w, 0, d, 0, lz, s, s * 0.5))
        if apron and not round_top:
            m.add(box(s, w - s, s * 0.8, d - s * 0.8, lz - 70, lz))
    elif legs == "turned":
        inset = min(w, d) * (0.22 if round_top else 0.1)
        if round_top:
            m.add(legs4(w * 0.12, w * 0.88, 0, d, 0, lz, max(18, min(w, d) * 0.035), inset, 0.7))
        else:
            m.add(legs4(0, w, 0, d, 0, lz, max(18, min(w, d) * 0.035), inset, 0.7))
        if apron and not round_top:
            m.add(box(min(w, d) * 0.08, w - min(w, d) * 0.08, min(w, d) * 0.08, d - min(w, d) * 0.08, lz - 70, lz))
    elif legs == "pedestal":
        m.add(turned([(0.9, 0), (1, 25), (0.35, 60), (0.22, 120), (0.2, lz - 80), (0.35, lz)], min(w, d) * 0.3,
                     10, cx=w / 2, cy=d / 2))
    elif legs == "trestle":
        for x in (w * 0.12, w * 0.88):
            m.add(box(x - 40, x + 40, d * 0.1, d * 0.9, 0, 40))
            m.add(box(x - 25, x + 25, d * 0.4, d * 0.6, 40, lz))
        m.add(box(w * 0.12, w * 0.88, d * 0.45, d * 0.55, lz * 0.35, lz * 0.35 + 50))
    elif legs == "hairpin":
        for x in (w * 0.08, w * 0.92):
            for y in (d * 0.12, d * 0.88):
                m.add(sweep([(x, y - 40, lz), (x, y, 0), (x, y + 40, lz)], 6, 6))
    elif legs == "trestle_a":
        for x in (w * 0.1, w * 0.9):
            m.add(bar((x, d * 0.1, 0), (x, d * 0.5, lz), 18, 18)).add(bar((x, d * 0.9, 0), (x, d * 0.5, lz), 18, 18))
    if shelf:
        m.add(box(w * 0.06, w * 0.94, d * 0.08, d * 0.92, lz * 0.22, lz * 0.22 + 18))
    if drawer:
        m.add(box(w * 0.3, w * 0.7, -6, 20, lz - 90, lz - 10)).add(box(w * 0.46, w * 0.54, -14, -6, lz - 56, lz - 44))
    return m


def seat(w, d, h, seat_h=450, back="slats", arms=False, legs="square", cushion=True, runners=False):
    """A chair-like seat with its front at y = 0."""
    m = Mesh()
    lz = seat_h - (60 if cushion else 30)
    if legs == "square":
        m.add(square_legs(0, w, 0, d, 0, lz, 38, 10))
    elif legs == "turned":
        m.add(legs4(0, w, 0, d, 0, lz, 20, 34, 0.7))
    elif legs == "sled":
        for x in (30, w - 30):
            m.add(sweep([(x, 20, lz), (x, 20, 12), (x, d - 20, 12), (x, d - 20, lz)], 11, 6))
    if runners:
        m.add(square_legs(0, w, 0, d, 40, lz, 38, 10))
        for x in (40, w - 40):
            m.add(sweep([(x, -d * 0.2, 60), (x, d * 0.1, 10), (x, d * 0.6, 10), (x, d * 1.05, 70)], 16, 6))
    if cushion:
        m.add(rbox(0, w, 0, d, lz, seat_h, 30, 20))
    else:
        m.add(box(0, w, 0, d, lz, seat_h))
    if back:
        bz0, bz1 = seat_h, h
        for x in (10, w - 48):
            m.add(box(x, x + 38, d - 40, d, bz0, bz1))
        if back == "slats":
            for k in range(3):
                z = bz0 + (bz1 - bz0) * (0.35 + k * 0.22)
                m.add(box(48, w - 48, d - 34, d - 10, z, z + 45))
        elif back == "spindles":
            n = 5
            for k in range(n):
                x = 60 + (w - 120) * k / (n - 1)
                m.add(cyl(x, d - 22, 9, bz0, bz1 - 60, 6))
            m.add(box(10, w - 10, d - 40, d, bz1 - 60, bz1))
        elif back == "solid":
            m.add(rbox(48, w - 48, d - 60, d - 8, bz0 + 30, bz1, 20, 15))
        elif back == "ladder":
            for k in range(4):
                z = bz0 + (bz1 - bz0) * (0.2 + k * 0.2)
                m.add(box(48, w - 48, d - 30, d - 12, z, z + 35))
    if arms:
        for x in (0, w - 50):
            m.add(rbox(x, x + 50, 40, d - 20, seat_h + 160, seat_h + 200, 12, 8))
            m.add(box(x + 8, x + 42, 60, 100, seat_h, seat_h + 165))
    return m


def body(w, d, h, r=None, screen=None, dial=None, buttons=0, feet=True, vents=False, handle=False, slot=None,
         front_panel=None, lid_line=None):
    """
    A moulded appliance or gadget: a softened body with details on its front (y = 0).
    screen = (x0, x1, z0, z1) inset panel; dial = (x, z, radius); slot = (x0, x1, y0, y1) on top.
    """
    m = Mesh()
    fz = 12 if feet else 0
    if feet:
        for x in (w * 0.15, w * 0.85):
            for y in (d * 0.2, d * 0.8):
                m.add(cyl(x, y, min(w, d) * 0.05, 0, fz, 6))
    m.add(rbox(0, w, 0, d, fz, h, r if r is not None else min(w, d, h) * 0.12))
    if screen:
        x0, x1, z0, z1 = screen
        m.add(box(x0, x1, -4, 3, z0, z1))
    if front_panel:
        x0, x1, z0, z1 = front_panel
        m.add(rbox(x0, x1, -8, 4, z0, z1, min(x1 - x0, z1 - z0) * 0.1, 3))
    if dial:
        x, z, rr = dial
        m.add(lathe([(1, 0), (1, -rr * 0.6), (0.8, -rr * 0.8)], 8, "y", (x, z), (rr, rr)))
    for k in range(buttons):
        x = w * (0.2 + 0.6 * k / max(1, buttons - 1)) if buttons > 1 else w / 2
        m.add(box(x - 10, x + 10, d * 0.4, d * 0.6, h, h + 6))
    if vents:
        for k in range(4):
            z = h * (0.35 + k * 0.1)
            m.add(box(w * 0.99, w + 3, d * 0.2, d * 0.8, z, z + 8))
    if handle:
        m.add(sweep([(w * 0.3, d / 2, h), (w * 0.35, d / 2, h + h * 0.12), (w * 0.65, d / 2, h + h * 0.12), (w * 0.7, d / 2, h)],
                    min(w, d) * 0.03, 6))
    if slot:
        x0, x1, y0, y1 = slot
        m.add(box(x0, x1, y0, y1, h, h + 5))
    if lid_line:
        m.add(box(-2, w + 2, -2, d + 2, lid_line, lid_line + 6))
    return m


def garment_stack(w, d, h, layers=3, collar=False, sleeves=False, fold=True):
    """Folded clothes: soft layers, optionally with a shirt collar and folded sleeves on top."""
    m = Mesh()
    lh = h / (layers + (0.6 if collar else 0))
    for k in range(layers):
        o = (k % 2) * 6
        m.add(rbox(o, w - 6 + o, o * 0.5, d - 4 + o * 0.5, k * lh, (k + 1) * lh - 1, min(w, d) * 0.1, lh * 0.4))
    top = layers * lh
    if collar:
        m.add(turned([(1, top), (1, top + lh * 0.6), (0.8, top + lh * 0.6), (0.8, top + lh * 0.2)], w * 0.16, 12,
                     cx=w / 2, cy=d * 0.2, ry=d * 0.1))
    if sleeves:
        m.add(box(w * 0.05, w * 0.35, d * 0.3, d * 0.9, top - 2, top + 4)).add(box(w * 0.65, w * 0.95, d * 0.3, d * 0.9, top - 2, top + 4))
    return m


def bag(w, d, h, taper=0.9, handles="two", flap=False, strap=False, zip_top=False):
    """A soft bag: body wider at the base, handles or a strap over the top."""
    m = Mesh()
    m.add(hexahedron([(0, 0, 0), (w, 0, 0), (w, d, 0), (0, d, 0),
                      (w * (1 - taper) / 2, d * 0.1, h * 0.72), (w * (1 + taper) / 2, d * 0.1, h * 0.72),
                      (w * (1 + taper) / 2, d * 0.9, h * 0.72), (w * (1 - taper) / 2, d * 0.9, h * 0.72)]))
    top = h * 0.72
    if flap:
        m.add(box(w * (1 - taper) / 2, w * (1 + taper) / 2, -6, d * 0.1, top * 0.45, top + 4))
    if zip_top:
        m.add(box(w * 0.2, w * 0.8, d * 0.45, d * 0.55, top, top + 5))
    if handles == "two":
        for y in (d * 0.25, d * 0.75):
            m.add(sweep(arc((w / 2, y, top), w * 0.22, 180, 0, 8), max(4, d * 0.03), 6))
    elif handles == "one":
        m.add(sweep(arc((w / 2, d / 2, top), w * 0.18, 180, 0, 8), max(5, d * 0.04), 6))
    if strap:
        m.add(sweep(arc((w / 2, d / 2, top), w * 0.48, 180, 0, 10), max(4, d * 0.03), 6))
    return m


def long_tool(length, head, head_w, handle_r, head_kind="bowl", tines=0, width=None, height=None):
    """
    Things held by a handle and lying along X: spoons, forks, knives, brushes, spatulas.
    `head` is the head length. `head_kind`: bowl, flat, blade, tines, brush, loop, pad, hook.
    """
    m = Mesh()
    hl = length - head
    hz = (height or head_w * 0.4) / 2
    m.add(sweep([(0, 0, hz), (hl * 0.5, 0, hz * 1.05), (hl, 0, hz)], [handle_r, handle_r * 0.9, handle_r * 0.7], 8))
    if head_kind == "bowl":
        m.add(sphere((hl + head / 2, 0, hz), (head / 2, head_w / 2, hz), 8, 4))
    elif head_kind == "flat":
        m.add(rbox(hl, length, -head_w / 2, head_w / 2, hz - 2, hz + 2, head_w * 0.2, 1))
    elif head_kind == "blade":
        m.add(prism([(hl, -head_w * 0.1), (length, head_w * 0.3), (hl + head * 0.85, head_w * 0.5), (hl, head_w * 0.5)],
                    "z", hz - 1, hz + 1))
    elif head_kind == "tines":
        m.add(box(hl, hl + head * 0.35, -head_w / 2, head_w / 2, hz - 2, hz + 2))
        n = tines or 4
        for k in range(n):
            y = -head_w / 2 + head_w * (k + 0.5) / n
            m.add(box(hl + head * 0.35, length, y - head_w / (n * 3.5), y + head_w / (n * 3.5), hz - 1.5, hz + 1.5))
    elif head_kind == "brush":
        m.add(rbox(hl, length, -head_w / 2, head_w / 2, 0, hz * 1.4, head_w * 0.2, 3))
        m.add(box(hl + head * 0.08, length - head * 0.08, -head_w * 0.4, head_w * 0.4, hz * 1.4, hz * 2))
    elif head_kind == "loop":
        m.add(sweep(arc((hl + head / 2, 0, hz), head / 2, 180, -180, 14, "xy"), handle_r * 0.5, 6, closed=False))
    elif head_kind == "pad":
        m.add(rbox(hl, length, -head_w / 2, head_w / 2, 0, hz * 2, head_w * 0.3, hz * 0.5))
    elif head_kind == "hook":
        m.add(sweep(arc((length - head / 2, 0, hz + head / 2), head / 2, 180, -60, 8), handle_r, 6))
    return m


# ---------------------------------------------------------------------------------------------
# turned things: (name, words, phrases, radius mm, profile [(r fraction, z mm)], extras)
# extras: "handle", "spout", "knob", "pump", "cap", "stem"


TURNED = [
    # tableware and drink
    ("tumbler", ["tumbler", "glass", "glasses"], ["drinking glass", "water glass"], 38, [(0.85, 0), (0.9, 6), (1, 110), (0.93, 110), (0.88, 12)], ()),
    ("pint_glass", [], ["pint glass", "beer glass"], 45, [(0.7, 0), (0.72, 8), (1, 145), (0.95, 150), (1, 158)], ()),
    ("shot_glass", [], ["shot glass"], 25, [(0.8, 0), (0.85, 12), (1, 60), (0.9, 60), (0.82, 14)], ()),
    ("champagne_flute", ["flute"], ["champagne flute", "champagne glass"], 36, [(1, 0), (0.9, 4), (0.12, 10), (0.12, 100), (0.5, 120), (0.75, 180), (0.8, 230)], ()),
    ("whisky_glass", [], ["whisky glass", "whiskey glass", "rocks glass"], 42, [(1, 0), (1, 18), (1, 90), (0.93, 90), (0.93, 22)], ()),
    ("teacup", ["teacup"], ["tea cup", "cup and saucer"], 75, [(1, 0), (1, 6), (0.35, 8), (0.4, 14), (0.55, 30), (0.62, 62), (0.55, 60)], ("handle",)),
    ("cup", ["cup", "cups"], ["paper cup", "coffee cup"], 42, [(0.7, 0), (0.75, 8), (1, 105), (0.93, 105), (0.86, 90)], ("handle",)),
    ("travel_mug", [], ["travel mug", "travel cup", "reusable cup"], 44, [(0.78, 0), (0.8, 6), (0.95, 130), (1, 150), (0.95, 175), (0.6, 182)], ()),
    ("water_bottle", [], ["water bottle", "sports bottle", "drink bottle"], 37, [(0.95, 0), (1, 10), (1, 200), (0.95, 215), (0.7, 225), (0.72, 250), (0.5, 262)], ()),
    ("thermos_flask", ["thermos"], ["vacuum flask", "thermos flask", "coffee flask"], 42, [(0.95, 0), (1, 10), (1, 250), (0.85, 270), (0.9, 275), (0.9, 320), (0.8, 330)], ()),
    ("wine_bottle", [], ["wine bottle"], 38, [(0.95, 0), (1, 5), (1, 200), (0.85, 225), (0.36, 250), (0.33, 300), (0.36, 305), (0.3, 310)], ()),
    ("beer_bottle", [], ["beer bottle"], 32, [(0.95, 0), (1, 5), (1, 120), (0.55, 165), (0.4, 205), (0.42, 212), (0.35, 214)], ()),
    ("soda_can", [], ["soda can", "drink can", "beer can", "fizzy drink"], 33, [(0.8, 0), (1, 10), (1, 105), (0.82, 118), (0.8, 122)], ()),
    ("milk_jug", [], ["milk jug", "milk bottle"], 60, [(0.9, 0), (1, 10), (1, 160), (0.8, 190), (0.4, 215), (0.42, 235)], ("handle",)),
    ("pitcher", ["pitcher", "jug", "carafe"], ["water jug", "water pitcher"], 65, [(0.75, 0), (0.8, 8), (1, 90), (0.9, 170), (1, 220)], ("handle",)),
    ("decanter", ["decanter"], [], 75, [(0.9, 0), (1, 20), (1, 90), (0.6, 150), (0.3, 190), (0.32, 220), (0.45, 240), (0.2, 260)], ()),
    ("gravy_boat", [], ["gravy boat", "sauce boat"], 70, [(0.6, 0), (0.65, 10), (1, 70), (0.92, 70), (0.6, 18)], ("handle",)),
    ("sugar_bowl", [], ["sugar bowl"], 50, [(0.7, 0), (0.75, 6), (1, 50), (0.95, 70), (0.4, 80), (0.3, 88), (0, 92)], ()),
    ("egg_cup", [], ["egg cup", "egg cups"], 25, [(1, 0), (0.95, 5), (0.45, 12), (0.45, 25), (1, 50), (0.9, 50), (0.6, 30)], ()),
    ("ramekin", ["ramekin"], [], 45, [(0.95, 0), (1, 5), (1, 45), (0.9, 45), (0.9, 8)], ()),
    ("salad_bowl", [], ["salad bowl", "serving bowl", "fruit bowl"], 150, [(0.45, 0), (0.5, 10), (0.9, 70), (1, 110), (0.95, 110), (0.45, 18)], ()),
    ("soup_bowl", [], ["soup bowl", "cereal bowl"], 80, [(0.55, 0), (0.6, 8), (0.95, 50), (1, 70), (0.93, 70), (0.55, 14)], ()),
    ("side_plate", [], ["side plate", "saucer", "dessert plate"], 100, [(0.55, 0), (0.58, 4), (0.62, 10), (0.95, 16), (1, 20), (0.97, 20), (0.6, 13)], ()),
    ("dinner_plate", ["plate", "plates"], ["dinner plate"], 135, [(0.55, 0), (0.58, 4), (0.62, 10), (0.95, 18), (1, 24), (0.97, 24), (0.6, 14)], ()),
    ("cake_stand", [], ["cake stand"], 150, [(0.4, 0), (0.42, 8), (0.12, 25), (0.12, 100), (0.2, 108), (1, 112), (1, 124)], ()),
    ("butter_dish", [], ["butter dish", "cheese dome"], 90, [(1, 0), (1, 10), (0.85, 12), (0.85, 50), (0.6, 70), (0.2, 75), (0.25, 85), (0, 88)], ()),
    ("cookie_jar", [], ["cookie jar", "biscuit jar", "biscuit tin"], 80, [(0.9, 0), (1, 12), (1, 170), (0.85, 185), (0.87, 195), (0.3, 205), (0.35, 215), (0, 218)], ()),
    ("storage_jar", [], ["storage jar", "mason jar", "glass jar", "jam jar"], 45, [(0.95, 0), (1, 8), (1, 120), (0.85, 130), (0.87, 150)], ()),
    ("spice_jar", [], ["spice jar", "spice jars", "salt shaker", "pepper shaker"], 22, [(0.95, 0), (1, 5), (1, 70), (0.9, 75), (0.92, 95), (0.6, 100)], ()),
    ("pepper_mill", [], ["pepper mill", "pepper grinder", "salt mill"], 28, [(0.9, 0), (1, 20), (0.75, 70), (0.8, 110), (1, 150), (0.6, 190), (0.25, 200), (0, 205)], ()),
    ("oil_bottle", [], ["oil bottle", "olive oil", "vinegar bottle", "cooking oil"], 35, [(0.95, 0), (1, 6), (1, 190), (0.5, 230), (0.3, 250), (0.35, 270)], ()),
    ("sauce_bottle", [], ["sauce bottle", "ketchup", "hot sauce", "squeeze bottle"], 30, [(0.8, 0), (1, 20), (1, 160), (0.7, 180), (0.45, 195), (0.1, 215)], ()),
    ("baby_bottle", [], ["baby bottle", "feeding bottle"], 35, [(0.95, 0), (1, 8), (1, 140), (1.05, 150), (1.05, 165), (0.45, 180), (0.2, 205), (0.1, 212)], ()),
    ("sippy_cup", [], ["sippy cup"], 38, [(0.9, 0), (1, 10), (1, 110), (1.05, 118), (0.6, 130), (0.3, 150)], ("handle",)),
    ("ice_bucket", [], ["ice bucket", "wine cooler bucket"], 105, [(0.8, 0), (0.85, 10), (1, 220), (0.92, 220), (0.8, 16)], ()),
    ("casserole_dish", [], ["casserole dish", "casserole"], 130, [(0.9, 0), (1, 12), (1, 95), (0.95, 100), (0.8, 125), (0.2, 130), (0.25, 145), (0, 148)], ("ears",)),
    ("wok", ["wok"], [], 175, [(0.3, 0), (0.6, 30), (0.9, 75), (1, 100), (0.96, 100), (0.3, 6)], ("long_handle",)),
    ("colander", ["colander", "strainer", "sieve"], [], 120, [(0.5, 0), (0.55, 10), (0.9, 90), (1, 110), (0.95, 110), (0.5, 16)], ("ears",)),
    ("mortar_and_pestle", ["mortar"], ["mortar and pestle", "pestle and mortar"], 70, [(0.7, 0), (0.75, 10), (1, 80), (0.95, 90), (0.8, 90), (0.6, 30)], ("pestle",)),
    ("measuring_jug", [], ["measuring jug", "measuring cup"], 60, [(0.9, 0), (0.95, 8), (1, 150), (0.93, 150), (0.88, 15)], ("handle",)),
    ("coffee_pot", [], ["coffee pot", "cafetiere", "french press", "moka pot"], 55, [(0.9, 0), (1, 10), (1, 190), (0.95, 200), (0.95, 215), (0.25, 220), (0.15, 250), (0.25, 255)], ("handle",)),
    ("trophy", ["trophy", "cup trophy"], [], 70, [(1, 0), (1, 40), (0.4, 50), (0.2, 70), (0.2, 110), (0.5, 130), (1, 230), (0.9, 230), (0.6, 150)], ("ears",)),
    ("candle_holder", [], ["candle holder", "candlestick", "candle stick"], 45, [(1, 0), (0.95, 10), (0.2, 25), (0.18, 180), (0.4, 195), (0.45, 210), (0.3, 210), (0.3, 300)], ()),
    ("pillar_candle", [], ["pillar candle", "scented candle", "candle jar"], 45, [(1, 0), (1, 140), (0.2, 140), (0.2, 150)], ()),
    ("lampshade", ["lampshade", "shade"], [], 180, [(1, 0), (0.62, 260), (0.58, 262), (0.95, 2)], ()),
    ("table_lamp", [], ["table lamp", "bedside lamp"], 90, [(1, 0), (1, 20), (0.25, 30), (0.25, 60), (0.9, 150), (0.7, 250), (0.25, 260), (0.25, 320),
                                                              (1.6, 320), (1.1, 480)], ()),
    ("floor_lamp", [], ["floor lamp", "standard lamp", "standing lamp"], 140, [(1, 0), (1, 25), (0.12, 35), (0.08, 1350), (1.2, 1350), (0.8, 1600)], ()),
    ("globe_light", [], ["globe light", "pendant light", "ceiling light", "light fitting"], 150, [(0.1, 0), (0.6, 40), (0.95, 110), (1, 160), (0.9, 230), (0.5, 285), (0.12, 300), (0.12, 340)], ()),
    ("urn", ["urn"], [], 120, [(0.7, 0), (0.75, 20), (1, 150), (0.95, 250), (0.55, 300), (0.6, 330), (0.4, 345), (0, 350)], ()),
    ("planter", ["planter", "flowerpot"], ["window box", "hanging basket"], 160, [(0.7, 0), (0.75, 12), (1, 300), (1.05, 305), (1.05, 320), (0.95, 320), (0.95, 305)], ()),
    ("bird_bath", [], ["bird bath", "birdbath"], 250, [(0.5, 0), (0.5, 40), (0.25, 60), (0.2, 520), (0.4, 560), (1, 620), (0.9, 640), (0.3, 600)], ()),
    ("garden_gnome", ["gnome"], ["garden gnome"], 90, [(1, 0), (1, 30), (0.9, 150), (0.95, 220), (0.7, 260), (0.6, 300), (0.4, 360), (0, 460)], ()),
    ("fire_pit", [], ["fire pit", "brazier"], 350, [(0.5, 0), (0.5, 100), (0.8, 180), (1, 300), (0.95, 300), (0.6, 200)], ("ears",)),
    ("pouffe", ["pouffe", "pouf", "footstool"], [], 250, [(0.9, 0), (1, 40), (1, 360), (0.9, 400), (0.4, 420), (0, 420)], ()),
    ("drum_kit_drum", [], ["snare drum", "bass drum", "bongo", "tom drum"], 180, [(1, 0), (1.03, 10), (1.03, 20), (1, 20), (1, 160), (1.03, 160), (1.03, 170), (1, 180)], ()),
    ("hat_box", [], ["hat box", "hatbox", "round box"], 180, [(1, 0), (1, 170), (1.03, 170), (1.03, 220), (0.1, 220)], ()),
    ("cake_tin_round", [], ["cake tin", "round tin", "tin of biscuits"], 110, [(1, 0), (1, 85), (1.03, 85), (1.03, 105), (0.2, 105)], ()),
    ("paper_bin", [], ["paper bin", "wastepaper basket", "waste paper basket"], 120, [(0.8, 0), (0.82, 10), (1, 290), (0.95, 290), (0.8, 12)], ()),
    ("umbrella_stand", [], ["umbrella stand"], 120, [(1, 0), (1, 500), (0.93, 500), (0.93, 20)], ()),
    ("cotton_bud_tub", [], ["cotton buds", "cotton swabs", "q tips", "cotton pads"], 45, [(1, 0), (1, 90), (1.05, 90), (1.05, 110), (0.2, 110)], ()),
    # hygiene and grooming
    ("soap_dispenser", [], ["soap dispenser", "hand soap", "hand wash", "lotion pump", "pump bottle"], 38,
     [(0.9, 0), (1, 10), (1, 150), (0.6, 170), (0.3, 175), (0.3, 185)], ("pump",)),
    ("shampoo_bottle", ["shampoo", "conditioner", "bodywash"], ["shampoo bottle", "body wash", "shower gel"], 42,
     [(0.85, 0), (1, 15), (1, 190), (0.7, 205), (0.75, 225), (0.5, 230)], ()),
    ("lotion_bottle", ["lotion", "moisturiser", "moisturizer", "sunscreen", "suncream"], ["body lotion", "sun cream"], 32,
     [(0.8, 0), (0.85, 20), (1, 60), (1, 150), (0.8, 160), (0.8, 180)], ()),
    ("perfume_bottle", ["perfume", "cologne", "fragrance", "aftershave"], [], 35,
     [(1, 0), (1, 80), (0.6, 95), (0.3, 100), (0.3, 110), (0.55, 112), (0.55, 140), (0, 142)], ()),
    ("deodorant", ["deodorant", "antiperspirant", "bodyspray", "hairspray"], ["body spray", "hair spray", "spray can", "aerosol"], 26,
     [(0.95, 0), (1, 5), (1, 150), (0.75, 165), (0.8, 170), (0.8, 190), (0.6, 198)], ()),
    ("toothbrush_cup", [], ["toothbrush holder", "toothbrush cup", "tooth mug"], 38,
     [(0.85, 0), (0.9, 8), (1, 110), (0.93, 110), (0.88, 14)], ("brushes",)),
    ("cream_jar", [], ["face cream", "cream jar", "night cream", "hair wax", "lip balm"], 35,
     [(1, 0), (1, 35), (1.06, 35), (1.06, 55), (0.15, 55)], ()),
    ("nail_polish", [], ["nail polish", "nail varnish"], 18, [(1, 0), (1, 35), (0.5, 40), (0.45, 80), (0.3, 82)], ()),
    ("toilet_roll", [], ["toilet paper", "toilet rolls", "loo roll"], 55, [(1, 0), (1, 100), (0.38, 100), (0.38, 0)], ()),
    ("bath_bomb", [], ["bath bomb", "bath bombs"], 35, [(0.3, 0), (0.8, 10), (1, 30), (0.8, 55), (0.3, 65), (0, 68)], ()),
    ("hair_spray_can", [], ["dry shampoo", "shaving foam", "shaving gel", "air freshener"], 30,
     [(0.95, 0), (1, 6), (1, 170), (0.7, 185), (0.72, 205), (0.5, 212)], ()),
    ("mouthwash", ["mouthwash"], ["mouth wash"], 38, [(0.9, 0), (1, 12), (0.85, 110), (1, 170), (0.6, 205), (0.62, 230)], ()),
    ("electric_toothbrush", [], ["electric toothbrush"], 20, [(1, 0), (1, 15), (0.95, 150), (0.55, 175), (0.35, 210), (0.3, 245)], ()),
    ("pill_bottle", [], ["pill bottle", "vitamins", "medicine bottle", "pill tub"], 28, [(1, 0), (1, 75), (1.05, 75), (1.05, 95), (0.2, 95)], ()),
    ("sanitiser", ["sanitiser", "sanitizer"], ["hand sanitiser", "hand sanitizer", "hand gel"], 30,
     [(0.8, 0), (0.95, 12), (1, 130), (0.6, 142), (0.62, 160), (0.5, 165)], ()),
    # electronics and appliances that are round
    ("smart_speaker", [], ["smart speaker", "alexa", "echo dot", "google home", "homepod"], 50,
     [(0.9, 0), (1, 10), (1, 130), (0.95, 142), (0.2, 148), (0, 148)], ()),
    ("air_purifier", [], ["air purifier", "humidifier", "diffuser", "aroma diffuser"], 110,
     [(0.95, 0), (1, 20), (1, 420), (0.9, 440), (0.85, 450), (0, 452)], ()),
    ("tower_fan", [], ["tower fan", "column fan"], 70, [(1.7, 0), (1.8, 20), (1.7, 40), (1, 60), (1, 950), (0.85, 980), (0, 985)], ()),
    ("space_heater", [], ["space heater", "fan heater", "patio heater"], 110, [(1, 0), (1, 30), (0.8, 50), (0.8, 480), (0.95, 500), (0.9, 560), (0, 565)], ()),
    ("rice_cooker", [], ["rice cooker", "multi cooker", "instant pot", "multicooker"], 140,
     [(0.9, 0), (1, 20), (1, 200), (0.95, 215), (0.9, 225), (0.6, 250), (0.2, 258), (0, 260)], ("ears",)),
    ("slow_cooker", [], ["slow cooker", "crock pot", "crockpot"], 160,
     [(0.8, 0), (0.9, 15), (1, 180), (0.95, 190), (0.7, 215), (0.2, 225), (0.25, 240), (0, 242)], ("ears",)),
    ("electric_kettle_jug", [], ["electric kettle", "cordless kettle"], 80,
     [(1.2, 0), (1.2, 20), (1, 25), (1, 230), (0.85, 245), (0.3, 250), (0, 250)], ("handle", "spout")),
    ("juicer", ["juicer"], ["citrus press", "citrus juicer", "juice extractor"], 90,
     [(1, 0), (1, 90), (0.85, 110), (0.95, 150), (0.4, 170), (0.1, 210)], ()),
    ("food_processor_bowl", [], ["food processor", "chopper"], 100,
     [(1.1, 0), (1.1, 120), (1, 125), (1, 260), (1.05, 270), (0.2, 280), (0.2, 330)], ("handle",)),
    ("stand_fan_desk", [], ["desk fan", "table fan", "usb fan", "clip fan"], 120,
     [(0.6, 0), (0.6, 20), (0.12, 30), (0.12, 150), (1, 160), (1, 200), (0.95, 205), (0.3, 205)], ()),
    ("record_player_speaker", [], ["bluetooth speaker", "portable speaker", "speaker cylinder"], 45,
     [(0.2, 0), (0.95, 5), (1, 20), (1, 180), (0.95, 195), (0.2, 200)], ()),
    ("webcam_ring_light", [], ["ring light", "selfie light"], 150, [(0.2, 0), (0.2, 10), (0.08, 20), (0.08, 700)], ("ring",)),
    ("bedpan_potty", ["potty"], ["baby potty", "training potty"], 130, [(0.8, 0), (0.85, 20), (1, 180), (0.95, 185), (0.8, 190), (0.75, 60)], ()),
    ("watering_globe", [], ["spray bottle", "plant mister", "mister", "trigger spray"], 40,
     [(0.9, 0), (1, 10), (1, 180), (0.6, 200), (0.4, 210), (0.4, 240)], ("trigger",)),
]


def make_turned(r, profile, extras):
    m = Mesh()
    top = max(z for _, z in profile)
    rmax = max(p for p, _ in profile) * r
    cx = cy = rmax
    m.add(lathe(profile, 12, "z", (cx, cy), (r, r)))
    if "handle" in extras:
        z_mid = top * 0.55
        reach = max(top * 0.28, r * 0.35)
        m.add(sweep(arc((cx + rmax * 0.92, cy, z_mid), reach, 80, -80, 10), max(4.0, r * 0.08), 8))
    if "spout" in extras:
        m.add(sweep([(cx - rmax * 0.9, cy, top * 0.3), (cx - rmax * 1.15, cy, top * 0.6), (cx - rmax * 1.3, cy, top * 0.85)],
                    [r * 0.18, r * 0.13, r * 0.1], 8))
    if "long_handle" in extras:
        m.add(sweep([(cx + rmax * 0.95, cy, top * 0.8), (cx + rmax * 1.7, cy, top * 1.0)], r * 0.07, 8))
    if "ears" in extras:
        for s in (-1, 1):
            m.add(box(cx + s * rmax * 0.97 - (0 if s > 0 else r * 0.18), cx + s * rmax * 0.97 + (r * 0.18 if s > 0 else 0),
                      cy - r * 0.2, cy + r * 0.2, top * 0.72, top * 0.8))
    if "pump" in extras:
        m.add(box(cx - r * 0.12, cx + r * 0.12, cy - r * 0.12, cy + r * 0.12, top, top + r * 0.5))
        m.add(box(cx - r * 0.7, cx + r * 0.15, cy - r * 0.15, cy + r * 0.15, top + r * 0.5, top + r * 0.7))
    if "pestle" in extras:
        m.add(sweep([(cx + r * 0.2, cy, top * 0.5), (cx + r * 0.9, cy, top * 1.5)], [r * 0.16, r * 0.1], 8))
    if "brushes" in extras:
        for dx in (-0.25, 0.3):
            m.add(bar((cx + r * dx, cy, top * 0.3), (cx + r * dx * 2.2, cy, top * 1.9), r * 0.08, r * 0.06))
    if "ring" in extras:
        m.add(torus((cx, cy, top + r * 0.9), (r * 0.9, r * 0.9), 0.1, r * 0.08, 16, 8))
    if "trigger" in extras:
        m.add(box(cx - r * 1.2, cx + r * 0.3, cy - r * 0.2, cy + r * 0.2, top, top + r * 0.5))
        m.add(box(cx - r * 0.9, cx - r * 0.6, cy - r * 0.15, cy + r * 0.15, top - r * 0.6, top))
    return m


def _register_turned():
    for name, words, phrases, r, profile, extras in TURNED:
        def fn(r=r, profile=profile, extras=extras):
            return make_turned(r, profile, extras)
        fn.__doc__ = name.replace("_", " ").capitalize() + "."
        shape(name, words, phrases, kind="round")(fn)


_register_turned()


# ---------------------------------------------------------------------------------------------
# furniture: storage


CABINETS = [
    # name, words, phrases, (w, d, h), rows, options
    ("sideboard", ["sideboard", "credenza", "buffet"], [], (1600, 450, 800), [(1, ["door", "drawer", "drawer", "door"])], dict(legs=120)),
    ("dresser", ["dresser"], ["dressing chest"], (1200, 480, 850), [(1, ["drawer", "drawer"]), (1, ["drawer", "drawer"]), (1, ["drawer", "drawer", "drawer"])], dict(plinth=70, top=20)),
    ("nightstand", ["nightstand"], ["night stand", "night table"], (450, 400, 550), [(1, ["open"]), (1, ["drawer"])], dict(legs=110, leg_style="round")),
    ("cupboard", ["cupboard", "cabinet"], ["storage cabinet", "storage cupboard"], (800, 400, 1800), [(3, ["door", "door"]), (1, ["door", "door"])], dict(plinth=60)),
    ("display_cabinet", [], ["display cabinet", "china cabinet", "glass cabinet", "curio cabinet"], (900, 400, 1800), [(1, ["door", "door"]), (2, ["glass", "glass"])], dict(plinth=60, top=20)),
    ("tall_boy", [], ["tall boy", "tallboy", "chest of five drawers"], (600, 450, 1200), [(1, ["drawer"])] * 5, dict(plinth=70)),
    ("shoe_cabinet", [], ["shoe cabinet", "shoe cupboard", "shoe storage"], (900, 300, 1000), [(1, ["door", "door"]), (1, ["door", "door"])], dict(plinth=40, handle="knob")),
    ("kitchen_unit", [], ["kitchen unit", "base unit", "kitchen cabinet", "kitchen island"], (600, 580, 900), [(3, ["door"]), (1, ["drawer"])], dict(plinth=100, top=30)),
    ("wall_cabinet", [], ["wall cabinet", "wall cupboard", "medicine cabinet", "bathroom cabinet"], (600, 300, 700), [(1, ["door", "door"])], dict()),
    ("locker", ["locker", "lockers"], ["metal locker", "gym locker"], (380, 450, 1800), [(1, ["door"]), (4, ["door"])], dict(plinth=30)),
    ("safe", ["safe"], ["safe box", "fireproof safe"], (400, 400, 500), [(1, ["door"])], dict(handle="knob")),
    ("hutch", ["hutch", "dresser top"], ["welsh dresser", "kitchen dresser"], (1200, 450, 1900), [(2, ["door", "door", "door"]), (1, ["drawer", "drawer", "drawer"]), (3, ["open"])], dict(plinth=60, top=20)),
    ("tv_cabinet_low", [], ["tv console", "media unit", "entertainment unit", "low cabinet"], (1500, 400, 500), [(1, ["drawer", "open", "open", "drawer"])], dict(legs=100)),
    ("cube_shelf", [], ["cube shelf", "cube storage", "kallax", "cubby", "cube organiser"], (770, 390, 770), [(1, ["open", "open"]), (1, ["open", "open"])], dict()),
    ("bookshelf_tall", ["bookshelf", "bookshelves"], ["tall bookshelf", "billy bookcase"], (800, 280, 2000), [(1, ["open"])] * 5, dict(plinth=60)),
    ("bathroom_vanity", [], ["bathroom vanity", "vanity unit", "sink unit"], (800, 460, 850), [(1, ["door", "door"]), (1, ["drawer"])], dict(plinth=80, top=30)),
    ("filing_drawers", [], ["filing drawers", "desk pedestal", "drawer unit", "rolling drawers"], (400, 500, 600), [(2, ["drawer"]), (1, ["drawer"]), (1, ["drawer"])], dict(legs=50, leg_style="round")),
    ("plan_chest", [], ["plan chest", "map chest", "flat file"], (1200, 900, 700), [(1, ["drawer"])] * 6, dict(plinth=60)),
    ("blanket_box", [], ["blanket box", "storage trunk", "toy box", "toy chest", "ottoman box"], (900, 450, 450), [(1, ["blank"])], dict(plinth=40, top=25, handle=None)),
    ("tool_chest_tall", [], ["tool cabinet", "rolling tool chest", "tool trolley"], (700, 460, 1000), [(1, ["drawer"])] * 6, dict(legs=90, leg_style="round")),
    ("drinks_cabinet", [], ["drinks cabinet", "bar cabinet", "wine cabinet", "wine rack"], (800, 400, 1300), [(1, ["open", "open"]), (2, ["glass", "glass"])], dict(legs=150)),
    ("washstand", ["washstand"], ["console cabinet"], (900, 350, 800), [(1, ["drawer", "drawer"]), (2, ["open"])], dict(legs=300)),
    ("pantry_cupboard", [], ["pantry cupboard", "larder unit", "broom cupboard", "tall cupboard"], (600, 600, 2100), [(3, ["door"]), (1, ["door"])], dict(plinth=100)),
    ("jewellery_armoire", [], ["jewellery armoire", "jewelry armoire", "jewellery cabinet"], (400, 350, 1000), [(1, ["drawer"])] * 5 + [(1, ["door"])], dict(legs=80, leg_style="round")),
    ("desk_hutch", [], ["writing bureau", "bureau", "secretary desk"], (900, 480, 1050), [(1, ["drawer"]), (1, ["drawer"]), (1, ["drawer"]), (2, ["blank"])], dict(legs=100)),
]


def _register_cabinets():
    for name, words, phrases, (w, d, h), rows, opts in CABINETS:
        def fn(w=w, d=d, h=h, rows=rows, opts=opts):
            return cabinet(w, d, h, rows, **opts)
        fn.__doc__ = name.replace("_", " ").capitalize() + "."
        shape(name, words, phrases, facing=True)(fn)


_register_cabinets()


# ---------------------------------------------------------------------------------------------
# furniture: tables and seats


TABLES = [
    ("console_table", [], ["console table", "hall table", "sofa table"], (1100, 350, 780), dict(legs="four", shelf=True)),
    ("bistro_table", [], ["bistro table", "cafe table", "garden table", "round table"], (700, 700, 740), dict(legs="pedestal", round_top=True)),
    ("kitchen_table", [], ["kitchen table", "farmhouse table"], (1500, 850, 760), dict(legs="turned")),
    ("folding_table", [], ["folding table", "trestle table", "camping table", "picnic table"], (1800, 700, 740), dict(legs="trestle_a", apron=False)),
    ("workbench", ["workbench"], ["work bench", "potting bench"], (1500, 600, 900), dict(legs="four", shelf=True, top_t=45)),
    ("dressing_table", [], ["dressing table", "vanity table", "makeup table"], (1000, 450, 760), dict(legs="turned", drawer=True)),
    ("nesting_tables", [], ["nesting tables", "nest of tables"], (550, 400, 500), dict(legs="four", shelf=True)),
    ("writing_desk", [], ["writing desk", "computer desk", "study desk", "laptop desk"], (1200, 600, 750), dict(legs="hairpin", drawer=True)),
    ("standing_desk", [], ["standing desk", "sit stand desk", "adjustable desk"], (1400, 700, 1000), dict(legs="trestle")),
    ("pedestal_table", [], ["pedestal table", "round dining table", "tulip table"], (1100, 1100, 750), dict(legs="pedestal", round_top=True)),
    ("oval_table", [], ["oval table", "extending table", "extendable table"], (1800, 950, 760), dict(legs="turned", round_top=True, apron=False)),
    ("bedside_table_legs", [], ["bedside shelf", "plant stand", "lamp table"], (400, 400, 600), dict(legs="four", shelf=True, apron=False)),
    ("sawhorse", ["sawhorse", "trestle"], ["saw horse", "trestles"], (900, 400, 700), dict(legs="trestle_a", top_t=60, apron=False)),
    ("tv_tray_table", [], ["tv tray", "tray table", "snack table", "folding tray"], (500, 380, 660), dict(legs="trestle_a", apron=False, top_t=18)),
    ("childs_table", [], ["childrens table", "kids table", "play table", "activity table"], (800, 600, 500), dict(legs="four")),
]


def _register_tables():
    for name, words, phrases, (w, d, h), opts in TABLES:
        def fn(w=w, d=d, h=h, opts=opts):
            return table(w, d, h, **opts)
        fn.__doc__ = name.replace("_", " ").capitalize() + "."
        shape(name, words, phrases)(fn)


_register_tables()


SEATS = [
    ("dining_chair", [], ["dining chair", "kitchen chair", "wooden chair"], (450, 500, 900), dict(back="slats", cushion=False, legs="turned")),
    ("windsor_chair", [], ["windsor chair", "spindle chair"], (480, 480, 950), dict(back="spindles", cushion=False, legs="turned")),
    ("ladder_back_chair", [], ["ladder back chair", "ladderback chair"], (460, 450, 1000), dict(back="ladder", cushion=False, legs="turned")),
    ("upholstered_chair", [], ["upholstered chair", "accent chair", "occasional chair", "tub chair"], (650, 650, 800), dict(back="solid", arms=True, seat_h=430)),
    ("rocking_chair", [], ["rocking chair", "rocker", "glider"], (600, 850, 1050), dict(back="spindles", arms=True, legs=None, runners=True, cushion=False)),
    ("cantilever_chair", [], ["cantilever chair", "conference chair", "visitor chair"], (500, 550, 850), dict(back="solid", legs="sled")),
    ("stacking_chair", [], ["stacking chair", "plastic chair", "garden chair", "patio chair"], (520, 540, 800), dict(back="solid", cushion=False, legs="square")),
    ("gaming_stool", [], ["vanity stool", "piano stool", "piano bench", "dressing stool"], (600, 350, 500), dict(back=None, legs="turned")),
    ("bench", ["bench"], ["dining bench", "hallway bench", "garden bench", "park bench"], (1400, 380, 460), dict(back=None, legs="square", cushion=False)),
    ("bench_back", [], ["bench with back", "storage bench", "settle", "church pew"], (1300, 500, 900), dict(back="slats", legs="square", cushion=False)),
    ("kids_chair", [], ["kids chair", "childs chair", "toddler chair"], (320, 320, 550), dict(back="solid", cushion=False, seat_h=280)),
    ("bar_chair", [], ["counter stool", "kitchen stool", "breakfast bar stool"], (430, 430, 1000), dict(back="slats", cushion=True, seat_h=650)),
    ("directors_chair", [], ["directors chair", "director chair", "fishing chair"], (560, 480, 880), dict(back="solid", arms=True, legs="square", cushion=False)),
]


def _register_seats():
    for name, words, phrases, (w, d, h), opts in SEATS:
        def fn(w=w, d=d, h=h, opts=opts):
            return seat(w, d, h, **opts)
        fn.__doc__ = name.replace("_", " ").capitalize() + "."
        shape(name, words, phrases, kind="long" if w > 1.6 * d else None, facing=True)(fn)


_register_seats()


@shape("bunk_bed", phrases=["bunk beds", "loft bed", "cabin bed"], facing=True)
def bunk_bed():
    """Bunk bed: four posts, two decks with mattresses, a ladder at one end."""
    m = Mesh()
    w, d, h = 2000, 950, 1600
    m.add(square_legs(0, w, 0, d, 0, h, 60))
    for z in (250, 1100):
        m.add(box(60, w - 60, 0, d, z, z + 60)).add(rbox(70, w - 70, 30, d - 30, z + 60, z + 190, 40, 30))
    m.add(box(60, w - 60, 0, 40, 1350, 1420))
    for k in range(4):
        z = 350 + k * 200
        m.add(box(w - 400, w - 100, -40, 0, z, z + 30))
    return m


@shape("cot", ["cot", "crib", "cradle"], ["baby cot", "travel cot", "moses basket"], facing=True)
def cot():
    """Baby cot: a slatted box on legs with a mattress inside."""
    m = Mesh()
    w, d, h = 1250, 650, 950
    m.add(square_legs(0, w, 0, d, 0, h, 45))
    m.add(box(45, w - 45, 45, d - 45, 250, 280)).add(rbox(50, w - 50, 50, d - 50, 280, 380, 25, 20))
    for y0, y1 in ((0, 25), (d - 25, d)):
        m.add(box(45, w - 45, y0, y1, h - 50, h))
        n = 12
        for k in range(n):
            x = 90 + (w - 180) * k / (n - 1)
            m.add(box(x - 10, x + 10, y0 + 3, y1 - 3, 280, h - 50))
    for x0, x1 in ((0, 25), (w - 25, w)):
        m.add(box(x0, x1, 45, d - 45, 280, h - 30))
    return m


@shape("camp_bed", phrases=["camp bed", "camping bed", "air bed", "air mattress", "inflatable mattress"], kind="long")
def camp_bed():
    """Camp bed: a fabric bed on folding cross legs."""
    m = Mesh()
    w, d, h = 1900, 700, 420
    m.add(rbox(0, w, 0, d, h - 90, h, 40, 30))
    for x in (200, w / 2, w - 200):
        m.add(bar((x - 120, 30, 0), (x + 120, 30, h - 90), 12, 12)).add(bar((x + 120, 30, 0), (x - 120, 30, h - 90), 12, 12))
        m.add(bar((x - 120, d - 30, 0), (x + 120, d - 30, h - 90), 12, 12)).add(bar((x + 120, d - 30, 0), (x - 120, d - 30, h - 90), 12, 12))
    return m


@shape("headboard", ["headboard"], ["head board", "bed head"], kind="thin")
def headboard():
    """Headboard: a padded panel on two legs."""
    m = Mesh()
    w, d, h = 1500, 80, 1200
    m.add(box(40, 110, 20, 70, 0, 700)).add(box(w - 110, w - 40, 20, 70, 0, 700))
    return m.add(rbox(0, w, 0, d, 500, h, 60, 40))


@shape("room_screen", phrases=["folding screen", "privacy screen", "dressing screen"], kind="thin")
def room_screen():
    """Folding screen: three hinged panels in a zigzag."""
    m = Mesh()
    pw, h = 500, 1750
    xs = [0, pw * 0.94, pw * 1.88, pw * 2.82]
    ys = [0, 200, 0, 200]
    for i in range(3):
        m.add(bar((xs[i], ys[i], h / 2), (xs[i + 1], ys[i + 1], h / 2), 12, h / 2, (0.0, 0.0, 1.0)))
    return m


@shape("coat_hooks", phrases=["coat hooks", "wall hooks", "hook rail", "key holder"], kind="long")
def coat_hooks():
    """A board of coat hooks."""
    m = Mesh().add(rbox(0, 700, 0, 22, 0, 110, 10, 5))
    for k in range(5):
        x = 70 + k * 140
        m.add(sweep([(x, 22, 60), (x, 80, 55), (x, 100, 80)], 7, 6))
    return m


@shape("mirror_standing", phrases=["standing mirror", "floor mirror", "full length mirror", "cheval mirror"], kind="thin")
def mirror_standing():
    """Full-length mirror on a stand."""
    m = Mesh()
    w, h = 500, 1600
    m.add(box(0, 40, 150, 250, 0, h * 0.6)).add(box(w - 40, w, 150, 250, 0, h * 0.6))
    m.add(box(0, 40, 0, 400, 0, 30)).add(box(w - 40, w, 0, 400, 0, 30))
    m.add(box(40, w - 40, 180, 220, 80, h)).add(box(60, w - 60, 175, 180, 100, h - 20))
    return m


@shape("wall_shelf", phrases=["wall shelf", "floating shelf", "shelf bracket", "spice rack"], kind="long")
def wall_shelf():
    """A shelf board on two brackets."""
    m = Mesh().add(box(0, 800, 0, 220, 180, 205))
    for x in (100, 680):
        m.add(box(x, x + 20, 190, 220, 0, 180)).add(prism([(190, 0), (220, 0), (220, 180), (40, 180)], "x", x, x + 20))
    return m


@shape("chaise_lounger", phrases=["sun lounger", "sunlounger", "deckchair lounger", "pool lounger", "garden lounger"], kind="long")
def chaise_lounger():
    """Sun lounger: a long frame on legs with a raised back."""
    m = Mesh()
    w, d = 1900, 650
    m.add(square_legs(0, w * 0.7, 0, d, 0, 300, 40))
    m.add(box(0, w * 0.68, 0, d, 300, 340)).add(rbox(10, w * 0.68, 20, d - 20, 340, 380, 30, 15))
    m.add(hexahedron([(w * 0.68, 0, 300), (w * 0.68, d, 300), (w, d, 800), (w, 0, 800),
                      (w * 0.68 + 40, 0, 340), (w * 0.68 + 40, d, 340), (w - 30, d, 820), (w - 30, 0, 820)]))
    return m


@shape("hammock", ["hammock"], ["hammock stand"], kind="long")
def hammock():
    """Hammock on its stand."""
    m = Mesh()
    w, d = 2800, 900
    m.add(sweep([(0, d / 2, 1100), (300, d / 2, 60), (w - 300, d / 2, 60), (w, d / 2, 1100)], 25, 6))
    m.add(box(250, 350, 0, d, 0, 60)).add(box(w - 350, w - 250, 0, d, 0, 60))
    pts = [(200 + k * (w - 400) / 8, d / 2, 800 - 350 * math.sin(math.pi * k / 8)) for k in range(9)]
    m.add(sweep(pts, 180, 8))
    return m


# ---------------------------------------------------------------------------------------------
# utensils and tableware that are long and flat


LONG_TOOLS = [
    ("spoon", ["spoon", "spoons", "teaspoon", "tablespoon"], ["dessert spoon", "soup spoon"], (180, 55, 40, 4.5), dict(head_kind="bowl", height=12)),
    ("fork", ["fork", "forks"], ["dinner fork", "dessert fork"], (190, 60, 25, 4), dict(head_kind="tines", tines=4, height=8)),
    ("table_knife", ["knife", "knives"], ["dinner knife", "butter knife", "steak knife"], (225, 110, 20, 5), dict(head_kind="blade", height=10)),
    ("chef_knife", [], ["chef knife", "chefs knife", "kitchen knife", "bread knife", "carving knife"], (330, 200, 45, 10), dict(head_kind="blade", height=22)),
    ("ladle", ["ladle"], ["soup ladle"], (320, 90, 85, 5), dict(head_kind="bowl", height=45)),
    ("spatula", ["spatula", "turner", "fish slice"], ["egg flip"], (320, 100, 75, 6), dict(head_kind="flat", height=14)),
    ("wooden_spoon", [], ["wooden spoon", "mixing spoon", "serving spoon"], (300, 70, 50, 7), dict(head_kind="bowl", height=16)),
    ("whisk", ["whisk"], ["balloon whisk"], (290, 160, 60, 9), dict(head_kind="loop", height=60)),
    ("tongs", ["tongs"], ["kitchen tongs", "bbq tongs", "salad tongs"], (300, 60, 40, 8), dict(head_kind="tines", tines=2, height=30)),
    ("chopsticks", ["chopsticks"], [], (240, 90, 12, 3.5), dict(head_kind="tines", tines=2, height=7)),
    ("peeler", ["peeler"], ["potato peeler", "vegetable peeler"], (180, 60, 45, 8), dict(head_kind="loop", height=18)),
    ("grater_flat", [], ["zester", "microplane", "flat grater"], (310, 180, 35, 8), dict(head_kind="flat", height=14)),
    ("pizza_cutter", [], ["pizza cutter", "pizza wheel"], (200, 90, 90, 9), dict(head_kind="loop", height=90)),
    ("toothbrush", ["toothbrush", "toothbrushes"], ["tooth brush"], (190, 30, 13, 6), dict(head_kind="brush", height=14)),
    ("hairbrush", ["hairbrush", "brush"], ["hair brush", "paddle brush"], (240, 110, 75, 12), dict(head_kind="brush", height=35)),
    ("comb", ["comb"], ["hair comb"], (190, 150, 45, 3), dict(head_kind="tines", tines=12, height=5)),
    ("razor", ["razor", "shaver", "razors"], ["safety razor", "disposable razor"], (150, 40, 42, 7), dict(head_kind="pad", height=16)),
    ("back_scrubber", [], ["back brush", "bath brush", "back scrubber", "loofah brush"], (420, 130, 80, 12), dict(head_kind="brush", height=40)),
    ("toilet_brush_long", [], ["bottle brush", "dish brush", "washing up brush", "scrubbing brush"], (260, 80, 55, 9), dict(head_kind="brush", height=40)),
    ("shoe_horn", [], ["shoe horn", "shoehorn"], (450, 110, 45, 7), dict(head_kind="flat", height=10)),
    ("fly_swatter", [], ["fly swatter", "fly swat"], (450, 110, 100, 5), dict(head_kind="flat", height=6)),
    ("back_hook_umbrella", [], ["walking cane", "cane", "crook"], (900, 150, 60, 12), dict(head_kind="hook", height=24)),
    ("baseball_bat", [], ["baseball bat", "cricket bat", "rounders bat", "bat"], (840, 380, 70, 16), dict(head_kind="pad", height=70)),
    ("paddle", ["paddle", "oar"], ["kayak paddle", "canoe paddle", "sup paddle"], (1500, 450, 200, 16), dict(head_kind="flat", height=30)),
    ("rake", ["rake"], ["garden rake", "leaf rake"], (1600, 120, 400, 14), dict(head_kind="tines", tines=12, height=30)),
    ("shovel", ["shovel", "spade"], ["snow shovel", "garden spade"], (1100, 290, 210, 16), dict(head_kind="flat", height=30)),
    ("hoe", ["hoe"], ["garden hoe", "dutch hoe"], (1500, 80, 160, 14), dict(head_kind="flat", height=20)),
    ("screwdriver", ["screwdriver", "screwdrivers"], [], (220, 110, 30, 14), dict(head_kind="pad", height=6)),
    ("spanner", ["spanner", "wrench"], ["adjustable spanner", "adjustable wrench"], (250, 50, 55, 7), dict(head_kind="tines", tines=2, height=10)),
    ("saw", ["saw", "handsaw"], ["hand saw", "tenon saw"], (600, 450, 120, 18), dict(head_kind="blade", height=30)),
    ("pliers", ["pliers"], ["needle nose pliers"], (200, 70, 45, 9), dict(head_kind="tines", tines=2, height=15)),
    ("paintbrush", ["paintbrush"], ["paint brush", "paint roller"], (240, 60, 55, 8), dict(head_kind="brush", height=18)),
    ("mop_head", [], ["squeegee", "window squeegee", "floor squeegee"], (1300, 70, 450, 12), dict(head_kind="pad", height=40)),
    ("feather_duster", [], ["feather duster", "duster", "cobweb brush"], (600, 250, 120, 12), dict(head_kind="pad", height=120)),
    ("ice_scraper", [], ["ice scraper", "snow brush"], (500, 120, 110, 14), dict(head_kind="flat", height=20)),
]


def _register_long_tools():
    for name, words, phrases, (length, head, head_w, hr), opts in LONG_TOOLS:
        def fn(length=length, head=head, head_w=head_w, hr=hr, opts=opts):
            return long_tool(length, head, head_w, hr, **opts)
        fn.__doc__ = name.replace("_", " ").capitalize() + "."
        shape(name, words, phrases, kind="long")(fn)


_register_long_tools()


# ---------------------------------------------------------------------------------------------
# electronics, appliances and gadgets with a front


BODIES = [
    # name, words, phrases, (w, d, h), options, facing
    ("tv_screen", ["tv", "television", "telly"], ["flat screen tv", "smart tv", "led tv"], (1230, 60, 710), dict(r=12, feet=False, screen=(20, 1210, 20, 690)), True),
    ("desktop_pc", [], ["desktop computer", "pc tower", "computer tower", "gaming pc"], (210, 450, 460), dict(r=10, front_panel=(20, 190, 330, 440), buttons=1), True),
    ("games_console", ["console", "playstation", "xbox", "ps5", "ps4"], ["game console", "gaming console"], (390, 260, 100), dict(r=18, feet=False, front_panel=(20, 370, 30, 70)), True),
    ("router", ["router", "modem"], ["wifi router", "internet router"], (220, 150, 45), dict(r=14, buttons=3), True),
    ("soundbar", ["soundbar"], ["sound bar"], (900, 90, 70), dict(r=25, feet=False, front_panel=(20, 880, 10, 60)), True),
    ("radio", ["radio"], ["dab radio", "digital radio", "clock radio"], (260, 110, 150), dict(r=20, screen=(140, 240, 90, 130), dial=(70, 75, 35), handle=True), True),
    ("projector", ["projector"], ["home projector", "mini projector"], (300, 220, 110), dict(r=20, dial=(80, 55, 38)), True),
    ("record_player", [], ["record player", "turntable", "gramophone"], (420, 350, 120), dict(r=12, feet=True), True),
    ("hifi_amp", [], ["amplifier", "amp", "stereo", "hi fi", "hifi", "av receiver"], (430, 350, 150), dict(r=8, screen=(140, 290, 80, 120), dial=(360, 75, 30)), True),
    ("keyboard_pc", [], ["computer keyboard", "gaming keyboard"], (440, 140, 35), dict(r=8, feet=False, slot=(20, 420, 20, 120)), False),
    ("printer_small", [], ["photo printer", "label printer", "3d printer"], (420, 380, 420), dict(r=15, screen=(40, 380, 60, 380)), True),
    ("scanner", ["scanner", "shredder"], ["paper shredder", "flatbed scanner"], (330, 250, 380), dict(r=20, slot=(40, 290, 110, 140)), True),
    ("dehumidifier", ["dehumidifier"], ["air conditioner portable", "portable air con", "portable ac"], (350, 250, 600), dict(r=40, front_panel=(30, 320, 60, 300), buttons=3, handle=True), True),
    ("dishwasher_slim", [], ["slimline dishwasher", "countertop dishwasher", "table top dishwasher"], (550, 500, 440), dict(r=20, front_panel=(20, 530, 20, 400)), True),
    ("chest_freezer", [], ["chest freezer", "deep freezer", "box freezer"], (900, 600, 850), dict(r=30, lid_line=780, front_panel=(400, 500, 700, 760)), True),
    ("water_dispenser", [], ["water dispenser", "water cooler", "drinks fridge"], (320, 330, 1000), dict(r=25, front_panel=(80, 240, 500, 700)), True),
    ("bread_maker", [], ["bread maker", "breadmaker", "bread machine"], (300, 380, 320), dict(r=60, screen=(80, 220, 250, 300)), True),
    ("waffle_maker", [], ["waffle maker", "sandwich maker", "toastie maker", "panini press", "health grill"], (300, 280, 110), dict(r=60, lid_line=55, handle=True), True),
    ("electric_grill", [], ["electric grill", "table grill", "griddle", "hot plate", "induction hob"], (500, 330, 90), dict(r=25, dial=(420, 45, 22)), True),
    ("ice_maker", [], ["ice maker", "ice machine", "soda maker", "sodastream"], (260, 360, 360), dict(r=40, front_panel=(40, 220, 60, 300)), True),
    ("coffee_grinder", [], ["coffee grinder", "spice grinder", "bean grinder"], (140, 160, 260), dict(r=50, front_panel=(30, 110, 40, 120)), True),
    ("steam_mop_base", [], ["steam cleaner", "carpet cleaner", "pressure washer"], (340, 300, 500), dict(r=50, handle=True, dial=(170, 250, 40)), True),
    ("wet_vacuum", [], ["cylinder vacuum", "wet dry vacuum", "shop vac", "hoover cylinder"], (400, 300, 320), dict(r=100, handle=True), True),
    ("sewing_kit_box", [], ["sewing kit", "craft box", "art box", "tackle case"], (300, 200, 150), dict(r=15, handle=True, lid_line=100, feet=False), True),
    ("laptop_open", [], ["open laptop", "notebook computer", "macbook", "chromebook"], (330, 230, 20), dict(r=8, feet=False), False),
    ("tablet_stand", [], ["docking station", "charging station", "charger dock"], (200, 120, 60), dict(r=20), True),
    ("baby_monitor", [], ["baby monitor", "video monitor", "intercom"], (110, 60, 150), dict(r=25, screen=(15, 95, 60, 135)), True),
    ("smoke_detector_box", [], ["carbon monoxide detector", "security camera", "doorbell camera"], (120, 120, 60), dict(r=40, dial=(60, 30, 20)), True),
    ("drone", ["drone"], ["quadcopter"], (350, 350, 110), dict(r=40, feet=True), False),
    ("vr_headset", [], ["vr headset", "virtual reality headset", "oculus", "quest"], (190, 110, 100), dict(r=40, feet=False, front_panel=(20, 170, 20, 80)), True),
    ("game_controller", ["controller", "gamepad", "joypad"], ["game controller", "joystick"], (160, 100, 60), dict(r=40, feet=False, buttons=4), False),
    ("power_bank", [], ["power bank", "portable charger", "battery pack", "power station"], (150, 70, 25), dict(r=12, feet=False), False),
    ("external_drive", [], ["hard drive", "external drive", "ssd", "nas"], (120, 80, 20), dict(r=8, feet=False), False),
    ("sat_box", [], ["set top box", "tv box", "sky box", "cable box", "freeview box", "apple tv", "streaming box"], (280, 200, 45), dict(r=12, screen=(100, 180, 15, 30)), True),
    ("electric_heater_panel", [], ["panel heater", "convector heater", "radiator heater"], (800, 90, 450), dict(r=20, vents=True), True),
    ("microwave_small", [], ["compact microwave", "mini microwave", "countertop oven"], (440, 340, 260), dict(r=15, screen=(20, 320, 20, 240), front_panel=(330, 420, 30, 230)), True),
    ("toaster_2slice", [], ["two slice toaster", "four slice toaster", "long slot toaster"], (300, 180, 190), dict(r=40, slot=(40, 260, 50, 70)), True),
    ("kitchen_scale", [], ["kitchen scale", "kitchen scales", "food scale", "postal scale"], (220, 170, 25), dict(r=15, screen=(60, 160, 5, 20)), True),
    ("bathroom_scale", [], ["bathroom scale", "bathroom scales", "weighing scale", "scales"], (300, 300, 25), dict(r=40, feet=False), False),
    ("hair_straightener", [], ["hair straightener", "straighteners", "curling iron", "curling tongs"], (300, 40, 35), dict(r=12, feet=False), False),
    ("electric_shaver", [], ["electric shaver", "beard trimmer", "clippers", "hair clippers"], (60, 45, 160), dict(r=18, feet=False, front_panel=(10, 50, 60, 100)), True),
    ("sewing_machine_small", [], ["overlocker", "embroidery machine", "label maker"], (350, 180, 280), dict(r=30, dial=(300, 200, 30)), True),
    ("safe_box_small", [], ["cash box", "money box", "lock box", "key safe"], (250, 180, 90), dict(r=8, dial=(125, 45, 20), handle=True), True),
    ("first_aid_case", [], ["medicine box", "medical kit", "pill organiser"], (260, 100, 180), dict(r=20, handle=True), True),
    ("tissue_box", [], ["tissue box", "tissues", "box of tissues", "kleenex"], (230, 120, 90), dict(r=8, feet=False, slot=(60, 170, 45, 75)), False),
    ("lunch_box", [], ["lunch box", "lunchbox", "bento box", "food container", "tupperware", "sandwich box"], (220, 150, 80), dict(r=25, feet=False, lid_line=60), False),
    ("bread_bin", [], ["bread bin", "bread box"], (400, 250, 230), dict(r=60, front_panel=(160, 240, 110, 130)), True),
    ("jewellery_box", [], ["jewellery box", "jewelry box", "trinket box", "music box", "watch box"], (230, 160, 90), dict(r=10, feet=True, lid_line=60), False),
    ("makeup_case", [], ["makeup case", "vanity case", "cosmetic case", "beauty case", "train case"], (300, 200, 220), dict(r=25, handle=True, lid_line=160), True),
    ("shoebox", ["shoebox"], ["shoe box", "boot box"], (330, 210, 120), dict(r=4, feet=False, lid_line=95), False),
    ("pizza_box", [], ["pizza box", "cake box", "gift box"], (330, 330, 45), dict(r=3, feet=False, lid_line=38), False),
    ("wine_case", [], ["wine case", "case of wine", "case of beer", "crate of beer", "six pack"], (330, 250, 330), dict(r=4, feet=False), False),
    ("printer_paper", [], ["printer paper", "ream of paper", "a4 paper", "copy paper"], (297, 210, 55), dict(r=2, feet=False), False),
    ("ice_pack_cooler_bag", [], ["cool bag", "cooler bag", "insulated bag", "lunch bag"], (320, 220, 260), dict(r=60, handle=True, feet=False), True),
]


def _register_bodies():
    for name, words, phrases, (w, d, h), opts, facing in BODIES:
        def fn(w=w, d=d, h=h, opts=opts):
            return body(w, d, h, **opts)
        fn.__doc__ = name.replace("_", " ").capitalize() + "."
        kind = "thin" if min(w, d, h) * 5 < max(w, d, h) and min(w, d, h) == h else None
        shape(name, words, phrases, kind=kind, facing=facing)(fn)


_register_bodies()


# ---------------------------------------------------------------------------------------------
# clothing, accessories and bags


@shape("folded_shirt", ["shirt", "shirts", "blouse"], ["t shirt", "tshirt", "tee shirt", "polo shirt", "dress shirt"])
def folded_shirt():
    """Folded shirt: a neat layered fold with the collar on top."""
    return garment_stack(300, 220, 60, layers=3, collar=True, sleeves=True)


@shape("folded_trousers", ["trousers", "jeans", "pants", "shorts", "leggings", "skirt"], ["pair of jeans", "pair of trousers"])
def folded_trousers():
    """Folded trousers: long flat layers."""
    return garment_stack(380, 280, 50, layers=4)


@shape("folded_jumper", ["jumper", "sweater", "hoodie", "cardigan", "sweatshirt", "fleece"], [])
def folded_jumper():
    """Folded jumper: thick soft layers with the sleeves folded across."""
    return garment_stack(330, 280, 110, layers=3, sleeves=True)


@shape("folded_towel", ["towel", "towels", "flannel", "facecloth", "washcloth"], ["bath towel", "hand towel", "beach towel", "face cloth", "tea towel"])
def folded_towel():
    """Folded towel: a plump stack of folds."""
    return garment_stack(350, 260, 110, layers=4)


@shape("folded_blanket", ["blanket", "duvet", "quilt", "throw", "comforter", "sheets", "bedding"], ["bed sheet", "fitted sheet", "duvet cover", "pillow case"])
def folded_blanket():
    """Folded blanket or duvet: thick, wide layers."""
    return garment_stack(600, 450, 220, layers=5)


@shape("socks", ["socks", "sock", "underwear", "boxers", "briefs", "tights"], ["pair of socks"], kind="round")
def socks():
    """Balled-up socks."""
    return Mesh().add(sphere((60, 50, 40), (60, 50, 40), 8, 4)).add(sphere((110, 50, 35), (30, 32, 30), 8, 4))


@shape("dress_hanger", ["dress", "coat", "jacket", "suit", "blazer", "gown", "raincoat"], ["on a hanger", "suit bag", "garment bag", "suit carrier"], kind="thin")
def dress_hanger():
    """A garment on its hanger, seen flat."""
    m = Mesh()
    m.add(sweep([(250, 40, 1000), (250, 40, 1060), (280, 40, 1080), (300, 40, 1060)], 5, 6))
    m.add(bar((40, 40, 940), (250, 40, 1000), 8, 8)).add(bar((460, 40, 940), (250, 40, 1000), 8, 8))
    m.add(hexahedron([(0, 0, 0), (500, 0, 0), (500, 80, 0), (0, 80, 0), (60, 10, 950), (440, 10, 950), (440, 70, 950), (60, 70, 950)]))
    return m


@shape("sun_hat", ["hat", "hats", "sunhat", "fedora", "panama", "stetson"], ["sun hat", "straw hat", "bucket hat", "cowboy hat"], kind="round")
def sun_hat():
    """Brimmed hat: a wide brim and a crown."""
    return Mesh().add(lathe([(1, 0), (1, 6), (0.45, 12), (0.42, 90), (0.3, 110), (0, 112)], 12, "z", (190, 190), (190, 190)))


@shape("cap", ["cap", "caps", "baseball cap"], ["peaked cap", "snapback"], kind="round")
def cap():
    """Baseball cap: a round crown with its peak."""
    m = Mesh().add(lathe([(1, 0), (0.95, 40), (0.7, 90), (0.2, 108), (0, 110)], 12, "z", (95, 95), (95, 95)))
    return m.add(prism([(20, 0), (170, 0), (150, -80), (40, -80)], "z", 0, 8))


@shape("beanie", ["beanie", "bobble", "tuque"], ["woolly hat", "bobble hat", "knitted hat"], kind="round")
def beanie():
    """Knitted beanie with a turned-up edge and a bobble."""
    m = Mesh().add(lathe([(1, 0), (1.04, 35), (1, 40), (0.95, 120), (0.6, 180), (0, 195)], 12, "z", (100, 100), (100, 100)))
    return m.add(sphere((100, 100, 215), (30, 30, 30), 8, 4))


@shape("handbag", ["handbag", "purse", "satchel"], ["hand bag", "shoulder bag", "cross body bag", "crossbody bag"])
def handbag():
    """Handbag: a structured body, a flap and a handle."""
    return bag(320, 140, 320, 0.85, "one", flap=True)


@shape("clutch_bag", ["clutch", "wallet", "billfold", "cardholder", "passport"], ["clutch bag", "evening bag", "card holder", "passport holder"], kind="thin")
def clutch_bag():
    """Wallet or clutch: a slim soft case with a flap."""
    m = Mesh().add(rbox(0, 220, 0, 25, 0, 120, 8, 6))
    return m.add(hexahedron([(0, -3, 60), (220, -3, 60), (220, 3, 60), (0, 3, 60),
                             (5, -3, 118), (215, -3, 118), (215, 3, 118), (5, 3, 118)]))


@shape("briefcase", ["briefcase", "attache", "portfolio"], ["brief case", "attache case", "document case"], facing=True)
def briefcase():
    """Briefcase: a slim rigid case with a carry handle and two latches."""
    m = Mesh().add(rbox(0, 440, 0, 110, 0, 320, 14, 8))
    m.add(box(60, 110, -6, 0, 260, 290)).add(box(330, 380, -6, 0, 260, 290))
    return m.add(sweep([(170, 55, 320), (175, 55, 370), (265, 55, 370), (270, 55, 320)], 9, 6))


@shape("messenger_bag", [], ["messenger bag", "laptop case", "courier bag"], facing=True)
def messenger_bag():
    """Messenger bag: a soft box with a flap and a long strap."""
    return bag(400, 130, 420, 0.95, None, flap=True, strap=True)


@shape("school_bag", [], ["drawstring bag", "gym sack", "laundry bag", "bin bag", "bin liner", "sack"])
def school_bag():
    """Drawstring bag or sack: a soft body gathered at the top."""
    return Mesh().add(lathe([(0.8, 0), (1, 40), (1, 280), (0.6, 360), (0.2, 390), (0.25, 410)], 12, "z", (180, 130), (180, 130)))


@shape("belt", ["belt", "belts", "tie", "ties", "necklace", "bracelet", "scarf", "scarves"], ["rolled belt"], kind="round")
def belt():
    """A belt, tie or scarf rolled into a coil."""
    return Mesh().add(torus((50, 50, 20), (38, 38), 0.35, 20, 16, 8))


@shape("watch", ["watch", "watches", "smartwatch", "fitbit"], ["wrist watch", "fitness tracker"], kind="thin")
def watch():
    """Wristwatch: a round face on its strap, lying flat."""
    m = Mesh().add(box(0, 240, 35, 65, 0, 4))
    return m.add(lathe([(1, 4), (1, 14), (0.9, 16)], 12, "z", (120, 50), (22, 22)))


@shape("sunglasses", ["sunglasses", "glasses", "spectacles", "goggles", "shades"], ["reading glasses", "ski goggles", "swimming goggles"], kind="long")
def sunglasses():
    """Glasses: two lenses on a bridge, the arms folded behind."""
    m = Mesh()
    for x in (40, 110):
        m.add(lathe([(1, 0), (1, 6)], 12, "y", (x, 25), (32, 24)))
    m.add(box(72, 78, 0, 6, 40, 46)).add(box(8, 142, 6, 14, 30, 36))
    return m


@shape("gloves", ["gloves", "glove", "mittens", "mitts"], ["pair of gloves"], kind="thin")
def gloves():
    """A pair of mittens lying flat, one on the other, thumbs out to the side."""
    m = Mesh()
    for k, z in enumerate((0, 24)):
        o = k * 14
        m.add(soft_box(o, 240 + o, o, 110 + o, z, z + 24, 50, 12, 7, segs=3, arc=1))
        m.add(soft_box(60 + o, 130 + o, 100 + o, 150 + o, z + 2, z + 22, 22, 8, 5, segs=2, arc=1))
    return m


@shape("sandal", ["sandal", "sandals", "flipflops", "slippers", "slipper", "slides"], ["flip flops", "flip flop", "house shoes"], kind="long")
def sandal():
    """Sandal or slipper: a sole with a strap over the front."""
    m = Mesh().add(rbox(0, 270, 0, 100, 0, 25, 40, 8))
    return m.add(sweep(arc((80, 50, 25), 45, 180, 0, 8, "yz"), 8, 6))


@shape("high_heel", [], ["high heels", "high heel", "heels", "stilettos", "court shoes", "pumps"], kind="long")
def high_heel():
    """High-heeled shoe: the sole rising from the toe to the heel, the thin heel and the toe cap."""
    m = Mesh()
    m.add(hexahedron([(0, 0, 0), (100, 0, 0), (100, 75, 0), (0, 75, 0), (0, 0, 10), (100, 0, 10), (100, 75, 10), (0, 75, 10)]))
    m.add(hexahedron([(100, 0, 0), (230, 5, 80), (230, 70, 80), (100, 75, 0), (100, 0, 10), (230, 5, 90), (230, 70, 90), (100, 75, 10)]))
    m.add(box(212, 228, 28, 48, 0, 80))
    m.add(soft_box(0, 110, 3, 72, 10, 50, 25, 15, 12, segs=2, arc=1))
    # The heel counter: a low wall round the back of the foot.
    m.add(hexahedron([(190, 5, 68), (240, 5, 85), (240, 70, 85), (190, 70, 68), (190, 5, 92), (240, 5, 112), (240, 70, 112), (190, 70, 92)]))
    return m


@shape("wellington", ["wellies", "wellingtons", "gumboots"], ["wellington boots", "wellie boots"])
def wellington():
    """A pair of wellies standing side by side."""
    m = Mesh()
    for y in (0, 110):
        m.add(rbox(0, 290, y, y + 100, 0, 30, 30, 8)).add(rbox(170, 290, y + 5, y + 95, 30, 400, 35, 10))
        m.add(rbox(0, 180, y + 8, y + 92, 30, 110, 40, 25))
    return m


@shape("suit_case_small", [], ["cabin bag", "carry on", "hand luggage", "wheeled bag", "trolley bag"], facing=True)
def suit_case_small():
    """Cabin case: a hard shell on two wheels with a pull handle up."""
    m = Mesh().add(rbox(0, 360, 0, 220, 30, 550, 40, 20))
    for x in (40, 320):
        m.add(cyly(x, 25, 25, 160, 210))
        m.add(box(x - 15, x + 15, 190, 215, 550, 750))
    m.add(box(40, 320, 190, 215, 730, 760))
    return m


# ---------------------------------------------------------------------------------------------
# hygiene and grooming that are not round


@shape("soap_bar", ["soap", "soaps"], ["bar of soap", "soap bar"], kind="thin")
def soap_bar():
    """Bar of soap: a rounded pillow of a block."""
    return Mesh().add(soft_box(0, 90, 0, 60, 0, 28, 22, 12, 9, segs=3, arc=2))


@shape("soap_dish", [], ["soap dish", "soap holder", "trinket dish"], kind="thin")
def soap_dish():
    """Soap dish with a bar in it."""
    m = Mesh().add(rbox(0, 130, 0, 90, 0, 20, 25, 6))
    return m.add(soft_box(20, 110, 15, 75, 20, 42, 18, 10, 8, segs=2, arc=1))


@shape("toothpaste", ["toothpaste"], ["toothpaste tube", "tube of cream", "hand cream", "tube of toothpaste"], kind="long")
def toothpaste():
    """Toothpaste tube: round at the cap, crimped flat at the end."""
    m = Mesh().add(lathe([(1, 0), (1, 14)], 8, "x", (22, 22), (16, 16)))
    m.add(hexahedron([(14, 2, 2), (14, 42, 2), (14, 42, 42), (14, 2, 42),
                      (180, 0, 18), (180, 44, 18), (180, 44, 26), (180, 0, 26)]))
    return m.add(box(180, 195, 0, 44, 17, 27))


@shape("sponge", ["sponge", "sponges", "loofah", "scourer"], ["bath sponge", "kitchen sponge", "scrubbing pad"], kind="thin")
def sponge():
    """Sponge with a scourer layer."""
    m = Mesh().add(rbox(0, 110, 0, 70, 0, 30, 12, 6))
    return m.add(rbox(0, 110, 0, 70, 30, 40, 12, 3))


@shape("shower_caddy", [], ["shower caddy", "bath caddy", "shower basket", "shower shelf"], facing=True)
def shower_caddy():
    """Shower caddy: a wire basket with bottles standing in it."""
    m = Mesh().add(box(0, 300, 0, 120, 0, 12))
    for x0, x1, y0, y1 in ((0, 300, 0, 8), (0, 300, 112, 120), (0, 8, 0, 120), (292, 300, 0, 120)):
        m.add(box(x0, x1, y0, y1, 12, 90))
    for x, r, h in ((60, 35, 220), (140, 30, 190), (220, 38, 240)):
        m.add(lathe([(1, 12), (1, h * 0.8), (0.5, h * 0.9), (0.5, h)], 10, "z", (x, 60), (r, r)))
    m.add(sweep([(150, 120, 90), (150, 130, 300), (150, 110, 320)], 6, 6))
    return m


@shape("makeup_bag", [], ["make up bag", "cosmetics bag", "cosmetic bag", "vanity bag"], kind="long")
def makeup_bag():
    """Makeup bag: a soft zipped case, wider at the base."""
    return bag(240, 110, 190, 0.8, None, zip_top=True)


@shape("hairdryer_diffuser", [], ["hot brush", "air styler", "dyson airwrap", "blow dryer"], kind="long")
def hairdryer_diffuser():
    """Hot-air brush: a long barrel with bristles and a handle."""
    m = Mesh().add(lathe([(0.9, 0), (1, 30), (1, 200), (0.8, 220)], 10, "x", (35, 35), (35, 35)))
    return m.add(lathe([(1, 220), (1.4, 240), (1.4, 380), (0.9, 400)], 10, "x", (35, 35), (25, 25)))


@shape("bath_mat_rolled", [], ["shower mat", "rolled mat", "door mat", "doormat", "welcome mat"], kind="thin")
def bath_mat_rolled():
    """Door mat: a flat mat with a raised border."""
    m = Mesh().add(rbox(0, 750, 0, 450, 0, 12, 20, 3))
    return m.add(box(40, 710, 40, 410, 12, 18))


@shape("first_aid_bag", [], ["first aid bag", "medical bag", "doctors bag", "baby changing bag", "nappy bag", "diaper bag"], facing=True)
def first_aid_bag():
    """Changing or first-aid bag: a soft box bag with a zip top and handles."""
    return bag(400, 200, 380, 0.92, "two", zip_top=True)


@shape("nappy_pack", [], ["nappies", "diapers", "pack of nappies", "wet wipes", "baby wipes", "pack of wipes"])
def nappy_pack():
    """A soft plastic pack, rounded at the corners."""
    return Mesh().add(soft_box(0, 350, 0, 200, 0, 300, 50, 30, 30, segs=3, arc=2))


@shape("laundry_hamper", ["hamper"], ["laundry hamper", "linen basket", "wicker basket", "picnic basket", "picnic hamper"])
def laundry_hamper():
    """Wicker hamper with a lid and handles."""
    m = Mesh().add(rbox(0, 450, 0, 320, 0, 330, 40, 10))
    for k in range(5):
        m.add(box(-3, 453, -3, 323, 30 + k * 60, 38 + k * 60))
    m.add(rbox(-8, 458, -8, 328, 330, 370, 45, 10))
    for x in (-25, 450):
        m.add(box(x, x + 25, 120, 200, 260, 300))
    return m


# ---------------------------------------------------------------------------------------------
# a few more household things that need their own shape


@shape("ironing_basket_stool", [], ["step stool", "kick stool", "footstep", "step up"], facing=True)
def ironing_basket_stool():
    """Two-step stool."""
    m = Mesh()
    m.add(box(0, 400, 0, 300, 0, 220)).add(box(0, 400, 0, 150, 220, 450))
    return m


@shape("clothes_airer", [], ["clothes airer", "clothes horse", "drying rack clothes", "laundry rack"], kind="thin")
def clothes_airer():
    """Folded clothes airer: a flat frame of rails."""
    m = Mesh()
    w, h = 1300, 600
    m.add(box(0, 30, 0, 40, 0, h)).add(box(w - 30, w, 0, 40, 0, h))
    for k in range(8):
        z = 40 + k * 70
        m.add(cylx(20, z, 5, 30, w - 30, 6))
    return m


@shape("hand_mirror", [], ["hand mirror", "compact mirror", "vanity mirror", "makeup mirror"], kind="long")
def hand_mirror():
    """Hand mirror lying flat: a round glass in its rim and a handle."""
    m = Mesh().add(lathe([(1, 0), (1, 14), (0.9, 14), (0.9, 10)], 12, "z", (90, 90), (90, 90)))
    return m.add(rbox(170, 330, 72, 108, 0, 14, 10, 4))


@shape("wall_clock", [], ["kitchen clock", "station clock", "clock face"], kind="thin")
def wall_clock():
    """Wall clock: a round face in a deep rim, with hands."""
    m = Mesh().add(lathe([(1, 0), (1, 50), (0.9, 50), (0.9, 40)], 16, "y", (175, 175), (175, 175)))
    m.add(box(170, 180, -3, 40, 175, 290)).add(box(175, 270, -3, 40, 170, 180))
    return m


@shape("fire_screen", [], ["fire guard", "fireguard", "fire screen", "stair gate", "baby gate", "pet gate"], kind="thin")
def fire_screen():
    """Gate or fire guard: a frame of bars."""
    m = Mesh()
    w, h = 900, 750
    m.add(box(0, 30, 0, 30, 0, h)).add(box(w - 30, w, 0, 30, 0, h))
    m.add(box(0, w, 0, 30, 0, 30)).add(box(0, w, 0, 30, h - 30, h))
    for k in range(10):
        x = 80 + k * 76
        m.add(cyl(x, 15, 7, 30, h - 30, 6))
    return m


@shape("doll_house", [], ["dolls house", "doll house", "play house", "toy house"], facing=True)
def doll_house():
    """Dolls' house: a box house with a pitched roof and open rooms."""
    m = Mesh().add(box(0, 600, 0, 350, 0, 520))
    m.add(prism([(0, 520), (600, 520), (300, 760)], "y", 0, 350))
    for z in (60, 290):
        for x in (60, 330):
            m.add(box(x, x + 210, -5, 0, z, z + 170))
    return m


@shape("rocking_horse", [], ["rocking horse", "ride on toy", "hobby horse"], kind="long")
def rocking_horse():
    """Rocking horse: body, head and legs on curved rockers."""
    m = Mesh()
    for y in (40, 260):
        m.add(sweep([(0, y, 120), (200, y, 20), (700, y, 20), (900, y, 120)], 18, 6))
    for x in (230, 670):
        m.add(bar((x, 150, 40), (x - 60, 60, 350), 18, 18)).add(bar((x, 150, 40), (x + 60, 240, 350), 18, 18))
    m.add(rbox(160, 740, 90, 210, 330, 520, 50, 40))
    m.add(bar((700, 150, 480), (820, 150, 720), 55, 45)).add(rbox(780, 900, 110, 190, 650, 780, 30, 20))
    return m


@shape("toy_car", [], ["toy car", "ride on car", "toy truck", "pedal car"], facing=True)
def toy_car():
    """Toy car: a body, a cabin and four wheels."""
    m = Mesh()
    for x in (90, 510):
        for y in (0, 280):
            m.add(cyly(x, 80, 80, y, y + 60))
    m.add(rbox(0, 600, 30, 310, 60, 260, 40, 30)).add(rbox(150, 450, 50, 290, 260, 420, 40, 30))
    return m


@shape("teddy_bear", ["teddy", "plushie"], ["teddy bear", "soft toy", "stuffed animal", "cuddly toy"], kind="round")
def teddy_bear():
    """Teddy bear: a round body, head, ears, arms and legs."""
    m = Mesh().add(sphere((150, 130, 130), (110, 95, 120), 6, 3))
    m.add(sphere((150, 130, 300), (80, 75, 75), 6, 3))
    for x in (95, 205):
        m.add(sphere((x, 130, 365), (25, 20, 25), 6, 3))
        m.add(sphere((x - (60 if x < 150 else -60), 110, 160), (40, 40, 60), 6, 3))
        m.add(sphere((x, 60, 40), (45, 60, 40), 6, 3))
    return m


@shape("lego_box", [], ["lego", "building blocks", "toy bricks", "jigsaw", "puzzle", "board games"])
def lego_box():
    """A big toy box with bricks on its lid."""
    m = Mesh().add(box(0, 480, 0, 380, 0, 100))
    for x, y, c in ((60, 60, 1), (200, 120, 2), (320, 60, 1)):
        m.add(box(x, x + 32 * c * 2, y, y + 64, 100, 140))
    return m


@shape("keyboard_piano", [], ["digital piano", "electric keyboard", "keyboard piano", "synthesiser", "synthesizer", "keyboard"], kind="long", facing=True)
def keyboard_piano():
    """Electric keyboard: a long body with its keys along the front."""
    m = Mesh().add(rbox(0, 1300, 0, 350, 0, 120, 20, 10))
    m.add(box(60, 1240, -5, 160, 60, 80))
    for k in range(20):
        x = 90 + k * 57
        m.add(box(x, x + 30, 60, 160, 80, 100))
    return m


@shape("violin_case", [], ["violin case", "instrument case", "guitar case", "cello case", "trumpet case"], kind="long")
def violin_case():
    """Instrument case: a shaped hard case with a handle."""
    m = Mesh().add(lathe([(0.55, 0), (0.95, 120), (1, 250), (0.75, 420), (0.85, 560), (0.45, 800)], 10, "x", (140, 60), (140, 60)))
    return m.add(sweep([(330, 140, 120), (340, 140, 160), (470, 140, 160), (480, 140, 120)], 9, 6))


@shape("microphone_stand", [], ["mic stand", "music stand", "tripod", "camera tripod", "easel", "speaker stand"], kind="long")
def microphone_stand():
    """Tripod folded for carrying: three legs bundled with a head."""
    m = Mesh()
    for dy, dz in ((0, 0), (40, 0), (20, 34)):
        m.add(cylx(30 + dy, 30 + dz, 12, 0, 1200, 6))
    return m.add(rbox(1200, 1320, 0, 100, 0, 100, 20, 20))


# ---------------------------------------------------------------------------------------------
# output


def app_paths():
    root = os.path.abspath(os.path.join(HERE, "..", ".."))
    render = os.path.join(root, "app", "src", "main", "java", "com", "packabunch", "ui", "render")
    return root, render


def existing_names(render_dir):
    """Words and phrases the first library already answers to, so none is taken twice."""
    text = open(os.path.join(render_dir, "FamilyMeshes.kt"), encoding="utf-8").read()
    words = set(re.findall(r'"([a-z ]+)"', text))
    return words


ALIASES = [("sofa bed", "SOFA"), ("futon", "SOFA"), ("day bed", "SOFA"), ("daybed", "SOFA"),
           ("chaise longue", "SOFA"), ("chaise", "SOFA"), ("corner sofa", "SOFA")]


def enum_name(name):
    return name.upper()


def write_kotlin(render_dir):
    taken = existing_names(render_dir)
    seen = set()
    word_rows, phrase_rows = [], []
    kinds = {"round": [], "long": [], "thin": []}
    for entry in REGISTRY:
        e = enum_name(entry["name"])
        for w in entry["words"]:
            if w in taken or w in seen or " " in w:
                continue
            seen.add(w)
            word_rows.append((w, e))
        for p in entry["phrases"]:
            p = p.lower()
            if p in taken or p in seen:
                continue
            seen.add(p)
            (phrase_rows if " " in p else word_rows).append((p, e))
        if entry["kind"] in kinds:
            kinds[entry["kind"]].append(e)
    # Names that belong to a shape from the first library.
    for p, e in ALIASES:
        if p not in taken and p not in seen:
            seen.add(p)
            (phrase_rows if " " in p else word_rows).append((p, e))
    # Longest phrases first, so "hand soap dispenser" is not taken by "hand soap".
    phrase_rows.sort(key=lambda r: -len(r[0]))
    out = []
    out.append("package com.packabunch.ui.render\n")
    out.append("// Generated by tools/geometry/everyday.py — do not edit by hand; edit the script and re-run it.\n")
    out.append("/** Whole names the everyday shapes answer to, longest first. Checked after the first library's. */")
    out.append("internal val EVERYDAY_PHRASES: List<Pair<String, GeometryFamily>> = listOf(")
    for p, e in phrase_rows:
        out.append('    "%s" to GeometryFamily.%s,' % (p, e))
    out.append(")\n")
    out.append("/** Last words of a name the everyday shapes answer to. Checked after the first library's. */")
    out.append("internal val EVERYDAY_WORDS: Map<String, GeometryFamily> = mapOf(")
    for w, e in word_rows:
        out.append('    "%s" to GeometryFamily.%s,' % (w, e))
    out.append(")\n")
    for k, label, doc in (("round", "EVERYDAY_ROUND", "Round everyday shapes: a scan that measured round keeps these."),
                          ("long", "EVERYDAY_LONG", "Everyday shapes that run along the longer side of their box."),
                          ("thin", "EVERYDAY_THIN", "Everyday shapes that stand on their thinnest side when that is not their height.")):
        out.append("/** %s */" % doc)
        out.append("internal val %s: Set<GeometryFamily> = setOf(" % label)
        for e in kinds[k]:
            out.append("    GeometryFamily.%s," % e)
        out.append(")\n")
    with open(os.path.join(render_dir, "EverydayFamilies.kt"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(out))

    # The enum entries, between markers in FamilyMeshes.kt.
    path = os.path.join(render_dir, "FamilyMeshes.kt")
    text = open(path, encoding="utf-8").read()
    start, end = "    // BEGIN EVERYDAY (generated by tools/geometry/everyday.py)\n", "    // END EVERYDAY\n"
    block = start + "".join("    %s(R.raw.family_%s),\n" % (enum_name(r["name"]), r["name"]) for r in REGISTRY) + end
    if start in text:
        a = text.index(start)
        b = text.index(end) + len(end)
        text = text[:a] + block + text[b:]
    else:
        anchor = "    AQUARIUM(R.raw.family_aquarium),\n"
        text = text.replace(anchor, anchor + block, 1)
    open(path, "w", encoding="utf-8").write(text)
    return len(word_rows), len(phrase_rows)


REAL = {}


def emit(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    first = {name for name, _ in F.FAMILIES}
    names = [r["name"] for r in REGISTRY]
    dupes = {n for n in names if names.count(n) > 1} | (set(names) & first)
    if dupes:
        print("DUPLICATE NAMES", sorted(dupes))
        return True
    failed = False
    rows = []
    for r in REGISTRY:
        mesh = r["fn"]()
        faces = F.normalise(mesh.faces)
        problems = F.check(r["name"], faces)
        if problems:
            failed = True
            print("FAIL", r["name"], problems[:3])
        data = {
            "family": r["name"], "version": 1, "params": {}, "bounds_mm": [1000, 1000, 1000],
            "faces": [dict({"points": [[round(c * F.S, 1) for c in p] for p in f], "part": f.part},
                           **({"smooth": True} if isinstance(f, F.SmoothFace) else {})) for f in faces],
        }
        with open(os.path.join(out_dir, "family_%s.json" % r["name"]), "w") as fh:
            json.dump(data, fh, separators=(",", ":"))
        rows.append((r["name"], len(faces), mesh.parts, r["doc"].split("\n")[0]))
        pts = [p for f in mesh.faces for p in f]
        REAL[r["name"]] = [round(max(p[i] for p in pts) - min(p[i] for p in pts)) for i in range(3)]
    with open(os.path.join(HERE, "EVERYDAY.md"), "w", encoding="utf-8") as fh:
        fh.write("# Everyday item shapes\n\nGenerated by `everyday.py`; original geometry authored there. "
                 "Same contract as `README.md`.\n\n| Shape | Quads | Parts | What it is |\n| --- | ---: | ---: | --- |\n")
        for name, q, parts, doc in rows:
            fh.write("| `%s` | %d | %d | %s |\n" % (name, q, parts, doc))
    with open(os.path.join(HERE, "everyday_sizes.json"), "w") as fh:
        json.dump(REAL, fh, indent=0)
    return failed


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "everyday_out"
    bad = emit(out)
    _, render = app_paths()
    if not bad and os.path.isdir(render):
        w, p = write_kotlin(render)
        print("%d everyday shapes, %d words, %d phrases" % (len(REGISTRY), w, p))
    sys.exit(1 if bad else 0)
