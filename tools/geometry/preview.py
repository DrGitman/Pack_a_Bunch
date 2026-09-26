"""Contact sheet of every family, drawn the way IsometricCrate draws them (normal shading, culling)."""
import json, glob, math, os, sys
import matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Polygon

BASE = (0xA6/255, 0x5C/255, 0x34/255)
def lighten(c, a): return tuple(x + (1-x)*a for x in c)
def darken(c, a): return tuple(x*(1-a) for x in c)
ct, st = math.cos(0.5236), math.sin(0.5236)
def proj(x, y, z, W, D):  # same as CrateView.rawProject with yaw 0, then flip for matplotlib
    rx, ry = x - W/2, y - D/2
    return ((rx - ry)*ct, -((rx + ry)*st - z))
def depth(x, y, z): return x + y + z   # CrateView.depth with yaw 0 (x*1 - 0 + 0 + y + z)

def newell(p):
    n=[0,0,0]
    for i in range(len(p)):
        a,b=p[i],p[(i+1)%len(p)]
        n[0]+=(a[1]-b[1])*(a[2]+b[2]); n[1]+=(a[2]-b[2])*(a[0]+b[0]); n[2]+=(a[0]-b[0])*(a[1]+b[1])
    l=math.sqrt(sum(v*v for v in n)) or 1
    return [v/l for v in n]

# Usage: python3 preview.py [json_dir]  (default: ../../app/src/main/res/raw next to this script)
HERE = os.path.dirname(os.path.abspath(__file__))
SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "..", "app", "src", "main", "res", "raw")
files = sorted(glob.glob(os.path.join(SRC, "family_*.json")))
dims = {  # realistic sizes (mm) so proportions read as they will in the app
 "flat_rectangle":(240,170,30),"slim_slab":(160,75,9),"small_carton":(300,200,180),"upright_cylinder":(70,70,120),
 "lying_cylinder":(300,120,120),"bottle":(80,80,300),"tight_roll":(450,220,220),"soft_pouch":(250,90,140),
 "cable_coil":(300,300,60),"thin_bundle":(250,60,30),"shallow_tray":(400,300,40),"suitcase":(450,250,650),
 "duffel_bag":(600,300,300),"backpack":(320,220,480),"cooler_box":(500,350,380),"folded_chair":(450,80,800),
 "yoga_mat":(610,150,150),"toolbox":(450,220,220),"ball":(220,220,220),"crate":(500,350,300),
 "appliance_slab":(600,600,850),"upright_fridge":(600,650,1800),"mattress":(1900,1400,250),"plank_stack":(1800,150,100),
 "ladder":(2400,400,120),"barrel":(600,600,880),"bicycle":(1750,600,1050),"lawnmower":(1400,550,1000),
 "sofa":(2000,900,850),"armchair":(850,850,900),"dining_table":(1600,900,750),"chair":(450,500,900),
 "wardrobe":(1200,600,2000),"bed_frame":(1500,2100,1000),"lamp":(400,400,1500),"tv_stand":(1200,400,1100),
 "plant_pot":(300,300,280),"piano":(1500,600,1250)}
cols = 8; rows = math.ceil(len(files)/cols)
fig, axes = plt.subplots(rows, cols, figsize=(cols*2.4, rows*2.6), facecolor="#F7EFE6")
wrong = []
for ax, f in zip(axes.flat, files):
    d = json.load(open(f)); name = d["family"]; W, D, H = dims.get(name, (1000,1000,1000))
    faces = [[(p[0]/1000*W, p[1]/1000*D, p[2]/1000*H) for p in fc["points"]] for fc in d["faces"]]
    view = (1, 1, 1)  # towards the viewer, gradient of depth()
    shown = []
    for fc in faces:
        # normal in canonical space, then scaled by inverse dims (normals transform with inverse-transpose)
        n0 = newell([(p[0]/W, p[1]/D, p[2]/H) for p in fc])
        n = [n0[0]/W, n0[1]/D, n0[2]/H]; l = math.sqrt(sum(v*v for v in n)) or 1; n = [v/l for v in n]
        if sum(n[i]*view[i] for i in range(3)) <= 0: continue
        lit = 0.12 + 0.5*max(n[2],0) + 0.22*n[0]*n[0]
        shown.append((sum(depth(*p) for p in fc)/4, fc, lit))
    shown.sort(key=lambda t: t[0])
    pts=[proj(*p, W, D) for fc in faces for p in fc]
    for _, fc, lit in shown:
        poly=[proj(*p, W, D) for p in fc]
        ax.add_patch(Polygon(poly, closed=True, facecolor=lighten(BASE, lit), edgecolor=darken(BASE,0.28)+(0.45,), linewidth=0.6))
    xs=[p[0] for p in pts]; ys=[p[1] for p in pts]
    ax.set_xlim(min(xs), max(xs)); ax.set_ylim(min(ys), max(ys)); ax.set_aspect("equal"); ax.axis("off")
    ax.set_title(name.replace("_"," "), fontsize=9, color="#2B1D14")
for ax in list(axes.flat)[len(files):]: ax.axis("off")
plt.tight_layout(); plt.savefig(os.path.join(HERE, "families_preview.png"), dpi=110, facecolor="#F7EFE6")
print("ok")
