package de.fabmax.kool.shading

import de.fabmax.kool.CubeMapTexture
import de.fabmax.kool.KoolContext
import de.fabmax.kool.RenderPass
import de.fabmax.kool.Texture
import de.fabmax.kool.math.MutableVec4f
import de.fabmax.kool.pipeline.Attribute
import de.fabmax.kool.scene.InstancedMesh
import de.fabmax.kool.scene.Mesh
import de.fabmax.kool.scene.Node
import de.fabmax.kool.scene.animation.Armature
import de.fabmax.kool.util.Float32Buffer


fun basicShader(injector: GlslGenerator.GlslInjector? = null, propsInit: ShaderProps.() -> Unit): BasicShader {
    val gen = GlslGenerator()
    if (injector != null) {
        gen.injectors += injector
    }
    return BasicShader(ShaderProps().apply(propsInit), gen)
}

/**
 * Generic simple shader generated by [GlslGenerator]
 */
open class BasicShader(val props: ShaderProps, protected val generator: GlslGenerator = GlslGenerator()) : Shader() {

    protected val uMvpMatrix = addUniform(UniformMatrix4(GlslGenerator.U_MVP_MATRIX))
    protected val uModelMatrix = addUniform(UniformMatrix4(GlslGenerator.U_MODEL_MATRIX))
    protected val uViewMatrix = addUniform(UniformMatrix4(GlslGenerator.U_VIEW_MATRIX))
    protected val uProjMatrix = addUniform(UniformMatrix4(GlslGenerator.U_PROJ_MATRIX))
    protected val uLightColor = addUniform(Uniform3f(GlslGenerator.U_LIGHT_COLOR))
    protected val uLightDirection = addUniform(Uniform3f(GlslGenerator.U_LIGHT_DIRECTION))
    protected val uCamPosition = addUniform(Uniform3f(GlslGenerator.U_CAMERA_POSITION))
    protected val uShininess = addUniform(Uniform1f(GlslGenerator.U_SHININESS))
    protected val uSpecularIntensity = addUniform(Uniform1f(GlslGenerator.U_SPECULAR_INTENSITY))
    protected val uReflectivity = addUniform(Uniform1f(GlslGenerator.U_REFLECTIVENESS))
    protected val uStaticColor = addUniform(Uniform4f(GlslGenerator.U_STATIC_COLOR))
    protected val uTexture = addUniform(UniformTexture2D(GlslGenerator.U_TEXTURE_0))
    protected val uNormalMap = addUniform(UniformTexture2D(GlslGenerator.U_NORMAL_MAP_0))
    protected val uEnvironmentMap = addUniform(UniformTextureCubeMap(GlslGenerator.U_ENVIRONMENT_MAP))
    protected val uAlpha = addUniform(Uniform1f(GlslGenerator.U_ALPHA))
    protected val uSaturation = addUniform(Uniform1f(GlslGenerator.U_SATURATION))
    protected val uFogColor = addUniform(Uniform4f(GlslGenerator.U_FOG_COLOR))
    protected val uFogRange = addUniform(Uniform1f(GlslGenerator.U_FOG_RANGE))
    protected val uBones = addUniform(UniformMatrix4(GlslGenerator.U_BONES))
    protected val uShadowMvp: UniformMatrix4 = addUniform(UniformMatrix4(GlslGenerator.U_SHADOW_MVP))
    protected val uShadowTexSz: Uniform1iv = addUniform(Uniform1iv(GlslGenerator.U_SHADOW_TEX_SZ))
    protected val uClipSpaceFarZ: Uniform1fv = addUniform(Uniform1fv(GlslGenerator.U_CLIP_SPACE_FAR_Z))

    protected val uShadowTex = mutableListOf<UniformTexture2D>()

    val clipMethod = props.clipMethod

    var shininess: Float
        get() = uShininess.value[0]
        set(value) { uShininess.value[0] = value }
    var specularIntensity: Float
        get() = uSpecularIntensity.value[0]
        set(value) { uSpecularIntensity.value[0] = value }
    var reflectivity: Float
        get() = uReflectivity.value[0]
        set(value) { uReflectivity.value[0] = value }
    var staticColor: MutableVec4f
        get() = uStaticColor.value
        set(value) { uStaticColor.value.set(value) }
    var texture: Texture?
        get() = uTexture.value
        set(value) { uTexture.value = value }
    var normalMap: Texture?
        get() = uNormalMap.value
        set(value) { uNormalMap.value = value }
    var environmentMap: CubeMapTexture?
        get() = uEnvironmentMap.value
        set(value) { uEnvironmentMap.value = value }
    var alpha: Float
        get() = uAlpha.value[0]
        set(value) { uAlpha.value[0] = value }
    var saturation: Float
        get() = uSaturation.value[0]
        set(value) { uSaturation.value[0] = value }
    var bones: Float32Buffer?
        get() = uBones.value
        set(value) { uBones.value = value }

    init {
        // set meaningful uniform default values
        shininess = props.shininess
        specularIntensity = props.specularIntensity
        reflectivity = props.reflectivity
        staticColor.set(props.staticColor)
        texture = props.texture
        normalMap = props.normalMap
        environmentMap = props.environmentMap
        alpha = props.alpha
        saturation = props.saturation

        for (uniform in clipMethod.getUniforms()) {
            addUniform(uniform)
            generator.customUniforms += uniform
        }
        generator.injectors += clipMethod
    }

    override fun generate(node: Node, ctx: KoolContext) {
        val shadowMap = node.scene?.lighting?.shadowMap
        uShadowTexSz.setSize(shadowMap?.numMaps ?: 0)
        uClipSpaceFarZ.setSize(shadowMap?.numMaps ?: 0)
        uShadowTex.clear()
        if (shadowMap != null) {
            uShadowMvp.value = shadowMap.shadowMvp
            for (i in 0 until shadowMap.numMaps) {
                val shadowTex = addUniform(UniformTexture2D("${GlslGenerator.U_SHADOW_TEX}_$i"))
                uShadowTex += shadowTex
                shadowTex.value = shadowMap.getShadowMap(i)
                uShadowTexSz.value[i] = shadowMap.getShadowMapSize(i)
            }
        }

        source = generator.generate(props, node, ctx)

        attributes.clear()
        attributes.add(Attribute.POSITIONS)
        attributes.add(Attribute.NORMALS)
        attributes.add(Attribute.TEXTURE_COORDS)
        attributes.add(Attribute.COLORS)
        if (props.isNormalMapped) {
            attributes.add(Attribute.TANGENTS)
        }
        if (props.numBones > 0 && ctx.glCapabilities.shaderIntAttribs) {
            attributes.add(Armature.BONE_INDICES)
            attributes.add(Armature.BONE_WEIGHTS)
        }
        if (props.isInstanced) {
            attributes.add(InstancedMesh.MODEL_INSTANCES_0)
            attributes.add(InstancedMesh.MODEL_INSTANCES_1)
            attributes.add(InstancedMesh.MODEL_INSTANCES_2)
            attributes.add(InstancedMesh.MODEL_INSTANCES_3)
        }
        attributes += generator.customAttributes
    }

    override fun onBind(node: Node, ctx: KoolContext) {
        onMatrixUpdate(ctx)

        uFogColor.bind(ctx)
        uFogRange.bind(ctx)
        uSaturation.bind(ctx)
        uAlpha.bind(ctx)
        uShininess.bind(ctx)
        uSpecularIntensity.bind(ctx)
        uReflectivity.bind(ctx)
        uStaticColor.bind(ctx)
        uTexture.bind(ctx)
        uNormalMap.bind(ctx)
        uEnvironmentMap.bind(ctx)
        uBones.bind(ctx)

        val shadowMap = node.scene?.lighting?.shadowMap
        if (ctx.glCapabilities.depthTextures && shadowMap != null) {
            if (ctx.renderPass == RenderPass.SHADOW) {
                for (i in 0 until shadowMap.numMaps) {
                    uShadowTex[i].value = null
                    uShadowTex[i].bind(ctx)
                }
            } else {
                for (i in 0 until shadowMap.numMaps) {
                    uClipSpaceFarZ.value[i] = shadowMap.getClipSpaceFarZ(i)
                    uShadowTex[i].value = shadowMap.getShadowMap(i)
                    uShadowTex[i].bind(ctx)
                }
                uShadowMvp.bind(ctx)
                uShadowTexSz.bind(ctx)
                uClipSpaceFarZ.bind(ctx)
            }
        }

        clipMethod.onBind(node, ctx)
    }

    override fun onMatrixUpdate(ctx: KoolContext) {
        // pass current transformation matrices to shader
        uMvpMatrix.value = ctx.mvpState.mvpMatrixBuffer
        uMvpMatrix.bind(ctx)
        uModelMatrix.value = ctx.mvpState.modelMatrixBuffer
        uModelMatrix.bind(ctx)
        uViewMatrix.value = ctx.mvpState.viewMatrixBuffer
        uViewMatrix.bind(ctx)
        uProjMatrix.value = ctx.mvpState.projMatrixBuffer
        uProjMatrix.bind(ctx)
    }

    override fun bindMesh(mesh: Mesh, ctx: KoolContext) {
        val scene = mesh.scene!!
        uCamPosition.value.set(scene.camera.globalPos)
        uCamPosition.bind(ctx)

        val lighting = scene.lighting
        val primaryLight = lighting.lights[0]
        uLightDirection.value.set(primaryLight.direction)
        uLightDirection.bind(ctx)
        uLightColor.value.set(primaryLight.color.r, primaryLight.color.g, primaryLight.color.b)
        uLightColor.bind(ctx)

        super.bindMesh(mesh, ctx)
    }

    override fun dispose(ctx: KoolContext) {
        super.dispose(ctx)
        texture?.dispose(ctx)
        normalMap?.dispose(ctx)
    }
}

fun basicPointShader(propsInit: ShaderProps.() -> Unit): BasicPointShader {
    return BasicPointShader(ShaderProps().apply(propsInit), GlslGenerator())
}

open class BasicPointShader internal constructor(props: ShaderProps, generator: GlslGenerator) :
        BasicShader(props,  generator) {

    companion object {
        const val U_POINT_SIZE = "uPointSz"
    }

    protected val uPointSz = addUniform(Uniform1f(U_POINT_SIZE))
    var pointSize: Float
        get() = uPointSz.value[0]
        set(value) { uPointSz.value[0] = value }

    init {
        generator.customUniforms += uPointSz
        generator.injectors += object : GlslGenerator.GlslInjector {
            override fun vsAfterProj(shaderProps: ShaderProps, node: Node, text: StringBuilder, ctx: KoolContext) {
                text.append("gl_PointSize = ${BasicPointShader.U_POINT_SIZE};\n")
            }
        }

        pointSize = 1f
    }

    override fun onBind(node: Node, ctx: KoolContext) {
        super.onBind(node, ctx)
        uPointSz.bind(ctx)
    }
}
