import org.joml.*;
public class MetallumProjectionCheck {
  public static void main(String[] args) {
    // A reversed-Z, zero-to-one perspective matrix, tested with rotated cameras.
    for (float yaw : new float[]{0f, 0.4f, -0.7f}) {
      Matrix4f projection = new Matrix4f().zero()
          .m00(1.2f).m11(1.8f).m23(-1f).m32(0.05f);
      Matrix4f vp = new Matrix4f(projection).mul(new Matrix4f().rotationY(yaw));
      Matrix4f inverse = new Matrix4f(vp).invert();
      // Horizontal plane below camera; project and reconstruct three points.
      Vector3f[] points = {new Vector3f(0,-2,-8), new Vector3f(1,-2,-8), new Vector3f(0,-2,-9)};
      Vector3f[] restored = new Vector3f[3];
      for (int i=0; i<3; i++) {
        Vector4f clip = vp.transform(new Vector4f(points[i],1));
        clip.div(clip.w);
        Vector4f world = inverse.transform(new Vector4f(clip.x,clip.y,clip.z,1));
        world.div(world.w);
        restored[i] = new Vector3f(world.x,world.y,world.z);
        if (restored[i].distance(points[i]) > 0.0001f) throw new AssertionError("projection round trip");
      }
      Vector3f n = new Vector3f(restored[1]).sub(restored[0]).cross(new Vector3f(restored[2]).sub(restored[0])).normalize();
      if (n.distance(new Vector3f(0,1,0)) > 0.0001f) throw new AssertionError("world normal changed with camera");
    }
    System.out.println("PASS: reversed-Z projection round trip and horizontal world normals at three camera rotations");
  }
}
