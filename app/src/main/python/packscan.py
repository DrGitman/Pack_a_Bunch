"""
Pack a Bunch — the measuring engine, in Python with OpenCV and NumPy.

The app's camera screens stay in Kotlin: ARCore supplies what only it can (the real-world
scale — every depth pixel in millimetres — and where the phone is), ML Kit says roughly where
each thing is and what it is called, and the screens draw the result. Everything in between is
here:

* select() — tracing an object's body. From the depth pixels inside a detector's box (already
  turned into millimetres above the surface the object stands on), keep the ones that are the
  object: a depth window around the middle of the box, then a height map sliced into layers and
  labelled with OpenCV's connected components, joined layer to layer, and the piece that owns the
  middle of the box wins. The lowest layer never joins sideways, so table noise cannot bridge one
  object to the next.

* fit() — measuring it. The object's accumulated points become a footprint: OpenCV's minimum-area
  rotated rectangle gives its turn (the same thing PCA gives a long object, and correct for a
  square one where PCA is not), then the faces are placed at the weighted median of the points on
  them. Round things are found slice by slice with a least-squares circle, so a cup seen from the
  front still gets its full diameter; the slices say cylinder, tapered or ball. Anything that
  neither fills its rectangle nor is round is IRREGULAR and gets its convex hull.

Both take and return JSON text, so Kotlin (through Chaquopy on the phone) and the JVM tests
(through a python3 process) call exactly the same code. Run `python3 packscan.py --serve` for the
line-by-line JSON server the tests use.
"""

import json
import math
import sys

import numpy as np

try:  # OpenCV does the image work when it is there; NumPy fallbacks keep the maths identical.
    import cv2
except Exception:  # pragma: no cover — only when OpenCV is missing
    cv2 = None

# Shared with the Kotlin side (DetectionPoints, ScanFilters, ObjectCloud, ShapeFitter).
MIN_HEIGHT_MM = 6.0
CELL_MM = 15.0
MAX_HEIGHT_MM = 1500.0
MIN_SAMPLES = 12
MIN_CENTRAL = 6
MIN_CENTRAL_SHARE = 0.5
MIN_CENTRAL_SAMPLES = 6
MIN_REACH_MM = 120.0
MAX_REACH_MM = 900.0

MIN_POINTS = 20
MIN_VIEW_ANGLE_DEG = 15.0
BOX_HULL_FILL = 0.88
BOX_FACE_SHARE = 0.72
TRIM_SHARE = 0.004
MIN_INLIER_SHARE = 0.85
STEEP_SLOPE = 1.2
SPHERE_CURVATURE = 0.4
BAND_MM = 25.0
MIN_ROUND_ARC_DEG = 100.0
ROUND_OBSERVED_ARC_DEG = 140.0


# ============================================================================================
# tracing the body


def depth_window(depths, central, span_px, focal_px):
    """The depths that can be this object: around the middle of the box, as deep as it is wide."""
    mid = depths[central]
    if mid.size < MIN_CENTRAL:
        return None
    median = float(np.sort(mid)[mid.size // 2])
    reach = min(max(span_px * median / focal_px, MIN_REACH_MM), MAX_REACH_MM)
    return median - int(reach), median + int(reach)


def _label_layer(mask):
    """8-connected components of one layer of the height map."""
    if cv2 is not None:
        n, labels = cv2.connectedComponents(mask.astype(np.uint8), connectivity=8)
        return n, labels
    labels = np.zeros(mask.shape, np.int32)
    n = 1
    for (i, j) in zip(*np.nonzero(mask)):
        if labels[i, j]:
            continue
        stack = [(i, j)]
        labels[i, j] = n
        while stack:
            a, b = stack.pop()
            for da in (-1, 0, 1):
                for db in (-1, 0, 1):
                    p, q = a + da, b + db
                    if 0 <= p < mask.shape[0] and 0 <= q < mask.shape[1] and mask[p, q] and not labels[p, q]:
                        labels[p, q] = n
                        stack.append((p, q))
        n += 1
    return n, labels


def _dilate(mask):
    if cv2 is not None:
        return cv2.dilate(mask.astype(np.uint8), np.ones((3, 3), np.uint8)) > 0
    out = mask.copy()
    for da in (-1, 0, 1):
        for db in (-1, 0, 1):
            out |= np.roll(np.roll(mask, da, 0), db, 1)
    return out


class _Union:
    def __init__(self):
        self.parent = {}

    def find(self, a):
        self.parent.setdefault(a, a)
        while self.parent[a] != a:
            self.parent[a] = self.parent[self.parent[a]]
            a = self.parent[a]
        return a

    def join(self, a, b):
        ra, rb = self.find(a), self.find(b)
        if ra != rb:
            self.parent[rb] = ra


def select(x, y, h, depth, central, edge, span_px, focal_px, cell=CELL_MM, min_share=MIN_CENTRAL_SHARE,
           min_central=MIN_CENTRAL_SAMPLES, max_h=MAX_HEIGHT_MM, use_depth_window=True, in_mask=None):
    """
    Indices of the samples that are the object, or [] when nothing in the box is one.
    [in_mask], when the picture has been segmented, says which samples fall inside the object's
    traced outline; the others — table, wall, the neighbour behind — are left out before anything
    else, so the fitted shape wraps the object and nothing beside it.
    """
    x = np.asarray(x, np.float64); y = np.asarray(y, np.float64); h = np.asarray(h, np.float64)
    central = np.asarray(central, bool); edge = np.asarray(edge, bool)
    n = x.size
    if n == 0:
        return []
    ok = np.ones(n, bool)
    if in_mask is not None and len(in_mask) == n:
        inside = np.asarray(in_mask, bool)
        # A mask that misses the object entirely (a bad trace) must not blank the frame.
        if inside[central].sum() >= MIN_CENTRAL:
            ok &= inside
    if use_depth_window and depth is not None and len(depth) == n:
        d = np.asarray(depth, np.float64)
        win = depth_window(d, central, span_px, focal_px)
        if win is None:
            return []
        ok &= (d >= win[0]) & (d <= win[1])
    central_total = int(np.count_nonzero(central & ok))
    raised = ok & (h > MIN_HEIGHT_MM) & (h <= max_h)
    body = raised & ~edge
    if not body.any():
        return []

    # The height map: a grid on the surface, one layer per cell of height.
    gi = np.floor(x / cell).astype(np.int64)
    gj = np.floor(y / cell).astype(np.int64)
    gk = np.floor(h / cell).astype(np.int64)
    i0, j0 = gi[body].min() - 1, gj[body].min() - 1
    ni, nj = int(gi[body].max() - i0 + 2), int(gj[body].max() - j0 + 2)
    kmax = int(gk[body].max()) + 1
    occupied = np.zeros((kmax, ni, nj), bool)
    occupied[gk[body], gi[body] - i0, gj[body] - j0] = True

    union = _Union()
    labels = np.zeros((kmax, ni, nj), np.int64)
    next_id = 1
    for k in range(kmax):
        layer = occupied[k]
        if not layer.any():
            continue
        if k == 0:
            # The lowest layer does not join sideways: every cell is its own piece until
            # something above it claims it.
            idx = np.nonzero(layer)
            labels[k][idx] = np.arange(next_id, next_id + idx[0].size)
            next_id += idx[0].size
        else:
            count, lab = _label_layer(layer)
            labels[k][layer] = lab[layer] + next_id - 1
            next_id += count
        if k > 0:
            # Join to the layer below where they touch, diagonals included.
            below = labels[k - 1]
            grown = _dilate(below > 0)
            touch = layer & grown
            if touch.any():
                for (a, b) in zip(*np.nonzero(touch)):
                    here = labels[k][a, b]
                    window = below[max(a - 1, 0):a + 2, max(b - 1, 0):b + 2]
                    for other in np.unique(window[window > 0]):
                        union.join(int(here), int(other))

    comp = np.zeros(n, np.int64)
    sel = np.nonzero(body)[0]
    comp[sel] = [union.find(int(v)) for v in labels[gk[sel], gi[sel] - i0, gj[sel] - j0]]
    roots, inverse = np.unique(comp[sel], return_inverse=True)
    size = np.bincount(inverse, minlength=roots.size)
    votes = np.bincount(inverse, weights=central[sel].astype(np.float64), minlength=roots.size)
    order = np.lexsort((size, votes))
    winner = order[-1]
    if size[winner] < MIN_SAMPLES:
        return []
    if votes[winner] < min_central or votes[winner] < central_total * min_share:
        return []
    keep = sel[inverse == winner]

    # Edge pixels join only where they land in or beside the winner's cells.
    kept_cells = set(zip(gk[keep].tolist(), gi[keep].tolist(), gj[keep].tolist()))
    extra = []
    for e in np.nonzero(raised & edge)[0]:
        a, b, c = int(gk[e]), int(gi[e]), int(gj[e])
        if any((a + da, b + db, c + dc) in kept_cells for da in (-1, 0, 1) for db in (-1, 0, 1) for dc in (-1, 0, 1)):
            extra.append(int(e))
    return sorted(keep.tolist() + extra)


# ============================================================================================
# measuring it


def robust_range(values, weights):
    if values.size < 100:
        return float(values.min()), float(values.max())
    order = np.argsort(values, kind="stable")
    cum = np.cumsum(weights[order])
    trim = cum[-1] * TRIM_SHARE
    lo = values[order[np.searchsorted(cum, trim, side="right")]]
    rcum = np.cumsum(weights[order][::-1])
    hi = values[order[::-1][np.searchsorted(rcum, trim, side="right")]]
    return float(lo), float(hi)


def weighted_median(v, w):
    order = np.argsort(v, kind="stable")
    cum = np.cumsum(w[order])
    return float(v[order[np.searchsorted(cum, cum[-1] / 2.0)]])


def min_area_rect(xs, ys):
    """Angle (degrees, 0..90) of the tightest rectangle round the points."""
    pts = np.stack([xs, ys], 1).astype(np.float32)
    if cv2 is not None and pts.shape[0] >= 3:
        hull = cv2.convexHull(pts)
        (_, _), (_, _), ang = cv2.minAreaRect(hull)
        return float(ang) % 90.0
    # NumPy fallback: rotating calipers over the hull's edge directions.
    best, best_deg = None, 0.0
    for deg in np.arange(0, 90, 0.1):
        r = math.radians(deg)
        u = xs * math.cos(r) + ys * math.sin(r)
        v = -xs * math.sin(r) + ys * math.cos(r)
        a = (u.max() - u.min()) * (v.max() - v.min())
        if best is None or a < best:
            best, best_deg = a, deg
    return float(best_deg)


def refine_angle(xs, ys, deg):
    """Polish OpenCV's angle against the trimmed extents (as the Kotlin sweep does)."""
    def area(d):
        r = math.radians(d)
        u = xs * math.cos(r) + ys * math.sin(r)
        v = -xs * math.sin(r) + ys * math.cos(r)
        return (u.max() - u.min()) * (v.max() - v.min())
    best, best_a = deg, area(deg)
    for span, step in ((3.0, 0.5), (0.5, 0.1)):
        base = best
        for d in np.arange(base - span, base + span + 1e-9, step):
            a = area(d)
            if a < best_a:
                best, best_a = d, a
    return best


def convex_hull(xs, ys):
    pts = np.stack([xs, ys], 1).astype(np.float32)
    if cv2 is not None and pts.shape[0] >= 3:
        hull = cv2.convexHull(pts)[:, 0, :]
        # OpenCV returns it clockwise in a y-down image, which is anticlockwise here.
        return [(float(a), float(b)) for a, b in hull]
    return []


def polygon_area(poly):
    if len(poly) < 3:
        return 0.0
    p = np.asarray(poly, np.float64)
    return float(abs(np.dot(p[:, 0], np.roll(p[:, 1], -1)) - np.dot(np.roll(p[:, 0], -1), p[:, 1])) / 2)


def _circle_once(x, y, h, h0, h1):
    n = x.size
    mx, my = x.mean(), y.mean()
    u, v = x - mx, y - my
    suu, svv, suv = (u * u).sum(), (v * v).sum(), (u * v).sum()
    det = suu * svv - suv * suv
    if abs(det) < 1e-9:
        return None
    bu = 0.5 * ((u ** 3).sum() + (u * v * v).sum())
    bv = 0.5 * ((v ** 3).sum() + (v * u * u).sum())
    cu = (bu * svv - bv * suv) / det
    cv = (suu * bv - suv * bu) / det
    r = math.sqrt(cu * cu + cv * cv + (suu + svv) / n)
    cx, cy = mx + cu, my + cv
    for _ in range(8):  # Gauss–Newton on the true distance
        dx, dy = x - cx, y - cy
        di = np.maximum(np.hypot(dx, dy), 1e-6)
        res = di - r
        J = np.stack([-dx / di, -dy / di, -np.ones(n)], 1)
        try:
            step = np.linalg.solve(J.T @ J, -J.T @ res)
        except np.linalg.LinAlgError:
            break
        cx += step[0]; cy += step[1]; r += step[2]
    if not (math.isfinite(r) and r > 0 and math.isfinite(cx) and math.isfinite(cy)):
        return None
    dist = np.hypot(x - cx, y - cy)
    ang = np.sort(np.arctan2(y - cy, x - cx))
    mh, md = h.mean(), dist.mean()
    shh = ((h - mh) ** 2).sum()
    slope = ((h - mh) * (dist - md)).sum() / shh if shh > 1e-6 else 0.0
    rms = math.sqrt((((dist - (md + slope * (h - mh))) ** 2).sum()) / n)
    gaps = np.diff(ang)
    max_gap = max(ang[0] + 2 * math.pi - ang[-1], gaps.max() if gaps.size else 0.0)
    arc = (2 * math.pi - max_gap) * 180 / math.pi
    mid = (h0 + h1) / 2
    return dict(cx=cx, cy=cy, r=md + slope * (mid - mh), rLow=md + slope * (h0 - mh), rHigh=md + slope * (h1 - mh),
                rms=rms, arc=arc, count=n)


def fit_circle(x, y, h, h0, h1):
    first = _circle_once(x, y, h, h0, h1)
    if first is None:
        return None
    d = np.hypot(x - first["cx"], y - first["cy"])
    expected = first["r"] + (first["rHigh"] - first["rLow"]) * ((h - (h0 + h1) / 2) / max(h1 - h0, 1.0))
    keep = np.abs(d - expected) <= max(3 * first["rms"], 6.0)
    if keep.sum() < x.size * MIN_INLIER_SHARE or keep.all():
        return first
    return _circle_once(x[keep], y[keep], h[keep], h0, h1) or first


def fit_round(xs, ys, hs, height, voxel):
    top = height - max(2 * voxel, 8.0)
    if top <= 2 * voxel:
        return None
    bands = int(min(max(top / BAND_MM, 2), 8))
    good = []
    for b in range(bands):
        h0, h1 = top * b / bands, top * (b + 1) / bands
        idx = (hs >= h0) & (hs < h1)
        if idx.sum() < 12:
            continue
        c = fit_circle(xs[idx], ys[idx], hs[idx], h0, h1)
        if c is None:
            return None
        steep = abs(c["rHigh"] - c["rLow"]) > STEEP_SLOPE * (h1 - h0)
        max_rms = max(2.5 + voxel * 0.3, 0.07 * c["r"])
        if c["r"] > 450 or (not steep and c["rms"] > max_rms):
            return None
        if steep or c["arc"] < MIN_ROUND_ARC_DEG or c["r"] < 8:
            continue
        good.append(((h0 + h1) / 2, c))
    if len(good) < 2:
        return None
    fitted = [c for _, c in good]
    w = np.array([c["count"] for c in fitted], np.float64)
    mean_x = float((np.array([c["cx"] for c in fitted]) * w).sum() / w.sum())
    mean_y = float((np.array([c["cy"] for c in fitted]) * w).sum() / w.sum())
    r_mid = max(c["r"] for c in fitted)
    if any(math.hypot(c["cx"] - mean_x, c["cy"] - mean_y) > max(6.0, 0.2 * r_mid) for c in fitted):
        return None
    if len(good) >= 3:
        hh = np.array([g[0] for g in good]); rr = np.array([g[1]["r"] ** 2 for g in good])
        qc, qb, qa = np.polyfit(hh, rr, 2)
        if qc < -SPHERE_CURVATURE:
            peak = math.sqrt(max(qa - qb * qb / (4 * qc), 0.0))
            r = max(peak, r_mid)
            return dict(family="SPHERE", cx=mean_x, cy=mean_y, rMax=r, rTop=fitted[-1]["r"], rBottom=fitted[0]["r"],
                        arc=max(c["arc"] for c in fitted))
    r_max = max(max(c["r"], min(max(c["rLow"], c["rHigh"]), c["r"] * 1.15)) for c in fitted)
    fam = "TAPERED" if abs(fitted[-1]["r"] - fitted[0]["r"]) / r_mid >= 0.10 else "CYLINDER"
    return dict(family=fam, cx=mean_x, cy=mean_y, rMax=r_max, rTop=max(fitted[-1]["rHigh"], 0.0),
                rBottom=max(fitted[0]["rLow"], 0.0), arc=max(c["arc"] for c in fitted))


def fit(x, y, h, weights=None, cameras=(), voxel=5.0):
    """The fitted object as a dict with the fields of Kotlin's FittedObject, or None."""
    xs = np.asarray(x, np.float64); ys = np.asarray(y, np.float64); hs = np.asarray(h, np.float64)
    n = xs.size
    if n < MIN_POINTS:
        return None
    wts = np.ones(n) if weights is None or len(weights) != n else np.asarray(weights, np.float64)
    cams = np.asarray(cameras, np.float64).reshape(-1, 3) if len(cameras) else np.zeros((0, 3))

    srt = np.sort(wts)
    cut = 0.3 * srt[int((n - 1) * 0.9)]
    strong = wts >= cut
    if strong.sum() < MIN_POINTS:
        strong[:] = True

    deg = refine_angle(xs[strong], ys[strong], min_area_rect(xs[strong], ys[strong]))
    r = math.radians(deg)
    c, s = math.cos(r), math.sin(r)
    us = xs * c + ys * s
    vs = -xs * s + ys * c
    u_min, u_max = robust_range(us, wts)
    v_min, v_max = robust_range(vs, wts)
    height = max(robust_range(hs, wts)[1], voxel)
    u_mid, v_mid = (u_min + u_max) / 2, (v_min + v_max) / 2
    rect_cx, rect_cy = u_mid * c - v_mid * s, u_mid * s + v_mid * c
    ext_u, ext_v = u_max - u_min, v_max - v_min

    tol = max(2 * voxel, 10.0)
    need = max(5, int(n * 0.03))
    min_cos = math.sin(math.radians(MIN_VIEW_ANGLE_DEG))

    def face_seen(normal, at, on_face):
        if on_face.sum() < need or cams.shape[0] == 0:
            return False
        d = cams - np.asarray(at)
        ln = np.linalg.norm(d, axis=1)
        ok = ln > 1
        return bool(np.any((d[ok] @ np.asarray(normal)) / ln[ok] >= min_cos))

    ux, uy, vx, vy = c, s, -s, c
    mid_h = height / 2
    top_seen = face_seen((0, 0, 1), (rect_cx, rect_cy, height), hs >= height - tol)
    minus_v = face_seen((-vx, -vy, 0), (rect_cx + vx * (v_min - v_mid), rect_cy + vy * (v_min - v_mid), mid_h), vs <= v_min + tol)
    plus_v = face_seen((vx, vy, 0), (rect_cx + vx * (v_max - v_mid), rect_cy + vy * (v_max - v_mid), mid_h), vs >= v_max - tol)
    minus_u = face_seen((-ux, -uy, 0), (rect_cx + ux * (u_min - u_mid), rect_cy + uy * (u_min - u_mid), mid_h), us <= u_min + tol)
    plus_u = face_seen((ux, uy, 0), (rect_cx + ux * (u_max - u_mid), rect_cy + uy * (u_max - u_mid), mid_h), us >= u_max - tol)
    u_obs = top_seen or minus_v or plus_v
    v_obs = top_seen or minus_u or plus_u
    h_obs = minus_v or plus_v or minus_u or plus_u

    rnd = fit_round(xs, ys, hs, height, voxel)
    hull = []
    top_r = bot_r = None
    if rnd is not None:
        shape = rnd["family"]
        cx, cy = rnd["cx"], rnd["cy"]
        w = d = 2 * rnd["rMax"]
        yaw = 0.0
        if shape == "SPHERE":
            height = max(height, w)
        top_r, bot_r = rnd["rTop"], rnd["rBottom"]
        if rnd["arc"] >= ROUND_OBSERVED_ARC_DEG or top_seen:
            u_obs = v_obs = True
        h_obs = True
    else:
        swap = ext_v > ext_u
        w, d = (ext_v, ext_u) if swap else (ext_u, ext_v)
        width_dir = deg + 90 if swap else deg
        yaw = ((width_dir % 180) + 180) % 180
        if swap:
            u_obs, v_obs = v_obs, u_obs
        on_box = ((hs >= height - tol) | (us <= u_min + tol) | (us >= u_max - tol) | (vs <= v_min + tol) | (vs >= v_max - tol)).sum()
        cand = convex_hull(xs, ys)
        fills = (not (u_obs and v_obs)) or ext_u * ext_v <= 1 or polygon_area(cand) / (ext_u * ext_v) >= BOX_HULL_FILL
        shape = "BOX" if on_box >= n * BOX_FACE_SHARE and fills else "IRREGULAR"
        cx, cy = rect_cx, rect_cy
        if shape == "IRREGULAR":
            hull = cand
        else:
            side = hs < height - tol

            def face(seen, vals, fallback, sel):
                if not seen or sel.sum() < need:
                    return fallback
                return weighted_median(vals[sel], wts[sel])

            u0 = face(minus_u, us, u_min, (us <= u_min + tol) & side)
            u1 = face(plus_u, us, u_max, (us >= u_max - tol) & side)
            v0 = face(minus_v, vs, v_min, (vs <= v_min + tol) & side)
            v1 = face(plus_v, vs, v_max, (vs >= v_max - tol) & side)
            height = face(top_seen, hs, height, hs >= height - tol)
            eu, ev = u1 - u0, v1 - v0
            w, d = (ev, eu) if swap else (eu, ev)
            um, vm = (u0 + u1) / 2, (v0 + v1) / 2
            cx, cy = um * c - vm * s, um * s + vm * c

    observed = [a for a, ok in (("WIDTH", u_obs), ("DEPTH", v_obs), ("HEIGHT", h_obs)) if ok]
    return dict(centreX=cx, centreY=cy, yaw=yaw, width=w, depth=d, height=height, shape=shape,
                topRadius=top_r, bottomRadius=bot_r, hull=hull, observed=observed, pointCount=int(n))


# ============================================================================================
# the JSON doors — one for Chaquopy, one for the test process


def select_json(text):
    p = json.loads(text)
    keep = select(p["x"], p["y"], p["h"], p.get("depth"), p["central"], p["edge"], p.get("spanPx", 0.0),
                  p.get("focalPx", 1.0), p.get("cell", CELL_MM), p.get("minShare", MIN_CENTRAL_SHARE),
                  p.get("minCentral", MIN_CENTRAL_SAMPLES), p.get("maxH", MAX_HEIGHT_MM), p.get("depthWindow", True),
                  p.get("inMask"))
    return json.dumps({"keep": keep})


def fit_json(text):
    p = json.loads(text)
    out = fit(p["x"], p["y"], p["h"], p.get("weights"), p.get("cameras", []), p.get("voxel", 5.0))
    return json.dumps({"fit": out})


def serve():
    """One JSON request per line on stdin, one JSON answer per line on stdout."""
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        req = json.loads(line)
        try:
            if req["op"] == "select":
                out = select_json(json.dumps(req))
            elif req["op"] == "fit":
                out = fit_json(json.dumps(req))
            elif req["op"] == "fitSpace":
                out = fit_space_json(json.dumps(req))
            elif req["op"] == "selectSpace":
                out = select_space_json(json.dumps(req))
            elif req["op"] == "ping":
                out = json.dumps({"ok": True, "opencv": cv2.__version__ if cv2 is not None else None})
            else:
                out = json.dumps({"error": "unknown op"})
        except Exception as e:  # the caller falls back to "nothing this frame"
            out = json.dumps({"error": repr(e)})
        sys.stdout.write(out + "\n")
        sys.stdout.flush()




# ============================================================================================
# the object's outline in the picture — the segmentation step


def segment(image, box, depth_mask=None, exclude=None, iterations=5):
    """
    The object's own pixels inside a detector box, as a 0/1 mask the size of the image.

    This is the segmentation step of the pipelines this engine follows — a detector's box, then a
    pixel-exact mask of the thing (what SAM or YOLO-seg give on a server), then its contour. On
    the phone it is OpenCV's GrabCut, prompted by the box the way SAM is: colour models of the
    thing and of what is round it split them along the real edge.

    [depth_mask] (0/1, the image's size) marks pixels ARCore's depth says stand up off the
    surface. Depth is coarse and a pixel or two off at edges, so it is only trusted where it is
    certain: well inside a raised region is the thing, well outside any is table or wall. The
    exact edge is left to the picture. [exclude] marks pixels already given to another object,
    so two neighbours never share an outline.
    """
    h, w = image.shape[:2]
    x0, y0, x1, y1 = [int(round(v)) for v in (box[0] * w, box[1] * h, box[2] * w, box[3] * h)]
    x0, y0 = max(x0, 0), max(y0, 0)
    x1, y1 = min(x1, w - 1), min(y1, h - 1)
    if x1 - x0 < 8 or y1 - y0 < 8:
        return None
    if cv2 is None:
        mask = np.zeros((h, w), np.uint8)
        mask[y0:y1, x0:x1] = 1
        if depth_mask is not None:
            mask &= depth_mask.astype(np.uint8)
        return mask
    gc = np.full((h, w), cv2.GC_BGD, np.uint8)
    gc[y0:y1, x0:x1] = cv2.GC_PR_FGD
    inside = np.zeros((h, w), bool)
    inside[y0:y1, x0:x1] = True
    if depth_mask is not None:
        dm = depth_mask.astype(np.uint8)
        k = max(3, int(round(min(w, h) / 90.0)) * 2 + 1)      # about one depth pixel
        sure_in = cv2.erode(dm, np.ones((k, k), np.uint8), iterations=2) > 0
        sure_out = cv2.dilate(dm, np.ones((k, k), np.uint8), iterations=2) == 0
        gc[inside & ~sure_out] = cv2.GC_PR_FGD
        gc[inside & sure_out] = cv2.GC_BGD
        # The raised region under the middle of the box is this thing; any other is a neighbour.
        n, lab = cv2.connectedComponents(sure_in.astype(np.uint8), connectivity=8)
        cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
        win = lab[max(cy - 3, 0):cy + 4, max(cx - 3, 0):cx + 4]
        ids = [i for i in np.unique(win) if i > 0]
        mine = np.isin(lab, ids) if ids else np.zeros_like(sure_in)
        gc[inside & mine] = cv2.GC_FGD
        gc[inside & sure_in & ~mine] = cv2.GC_PR_BGD
    else:
        mx0, mx1 = x0 + (x1 - x0) * 3 // 8, x1 - (x1 - x0) * 3 // 8
        my0, my1 = y0 + (y1 - y0) * 3 // 8, y1 - (y1 - y0) * 3 // 8
        gc[my0:my1, mx0:mx1] = cv2.GC_FGD
    if exclude is not None:
        gc[exclude.astype(bool)] = cv2.GC_BGD
    if not np.any((gc == cv2.GC_FGD) | (gc == cv2.GC_PR_FGD)):
        return None
    bgd, fgd = np.zeros((1, 65), np.float64), np.zeros((1, 65), np.float64)
    try:
        cv2.grabCut(image, gc, None, bgd, fgd, iterations, cv2.GC_INIT_WITH_MASK)
    except cv2.error:
        return None
    mask = ((gc == cv2.GC_FGD) | (gc == cv2.GC_PR_FGD)).astype(np.uint8)
    # A clean edge: close pin-holes, drop specks, keep the one piece holding the middle.
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    n, labels, stats, _ = cv2.connectedComponentsWithStats(mask, connectivity=8)
    if n <= 1:
        return None
    fg = (gc == cv2.GC_FGD)
    votes = np.bincount(labels[fg].ravel(), minlength=n) if fg.any() else np.zeros(n)
    votes[0] = -1
    target = int(np.argmax(votes)) if votes.max() > 0 else 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
    mask = (labels == target).astype(np.uint8)
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    filled = np.zeros_like(mask)
    cv2.drawContours(filled, contours, -1, 1, thickness=-1)
    return filled


def segment_all(image, boxes, depth_mask=None):
    """Every box's object, smallest box first, each kept off the pixels already given to another."""
    order = sorted(range(len(boxes)), key=lambda i: (boxes[i][2] - boxes[i][0]) * (boxes[i][3] - boxes[i][1]))
    taken = np.zeros(image.shape[:2], np.uint8)
    out = [None] * len(boxes)
    for i in order:
        m = segment(image, boxes[i], depth_mask, taken)
        if m is not None:
            out[i] = m
            taken |= m
    return out


def outline(mask):
    """The mask's outer contour, its PCA turn, and its tightest rotated rectangle (in pixels)."""
    if cv2 is None or mask is None:
        return None
    contours, _ = cv2.findContours(mask.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    if not contours:
        return None
    c = max(contours, key=cv2.contourArea)
    pts = c[:, 0, :].astype(np.float64)
    mean = pts.mean(0)
    cov = np.cov((pts - mean).T)
    evals, evecs = np.linalg.eigh(cov)
    major = evecs[:, int(np.argmax(evals))]
    pca_deg = math.degrees(math.atan2(major[1], major[0])) % 180
    (rx, ry), (rw, rh), rdeg = cv2.minAreaRect(c)
    return dict(contour=pts.tolist(), pcaDeg=pca_deg, rect=dict(cx=rx, cy=ry, w=rw, h=rh, deg=rdeg),
                areaPx=float(cv2.contourArea(c)))


def segment_json(text):
    """{image: [h][w][3] or flat bytes with width/height, box, depthMask?} → {mask as runs, outline}."""
    p = json.loads(text)
    img = np.asarray(p["pixels"], np.uint8).reshape(p["height"], p["width"], 3)
    dm = None
    if p.get("depthMask") is not None:
        dm = np.asarray(p["depthMask"], np.uint8).reshape(p["height"], p["width"])
    m = segment(img, p["box"], dm)
    return json.dumps({"mask": None if m is None else m.flatten().tolist(), "outline": outline(m)})


def segment_frame(rgb, width, height, boxes_json, grow_px=2):
    """
    For the phone: a small camera frame (RGB bytes, sensor orientation) and its detector boxes
    ([[l, t, r, b], …] in 0..1). Returns one byte per pixel: 0 for nothing, i + 1 for box i's
    object. Each mask is grown by [grow_px] so a depth pixel on the very edge still counts —
    depth and picture can sit a pixel apart.
    """
    img = np.frombuffer(bytes(rgb), np.uint8).reshape(height, width, 3)
    boxes = json.loads(boxes_json)
    masks = segment_all(img[:, :, ::-1].copy(), boxes)
    out = np.zeros((height, width), np.uint8)
    kernel = np.ones((2 * grow_px + 1, 2 * grow_px + 1), np.uint8)
    for i, m in enumerate(masks):
        if m is None:
            continue
        if cv2 is not None:
            m = cv2.dilate(m, kernel)
        out[(m > 0) & (out == 0)] = i + 1
    return out.tobytes()


# ============================================================================================
# spaces — a car boot, a carton, a cupboard, a room


FLOOR_BAND_MM = 25.0
MIN_WALL_POINTS = 60
MIN_FACE_POINTS = 12
SPACE_CELL_MM = 50.0
FACES = ("FLOOR", "LEFT", "RIGHT", "BACK", "FRONT", "TOP")


def _range(idx, v, w, trim=0.01):
    vals = v[idx]
    if vals.size == 0:
        return 0.0, 0.0
    order = np.argsort(vals, kind="stable")
    cum = np.cumsum(w[idx][order])
    t = cum[-1] * trim
    lo = vals[order[min(np.searchsorted(cum, t, side="right"), vals.size - 1)]]
    rcum = np.cumsum(w[idx][order][::-1])
    hi = vals[order[::-1][min(np.searchsorted(rcum, t, side="right"), vals.size - 1)]]
    return float(lo), float(hi)


def _densest(idx, v):
    """The middle of the most crowded 20 mm of values."""
    vals = np.sort(v[idx])
    if vals.size == 0:
        return 0.0
    j = np.searchsorted(vals, vals + 20.0, side="right") - np.arange(vals.size)
    k = int(np.argmax(j))
    return float(vals[k:k + j[k]].mean())


def _wmedian(idx, v, w):
    return weighted_median(v[idx], w[idx])


def fit_space(x, y, h, weights=None, cameras=()):
    """The inside of a space as a box, in its floor's frame: the fields of Kotlin's SpaceBox."""
    xs = np.asarray(x, np.float64); ys = np.asarray(y, np.float64); hs = np.asarray(h, np.float64)
    n = xs.size
    cams = np.asarray(cameras, np.float64).reshape(-1, 3)
    walls = np.nonzero(hs > FLOOR_BAND_MM)[0]
    if walls.size < MIN_WALL_POINTS or cams.shape[0] == 0:
        return None
    w = np.ones(n) if weights is None or len(weights) != n else np.asarray(weights, np.float64)

    # The turn of the space: OpenCV's tightest rotated rectangle round the walls, polished.
    deg = refine_angle(xs[walls], ys[walls], min_area_rect(xs[walls], ys[walls]))
    # Width runs across the person's line of sight; depth away from them.
    look = np.array([xs[walls].mean() - cams[:, 0].mean(), ys[walls].mean() - cams[:, 1].mean()])
    base = math.radians(deg)
    yaw = base
    if np.hypot(*look) > 50:
        best = -1e18
        for q in range(4):
            a = base + q * math.pi / 2
            dot = (-math.sin(a) * look[0] + math.cos(a) * look[1]) / np.hypot(*look)
            if dot > best:
                best, yaw = dot, a
    c, s = math.cos(yaw), math.sin(yaw)
    us = xs * c + ys * s
    vs = -xs * s + ys * c

    u_lo, u_hi = _range(walls, us, w)
    v_lo, v_hi = _range(walls, vs, w)
    band = max(40.0, 0.06 * max(u_hi - u_lo, v_hi - v_lo))
    wall_top = _range(walls, hs, w)[1]
    lower_half = wall_top * 0.5
    low = walls[hs[walls] <= lower_half]

    def side(vals, along, along_len, outer, inward, span):
        """Where one side stands, how many points it has (0 when open) and their spread."""
        found = []
        offset = 0.0
        while offset <= span * 0.35:
            a = outer + inward * offset
            d = (vals[low] - a) * inward
            lower = low[(d >= 0) & (d <= band)]
            if lower.size >= MIN_FACE_POINTS:
                peak = _densest(lower, vals)
                on = low[np.abs(vals[low] - peak) <= max(20.0, band / 4)]
                if on.size >= MIN_FACE_POINTS:
                    lo = along[on].min()
                    bins = np.clip(((along[on] - lo) / max(along_len, 1e-6) * 10).astype(int), 0, 9)
                    if np.unique(bins).size >= 6:
                        at = _wmedian(on, vals, w)
                        spread = float(np.median(np.abs(vals[on] - at)) * 1.4826)
                        if all(abs(f[0] - at) >= 5 for f in found):
                            found.append((at, int(on.size), spread))
            offset += band / 2
        if found:
            most = max(f[1] for f in found)
            ok = [f for f in found if f[1] >= most / 2]
            return min(ok, key=lambda f: (f[0] - outer) * inward)
        if low.size < MIN_FACE_POINTS:
            return (outer, 0, 0.0)
        # Open or unseen: bounded by the furthest thing standing along the middle of the side —
        # not its two ends, where a boot's tail-light pillars and a carton's corners stand
        # outside the space.
        lo, hi = _range(low, vals, w, trim=0.002)
        return (lo if inward > 0 else hi, 0, 0.0)

    left_s = side(us, vs, v_hi - v_lo, u_lo, 1.0, u_hi - u_lo)
    right_s = side(us, vs, v_hi - v_lo, u_hi, -1.0, u_hi - u_lo)
    back_s = side(vs, us, u_hi - u_lo, v_hi, -1.0, v_hi - v_lo)
    front_s = side(vs, us, u_hi - u_lo, v_lo, 1.0, v_hi - v_lo)
    left, right, back, front = left_s[0], right_s[0], back_s[0], front_s[0]
    width = max(right - left, 1.0)
    depth = max(back - front, 1.0)

    def top_of(sd, vals, along, lo, hi):
        if sd[1] == 0:
            return None
        ln = hi - lo
        tight = max(8.0, 2 * sd[2])
        sel = walls[(np.abs(vals[walls] - sd[0]) <= tight) & (along[walls] > lo + 0.1 * ln) & (along[walls] < hi - 0.1 * ln)]
        if sel.size < MIN_FACE_POINTS:
            return None
        # A flap folded out from the rim carries on from the wall with no gap, leaning away;
        # the band round the wall is kept tight so it is left within a few millimetres.
        tight2 = max(5.0, 1.5 * sd[2])
        hh = np.sort(hs[sel[np.abs(vals[sel] - sd[0]) <= tight2]])
        if hh.size < MIN_FACE_POINTS:
            return None
        gap = max(50.0, 0.08 * wall_top)
        top = hh[0]
        for v in hh:
            if v - top > gap:
                break
            top = v
        return float(top)

    tops = sorted(t for t in (top_of(left_s, us, vs, front, back), top_of(right_s, us, vs, front, back),
                              top_of(back_s, vs, us, left, right)) if t is not None)
    walls_top = wall_top if not tops else tops[len(tops) // 2]
    top_band = max(60.0, 0.1 * walls_top)
    hw = walls
    high = hw[(np.abs(hs[hw] - walls_top) <= top_band) & (us[hw] > left + band) & (us[hw] < right - band)
              & (vs[hw] > front + band) & (vs[hw] < back - band)]
    height = _wmedian(high, hs, w) if high.size >= MIN_FACE_POINTS * 3 else walls_top
    # Seen from low down, the near walls' tops are out of view and stop short; a ceiling seen
    # over the middle of the space is the height itself.
    over = hw[(hs[hw] > 0.6 * wall_top) & (us[hw] > left + 2 * band) & (us[hw] < right - 2 * band)
              & (vs[hw] > front + 2 * band) & (vs[hw] < back - 2 * band)]
    if over.size >= MIN_FACE_POINTS * 3:
        ceiling = _wmedian(over, hs, w)
        # Only a surface spread flat over the middle, not a wall's upper stretch.
        if np.std(hs[over]) < max(40.0, 0.04 * ceiling) and ceiling > height:
            height = ceiling

    um, vm = (left + right) / 2, (front + back) / 2
    cx, cy = um * c - vm * s, um * s + vm * c
    yaw_deg = (math.degrees(yaw) % 360 + 360) % 360

    cu = (cams[:, 0] - cx) * c + (cams[:, 1] - cy) * s
    cv = -(cams[:, 0] - cx) * s + (cams[:, 1] - cy) * c
    cam_inside = bool(np.any((np.abs(cu) < width / 2) & (np.abs(cv) < depth / 2) & (cams[:, 2] < height)))

    cell = max(40.0, max(width, depth, height) / 24)

    def cover(face):
        a_len, b_len = {"FLOOR": (width, depth), "TOP": (width, depth), "LEFT": (depth, height),
                        "RIGHT": (depth, height), "BACK": (width, height), "FRONT": (width, height)}[face]
        na, nb = max(int(math.ceil(a_len / cell)), 1), max(int(math.ceil(b_len / cell)), 1)
        u, v = us - left, vs - front
        if face == "FLOOR":
            a, b, near = u, v, np.abs(hs) <= FLOOR_BAND_MM
        elif face == "TOP":
            a, b, near = u, v, np.abs(hs - height) <= band
        elif face == "LEFT":
            a, b, near = v, hs, np.abs(us - left) <= band
        elif face == "RIGHT":
            a, b, near = v, hs, np.abs(us - right) <= band
        elif face == "BACK":
            a, b, near = u, hs, np.abs(vs - back) <= band
        else:
            a, b, near = u, hs, np.abs(vs - front) <= band
        ok = near & (a >= 0) & (b >= 0) & (a < a_len) & (b < b_len)
        if not ok.any():
            return 0.0
        ia = np.minimum(na - 1, np.floor(a[ok] / cell).astype(int))
        ib = np.minimum(nb - 1, np.floor(b[ok] / cell).astype(int))
        return float(np.unique(ia * nb + ib).size / (na * nb))

    coverage = {f: cover(f) for f in FACES}

    opening = None
    if not cam_inside:
        sill_pts = np.nonzero((np.abs(vs - front) <= band) & (hs > FLOOR_BAND_MM) & (hs < height * 0.6)
                              & (us > left + 2 * band) & (us < right - 2 * band))[0]
        sill = 0.0
        if sill_pts.size >= MIN_FACE_POINTS:
            sill_top = _range(sill_pts, hs, w)[1]
            sill = sill_top if sill_top < height * 0.5 else 0.0
        opening = [int(round(width)), int(round(max(height - sill, 0.0)))]

    return dict(centreX=cx, centreY=cy, yaw=yaw_deg, width=width, depth=depth, height=height,
                coverage=coverage, opening=opening, cameraInside=cam_inside, pointCount=int(n))


def select_space(x, y, h, central, cell=SPACE_CELL_MM, max_h=3000.0):
    """A space's walls: everything standing connected, the middle of the view not required."""
    return select(x, y, h, None, central, np.zeros(len(x), bool), 0.0, 1.0, cell, 0.0, 0, max_h, False)


def fit_space_json(text):
    p = json.loads(text)
    return json.dumps({"space": fit_space(p["x"], p["y"], p["h"], p.get("weights"), p.get("cameras", []))})


def select_space_json(text):
    p = json.loads(text)
    return json.dumps({"keep": select_space(p["x"], p["y"], p["h"], p["central"], p.get("cell", SPACE_CELL_MM), p.get("maxH", 3000.0))})


def trace_space(image, box, seed=None, iterations=5):
    """
    A space's inside in the picture — the boot's load floor and walls, the inside of a carton or
    a cupboard, a room's floor — traced from a prompt box round it and a point inside it, the
    way the object outlines are. What lies round it (the car's bumper and lights, a carton's
    flaps folded out, the wall a shelf hangs on) is left out, so the depth points on those never
    reach the fit. Returns the mask and its outline simplified to the space's corners.
    """
    if cv2 is None:
        return None
    h, w = image.shape[:2]
    x0, y0, x1, y1 = [int(round(v)) for v in (box[0] * w, box[1] * h, box[2] * w, box[3] * h)]
    gc = np.full((h, w), cv2.GC_BGD, np.uint8)
    gc[y0:y1, x0:x1] = cv2.GC_PR_FGD
    sx, sy = seed if seed is not None else ((box[0] + box[2]) / 2, (box[1] + box[3]) / 2)
    sx, sy = int(sx * w), int(sy * h)
    # The middle half of the prompt is the space for certain: a space fills its prompt far more
    # than an object does, and a small seed let a bright back wall be mistaken for outside.
    rx, ry = max(4, (x1 - x0) // 4), max(4, (y1 - y0) // 4)
    gc[max(sy - ry, 0):sy + ry, max(sx - rx, 0):sx + rx] = cv2.GC_FGD
    bgd, fgd = np.zeros((1, 65), np.float64), np.zeros((1, 65), np.float64)
    cv2.grabCut(image, gc, None, bgd, fgd, iterations, cv2.GC_INIT_WITH_MASK)
    mask = ((gc == cv2.GC_FGD) | (gc == cv2.GC_PR_FGD)).astype(np.uint8)
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, np.ones((7, 7), np.uint8))
    n, labels = cv2.connectedComponents(mask, connectivity=8)
    if n <= 1:
        return None
    mask = (labels == labels[sy, sx]).astype(np.uint8) if labels[sy, sx] else mask
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    c = max(contours, key=cv2.contourArea)
    hull = cv2.convexHull(c)
    corners = cv2.approxPolyDP(hull, 0.02 * cv2.arcLength(hull, True), True)[:, 0, :]
    quad = snap_quad(image, largest_quad(c[:, 0, :]))
    return dict(mask=mask, contour=c[:, 0, :], corners=corners, quad=quad, areaPx=float(cv2.contourArea(c)))


def largest_quad(contour):
    """The largest four-sided shape inside a traced outline, corners TL, TR, BR, BL."""
    from itertools import combinations
    hull = cv2.convexHull(np.asarray(contour, np.float32).reshape(-1, 1, 2))[:, 0, :]
    if len(hull) > 36:
        hull = hull[np.linspace(0, len(hull) - 1, 36).astype(int)]
    best, best_a = None, -1.0
    for idx in combinations(range(len(hull)), 4):
        q = hull[list(idx)]
        a = cv2.contourArea(q.reshape(-1, 1, 2))
        if a > best_a:
            best_a, best = a, q
    s = best[best[:, 1].argsort()]
    top = s[:2][s[:2, 0].argsort()]
    bot = s[2:][s[2:, 0].argsort()]
    return np.array([top[0], top[1], bot[1], bot[0]], np.float64)


def snap_quad(image, quad, reach=None):
    """
    Moves each side of a traced quad onto the strongest straight edge in the picture near it and
    running the same way (Canny edges, probabilistic Hough lines), then re-finds the corners where
    the sides meet. A space's sides are straight — a boot's lip, a shelf's frame, a room's skirting —
    so a side the colour trace cut short or let wander is put back on the real edge.
    """
    h, w = image.shape[:2]
    reach = reach or 0.06 * max(w, h)
    grey = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    edges = cv2.Canny(cv2.GaussianBlur(grey, (5, 5), 0), 40, 120)
    lines = cv2.HoughLinesP(edges, 1, np.pi / 360, 40, minLineLength=int(0.12 * min(w, h)), maxLineGap=12)
    if lines is None:
        return quad
    lines = np.asarray(lines).reshape(-1, 4).astype(np.float64)
    sides = []
    for i in range(4):
        a, b = quad[i], quad[(i + 1) % 4]
        d = b - a
        ang = math.atan2(d[1], d[0])
        n = np.array([-d[1], d[0]]) / (np.linalg.norm(d) + 1e-9)
        best, score = None, 0.0
        for x1, y1, x2, y2 in lines:
            la = math.atan2(y2 - y1, x2 - x1)
            dang = abs((la - ang + math.pi / 2) % math.pi - math.pi / 2)
            if dang > math.radians(10):
                continue
            mid = np.array([(x1 + x2) / 2, (y1 + y2) / 2])
            off = abs(np.dot(mid - a, n))
            along = np.dot(mid - a, d) / (np.dot(d, d) + 1e-9)
            if off > reach or along < -0.2 or along > 1.2:
                continue
            length = math.hypot(x2 - x1, y2 - y1)
            sc = length / (1.0 + off / 10.0)
            if sc > score:
                score, best = sc, (np.array([x1, y1]), np.array([x2, y2]))
        sides.append(best if best is not None else (a, b))

    def meet(l1, l2):
        p, r = l1[0], l1[1] - l1[0]
        q, s_ = l2[0], l2[1] - l2[0]
        den = r[0] * s_[1] - r[1] * s_[0]
        if abs(den) < 1e-9:
            return None
        t = ((q[0] - p[0]) * s_[1] - (q[1] - p[1]) * s_[0]) / den
        return p + t * r

    out = []
    for i in range(4):
        c = meet(sides[i - 1], sides[i])
        # Keep the traced corner if the snapped one flies off (near-parallel sides).
        out.append(c if c is not None and np.linalg.norm(c - quad[i]) < 2.5 * reach else quad[i])
    return np.array(out)


if __name__ == "__main__" and "--serve" in sys.argv:
    serve()
