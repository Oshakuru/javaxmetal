import Foundation
import Metal
import simd

func check(_ condition: @autoclosure () -> Bool, _ message: String) {
    if !condition() { fputs("FAIL: \(message)\n", stderr); exit(1) }
}
let device = MTLCreateSystemDefaultDevice()!
let queue = device.makeCommandQueue()!
let shader = try String(contentsOfFile: CommandLine.arguments[1], encoding: .utf8)
let library = try device.makeLibrary(source: shader, options: nil)
let size = 65
let descriptor = MTLRenderPipelineDescriptor()
descriptor.vertexFunction = library.makeFunction(name: "visual_vs")
descriptor.fragmentFunction = library.makeFunction(name: "visual_fs")
descriptor.colorAttachments[0].pixelFormat = .rgba8Unorm
let pipeline = try device.makeRenderPipelineState(descriptor: descriptor)
let depthDesc = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .depth32Float, width: size, height: size, mipmapped: false)
depthDesc.usage = [.renderTarget, .shaderRead]
let depth = device.makeTexture(descriptor: depthDesc)!
let textureDesc = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .rgba8Unorm, width: size, height: size, mipmapped: false)
textureDesc.storageMode = .shared
textureDesc.usage = [.renderTarget, .shaderRead]
let color = device.makeTexture(descriptor: textureDesc)!
let output = device.makeTexture(descriptor: textureDesc)!
var pixels = [UInt8](repeating: 0, count: size * size * 4)
for y in 0..<size { for x in 0..<size {
    let at = (y * size + x) * 4
    pixels[at] = UInt8(60 + x); pixels[at+1] = UInt8(70 + y); pixels[at+2] = 80; pixels[at+3] = 255
}}
pixels.withUnsafeBytes { color.replace(region: MTLRegionMake2D(0,0,size,size), mipmapLevel: 0, withBytes: $0.baseAddress!, bytesPerRow: size*4) }
var inv = matrix_identity_float4x4
inv.columns.3.z = -2.5
struct Uniforms {
    var inverse: simd_float4x4
    var projection: simd_float4x4
    var camera: SIMD4<Float>
    var settings: SIMD4<Float>
}
check(MemoryLayout<Uniforms>.stride == 160, "Java/MSL uniform stride")
let halfDesc = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .rgba16Float, width: (size+1)/2, height: (size+1)/2, mipmapped: false)
halfDesc.usage = [.renderTarget,.shaderRead]
let halfTexture = device.makeTexture(descriptor: halfDesc)!
let halfPipelineDesc = MTLRenderPipelineDescriptor()
halfPipelineDesc.vertexFunction = library.makeFunction(name: "visual_vs")
halfPipelineDesc.fragmentFunction = library.makeFunction(name: "visual_half_fs")
halfPipelineDesc.colorAttachments[0].pixelFormat = .rgba16Float
let halfPipeline = try device.makeRenderPipelineState(descriptor: halfPipelineDesc)
let compositeDesc = MTLRenderPipelineDescriptor()
compositeDesc.vertexFunction = library.makeFunction(name: "visual_vs")
compositeDesc.fragmentFunction = library.makeFunction(name: "visual_composite_fs")
compositeDesc.colorAttachments[0].pixelFormat = .rgba8Unorm
let compositePipeline = try device.makeRenderPipelineState(descriptor: compositeDesc)
func run(_ material: SIMD4<Float>, diagnostic: Bool = false, clearDepth: Double = 0.5, blocker: Bool = false, half: Bool = false, targetOnlyBlocker: Bool = false) -> [UInt8] {
    var values = [SIMD4<Float>](repeating: .zero, count: 24*24*24*7)
    for cell in 0..<(24*24*24) { values[cell*7+3] = material }
    if blocker {
        // Transparent/unsupported cell between camera and the opaque fixture plane.
        for y in 0..<24 { for x in 0..<24 { values[((y*24+11)*24+x)*7+6].w = -1 } }
    }
    if targetOnlyBlocker { values[((12*24+10)*24+11)*7+6].w = -1 }
    let grid = values.withUnsafeBytes { device.makeBuffer(bytes: $0.baseAddress!, length: $0.count)! }
    var uniforms = Uniforms(inverse: inv, projection: inv.inverse, camera: SIMD4(12,12,12,0), settings: SIMD4(diagnostic ? 1 : 0,0,0,0))
    if targetOnlyBlocker {
        uniforms.inverse.columns.3.x = 0.015
        uniforms.projection = uniforms.inverse.inverse
        uniforms.camera.x = 11.99
    }
    let command = queue.makeCommandBuffer()!
    let depthPass = MTLRenderPassDescriptor()
    depthPass.depthAttachment.texture = depth
    depthPass.depthAttachment.loadAction = .clear
    depthPass.depthAttachment.storeAction = .store
    depthPass.depthAttachment.clearDepth = clearDepth
    command.makeRenderCommandEncoder(descriptor: depthPass)!.endEncoding()
    func draw(_ state: MTLRenderPipelineState, _ target: MTLTexture, _ composite: Bool) {
        let pass = MTLRenderPassDescriptor()
        pass.colorAttachments[0].texture = target
        pass.colorAttachments[0].loadAction = .dontCare
        pass.colorAttachments[0].storeAction = .store
        let encoder = command.makeRenderCommandEncoder(descriptor: pass)!
        encoder.setRenderPipelineState(state)
        encoder.setFragmentTexture(color, index: 0)
        encoder.setFragmentTexture(composite ? halfTexture : depth, index: 1)
        if composite { encoder.setFragmentTexture(depth,index:2) }
        encoder.setFragmentBytes(&uniforms, length: 160, index: 0)
        encoder.setFragmentBuffer(grid, offset: 0, index: 1)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
        encoder.endEncoding()
    }
    if half { draw(halfPipeline,halfTexture,false); draw(compositePipeline,output,true) }
    else { draw(pipeline,output,false) }
    command.commit(); command.waitUntilCompleted()
    check(command.status == .completed, "GPU command failed: \(String(describing: command.error))")
    var result = [UInt8](repeating: 0, count: pixels.count)
    result.withUnsafeMutableBytes { output.getBytes($0.baseAddress!, bytesPerRow: size*4, from: MTLRegionMake2D(0,0,size,size), mipmapLevel: 0) }
    return result
}
let unknown = run(SIMD4(0,0,0,0))
check(unknown == pixels, "Unknown-material passthrough or world Y orientation failed")
check(run(SIMD4(0.25,1,0,1), clearDepth: 0) == pixels, "Sky passthrough failed")
check(run(SIMD4(0.25,1,0,1), blocker: true) == pixels, "Transparent foreground did not reject opaque background material")
let rough = run(SIMD4(0.9,0,0,1))
let emissive = run(SIMD4(0.65,0,2,1))
let metal = run(SIMD4(0.25,1,0,1), diagnostic: true)
let at = (32 * size + 32) * 4
check(rough[at] != pixels[at], "Mapped rough shading did not run")
check(emissive[at] > rough[at] + 15, "Emission not brighter than rough surface")
check(metal[at] == 255 && metal[at+1] > 190 && metal[at+2] < 30, "GPU material diagnostic lookup failed")
for image in [rough, emissive, metal] {
    for i in stride(from: 3, to: image.count, by: 4) { check(image[i] == 255, "Alpha changed") }
}
print("PASS: native Metal shader compilation, pipeline, GPU mapping, rough shading, emissive response, sky/unknown passthrough, pixel orientation and alpha on \(device.name)")

check(run(SIMD4(0,0,0,0), half:true) == pixels, "Half-resolution unknown passthrough changed full-resolution detail")
check(run(SIMD4(0.25,1,0,1),clearDepth:0,half:true) == pixels, "Half-resolution sky passthrough")
check(run(SIMD4(0.25,1,0,1),blocker:true,half:true) == pixels, "Half-resolution transparency fallback")
let halfEmission = run(SIMD4(0.65,0,2,1),half:true)
check(halfEmission[at] > pixels[at]+15 && halfEmission.last == 255, "Half-resolution emission or odd-size edge coverage failed")
print("PASS: half-resolution correction + full-resolution detail/sky/unknown/transparency/alpha at odd 65×65 dimensions")

// Execute the production SSR helper against a perspective ground plane and a visible wall.
let fixtureSource = shader + """
struct FixtureDepth { float value [[depth(any)]]; };
fragment FixtureDepth depth_fixture(VisualVertex in [[stage_in]], texture2d<float, access::read> values [[texture(0)]]) {
    return { values.read(uint2(in.position.xy)).r };
}
kernel void visibility_fixture(constant VisualUniforms& u [[buffer(0)]], device const float4* grid [[buffer(1)]],
    device const float4* rays [[buffer(2)]], device uint* result [[buffer(3)]], uint id [[thread_position_in_grid]]) {
    result[id] = blockedPath(rays[id].xyz, u, grid) ? 1 : 0;
}
kernel void reflection_fixture(texture2d<float, access::read> color [[texture(0)]],
    depth2d<float, access::read> depth [[texture(1)]], constant VisualUniforms& u [[buffer(0)]],
    device float4* result [[buffer(1)]]) {
    float3 p = positionAt(uint2(32, 16), depth, u);
    float confidence;
    float3 reflected = reflection(p, float3(0,1,0), 0.25, color, depth, u, confidence);
    result[0] = float4(reflected, confidence);
}
"""
let fixtureLibrary = try device.makeLibrary(source: fixtureSource, options: nil)
let fillDesc = MTLRenderPipelineDescriptor()
fillDesc.vertexFunction = fixtureLibrary.makeFunction(name: "visual_vs")
fillDesc.fragmentFunction = fixtureLibrary.makeFunction(name: "depth_fixture")
fillDesc.depthAttachmentPixelFormat = .depth32Float
let fillPipeline = try device.makeRenderPipelineState(descriptor: fillDesc)
let computePipeline = try device.makeComputePipelineState(function: fixtureLibrary.makeFunction(name: "reflection_fixture")!)
let zDesc = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .r32Float, width: size, height: size, mipmapped: false)
zDesc.storageMode = .shared; zDesc.usage = .shaderRead
let zValues = device.makeTexture(descriptor: zDesc)!
let depthStateDesc = MTLDepthStencilDescriptor()
depthStateDesc.depthCompareFunction = .always; depthStateDesc.isDepthWriteEnabled = true
let depthState = device.makeDepthStencilState(descriptor: depthStateDesc)!
let resultBuffer = device.makeBuffer(length: 16)!
var perspective = simd_float4x4(columns: (SIMD4(1,0,0,0),SIMD4(0,1,0,0),SIMD4(0,0,0,-1),SIMD4(0,0,0.1,0)))
var perspectiveUniforms = Uniforms(inverse: perspective.inverse, projection: perspective, camera: SIMD4(12,12,12,0), settings: .zero)
func traceFixture(wall: Bool) -> SIMD4<Float> {
    var z = [Float](repeating: 0, count: size*size)
    for y in 0..<size { for x in 0..<size {
        let ndcY = (Float(y)+0.5)/Float(size)*2-1
        // Ground y=-1; back wall z=-4. Both use reversed-Z depth.
        z[y*size+x] = max(ndcY < 0 ? -ndcY*0.1 : 0, wall ? 0.025 : 0)
    }}
    z.withUnsafeBytes { zValues.replace(region: MTLRegionMake2D(0,0,size,size), mipmapLevel: 0, withBytes: $0.baseAddress!, bytesPerRow: size*4) }
    let command = queue.makeCommandBuffer()!
    let pass = MTLRenderPassDescriptor()
    pass.depthAttachment.texture = depth; pass.depthAttachment.loadAction = .clear; pass.depthAttachment.storeAction = .store
    pass.depthAttachment.clearDepth = 0
    let fill = command.makeRenderCommandEncoder(descriptor: pass)!
    fill.setRenderPipelineState(fillPipeline); fill.setDepthStencilState(depthState)
    fill.setFragmentTexture(zValues, index: 0)
    fill.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3); fill.endEncoding()
    let compute = command.makeComputeCommandEncoder()!
    compute.setComputePipelineState(computePipeline)
    compute.setTexture(color, index: 0); compute.setTexture(depth, index: 1)
    compute.setBytes(&perspectiveUniforms, length: 160, index: 0)
    compute.setBuffer(resultBuffer, offset: 0, index: 1)
    compute.dispatchThreads(MTLSize(width:1,height:1,depth:1), threadsPerThreadgroup: MTLSize(width:1,height:1,depth:1))
    compute.endEncoding(); command.commit(); command.waitUntilCompleted()
    check(command.status == .completed, "SSR fixture GPU failure")
    return resultBuffer.contents().load(as: SIMD4<Float>.self)
}
let hit = traceFixture(wall: true)
let miss = traceFixture(wall: false)
check(hit.w > 0.1 && hit.x > 0, "SSR did not hit known visible wall: \(hit)")
check(miss.w == 0, "SSR offscreen/missing geometry did not fall back: \(miss)")
print("PASS: production screen-space reflection helper hits a known wall and rejects missing/offscreen geometry (confidence \(hit.w))")
let visibilityPipeline = try device.makeComputePipelineState(function: fixtureLibrary.makeFunction(name: "visibility_fixture")!)
let rays: [SIMD4<Float>] = [SIMD4(3,0,0,0),SIMD4(-3,0,0,0),SIMD4(0,3,0,0),SIMD4(0,0,-3,0),SIMD4(3,3,3,0),SIMD4(-3,-3,-3,0)]
let rayBuffer = rays.withUnsafeBytes { device.makeBuffer(bytes: $0.baseAddress!, length: $0.count)! }
let visibilityResult = device.makeBuffer(length: rays.count*4)!
func visibility(_ blocker: SIMD3<Int>?) -> [UInt32] {
    var values = [SIMD4<Float>](repeating: .zero, count: 24*24*24*7)
    if let b = blocker { values[((b.y*24+b.z)*24+b.x)*7+6].w = -1 }
    let grid = values.withUnsafeBytes { device.makeBuffer(bytes: $0.baseAddress!, length: $0.count)! }
    let command = queue.makeCommandBuffer()!
    let compute = command.makeComputeCommandEncoder()!
    compute.setComputePipelineState(visibilityPipeline)
    compute.setBytes(&perspectiveUniforms, length: 160, index: 0)
    compute.setBuffer(grid, offset: 0, index: 1); compute.setBuffer(rayBuffer, offset: 0, index: 2)
    compute.setBuffer(visibilityResult, offset: 0, index: 3)
    compute.dispatchThreads(MTLSize(width:rays.count,height:1,depth:1), threadsPerThreadgroup: MTLSize(width:1,height:1,depth:1))
    compute.endEncoding(); command.commit(); command.waitUntilCompleted()
    check(command.status == .completed, "DDA fixture GPU failure")
    return Array(UnsafeBufferPointer(start: visibilityResult.contents().assumingMemoryBound(to: UInt32.self), count: rays.count))
}
check(visibility(nil).allSatisfy { $0 == 0 }, "Clear axis/negative/diagonal rays rejected")
check(visibility(SIMD3(12,12,12)).allSatisfy { $0 == 1 }, "Camera in unsupported cell not rejected")
check(visibility(SIMD3(13,12,12))[0] == 1, "Intervening unsupported cell not rejected")
check(visibility(SIMD3(10,12,12))[1] == 1, "Negative axis blocker skipped")
check(visibility(SIMD3(13,13,13))[4] == 1, "Diagonal blocker skipped")
print("PASS: GPU conservative visibility at integer boundaries, axis-aligned/negative/diagonal rays, intervening blockers and camera-inside rejection")

// An offscreen world-space emitter must light a separate receiver and be blocked by occupancy.
func illuminate(_ emission: Float, blocker: Bool = false, half: Bool = false) -> [UInt8] {
    var values = [SIMD4<Float>](repeating: .zero, count: 24*24*24*7 + 16)
    for cell in 0..<(24*24*24) { for face in 0..<6 { values[cell*7+face] = SIMD4(0.9,0,0,1) } }
    if blocker { for z in 0..<24 { for x in 0..<24 { values[((12*24+z)*24+x)*7+6].w = 1 } } }
    values[96768] = SIMD4(13.5,13.5,11.5,1)
    values[96769] = SIMD4(emission,emission*0.075,emission*0.025,0)
    let grid = values.withUnsafeBytes { device.makeBuffer(bytes: $0.baseAddress!, length: $0.count)! }
    var u = perspectiveUniforms
    u.settings.y = emission > 0 ? 1 : 0
    let command = queue.makeCommandBuffer()!
    func draw(_ state:MTLRenderPipelineState,_ target:MTLTexture,_ composite:Bool) {
        let pass = MTLRenderPassDescriptor()
        pass.colorAttachments[0].texture = target; pass.colorAttachments[0].loadAction = .dontCare
        pass.colorAttachments[0].storeAction = .store
        let encoder = command.makeRenderCommandEncoder(descriptor: pass)!
        encoder.setRenderPipelineState(state)
        encoder.setFragmentTexture(color, index: 0); encoder.setFragmentTexture(composite ? halfTexture : depth, index: 1)
        if composite { encoder.setFragmentTexture(depth,index:2) }
        encoder.setFragmentBytes(&u, length: 160, index: 0)
        encoder.setFragmentBuffer(grid, offset: 0, index: 1)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3); encoder.endEncoding()
    }
    if half { draw(halfPipeline,halfTexture,false); draw(compositePipeline,output,true) }
    else { draw(pipeline,output,false) }
    command.commit(); command.waitUntilCompleted()
    check(command.status == .completed, "Local-light GPU failure")
    var image = [UInt8](repeating: 0, count: pixels.count)
    image.withUnsafeMutableBytes { output.getBytes($0.baseAddress!, bytesPerRow: size*4, from: MTLRegionMake2D(0,0,size,size), mipmapLevel: 0) }
    return image
}
_ = traceFixture(wall: true)
let unlit = illuminate(0), lit = illuminate(2), occluded = illuminate(2, blocker: true)
// Choose a receiver away from the horizon, where the representative depth is within the compositor's tolerance.
let groundPixel = (2*size+32)*4
check(lit[groundPixel] > unlit[groundPixel] + 8, "Offscreen world emitter failed to light ground receiver")
check(lit[groundPixel] - unlit[groundPixel] > lit[groundPixel+1] - unlit[groundPixel+1], "Local-light color lost")
check(occluded[groundPixel] == unlit[groundPixel], "Light leaked through opaque wall")
print("PASS: offscreen colored local light affects separate receiver (\(unlit[groundPixel]) → \(lit[groundPixel])) and opaque blocker restores \(occluded[groundPixel])")

let halfUnlit = illuminate(0,half:true), halfLit = illuminate(2,half:true), halfBlocked = illuminate(2,blocker:true,half:true)
check(halfLit[groundPixel] > halfUnlit[groundPixel]+8, "Optimized path lost offscreen lighting")
check(halfBlocked[groundPixel] == halfUnlit[groundPixel], "Optimized local light leaked through blocker")
let horizonPixel = (16*size+32)*4
check(halfLit[horizonPixel] == pixels[horizonPixel], "Depth-discontinuous half sample was not rejected")
print("PASS: optimized two-pass path retains offscreen colored lighting/occlusion and rejects a depth-discontinuous sample")

let edgeBlocked = run(SIMD4(0.65,0,2,1),half:true,targetOnlyBlocker:true)
let clearRepresentative = (33*size+33)*4
check(edgeBlocked[at] == pixels[at] && edgeBlocked[at+1] == pixels[at+1], "Half correction leaked onto target-only transparent foreground")
check(edgeBlocked[clearRepresentative] > pixels[clearRepresentative]+15, "Target-only blocker fixture also blocked its representative sample")
print("PASS: half composite rejects a target-only transparency ray while its same-block/depth representative remains shaded")
