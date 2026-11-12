import Foundation
import Metal
import simd

func require(_ ok: @autoclosure () -> Bool, _ message: String) {
    if !ok() { fputs("FAIL: \(message)\n", stderr); exit(1) }
}
let device = MTLCreateSystemDefaultDevice()!
let queue = device.makeCommandQueue()!
let width = 1440, height = 900
let source = try String(contentsOfFile: CommandLine.arguments[1], encoding: .utf8)
let library = try device.makeLibrary(source: source + """
struct BenchmarkDepth { float value [[depth(any)]]; };
fragment BenchmarkDepth benchmark_depth(VisualVertex in [[stage_in]], texture2d<float, access::read> z [[texture(0)]]) {
    return {z.read(uint2(in.position.xy)).r};
}
""", options: nil)
let desc = MTLRenderPipelineDescriptor()
desc.vertexFunction = library.makeFunction(name: "visual_vs")
desc.fragmentFunction = library.makeFunction(name: "visual_fs")
desc.colorAttachments[0].pixelFormat = .rgba8Unorm
let pipeline = try device.makeRenderPipelineState(descriptor: desc)
let halfPD = MTLRenderPipelineDescriptor()
halfPD.vertexFunction = library.makeFunction(name:"visual_vs")
halfPD.fragmentFunction = library.makeFunction(name:"visual_half_fs")
halfPD.colorAttachments[0].pixelFormat = .rgba16Float
let halfPipeline = try device.makeRenderPipelineState(descriptor:halfPD)
let compositePD = MTLRenderPipelineDescriptor()
compositePD.vertexFunction = library.makeFunction(name:"visual_vs")
compositePD.fragmentFunction = library.makeFunction(name:"visual_composite_fs")
compositePD.colorAttachments[0].pixelFormat = .rgba8Unorm
let compositePipeline = try device.makeRenderPipelineState(descriptor:compositePD)
let halfTD = MTLTextureDescriptor.texture2DDescriptor(pixelFormat:.rgba16Float,width:(width+1)/2,height:(height+1)/2,mipmapped:false)
halfTD.usage = [.renderTarget,.shaderRead]
let halfTexture = device.makeTexture(descriptor:halfTD)!
let dd = MTLRenderPipelineDescriptor()
dd.vertexFunction = library.makeFunction(name: "visual_vs")
dd.fragmentFunction = library.makeFunction(name: "benchmark_depth")
dd.depthAttachmentPixelFormat = .depth32Float
let depthPipeline = try device.makeRenderPipelineState(descriptor: dd)
let depthDesc = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .depth32Float, width: width, height: height, mipmapped: false)
depthDesc.usage = [.renderTarget,.shaderRead]
let depth = device.makeTexture(descriptor: depthDesc)!
let texDesc = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .rgba8Unorm, width: width, height: height, mipmapped: false)
texDesc.storageMode = .shared; texDesc.usage = [.renderTarget,.shaderRead]
let color = device.makeTexture(descriptor: texDesc)!, output = device.makeTexture(descriptor: texDesc)!, copied = device.makeTexture(descriptor: texDesc)!
var pixels = [UInt8](repeating: 255, count: width*height*4)
for i in 0..<(width*height) { pixels[i*4] = 95; pixels[i*4+1] = 100; pixels[i*4+2] = 90 }
pixels.withUnsafeBytes { color.replace(region:MTLRegionMake2D(0,0,width,height), mipmapLevel:0, withBytes:$0.baseAddress!, bytesPerRow:width*4) }
let zDesc = MTLTextureDescriptor.texture2DDescriptor(pixelFormat:.r32Float,width:width,height:height,mipmapped:false)
zDesc.storageMode = .shared; zDesc.usage = .shaderRead
let zTexture = device.makeTexture(descriptor:zDesc)!
var z = [Float](repeating:0,count:width*height)
for y in 0..<height { for x in 0..<width {
    let ndcY = (Float(y)+0.5)/Float(height)*2-1
    z[y*width+x] = max(ndcY < 0 ? -ndcY*0.1 : 0, 0.025)
}}
z.withUnsafeBytes { zTexture.replace(region:MTLRegionMake2D(0,0,width,height),mipmapLevel:0,withBytes:$0.baseAddress!,bytesPerRow:width*4) }
let depthStateDesc = MTLDepthStencilDescriptor()
depthStateDesc.depthCompareFunction = .always; depthStateDesc.isDepthWriteEnabled = true
let command = queue.makeCommandBuffer()!, pass = MTLRenderPassDescriptor()
pass.depthAttachment.texture = depth; pass.depthAttachment.loadAction = .clear; pass.depthAttachment.storeAction = .store
let fill = command.makeRenderCommandEncoder(descriptor:pass)!
fill.setRenderPipelineState(depthPipeline); fill.setDepthStencilState(device.makeDepthStencilState(descriptor:depthStateDesc))
fill.setFragmentTexture(zTexture,index:0); fill.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3); fill.endEncoding()
command.commit(); command.waitUntilCompleted(); require(command.status == .completed,"Depth fixture failed")
struct Uniforms { var inverse:simd_float4x4; var projection:simd_float4x4; var camera:SIMD4<Float>; var settings:SIMD4<Float> }
let projection = simd_float4x4(columns:(SIMD4(Float(height)/Float(width),0,0,0),SIMD4(0,1,0,0),SIMD4(0,0,0,-1),SIMD4(0,0,0.1,0)))
var uniforms = Uniforms(inverse:projection.inverse,projection:projection,camera:SIMD4(12,12,12,0),settings:.zero)
func grid(metal:Bool, lights:Int) -> MTLBuffer {
    var values = [SIMD4<Float>](repeating:.zero,count:24*24*24*7+16)
    for y in 0..<24 { for z in 0..<24 { for x in 0..<24 {
        let cell = (y*24+z)*24+x
        let m = metal && x >= 12 ? SIMD4<Float>(0.25,1,0,1) : SIMD4<Float>(0.9,0,0,1)
        for face in 0..<6 { values[cell*7+face] = m }
        values[cell*7+6].w = (y == 10 || z == 7) ? 1 : 0
    }}}
    for i in 0..<lights {
        let x = 9.5 + Float(i%4)*2, z = 9.5 + Float(i/4)*3
        values[96768+i*2] = SIMD4(x,12.5,z,1)
        values[96769+i*2] = SIMD4(1.5,0.7,0.3,0)
        let cell = (12*24+Int(z))*24+Int(x)
        values[cell*7+6].w = 1
    }
    return values.withUnsafeBytes { device.makeBuffer(bytes:$0.baseAddress!,length:$0.count)! }
}
var reports = [[String:Any]]()
for (name, metal, lights, effect, half) in [("copy_only",false,0,false,false),("rough_no_lights_full",false,0,true,false),
    ("mixed_no_lights_full",true,0,true,false),("mixed_eight_lights_full",true,8,true,false),
    ("mixed_no_lights_half",true,0,true,true),("mixed_eight_lights_half",true,8,true,true)] {
    let data = grid(metal:metal,lights:lights)
    uniforms.settings.y = Float(lights)
    var samples = [Double]()
    for frame in 0..<40 {
        let cmd = queue.makeCommandBuffer()!
        func draw(_ state:MTLRenderPipelineState,_ texture:MTLTexture,_ composite:Bool) {
            let pass = MTLRenderPassDescriptor()
            pass.colorAttachments[0].texture = texture; pass.colorAttachments[0].loadAction = .dontCare
            pass.colorAttachments[0].storeAction = .store
            let encoder = cmd.makeRenderCommandEncoder(descriptor:pass)!
            encoder.setRenderPipelineState(state)
            encoder.setFragmentTexture(color,index:0); encoder.setFragmentTexture(composite ? halfTexture : depth,index:1)
            if composite { encoder.setFragmentTexture(depth,index:2) }
            encoder.setFragmentBytes(&uniforms,length:160,index:0)
            encoder.setFragmentBuffer(data,offset:0,index:1)
            encoder.drawPrimitives(type:.triangle,vertexStart:0,vertexCount:3); encoder.endEncoding()
        }
        if effect {
            if half { draw(halfPipeline,halfTexture,false); draw(compositePipeline,output,true) }
            else { draw(pipeline,output,false) }
        }
        let blit = cmd.makeBlitCommandEncoder()!
        blit.copy(from:effect ? output : color,sourceSlice:0,sourceLevel:0,sourceOrigin:MTLOrigin(x:0,y:0,z:0),sourceSize:MTLSize(width:width,height:height,depth:1),to:copied,destinationSlice:0,destinationLevel:0,destinationOrigin:MTLOrigin(x:0,y:0,z:0))
        blit.endEncoding(); cmd.commit(); cmd.waitUntilCompleted()
        require(cmd.status == .completed,"Benchmark GPU command failed")
        if frame >= 10 { samples.append((cmd.gpuEndTime-cmd.gpuStartTime)*1000) }
    }
    require(samples.allSatisfy { $0 > 0 && $0.isFinite },"GPU timestamps unavailable")
    let sorted = samples.sorted(), mean = samples.reduce(0,+)/Double(samples.count)
    let report:[String:Any] = ["scenario":name,"meanGpuMs":mean,"medianGpuMs":sorted[14],"p95GpuMs":sorted[28],"samplesGpuMs":samples]
    reports.append(report)
    print("\(name): mean \(String(format:"%.3f",mean)) ms, p95 \(String(format:"%.3f",sorted[28])) ms")
}
let result:[String:Any] = ["device":device.name,"width":width,"height":height,"warmupFramesPerScenario":10,"measuredFramesPerScenario":30,"scope":"Isolated native GPU command timing: production post shader plus scratch copy, synthetic ground/wall, no Minecraft or CPU cache cost. Copy-only is a bandwidth reference, not actual effect-OFF gameplay.","scenarios":reports]
try JSONSerialization.data(withJSONObject:result,options:[.prettyPrinted,.sortedKeys]).write(to:URL(fileURLWithPath:CommandLine.arguments[2]))
