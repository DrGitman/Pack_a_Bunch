import importlib.util
from pathlib import Path
import unittest
import numpy as np

spec = importlib.util.spec_from_file_location("engine", Path(__file__).parents[2] / "main/python/packscan.py")
engine = importlib.util.module_from_spec(spec)
spec.loader.exec_module(engine)


class PhoneEngineRegression(unittest.TestCase):
    def setUp(self):
        self.backend = engine.cv2
        engine.cv2 = None  # The backend actually shipped to Android.
    def tearDown(self):
        engine.cv2 = self.backend

    def test_irregular_hull_without_opencv(self):
        x = np.array([0, 100, 100, 40, 0, 25])
        y = np.array([0, 0, 30, 90, 60, 25])
        hull = engine.convex_hull(x, y)
        self.assertAlmostEqual(engine.polygon_area(hull), 6600, delta=1)

    def test_colour_trace_excludes_background_inside_detector_box(self):
        image = np.full((96, 96, 3), 190, np.uint8)
        yy, xx = np.mgrid[:96, :96]
        truth = (xx-48)**2 + (yy-48)**2 < 25**2
        image[truth] = [25, 80, 220]
        result = engine.segment(image, [0.15, 0.15, 0.85, 0.85])
        self.assertIsNotNone(result)
        iou = np.count_nonzero(result.astype(bool) & truth) / np.count_nonzero(result.astype(bool) | truth)
        self.assertGreater(iou, .93)

    def test_dilation_does_not_wrap_opposite_edges(self):
        mask = np.zeros((8, 8), bool)
        mask[0, 0] = True
        grown = engine._dilate(mask)
        self.assertFalse(grown[-1].any())
        self.assertFalse(grown[:, -1].any())

    def test_mask_miss_is_not_silently_replaced_by_entire_box(self):
        x,y=np.meshgrid(np.arange(6)*10, np.arange(6)*10)
        n=x.size
        keep=engine.select(x.ravel(), y.ravel(), np.full(n,80), None,
                           np.ones(n,bool),np.zeros(n,bool),100,100,
                           in_mask=np.zeros(n,bool))
        self.assertEqual(keep, [])

    def test_outlier_does_not_rotate_item_footprint(self):
        x,y=np.meshgrid(np.linspace(-200,200,45),np.linspace(-100,100,25))
        points=np.column_stack((x.ravel(),y.ravel(),np.full(x.size,150.)))
        points=np.vstack((points, [1400,1200,150]))
        fit=engine.fit(*points.T, cameras=[[0,-800,700]], voxel=5)
        self.assertLess(abs(fit["width"]-400),12)
        self.assertLess(abs(fit["depth"]-200),12)


    def test_noisy_rotated_items_and_spaces(self):
        rng = np.random.default_rng(725)
        for width, depth, height in [(400,200,150),(1000,800,600),(2800,3600,2600)]:
            axis = np.linspace(0,1,26)
            a,b = np.meshgrid(axis,axis)
            a,b = a.ravel(),b.ravel()
            walls = []
            for side in (0,1):
                walls.extend([
                    np.column_stack((np.full(a.size,side*width),a*depth,b*height)),
                    np.column_stack((a*width,np.full(a.size,side*depth),b*height)),
                ])
            floor=np.column_stack((a*width,b*depth,np.zeros(a.size)))
            top=np.column_stack((a*width,b*depth,np.full(a.size,height)))
            for angle in (0,23,67):
                with self.subTest(size=(width,depth,height),angle=angle):
                    p=np.vstack(walls+[floor,top])
                    p=p[rng.random(len(p)) > .2]
                    p += rng.normal(0,2,p.shape)
                    r=np.deg2rad(angle)
                    rotation=np.array([[np.cos(r),-np.sin(r)],[np.sin(r),np.cos(r)]])
                    p[:,:2] = p[:,:2] @ rotation.T
                    camera=np.array([[width/2,-2*depth,height*1.5]])
                    camera[:,:2]=camera[:,:2] @ rotation.T
                    fit=engine.fit_space(*p.T,cameras=camera)
                    self.assertIsNotNone(fit)
                    for key,target in zip(("width","depth","height"),(width,depth,height)):
                        self.assertLess(abs(fit[key]-target),max(20,target*.02),(key,fit))
                    if width == 400:
                        item=engine.fit(*p[p[:,2]>6].T,cameras=camera)
                        for key,target in zip(("width","depth","height"),(width,depth,height)):
                            self.assertLess(abs(item[key]-target),12,(key,item))

    def test_nonfinite_depth_samples_do_not_poison_fit(self):
        x,y=np.meshgrid(np.linspace(-200,200,30),np.linspace(-100,100,20))
        p=np.column_stack((x.ravel(),y.ravel(),np.full(x.size,150.)))
        p=np.vstack((p,[np.nan,0,100],[0,np.inf,100]))
        fit=engine.fit(*p.T,cameras=[[0,-800,700]])
        self.assertTrue(np.isfinite(fit["width"]))
        self.assertAlmostEqual(fit["width"],400,delta=12)

    def test_phone_rgb_entry_point_returns_labels_and_respects_exclusion(self):
        import json
        image = np.full((96,96,3),190,np.uint8)
        image[25:70,25:70] = [220,80,25]
        data = engine.segment_frame(image.tobytes(),96,96,json.dumps([[.1,.1,.9,.9],[.2,.2,.8,.8]]),grow_px=0)
        labels=np.frombuffer(data,np.uint8).reshape(96,96)
        self.assertEqual(labels[40,40],2)
        self.assertEqual(labels[15,15],0)

    def test_ambiguous_colour_is_not_a_fake_outline(self):
        image=np.full((96,96,3),100,np.uint8)
        self.assertIsNone(engine.segment(image,[.1,.1,.9,.9]))

    def test_degenerate_quad_is_rejected(self):
        self.assertIsNone(engine.largest_quad(np.array([[0,0],[1,1],[2,2]])))

    def test_json_worker_recovers_after_bad_request(self):
        import subprocess,sys,json
        engine_path=Path(__file__).parents[2] / "main/python/packscan.py"
        result=subprocess.run([sys.executable,str(engine_path),"--serve"],
            input="broken json"+chr(10)+'{"op":"ping"}'+chr(10),
            capture_output=True,text=True,timeout=15)
        lines=result.stdout.splitlines()
        self.assertEqual(len(lines),2,result.stderr)
        self.assertIn("error",json.loads(lines[0]))
        self.assertTrue(json.loads(lines[1])["ok"])

if __name__ == "__main__":
    unittest.main(verbosity=2)





