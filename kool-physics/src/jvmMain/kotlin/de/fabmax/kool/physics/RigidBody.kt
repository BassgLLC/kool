package de.fabmax.kool.physics

import com.bulletphysics.collision.dispatch.CollisionObject
import com.bulletphysics.dynamics.RigidBodyConstructionInfo
import com.bulletphysics.linearmath.DefaultMotionState
import com.bulletphysics.linearmath.Transform
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.MutableVec4f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.Vec4f
import de.fabmax.kool.physics.shapes.CollisionShape
import javax.vecmath.Quat4f
import javax.vecmath.Vector3f

actual class RigidBody actual constructor(collisionShape: CollisionShape, mass: Float, bodyProperties: RigidBodyProperties)
    : CommonRigidBody(collisionShape, mass, bodyProperties)
{
    val btRigidBody: BtRigidBody

    private val bufOrigin = MutableVec3f()
    private val bufRotation = MutableVec4f()
    private val bufTransform = Transform()
    private val bufQuat = Quat4f()

    override var origin: Vec3f
        get() {
            btRigidBody.getWorldTransform(bufTransform)
            bufTransform.origin.toVec3f(bufOrigin)
            return bufOrigin
        }
        set(value) {
            btRigidBody.getWorldTransform(bufTransform)
            bufTransform.origin.set(value)
            btRigidBody.setWorldTransform(bufTransform)
            updateTransform()
        }

    override var rotation: Vec4f
        get() {
            btRigidBody.getWorldTransform(bufTransform)
            bufTransform.getRotation(bufQuat).toVec4f(bufRotation)
            return bufRotation
        }
        set(value) {
            btRigidBody.getWorldTransform(bufTransform)
            bufTransform.setRotation(value.toBtQuat4f())
            btRigidBody.setWorldTransform(bufTransform)
            updateTransform()
        }

    init {
        val startTransform = Transform()
        startTransform.setIdentity()
        val motionState = DefaultMotionState(startTransform)
        val boxInertia = Vector3f(0f, 0f, 0f)
        val btShape = collisionShape.btShape
        if (mass > 0f) {
            btShape.calculateLocalInertia(mass, boxInertia)
        }
        val constructionInfo = RigidBodyConstructionInfo(mass, motionState, btShape, boxInertia)
        constructionInfo.friction = bodyProperties.friction
        //constructionInfo.rollingFriction = bodyProperties.rollingFriction     // not available in JBullet
        constructionInfo.restitution = bodyProperties.restitution
        constructionInfo.linearDamping = bodyProperties.linearDamping
        constructionInfo.angularDamping = bodyProperties.angularDamping
        constructionInfo.linearSleepingThreshold *= bodyProperties.sleepThreshold
        constructionInfo.angularSleepingThreshold *= bodyProperties.sleepThreshold

        btRigidBody = BtRigidBody(constructionInfo)

        if (!bodyProperties.canSleep) {
            btRigidBody.activationState = CollisionObject.DISABLE_DEACTIVATION
        }
    }

    override fun fixedUpdate(timeStep: Float) {
        super.fixedUpdate(timeStep)
        updateTransform()
    }

    private fun updateTransform() {
        btRigidBody.getWorldTransform(bufTransform)
        bufTransform.toMat4f(transform)
    }
}