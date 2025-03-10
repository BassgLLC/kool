package de.fabmax.kool.platform.vk

import de.fabmax.kool.pipeline.*
import de.fabmax.kool.platform.vk.util.vkBytesPerPx
import de.fabmax.kool.util.logD
import org.lwjgl.vulkan.VK10.vkDestroySampler
import java.util.concurrent.atomic.AtomicLong

class LoadedTextureVk(val sys: VkSystem, val format: TexFormat, val textureImage: Image,
                      val textureImageView: ImageView, val sampler: Long,
                      private val isSharedRes: Boolean = false) : VkResource(), LoadedTexture {

    val texId = nextTexId.getAndIncrement()

    override var width = 0
    override var height = 0
    override var depth = 0

    init {
        if (!isSharedRes) {
            addDependingResource(textureImage)
            addDependingResource(textureImageView)

            sys.ctx.engineStats.textureAllocated(texId, Texture.estimatedTexSize(
                    textureImage.width, textureImage.height, textureImage.arrayLayers, textureImage.mipLevels, format.vkBytesPerPx))
        }
        logD { "Created texture: Image: ${textureImage.vkImage}, view: ${textureImageView.vkImageView}, sampler: $sampler" }
    }

    fun setSize(width: Int, height: Int, depth: Int) {
        this.width = width
        this.height = height
        this.depth = depth
    }

    override fun freeResources() {
        if (!isSharedRes) {
            vkDestroySampler(sys.device.vkDevice, sampler, null)
            sys.ctx.engineStats.textureDeleted(texId)
        }
        logD { "Destroyed texture" }
    }

    override fun dispose() {
        if (!isDestroyed) {
            // fixme: kinda hacky... also might be depending resource of something else than sys.device
            sys.ctx.runDelayed(sys.swapChain?.nImages ?: 3) {
                sys.device.removeDependingResource(this)
                destroy()
            }
        }
    }

    companion object {
        private val nextTexId = AtomicLong(1L)

        fun fromTexData(sys: VkSystem, texProps: TextureProps, data: TextureData): LoadedTextureVk {
            return when(data) {
                is TextureData1d -> TextureLoader.loadTexture2d(sys, texProps, data)
                is TextureData2d -> TextureLoader.loadTexture2d(sys, texProps, data)
                is TextureData3d -> TextureLoader.loadTexture3d(sys, texProps, data)
                is TextureDataCube -> TextureLoader.loadCubeMap(sys, texProps, data)
                else -> TODO("texture data not implemented: ${data::class.java.name}")
            }
        }
    }
}
