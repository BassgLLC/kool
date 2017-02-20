package de.fabmax.kool.scene

import de.fabmax.kool.platform.RenderContext
import de.fabmax.kool.util.*

/**
 * @author fabmax
 */

fun transformGroup(name: String? = null, block: TransformGroup.() -> Unit): TransformGroup {
    val tg = TransformGroup(name)
    tg.block()
    return tg
}

open class TransformGroup(name: String? = null) : Group(name) {
    protected val transform = Mat4f()
    protected val invTransform = Mat4f()
    protected var transformDirty = false

    open var animation: (TransformGroup.(RenderContext) -> Unit)? = null

    private val tmpTransformVec = MutableVec3f()

    protected fun checkInverse() {
        if (transformDirty) {
            transform.invert(invTransform)
            transformDirty = false
        }
    }

    override fun render(ctx: RenderContext) {
        if (!isVisible) {
            return
        }

        animation?.invoke(this, ctx)

        // apply transformation
        ctx.mvpState.modelMatrix.push()
        ctx.mvpState.modelMatrix.mul(transform)
        ctx.mvpState.update(ctx)

        // draw all child nodes
        super.render(ctx)

        // transform updated bounding box
        if (!bounds.isEmpty) {
            tmpBounds.clear()
            tmpBounds.add(transform.transform(tmpTransformVec.set(bounds.min.x, bounds.min.y, bounds.min.z), 1f))
            tmpBounds.add(transform.transform(tmpTransformVec.set(bounds.min.x, bounds.min.y, bounds.max.z), 1f))
            tmpBounds.add(transform.transform(tmpTransformVec.set(bounds.min.x, bounds.max.y, bounds.min.z), 1f))
            tmpBounds.add(transform.transform(tmpTransformVec.set(bounds.min.x, bounds.max.y, bounds.max.z), 1f))
            tmpBounds.add(transform.transform(tmpTransformVec.set(bounds.max.x, bounds.min.y, bounds.min.z), 1f))
            tmpBounds.add(transform.transform(tmpTransformVec.set(bounds.max.x, bounds.min.y, bounds.max.z), 1f))
            tmpBounds.add(transform.transform(tmpTransformVec.set(bounds.max.x, bounds.max.y, bounds.min.z), 1f))
            tmpBounds.add(transform.transform(tmpTransformVec.set(bounds.max.x, bounds.max.y, bounds.max.z), 1f))
            bounds.set(tmpBounds)
        }

        // clear transformation
        ctx.mvpState.modelMatrix.pop()
        ctx.mvpState.update(ctx)
    }

    override fun toGlobalCoords(vec: MutableVec3f, w: Float): MutableVec3f {
        transform.transform(vec, w)
        return super.toGlobalCoords(vec, w)
    }

    override fun toLocalCoords(vec: MutableVec3f, w: Float): MutableVec3f {
        checkInverse()
        super.toLocalCoords(vec, w)
        return invTransform.transform(vec, w)
    }

    override fun rayTest(test: RayTest) {
        // transform ray to local coordinates
        checkInverse()
        invTransform.transform(test.ray.origin, 1f)
        invTransform.transform(test.ray.direction, 0f)

        super.rayTest(test)

        // transform ray back to previous coordinates
        transform.transform(test.ray.origin, 1f)
        transform.transform(test.ray.direction, 0f)
    }

    fun translate(tx: Float, ty: Float, tz: Float): TransformGroup {
        transform.translate(tx, ty, tz)
        transformDirty = true
        return this
    }

    fun rotate(angleDeg: Float, axis: Vec3f) = rotate(angleDeg, axis.x, axis.y, axis.z)

    fun rotate(angleDeg: Float, axX: Float, axY: Float, axZ: Float): TransformGroup {
        transform.rotate(angleDeg, axX, axY, axZ)
        transformDirty = true
        return this
    }

//    fun rotateEuler(xDeg: Float, yDeg: Float, zDeg: Float): TransformGroup {
//        transform.rotateEuler(xDeg, yDeg, zDeg)
//        transformDirty = true
//        return this
//    }

    fun scale(sx: Float, sy: Float, sz: Float): TransformGroup {
        transform.scale(sx, sy, sz)
        transformDirty = true
        return this
    }

    fun mul(mat: Mat4f): TransformGroup {
        transform.mul(mat)
        transformDirty = true
        return this
    }

    fun set(mat: Mat4f): TransformGroup {
        transform.set(mat)
        transformDirty = true
        return this
    }

    fun setIdentity(): TransformGroup {
        transform.setIdentity()
        invTransform.setIdentity()
        transformDirty = false
        return this
    }
}