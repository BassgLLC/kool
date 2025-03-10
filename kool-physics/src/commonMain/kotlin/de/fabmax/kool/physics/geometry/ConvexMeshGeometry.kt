package de.fabmax.kool.physics.geometry

import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.spatial.BoundingBox
import de.fabmax.kool.scene.geometry.MeshBuilder

expect class ConvexMeshGeometry(convexMesh: ConvexMesh, scale: Vec3f = Vec3f.ONES) : CommonConvexMeshGeometry, CollisionGeometry {
    constructor(points: List<Vec3f>)
}

abstract class CommonConvexMeshGeometry(val convexMesh: ConvexMesh, val scale: Vec3f) {
    open fun generateMesh(target: MeshBuilder) {
        target.apply {
            withTransform {
                scale(scale.x, scale.y, scale.z)
                val hull = convexMesh.convexHull
                val inds = mutableListOf<Int>()
                hull.forEach {
                    inds += vertex(it, it.normal)
                }
                for (i in 0 until hull.numIndices step 3) {
                    val i0 = inds[hull.indices[i]]
                    val i1 = inds[hull.indices[i + 1]]
                    val i2 = inds[hull.indices[i + 2]]
                    geometry.addTriIndices(i0, i1, i2)
                }
            }
        }
    }

    open fun getBounds(result: BoundingBox) = result.set(convexMesh.convexHull.bounds)

    open fun estimateInertiaForMass(mass: Float, result: MutableVec3f): MutableVec3f {
        // rough approximation: use inertia of bounding box
        val bounds = convexMesh.convexHull.bounds
        result.x = (mass / 12f) * (bounds.size.y * bounds.size.y + bounds.size.z * bounds.size.z)
        result.z = (mass / 12f) * (bounds.size.x * bounds.size.x + bounds.size.y * bounds.size.y)
        result.y = (mass / 12f) * (bounds.size.x * bounds.size.x + bounds.size.z * bounds.size.z)
        return result
    }
}
