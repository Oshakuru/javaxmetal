import com.metallum.diagnostic.CameraSnapshot;
import org.joml.*;
import java.nio.FloatBuffer;

public class CameraSnapshotCheck {
    public static void main(String[] args) {
        CameraSnapshot snapshot = new CameraSnapshot();
        Object world = new Object();
        Matrix4f destination = new Matrix4f();
        if (snapshot.copyInverse(world, 1440, 900, destination)) throw new AssertionError("Uncaptured frame accepted");
        Matrix4f projection = new Matrix4f().zero().m00(1.2f).m11(1.8f).m23(-1).m32(.05f);
        Matrix4f view = new Matrix4f().rotationY(.4f);
        Matrix4f effects = new Matrix4f().translate(.03f, -.07f, 0).rotateZ(.1f)
                .rotate(.2f, 0, .70710677f, .70710677f).scale(.85f, 1, 1)
                .rotate(-.2f, 0, .70710677f, .70710677f);
        Matrix4f effective = new Matrix4f(projection).mul(effects);
        Matrix4f rendered = new Matrix4f(effective).mul(view);
        Vector3f point = new Vector3f(1, -2, -8);
        Vector3f clip = rendered.transformProject(new Vector3f(point));
        Vector3f oldResult = new Matrix4f(projection).mul(view).invert().transformProject(new Vector3f(clip));
        if (oldResult.distance(point) < .1f) throw new AssertionError("Fixture does not expose old projection bug");
        snapshot.capture(world, 1440, 900, effective, view);
        if (!snapshot.copyInverse(world, 1440, 900, destination)) throw new AssertionError("Capture unavailable");
        Vector3f restored = destination.transformProject(new Vector3f(clip));
        if (restored.distance(point) > .0001f) throw new AssertionError("Effective projection reconstruction failed");
        Vector3f[] plane = {new Vector3f(0,-2,-8), new Vector3f(1,-2,-8), new Vector3f(0,-2,-9)};
        Vector3f[] reconstructed = new Vector3f[3];
        for (int i = 0; i < plane.length; i++) {
            Vector3f ndc = rendered.transformProject(new Vector3f(plane[i]));
            // Native depth texture Y after SPIRV-Cross's vertex Y flip maps to (ndc.y + 1)/2.
            float u = (ndc.x + 1) * .5f, v = (ndc.y + 1) * .5f;
            reconstructed[i] = destination.transformProject(new Vector3f(u * 2 - 1, v * 2 - 1, ndc.z));
        }
        Vector3f normal = new Vector3f(reconstructed[1]).sub(reconstructed[0])
                .cross(new Vector3f(reconstructed[2]).sub(reconstructed[0])).normalize();
        if (normal.distance(new Vector3f(0,1,0)) > .0001f)
            throw new AssertionError("Upward plane changed direction under camera effects");
        effective.identity(); view.identity();
        if (!snapshot.copyInverse(world, 1440, 900, destination)
                || destination.transformProject(new Vector3f(clip)).distance(point) > .0001f)
            throw new AssertionError("Captured matrices were not copied");
        FloatBuffer packed = java.nio.ByteBuffer.allocateDirect(80)
                .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
        CameraSnapshot.putMatrix(destination, packed);
        Matrix4f unpacked = new Matrix4f(packed);
        if (!unpacked.equals(destination, 1e-6f)) throw new AssertionError("Metal column-major packing changed matrix");
        if (snapshot.copyInverse(new Object(), 1440, 900, destination)
                || snapshot.copyInverse(world, 900, 1440, destination)) throw new AssertionError("Wrong world/size accepted");
        snapshot.beginFrame();
        if (snapshot.copyInverse(world, 1440, 900, destination)) throw new AssertionError("Previous frame leaked");
        snapshot.capture(world, 1440, 900, new Matrix4f().zero(), new Matrix4f());
        if (snapshot.copyInverse(world, 1440, 900, destination)) throw new AssertionError("Singular matrix accepted");
        System.out.println("PASS: effective projection regression, snapshot ownership, frame invalidation, resize/world rejection, and Metal matrix packing");
    }
}
