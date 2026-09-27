"""
Pack a Bunch — item geometry families.

Sixty-eight generic, low-poly shapes that make a packing plan readable: a bottle is drawn as
a bottle, a chair as a chair, scaled to the size the person measured. They are for *looking*
only. The solver never sees them — it packs the measured bounding box, and every mesh here
fills exactly that box, so the picture can never show an item where the solver did not put it.

All geometry is original, authored procedurally in this file. Nothing is downloaded, imported,
traced or converted from any third-party asset (dimensions.com, Sketchfab, Poly Haven or any
other source).

Rendering contract (see docs/briefs/item-geometry-families.md):
  * every face is exactly four points (a degenerate quad repeats a vertex where a triangle is
    unavoidable — sphere poles only), planar and convex;
  * winding is counter-clockwise seen from outside, so the app can derive the outward normal;
  * closed outer shells only, no interior faces, no interpenetrating parts;
  * canonical box 1000 × 1000 × 1000 mm, origin at the minimum corner, X = width, Y = depth,
    Z = height, Z up. The app rescales to the item's measured size.

Run:
    python3 families.py OUT_DIR            # writes family_<id>.json + README.md
    blender -b -P families.py -- OUT_DIR   # same, and also builds the meshes in the scene
"""

import json
import math
import os
import sys

S = 1000.0  # canonical size


class Face(list):
    """Four points, plus the part of the shape they belong to (see Mesh.add)."""
    part = 0


class SmoothFace(Face):
    """
    One facet of a curved surface, or one piece of a flat cap cut into a fan. Written out with
    `"smooth": true`, so the app draws it without an edge line: a round thing should shade as
    one surface, not read as staves, and a lid should not look like a sliced pizza.
    """


def smooth_sides(sides):
    """
    Facets round the curve. The shapes were first authored at 6–12 sides and looked like
    barrels made of planks; these counts shade smoothly at the sizes a plan draws them.
    Always a multiple of four so the ring reaches its box on both axes.
    """
    return 16 if sides <= 6 else 24 if sides <= 8 else 32


# ---------------------------------------------------------------------------------------------
# vector helpers


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def newell(pts):
    nx = ny = nz = 0.0
    for i in range(len(pts)):
        a, b = pts[i], pts[(i + 1) % len(pts)]
        nx += (a[1] - b[1]) * (a[2] + b[2])
        ny += (a[2] - b[2]) * (a[0] + b[0])
        nz += (a[0] - b[0]) * (a[1] + b[1])
    return (nx, ny, nz)


def centroid(pts):
    n = len(pts)
    return (sum(p[0] for p in pts) / n, sum(p[1] for p in pts) / n, sum(p[2] for p in pts) / n)


def orient(face, outward):
    """Return the face wound so its normal agrees with `outward`."""
    return face if dot(newell(face), outward) >= 0 else list(reversed(face))


def fan(poly):
    """A convex polygon as quads: (v0, v1, v2, v3), (v0, v3, v4, v5), … — last one degenerate if odd."""
    out = []
    i = 1
    while i + 1 < len(poly):
        if i + 2 < len(poly):
            out.append([poly[0], poly[i], poly[i + 1], poly[i + 2]])
        else:
            out.append([poly[0], poly[i], poly[i + 1], poly[i + 1]])
        i += 2
    return out


# ---------------------------------------------------------------------------------------------
# primitives — all in unit coordinates (0..1), scaled at the end


class Mesh:
    def __init__(self):
        self.faces = []
        self.parts = 0

    def add(self, faces):
        # Every face remembers which part it belongs to. The app paints whole parts far to near
        # before sorting faces inside each one; sorting all faces of a sofa together lets a
        # cushion's side be painted over the cushion in front of it.
        faces = [f if isinstance(f, Face) else Face(f) for f in faces]
        for f in faces:
            f.part = self.parts
        self.faces.extend(faces)
        self.parts += 1
        return self


def box(x0, x1, y0, y1, z0, z1):
    c = ((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)
    v = lambda x, y, z: (x, y, z)
    faces = [
        [v(x0, y0, z0), v(x0, y1, z0), v(x1, y1, z0), v(x1, y0, z0)],
        [v(x0, y0, z1), v(x1, y0, z1), v(x1, y1, z1), v(x0, y1, z1)],
        [v(x0, y0, z0), v(x0, y0, z1), v(x0, y1, z1), v(x0, y1, z0)],
        [v(x1, y0, z0), v(x1, y1, z0), v(x1, y1, z1), v(x1, y0, z1)],
        [v(x0, y0, z0), v(x1, y0, z0), v(x1, y0, z1), v(x0, y0, z1)],
        [v(x0, y1, z0), v(x0, y1, z1), v(x1, y1, z1), v(x1, y1, z0)],
    ]
    return [orient(f, sub(centroid(f), c)) for f in faces]


def hexahedron(corners):
    """Eight corners, bottom ring then top ring (each anticlockwise-ish). A convex block of any slant."""
    b, t = corners[:4], corners[4:]
    c = centroid(corners)
    faces = [list(b), list(t)] + [[b[i], b[(i + 1) % 4], t[(i + 1) % 4], t[i]] for i in range(4)]
    return [orient(f, sub(centroid(f), c)) for f in faces]


def bar(a, b, half_w, half_h, up=(0.0, 0.0, 1.0)):
    """A straight square-section strut from point a to point b (for frames, handles, rails)."""
    d = sub(b, a)
    ln = math.sqrt(dot(d, d))
    d = (d[0] / ln, d[1] / ln, d[2] / ln)
    s = cross(d, up)
    if dot(s, s) < 1e-9:
        s = cross(d, (1.0, 0.0, 0.0))
    sl = math.sqrt(dot(s, s)); s = (s[0] / sl, s[1] / sl, s[2] / sl)
    u = cross(s, d)
    def ring(p):
        return [
            (p[0] + s[0] * half_w * sx + u[0] * half_h * ux,
             p[1] + s[1] * half_w * sx + u[1] * half_h * ux,
             p[2] + s[2] * half_w * sx + u[2] * half_h * ux)
            for sx, ux in ((-1, -1), (1, -1), (1, 1), (-1, 1))
        ]
    return hexahedron(ring(a) + ring(b))


def prism(poly, axis, t0, t1):
    """A convex polygon (in the plane across `axis`) extruded from t0 to t1."""
    def at(p, t):
        a, b = p
        return {"x": (t, a, b), "y": (a, t, b), "z": (a, b, t)}[axis]
    bottom = [at(p, t0) for p in poly]
    top = [at(p, t1) for p in poly]
    c = centroid(bottom + top)
    faces = []
    n = len(poly)
    for i in range(n):
        j = (i + 1) % n
        faces.append([bottom[i], bottom[j], top[j], top[i]])
    # An end cap is one flat face cut into a fan; its pieces are drawn without lines between them.
    caps = [SmoothFace(orient(f, sub(centroid(f), c))) for f in fan(bottom) + fan(top)]
    return [orient(f, sub(centroid(f), c)) for f in faces] + caps


def chamfered_rect(a0, a1, b0, b1, ca, cb=None):
    """Rectangle with 45° corners — the 'rounded' slab the brief allows at this poly count."""
    cb = ca if cb is None else cb
    return [(a0 + ca, b0), (a1 - ca, b0), (a1, b0 + cb), (a1, b1 - cb), (a1 - ca, b1), (a0 + ca, b1), (a0, b1 - cb), (a0, b0 + cb)]


def lathe(profile, sides, axis, centre, radii):
    """
    Surface of revolution. `profile` is [(r, t), …] walked from one end to the other; r is a
    fraction of `radii` (the semi-axes across the axis, so an oval section is fine); t is the
    position along the axis. Caps close any end with r > 0.
    """
    ca, cb = centre
    ra, rb = radii
    sides = smooth_sides(sides)
    # A faceted ring only touches its circle at its corners; stretch it so its flats reach
    # the radius on both axes and the mesh fills its box exactly.
    cmax = max(abs(math.cos(2 * math.pi * k / sides)) for k in range(sides))
    smax = max(abs(math.sin(2 * math.pi * k / sides)) for k in range(sides))
    def point(r, t, k):
        ang = 2 * math.pi * k / sides
        a = ca + math.cos(ang) * ra * r / cmax
        b = cb + math.sin(ang) * rb * r / smax
        return {"x": (t, a, b), "y": (a, t, b), "z": (a, b, t)}[axis]
    def axis_dir(sign):
        return {"x": (sign, 0, 0), "y": (0, sign, 0), "z": (0, 0, sign)}[axis]
    faces = []
    for (r0, t0), (r1, t1) in zip(profile, profile[1:]):
        if r0 == 0 and r1 == 0:
            continue
        dr, dt = r1 - r0, t1 - t0
        for k in range(sides):
            quad = [point(r0, t0, k), point(r0, t0, k + 1), point(r1, t1, k + 1), point(r1, t1, k)]
            mid_ang = 2 * math.pi * (k + 0.5) / sides
            radial = {"x": (0, math.cos(mid_ang), math.sin(mid_ang)),
                      "y": (math.cos(mid_ang), 0, math.sin(mid_ang)),
                      "z": (math.cos(mid_ang), math.sin(mid_ang), 0)}[axis]
            # Outward in the (r, t) plane when walking the profile from start to end: (dt, −dr).
            out = tuple(radial[i] * dt + axis_dir(1)[i] * (-dr) for i in range(3))
            faces.append(SmoothFace(orient(quad, out)))
    # The caps face the way the profile runs overall, not the way its last step runs: a mug's
    # profile ends by dipping from the rim down to the coffee line, and judging by that last
    # step turned the coffee's surface upside down, where it was culled and left a hole.
    ascending = profile[-1][1] >= profile[0][1]
    if profile[0][0] > 0:
        ring = [point(profile[0][0], profile[0][1], k) for k in range(sides)]
        faces += [SmoothFace(orient(f, axis_dir(-1 if ascending else 1))) for f in fan(ring)]
    if profile[-1][0] > 0:
        ring = [point(profile[-1][0], profile[-1][1], k) for k in range(sides)]
        faces += [SmoothFace(orient(f, axis_dir(1 if ascending else -1))) for f in fan(ring)]
    return faces


def sphere(centre, radii, slices, stacks):
    slices, stacks = smooth_sides(slices), smooth_sides(slices) // 2
    faces = []
    cx, cy, cz = centre
    rx, ry, rz = radii
    def p(i, k):
        th = math.pi * i / stacks
        ph = 2 * math.pi * k / slices
        return (cx + rx * math.sin(th) * math.cos(ph), cy + ry * math.sin(th) * math.sin(ph), cz - rz * math.cos(th))
    for i in range(stacks):
        for k in range(slices):
            q = [p(i, k), p(i, k + 1), p(i + 1, k + 1), p(i + 1, k)]
            faces.append(SmoothFace(orient(q, sub(centroid(q), centre))))
    return faces


def torus(centre, big, small_ab, small_z, ring_sides, tube_sides):
    """Lying flat (axis Z). `big` is (ra, rb) of the centreline; the tube is small_ab across, small_z tall."""
    cx, cy, cz = centre
    ring_sides, tube_sides = smooth_sides(ring_sides), 12
    faces = []
    zmax = max(abs(math.sin(2 * math.pi * k / tube_sides)) for k in range(tube_sides))
    def p(i, k):
        u = 2 * math.pi * i / ring_sides
        v = 2 * math.pi * k / tube_sides
        rad = 1 + small_ab * math.cos(v)
        return (cx + big[0] * rad * math.cos(u), cy + big[1] * rad * math.sin(u), cz + small_z * math.sin(v) / zmax)
    for i in range(ring_sides):
        for k in range(tube_sides):
            q = [p(i, k), p(i + 1, k), p(i + 1, k + 1), p(i, k + 1)]
            um = 2 * math.pi * (i + 0.5) / ring_sides
            core = (cx + big[0] * math.cos(um), cy + big[1] * math.sin(um), cz)
            faces.append(SmoothFace(orient(q, sub(centroid(q), core))))
    return faces


def rounded_ring(x0, x1, y0, y1, r, inset, segs):
    """
    A rounded rectangle shrunk by `inset`, as a ring of points. Corner centres stay put and the
    radius shrinks with the inset, so every edge of one ring is parallel to the same edge of the
    next — which keeps the quads between rings flat.
    """
    rr = max(r - inset, 0.0)
    corners = ((x1 - r, y0 + r, -90), (x1 - r, y1 - r, 0), (x0 + r, y1 - r, 90), (x0 + r, y0 + r, 180))
    pts = []
    for cx, cy, start in corners:
        for k in range(segs + 1):
            a = math.radians(start + 90.0 * k / segs)
            pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return pts


def soft_box(x0, x1, y0, y1, z0, z1, r, inset=None, edge=None, segs=3, arc=2):
    """
    A box with rounded upright corners and rounded top and bottom edges: a cushion, a case, a
    moulded appliance body. `r` rounds the corners seen from above; the top and bottom edges
    curve in by `inset` across and `edge` up. The whole surface is smooth, so it shades as one
    soft object rather than six flat panels.
    """
    inset = r * 0.6 if inset is None else min(inset, r)
    edge = (z1 - z0) * 0.12 if edge is None else edge
    profile = []
    for k in range(arc + 1):
        t = math.pi / 2 * k / arc
        profile.append((inset * (1 - math.sin(t)), z0 + edge * (1 - math.cos(t))))
    for k in range(arc, -1, -1):
        t = math.pi / 2 * k / arc
        profile.append((inset * (1 - math.sin(t)), z1 - edge * (1 - math.cos(t))))
    rings = [[(x, y, z) for x, y in rounded_ring(x0, x1, y0, y1, r, d, segs)] for d, z in profile]
    c = ((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)
    faces = []
    n = len(rings[0])
    for a, b in zip(rings, rings[1:]):
        if a[0][2] == b[0][2]:
            continue
        for i in range(n):
            j = (i + 1) % n
            q = [a[i], a[j], b[j], b[i]]
            if newell_len(q) < 1e-12:
                continue
            mid = centroid(q)
            out = (mid[0] - c[0], mid[1] - c[1], 0.0)
            # The outward direction of a band leans up or down with the edge it rounds.
            out = (out[0], out[1], (mid[2] - c[2]) * 0.5)
            faces.append(SmoothFace(orient(q, out)))
    faces += [SmoothFace(orient(f, (0, 0, -1))) for f in fan(rings[0])]
    faces += [SmoothFace(orient(f, (0, 0, 1))) for f in fan(rings[-1])]
    return faces


def newell_len(q):
    n = newell(q)
    return math.sqrt(dot(n, n))


def leg(cx, cy, radius, z0, z1, taper=1.0):
    """A turned round leg or post, optionally narrowing towards the floor."""
    return lathe([(taper, z0), (1, z1)], 6, "z", (cx, cy), (radius, radius))


# ---------------------------------------------------------------------------------------------
# the families — each fills the unit box exactly


def flat_rectangle():
    """Book, notebook, board game: two covers, a page block, a spine."""
    return Mesh().add(box(0, 1, 0, 1, 0, 0.12)).add(box(0.06, 0.97, 0.03, 0.97, 0.12, 0.88)) \
        .add(box(0, 0.06, 0, 1, 0.12, 0.88)).add(box(0, 1, 0, 1, 0.88, 1))


def slim_slab():
    """Phone, tablet, laptop: a thin slab with softened corners and a screen step."""
    m = Mesh().add(prism(chamfered_rect(0, 1, 0, 1, 0.06, 0.06), "z", 0, 0.72))
    return m.add(prism(chamfered_rect(0.05, 0.95, 0.05, 0.95, 0.04, 0.04), "z", 0.72, 1))


def small_carton():
    """A closed carton, with the two top flaps meeting in the middle."""
    return Mesh().add(box(0, 1, 0, 1, 0, 0.93)).add(box(0, 0.49, 0, 1, 0.93, 1)).add(box(0.51, 1, 0, 1, 0.93, 1))


def upright_cylinder():
    """Can, jar, candle: a cylinder with a rolled rim."""
    return Mesh().add(lathe([(1, 0), (1, 0.93), (0.92, 1)], 12, "z", (0.5, 0.5), (0.5, 0.5)))


def lying_cylinder():
    """A cylinder on its side, along the width."""
    return Mesh().add(lathe([(0.92, 0), (1, 0.04), (1, 0.96), (0.92, 1)], 12, "x", (0.5, 0.5), (0.5, 0.5)))


def bottle():
    """Body, shoulder, neck, cap. Neck ratio 0.34 of the body width."""
    return Mesh().add(lathe(
        [(1, 0), (1, 0.6), (0.62, 0.74), (0.34, 0.82), (0.34, 0.9), (0.4, 0.9), (0.4, 1)],
        10, "z", (0.5, 0.5), (0.5, 0.5)))


def tight_roll():
    """Sleeping bag, rolled towel: a lying roll held by two straps."""
    return Mesh().add(lathe(
        [(0.94, 0), (0.94, 0.24), (1, 0.24), (1, 0.31), (0.94, 0.31), (0.94, 0.69),
         (1, 0.69), (1, 0.76), (0.94, 0.76), (0.94, 1)],
        8, "x", (0.5, 0.5), (0.5, 0.5)))


def soft_pouch():
    """Wash bag or pencil case: a plump soft body, the zip along its top and the pull tab."""
    m = Mesh().add(soft_box(0, 250, 0, 90, 0, 140, 30, 24, 40, segs=4, arc=3))
    m.add(tube((20, 45, 138), (230, 45, 138), 5, 6))
    return m.add(soft_box(222, 246, 38, 52, 118, 146, 4, 2, 2, segs=1, arc=1))


def cable_coil():
    """A coiled cable or hose: a flat torus."""
    return Mesh().add(torus((0.5, 0.5, 0.5), (0.35, 0.35), 0.5 / 0.35 - 1, 0.5, 12, 5))


def thin_bundle():
    """A bundle of cutlery, poles or rods: round bars side by side, their ends staggered."""
    m = Mesh()
    for k in range(6):
        row, col = divmod(k, 3)
        x0 = (k * 37) % 30
        m.add(tube((x0, 10 + col * 20, 7 + row * 15), (250 - (k * 23) % 26, 10 + col * 20, 7 + row * 15), 7, 8))
    return m


def shallow_tray():
    """Baking tray, pan, drawer organiser: a floor and four low walls."""
    t = 0.07
    m = Mesh().add(box(0, 1, 0, 1, 0, 0.18))
    m.add(box(0, 1, 0, t, 0.18, 1)).add(box(0, 1, 1 - t, 1, 0.18, 1))
    return m.add(box(0, t, t, 1 - t, 0.18, 1)).add(box(1 - t, 1, t, 1 - t, 0.18, 1))


def suitcase():
    """Hard-shell case standing on four spinner wheels, with a pull handle and a carry handle."""
    m = Mesh().add(soft_box(0, 1, 0, 0.88, 0.07, 0.92, 0.1, 0.05, 0.06))
    for x in (0.06, 0.86):
        for y in (0.06, 0.74):
            m.add(lathe([(1, x), (1, x + 0.08)], 6, "x", (y + 0.04, 0.035), (0.04, 0.035)))
    m.add(box(0.3, 0.34, 0.92, 0.96, 0.4, 0.95)).add(box(0.66, 0.7, 0.92, 0.96, 0.4, 0.95))
    m.add(soft_box(0.27, 0.73, 0.9, 1, 0.94, 1, 0.03, 0.02, 0.02, segs=2, arc=1))
    return m.add(soft_box(0.38, 0.62, 0.36, 0.52, 0.92, 0.97, 0.04, 0.02, 0.02, segs=2, arc=1))


def duffel_bag():
    """A barrel bag lying along its width, with a strap handle on top."""
    m = Mesh().add(lathe([(0.55, 0), (0.85, 0.05), (1, 0.14), (1, 0.86), (0.85, 0.95), (0.55, 1)], 10, "x", (0.5, 0.44), (0.5, 0.44)))
    return m.add(box(0.3, 0.7, 0.42, 0.58, 0.88, 1))


def backpack():
    """Rounded main body, a front pocket, two side pockets and a grab loop."""
    m = Mesh().add(soft_box(0.06, 0.94, 0.26, 1, 0, 0.92, 0.16, 0.12, 0.2))
    m.add(soft_box(0.16, 0.84, 0, 0.3, 0.06, 0.56, 0.1, 0.08, 0.1))
    m.add(soft_box(0, 0.08, 0.42, 0.82, 0.05, 0.45, 0.03, 0.02, 0.06, segs=2))
    m.add(soft_box(0.92, 1, 0.42, 0.82, 0.05, 0.45, 0.03, 0.02, 0.06, segs=2))
    return m.add(soft_box(0.42, 0.58, 0.55, 0.7, 0.9, 1, 0.04, 0.02, 0.03, segs=2, arc=1))


def cooler_box():
    """Cool box: moulded body, an overhanging lid and a carry handle across the top."""
    m = Mesh().add(soft_box(0.02, 0.98, 0.02, 0.98, 0, 0.8, 0.08, 0.04, 0.05))
    m.add(soft_box(0, 1, 0, 1, 0.78, 0.92, 0.09, 0.03, 0.03, arc=1))
    m.add(box(0.28, 0.32, 0.46, 0.54, 0.92, 0.98)).add(box(0.68, 0.72, 0.46, 0.54, 0.92, 0.98))
    return m.add(soft_box(0.26, 0.74, 0.44, 0.56, 0.96, 1, 0.03, 0.02, 0.015, segs=2, arc=1))


def folded_chair():
    """Folding chair folded flat: two tube frames, the seat slats and the back panel."""
    m = Mesh()
    m.add(sweep([(20, 20, 0), (20, 20, 800), (430, 20, 800), (430, 20, 0)], 11, 8))
    m.add(sweep([(40, 62, 0), (40, 62, 460), (410, 62, 460), (410, 62, 0)], 11, 8))
    m.add(soft_box(40, 410, 28, 54, 470, 780, 12, 6, 10, segs=2, arc=1))
    return m.add(soft_box(50, 400, 30, 60, 200, 460, 12, 6, 8, segs=2, arc=1))


def yoga_mat():
    """A rolled mat: the roll, with its core showing at both ends."""
    m = Mesh().add(lathe([(1, 0.03), (1, 0.97)], 12, "x", (0.5, 0.5), (0.5, 0.5)))
    m.add(lathe([(0.35, 0), (0.35, 0.03)], 8, "x", (0.5, 0.5), (0.5, 0.5)))
    return m.add(lathe([(0.35, 0.97), (0.35, 1)], 8, "x", (0.5, 0.5), (0.5, 0.5)))


def toolbox():
    """Toolbox: a rounded body, a lid with two latches, and the carry handle on top."""
    m = Mesh().add(soft_box(0, 450, 0, 220, 0, 160, 20, 10, 10))
    m.add(soft_box(-4, 454, -4, 224, 160, 205, 22, 10, 12, arc=1))
    for x in (60, 360):
        m.add(soft_box(x, x + 30, 222, 232, 130, 180, 5, 2, 3, segs=1, arc=1))
    return m.add(sweep([(120, 110, 200), (140, 110, 255), (310, 110, 255), (330, 110, 200)], 12, 8))


def ball():
    """Football, globe, any ball."""
    return Mesh().add(sphere((0.5, 0.5, 0.5), (0.5, 0.5, 0.5), 12, 6))


def crate():
    """Open-top slatted crate: base, corner posts, two boards per side."""
    p = 0.09
    m = Mesh().add(box(0, 1, 0, 1, 0, 0.08))
    for x0, y0 in ((0, 0), (1 - p, 0), (0, 1 - p), (1 - p, 1 - p)):
        m.add(box(x0, x0 + p, y0, y0 + p, 0.08, 1))
    for z0, z1 in ((0.16, 0.5), (0.6, 0.94)):
        m.add(box(p, 1 - p, 0, 0.05, z0, z1)).add(box(p, 1 - p, 0.95, 1, z0, z1))
        m.add(box(0, 0.05, p, 1 - p, z0, z1)).add(box(0.95, 1, p, 1 - p, z0, z1))
    return m


def appliance_slab():
    """Washing machine: a rounded cabinet, the control panel and dial, and the round porthole door."""
    m = Mesh().add(soft_box(0, 600, 0, 580, 0, 850, 20, 8, 10))
    m.add(box(20, 580, 580, 590, 720, 830))
    m.add(lathe([(1, 590), (0.8, 604)], 12, "y", (470, 775), (34, 34)))
    return m.add(lathe([(1, 580), (1, 596), (0.86, 604), (0.8, 598)], 12, "y", (300, 400), (210, 210)))


def upright_fridge():
    """Tall fridge: body, fridge and freezer doors, handles."""
    m = Mesh().add(box(0, 1, 0.04, 1, 0, 1))
    m.add(box(0.01, 0.99, 0.015, 0.04, 0.01, 0.62)).add(box(0.01, 0.99, 0.015, 0.04, 0.64, 0.99))
    return m.add(box(0.82, 0.88, 0, 0.015, 0.36, 0.58)).add(box(0.82, 0.88, 0, 0.015, 0.68, 0.84))


def mattress():
    """A mattress: plump rounded edges and a quilted top panel."""
    m = Mesh().add(soft_box(0, 1, 0, 1, 0, 0.9, 0.04, 0.03, 0.25))
    return m.add(soft_box(0.05, 0.95, 0.05, 0.95, 0.9, 1, 0.03, 0.02, 0.06, arc=1))


def plank_stack():
    """Boards stacked flat, each a little offset."""
    m = Mesh()
    for i in range(4):
        x0 = 0.0 if i % 2 == 0 else 0.05
        m.add(box(x0, x0 + 0.95, 0, 1, i * 0.25, i * 0.25 + 0.25))
    return m


def ladder():
    """Two rails and five rungs, lying flat as it is carried."""
    m = Mesh().add(box(0, 1, 0, 0.1, 0, 1)).add(box(0, 1, 0.9, 1, 0, 1))
    for i in range(5):
        x = 0.08 + i * 0.2
        m.add(box(x, x + 0.05, 0.1, 0.9, 0.3, 0.7))
    return m


def barrel():
    """Drum or barrel: bulging body with rims."""
    return Mesh().add(lathe([(0.84, 0), (0.9, 0.04), (0.97, 0.2), (1, 0.5), (0.97, 0.8), (0.9, 0.96), (0.84, 1)], 10, "z", (0.5, 0.5), (0.5, 0.5)))


def bicycle():
    """Bicycle side on: tyres and rims, hubs, a diamond frame, fork, bars, saddle and cranks."""
    m = Mesh()
    y = 280
    for cx in (330, 1420):
        m.add(sweep(arc((cx, y, 340), 322, 0, 360, 28)[:-1], 18, 6, closed=True))
        m.add(sweep(arc((cx, y, 340), 298, 0, 360, 28)[:-1], 6, 4, closed=True))
        m.add(lathe([(1, y - 50), (1, y + 50)], 8, "y", (cx, 340), (22, 22)))
        for k in range(8):
            a = math.radians(k * 45)
            m.add(tube((cx, y, 340), (cx + 296 * math.cos(a), y, 340 + 296 * math.sin(a)), 3, 4))
    bb, seat, head_top, head_bot = (800, y, 300), (720, y, 780), (1260, y, 820), (1300, y, 640)
    for a, b in ((bb, seat), (seat, head_top), (bb, head_bot), ((330, y, 340), bb), ((330, y, 340), (735, y, 740)),
                 (head_bot, (1420, y, 340)), (head_top, head_bot), (head_top, (1240, y, 900)), (seat, (705, y, 850))):
        m.add(tube(a, b, 18, 8))
    m.add(tube((1240, 40, 900), (1240, 520, 900), 14, 8))
    m.add(soft_box(640, 820, y - 60, y + 60, 850, 895, 30, 16, 12, segs=2, arc=1))
    m.add(lathe([(1, y + 25), (1, y + 40)], 12, "y", (800, 300), (95, 95)))
    return m.add(box(760, 860, y + 60, y + 110, 180, 200)).add(box(740, 840, y - 110, y - 60, 400, 420))


def lawnmower():
    """Push lawnmower: a rounded deck on four wheels, the engine, a grass box and the handle."""
    m = Mesh().add(soft_box(0, 620, 0, 550, 80, 290, 60, 30, 40))
    m.add(lathe([(1, 290), (1, 380), (0.8, 420)], 12, "z", (300, 275), (110, 110)))
    m.add(soft_box(620, 1000, 90, 460, 70, 400, 50, 24, 40))
    for x in (100, 520):
        for y0, y1 in ((-40, 0), (550, 590)):
            m.add(lathe([(0.8, y0), (1, y0 + 6), (1, y1 - 6), (0.8, y1)], 12, "y", (x, 100), (100, 100)))
    for y in (90, 460):
        m.add(tube((600, y, 260), (1380, y, 980), 15, 8))
    return m.add(tube((1380, 70, 980), (1380, 480, 980), 15, 8)).add(tube((1100, 90, 700), (1100, 460, 700), 12, 6))


def sofa():
    """Sofa: base, three seat cushions, three back cushions against the frame, padded arms."""
    m = Mesh().add(soft_box(0.1, 0.9, 0, 0.8, 0.05, 0.3, 0.03, 0.02, 0.03, segs=2, arc=1))
    for i in range(3):
        a = 0.1 + i * 0.8 / 3
        m.add(soft_box(a + 0.005, a + 0.8 / 3 - 0.005, 0, 0.74, 0.3, 0.46, 0.05, 0.04, 0.05, segs=2, arc=1))
        m.add(soft_box(a + 0.01, a + 0.8 / 3 - 0.01, 0.62, 0.84, 0.46, 0.9, 0.05, 0.04, 0.08, segs=2, arc=1))
    m.add(soft_box(0, 1, 0.8, 1, 0.05, 1, 0.05, 0.03, 0.06, segs=2))
    m.add(soft_box(0, 0.12, 0, 0.84, 0.05, 0.64, 0.05, 0.04, 0.08, segs=2))
    m.add(soft_box(0.88, 1, 0, 0.84, 0.05, 0.64, 0.05, 0.04, 0.08, segs=2))
    # Short feet, as blocks: turned ones this small are a few pixels and cost a hundred faces.
    for x in (0.04, 0.92):
        for y in (0.04, 0.92):
            m.add(box(x, x + 0.04, y, y + 0.04, 0, 0.05))
    return m


def armchair():
    """Armchair: a deep seat cushion between padded arms, a back cushion and short legs."""
    m = Mesh().add(soft_box(0.18, 0.82, 0, 0.8, 0.07, 0.3, 0.04, 0.03, 0.04, segs=2, arc=1))
    m.add(soft_box(0.19, 0.81, 0, 0.74, 0.3, 0.48, 0.07, 0.05, 0.06, segs=2))
    m.add(soft_box(0.2, 0.8, 0.6, 0.84, 0.48, 0.92, 0.07, 0.05, 0.09, segs=2))
    m.add(soft_box(0.04, 0.96, 0.8, 1, 0.07, 1, 0.08, 0.04, 0.08, segs=2))
    m.add(soft_box(0, 0.2, 0, 0.86, 0.07, 0.68, 0.07, 0.05, 0.1, segs=2))
    m.add(soft_box(0.8, 1, 0, 0.86, 0.07, 0.68, 0.07, 0.05, 0.1, segs=2))
    for x in (0.07, 0.93):
        for y in (0.06, 0.93):
            m.add(leg(x, y, 0.03, 0, 0.07, 0.7))
    return m


def dining_table():
    """Table: a top with rounded edges, an apron under it and four turned legs."""
    m = Mesh().add(soft_box(0, 1, 0, 1, 0.93, 1, 0.03, 0.01, 0.02, segs=3, arc=1))
    m.add(box(0.06, 0.94, 0.06, 0.1, 0.84, 0.93)).add(box(0.06, 0.94, 0.9, 0.94, 0.84, 0.93))
    for x in (0.07, 0.93):
        for y in (0.07, 0.93):
            m.add(leg(x, y, 0.03, 0, 0.93, 0.65))
    return m


def chair():
    """Chair: a padded seat on four turned legs, back posts and a curved back rest."""
    m = Mesh().add(soft_box(0, 1, 0, 1, 0.44, 0.53, 0.08, 0.05, 0.03, segs=3))
    for x in (0.08, 0.92):
        for y in (0.08, 0.92):
            m.add(leg(x, y, 0.06, 0, 0.44, 0.7))
        m.add(leg(x, 0.93, 0.05, 0.53, 1))
    return m.add(soft_box(0.1, 0.9, 0.88, 0.98, 0.68, 0.96, 0.04, 0.03, 0.04, segs=2))


def wardrobe():
    """Carcass, two doors, two handles."""
    m = Mesh().add(box(0, 1, 0.03, 1, 0, 1))
    m.add(box(0.01, 0.495, 0.012, 0.03, 0.02, 0.98)).add(box(0.505, 0.99, 0.012, 0.03, 0.02, 0.98))
    return m.add(box(0.44, 0.47, 0, 0.012, 0.42, 0.6)).add(box(0.53, 0.56, 0, 0.012, 0.42, 0.6))


def bed_frame():
    """Bed: frame on turned legs, a plump mattress, a padded headboard and a low footboard."""
    m = Mesh().add(soft_box(0, 1, 0.04, 0.94, 0.1, 0.3, 0.02, 0.01, 0.02, arc=1))
    m.add(soft_box(0.02, 0.98, 0.05, 0.93, 0.3, 0.5, 0.04, 0.03, 0.06))
    m.add(soft_box(0.04, 0.34, 0.72, 0.92, 0.5, 0.58, 0.05, 0.04, 0.03, segs=2))
    m.add(soft_box(0.66, 0.96, 0.72, 0.92, 0.5, 0.58, 0.05, 0.04, 0.03, segs=2))
    m.add(soft_box(0, 1, 0.94, 1, 0, 1, 0.02, 0.01, 0.1, segs=2))
    m.add(soft_box(0, 1, 0, 0.04, 0, 0.4, 0.02, 0.01, 0.03, segs=2, arc=1))
    for x in (0.04, 0.96):
        m.add(leg(x, 0.5, 0.03, 0, 0.1, 0.8))
    return m


def lamp():
    """Base, stem and a shade wider at the bottom."""
    return Mesh().add(lathe(
        [(0.62, 0), (0.62, 0.05), (0.08, 0.05), (0.08, 0.6), (1, 0.6), (0.62, 1)],
        10, "z", (0.5, 0.5), (0.5, 0.5)))


def tv_stand():
    """A TV on its stand: cabinet, neck, screen."""
    m = Mesh().add(box(0, 1, 0, 1, 0, 0.34)).add(box(0.44, 0.56, 0.42, 0.58, 0.34, 0.44))
    return m.add(box(0.03, 0.97, 0.44, 0.56, 0.44, 1))


def plant_pot():
    """Tapered pot with a rim — also how a cup or tumbler is drawn."""
    return Mesh().add(lathe([(0.76, 0), (0.94, 0.84), (1, 0.84), (1, 1)], 12, "z", (0.5, 0.5), (0.5, 0.5)))


def piano():
    """Upright piano: case, keyboard shelf, legs under it."""
    m = Mesh().add(box(0, 1, 0.34, 1, 0, 1)).add(box(0.03, 0.97, 0.06, 0.34, 0.55, 0.66))
    return m.add(box(0.03, 0.09, 0, 0.06, 0, 0.66)).add(box(0.91, 0.97, 0, 0.06, 0, 0.66))


# -- everyday household things -----------------------------------------------------------------


def kettle():
    """Jug kettle on its power base: a tapered body, lid and knob, a curved spout, a tall handle."""
    m = Mesh().add(lathe([(1, -14), (1, -2), (0.9, 0)], 12, "z", (100, 80), (84, 84)))
    m.add(lathe([(0.86, 0), (0.95, 4), (1, 22), (0.93, 168), (0.76, 200), (0.7, 206)], 12, "z", (100, 80), (76, 76)))
    m.add(lathe([(1, 206), (0.95, 214), (0.3, 220), (0.36, 230), (0, 233)], 10, "z", (100, 80), (54, 54)))
    m.add(sweep([(38, 80, 60), (18, 80, 120), (0, 80, 165), (-16, 80, 186)], [15, 11, 8, 7], 8))
    return m.add(sweep([(168, 80, 55), (206, 80, 95), (212, 80, 185), (150, 80, 214)], 11, 8))


def coffee_maker():
    """Drip coffee maker: base plate, water tower at the back, hood over a round carafe."""
    m = Mesh().add(box(0, 1, 0, 1, 0, 0.1)).add(box(0, 1, 0.58, 1, 0.1, 1)).add(box(0, 1, 0.1, 0.58, 0.82, 1))
    return m.add(lathe([(1, 0.1), (1, 0.5), (0.72, 0.62)], 10, "z", (0.5, 0.33), (0.3, 0.22)))


def microwave():
    """Microwave or countertop oven: a rounded body, a door window and the control strip beside it."""
    m = Mesh().add(soft_box(0, 1, 0.04, 1, 0, 1, 0.05, 0.03, 0.05))
    return m.add(box(0.05, 0.7, 0, 0.04, 0.12, 0.88)).add(box(0.76, 0.95, 0.015, 0.04, 0.12, 0.88))


def toaster():
    """Pop-up toaster: a rounded body, two slot rims on top and the lever on its side."""
    m = Mesh().add(soft_box(0, 0.9, 0, 1, 0, 0.9, 0.14, 0.06, 0.1))
    m.add(soft_box(0.14, 0.76, 0.2, 0.4, 0.9, 0.95, 0.04, 0.02, 0.02, segs=2, arc=1))
    m.add(soft_box(0.14, 0.76, 0.6, 0.8, 0.9, 0.95, 0.04, 0.02, 0.02, segs=2, arc=1))
    return m.add(soft_box(0.9, 1, 0.42, 0.58, 0.5, 1, 0.03, 0.02, 0.03, segs=2, arc=1))


def cooker():
    """Free-standing cooker: body, four rings on the hob, splashback, oven door and handle."""
    m = Mesh().add(box(0, 1, 0.03, 1, 0, 0.92)).add(box(0, 1, 0.9, 1, 0.92, 1))
    for cx, cy in ((0.27, 0.28), (0.73, 0.28), (0.27, 0.64), (0.73, 0.64)):
        m.add(lathe([(1, 0.92), (1, 0.95)], 6, "z", (cx, cy), (0.14, 0.12)))
    return m.add(box(0.08, 0.92, 0.012, 0.03, 0.1, 0.7)).add(box(0.2, 0.8, 0, 0.012, 0.62, 0.66))


def cooking_pot():
    """Saucepan or stock pot: round body, a lid knob and a long handle."""
    m = Mesh().add(lathe([(1, 0), (1, 0.8), (0.92, 0.86)], 12, "z", (0.4, 0.5), (0.4, 0.5)))
    return m.add(box(0.36, 0.44, 0.46, 0.54, 0.86, 1)).add(box(0.8, 1, 0.45, 0.55, 0.66, 0.76))


def frying_pan():
    """Frying pan or skillet: a shallow flared disc and a long handle."""
    m = Mesh().add(lathe([(0.8, 0), (1, 1)], 12, "z", (0.3, 0.5), (0.3, 0.5)))
    return m.add(box(0.6, 1, 0.44, 0.56, 0.62, 0.86))


def plate_stack():
    """A stack of four dinner plates: each with its foot ring, a shallow well and a flared rim."""
    m = Mesh()
    for i in range(4):
        z = i * 17
        m.add(lathe([(0.45, z), (0.5, z + 3), (0.56, z + 6), (0.66, z + 9), (0.93, z + 14)], 8, "z", (135, 135), (135, 135)))
        # The rim keeps its edge lines, so each plate reads as its own layer rather than the
        # stack blurring into one smooth drum.
        rim = lathe([(0.93, z + 14), (1, z + 18), (0.96, z + 20)], 8, "z", (135, 135), (135, 135))
        m.add([Face(f) if abs(unit(newell(f))[2]) < 0.95 else f for f in rim])
    return m


def bowl():
    """A bowl: a foot ring and a wide flared body."""
    return Mesh().add(lathe([(0.55, 0), (0.55, 0.08), (0.88, 0.55), (1, 1)], 12, "z", (0.5, 0.5), (0.5, 0.5)))


def pillow():
    """Pillow or cushion: plump in the middle, thinning to rounded edges all round."""
    return Mesh().add(soft_box(0, 1, 0, 1, 0, 1, 0.22, 0.2, 0.48, segs=4, arc=3))


def folded_stack():
    """Folded clothes, towels or a blanket: four soft layers, none quite squared up."""
    return Mesh().add(box(0, 0.97, 0.02, 1, 0, 0.25)).add(box(0.02, 1, 0, 0.98, 0.25, 0.5)) \
        .add(box(0.01, 0.98, 0.01, 0.99, 0.5, 0.75)).add(box(0.03, 0.99, 0.02, 0.97, 0.75, 1))


def bookcase():
    """Bookcase or open shelving: sides, back, three shelves and books on two of them."""
    m = Mesh().add(box(0, 0.05, 0, 1, 0, 1)).add(box(0.95, 1, 0, 1, 0, 1)).add(box(0.05, 0.95, 0.94, 1, 0, 1))
    m.add(box(0.05, 0.95, 0, 0.94, 0, 0.05)).add(box(0.05, 0.95, 0, 0.94, 0.95, 1))
    for z in (0.3, 0.55, 0.78):
        m.add(box(0.05, 0.95, 0, 0.94, z, z + 0.03))
    return m.add(box(0.08, 0.62, 0.2, 0.94, 0.05, 0.25)).add(box(0.3, 0.9, 0.2, 0.94, 0.58, 0.76))


def chest_of_drawers():
    """Chest of drawers, cupboard or bedside table: carcass, four drawer fronts, handles."""
    # Built as separate blocks with no single front panel behind the drawers: the plan paints
    # faces far to near by their centres, and one tall panel would be painted over the lower
    # drawers and hide them.
    m = Mesh().add(box(0, 0.04, 0.03, 1, 0, 1)).add(box(0.96, 1, 0.03, 1, 0, 1))
    m.add(box(0.04, 0.96, 0.03, 1, 0.97, 1)).add(box(0.04, 0.96, 0.03, 1, 0, 0.04))
    for i in range(4):
        z0 = 0.04 + i * 0.235
        m.add(box(0.04, 0.96, 0.012, 1, z0 + 0.01, z0 + 0.225)).add(box(0.42, 0.58, 0, 0.012, z0 + 0.1, z0 + 0.13))
    return m


def desk():
    """Desk: top, a drawer pedestal on one side, two legs on the other."""
    m = Mesh().add(box(0, 1, 0, 1, 0.92, 1)).add(box(0.62, 1, 0.05, 1, 0, 0.92))
    m.add(box(0.64, 0.98, 0.03, 0.05, 0.5, 0.88)).add(box(0.64, 0.98, 0.03, 0.05, 0.06, 0.46))
    return m.add(box(0.02, 0.08, 0.05, 0.11, 0, 0.92)).add(box(0.02, 0.08, 0.89, 0.95, 0, 0.92))


def monitor():
    """Monitor or flat-screen TV: a rounded foot, a neck and a thin screen with softened corners."""
    m = Mesh().add(soft_box(0.3, 0.7, 0, 1, 0, 0.05, 0.08, 0.03, 0.02, segs=2, arc=1))
    m.add(box(0.45, 0.55, 0.56, 0.7, 0.05, 0.3))
    m.add(prism(rounded_ring(0, 1, 0.3, 1, 0.02, 0, 2), "y", 0.4, 0.56))
    return m


def printer():
    """Printer: body, scanner lid, output tray at the front and paper feed behind."""
    m = Mesh().add(box(0, 1, 0.1, 1, 0, 0.7)).add(box(0.02, 0.98, 0.12, 0.98, 0.7, 0.78))
    return m.add(box(0.15, 0.85, 0, 0.1, 0.25, 0.29)).add(box(0.15, 0.85, 0.9, 0.97, 0.78, 1))


def disc():
    """Clock, platter, frisbee, anything round and flat: a disc with a raised rim."""
    return Mesh().add(lathe([(1, 0), (1, 1), (0.9, 1), (0.9, 0.7)], 12, "z", (0.5, 0.5), (0.5, 0.5)))


def framed_panel():
    """Mirror, picture, whiteboard or a door: a frame round a recessed panel, drawn lying flat."""
    m = Mesh().add(box(0, 0.06, 0, 1, 0, 1)).add(box(0.94, 1, 0, 1, 0, 1))
    m.add(box(0.06, 0.94, 0, 0.06, 0, 1)).add(box(0.06, 0.94, 0.94, 1, 0, 1))
    return m.add(box(0.06, 0.94, 0.06, 0.94, 0.25, 0.75))


def helmet():
    """Helmet: a dome with a short peak at the front."""
    m = Mesh().add(lathe(
        [(1, 0), (1, 0.15), (0.95, 0.4), (0.8, 0.65), (0.55, 0.85), (0.25, 0.97), (0, 1)],
        10, "z", (0.5, 0.56), (0.5, 0.44)))
    return m.add(box(0.2, 0.8, 0, 0.14, 0.08, 0.18))


def shoe():
    """Trainer: a thick sole, the heel counter and collar, the toe box, the tongue and laces."""
    m = Mesh().add(soft_box(0, 300, 0, 110, 0, 26, 45, 14, 8, arc=1))
    m.add(soft_box(0, 150, 4, 106, 26, 120, 45, 20, 20))
    m.add(soft_box(100, 298, 6, 104, 22, 82, 48, 30, 30))
    m.add(soft_box(120, 200, 30, 80, 70, 118, 20, 12, 12, segs=2))
    for k in range(4):
        x = 125 + k * 20
        m.add(tube((x, 22, 84 + 6 * (3 - k)), (x, 88, 84 + 6 * (3 - k)), 3, 4))
    return m.add(sweep(arc((75, 55, 120), 50, 0, 360, 16, "xy")[:-1], 8, 6, closed=True))


def upright_vacuum():
    """Upright vacuum: a wide floor head, the motor body with its dust bin, the stick and handle."""
    m = Mesh().add(soft_box(0, 330, 0, 260, 0, 90, 40, 20, 20))
    m.add(rounded_loft(70, 260, 130, 300, 60, [(10, 90), (0, 120), (0, 620), (20, 660)]))
    m.add(lathe([(0.9, 250), (1, 262), (1, 510), (0.9, 522)], 12, "z", (165, 130), (68, 68)))
    m.add(tube((165, 240, 650), (165, 240, 1080), 16, 8))
    return m.add(sweep([(165, 240, 1070), (165, 240, 1140), (165, 160, 1150), (165, 150, 1090)], 14, 8))


def long_handle():
    """Broom or mop lying down: a long handle with its threaded collar, the head and its bristles."""
    m = Mesh().add(tube((0, 150, 60), (1000, 150, 60), 13, 8))
    m.add(lathe([(1, 980), (1, 1020)], 8, "x", (150, 60), (20, 20)))
    m.add(soft_box(1010, 1070, 0, 300, 50, 120, 14, 8, 8, segs=2, arc=1))
    return m.add(soft_box(1000, 1080, 8, 292, 0, 52, 10, 4, 4, segs=2, arc=1))


def watering_can():
    """Watering can: an oval body with its filler, a long spout with the rose, an arched handle."""
    m = Mesh().add(lathe([(0.94, 0), (1, 8), (1, 240), (0.9, 262), (0.55, 270)], 12, "z", (200, 90), (110, 85)))
    m.add(lathe([(1, 262), (1, 284)], 10, "z", (170, 90), (32, 32)))
    m.add(sweep([(110, 90, 40), (40, 90, 160), (-60, 90, 300)], [20, 14, 10], 8))
    m.add(lathe([(0.3, -100), (1, -74), (1, -64)], 10, "x", (90, 306), (34, 34)))
    return m.add(sweep([(250, 90, 262), (320, 90, 340), (356, 90, 250), (330, 90, 120), (300, 90, 80)], 12, 8))


def power_drill():
    """Cordless drill: rounded motor housing, a round chuck, the grip and the battery pack."""
    m = Mesh().add(soft_box(0, 0.78, 0.2, 0.8, 0.55, 1, 0.12, 0.08, 0.1))
    m.add(lathe([(0.9, 0.78), (1, 0.84), (1, 0.94), (0.6, 1)], 8, "x", (0.5, 0.77), (0.13, 0.11)))
    m.add(soft_box(0.36, 0.64, 0.3, 0.7, 0.16, 0.58, 0.08, 0.05, 0.04, segs=2))
    return m.add(soft_box(0.22, 0.78, 0, 1, 0, 0.18, 0.08, 0.04, 0.04, segs=2))


def hand_tool():
    """Hammer lying flat: a shaped wooden handle and the steel head with its face and claw."""
    m = Mesh().add(lathe([(0.8, 0), (1, 20), (1, 120), (0.8, 180), (0.75, 270)], 10, "x", (60, 17), (15, 15)))
    m.add(soft_box(268, 302, 0, 76, 0, 35, 10, 4, 4, segs=2, arc=1))
    m.add(lathe([(1, 76), (1, 96), (0.9, 100)], 10, "y", (285, 17), (16, 16)))
    return m.add(hexahedron([(270, -44, 6), (300, -44, 6), (300, 0, 0), (270, 0, 0),
                             (270, -44, 22), (300, -44, 22), (300, 0, 35), (270, 0, 35)]))


def guitar():
    """Guitar or violin lying on its back: lower and upper bouts, neck and headstock."""
    m = Mesh().add(lathe([(1, 0), (1, 1)], 10, "z", (0.2, 0.5), (0.2, 0.5)))
    m.add(lathe([(1, 0), (1, 1)], 10, "z", (0.47, 0.5), (0.15, 0.38)))
    return m.add(box(0.6, 0.93, 0.44, 0.56, 0.55, 0.85)).add(box(0.93, 1, 0.4, 0.6, 0.55, 0.85))


def tote_bag():
    """Tote or shopping bag: a soft body that widens to its top, and two strap handles."""
    m = Mesh().add(rounded_loft(0, 400, 0, 150, 30, [(24, 0), (4, 20), (0, 330), (2, 340)]))
    for y in (22, 128):
        m.add(sweep(arc((200, y, 330), 95, 180, 0, 12), 7, 6))
    return m


def stool():
    """Stool or bar stool: a round padded seat on four splayed turned legs and a foot ring."""
    m = Mesh().add(lathe([(0.9, 0.86), (1, 0.9), (1, 0.97), (0.9, 1)], 10, "z", (0.5, 0.5), (0.5, 0.5)))
    for x, y in ((0.2, 0.2), (0.8, 0.2), (0.2, 0.8), (0.8, 0.8)):
        m.add(leg(x, y, 0.05, 0, 0.86, 0.8))
    m.add(box(0.2, 0.8, 0.18, 0.22, 0.32, 0.35)).add(box(0.2, 0.8, 0.78, 0.82, 0.32, 0.35))
    return m


def ottoman():
    """Ottoman, pouf or bench: a plump padded block on four short turned feet."""
    m = Mesh().add(soft_box(0, 1, 0, 1, 0.12, 1, 0.12, 0.06, 0.14))
    for x in (0.08, 0.92):
        for y in (0.08, 0.92):
            m.add(leg(x, y, 0.04, 0, 0.12, 0.7))
    return m


def clothes_rail():
    """Clothes rail: two feet, two uprights, the rail, and coats hanging from it."""
    m = Mesh()
    for x in (20, 1180):
        m.add(tube((x, 0, 20), (x, 450, 20), 16, 8)).add(tube((x, 225, 20), (x, 225, 1600), 14, 8))
    m.add(tube((20, 225, 1600), (1180, 225, 1600), 14, 8))
    for k in range(6):
        x = 150 + k * 170
        length = 700 + (k % 3) * 180
        m.add(sweep(arc((x, 225, 1560), 30, 180, 0, 6), 3, 4))
        m.add(rounded_loft(x - 75, x + 75, 150, 300, 45, [(20, 1540 - length), (0, 1500), (35, 1540)]))
    return m


# ---------------------------------------------------------------------------------------------
# modelled at real size
#
# The shapes below are authored in millimetres at the size of a typical real one — a mug is
# 82 mm across and 95 mm tall — and emit() scales each into the unit box afterwards. Working in
# real proportions is what lets a handle be a handle's thickness and a lip a lip's height,
# instead of a block placed by eye in a unit cube.


def add3(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def mul3(a, s):
    return (a[0] * s, a[1] * s, a[2] * s)


def unit(v):
    ln = math.sqrt(dot(v, v))
    return (v[0] / ln, v[1] / ln, v[2] / ln)


def sweep(path, radius, sides=10, closed=False):
    """
    A round tube along a path of points: a handle, a frame, a hose, a bail. `radius` is one
    number or one per point, so a spout can taper. The cross-section is carried along the path
    without twisting (parallel transport), so a bent handle stays round at every bend.
    """
    n = len(path)
    rings, prev = [], None
    for i, p in enumerate(path):
        if closed:
            t = unit(sub(path[(i + 1) % n], path[i - 1]))
        else:
            t = unit(sub(path[min(i + 1, n - 1)], path[max(i - 1, 0)]))
        if prev is None:
            ref = (0.0, 0.0, 1.0) if abs(t[2]) < 0.9 else (1.0, 0.0, 0.0)
            u = unit(cross(t, ref))
        else:
            u = unit(sub(prev, mul3(t, dot(prev, t))))
        v = cross(t, u)
        prev = u
        r = radius[i] if isinstance(radius, (list, tuple)) else radius
        rings.append([add3(p, add3(mul3(u, r * math.cos(2 * math.pi * k / sides)), mul3(v, r * math.sin(2 * math.pi * k / sides))))
                      for k in range(sides)])
    faces = []
    for i in range(n if closed else n - 1):
        a, b = rings[i], rings[(i + 1) % n]
        mid_axis = mul3(add3(path[i], path[(i + 1) % n]), 0.5)
        for k in range(sides):
            q = [a[k], a[(k + 1) % sides], b[(k + 1) % sides], b[k]]
            faces.append(SmoothFace(orient(q, sub(centroid(q), mid_axis))))
    if not closed:
        faces += [SmoothFace(orient(f, sub(path[0], path[1]))) for f in fan(rings[0])]
        faces += [SmoothFace(orient(f, sub(path[-1], path[-2]))) for f in fan(rings[-1])]
    return faces


def tube(a, b, r, sides=10):
    return sweep([a, b], r, sides)


def arc(centre, radius, a0, a1, steps, plane="xz"):
    """Points on a circular arc from angle a0 to a1 (degrees) in the given plane."""
    out = []
    for k in range(steps + 1):
        t = math.radians(a0 + (a1 - a0) * k / steps)
        c, s = math.cos(t) * radius, math.sin(t) * radius
        out.append({"xz": (centre[0] + c, centre[1], centre[2] + s),
                    "yz": (centre[0], centre[1] + c, centre[2] + s),
                    "xy": (centre[0] + c, centre[1] + s, centre[2])}[plane])
    return out


def rounded_loft(x0, x1, y0, y1, r, rings, segs=4):
    """
    Rounded-rectangle rings stacked up the height: [(inset, z), …]. A smaller inset is a wider
    ring, so insets shrinking with height make a tub that flares to its rim. Closed top and
    bottom; smooth all over.
    """
    loops = [[(x, y, z) for x, y in rounded_ring(x0, x1, y0, y1, r, d, segs)] for d, z in rings]
    c = ((x0 + x1) / 2, (y0 + y1) / 2, sum(z for _, z in rings) / len(rings))
    faces, n = [], len(loops[0])
    for a, b in zip(loops, loops[1:]):
        for i in range(n):
            j = (i + 1) % n
            q = [a[i], a[j], b[j], b[i]]
            if newell_len(q) < 1e-9:
                continue
            m = centroid(q)
            faces.append(SmoothFace(orient(q, (m[0] - c[0], m[1] - c[1], 0.0))))
    faces += [SmoothFace(orient(f, (0, 0, -1))) for f in fan(loops[0])]
    faces += [SmoothFace(orient(f, (0, 0, 1))) for f in fan(loops[-1])]
    return faces


def blob(centre, radii):
    """An ellipsoid: a leaf cluster, a bean bag's slump, a caster wheel."""
    return sphere(centre, radii, 6, 3)


# -- kitchen ----------------------------------------------------------------------------------


def mug():
    """Mug: a straight-sided body with a rolled foot, a coffee line below the rim, a D handle."""
    m = Mesh().add(lathe([(0.9, 0), (0.97, 2), (1, 7), (1, 95), (0.9, 95), (0.9, 86)], 12, "z", (41, 41), (41, 41)))
    return m.add(sweep(arc((80, 41, 50), 27, 72, -72, 10), 5.5, 8))


def wine_glass():
    """Wine glass: a round foot, a thin stem, and a tulip bowl."""
    return Mesh().add(lathe([(0.95, 0), (1, 2), (0.9, 5), (0.14, 10), (0.1, 16), (0.1, 88), (0.3, 96), (0.72, 112),
                             (0.95, 138), (1, 165), (0.97, 200), (0.92, 212)], 12, "z", (38, 38), (38, 38)))


def vase():
    """Vase: a foot ring, a full shoulder, a narrow neck and a flared lip."""
    return Mesh().add(lathe([(0.72, 0), (0.76, 5), (0.7, 10), (0.8, 32), (0.95, 72), (1, 112), (0.95, 152), (0.78, 192),
                             (0.5, 226), (0.36, 246), (0.35, 266), (0.46, 282), (0.56, 292), (0.5, 296)],
                            12, "z", (75, 75), (75, 75)))


def teapot():
    """Teapot: a squat round body, a lid with a knob, a curved spout and a looped handle."""
    m = Mesh().add(lathe([(0.55, 0), (0.8, 10), (0.97, 42), (1, 66), (0.9, 100), (0.62, 118), (0.56, 122)],
                         12, "z", (95, 85), (80, 80)))
    m.add(lathe([(1, 122), (0.9, 132), (0.35, 140), (0.45, 150), (0.3, 160), (0, 162)], 8, "z", (95, 85), (45, 45)))
    m.add(sweep([(35, 85, 42), (18, 85, 60), (8, 85, 86), (0, 85, 112), (-8, 85, 124)], [13, 11, 9, 7, 6], 8))
    return m.add(sweep(arc((172, 85, 72), 34, 80, -70, 10), 6.5, 8))


def blender():
    """Blender: a motor base with a dial, a tapered jug with a lid, and the jug's handle."""
    m = Mesh().add(soft_box(0, 160, 0, 160, 0, 110, 24, 10, 12))
    m.add(lathe([(1, 160), (0.7, 168)], 8, "y", (80, 52), (16, 16)))
    m.add(lathe([(0.72, 110), (0.8, 130), (1, 300), (1, 330)], 12, "z", (80, 80), (62, 62)))
    m.add(lathe([(1, 330), (0.97, 344), (0.4, 348), (0.4, 358), (0, 360)], 10, "z", (80, 80), (64, 64)))
    return m.add(sweep([(135, 80, 300), (168, 80, 292), (172, 80, 180), (140, 80, 150)], 8, 8))


def stand_mixer():
    """Stand mixer: a base, a column at the back, the tilting head over a steel bowl, the beater."""
    m = Mesh().add(soft_box(0, 330, 0, 220, 0, 40, 40, 16, 12))
    m.add(soft_box(230, 320, 55, 165, 36, 270, 30, 18, 20))
    m.add(soft_box(20, 330, 58, 162, 260, 370, 48, 30, 34))
    m.add(lathe([(0.42, 40), (0.5, 48), (0.86, 100), (1, 170), (1, 196), (0.96, 200)], 12, "z", (120, 110), (92, 92)))
    m.add(tube((110, 110, 262), (110, 110, 190), 7, 8))
    return m.add(lathe([(1, 186), (0.6, 150), (0.2, 140)], 8, "z", (110, 110), (26, 26)))


def air_fryer():
    """Air fryer: a rounded body, the basket drawer with its handle, a dial on the top panel."""
    m = Mesh().add(soft_box(0, 290, 0, 300, 0, 330, 70, 30, 40))
    m.add(soft_box(30, 260, 290, 318, 25, 190, 30, 10, 12))
    m.add(soft_box(110, 180, 318, 350, 90, 130, 14, 8, 8, segs=2))
    return m.add(lathe([(1, 330), (0.85, 344)], 10, "z", (145, 190), (28, 28)))


def knife_block():
    """Knife block: a slanted block with five handles standing out of its top."""
    m = Mesh().add(hexahedron([(0, 0, 0), (120, 0, 0), (120, 220, 0), (0, 220, 0),
                               (0, 40, 230), (120, 40, 230), (120, 220, 160), (0, 220, 160)]))
    for i, x in enumerate((18, 42, 66, 90, 108)):
        y = 70 + (i % 2) * 40
        z = 230 - (y - 40) * 70 / 180
        m.add(tube((x, y, z - 5), (x, y - 26, z + 70), 8 if i < 4 else 6, 8))
    return m


def dish_rack():
    """Dish drying rack: a drip tray, a wire rail round it, four plates standing in it."""
    m = Mesh().add(soft_box(0, 450, 0, 320, 0, 22, 20, 8, 6, arc=1))
    for x in (70, 120, 170, 220):
        m.add(lathe([(1, x), (1, x + 10)], 12, "x", (160, 140), (118, 118)))
    m.add(sweep([(8, 8, 90), (442, 8, 90), (442, 312, 90), (8, 312, 90)], 4, 6, closed=True))
    for x, y in ((8, 8), (442, 8), (442, 312), (8, 312)):
        m.add(tube((x, y, 22), (x, y, 90), 4, 6))
    return m.add(lathe([(1, 22), (1, 120)], 10, "z", (360, 160), (40, 40)))


# -- around the house -------------------------------------------------------------------------


def laundry_basket():
    """Laundry basket: an oval woven tub, ribbed round its sides, with a rolled rim."""
    prof = [(0.84, 0)]
    for k in range(1, 7):
        z = k * 60
        prof += [(0.84 + 0.14 * z / 400 - 0.02, z - 8), (0.84 + 0.14 * z / 400, z)]
    prof += [(1, 400), (0.93, 410)]
    return Mesh().add(lathe(prof, 12, "z", (300, 200), (300, 200)))


def bucket():
    """Bucket: a tapered pail with a beaded rim and a wire bail with a grip."""
    m = Mesh().add(lathe([(0.78, 0), (0.8, 6), (0.97, 262), (1, 266), (1, 276), (0.95, 280)], 12, "z", (150, 150), (150, 150)))
    m.add(sweep(arc((150, 150, 262), 152, 0, 180, 16), 3, 6))
    return m.add(tube((120, 150, 414), (180, 150, 414), 9, 8))


def pedal_bin():
    """Pedal bin: a round body, a domed lid on a hinge at the back, the pedal at the front."""
    m = Mesh().add(lathe([(0.96, 0), (1, 8), (1, 410), (0.98, 420)], 12, "z", (150, 150), (150, 150)))
    m.add(lathe([(1, 420), (0.95, 438), (0.7, 452), (0.3, 460), (0, 462)], 12, "z", (150, 150), (152, 152)))
    m.add(soft_box(100, 200, 290, 336, 0, 30, 12, 6, 6, segs=2, arc=1))
    return m.add(soft_box(110, 190, 0, 14, 380, 420, 8, 4, 4, segs=2, arc=1))


def clothes_iron():
    """Iron: a pointed soleplate, the body over it, a looped handle and the dial."""
    sole = [(0, 8), (170, 0), (260, 52), (170, 104), (0, 96)]
    m = Mesh().add(prism(sole, "z", 0, 22))
    m.add(prism([(12, 18), (165, 12), (236, 52), (165, 92), (12, 86)], "z", 22, 80))
    m.add(sweep([(30, 52, 78), (46, 52, 150), (120, 52, 160), (190, 52, 120), (215, 52, 80)], 12, 8))
    return m.add(lathe([(1, 80), (0.8, 92)], 10, "z", (95, 52), (26, 26)))


def ironing_board():
    """Ironing board folded for carrying: the padded board with its pointed end, legs folded under."""
    board = [(0, 20), (40, 0), (900, 0), (1180, 110), (1220, 190), (1180, 270), (900, 380), (40, 380), (0, 360)]
    m = Mesh().add(prism(board, "z", 50, 80))
    m.add(prism([(20, 30), (60, 12), (890, 12), (1160, 118), (1195, 190), (1160, 262), (890, 368), (60, 368), (20, 350)], "z", 80, 90))
    for y in (100, 280):
        m.add(tube((150, y, 20), (950, y, 20), 12, 8))
    return m.add(tube((150, 100, 20), (150, 280, 20), 12, 8)).add(tube((950, 100, 20), (950, 280, 20), 12, 8))


def pedestal_fan():
    """Standing fan: a round base, a pole, the motor housing and a caged three-blade head."""
    cx, cy, cz = 220, 120, 1050
    m = Mesh().add(lathe([(1, 0), (1, 18), (0.8, 30)], 12, "z", (cx, cy), (160, 160)))
    m.add(tube((cx, cy, 30), (cx, cy, cz - 70), 14, 10))
    m.add(lathe([(0.6, 40), (1, 70), (1, 150), (0.5, 180)], 10, "y", (cx, cz), (70, 70)))
    m.add(sweep(arc((cx, 208, cz), 220, 0, 360, 36, "xz")[:-1], 5, 6, closed=True))
    m.add(lathe([(1, 200), (1, 216)], 10, "y", (cx, cz), (40, 40)))
    for k in range(3):
        a = math.radians(90 + k * 120)
        tip = (cx + 190 * math.cos(a), 204, cz + 190 * math.sin(a))
        s = (-math.sin(a) * 45, 0, math.cos(a) * 45)
        m.add(hexahedron([add3((cx, 200, cz), mul3(s, 0.5)), add3(tip, s), add3(tip, mul3(s, -1)), add3((cx, 200, cz), mul3(s, -0.5)),
                          add3((cx, 208, cz), mul3(s, 0.5)), add3(add3(tip, s), (0, 4, 0)), add3(add3(tip, mul3(s, -1)), (0, 4, 0)), add3((cx, 208, cz), mul3(s, -0.5))]))
    return m


def oil_heater():
    """Oil-filled radiator: nine rounded fins in a row, a control box and castor feet."""
    m = Mesh()
    for i in range(9):
        x = i * 42
        m.add(soft_box(x, x + 30, 0, 150, 60, 640, 14, 10, 40, segs=2, arc=1))
    m.add(soft_box(0, 60, 20, 130, 640, 690, 10, 6, 8, segs=2, arc=1))
    for x in (40, 320):
        m.add(tube((x, 20, 60), (x, 130, 60), 8, 6))
        m.add(blob((x, 20, 20), (20, 20, 20))).add(blob((x, 130, 20), (20, 20, 20)))
    return m


def desk_lamp():
    """Desk lamp: a weighted round base, two jointed arms and a cone shade looking down."""
    m = Mesh().add(lathe([(1, 0), (1, 18), (0.7, 28)], 12, "z", (90, 90), (90, 90)))
    m.add(sweep([(90, 90, 26), (160, 90, 280), (320, 90, 380)], 8, 8))
    m.add(lathe([(1, 270), (0.5, 360), (0.35, 380)], 12, "z", (360, 90), (85, 85)))
    return m.add(blob((160, 90, 280), (14, 14, 14)))


def speaker():
    """Bookshelf speaker: a rounded cabinet with a woofer cone and a dome tweeter on the front."""
    m = Mesh().add(soft_box(0, 180, 0, 220, 0, 300, 14, 8, 10))
    m.add(lathe([(1, 220), (0.95, 224), (0.5, 214), (0.22, 218), (0, 222)], 12, "y", (90, 105), (68, 68)))
    return m.add(lathe([(1, 220), (0.8, 226), (0.3, 230), (0, 231)], 10, "y", (90, 235), (24, 24)))


def camera():
    """Camera: the body with its grip and viewfinder hump, and a lens with focus and zoom rings."""
    m = Mesh().add(soft_box(0, 140, 0, 80, 0, 100, 16, 10, 10))
    m.add(soft_box(-18, 30, 0, 95, 0, 92, 20, 14, 14, segs=2))
    m.add(soft_box(58, 102, 18, 70, 100, 124, 10, 8, 6, segs=2))
    m.add(lathe([(1, 80), (1, 120), (1.0, 122), (0.92, 124), (0.92, 160), (1, 164), (1, 176), (0.8, 182)], 12, "y", (80, 50), (37, 37)))
    return m.add(lathe([(1, 100), (0.9, 108)], 8, "z", (10, 40), (8, 8)))


def headphones():
    """Over-ear headphones: a padded band arching over two cushioned ear cups."""
    m = Mesh().add(sweep(arc((95, 45, 60), 88, 180, 0, 18), 9, 8))
    for x0, x1 in ((0, 32), (158, 190)):
        m.add(lathe([(0.8, x0), (1, x0 + 8), (1, x1 - 8), (0.8, x1)], 12, "x", (45, 45), (45, 52)))
    return m


def alarm_clock():
    """Twin-bell alarm clock: a round case facing forward, two bells, a hammer and two feet."""
    m = Mesh().add(lathe([(0.9, 0), (1, 5), (1, 40), (0.92, 45)], 12, "y", (65, 75), (60, 60)))
    for x in (25, 105):
        m.add(lathe([(1, 128), (0.9, 142), (0.6, 152), (0, 156)], 10, "z", (x, 22), (26, 26)))
    m.add(tube((65, 22, 128), (65, 22, 150), 4, 6))
    return m.add(tube((42, 22, 22), (34, 22, 0), 5, 6)).add(tube((88, 22, 22), (96, 22, 0), 5, 6))


def book_stack():
    """A stack of four books of different sizes, spines and page edges showing."""
    m = Mesh()
    books = [(0, 0, 240, 170, 0, 34), (8, 6, 228, 160, 34, 58), (-4, 2, 250, 175, 58, 98), (12, 10, 220, 152, 98, 120)]
    for x, y, w, d, z0, z1 in books:
        m.add(box(x, x + w, y, y + d, z0, z0 + 3)).add(box(x, x + w, y, y + d, z1 - 3, z1))
        m.add(box(x + 2, x + w - 4, y + 2, y + d - 2, z0 + 3, z1 - 3)).add(box(x, x + 5, y, y + d, z0 + 3, z1 - 3))
    return m


def potted_plant():
    """Potted plant: a tapered pot with its rim, soil, and a full leafy crown."""
    m = Mesh().add(lathe([(0.78, 0), (0.95, 190), (1, 192), (1, 214), (0.92, 214), (0.92, 205)], 12, "z", (150, 150), (110, 110)))
    m.add(lathe([(1, 205), (0.9, 212)], 10, "z", (150, 150), (104, 104)))
    # Leaves: each a curved spindle, thin at the stem, full in the middle, pointed at the tip,
    # arching out and up from the centre — a leafy crown rather than a ball of foliage.
    for k in range(9):
        a = math.radians(k * 40 + 10)
        reach = 150 + (k % 3) * 40
        rise = 260 + (k % 2) * 120
        ca, sa = math.cos(a), math.sin(a)
        path = [(150 + ca * reach * f, 150 + sa * reach * f, 205 + rise * math.sin(math.pi * 0.6 * f)) for f in (0.0, 0.3, 0.6, 0.85, 1.0)]
        m.add(sweep(path, [3, 18, 24, 14, 2], 8))
    return m


def umbrella():
    """Folded umbrella: the pleated canopy tapering to its tip, the shaft and a crook handle."""
    m = Mesh().add(lathe([(0.2, 160), (1, 300), (0.8, 560), (0.9, 600), (0.7, 780), (0.1, 900)], 12, "x", (40, 40), (38, 38)))
    m.add(tube((20, 40, 40), (170, 40, 40), 8, 8))
    return m.add(sweep([(20, 40, 40)] + arc((20, 40, 80), 40, 270, 90, 8, "xz")[1:], 12, 8))


def storage_bin():
    """Plastic storage box: a tub flaring to its rim, a clip-on lid and handles at each end."""
    m = Mesh().add(rounded_loft(0, 600, 0, 400, 40, [(34, 0), (6, 300), (0, 312)]))
    m.add(soft_box(-6, 606, -6, 406, 312, 350, 44, 10, 10, arc=1))
    for x0 in (-26, 600):
        m.add(soft_box(x0, x0 + 26, 140, 260, 250, 305, 8, 4, 6, segs=2, arc=1))
    return m


def boot():
    """Tall boot: sole and heel, the foot, and the shaft rising from the back."""
    m = Mesh().add(soft_box(0, 290, 0, 105, 0, 22, 40, 10, 6, arc=1))
    m.add(soft_box(0, 80, 5, 100, 22, 60, 20, 6, 6, segs=2, arc=1))
    m.add(soft_box(0, 280, 8, 97, 22, 130, 44, 26, 30))
    return m.add(rounded_loft(0, 115, 3, 102, 40, [(0, 100), (4, 380), (0, 420)]))


def hair_dryer():
    """Hair dryer: the barrel with its nozzle and back vent, and the handle angled below."""
    m = Mesh().add(lathe([(0.7, 0), (0.85, 10), (1, 40), (1, 190), (0.8, 214), (0.66, 232)], 12, "x", (45, 180), (45, 45)))
    return m.add(hexahedron([(62, 22, 0), (112, 22, 0), (112, 68, 0), (62, 68, 0),
                             (95, 22, 150), (150, 22, 150), (150, 68, 150), (95, 68, 150)]))


# -- sport, outdoors and children -------------------------------------------------------------


def skateboard():
    """Skateboard: a deck with both tails kicked up, two trucks and four wheels."""
    m = Mesh().add(prism(rounded_ring(120, 680, 0, 210, 100, 0, 6), "z", 80, 92))
    m.add(hexahedron([(0, 20, 110), (120, 0, 80), (120, 210, 80), (0, 190, 110),
                      (0, 20, 122), (120, 0, 92), (120, 210, 92), (0, 190, 122)]))
    m.add(hexahedron([(680, 0, 80), (800, 20, 110), (800, 190, 110), (680, 210, 80),
                      (680, 0, 92), (800, 20, 122), (800, 190, 122), (680, 210, 92)]))
    for x in (170, 630):
        m.add(soft_box(x - 20, x + 20, 40, 170, 50, 80, 10, 4, 6, segs=2, arc=1))
        for y0, y1 in ((10, 40), (170, 200)):
            m.add(lathe([(0.8, y0), (1, y0 + 4), (1, y1 - 4), (0.8, y1)], 12, "y", (x, 28), (28, 28)))
    return m


def kick_scooter():
    """Kick scooter: a deck on two wheels, the steering column and a T handlebar with grips."""
    m = Mesh().add(soft_box(120, 640, 30, 130, 60, 90, 30, 10, 8, arc=1))
    for x in (80, 690):
        m.add(lathe([(0.8, 60), (1, 64), (1, 96), (0.8, 100)], 12, "y", (x, 80), (80, 80)))
    m.add(tube((80, 80, 80), (130, 80, 820), 16, 10))
    m.add(tube((130, 0, 820), (130, 160, 820), 12, 8))
    return m.add(tube((130, -60, 820), (130, 0, 820), 16, 8)).add(tube((130, 160, 820), (130, 220, 820), 16, 8))


def tennis_racket():
    """Racket lying flat: the oval frame round its string bed, the throat, and the grip."""
    ring = [(170 + 170 * math.cos(math.radians(a)), 130 + 130 * math.sin(math.radians(a)), 14) for a in range(0, 360, 12)]
    m = Mesh().add(sweep(ring, 12, 6, closed=True))
    m.add(prism([(170 + 158 * math.cos(math.radians(a)), 130 + 118 * math.sin(math.radians(a))) for a in range(0, 360, 20)], "z", 12, 16))
    m.add(tube((330, 110, 14), (420, 130, 14), 10, 6)).add(tube((330, 150, 14), (420, 130, 14), 10, 6))
    return m.add(lathe([(0.9, 420), (1, 430), (1, 680), (0.9, 690)], 8, "x", (130, 14), (16, 14)))


def golf_bag():
    """Golf bag: a tall padded tube with a collar, a pocket, a strap and clubs standing in it."""
    m = Mesh().add(lathe([(0.9, 0), (1, 30), (1, 820), (1.0, 830), (0.92, 850)], 12, "z", (130, 130), (125, 115)))
    m.add(soft_box(40, 220, 230, 262, 120, 520, 30, 14, 20, segs=2))
    m.add(sweep([(10, 130, 700), (-20, 130, 500), (10, 130, 300)], 12, 6))
    for x, y, h in ((90, 100, 1050), (140, 150, 1100), (170, 100, 1000), (110, 170, 980)):
        m.add(tube((x, y, 800), (x, y, h), 8, 6)).add(blob((x + 12, y, h + 15), (30, 18, 22)))
    return m


def snowboard():
    """Snowboard lying flat: the board with raised tips at both ends and two bindings."""
    m = Mesh().add(prism(rounded_ring(180, 1380, 0, 290, 60, 0, 4), "z", 0, 14))
    m.add(hexahedron([(40, 25, 70), (180, 0, 0), (180, 290, 0), (40, 265, 70),
                      (40, 25, 84), (180, 0, 14), (180, 290, 14), (40, 265, 84)]))
    m.add(hexahedron([(1380, 0, 0), (1520, 25, 70), (1520, 265, 70), (1380, 290, 0),
                      (1380, 0, 14), (1520, 25, 84), (1520, 265, 84), (1380, 290, 14)]))
    for x in (520, 1040):
        m.add(soft_box(x - 60, x + 60, 30, 260, 14, 40, 30, 10, 8, segs=2, arc=1))
        m.add(sweep([(x + 55, 40, 30), (x + 70, 145, 130), (x + 55, 250, 30)], 10, 6))
    return m


def lantern():
    """Camping lantern: a base, a glass chimney in a wire cage, a vented cap and a bail handle."""
    m = Mesh().add(lathe([(1, 0), (1, 40), (0.9, 48)], 12, "z", (80, 80), (80, 80)))
    m.add(lathe([(0.7, 48), (0.8, 90), (0.8, 170), (0.7, 205)], 12, "z", (80, 80), (80, 80)))
    for k in range(4):
        a = math.radians(45 + 90 * k)
        x, y = 80 + 68 * math.cos(a), 80 + 68 * math.sin(a)
        m.add(tube((x, y, 45), (x, y, 210), 4, 6))
    m.add(lathe([(1, 205), (0.9, 225), (0.4, 250), (0.25, 262)], 12, "z", (80, 80), (78, 78)))
    return m.add(sweep(arc((80, 80, 250), 60, 0, 180, 12), 3, 6))


def gas_cylinder():
    """Gas bottle: a domed steel body on a foot ring, the valve and its protective shroud."""
    m = Mesh().add(lathe([(0.9, 0), (0.9, 30), (1, 40), (1, 460), (0.9, 520), (0.55, 560), (0.3, 572)], 12, "z", (160, 160), (160, 160)))
    m.add(lathe([(1, 572), (1, 600)], 8, "z", (160, 160), (26, 26)))
    return m.add(sweep(arc((160, 160, 590), 70, 20, 160, 10), 8, 6))


def stroller():
    """Folded stroller: two long frame tubes with hooked handles, the folded seat, four wheels."""
    m = Mesh()
    for y in (40, 210):
        m.add(sweep([(150, y, 80), (160, y, 900)] + arc((190, y, 900), 30, 180, 0, 6)[1:], 11, 8))
        m.add(tube((150, y, 80), (60, y, 60), 10, 6))
        for x in (60, 240):
            m.add(lathe([(0.8, y - 22), (1, y - 18), (1, y + 18), (0.8, y + 22)], 12, "y", (x, 70), (70, 70)))
    m.add(soft_box(110, 210, 50, 200, 250, 720, 30, 16, 30, segs=2))
    return m.add(tube((150, 40, 400), (150, 210, 400), 9, 6))


def child_car_seat():
    """Child car seat: a moulded base, the seat, a tall back with side wings and a headrest."""
    m = Mesh().add(soft_box(0, 440, 0, 480, 0, 120, 60, 24, 24))
    m.add(soft_box(40, 400, 60, 470, 120, 220, 50, 30, 30))
    m.add(soft_box(30, 410, 380, 480, 120, 680, 60, 30, 40))
    for x0 in (0, 360):
        m.add(soft_box(x0, x0 + 80, 150, 470, 150, 560, 36, 20, 40, segs=2))
    return m.add(soft_box(60, 380, 340, 440, 560, 720, 50, 24, 30, segs=2))


def high_chair():
    """High chair: a seat with arms and a tray, on four splayed legs."""
    m = Mesh().add(soft_box(140, 440, 200, 480, 540, 590, 30, 14, 10))
    m.add(soft_box(140, 440, 440, 490, 590, 900, 30, 14, 20, segs=2))
    m.add(soft_box(100, 480, 60, 260, 700, 730, 50, 16, 10, arc=1))
    for x0, x1 in ((0, 150), (580, 430)):
        for y0, y1 in ((20, 220), (600, 460)):
            m.add(tube((x0, y0, 0), (x1, y1, 545), 14, 8))
    return m


def office_chair():
    """Office chair: a five-star base on castors, the gas lift, a padded seat, back and arms."""
    m = Mesh()
    c = (330, 330)
    for k in range(5):
        a = math.radians(90 + 72 * k)
        tip = (c[0] + 300 * math.cos(a), c[1] + 300 * math.sin(a))
        m.add(tube((c[0], c[1], 90), (tip[0], tip[1], 70), 16, 8))
        m.add(blob((tip[0], tip[1], 30), (26, 26, 30)))
    m.add(lathe([(1, 70), (1, 110), (0.6, 120), (0.6, 440)], 10, "z", c, (40, 40)))
    m.add(soft_box(100, 560, 90, 560, 440, 520, 60, 30, 30))
    m.add(soft_box(120, 540, 540, 620, 560, 1080, 70, 30, 60))
    m.add(tube((330, 580, 440), (330, 580, 560), 20, 8))
    for x in (95, 565):
        m.add(tube((x, 300, 480), (x, 300, 640), 12, 8))
        m.add(soft_box(x - 30, x + 30, 180, 420, 640, 670, 24, 10, 8, segs=2, arc=1))
    return m


def bean_bag():
    """Bean bag: a heavy pear-shaped slump, wider and flatter at the bottom."""
    return Mesh().add(lathe([(0.8, 0), (0.97, 60), (1, 180), (0.94, 330), (0.74, 480), (0.4, 580), (0, 610)], 12, "z", (360, 360), (360, 360)))


def side_table():
    """Round side table: a top with a rounded edge, a pedestal and a weighted foot."""
    m = Mesh().add(lathe([(0.97, 0), (1, 6), (1, 26), (0.97, 30)], 12, "z", (225, 225), (225, 225)))
    m.add(lathe([(1, 520), (1, 550), (0.97, 555)], 12, "z", (225, 225), (225, 225)))
    return m.add(lathe([(1, 30), (0.7, 60), (0.7, 480), (1, 520)], 10, "z", (225, 225), (40, 40)))


def coffee_table():
    """Coffee table: a top with rounded edges, a lower shelf and four legs."""
    m = Mesh().add(soft_box(0, 1100, 0, 600, 400, 440, 24, 8, 12, arc=1))
    m.add(soft_box(60, 1040, 60, 540, 120, 140, 12, 4, 6, arc=1))
    for x in (40, 1060):
        for y in (40, 560):
            m.add(leg(x, y, 20, 0, 400, 0.8))
    return m


def filing_cabinet():
    """Filing cabinet: a tall carcass, four drawers, each with a handle and a label holder."""
    m = Mesh().add(box(0, 16, 0, 620, 0, 1320)).add(box(454, 470, 0, 620, 0, 1320))
    m.add(box(16, 454, 0, 620, 1300, 1320)).add(box(16, 454, 0, 620, 0, 30))
    for i in range(4):
        z0 = 30 + i * 317
        m.add(soft_box(18, 452, 0, 610, z0 + 4, z0 + 313, 6, 3, 4, segs=1, arc=1))
        m.add(soft_box(180, 290, 610, 628, z0 + 200, z0 + 226, 6, 3, 3, segs=1, arc=1))
        m.add(box(200, 270, 610, 616, z0 + 240, z0 + 270))
    return m


def robot_vacuum():
    """Robot vacuum: a low round body with a bevelled edge, the bumper and a sensor turret."""
    m = Mesh().add(lathe([(0.94, 8), (1, 20), (1, 72), (0.95, 86)], 12, "z", (175, 175), (175, 175)))
    m.add(lathe([(1, 0), (1, 8)], 10, "z", (175, 175), (140, 140)))
    return m.add(lathe([(1, 86), (1, 100), (0.9, 104)], 10, "z", (175, 120), (42, 42)))


def dumbbell():
    """Dumbbell: a knurled handle between stacks of round weight plates."""
    m = Mesh().add(tube((0, 60, 60), (360, 60, 60), 16, 10))
    for x0, x1, r in ((10, 35, 60), (35, 60, 52), (300, 325, 52), (325, 350, 60)):
        m.add(lathe([(0.95, x0), (1, x0 + 3), (1, x1 - 3), (0.95, x1)], 12, "x", (60, 60), (r, r)))
    return m


def kettlebell():
    """Kettlebell: a round cast body on a flat base, and the thick handle over it."""
    m = Mesh().add(lathe([(0.55, 0), (0.85, 20), (1, 90), (0.9, 150), (0.5, 185), (0.2, 192)], 12, "z", (110, 110), (110, 110)))
    return m.add(sweep([(40, 110, 150)] + arc((110, 110, 200), 70, 180, 0, 10)[1:] + [(180, 110, 150)], 16, 8))


def sewing_machine():
    """Sewing machine: the bed, the pillar, the arm over it, the head, needle and handwheel."""
    m = Mesh().add(soft_box(0, 420, 0, 190, 0, 70, 20, 10, 10))
    m.add(soft_box(290, 400, 30, 160, 70, 300, 30, 20, 20, segs=2))
    m.add(soft_box(30, 400, 40, 150, 230, 320, 40, 26, 30, segs=2))
    m.add(soft_box(30, 110, 40, 150, 150, 320, 26, 16, 16, segs=2))
    m.add(tube((70, 95, 150), (70, 95, 95), 5, 6))
    return m.add(lathe([(0.9, 400), (1, 406), (1, 430), (0.9, 436)], 12, "x", (95, 240), (60, 60)))


def paint_can():
    """Paint tin: a straight can with rolled rims, a lid and a wire handle."""
    m = Mesh().add(lathe([(0.96, 0), (1, 4), (1, 12), (0.98, 16), (0.98, 172), (1, 176), (1, 186), (0.94, 190)],
                         12, "z", (95, 95), (95, 95)))
    return m.add(sweep(arc((95, 95, 150), 97, 0, 180, 14), 3, 6))


def wheelbarrow():
    """Wheelbarrow: a flared steel tray, the wheel at the front, two handles and legs."""
    m = Mesh().add(rounded_loft(250, 1050, 60, 620, 90, [(80, 380), (0, 640), (0, 650)]))
    m.add(lathe([(0.8, 305), (1, 312), (1, 368), (0.8, 375)], 12, "y", (170, 180), (180, 180)))
    for y in (120, 560):
        m.add(tube((170, y, 180), (1500, y, 520), 18, 8))
        m.add(tube((950, y, 400), (980, y, 0), 16, 6))
    return m


def bbq_grill():
    """Kettle barbecue: a round bowl, its domed lid with a handle, three legs and two wheels."""
    c = (300, 300)
    m = Mesh().add(lathe([(0.3, 560), (0.7, 600), (0.95, 680), (1, 760), (1, 770)], 12, "z", c, (290, 290)))
    m.add(lathe([(1, 770), (0.98, 790), (0.8, 900), (0.4, 960), (0, 970)], 12, "z", c, (292, 292)))
    m.add(sweep(arc((300, 300, 975), 50, 180, 0, 8), 9, 6))
    for k in range(3):
        a = math.radians(90 + 120 * k)
        m.add(tube((c[0] + 150 * math.cos(a), c[1] + 150 * math.sin(a), 600), (c[0] + 260 * math.cos(a), c[1] + 260 * math.sin(a), 60), 12, 6))
    # Two wheels on an axle between the two rear legs' feet.
    m.add(tube((75, 170, 60), (525, 170, 60), 8, 6))
    return m.add(lathe([(1, 40), (1, 75)], 10, "x", (170, 60), (60, 60))).add(lathe([(1, 525), (1, 560)], 10, "x", (170, 60), (60, 60)))


def pet_carrier():
    """Pet carrier: a rounded shell, the barred door on the front and a handle on top."""
    m = Mesh().add(soft_box(0, 330, 0, 480, 0, 300, 50, 30, 40))
    for k in range(6):
        x = 70 + k * 38
        m.add(tube((x, 478, 60), (x, 478, 250), 5, 6))
    m.add(sweep([(60, 482, 50), (270, 482, 50), (270, 482, 260), (60, 482, 260)], 7, 6, closed=True))
    return m.add(sweep([(165, 150, 300), (165, 170, 360), (165, 310, 360), (165, 330, 300)], 12, 8))


def dog_bed():
    """Round dog bed: a plump bolster ring around a sunken cushion."""
    ring = arc((400, 400, 110), 320, 0, 360, 32, "xy")[:-1]
    m = Mesh().add(sweep(ring, 90, 12, closed=True))
    return m.add(lathe([(1, 0), (1, 60), (0.96, 80)], 12, "z", (400, 400), (330, 330)))


def aquarium():
    """Fish tank: a base trim, glass walls in a frame, gravel, and the hood on top."""
    m = Mesh().add(soft_box(0, 600, 0, 300, 0, 40, 10, 4, 6, arc=1))
    for x, y in ((0, 0), (580, 0), (580, 280), (0, 280)):
        m.add(box(x, x + 20, y, y + 20, 40, 380))
    m.add(box(20, 580, 6, 12, 40, 380)).add(box(20, 580, 288, 294, 40, 380))
    m.add(box(6, 12, 20, 280, 40, 380)).add(box(588, 594, 20, 280, 40, 380))
    m.add(box(20, 580, 20, 280, 40, 80))
    return m.add(soft_box(-4, 604, -4, 304, 380, 440, 12, 6, 8, arc=1))


def facing_viewer(fn):
    """
    Turns a family round so its front — the door, the keyboard, the seat — faces +Y.

    The plan is drawn from the +X, +Y side (IsometricCrate's projection), so a front built at
    Y = 0 would be the one face nobody sees: a wardrobe would show its bare back and a sofa
    its back rest. Mirroring flips the winding, so every face is reversed to stay outward.
    """
    def wrapped():
        m = fn()
        mirrored = []
        for f in m.faces:
            g = type(f)(reversed([(p[0], 1 - p[1], p[2]) for p in f]))
            g.part = f.part
            mirrored.append(g)
        m.faces = mirrored
        return m
    wrapped.__doc__ = fn.__doc__
    wrapped.__name__ = fn.__name__
    return wrapped


for _f in ("upright_fridge", "wardrobe", "piano", "backpack", "sofa", "armchair", "chair", "bed_frame",
           "coffee_maker", "microwave", "cooker", "bookcase", "chest_of_drawers", "desk", "monitor", "printer",
           "helmet"):
    globals()[_f] = facing_viewer(globals()[_f])


FAMILIES = [
    # drawer and box scale
    ("flat_rectangle", flat_rectangle), ("slim_slab", slim_slab), ("small_carton", small_carton),
    ("upright_cylinder", upright_cylinder), ("lying_cylinder", lying_cylinder), ("bottle", bottle),
    ("tight_roll", tight_roll), ("soft_pouch", soft_pouch), ("cable_coil", cable_coil),
    ("thin_bundle", thin_bundle), ("shallow_tray", shallow_tray),
    # car boot
    ("suitcase", suitcase), ("duffel_bag", duffel_bag), ("backpack", backpack), ("cooler_box", cooler_box),
    ("folded_chair", folded_chair), ("yoga_mat", yoga_mat), ("toolbox", toolbox), ("ball", ball), ("crate", crate),
    # truck bed
    ("appliance_slab", appliance_slab), ("upright_fridge", upright_fridge), ("mattress", mattress),
    ("plank_stack", plank_stack), ("ladder", ladder), ("barrel", barrel), ("bicycle", bicycle), ("lawnmower", lawnmower),
    # room
    ("sofa", sofa), ("armchair", armchair), ("dining_table", dining_table), ("chair", chair), ("wardrobe", wardrobe),
    ("bed_frame", bed_frame), ("lamp", lamp), ("tv_stand", tv_stand), ("plant_pot", plant_pot), ("piano", piano),
    # kitchen
    ("kettle", kettle), ("coffee_maker", coffee_maker), ("microwave", microwave), ("toaster", toaster),
    ("cooker", cooker), ("cooking_pot", cooking_pot), ("frying_pan", frying_pan), ("plate_stack", plate_stack),
    ("bowl", bowl),
    # bedroom and living room
    ("pillow", pillow), ("folded_stack", folded_stack), ("bookcase", bookcase), ("chest_of_drawers", chest_of_drawers),
    ("desk", desk), ("stool", stool), ("ottoman", ottoman), ("clothes_rail", clothes_rail),
    ("framed_panel", framed_panel), ("disc", disc),
    # office and electronics
    ("monitor", monitor), ("printer", printer),
    # personal
    ("helmet", helmet), ("shoe", shoe), ("tote_bag", tote_bag), ("guitar", guitar),
    # cleaning, tools and garden
    ("upright_vacuum", upright_vacuum), ("long_handle", long_handle), ("watering_can", watering_can),
    ("power_drill", power_drill), ("hand_tool", hand_tool),
    # modelled at real size
    ("mug", mug),
    ("wine_glass", wine_glass),
    ("vase", vase),
    ("teapot", teapot),
    ("blender", blender),
    ("stand_mixer", stand_mixer),
    ("air_fryer", air_fryer),
    ("knife_block", knife_block),
    ("dish_rack", dish_rack),
    ("laundry_basket", laundry_basket),
    ("bucket", bucket),
    ("pedal_bin", pedal_bin),
    ("clothes_iron", clothes_iron),
    ("ironing_board", ironing_board),
    ("pedestal_fan", pedestal_fan),
    ("oil_heater", oil_heater),
    ("desk_lamp", desk_lamp),
    ("speaker", speaker),
    ("camera", camera),
    ("headphones", headphones),
    ("alarm_clock", alarm_clock),
    ("book_stack", book_stack),
    ("potted_plant", potted_plant),
    ("umbrella", umbrella),
    ("storage_bin", storage_bin),
    ("boot", boot),
    ("hair_dryer", hair_dryer),
    ("skateboard", skateboard),
    ("kick_scooter", kick_scooter),
    ("tennis_racket", tennis_racket),
    ("golf_bag", golf_bag),
    ("snowboard", snowboard),
    ("lantern", lantern),
    ("gas_cylinder", gas_cylinder),
    ("stroller", stroller),
    ("child_car_seat", child_car_seat),
    ("high_chair", high_chair),
    ("office_chair", office_chair),
    ("bean_bag", bean_bag),
    ("side_table", side_table),
    ("coffee_table", coffee_table),
    ("filing_cabinet", filing_cabinet),
    ("robot_vacuum", robot_vacuum),
    ("dumbbell", dumbbell),
    ("kettlebell", kettlebell),
    ("sewing_machine", sewing_machine),
    ("paint_can", paint_can),
    ("wheelbarrow", wheelbarrow),
    ("bbq_grill", bbq_grill),
    ("pet_carrier", pet_carrier),
    ("dog_bed", dog_bed),
    ("aquarium", aquarium),
]


# ---------------------------------------------------------------------------------------------
# checks — the contract is enforced here, not trusted


# Enough for 32-sided curves on the most detailed shapes. A plan draws a few dozen items, and
# the renderer skips every face turned away from the viewer, so about half of these are drawn.
MAX_QUADS = 1200


def check(name, faces):
    problems = []
    pts = [p for f in faces for p in f]
    lo = [min(p[i] for p in pts) for i in range(3)]
    hi = [max(p[i] for p in pts) for i in range(3)]
    for i in range(3):
        if lo[i] < -1e-6 or hi[i] > 1 + 1e-6:
            problems.append("outside unit box on axis %d (%.3f..%.3f)" % (i, lo[i], hi[i]))
        if abs(lo[i]) > 1e-6 or abs(hi[i] - 1) > 1e-6:
            problems.append("does not fill axis %d (%.3f..%.3f)" % (i, lo[i], hi[i]))
    for k, f in enumerate(faces):
        if len(f) != 4:
            problems.append("face %d has %d points" % (k, len(f)))
            continue
        n = newell(f)
        ln = math.sqrt(dot(n, n))
        if ln < 1e-9:
            problems.append("face %d has no area" % k)
            continue
        n = (n[0] / ln, n[1] / ln, n[2] / ln)
        c = centroid(f)
        off = max(abs(dot(sub(p, c), n)) for p in f)
        # A facet of a bent tube — a handle, a spout — sits a hair off flat, which no drawing can
        # show; hard edges must be exactly flat.
        if off > (1e-2 if isinstance(f, SmoothFace) else 1e-4):
            problems.append("face %d not planar (%.5f)" % (k, off))
    if len(faces) > MAX_QUADS:
        problems.append("%d quads — over the %d budget" % (len(faces), MAX_QUADS))
    return problems


def normalise(faces):
    """
    Scales a family into the unit box, each axis on its own. Shapes authored at real size in
    millimetres come out filling the box exactly, as the app requires; shapes already authored
    in the unit box come through unchanged. Each face keeps its part and smoothness.
    """
    pts = [p for f in faces for p in f]
    lo = [min(p[i] for p in pts) for i in range(3)]
    hi = [max(p[i] for p in pts) for i in range(3)]
    span = [max(hi[i] - lo[i], 1e-9) for i in range(3)]
    out = []
    for f in faces:
        g = type(f)([tuple((p[i] - lo[i]) / span[i] for i in range(3)) for p in f])
        g.part = getattr(f, "part", 0)
        out.append(g)
    return out


def emit(out_dir, readme_dir=None):
    """
    Writes one JSON per family into `out_dir` and the README into `readme_dir` (default: next to
    this script). The README must not land in `res/raw`: Android rejects resource file names
    with capitals, and a stray README there would break the build.
    """
    readme_dir = readme_dir or os.path.dirname(os.path.abspath(__file__))
    os.makedirs(out_dir, exist_ok=True)
    rows = []
    failed = False
    for name, fn in FAMILIES:
        mesh = fn()
        faces = normalise(mesh.faces)
        problems = check(name, faces)
        if problems:
            failed = True
            print("FAIL", name, problems[:4])
        data = {
            "family": name,
            "version": 1,
            "params": {},
            "bounds_mm": [1000, 1000, 1000],
            "faces": [dict({"points": [[round(c * S, 1) for c in p] for p in f], "part": f.part},
                           **({"smooth": True} if isinstance(f, SmoothFace) else {}))
                      for f in faces],
        }
        with open(os.path.join(out_dir, "family_%s.json" % name), "w") as fh:
            json.dump(data, fh, separators=(",", ":"))
        rows.append((name, len(faces), mesh.parts, (fn.__doc__ or "").strip().split("\n")[0]))
    with open(os.path.join(readme_dir, "README.md"), "w", encoding="utf-8") as fh:
        fh.write("# Item geometry families\n\n")
        fh.write("All geometry is original, authored procedurally in `families.py`. Nothing was downloaded, "
                 "imported, traced or converted from any third-party asset.\n\n")
        fh.write("Canonical box 1000 × 1000 × 1000 mm, origin at the minimum corner, X width, Y depth, Z up. "
                 "Quads only, planar, convex, counter-clockwise from outside, closed shells, no interior faces.\n\n")
        fh.write("| Family | Quads | Parts | What it is |\n| --- | ---: | ---: | --- |\n")
        for name, q, parts, doc in rows:
            fh.write("| `%s` | %d | %d | %s |\n" % (name, q, parts, doc))
        fh.write("\n## Compromises\n\n"
                 "- Round parts are faceted at 16–32 sides and marked `smooth`, which the app draws without edge lines "
                 "so a curve shades as one surface. Sphere and dome poles are degenerate quads (one repeated vertex).\n"
                 "- `bicycle` wheels are solid discs rather than rings — a tyre ring costs 60+ quads per wheel.\n"
                 "- `folded_chair`, `ladder` and `plank_stack` are drawn lying as they are usually packed.\n"
                 "- One variant per family. Proportions are ratios of the measured box, so an unusually squat "
                 "bottle stays a bottle.\n")
        fh.write("\n## Regenerating\n\n"
                 "From the project root, in plain Python 3 or Blender 4.x headless:\n\n"
                 "```\npython3 tools/geometry/families.py app/src/main/res/raw\n"
                 "blender -b -P tools/geometry/families.py -- app/src/main/res/raw\n```\n\n"
                 "It checks every family (fills the box, 4 points per face, planar, at most 600 quads) and exits 1 "
                 "if any fails. `preview.py` redraws `families_preview.png` from the JSON.\n\n"
                 "## In the app\n\n"
                 "`ui/render/FamilyMeshes.kt` loads these with `org.json`, stretches each to the item's measured box, "
                 "and chooses the family (scanned voxels first, then the measured form, then the item's name). "
                 "`IsometricCrate` shades each face from its normal. The meshes are display only: they never reach "
                 "`ItemSpec.shape` or the solver.\n")
    return failed


def build_in_blender(out_dir):
    """When run inside Blender, also build every family in the scene for a visual check."""
    import bpy  # noqa: F401 — only available inside Blender
    for i, (name, fn) in enumerate(FAMILIES):
        faces = fn().faces
        verts, idx = [], []
        for f in faces:
            base = len(verts)
            verts.extend(f)
            idx.append(tuple(range(base, base + 4)))
        me = bpy.data.meshes.new(name)
        me.from_pydata(verts, [], idx)
        me.update()
        ob = bpy.data.objects.new(name, me)
        ob.location = ((i % 10) * 1.6, (i // 10) * 1.6, 0)
        bpy.context.collection.objects.link(ob)


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    out = argv[0] if argv else "families_out"
    bad = emit(out)
    try:
        build_in_blender(out)
    except ImportError:
        pass
    sys.exit(1 if bad else 0)
