package de.fabmax.kool.util.pbrMapGen

import de.fabmax.kool.OffscreenPassCube
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.pipeline.shadermodel.*
import de.fabmax.kool.pipeline.shading.ModeledShader
import de.fabmax.kool.scene.CullMethod
import de.fabmax.kool.scene.mesh
import de.fabmax.kool.scene.scene
import de.fabmax.kool.util.logD
import kotlin.math.PI

class ReflectionMapPass(hdriTexture: Texture) {
    val offscreenPass: OffscreenPassCube
    val reflectionMap: CubeMapTexture
        get() = offscreenPass.impl.texture
    var hdriTexture = hdriTexture
        set(value) {
            reflMapShader?.textureSampler?.texture = value
            field = value
        }

    private val uRoughness = Uniform1f(0.5f, "uRoughness")
    private var reflMapShader: ModeledShader.TextureColor? = null

    init {
        offscreenPass = OffscreenPassCube(256, 256, 7).apply {
            onSetup = { ctx ->
                if (frameIdx >= mipLevels) {
                    ctx.offscreenPasses -= this
                } else {
                    logD { "Render reflection map mip level $frameIdx" }
                    uRoughness.value = frameIdx.toFloat() / (mipLevels - 1)
                    targetMipLevel = frameIdx
                }
            }

            scene = scene {
                +mesh(setOf(Attribute.POSITIONS)) {
                    generator = {
                        cube { centerOrigin() }
                    }

                    pipelineConfig {
                        // cube is viewed from inside
                        cullMethod = CullMethod.CULL_FRONT_FACES

                        shaderLoader = { mesh, buildCtx, ctx ->
                            val texName = "colorTex"
                            val model = ShaderModel("Reflectance Convolution Sampler").apply {
                                val ifLocalPos: StageInterfaceNode
                                vertexStage {
                                    ifLocalPos = stageInterfaceNode("ifLocalPos", attrPositions().output)
                                    positionOutput = simpleVertexPositionNode().outPosition
                                }
                                fragmentStage {
                                    val roughness = pushConstantNode1f(uRoughness)
                                    val tex = textureNode(texName)
                                    val convNd = addNode(ConvoluteReflectionNode(tex, stage)).apply {
                                        inLocalPos = ifLocalPos.output
                                        inRoughness = roughness.output
                                    }
                                    colorOutput = convNd.outColor
                                }
                            }
                            ModeledShader.TextureColor(model, texName).setup(mesh, buildCtx, ctx)
                        }

                        onPipelineCreated += {
                            reflMapShader = (it.shader as ModeledShader.TextureColor)
                            reflMapShader!!.textureSampler.texture = hdriTexture
                        }
                    }
                }
            }
        }
    }

    private class ConvoluteReflectionNode(val texture: TextureNode, graph: ShaderGraph) : ShaderNode("convIrradiance", graph) {
        var inLocalPos = ShaderNodeIoVar(ModelVar3fConst(Vec3f.X_AXIS))
        var inRoughness = ShaderNodeIoVar(ModelVar1fConst(0f))
        val outColor = ShaderNodeIoVar(ModelVar4f("convReflection_outColor"), this)

        override fun setup(shaderGraph: ShaderGraph) {
            super.setup(shaderGraph)
            dependsOn(inLocalPos)
            dependsOn(texture)
        }

        override fun generateCode(generator: CodeGenerator) {
            super.generateCode(generator)

            generator.appendFunction("reflMapFuncs", """
                const vec2 invAtan = vec2(0.1591, 0.3183);
                vec3 sampleEquiRect(vec3 texCoord) {
                    vec3 equiRect_in = normalize(texCoord);
                    vec2 uv = vec2(atan(equiRect_in.z, equiRect_in.x), asin(equiRect_in.y));
                    uv *= invAtan;
                    uv += 0.5;
                    
                    vec4 rgbe = ${generator.sampleTexture2d(texture.name, "uv")};
                    
                    // decode rgbe
                    return rgbe.rgb * pow(2.0, rgbe.w * 255.0 - 127.0);
                }
                
                float RadicalInverse_VdC(uint bits) {
                    bits = (bits << 16u) | (bits >> 16u);
                    bits = ((bits & 0x55555555u) << 1u) | ((bits & 0xAAAAAAAAu) >> 1u);
                    bits = ((bits & 0x33333333u) << 2u) | ((bits & 0xCCCCCCCCu) >> 2u);
                    bits = ((bits & 0x0F0F0F0Fu) << 4u) | ((bits & 0xF0F0F0F0u) >> 4u);
                    bits = ((bits & 0x00FF00FFu) << 8u) | ((bits & 0xFF00FF00u) >> 8u);
                    return float(bits) * 2.3283064365386963e-10; // / 0x100000000
                }
                
                vec2 Hammersley(uint i, uint N) {
                    return vec2(float(i)/float(N), RadicalInverse_VdC(i));
                }
                
                vec3 ImportanceSampleGGX(vec2 Xi, vec3 N, float roughness) {
                    float a = roughness*roughness;
                    
                    float phi = 2.0 * $PI * Xi.x;
                    float cosTheta = sqrt((1.0 - Xi.y) / (1.0 + (a*a - 1.0) * Xi.y));
                    float sinTheta = sqrt(1.0 - cosTheta*cosTheta);
                    
                    // from spherical coordinates to cartesian coordinates
                    vec3 H;
                    H.x = cos(phi) * sinTheta;
                    H.y = sin(phi) * sinTheta;
                    H.z = cosTheta;
                    
                    // from tangent-space vector to world-space sample vector
                    vec3 up = abs(N.z) < 0.9999 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0);
                    vec3 tangent = normalize(cross(up, N));
                    vec3 bitangent = cross(N, tangent);
                    
                    vec3 sampleVec = tangent * H.x + bitangent * H.y + N * H.z;
                    return normalize(sampleVec);
                }
            """)

            generator.appendMain("""
                vec3 N = normalize(${inLocalPos.ref3f()});   
                vec3 R = N;
                vec3 V = R;
                
                const uint SAMPLE_COUNT = 1024u;
                float totalWeight = 0.0;   
                vec3 prefilteredColor = vec3(0.0);     
                for(uint i = 0u; i < SAMPLE_COUNT; ++i) {
                    vec2 Xi = Hammersley(i, SAMPLE_COUNT);
                    vec3 H  = ImportanceSampleGGX(Xi, N, ${inRoughness.ref1f()});
                    vec3 L  = normalize(2.0 * dot(V, H) * H - V);
            
                    float NdotL = max(dot(N, L), 0.0);
                    if(NdotL > 0.0) {
                        prefilteredColor += sampleEquiRect(L).rgb * NdotL;
                        totalWeight += NdotL;
                    }
                }
                prefilteredColor = prefilteredColor / totalWeight;
                ${outColor.declare()} = vec4(prefilteredColor, 1.0);
            """)
        }
    }
}