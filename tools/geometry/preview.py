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

def part_order(boxes, view, centre_depth):
    """boxes: {part: (lo[3], hi[3])}. Parts clearly apart along an axis are painted far side first."""
    eps = 1e-6
    keys = list(boxes)
    def behind(a, b):
        (alo, ahi), (blo, bhi) = boxes[a], boxes[b]
        for i in range(3):
            if view[i] > 0 and ahi[i] <= blo[i] + eps: return True
            if view[i] < 0 and alo[i] >= bhi[i] - eps: return True
        return False
    after = {k: set() for k in keys}; indeg = {k: 0 for k in keys}
    for a in keys:
        for b in keys:
            if a != b and behind(a, b) and not behind(b, a):
                after[a].add(b); indeg[b] += 1
    out = []; ready = sorted([k for k in keys if indeg[k] == 0], key=centre_depth)
    while ready:
        k = ready.pop(0); out.append(k)
        for b in after[k]:
            indeg[b] -= 1
            if indeg[b] == 0: ready.append(b)
        ready.sort(key=centre_depth)
    rest = sorted([k for k in keys if k not in out], key=centre_depth)   # a cycle: fall back
    return out + rest


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
 "lying_cylinder":(300,120,120),"bottle":(80,80,300),"tight_roll":(450,220,220),"soft_pouch":(250,90,146),
 "cable_coil":(300,300,60),"thin_bundle":(250,54,29),"shallow_tray":(400,300,40),"suitcase":(450,250,650),
 "duffel_bag":(600,300,300),"backpack":(320,220,480),"cooler_box":(500,350,380),"folded_chair":(432,64,805),
 "yoga_mat":(610,150,150),"toolbox":(458,236,267),"ball":(220,220,220),"crate":(500,350,300),
 "appliance_slab":(600,604,850),"upright_fridge":(600,650,1800),"mattress":(1900,1400,250),"plank_stack":(1800,150,100),
 "ladder":(2400,400,120),"barrel":(600,600,880),"bicycle":(1765,480,912),"lawnmower":(1395,630,995),
 "sofa":(2000,900,850),"armchair":(850,850,900),"dining_table":(1600,900,750),"chair":(450,500,900),
 "wardrobe":(1200,600,2000),"bed_frame":(1500,2100,1000),"lamp":(400,400,1500),"tv_stand":(1200,400,1100),
 "plant_pot":(300,300,280),"piano":(1500,600,1250),
 "kettle":(244,168,247),"coffee_maker":(250,260,360),"microwave":(480,360,280),"toaster":(300,180,200),
 "cooker":(600,600,900),"cooking_pot":(380,220,180),"frying_pan":(480,280,60),"plate_stack":(270,270,71),
 "bowl":(200,200,90),"pillow":(700,450,150),"folded_stack":(350,300,200),"bookcase":(800,300,1800),
 "chest_of_drawers":(800,450,900),"desk":(1200,600,750),"stool":(400,400,650),"ottoman":(800,450,420),
 "clothes_rail":(1192,450,1610),"framed_panel":(600,900,40),"disc":(300,300,50),"monitor":(600,200,450),
 "printer":(450,380,250),"helmet":(250,300,200),"shoe":(300,116,127),"tote_bag":(400,150,431),
 "guitar":(1000,380,110),"upright_vacuum":(330,300,1162),"long_handle":(1080,300,120),
 "watering_can":(468,170,352),"power_drill":(250,90,240),"hand_tool":(302,144,35),
 "mug":(112,82,95),"wine_glass":(76,76,212),"vase":(150,150,296),"teapot":(225,160,162),"blender":(180,168,360),"stand_mixer":(330,220,370),"air_fryer":(290,350,344),"knife_block":(120,220,291),"dish_rack":(450,320,258),"laundry_basket":(600,400,410),"bucket":(309,300,423),"pedal_bin":(304,338,462),"clothes_iron":(260,104,172),"ironing_board":(1220,380,82),"pedestal_fan":(449,320,1274),"oil_heater":(366,150,690),"desk_lamp":(445,180,387),"speaker":(180,231,300),"camera":(158,182,124),"headphones":(194,90,164),"alarm_clock":(132,52,157),"book_stack":(250,177,120),"potted_plant":(401,410,598),"umbrella":(932,76,130),"storage_bin":(652,412,350),"boot":(290,105,420),"hair_dryer":(232,90,225),"skateboard":(800,210,122),"kick_scooter":(770,280,836),"tennis_racket":(702,282,28),"golf_bag":(285,247,1137),"snowboard":(1480,290,139),"lantern":(160,160,313),"gas_cylinder":(320,320,667),"stroller":(320,214,941),"child_car_seat":(440,480,720),"high_chair":(607,607,906),"office_chair":(623,595,1080),"bean_bag":(720,720,610),"side_table":(450,450,555),"coffee_table":(1100,600,440),"filing_cabinet":(470,628,1320),"robot_vacuum":(350,350,104),"dumbbell":(360,120,120),"kettlebell":(220,220,286),"sewing_machine":(436,190,320),"paint_can":(199,190,250),"wheelbarrow":(1514,560,651),"bbq_grill":(584,584,1033),"pet_carrier":(330,489,371),"dog_bed":(820,820,200),"aquarium":(608,308,440)}
cols = 8; rows = math.ceil(len(files)/cols)
fig, axes = plt.subplots(rows, cols, figsize=(cols*2.4, rows*2.6), facecolor="#F7EFE6")
wrong = []
for ax, f in zip(axes.flat, files):
    d = json.load(open(f)); name = d["family"]; W, D, H = dims.get(name, (1000,1000,1000))
    faces = [[(p[0]/1000*W, p[1]/1000*D, p[2]/1000*H) for p in fc["points"]] for fc in d["faces"]]
    smooth = [fc.get("smooth", False) for fc in d["faces"]]
    parts = [fc.get("part", 0) for fc in d["faces"]]
    # Parts are painted as wholes in occlusion order, then faces within each — as IsometricCrate does.
    boxes, depths = {}, {}
    for fc, pt in zip(faces, parts):
        lo, hi = boxes.setdefault(pt, ([9e9]*3, [-9e9]*3))
        for q in fc:
            depths.setdefault(pt, []).append(depth(*q))
            for i in range(3): lo[i] = min(lo[i], q[i]); hi[i] = max(hi[i], q[i])
    rank = {k: i for i, k in enumerate(part_order(boxes, (1, 1, 1), lambda k: sum(depths[k]) / len(depths[k])))}
    view = (1, 1, 1)  # towards the viewer, gradient of depth()
    shown = []
    for fc, sm, pt in zip(faces, smooth, parts):
        # normal in canonical space, then scaled by inverse dims (normals transform with inverse-transpose)
        n0 = newell([(p[0]/W, p[1]/D, p[2]/H) for p in fc])
        n = [n0[0]/W, n0[1]/D, n0[2]/H]; l = math.sqrt(sum(v*v for v in n)) or 1; n = [v/l for v in n]
        if sum(n[i]*view[i] for i in range(3)) <= 0: continue
        lit = 0.12 + 0.5*max(n[2],0) + 0.22*n[0]*n[0]
        shown.append(((rank[pt], sum(depth(*p) for p in fc)/4), fc, lit, sm))
    shown.sort(key=lambda t: t[0])
    pts=[proj(*p, W, D) for fc in faces for p in fc]
    for _, fc, lit, sm in shown:
        poly=[proj(*p, W, D) for p in fc]
        # Smooth facets are edged in their own colour, as IsometricCrate draws them.
        edge = lighten(BASE, lit) if sm else darken(BASE,0.28)+(0.45,)
        ax.add_patch(Polygon(poly, closed=True, facecolor=lighten(BASE, lit), edgecolor=edge, linewidth=0.6))
    xs=[p[0] for p in pts]; ys=[p[1] for p in pts]
    ax.set_xlim(min(xs), max(xs)); ax.set_ylim(min(ys), max(ys)); ax.set_aspect("equal"); ax.axis("off")
    ax.set_title(name.replace("_"," "), fontsize=9, color="#2B1D14")
for ax in list(axes.flat)[len(files):]: ax.axis("off")
plt.tight_layout(); plt.savefig(os.path.join(HERE, "families_preview.png"), dpi=110, facecolor="#F7EFE6")
print("ok")
