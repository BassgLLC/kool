package de.fabmax.kool.platform

import de.fabmax.kool.pipeline.TextureData3d
import de.fabmax.kool.util.Uint8BufferImpl
import de.fabmax.kool.util.createUint8Buffer

class ImageAtlasTextureData(image: ImageTextureData, tilesX: Int, tilesY: Int) :
        TextureData3d(
                createUint8Buffer(image.width * image.height * image.format.channels),
                image.width / tilesX,
                image.height / tilesY,
                tilesX * tilesY, image.format
        ) {

    init {
        width = image.width / tilesX
        height = image.height / tilesY
        depth = tilesX * tilesY
        format = image.format

        val imgData = image.data as Uint8BufferImpl
        val lineBuf = ByteArray(width * format.channels)
        val target = data as Uint8BufferImpl

        for (tileY in 0 until tilesY) {
            for (tileX in 0 until tilesX) {
                val srcX = tileX * width
                val srcY = tileY * height

                for (l in 0 until height) {
                    imgData.position = ((srcY + l) * image.width + srcX) * format.channels
                    imgData.buffer.get(lineBuf)
                    target.put(lineBuf)
                }
            }
        }
        data.flip()
        imgData.position = 0
    }

}