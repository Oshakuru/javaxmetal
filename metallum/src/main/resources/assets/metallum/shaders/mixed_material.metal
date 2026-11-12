#include <metal_stdlib>
using namespace metal;

struct VisualUniforms {
    float4x4 inverseViewProjection;
    float4x4 viewProjection;
    float4 cameraInGrid;
    float4 settings; // material diagnostic, local light count, unused, unused
};
struct VisualVertex { float4 position [[position]]; };
vertex VisualVertex visual_vs(uint id [[vertex_id]]) {
    const float2 p[3] = {float2(-1, 1), float2(3, 1), float2(-1, -3)};
    return {float4(p[id], 0, 1)};
}
float3 positionAt(uint2 pixel, depth2d<float, access::read> depth, constant VisualUniforms& u) {
    float2 uv = (float2(pixel) + 0.5) / float2(depth.get_width(), depth.get_height());
    float4 p = u.inverseViewProjection * float4(uv * 2 - 1, depth.read(pixel), 1);
    return p.xyz / p.w;
}
float3 normalAt(uint2 pixel, depth2d<float, access::read> depth, constant VisualUniforms& u) {
    uint2 size(depth.get_width(), depth.get_height());
    uint2 l(pixel.x > 0 ? pixel.x - 1 : 0, pixel.y), r(min(pixel.x + 1, size.x - 1), pixel.y);
    uint2 b(pixel.x, pixel.y > 0 ? pixel.y - 1 : 0), t(pixel.x, min(pixel.y + 1, size.y - 1));
    float d = depth.read(pixel);
    float3 p = positionAt(pixel, depth, u);
    float3 dx = abs(depth.read(l) - d) < abs(depth.read(r) - d)
        ? p - positionAt(l, depth, u) : positionAt(r, depth, u) - p;
    float3 dy = abs(depth.read(b) - d) < abs(depth.read(t) - d)
        ? p - positionAt(b, depth, u) : positionAt(t, depth, u) - p;
    float3 n = cross(dx, dy);
    if (!all(isfinite(n)) || dot(n, n) < 1e-16) return float3(0);
    n = normalize(n);
    return dot(n, -p) < 0 ? -n : n;
}
int faceOf(float3 n) {
    if (abs(n.y) > 0.995) return n.y > 0 ? 1 : 0;
    if (abs(n.z) > 0.995) return n.z > 0 ? 3 : 2;
    if (abs(n.x) > 0.995) return n.x > 0 ? 5 : 4;
    return -1;
}
bool gridBlocked(float3 start, float3 finish, bool opaque, device const float4* grid) {
    float3 delta = finish - start;
    if (!all(isfinite(delta)) || !all(isfinite(start))) return true;
    if (dot(delta, delta) < 1e-10) return false;
    int3 cell = int3(floor(start)), end = int3(floor(finish));
    int3 step = int3(sign(delta));
    float3 stride = 1.0 / max(abs(delta), float3(1e-8));
    float3 boundary = float3(cell) + select(float3(0), float3(1), delta > 0);
    float3 next = abs(boundary - start) * stride;
    for (int i = 0; i < 72; i++) {
        if (any(cell < 0) || any(cell >= 24)) return true;
        if (opaque && all(cell == end)) return false; // emitter cell is the destination, not an occluder
        float occupied = grid[((cell.y * 24 + cell.z) * 24 + cell.x) * 7 + 6].w;
        if (opaque ? occupied != 0 : occupied < 0) return true;
        if (all(cell == end)) return false;
        int axis = next.x < next.y ? (next.x < next.z ? 0 : 2) : (next.y < next.z ? 1 : 2);
        cell[axis] += step[axis];
        next[axis] += stride[axis];
    }
    return true;
}
bool blockedPath(float3 p, constant VisualUniforms& u, device const float4* grid) {
    return gridBlocked(u.cameraInGrid.xyz, u.cameraInGrid.xyz + p - normalize(p) * 0.05, false, grid);
}
float4 materialAt(float3 p, float3 n, constant VisualUniforms& u, device const float4* grid) {
    int face = faceOf(n);
    if (face < 0 || !all(isfinite(p))) return float4(0);
    float3 g = p + u.cameraInGrid.xyz;
    int axis = face < 2 ? 1 : (face < 4 ? 2 : 0);
    if (abs(g[axis] - round(g[axis])) > 0.035) return float4(0);
    int3 cell = int3(floor(g - n * 0.04));
    if (any(cell < 0) || any(cell >= 24)) return float4(0);
    float4 m = grid[((cell.y * 24 + cell.z) * 24 + cell.x) * 7 + face];
    // A blended world color may contain glass/water while depth still identifies its opaque background.
    if (m.w > 0 && blockedPath(p, u, grid)) return float4(0);
    return m;
}
float3 linearColor(float3 c) { return pow(max(c, 0.0), float3(2.2)); }
float3 reflection(float3 p, float3 n, float roughness, texture2d<float, access::read> color,
        depth2d<float, access::read> depth, constant VisualUniforms& u, thread float& confidence) {
    float3 ray = reflect(normalize(p), n);
    float3 start = p + n * 0.07;
    float previous = -1, previousDistance = 0;
    uint2 size(color.get_width(), color.get_height());
    for (int step = 1; step <= 24; step++) {
        float distance = 0.12 * step + 0.018 * step * step;
        float3 q = start + ray * distance;
        float4 clip = u.viewProjection * float4(q, 1);
        if (clip.w <= 0) break;
        float2 uv = clip.xy / clip.w * 0.5 + 0.5;
        if (any(uv < 0.005) || any(uv > 0.995)) break;
        uint2 pixel = min(uint2(uv * float2(size)), size - 1);
        float d = depth.read(pixel);
        if (d <= 1e-7) { previous = -1; previousDistance = distance; continue; }
        float3 surface = positionAt(pixel, depth, u);
        float delta = length(q) - length(surface);
        if (previous < 0 && delta >= 0) {
            float low = previousDistance, high = distance;
            // Refine the crossing so the fixed march does not jump through thin geometry.
            for (int refine = 0; refine < 5; refine++) {
                float middle = (low + high) * 0.5;
                float3 point = start + ray * middle;
                float4 projected = u.viewProjection * float4(point, 1);
                float2 middleUV = projected.xy / projected.w * 0.5 + 0.5;
                uint2 sample = min(uint2(clamp(middleUV, 0.0, 1.0) * float2(size)), size - 1);
                float difference = depth.read(sample) <= 1e-7 ? -1 : length(point) - length(positionAt(sample, depth, u));
                if (difference >= 0) high = middle; else low = middle;
            }
            q = start + ray * high;
            clip = u.viewProjection * float4(q, 1);
            uv = clip.xy / clip.w * 0.5 + 0.5;
            pixel = min(uint2(clamp(uv, 0.0, 1.0) * float2(size)), size - 1);
            surface = positionAt(pixel, depth, u);
            delta = length(q) - length(surface);
            if (depth.read(pixel) <= 1e-7 || delta < 0 || delta > 0.18 + high * 0.025
                    || length(surface - p) <= 0.25) {
                previous = delta;
                previousDistance = distance;
                continue;
            }
            float edge = min(min(uv.x, 1 - uv.x), min(uv.y, 1 - uv.y));
            confidence = smoothstep(0.005, 0.12, edge) * (1 - distance / 14.0);
            int radius = int(roughness * 5);
            int2 a = clamp(int2(pixel) + int2(radius, 0), int2(0), int2(size) - 1);
            int2 b = clamp(int2(pixel) - int2(radius, 0), int2(0), int2(size) - 1);
            int2 c = clamp(int2(pixel) + int2(0, radius), int2(0), int2(size) - 1);
            int2 d = clamp(int2(pixel) - int2(0, radius), int2(0), int2(size) - 1);
            return (linearColor(color.read(pixel).rgb) * 2 + linearColor(color.read(uint2(a)).rgb)
                + linearColor(color.read(uint2(b)).rgb) + linearColor(color.read(uint2(c)).rgb)
                + linearColor(color.read(uint2(d)).rgb)) / 6;
        }
        previous = delta;
        previousDistance = distance;
    }
    confidence = 0;
    return float3(0);
}
float3 surfaceBRDF(float3 albedo, float3 n, float3 view, float3 light, float roughness, float metalness) {
    float nl = max(dot(n, light), 0.0), nv = max(dot(n, view), 0.001);
    if (nl <= 0) return float3(0);
    float3 halfDirection = view + light;
    if (dot(halfDirection, halfDirection) < 1e-8) return float3(0);
    halfDirection = normalize(halfDirection);
    float nh = max(dot(n, halfDirection), 0.0), vh = max(dot(view, halfDirection), 0.0);
    float a = max(roughness * roughness, 0.04), a2 = a * a;
    float denominator = nh * nh * (a2 - 1) + 1;
    float distribution = a2 / max(M_PI_F * denominator * denominator, 1e-5);
    float k = (roughness + 1) * (roughness + 1) * 0.125;
    float visibility = (nv / (nv * (1 - k) + k)) * (nl / (nl * (1 - k) + k));
    float3 f0 = mix(float3(0.04), albedo, metalness);
    float3 fresnel = f0 + (1 - f0) * pow(1 - vh, 5.0);
    float3 specular = min(float3(12), distribution * visibility * fresnel / max(4 * nv * nl, 0.001));
    float3 diffuse = albedo * (1 - metalness) * (1 - fresnel) * (1 - 0.25 * roughness * roughness) / M_PI_F;
    return (diffuse + specular) * nl;
}
float3 localIllumination(float3 p, float3 n, float3 view, float3 albedo, float roughness, float metalness,
        constant VisualUniforms& u, device const float4* grid) {
    float3 g = p + u.cameraInGrid.xyz, radiance(0);
    int count = clamp(int(u.settings.y), 0, 8);
    for (int i = 0; i < count; i++) {
        float3 emitter = grid[96768 + i * 2].xyz;
        float3 delta = emitter - g;
        float distance2 = dot(delta, delta);
        if (distance2 < 0.4 || distance2 >= 36) continue;
        float3 light = delta * rsqrt(distance2);
        if (dot(n, light) <= 0) continue;
        if (gridBlocked(g + n * 0.06, emitter, true, grid)) continue;
        float fade = 1 - distance2 / 36;
        float3 source = grid[96768 + i * 2 + 1].rgb;
        radiance += surfaceBRDF(albedo, n, view, light, roughness, metalness)
            * source * (6 * fade * fade / (1 + distance2));
    }
    return radiance;
}
float4 shadeWorld(uint2 pixel, texture2d<float, access::read> color, depth2d<float, access::read> depth,
        constant VisualUniforms& u, device const float4* grid) {
    float4 original = color.read(pixel);
    if (depth.read(pixel) <= 1e-7) return original;
    float3 p = positionAt(pixel, depth, u), n = normalAt(pixel, depth, u);
    float4 m = materialAt(p, n, u, grid);
    if (u.settings.x > 0.5) {
        if (m.w < 0.5) return float4(original.rgb * 0.25, original.a);
        return float4(m.z > 0 ? float3(1, 0.25, 0.05)
            : m.y > 0.5 ? float3(1, 0.8, 0.1) : float3(0.1, 1 - m.x * 0.6, m.x), original.a);
    }
    if (m.w < 0.5) return original;
    float3 base = linearColor(original.rgb);
    float3 view = normalize(-p), light = normalize(float3(-0.4, 0.8, 0.3));
    float facing = max(dot(n, light), 0.0);
    float roughness = clamp(m.x, 0.08, 1.0);
    float3 f0 = mix(float3(0.04), base, m.y);
    float3 fresnel = f0 + (1 - f0) * pow(1 - max(dot(n, view), 0.0), 5.0);
    // shortcut: the scene is already lit; this modest unshadowed highlight is not a replacement sun/shadow pass.
    float3 result = base * (0.94 + 0.06 * facing * (1 - 0.25 * roughness));
    result += surfaceBRDF(base, n, view, light, roughness, m.y) * 0.12;
    // Estimate albedo for additive lighting without multiplying shadow darkness a second time.
    float peak = max(max(base.r, base.g), base.b);
    float3 albedo = base / max(peak, 0.18) * 0.5;
    result += localIllumination(p, n, view, albedo, roughness, m.y, u, grid);
    if (roughness < 0.65) {
        float confidence;
        float3 reflected = reflection(p, n, roughness, color, depth, u, confidence);
        float3 weight = fresnel * (1 - roughness) * confidence * 0.7;
        result = result * (1 - weight) + reflected * weight;
    }
    result += base * m.z * 0.65;
    uint2 size(color.get_width(), color.get_height());
    const int2 taps[4] = {int2(1,0), int2(-1,0), int2(0,1), int2(0,-1)};
    int radius = max(1, int(size.y * (12.0 / 900.0)));
    // Screen glow is presentation only; colored local illumination above uses the voxel light list.
    for (int i = 0; i < 4; i++) {
        uint2 q = uint2(clamp(int2(pixel) + taps[i] * radius, int2(0), int2(size) - 1));
        if (depth.read(q) <= 1e-7) continue;
        float3 ep = positionAt(q, depth, u);
        if (length(ep - p) > 2) continue;
        float4 em = materialAt(ep, normalAt(q, depth, u), u, grid);
        if (em.w > 0 && em.z > 0) result += linearColor(color.read(q).rgb) * em.z * 0.015;
    }
    float peakResult = max(max(result.r, result.g), result.b);
    result /= 1 + max(peakResult - 0.8, 0.0);
    float3 g = p + u.cameraInGrid.xyz;
    float edge = min(min(min(g.x, g.y), g.z), min(min(24 - g.x, 24 - g.y), 24 - g.z));
    float3 shaded = pow(max(result, 0.0), float3(1.0 / 2.2));
    return float4(mix(original.rgb, shaded, smoothstep(0.5, 2.0, edge)), original.a);
}

fragment float4 visual_fs(VisualVertex in [[stage_in]],
        texture2d<float, access::read> color [[texture(0)]], depth2d<float, access::read> depth [[texture(1)]],
        constant VisualUniforms& u [[buffer(0)]], device const float4* grid [[buffer(1)]]) {
    return shadeWorld(uint2(in.position.xy), color, depth, u, grid);
}
fragment float4 visual_half_fs(VisualVertex in [[stage_in]],
        texture2d<float, access::read> color [[texture(0)]], depth2d<float, access::read> depth [[texture(1)]],
        constant VisualUniforms& u [[buffer(0)]], device const float4* grid [[buffer(1)]]) {
    uint2 size(color.get_width(), color.get_height());
    uint2 pixel = min(uint2(in.position.xy) * 2 + 1, size - 1);
    float4 original = color.read(pixel);
    return float4(shadeWorld(pixel, color, depth, u, grid).rgb - original.rgb, 0);
}
int surfaceAxis(float3 g) {
    float3 d = abs(g - round(g));
    return d.x < d.y ? (d.x < d.z ? 0 : 2) : (d.y < d.z ? 1 : 2);
}
fragment float4 visual_composite_fs(VisualVertex in [[stage_in]],
        texture2d<float, access::read> color [[texture(0)]], texture2d<float, access::read> correction [[texture(1)]],
        depth2d<float, access::read> depth [[texture(2)]], constant VisualUniforms& u [[buffer(0)]], device const float4* grid [[buffer(1)]]) {
    uint2 pixel = uint2(in.position.xy), size(color.get_width(), color.get_height());
    float4 original = color.read(pixel);
    float d = depth.read(pixel);
    uint2 low = pixel / 2, sample = min(low * 2 + 1, size - 1);
    float3 change = correction.read(low).rgb;
    if (all(abs(change) < 1e-6)) return original;
    float sourceDepth = depth.read(sample);
    if (d <= 1e-7 || sourceDepth <= 1e-7 || abs(sourceDepth - d) > d * 0.035) return original;
    float3 p = positionAt(pixel, depth, u), q = positionAt(sample, depth, u);
    // Each target ray must remain eligible, even if its 2×2 representative ray missed thin transparency.
    if (materialAt(p, normalAt(pixel, depth, u), u, grid).w < 0.5) return original;
    float3 pg = p + u.cameraInGrid.xyz, qg = q + u.cameraInGrid.xyz;
    // Keep original full-resolution texture detail and reject corrections across block/surface boundaries.
    if (any(int3(floor(pg + normalize(p) * 0.03)) != int3(floor(qg + normalize(q) * 0.03)))
            || surfaceAxis(pg) != surfaceAxis(qg)) return original;
    return float4(clamp(original.rgb + change, 0.0, 1.0), original.a);
}
