package physx

import de.fabmax.kool.math.*

external interface PxBoundedData {
    var count: Int
    var stride: Int
    var data: Int
}

external interface PxFoundation

external interface PxCpuDispatcher

external interface PxPhysicsInsertionCallback

external interface PxQuat {
    var x: Float
    var y: Float
    var z: Float
    var w: Float
}
fun PxQuat.toVec4f(result: MutableVec4f = MutableVec4f()) = result.set(x, y, z, w)
fun PxQuat.set(v: Vec4f): PxQuat { x = v.x; y = v.y; z = v.z; w = v.w; return this }
fun PxQuat.setIdentity(): PxQuat { x = 0f; y = 0f; z = 0f; w = 1f; return this }
fun Vec4f.toPxQuat(result: PxQuat = PhysX.PxQuat()) = result.set(this)

external interface PxTransform {
    var q: PxQuat
    var p: PxVec3
}
fun PxTransform.toMat4f(result: Mat4f): Mat4f {
    result.setRotate(q.toVec4f())
    result[0, 3] = p.x
    result[1, 3] = p.y
    result[2, 3] = p.z
    return result
}
fun PxTransform.set(mat: Mat4f): PxTransform {
    mat.getRotation(MutableVec4f()).toPxQuat(q)
    p.x = mat[0, 3]
    p.y = mat[1, 3]
    p.z = mat[2, 3]
    return this
}
fun PxTransform.setIdentity(): PxTransform {
    q.setIdentity()
    p.set(Vec3f.ZERO)
    return this
}
fun Mat4f.toPxTransform(t: PxTransform = PhysX.PxTransform()) = t.set(this)

external interface PxVec3 {
    var x: Float
    var y: Float
    var z: Float
}
fun PxVec3.toVec3f(result: MutableVec3f = MutableVec3f()) = result.set(x, y, z)
fun PxVec3.set(v: Vec3f): PxVec3 { x = v.x; y = v.y; z = v.z; return this }
fun Vec3f.toPxVec3(result: PxVec3 = PhysX.PxVec3()) = result.set(this)

external interface VectorPxVec3 {
    fun push_back(v: PxVec3)
    fun get(at: Int): PxVec3
    fun data(): Int
}
fun List<Vec3f>.toVectorPxVec3(): VectorPxVec3 {
    val vector = PhysX.VectorPxVec3()
    val pxVec3 = PhysX.PxVec3()
    forEach {
        vector.push_back(it.toPxVec3(pxVec3))
    }
    return vector
}
