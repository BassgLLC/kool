package de.fabmax.kool.pipeline.shadermodel

import de.fabmax.kool.KoolException
import de.fabmax.kool.drawqueue.DrawCommand
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.toRad
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.Light
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min

abstract class LightNode(name: String, shaderGraph: ShaderGraph) : ShaderNode(name, shaderGraph){
    abstract val outLightCount: ShaderNodeIoVar

    abstract fun callVec3GetFragToLight(idx: String, fragPos: String): String
    abstract fun callVec3GetRadiance(idx: String, fragToLight: String, innerAngle: String): String
}

open class MultiLightNode(shaderGraph: ShaderGraph, val maxLights: Int = 4) : LightNode("Lights", shaderGraph) {
    val uLightCnt = Uniform1i("lightCount")
    val uPositions = Uniform4fv("lightPositions", maxLights)
    val uColors = Uniform4fv("lightColors", maxLights)
    val uDirections = Uniform4fv("lightDirections", maxLights)

    override val outLightCount = ShaderNodeIoVar(ModelVar1i(uLightCnt.name), this)

    override fun setup(shaderGraph: ShaderGraph) {
        super.setup(shaderGraph)
        shaderGraph.descriptorSet.apply {
            uniformBuffer(name, shaderGraph.stage) {
                +{ uPositions }
                +{ uColors }
                +{ uDirections }
                +{ uLightCnt }

                onUpdate = { _, cmd ->
                    encodeLightSetup(cmd)
                }
            }
        }
    }

    private fun encodeLightSetup(cmd: DrawCommand) {
        val lights = cmd.mesh.scene?.lighting?.lights
        if (lights != null) {
            uLightCnt.value = min(lights.size, maxLights)
            for (i in 0 until uLightCnt.value) {
                val light = lights[i]

                uColors.value[i].set(light.color)
                uPositions.value[i].set(light.position, light.type.encoded)
                uDirections.value[i].set(light.direction, cos((light.spotAngle / 2).toRad()))
            }
        }
    }

    override fun generateCode(generator: CodeGenerator) {
        generator.appendFunction("light_getFragToLight", """
            vec3 light_getFragToLight(int idx, vec3 fragPos) {
                if (${uPositions.name}[idx].w == float(${Light.Type.DIRECTIONAL.encoded})) {
                    return -${uDirections.name}[idx].xyz;
                }
                // same for point and spot lights
                return ${uPositions.name}[idx].xyz - fragPos;
            }
            """)

        generator.appendFunction("light_getRadiance", """
            vec3 light_getRadiance(int idx, vec3 fragToLight, float innerAngle) {
                if (${uPositions.name}[idx].w == float(${Light.Type.DIRECTIONAL.encoded})) {
                    return ${uColors.name}[idx].rgb * ${uColors.name}[idx].w;
                }
                float dist = length(fragToLight);
                if (${uPositions.name}[idx].w == float(${Light.Type.POINT.encoded})) {
                    return ${uColors.name}[idx].rgb * ${uColors.name}[idx].w / (dist * dist);
                } else {
                    // spot light
                    vec3 lightDir = -normalize(fragToLight);
                    float spotAng = ${uDirections.name}[idx].w;
                    float innerAng = spotAng + (1.0 - spotAng) * (1.0 - innerAngle);
                    float ang = dot(lightDir, ${uDirections.name}[idx].xyz);
                    float angVal = cos(clamp((innerAng - ang) / (innerAng - spotAng), 0.0, 1.0) * $PI) * 0.5 + 0.5;
                    return ${uColors.name}[idx].rgb * ${uColors.name}[idx].w / (dist * dist) * angVal;
                }
            }
            """)
    }

    override fun callVec3GetFragToLight(idx: String, fragPos: String): String {
        return "light_getFragToLight($idx, $fragPos)"
    }

    override fun callVec3GetRadiance(idx: String, fragToLight: String, innerAngle: String): String {
        return "light_getRadiance($idx, $fragToLight, $innerAngle)"
    }
}

class ShadowedLightNode(vertexGraph: ShaderGraph, fragmentGraph: ShaderGraph, maxLights: Int = 4) {
    lateinit var depthTextures: TextureNode

    val uLightMvps = UniformMat4fv("lightMvp", maxLights)

    var inPosition: ShaderNodeIoVar = ShaderNodeIoVar(ModelVar3fConst(Vec3f.ZERO))
    var inModelMat: ShaderNodeIoVar? = null

    private var outLightPosLoc = 0

    val vertexNode = object : ShaderNode("shadowedLight", vertexGraph, ShaderStage.VERTEX_SHADER.mask) {
        override fun setup(shaderGraph: ShaderGraph) {
            super.setup(shaderGraph)
            val inModel = inModelMat ?: throw KoolException("MVP matrix input not set")
            dependsOn(inPosition, inModel)

            outLightPosLoc = shaderGraph.outputs.size
            shaderGraph.outputs += ShaderInterfaceIoVar(outLightPosLoc, ModelVar4f("ifPosLightSpace[$maxLights]"))

            shaderGraph.descriptorSet.apply {
                uniformBuffer(name, shaderGraph.stage) {
                    +{ uLightMvps }

                    onUpdate = { _, cmd ->
                        cmd.mesh.scene?.lighting?.lights?.let {
                            for (i in 0 until min(maxLights, it.size)) {
                                uLightMvps.value[i].set(it[i].lightMvpMat)
                            }
                        }
                    }
                }
            }
        }

        override fun generateCode(generator: CodeGenerator) {
            val modelMat = inModelMat?.variable ?: throw KoolException("MVP matrix input not set")

            for (i in 0 until maxLights) {
                generator.appendMain("ifPosLightSpace[$i] = ${uLightMvps.name}[$i] * (${modelMat.refAsType(GlslType.MAT_4F)} * vec4(${inPosition.ref3f()}, 1.0));")
            }
        }
    }

    val fragmentNode: LightNode = object : MultiLightNode(fragmentGraph, maxLights) {
        override fun setup(shaderGraph: ShaderGraph) {
            super.setup(shaderGraph)
            shaderGraph.inputs += ShaderInterfaceIoVar(outLightPosLoc, ModelVar4f("ifPosLightSpace[$maxLights]"))
        }

        override fun generateCode(generator: CodeGenerator) {
            generator.appendFunction("light_getFragToLight", """
                vec3 light_getFragToLight(int idx, vec3 fragPos) {
                    if (${uPositions.name}[idx].w == float(${Light.Type.DIRECTIONAL.encoded})) {
                        return -${uDirections.name}[idx].xyz;
                    }
                    // same for point and spot lights
                    return ${uPositions.name}[idx].xyz - fragPos;
                }
                """)

            generator.appendFunction("light_getRadiance", """
                vec3 light_getRadiance(int idx, vec3 fragToLight, float innerAngle) {
                    if (${uPositions.name}[idx].w == float(${Light.Type.DIRECTIONAL.encoded})) {
                        return ${uColors.name}[idx].rgb * ${uColors.name}[idx].w;
                    }
                    float dist = length(fragToLight);
                    if (${uPositions.name}[idx].w == float(${Light.Type.POINT.encoded})) {
                        return ${uColors.name}[idx].rgb * ${uColors.name}[idx].w / (dist * dist);
                    } else {
                        // spot light
                        vec3 lightDir = -normalize(fragToLight);
                        float spotAng = ${uDirections.name}[idx].w;
                        float innerAng = spotAng + (1.0 - spotAng) * (1.0 - innerAngle);
                        float ang = dot(lightDir, ${uDirections.name}[idx].xyz);
                        float angVal = cos(clamp((innerAng - ang) / (innerAng - spotAng), 0.0, 1.0) * $PI) * 0.5 + 0.5;
                        return ${uColors.name}[idx].rgb * ${uColors.name}[idx].w / (dist * dist) * angVal;
                    }
                }
                """)

            // compute shadow factors for all light sources in main function
            // shadow factors are then multiplied to the result of light_getRadiance function when called from main()
            // code would be much nicer (and faster if not all lights are used) if sampling would be done in
            // light_getRadiance function, but that would require accessing the depth map sampler array with a
            // dynamic index, which is not possible in WebGL...
            generator.appendMain("""
                float shadowFacs[$maxLights];
                ivec2 shadowMapSize = textureSize(${depthTextures.name}[0], 0);
                float shadowDx = 1.5 / float(shadowMapSize.x);
                float shadowDy = 1.5 / float(shadowMapSize.y);
                vec4 shadowProjPos;
                vec3 shadowCoord;
            """)
            for (lightI in 0 until maxLights) {
                generator.appendMain("""
                    shadowProjPos = ifPosLightSpace[$lightI];
                    shadowCoord = shadowProjPos.xyz / shadowProjPos.w;
                    shadowFacs[$lightI] = 0.0;
                """)

                // multi sample shadow map for nicer edges
                val nSamples = 9
                for (j in 0 until nSamples) {
                    val off = "vec2(shadowDx * ${shadowOffsets[j].x}, shadowDy * ${shadowOffsets[j].y})"
                    generator.appendMain("shadowFacs[$lightI] += step(shadowCoord.z - 0.001, " +
                            "${generator.sampleTexture2d("${depthTextures.name}[$lightI]", "shadowCoord.xy + $off")}.x);")
                }
                generator.appendMain("shadowFacs[$lightI] /= float($nSamples);")
            }
        }

        override fun callVec3GetRadiance(idx: String, fragToLight: String, innerAngle: String): String {
            // kinda hacky: call regular getRadiance function and multiply the result by the pre-computed shadow
            // factor (only available in main function)
            val call = super.callVec3GetRadiance(idx, fragToLight, innerAngle)
            return "($call * shadowFacs[$idx])"
        }
    }

    companion object {
        private val shadowOffsets = listOf(
                Vec2f(-0.9420f, -0.3990f),
                Vec2f(+0.9456f, -0.7689f),
                Vec2f(-0.0942f, -0.9294f),
                Vec2f(+0.3450f, +0.2939f),
                Vec2f(-0.9159f, +0.4577f),
                Vec2f(-0.8154f, -0.8791f),
                Vec2f(-0.3828f, +0.2768f),
                Vec2f(+0.9748f, +0.7565f),
                Vec2f(+0.4432f, -0.9751f),
                Vec2f(+0.5374f, -0.4737f),
                Vec2f(-0.2650f, -0.4189f),
                Vec2f(+0.7920f, +0.1909f),
                Vec2f(-0.2419f, +0.9971f),
                Vec2f(-0.8141f, +0.9144f),
                Vec2f(+0.1998f, +0.7864f),
                Vec2f(+0.1438f, -0.1410f)
        )
    }
}
