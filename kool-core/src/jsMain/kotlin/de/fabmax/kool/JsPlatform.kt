package de.fabmax.kool

import de.fabmax.kool.math.clamp
import de.fabmax.kool.platform.JsContext
import de.fabmax.kool.platform.WebGL2RenderingContext
import kotlinx.browser.document
import org.w3c.dom.HTMLDivElement
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Javascript / WebGL platform implementation
 *
 * @author fabmax
 */

actual fun createDefaultContext(): KoolContext = createContext(JsContext.InitProps())

fun createContext(props: JsContext.InitProps): KoolContext = JsImpl.createContext(props)

actual fun now(): Double = js("performance.now()") as Double

actual fun Double.toString(precision: Int): String {
    if (this.isNaN()) {
        return "NaN"
    } else if (this.isInfinite()) {
        return "Infinity"
    }

    val p = precision.clamp(0, 12)
    val s = if (this < 0) "-" else ""
    var a = abs(this)

    if (p == 0) {
        return "$s${a.roundToLong()}"
    }

    val fac = 10.0.pow(p).roundToLong()
    var fracF = ((a % 1.0) * fac).roundToLong()
    if (fracF == fac) {
        fracF = 0
        a += 1
    }

    var frac = fracF.toString()
    while (frac.length < p) {
        frac = "0$frac"
    }
    return "$s${a.toLong()}.$frac"
}

actual inline fun <R> lock(lock: Any, block: () -> R): R = block()

internal object JsImpl {
    val dpi: Float
    var ctx: JsContext? = null
    val gl: WebGL2RenderingContext
        get() = ctx?.gl ?: throw KoolException("Platform.createContext() not called")

    init {
        val measure = document.getElementById("dpiMeasure")
        dpi = if (measure == null) {
            // no need to warn, even if this exists the result is always 96 dpi...
            // logD { "dpiMeasure element not found, falling back to 96 dpi. Add this hidden div to your html:" }
            // logD { "<div id=\"dpiMeasure\" style=\"height: 1in; width: 1in; left: 100%; position: fixed; top: 100%;\"></div>" }
            96f
        } else {
            (measure as HTMLDivElement).offsetWidth.toFloat()
        }
    }

    fun createContext(props: JsContext.InitProps): KoolContext {
        if (ctx != null) {
            throw KoolException("Context was already created (multi-context is currently not supported in js")
        }
        ctx = JsContext(props)
        return ctx!!
    }
}
