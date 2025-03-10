package de.fabmax.kool.demo.physics.vehicle

import de.fabmax.kool.math.Mat4f
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.physics.RigidDynamic
import de.fabmax.kool.physics.RigidStatic
import de.fabmax.kool.physics.Shape
import de.fabmax.kool.physics.geometry.BoxGeometry
import de.fabmax.kool.physics.joints.RevoluteJoint
import de.fabmax.kool.pipeline.deferred.deferredPbrShader
import de.fabmax.kool.pipeline.shading.Albedo
import de.fabmax.kool.scene.colorMesh
import de.fabmax.kool.scene.geometry.MeshBuilder
import de.fabmax.kool.scene.geometry.multiShape
import de.fabmax.kool.scene.geometry.simpleShape

object Playground {

    fun makePlayground(vehicleWorld: VehicleWorld) {
        makeBoxes(Mat4f().translate(-20f, 0f, 30f), vehicleWorld)
        makeRocker(Mat4f().translate(0f, 0f, 30f), vehicleWorld)

        vehicleWorld.deferredPipeline.sceneContent += colorMesh {
            generate {
                makeRamp(Mat4f().translate(-20f, 0f, 80f).rotate(180f, Vec3f.Y_AXIS))
                makeBumps(Mat4f().translate(20f, 0f, 0f))
                makeHalfPipe(Mat4f().translate(-40f, 0f, 30f).rotate(90f, 0f, -1f, 0f))
            }
            shader = deferredPbrShader {
                albedoSource = Albedo.VERTEX_ALBEDO
            }

            vehicleWorld.addStaticCollisionBody(geometry)
        }
    }

    private fun makeBoxes(frame: Mat4f, world: VehicleWorld) {
        val n = 6
        val size = 2f
        val stepX = size * 1.2f
        for (r in 0 until n) {
            val c = n - r
            val x = (c - 1) * stepX * -0.5f

            for (i in 0 until c) {
                val boxShape = BoxGeometry(Vec3f(size))
                val body = RigidDynamic(250f)
                body.attachShape(Shape(boxShape, world.defaultMaterial))
                body.setSimulationFilterData(world.obstacleSimFilterData)
                body.setQueryFilterData(world.obstacleQryFilterData)
                val pos = MutableVec3f(x + i * stepX, size * 0.5f + r * size, 0f)
                frame.transform(pos)
                body.position = pos
                world.physics.addActor(body)

                val color = if (i % 2 == 0) VehicleDemo.color(400) else VehicleDemo.color(200)
                world.deferredPipeline.sceneContent += world.toPrettyMesh(body, color)
            }
        }
    }

    private fun makeRocker(frame: Mat4f, world: VehicleWorld) {
        val anchor = RigidStatic().apply {
            setSimulationFilterData(world.obstacleSimFilterData)
            setQueryFilterData(world.obstacleQryFilterData)
            attachShape(Shape(BoxGeometry(Vec3f(7.5f, 1.5f, 0.3f)), world.defaultMaterial))
            position = frame.transform(MutableVec3f(0f, 0.75f, 0f))
        }
        val rocker = RigidDynamic(500f).apply {
            setSimulationFilterData(world.obstacleSimFilterData)
            setQueryFilterData(world.obstacleQryFilterData)
            attachShape(Shape(BoxGeometry(Vec3f(7.5f, 0.15f, 15f)), world.defaultMaterial))
            position = frame.transform(MutableVec3f(0f, 1.7f, 0f))
        }
        world.physics.addActor(anchor)
        world.physics.addActor(rocker)
        world.deferredPipeline.sceneContent += world.toPrettyMesh(anchor, VehicleDemo.color(400))
        world.deferredPipeline.sceneContent += world.toPrettyMesh(rocker, VehicleDemo.color(200))

        RevoluteJoint(anchor, rocker, Mat4f().translate(0f, 0.85f, 0f), Mat4f().translate(0f, 0f, 0.2f))
    }

    private fun MeshBuilder.makeRamp(frame: Mat4f) {
        color = VehicleDemo.color(200)
        withTransform {
            transform.mul(frame)
            rotate(-11f, 0f, 0f)
            cube {
                size.set(10f, 2f, 10f)
                centered()
            }
        }
    }

    private fun MeshBuilder.makeBumps(frame: Mat4f) {
        for (i in 0 until 30) {
            val c = if (i % 2 == 0) VehicleDemo.color(400) else VehicleDemo.color(200)
            for (s in -1 .. 1 step 2) {
                withTransform {
                    transform.mul(frame)
                    translate(2f * s, -0.375f, i * 3.1f + s * 0.4f)
                    rotate(90f, Vec3f.Z_AXIS)
                    translate(0f, -2f, 0f)
                    color = c
                    cylinder {
                        radius = 0.5f
                        height = 4f
                        steps = 32
                    }
                }
            }
        }
    }

    private fun MeshBuilder.makeHalfPipe(frame: Mat4f) {
        withTransform {
            transform.mul(frame)
            profile {
                val multiShape = multiShape {
                    simpleShape(false) {
                        xy(24f, 0f)
                        xy(24f, 10f)
                    }
                    simpleShape(false) {
                        xy(24f, 10f)
                        xy(20f, 10f)
                    }
                    simpleShape(false) {
                        xyArc(Vec2f(20f, 10f), Vec2f(10f, 10f), -90f, 20)
                    }
                }

                color = VehicleDemo.color(200)
                sample()
                val inds = mutableListOf<Int>()
                inds += multiShape.shapes[0].sampledVertIndices
                inds += multiShape.shapes[1].sampledVertIndices
                inds += multiShape.shapes[2].sampledVertIndices
                fillPolygon(inds.reversed())

                sample(false)
                for (i in 0 until 5) {
                    translate(0f, 0f, 5f)
                    sample()
                }
                for (i in 0 until 50) {
                    rotate(180f / 50f, 0f, -1f, 0f)
                    sample()
                }
                for (i in 0 until 5) {
                    translate(0f, 0f, 5f)
                    sample()
                }
                sample(false)
                inds.clear()
                inds += multiShape.shapes[0].sampledVertIndices
                inds += multiShape.shapes[1].sampledVertIndices
                inds += multiShape.shapes[2].sampledVertIndices
                fillPolygon(inds)

                geometry.generateNormals()
            }
        }
    }
}