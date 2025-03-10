package de.fabmax.kool.pipeline.ibl

import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.pipeline.shadermodel.*
import de.fabmax.kool.pipeline.shading.ModeledShader
import de.fabmax.kool.scene.Group
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.textureMesh
import de.fabmax.kool.util.ColorGradient
import de.fabmax.kool.util.MutableColor
import de.fabmax.kool.util.createUint8Buffer
import de.fabmax.kool.util.logD
import kotlin.math.PI

class GradientCubeGenerator(scene: Scene, val gradientTex: Texture2d, ctx: KoolContext, size: Int = 128) :
        OffscreenRenderPassCube(Group(), renderPassConfig {
            name = "GradientEnvGenerator"
            setSize(size, size)
            addColorTexture(TexFormat.RGBA_F16)
            clearDepthTexture()
        }) {

    init {
        (drawNode as Group).apply {
            +textureMesh {
                generate {
                    cube {
                        centered()
                    }
                }
                shader = ModeledShader.TextureColor(gradientTex, "gradTex", gradientEnvModel())
                shader!!.onPipelineSetup += { builder, _, _ ->
                    builder.depthTest = DepthCompareOp.DISABLED
                    builder.cullMethod = CullMethod.NO_CULLING
                }
            }
        }

        // remove render pass as soon as the gradient texture is loaded and rendered
        onAfterDraw += {
            logD { "Generated gradient cube map" }
            scene.removeOffscreenPass(this)
            ctx.runDelayed(1) {
                dispose(ctx)
            }
        }
    }

    override fun dispose(ctx: KoolContext) {
        super.dispose(ctx)
        gradientTex.dispose()
    }

    private fun gradientEnvModel() = ShaderModel("gradientEnvModel()").apply {
        val ifFragPos: StageInterfaceNode

        vertexStage {
            val mvpNode = mvpNode()
            val localPos = attrPositions().output
            val worldPos = vec3TransformNode(localPos, mvpNode.outModelMat, 1f).outVec3
            ifFragPos = stageInterfaceNode("ifFragPos", worldPos)
            positionOutput = vec4TransformNode(localPos, mvpNode.outMvpMat).outVec4
        }
        fragmentStage {
            val uv = addNode(PosToUvNode(stage))
            uv.inPos = ifFragPos.output
            val sampler = texture2dSamplerNode(texture2dNode("gradTex"), uv.outUv)
            val linColor = gammaNode(sampler.outColor).outColor
            colorOutput(linColor)
        }
    }

    private class PosToUvNode(graph: ShaderGraph) : ShaderNode("posToUv", graph) {
        var inPos = ShaderNodeIoVar(ModelVar3fConst(Vec3f.Y_AXIS))
        val outUv = ShaderNodeIoVar(ModelVar2f("outUv"), this)

        override fun setup(shaderGraph: ShaderGraph) {
            super.setup(shaderGraph)
            dependsOn(inPos)
        }

        override fun generateCode(generator: CodeGenerator) {
            generator.appendMain("${outUv.declare()} = vec2(acos(normalize(${inPos.ref3f()}).y) / $PI, 0.0);")
        }
    }

    companion object {
        suspend fun makeGradientTex(gradient: ColorGradient, ctx: KoolContext, size: Int = 256): Texture2d {
            val buf = createUint8Buffer(size * 4)

            val color = MutableColor()
            for (i in 0 until size) {
                gradient.getColorInterpolated(1f - i.toFloat() / size, color)
                buf[i * 4 + 0] = ((color.r * 255f).toInt().toByte())
                buf[i * 4 + 1] = ((color.g * 255f).toInt().toByte())
                buf[i * 4 + 2] = ((color.b * 255f).toInt().toByte())
                buf[i * 4 + 3] = (255.toByte())
            }

            val data = TextureData2d(buf, size, 1, TexFormat.RGBA)
            val props = TextureProps(addressModeU = AddressMode.CLAMP_TO_EDGE, addressModeV = AddressMode.CLAMP_TO_EDGE, mipMapping = false, maxAnisotropy = 1)
            return ctx.assetMgr.loadAndPrepareTexture(data, props, "gradientEnvTex")
        }
    }
}