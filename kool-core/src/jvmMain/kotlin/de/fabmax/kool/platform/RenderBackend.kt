package de.fabmax.kool.platform

import de.fabmax.kool.math.Mat4d
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.pipeline.shadermodel.ShaderGenerator
import de.fabmax.kool.util.Viewport

interface RenderBackend {

    val apiName: String
    val deviceName: String

    val windowWidth: Int
    val windowHeight: Int
    val glfwWindowHandle: Long
    var isFullscreen: Boolean

    val projCorrectionMatrixScreen: Mat4d
    val projCorrectionMatrixOffscreen: Mat4d
    val depthBiasMatrix: Mat4d

    val shaderGenerator: ShaderGenerator

    fun getWindowViewport(result: Viewport)

    fun drawFrame(ctx: Lwjgl3Context)
    fun destroy(ctx: Lwjgl3Context)

    fun loadTex2d(tex: Texture2d, data: TextureData)
    fun loadTexCube(tex: TextureCube, data: TextureDataCube)

    fun createOffscreenPass2d(parentPass: OffscreenPass2dImpl): OffscreenPass2dImpl.BackendImpl
    fun createOffscreenPassCube(parentPass: OffscreenPassCubeImpl): OffscreenPassCubeImpl.BackendImpl
}