package de.fabmax.kool.scene.ui

import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.pipeline.*
import kotlin.math.round

/**
 * @author fabmax
 */

data class FontProps(
    val family: String,
    val sizePts: Float,
    val style: Int = Font.PLAIN,
    val chars: String = Font.STD_CHARS,
    val magFilter: FilterMethod = FilterMethod.LINEAR,
    val minFilter: FilterMethod = FilterMethod.LINEAR,
    val mipMapping: Boolean = false,
    val maxAnisotropy: Int = 0,
    val isScaledByScreenDpi: Boolean = true
) {

    override fun toString(): String {
        return "FontProps($family, ${sizePts}pts, $style)"
    }
}

class Font(val fontProps: FontProps) {

    var charMap: CharMap? = null

    val lineSpace: Float
        get() = charMap?.lineSpace ?: round(fontProps.sizePts * 1.2f)
    val normHeight: Float
        get() = charMap?.normHeight ?: (fontProps.sizePts * 0.7f)

    constructor(fontProps: FontProps, ctx: KoolContext) : this(fontProps) {
        getOrInitCharMap(ctx)
    }

    fun getOrInitCharMap(ctx: KoolContext): CharMap {
        return charMap ?: ctx.assetMgr.createCharMap(fontProps).also { charMap = it }
    }

    fun textWidth(string: String): Float {
        var width = 0f
        var maxWidth = 0f

        for (i in string.indices) {
            val c = string[i]
            width += charWidth(c)
            if (width > maxWidth) {
                maxWidth = width
            }
            if (c == '\n') {
                width = 0f
            }
        }
        return maxWidth
    }

    fun charWidth(char: Char): Float {
        val cm = charMap ?: throw IllegalStateException("Font char map has not yet been initialized")
        return cm.get(char)?.advance ?: 0f
    }

    override fun toString(): String {
        return "Font(${fontProps})"
    }

    companion object {
        const val PLAIN = 0
        const val BOLD = 1
        const val ITALIC = 2

        const val SYSTEM_FONT = "-apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Oxygen, Ubuntu, Cantarell, \"Open Sans\", \"Helvetica Neue\", sans-serif"

        val STD_CHARS: String
        val DEFAULT_FONT_PROPS: FontProps
        val DEFAULT_FONT: Font

        init {
            var str = ""
            for (i in 32..126) {
                str += i.toChar()
            }
            str += "äÄöÖüÜß°©"
            STD_CHARS = str
            DEFAULT_FONT_PROPS = FontProps(SYSTEM_FONT, 12f)
            DEFAULT_FONT = Font(DEFAULT_FONT_PROPS)
        }
    }
}

class CharMetrics {
    var width = 0f
    var height = 0f
    var xOffset = 0f
    var yBaseline = 0f
    var advance = 0f

    val uvMin = MutableVec2f()
    val uvMax = MutableVec2f()
}

class CharMap internal constructor(val fontProps: FontProps, private val map: MutableMap<Char, CharMetrics> = mutableMapOf()) :
    MutableMap<Char, CharMetrics> by map
{
    var textureData: TextureData? = null
        set(value) {
            field = value
            if (texture.loadingState == Texture.LoadingState.LOADED) {
                texture.dispose()
            }
        }

    val texture = Texture2d(
        TextureProps(
            addressModeU = AddressMode.CLAMP_TO_EDGE,
            addressModeV = AddressMode.CLAMP_TO_EDGE,
            magFilter = fontProps.magFilter,
            minFilter = fontProps.minFilter,
            mipMapping = fontProps.mipMapping,
            maxAnisotropy = fontProps.maxAnisotropy
        ),
        fontProps.toString(),
        loader = SyncTextureLoader { getOrGenerateTextureData(it) })

    var lineSpace = round(fontProps.sizePts * 1.2f)
        private set
    var normHeight = fontProps.sizePts * 0.7f
        private set

    val isInitialized: Boolean
        get() = textureData != null

    fun applyScale(scale: Float) {
        lineSpace = round(fontProps.sizePts * 1.2f * scale)
        normHeight = fontProps.sizePts * 0.7f * scale
    }

    fun getOrGenerateTextureData(ctx: KoolContext): TextureData {
        if (!isInitialized) {
            ctx.assetMgr.updateCharMap(this)
        }
        return textureData!!
    }
}
