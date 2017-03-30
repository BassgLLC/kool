package de.fabmax.kool.desktop

import de.fabmax.kool.demo.pointDemo
import de.fabmax.kool.demo.simpleShapesDemo
import de.fabmax.kool.demo.uiDemo
import de.fabmax.kool.platform.PlatformImpl

/**
 * @author fabmax
 */
fun main(args: Array<String>) {
    val ctx = PlatformImpl.initContext()

    //simpleShapesDemo(ctx)
    //modelDemo(ctx)
    uiDemo(ctx)
    //pointDemo(ctx)
    //multiSceneDemo(ctx)
}
