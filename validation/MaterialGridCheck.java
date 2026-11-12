import com.metallum.visual.MaterialGrid;

public class MaterialGridCheck {
    public static void main(String[] args) {
        boolean[] used = new boolean[MaterialGrid.CELLS];
        for (int y = 0; y < 24; y++) for (int z = 0; z < 24; z++) for (int x = 0; x < 24; x++) {
            int cell = MaterialGrid.cell(x, y, z);
            check(!used[cell], "cell collision");
            used[cell] = true;
            check(cell == (y * 24 + z) * 24 + x, "MSL layout mismatch");
        }
        check(MaterialGrid.cell(-1, 0, 0) == -1 && MaterialGrid.cell(24, 0, 0) == -1, "bounds");
        check(MaterialGrid.cell(0, 24, 0) == -1 && MaterialGrid.cell(0, 0, 24) == -1, "bounds");
        float[][] normals = {{0,-1,0}, {0,1,0}, {0,0,-1}, {0,0,1}, {-1,0,0}, {1,0,0}};
        for (int face = 0; face < 6; face++) {
            float[] n = normals[face];
            check(MaterialGrid.face(n[0], n[1], n[2]) == face, "direction order");
            for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
                int axis = face < 2 ? y : face < 4 ? z : x;
                check(MaterialGrid.corner(x,y,z,face) == (axis == face % 2), "boundary plane");
            }
        }
        check(MaterialGrid.face(0.707f,0.707f,0) == -1, "sloped face must fall back");
        check(!MaterialGrid.corner(0.5f,1,0,1), "partial quad must fall back");
        check(!MaterialGrid.corner(Float.NaN,1,0,1), "invalid quad must fall back");
        check(MaterialGrid.GRID_BYTES == MaterialGrid.CELLS * 112, "buffer stride");
        check(MaterialGrid.BYTES == MaterialGrid.GRID_BYTES + 8 * 32, "light buffer offset");
        System.out.println("PASS: material grid bounds, unique addresses, GPU face ordering and cube-face rejection");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
