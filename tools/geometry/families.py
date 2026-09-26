"""
Pack a Bunch — item geometry families.

Thirty-eight generic, low-poly shapes that make a packing plan readable: a bottle is drawn as
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
    faces += fan(bottom) + fan(top)
    return [orient(f, sub(centroid(f), c)) for f in faces]


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
            faces.append(orient(quad, out))
    if profile[0][0] > 0:
        ring = [point(profile[0][0], profile[0][1], k) for k in range(sides)]
        faces += [orient(f, axis_dir(-1 if profile[1][1] >= profile[0][1] else 1)) for f in fan(ring)]
    if profile[-1][0] > 0:
        ring = [point(profile[-1][0], profile[-1][1], k) for k in range(sides)]
        faces += [orient(f, axis_dir(1 if profile[-1][1] >= profile[-2][1] else -1)) for f in fan(ring)]
    return faces


def sphere(centre, radii, slices, stacks):
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
            faces.append(orient(q, sub(centroid(q), centre)))
    return faces


def torus(centre, big, small_ab, small_z, ring_sides, tube_sides):
    """Lying flat (axis Z). `big` is (ra, rb) of the centreline; the tube is small_ab across, small_z tall."""
    cx, cy, cz = centre
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
            faces.append(orient(q, sub(centroid(q), core)))
    return faces


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
    """Wash bag, pencil case, soft bag: a pillow section with a zip ridge."""
    # The polygon is (y, z): width across depth, height up; the zip ridge sits on top.
    m = Mesh().add(prism([(0, 0.18), (0.12, 0), (0.88, 0), (1, 0.18), (1, 0.74), (0.86, 0.9), (0.14, 0.9), (0, 0.74)], "x", 0, 1))
    return m.add(box(0.04, 0.96, 0.44, 0.56, 0.9, 1))


def cable_coil():
    """A coiled cable or hose: a flat torus."""
    return Mesh().add(torus((0.5, 0.5, 0.5), (0.35, 0.35), 0.5 / 0.35 - 1, 0.5, 12, 5))


def thin_bundle():
    """Cutlery, tent poles, a sheaf of rods: long bars side by side, ends staggered."""
    return Mesh().add(box(0, 0.96, 0, 0.3, 0, 1)).add(box(0.02, 1, 0.35, 0.65, 0, 0.8)).add(box(0.04, 0.98, 0.7, 1, 0, 0.9))


def shallow_tray():
    """Baking tray, pan, drawer organiser: a floor and four low walls."""
    t = 0.07
    m = Mesh().add(box(0, 1, 0, 1, 0, 0.18))
    m.add(box(0, 1, 0, t, 0.18, 1)).add(box(0, 1, 1 - t, 1, 0.18, 1))
    return m.add(box(0, t, t, 1 - t, 0.18, 1)).add(box(1 - t, 1, t, 1 - t, 0.18, 1))


def suitcase():
    """Standing case with rounded corners and a carry handle."""
    m = Mesh().add(prism(chamfered_rect(0, 1, 0, 0.9, 0.08, 0.08), "y", 0, 1))
    m.add(box(0.34, 0.39, 0.4, 0.6, 0.9, 0.955)).add(box(0.61, 0.66, 0.4, 0.6, 0.9, 0.955))
    return m.add(box(0.34, 0.66, 0.4, 0.6, 0.955, 1))


def duffel_bag():
    """A barrel bag lying along its width, with a strap handle on top."""
    m = Mesh().add(lathe([(0.55, 0), (0.85, 0.05), (1, 0.14), (1, 0.86), (0.85, 0.95), (0.55, 1)], 10, "x", (0.5, 0.44), (0.5, 0.44)))
    return m.add(box(0.3, 0.7, 0.42, 0.58, 0.88, 1))


def backpack():
    """Main body with a rounded top, a front pocket and a grab handle."""
    body = prism([(0.22, 0), (1, 0), (1, 0.84), (0.86, 0.94), (0.36, 0.94), (0.22, 0.84)], "x", 0, 1)
    m = Mesh().add(body)
    m.add(box(0.16, 0.84, 0, 0.22, 0.08, 0.56))
    return m.add(box(0.4, 0.6, 0.52, 0.7, 0.94, 1))


def cooler_box():
    """Cool box: body, overhanging lid, handle."""
    return Mesh().add(box(0.03, 0.97, 0.03, 0.97, 0, 0.8)).add(box(0, 1, 0, 1, 0.8, 0.92)).add(box(0.28, 0.72, 0.44, 0.56, 0.92, 1))


def folded_chair():
    """A folding chair folded flat: two frames and the hinge between them."""
    return Mesh().add(box(0, 1, 0, 0.42, 0, 0.94)).add(box(0, 1, 0.58, 1, 0.06, 1)).add(box(0.1, 0.9, 0.42, 0.58, 0.44, 0.56))


def yoga_mat():
    """A rolled mat: the roll, with its core showing at both ends."""
    m = Mesh().add(lathe([(1, 0.03), (1, 0.97)], 12, "x", (0.5, 0.5), (0.5, 0.5)))
    m.add(lathe([(0.35, 0), (0.35, 0.03)], 8, "x", (0.5, 0.5), (0.5, 0.5)))
    return m.add(lathe([(0.35, 0.97), (0.35, 1)], 8, "x", (0.5, 0.5), (0.5, 0.5)))


def toolbox():
    """Box body, sloped lid, carry handle."""
    m = Mesh().add(box(0, 1, 0, 1, 0, 0.72))
    m.add(prism([(0, 0.72), (1, 0.72), (0.88, 0.88), (0.12, 0.88)], "x", 0, 1))
    m.add(box(0.2, 0.26, 0.45, 0.55, 0.88, 1)).add(box(0.74, 0.8, 0.45, 0.55, 0.88, 1))
    return m.add(box(0.26, 0.74, 0.45, 0.55, 0.95, 1))


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
    """Washing machine, dishwasher: body, round door, control strip."""
    m = Mesh().add(box(0, 1, 0.05, 1, 0, 1))
    m.add(lathe([(1, 0), (1, 0.05)], 12, "y", (0.5, 0.42), (0.3, 0.3)))
    return m.add(box(0.08, 0.92, 0.02, 0.05, 0.84, 0.95))


def upright_fridge():
    """Tall fridge: body, fridge and freezer doors, handles."""
    m = Mesh().add(box(0, 1, 0.04, 1, 0, 1))
    m.add(box(0.01, 0.99, 0.015, 0.04, 0.01, 0.62)).add(box(0.01, 0.99, 0.015, 0.04, 0.64, 0.99))
    return m.add(box(0.82, 0.88, 0, 0.015, 0.36, 0.58)).add(box(0.82, 0.88, 0, 0.015, 0.68, 0.84))


def mattress():
    """A mattress with softened long edges."""
    return Mesh().add(prism(chamfered_rect(0, 1, 0, 1, 0.04, 0.2), "x", 0, 1))


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
    """Two wheels, a triangle frame, saddle and handlebar — seen side on along its width."""
    m = Mesh()
    for cx in (0.22, 0.78):
        m.add(lathe([(1, 0.44), (1, 0.56)], 10, "y", (cx, 0.36), (0.22, 0.36)))
    hub_r, hub_f = (0.22, 0.5, 0.36), (0.78, 0.5, 0.36)
    crank, seat, head = (0.44, 0.5, 0.36), (0.38, 0.5, 0.78), (0.72, 0.5, 0.8)
    m.add(bar(hub_r, crank, 0.012, 0.012)).add(bar(crank, seat, 0.012, 0.012)).add(bar(seat, head, 0.012, 0.012))
    m.add(bar(crank, head, 0.012, 0.012)).add(bar(head, hub_f, 0.012, 0.012))
    m.add(box(0.31, 0.45, 0.44, 0.56, 0.8, 0.86))
    return m.add(box(0.7, 0.76, 0, 1, 0.9, 1))


def lawnmower():
    """Deck, four wheels, and a handle rising to the back."""
    m = Mesh().add(box(0.08, 0.84, 0.12, 0.88, 0.08, 0.44))
    for cx in (0.15, 0.72):
        for y0, y1 in ((0, 0.12), (0.88, 1)):
            m.add(lathe([(1, y0), (1, y1)], 6, "y", (cx, 0.16), (0.15, 0.16)))
    m.add(bar((0.84, 0.2, 0.38), (0.98, 0.2, 0.97), 0.015, 0.015)).add(bar((0.84, 0.8, 0.38), (0.98, 0.8, 0.97), 0.015, 0.015))
    return m.add(box(0.95, 1, 0.16, 0.84, 0.94, 1))


def sofa():
    """Seat base, back, two arms."""
    m = Mesh().add(box(0.12, 0.88, 0, 0.74, 0, 0.45)).add(box(0, 1, 0.74, 1, 0, 1))
    return m.add(box(0, 0.12, 0, 0.74, 0, 0.64)).add(box(0.88, 1, 0, 0.74, 0, 0.64))


def armchair():
    """A deep seat between thick arms, high back."""
    m = Mesh().add(box(0.2, 0.8, 0, 0.72, 0, 0.46)).add(box(0, 1, 0.72, 1, 0, 1))
    return m.add(box(0, 0.2, 0, 0.72, 0, 0.66)).add(box(0.8, 1, 0, 0.72, 0, 0.66))


def dining_table():
    """Top and four legs."""
    m = Mesh().add(box(0, 1, 0, 1, 0.92, 1))
    for x0 in (0.04, 0.9):
        for y0 in (0.04, 0.9):
            m.add(box(x0, x0 + 0.06, y0, y0 + 0.06, 0, 0.92))
    return m


def chair():
    """Seat, four legs, back posts and a back rail."""
    m = Mesh().add(box(0, 1, 0, 1, 0.44, 0.52))
    for x0 in (0.02, 0.88):
        for y0 in (0.02, 0.88):
            m.add(box(x0, x0 + 0.1, y0, y0 + 0.1, 0, 0.44))
    m.add(box(0.02, 0.12, 0.88, 0.98, 0.52, 1)).add(box(0.88, 0.98, 0.88, 0.98, 0.52, 1))
    return m.add(box(0.12, 0.88, 0.9, 0.96, 0.72, 0.96))


def wardrobe():
    """Carcass, two doors, two handles."""
    m = Mesh().add(box(0, 1, 0.03, 1, 0, 1))
    m.add(box(0.01, 0.495, 0.012, 0.03, 0.02, 0.98)).add(box(0.505, 0.99, 0.012, 0.03, 0.02, 0.98))
    return m.add(box(0.44, 0.47, 0, 0.012, 0.42, 0.6)).add(box(0.53, 0.56, 0, 0.012, 0.42, 0.6))


def bed_frame():
    """Frame on legs, mattress, headboard and a low footboard; length runs along depth."""
    m = Mesh().add(box(0, 1, 0.04, 0.94, 0.1, 0.3)).add(box(0.03, 0.97, 0.05, 0.93, 0.3, 0.46))
    m.add(box(0, 1, 0.94, 1, 0, 1)).add(box(0, 1, 0, 0.04, 0, 0.4))
    for x0 in (0, 0.94):
        m.add(box(x0, x0 + 0.06, 0.4, 0.46, 0, 0.1))
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


def facing_viewer(fn):
    """
    Turns a family round so its front — the door, the keyboard, the seat — faces +Y.

    The plan is drawn from the +X, +Y side (IsometricCrate's projection), so a front built at
    Y = 0 would be the one face nobody sees: a wardrobe would show its bare back and a sofa
    its back rest. Mirroring flips the winding, so every face is reversed to stay outward.
    """
    def wrapped():
        m = fn()
        m.faces = [list(reversed([(p[0], 1 - p[1], p[2]) for p in f])) for f in m.faces]
        return m
    wrapped.__doc__ = fn.__doc__
    wrapped.__name__ = fn.__name__
    return wrapped


for _f in ("appliance_slab", "upright_fridge", "wardrobe", "piano", "backpack", "sofa", "armchair", "chair", "bed_frame"):
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
]


# ---------------------------------------------------------------------------------------------
# checks — the contract is enforced here, not trusted


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
        if off > 1e-4:
            problems.append("face %d not planar (%.5f)" % (k, off))
    if len(faces) > 80:
        problems.append("%d quads — over the 80 budget" % len(faces))
    return problems


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
        faces = mesh.faces
        problems = check(name, faces)
        if problems:
            failed = True
            print("FAIL", name, problems[:4])
        data = {
            "family": name,
            "version": 1,
            "params": {},
            "bounds_mm": [1000, 1000, 1000],
            "faces": [{"points": [[round(c * S, 1) for c in p] for p in f]} for f in faces],
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
                 "- `cable_coil`, `ball`, `lamp` and the other round families are faceted (10–12 sides); the sphere "
                 "poles are degenerate quads (one repeated vertex), the only triangles in the set.\n"
                 "- `bicycle` wheels are solid discs rather than rings — a tyre ring costs 60+ quads per wheel.\n"
                 "- `folded_chair`, `ladder` and `plank_stack` are drawn lying as they are usually packed.\n"
                 "- One variant per family. Proportions are ratios of the measured box, so an unusually squat "
                 "bottle stays a bottle.\n")
        fh.write("\n## Regenerating\n\n"
                 "From the project root, in plain Python 3 or Blender 4.x headless:\n\n"
                 "```\npython3 tools/geometry/families.py app/src/main/res/raw\n"
                 "blender -b -P tools/geometry/families.py -- app/src/main/res/raw\n```\n\n"
                 "It checks every family (fills the box, 4 points per face, planar, at most 80 quads) and exits 1 "
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
