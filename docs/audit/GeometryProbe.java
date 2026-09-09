import com.packabunch.packing.Box;
import com.packabunch.packing.Dimensions;
import com.packabunch.packing.ItemShape;

/** Standalone audit reproducer; run against packing.jar and kotlin-stdlib. */
class GeometryProbe {
    public static void main(String[] args) {
        // Mask material occupies x=[10,20), y=[0,10), z=[0,10).
        var mask = new ItemShape.VoxelMask(10, 2, 1, 1, new boolean[]{false, true});
        var maskBox = new Box(0, 0, 0, 20, 10, 10);
        // Cuboid occupies x=[5,15): a real 5 mm overlap with the mask.
        var cube = new ItemShape.Cuboid(new Dimensions(10, 10, 10));
        var cubeBox = new Box(5, 0, 0, 10, 10, 10);
        boolean actual = ItemShape.Companion.collide(mask, maskBox, cube, cubeBox);
        System.out.println("Off-grid overlap: expected=true, actual=" + actual);
        if (!actual) throw new AssertionError("Collision missed: occupied volumes overlap by 5 mm");
    }
}
