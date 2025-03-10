package de.fabmax.kool.scene

import de.fabmax.kool.InputManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.Ray
import de.fabmax.kool.math.RayTest
import de.fabmax.kool.pipeline.OffscreenRenderPass
import de.fabmax.kool.pipeline.RenderPass
import de.fabmax.kool.pipeline.ScreenRenderPass
import de.fabmax.kool.util.Disposable

/**
 * @author fabmax
 */

inline fun scene(name: String? = null, block: Scene.() -> Unit): Scene {
    return Scene(name).apply(block)
}

open class Scene(name: String? = null) : Group(name) {

    val lighting = Lighting()
    var camera: Camera = PerspectiveCamera()

    val onRenderScene: MutableList<(KoolContext) -> Unit> = mutableListOf()

    val mainRenderPass = ScreenRenderPass(this)

    private val mutOffscreenPasses = mutableListOf<OffscreenRenderPass>()
    private val addOffscreenPasses = mutableListOf<OffscreenRenderPass>()
    private val remOffscreenPasses = mutableListOf<OffscreenRenderPass>()
    val offscreenPasses: List<OffscreenRenderPass>
        get() = mutOffscreenPasses

    override var isFrustumChecked: Boolean
        // frustum check is force disabled for Scenes
        get() = false
        set(_) {}

    var isPickingEnabled = true
    private val rayTest = RayTest()
    private var hoverNode: Node? = null

    private val dragPtrs: MutableList<InputManager.Pointer> = mutableListOf()
    private val dragHandlers: MutableList<DragHandler> = mutableListOf()

    private val disposables = mutableListOf<Disposable>()

    fun addOffscreenPass(pass: OffscreenRenderPass) {
        addOffscreenPasses += pass
    }

    fun removeOffscreenPass(pass: OffscreenRenderPass) {
        remOffscreenPasses += pass
    }

    private fun addOffscreenPasses() {
        if (addOffscreenPasses.isNotEmpty()) {
            addOffscreenPasses.forEach {
                if (it !in mutOffscreenPasses) {
                    mutOffscreenPasses += it
                }
            }
            addOffscreenPasses.clear()
        }
    }

    private fun removeOffscreenPasses() {
        if (remOffscreenPasses.isNotEmpty()) {
            mutOffscreenPasses.removeAll(remOffscreenPasses)
            remOffscreenPasses.clear()
        }
    }

    fun renderScene(ctx: KoolContext) {
        for (i in onRenderScene.indices) {
            onRenderScene[i](ctx)
        }

        // remove all offscreen passes that were scheduled for removal in last frame
        removeOffscreenPasses()
        addOffscreenPasses()

        mainRenderPass.update(ctx)

        for (i in offscreenPasses.indices) {
            val pass = offscreenPasses[i]
            if (pass.isEnabled) {
                pass.update(ctx)
                pass.collectDrawCommands(ctx)
            }
        }
        mainRenderPass.collectDrawCommands(ctx)
    }

    fun processInput(ctx: KoolContext) {
        if (ctx.inputMgr.cursorMode != InputManager.CursorMode.LOCKED) {
            handleInput(ctx)
        }
    }

    override fun update(updateEvent: RenderPass.UpdateEvent) {
        for (i in disposables.indices) {
            disposables[i].dispose(updateEvent.ctx)
        }
        disposables.clear()
        super.update(updateEvent)
    }

    fun dispose(disposable: Disposable) {
        disposables += disposable
    }

    override fun dispose(ctx: KoolContext) {
        disposables.forEach { it.dispose(ctx) }
        disposables.clear()

        mainRenderPass.dispose(ctx)
        for (i in offscreenPasses.indices) {
            offscreenPasses[i].dispose(ctx)
        }
        remOffscreenPasses.clear()
        mutOffscreenPasses.clear()

        super.dispose(ctx)
    }

    fun registerDragHandler(handler: DragHandler) {
        if (handler !in dragHandlers) {
            dragHandlers += handler
        }
    }

    fun removeDragHandler(handler: DragHandler) {
        dragHandlers -= handler
    }

    fun computeRay(pointer: InputManager.Pointer, ctx: KoolContext, result: Ray): Boolean {
        return camera.computePickRay(result, pointer, mainRenderPass.viewport, ctx)
    }

    private fun handleInput(ctx: KoolContext) {
        var hovered: Node? = null
        val prevHovered = hoverNode
        val ptr = ctx.inputMgr.pointerState.primaryPointer

        if (!isPickingEnabled || !ptr.isValid || ptr.isConsumed()) {
            return
        }

        if (ptr.isInViewport(mainRenderPass.viewport, ctx) && camera.initRayTes(rayTest, ptr, mainRenderPass.viewport, ctx)) {
            rayTest(rayTest)
            if (rayTest.isHit) {
                hovered = rayTest.hitNode
            }
        }

        if (prevHovered != hovered) {
            if (prevHovered != null) {
                for (i in prevHovered.onHoverExit.indices) {
                    prevHovered.onHoverExit[i](prevHovered, ptr, rayTest, ctx)
                }
            }
            if (hovered != null) {
                for (i in hovered.onHoverEnter.indices) {
                    hovered.onHoverEnter[i](hovered, ptr, rayTest, ctx)
                }
            }
            hoverNode = hovered
        }
        if (hovered != null && prevHovered == hovered) {
            for (i in hovered.onHover.indices) {
                hovered.onHover[i](hovered, ptr, rayTest, ctx)
            }
        }

        handleDrag(ctx)
    }

    private fun handleDrag(ctx: KoolContext) {
        dragPtrs.clear()
        for (i in ctx.inputMgr.pointerState.pointers.indices) {
            val ptr = ctx.inputMgr.pointerState.pointers[i]
            if (ptr.isValid && (ptr.buttonMask != 0 || ptr.buttonEventMask != 0 || ptr.deltaScroll != 0.0)) {
                dragPtrs.add(ptr)
            }
        }

        for (i in dragHandlers.indices.reversed()) {
            dragHandlers[i].handleDrag(dragPtrs, this, ctx)
        }
    }

    interface DragHandler {
        fun handleDrag(dragPtrs: List<InputManager.Pointer>, scene: Scene, ctx: KoolContext)
    }
}
