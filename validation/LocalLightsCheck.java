import com.metallum.visual.LocalLights;
import com.metallum.visual.MaterialGrid;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;

public class LocalLightsCheck {
    public static void main(String[] args) {
        ByteBuffer data = ByteBuffer.allocate(MaterialGrid.BYTES).order(ByteOrder.nativeOrder());
        var expected = new ArrayList<Integer>();
        for (int x = 0; x < 24; x++) {
            int cell = MaterialGrid.cell(x, 12, 12);
            data.putFloat(cell * MaterialGrid.STRIDE + 96, x + 1);
            data.putFloat(cell * MaterialGrid.STRIDE + 100, 0.25f);
            data.putFloat(cell * MaterialGrid.STRIDE + 104, 0.75f);
            expected.add(cell);
        }
        expected.sort(Comparator.<Integer>comparingDouble(cell -> {
            double dx = cell % 24 + 0.5 - 12;
            return dx * dx + 0.5;
        }).thenComparingInt(cell -> cell));
        LocalLights lights = new LocalLights();
        check(lights.write(data, 12,12,12) == 8, "bounded light count");
        for (int i = 0; i < 8; i++) {
            int at = MaterialGrid.GRID_BYTES + i * 32;
            int x = expected.get(i) % 24;
            check(data.getFloat(at) == x + 0.5f, "nearest order/tie stability");
            check(data.getFloat(at+4) == 12.5f && data.getFloat(at+8) == 12.5f, "XYZ packing");
            check(data.getFloat(at+16) == x + 1 && data.getFloat(at+20) == 0.25f && data.getFloat(at+24) == 0.75f, "RGB packing");
        }
        for (int x = 0; x < 24; x++) {
            int at = MaterialGrid.cell(x,12,12) * MaterialGrid.STRIDE + 96;
            for (int c = 0; c < 3; c++) data.putFloat(at+c*4,0);
        }
        check(lights.write(data,12,12,12) == 0, "removed lights retained");
        for (int at = MaterialGrid.GRID_BYTES; at < data.capacity(); at += 4) check(data.getFloat(at) == 0, "stale trailing light data");
        System.out.println("PASS: nearest light budget/order, deterministic ties, GPU XYZ/RGB packing and removed-light clearing");
    }
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
