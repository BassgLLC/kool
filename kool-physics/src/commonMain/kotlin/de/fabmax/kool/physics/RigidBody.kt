package de.fabmax.kool.physics

import de.fabmax.kool.math.Vec3f

expect abstract class RigidBody : RigidActor {

    var mass: Float
    var inertia: Vec3f

    var linearVelocity: Vec3f
    var angularVelocity: Vec3f

    var maxLinearVelocity: Float
    var maxAngularVelocity: Float

    var linearDamping: Float
    var angularDamping: Float

    fun updateInertiaFromShapesAndMass()

    fun addForceAtPos(force: Vec3f, pos: Vec3f, isLocalForce: Boolean = false, isLocalPos: Boolean = false)
}