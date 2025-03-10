package de.fabmax.kool.demo.physics.joints

import de.fabmax.kool.AssetManager
import de.fabmax.kool.KoolContext
import de.fabmax.kool.demo.Demo
import de.fabmax.kool.demo.DemoScene
import de.fabmax.kool.demo.controlUi
import de.fabmax.kool.math.*
import de.fabmax.kool.math.spatial.BoundingBox
import de.fabmax.kool.physics.*
import de.fabmax.kool.physics.geometry.BoxGeometry
import de.fabmax.kool.physics.geometry.ConvexMeshGeometry
import de.fabmax.kool.physics.geometry.CylinderGeometry
import de.fabmax.kool.physics.geometry.PlaneGeometry
import de.fabmax.kool.physics.joints.RevoluteJoint
import de.fabmax.kool.pipeline.RenderPass
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.ao.AoPipeline
import de.fabmax.kool.pipeline.ibl.EnvironmentHelper
import de.fabmax.kool.pipeline.ibl.EnvironmentMaps
import de.fabmax.kool.pipeline.shading.pbrShader
import de.fabmax.kool.pipeline.shading.unlitShader
import de.fabmax.kool.scene.*
import de.fabmax.kool.toString
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.ColorGradient
import de.fabmax.kool.util.MdColor
import de.fabmax.kool.util.SimpleShadowMap
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max

class JointsDemo : DemoScene("Physics - Joints") {

    private var physicsWorld: PhysicsWorld? = null
    private val physicsStepper = SimplePhysicsStepper()

    private var motorGearConstraint: RevoluteJoint? = null
    private var motorStrength = 50f
    private var motorSpeed = 1.5f
    private var motorDirection = 1f
    private var numLinks = 40
    private val joints = mutableListOf<RevoluteJoint>()

    private val physMeshes = BodyMeshes(false).apply { isVisible = false }
    private val niceMeshes = BodyMeshes(true).apply { isVisible = true }
    private lateinit var constraintInfo: ConstraintsInfoMesh
    private var resetPhysics = false

    private val shadows = mutableListOf<SimpleShadowMap>()
    private lateinit var aoPipeline: AoPipeline
    private lateinit var ibl: EnvironmentMaps
    private lateinit var groundAlbedo: Texture2d
    private lateinit var groundNormal: Texture2d

    private val staticCollGroup = 1
    private val staticSimFilterData = FilterData().apply {
        setCollisionGroup(staticCollGroup)
        clearCollidesWith(staticCollGroup)
    }
    private val material = Material(0.5f)

    override suspend fun AssetManager.loadResources(ctx: KoolContext) {
        ibl = EnvironmentHelper.hdriEnvironment(mainScene, "${Demo.envMapBasePath}/colorful_studio_1k.rgbe.png", this)

        Physics.awaitLoaded()
        val world = PhysicsWorld()
        world.simStepper = physicsStepper
        physicsWorld = world
        resetPhysics = true
        constraintInfo = ConstraintsInfoMesh().apply { isVisible = false }
        mainScene += constraintInfo

        groundAlbedo = loadAndPrepareTexture("${Demo.pbrBasePath}/tile_flat/tiles_flat_fine.png")
        groundNormal = loadAndPrepareTexture("${Demo.pbrBasePath}/tile_flat/tiles_flat_fine_normal.png")

        world.registerHandlers(mainScene)
    }

    override fun Scene.setupMainScene(ctx: KoolContext) {
        defaultCamTransform().apply {
            setMouseRotation(-20f, -20f)
            zoom = 50.0
            maxZoom = 200.0
        }
        (camera as PerspectiveCamera).apply {
            clipNear = 1f
            clipFar = 1000f
        }

        // light setup
        lightSetup()

        // group containing physics bodies
        +physMeshes
        +niceMeshes

        // ground plane
        mainScene += textureMesh(isNormalMapped = true) {
            isCastingShadow = false
            generate {
                rotate(-90f, Vec3f.X_AXIS)
                rect {
                    size.set(250f, 250f)
                    origin.set(-size.x * 0.5f, -size.y * 0.5f, -20f)
                    generateTexCoords(15f)
                }
            }
            shader = pbrShader {
                useAlbedoMap(groundAlbedo)
                useNormalMap(groundNormal)
                useScreenSpaceAmbientOcclusion(aoPipeline.aoMap)
                useImageBasedLighting(ibl)
                shadowMaps += shadows
            }
        }

        mainScene += Skybox.cube(ibl.reflectionMap, 1f)

        onUpdate += {
            if (resetPhysics) {
                resetPhysics = false
                makePhysicsScene()
            }
        }
    }

    private fun Scene.lightSetup() {
        aoPipeline = AoPipeline.createForward(this)
        lighting.apply {
            lights.clear()
            val l1 = Vec3f(80f, 120f, 100f)
            val l2 = Vec3f(-30f, 100f, 100f)
            lights += Light().apply {
                setSpot(l1, MutableVec3f(l1).scale(-1f).norm(), 45f)
                setColor(Color.WHITE.mix(MdColor.AMBER, 0.1f), 50000f)
            }
            lights += Light().apply {
                setSpot(l2, MutableVec3f(l2).scale(-1f).norm(), 45f)
                setColor(Color.WHITE.mix(MdColor.LIGHT_BLUE, 0.1f), 25000f)
            }
        }
        shadows.add(SimpleShadowMap(this, 0).apply {
            clipNear = 100f
            clipFar = 500f
            shaderDepthOffset = -0.2f
            shadowBounds = BoundingBox(Vec3f(-75f, -20f, -75f), Vec3f(75f, 20f, 75f))
        })
        shadows.add(SimpleShadowMap(this, 1).apply {
            clipNear = 100f
            clipFar = 500f
            shaderDepthOffset = -0.2f
            shadowBounds = BoundingBox(Vec3f(-75f, -20f, -75f), Vec3f(75f, 20f, 75f))
        })
    }

    private fun makePhysicsScene() {
        physMeshes.clearBodies()
        niceMeshes.clearBodies()
        joints.forEach { it.release() }
        joints.clear()

        physicsWorld?.apply {
            clear()

            val groundPlane = RigidStatic()
            groundPlane.setSimulationFilterData(staticSimFilterData)
            groundPlane.attachShape(Shape(PlaneGeometry(), material))
            groundPlane.position = Vec3f(0f, -20f, 0f)
            groundPlane.setRotation(Mat3f().rotate(90f, Vec3f.Z_AXIS))
            addActor(groundPlane)
        }

        val frame = Mat4f().rotate(90f, Vec3f.Z_AXIS)
        makeGearChain(numLinks, frame)
        updateMotor()
    }

    override fun dispose(ctx: KoolContext) {
        super.dispose(ctx)
        joints.forEach { it.release() }
        physicsWorld?.apply {
            clear()
            release()
        }
        material.release()
        groundAlbedo.dispose()
        groundNormal.dispose()
    }

    override fun setupMenu(ctx: KoolContext) = controlUi {
        section("Physics") {
            sliderWithValue("Number of Links:", numLinks.toFloat() / 2, 10f, 50f, textFormat = { "${it.toInt() * 2}" }) {
                val lnks = value.toInt() * 2
                if (lnks != numLinks) {
                    numLinks = lnks
                    resetPhysics = true
                }
            }
            sliderWithValue("Motor Strength:", motorStrength, 0f, 100f, 0) {
                motorStrength = value
                updateMotor()
            }
            sliderWithValue("Motor Speed:", motorSpeed, 0f, 10f, 1) {
                motorSpeed = value
                updateMotor()
            }
            toggleButton("Reverse Motor Direction", motorDirection < 0) {
                motorDirection = if (isEnabled) -1f else 1f
                updateMotor()
            }
        }
        section("Rendering") {
            val showNiceMeshes = toggleButton("Draw Nice Meshes", niceMeshes.isVisible) { }
            val showPhysMeshes = toggleButton("Draw Physics Meshes", physMeshes.isVisible) { }
            var ignoreStateChange = false
            showNiceMeshes.onStateChange += {
                if (!ignoreStateChange) {
                    ignoreStateChange = true
                    niceMeshes.isVisible = isEnabled
                    physMeshes.isVisible = !isEnabled
                    showPhysMeshes.isEnabled = !isEnabled
                    ignoreStateChange = false
                }
            }
            showPhysMeshes.onStateChange += {
                if (!ignoreStateChange) {
                    ignoreStateChange = true
                    physMeshes.isVisible = isEnabled
                    niceMeshes.isVisible = !isEnabled
                    showNiceMeshes.isEnabled = !isEnabled
                    ignoreStateChange = false
                }
            }
            toggleButton("Draw Joint Indicators", false) {
                constraintInfo.isVisible = isEnabled
            }
        }
        section("Performance") {
            textWithValue("Physics:", "0.00 ms").apply {
                onUpdate += {
                    text = "${physicsStepper.perfCpuTime.toString(2)} ms"
                }
            }
            textWithValue("Time Factor:", "1.00 x").apply {
                onUpdate += {
                    text = "${physicsStepper.perfTimeFactor.toString(2)} x"
                }
            }
            textWithValue("Number of Bodies:", "").apply {
                onUpdate += {
                    text = "${physicsWorld?.actors?.size ?: 0}"
                }
            }
            textWithValue("Number of Joints:", "").apply {
                onUpdate += {
                    text = "${joints.size}"
                }
            }
        }
    }

    private fun updateMotor() {
        motorGearConstraint?.apply {
            enableAngularMotor(motorSpeed * motorDirection, motorStrength)
        }
    }

    private fun computeAxleDist(): Float {
        val linkLen = 4f
        return (numLinks - 12) / 2 * linkLen
    }

    private fun makeGearChain(numLinks: Int, frame: Mat4f) {
        val world = physicsWorld ?: return

        val linkMass = 1f
        val gearMass = 10f
        val gearR = 6.95f
        val axleDist = computeAxleDist()
        val tension = 0.05f

        if (numLinks % 2 != 0) {
            throw IllegalArgumentException("numLinks must be even")
        }

        makeGearAndAxle(gearR, Vec3f(0f, axleDist / 2f, 0f), gearMass, true, frame)
        makeGearAndAxle(gearR, Vec3f(0f, -axleDist / 2f, 0f), gearMass, false, frame)
        makeChain(linkMass, tension, gearR, axleDist, frame, world)
    }

    private fun makeChain(linkMass: Float, tension: Float, gearR: Float, axleDist: Float, frame: Mat4f, world: PhysicsWorld) {
        val t = Mat4f().set(frame).translate(0f, axleDist / 2f + gearR + 0.6f, 0f)
        val r = Mat3f()

        val rotLinks = mutableSetOf(1, 2, 3, numLinks - 2, numLinks - 1, numLinks)
        for (i in (numLinks / 2 - 2)..(numLinks / 2 + 3)) {
            rotLinks += i
        }

        val firstOuter = makeOuterChainLink(linkMass)
        firstOuter.position = t.getOrigin(MutableVec3f())
        firstOuter.setRotation(t.getRotation(r))
        world.addActor(firstOuter)

        var prevInner = makeInnerChainLink(linkMass)
        t.translate(1.5f, 0f, 0f)
        t.rotate(0f, 0f, -15f)
        t.translate(0.5f, 0f, 0f)
        prevInner.position = t.getOrigin(MutableVec3f())
        prevInner.setRotation(t.getRotation(r))
        world.addActor(prevInner)

        connectLinksOuterInner(firstOuter, prevInner, tension)

        physMeshes.linksO += firstOuter
        niceMeshes.linksO += firstOuter
        physMeshes.linksI += prevInner
        niceMeshes.linksI += prevInner

        for (i in 1 until numLinks) {
            t.translate(0.5f, 0f, 0f)
            if (i in rotLinks) {
                t.rotate(0f, 0f, -15f)
            }
            t.translate(1.5f, 0f, 0f)

            val outer = makeOuterChainLink(linkMass * 2)
            outer.position = t.getOrigin(MutableVec3f())
            outer.setRotation(t.getRotation(r))
            world.addActor(outer)

            connectLinksInnerOuter(prevInner, outer, tension)

            prevInner = makeInnerChainLink(linkMass)
            t.translate(1.5f, 0f, 0f)
            if ((i + 1) in rotLinks) {
                t.rotate(0f, 0f, -15f)
            }
            t.translate(0.5f, 0f, 0f)
            prevInner.position = t.getOrigin(MutableVec3f())
            prevInner.setRotation(t.getRotation(r))
            world.addActor(prevInner)

            connectLinksOuterInner(outer, prevInner, tension)

            physMeshes.linksO += outer
            niceMeshes.linksO += outer
            physMeshes.linksI += prevInner
            niceMeshes.linksI += prevInner
        }

        connectLinksInnerOuter(prevInner, firstOuter, tension)
    }

    private fun connectLinksOuterInner(outer: RigidDynamic, inner: RigidDynamic, t: Float) {
        val hinge = RevoluteJoint(outer, inner,
            Vec3f(1.5f - t, 0f, 0f), Vec3f(-0.5f, 0f, 0f),
            Vec3f.Z_AXIS, Vec3f.Z_AXIS)
        joints += hinge
    }

    private fun connectLinksInnerOuter(inner: RigidDynamic, outer: RigidDynamic, t: Float) {
        val hinge = RevoluteJoint(outer, inner,
            Vec3f(-1.5f + t, 0f, 0f), Vec3f(0.5f, 0f, 0f),
            Vec3f.Z_AXIS, Vec3f.Z_AXIS)
        joints += hinge
    }

    private fun makeGearAndAxle(gearR: Float, origin: Vec3f, gearMass: Float, isDriven: Boolean, frame: Mat4f) {
        val world = physicsWorld ?: return

        val axleGeom = CylinderGeometry(7f, 1f)
        val axle = RigidStatic()
        axle.setSimulationFilterData(staticSimFilterData)
        axle.attachShape(Shape(axleGeom, material))
        axle.setRotation(frame.getRotation(Mat3f()).rotate(0f, -90f, 0f))
        axle.position = frame.transform(MutableVec3f(origin))
        world.addActor(axle)
        physMeshes.axles += axle
        niceMeshes.axles += axle
        axleGeom.release()

        val gear = makeGear(gearR, gearMass)
        gear.setRotation(frame.getRotation(Mat3f()))
        gear.position = frame.transform(MutableVec3f(origin))
        world.addActor(gear)
        physMeshes.gears += gear
        niceMeshes.gears += gear

        val motor = RevoluteJoint(axle, gear,
            Vec3f(0f, 0f, 0f), Vec3f(0f, 0f, 0f),
            Vec3f.X_AXIS, Vec3f.Z_AXIS)
        joints += motor
        if (isDriven) {
            motorGearConstraint = motor
        }
    }

    private fun makeGear(gearR: Float, mass: Float): RigidDynamic {
        val s = 1f
        val toothH = 1f * s
        val toothBb = 0.55f * s
        val toothBt = 0.4f * s
        val toothWb = 1f * s
        val toothWt = 0.7f * s
        val gearShapes = mutableListOf<Shape>()

        val toothPts = listOf(
            Vec3f(toothWt, gearR + toothH, -toothBt), Vec3f(toothWt, gearR + toothH, toothBt),
            Vec3f(-toothWt, gearR + toothH, -toothBt), Vec3f(-toothWt, gearR + toothH, toothBt),

            Vec3f(toothWb, gearR - 0.1f, -toothBb), Vec3f(toothWb, gearR - 0.1f, toothBb),
            Vec3f(-toothWb, gearR - 0.1f, -toothBb), Vec3f(-toothWb, gearR - 0.1f, toothBb)
        )
        val toothGeom = ConvexMeshGeometry(toothPts)
        val cylGeom = CylinderGeometry(2.5f, gearR)

        gearShapes += Shape(cylGeom, material, Mat4f().rotate(0f, 90f, 0f))
        for (i in 0..11) {
            gearShapes += Shape(toothGeom, material, Mat4f().rotate(0f, 0f, 30f * i))
        }

        val gearFilterData = FilterData().apply {
            setCollisionGroup(0)
            clearCollidesWith(staticCollGroup)
        }
        val gear = RigidDynamic(mass)
        gear.setSimulationFilterData(gearFilterData)
        gearShapes.forEach { shape ->
            gear.attachShape(shape)
        }
        toothGeom.release()
        cylGeom.release()
        return gear
    }

    private fun makeOuterChainLink(mass: Float): RigidDynamic {
        val boxA = BoxGeometry(Vec3f(3.4f, 0.8f, 0.3f))
        val boxB = BoxGeometry(Vec3f(3.4f, 0.8f, 0.3f))

        val shapes = mutableListOf<Shape>()
        shapes += Shape(boxA, material, Mat4f().translate(0f, 0f, 0.75f))
        shapes += Shape(boxB, material, Mat4f().translate(0f, 0f, -0.75f))

        val link = RigidDynamic(mass)
        shapes.forEach { shape ->
            link.attachShape(shape)
        }
        boxA.release()
        boxB.release()
        return link
    }

    private fun makeInnerChainLink(mass: Float): RigidDynamic {
        val w1 = 0.95f
        val h1 = 0.2f
        val w2 = 0.7f
        val h2 = 0.6f
        val d = 0.5f
        val points = listOf(
            Vec3f(-w1, -h1, -d), Vec3f(-w1, -h1, d),
            Vec3f(-w1,  h1, -d), Vec3f(-w1,  h1, d),
            Vec3f( w1, -h1, -d), Vec3f( w1, -h1, d),
            Vec3f( w1,  h1, -d), Vec3f( w1,  h1, d),

            Vec3f(-w2, -h2, -d), Vec3f(-w2, -h2, d),
            Vec3f(-w2,  h2, -d), Vec3f(-w2,  h2, d),
            Vec3f( w2, -h2, -d), Vec3f( w2, -h2, d),
            Vec3f( w2,  h2, -d), Vec3f( w2,  h2, d),
        )
        val geom = ConvexMeshGeometry(points)

        val link = RigidDynamic(mass)
        link.attachShape(Shape(geom, material))
        geom.release()
        return link
    }

    private inner class BodyMesh(val color: Color, val onCreate: (Mesh) -> Unit) {
        var mesh: Mesh? = null

        var factory: (RigidActor) -> Mesh = { proto ->
            colorMesh {
                isFrustumChecked = false
                instances = MeshInstanceList(listOf(MeshInstanceList.MODEL_MAT))
                generate {
                    color = this@BodyMesh.color
                    proto.shapes.forEach { shape ->
                        withTransform {
                            transform.mul(shape.localPose)
                            shape.geometry.generateMesh(this)
                        }
                    }
                }
                shader = pbrShader {
                    roughness = 1f
                    isInstanced = true
                    shadowMaps += shadows
                    useImageBasedLighting(ibl)
                    useScreenSpaceAmbientOcclusion(aoPipeline.aoMap)
                }
            }
        }

        fun getOrCreate(protoBody: RigidActor): Mesh {
            if (mesh == null) {
                mesh = factory(protoBody)
                onCreate(mesh!!)
            }
            return mesh!!
        }

        fun updateInstances(bodies: List<RigidActor>) {
            if (bodies.isNotEmpty()) {
                getOrCreate(bodies[0]).instances!!.apply {
                    clear()
                    addInstances(bodies.size) { buf ->
                        for (i in bodies.indices) {
                            buf.put(bodies[i].transform.matrix)
                        }
                    }
                }
            }
        }
    }

    private inner class BodyMeshes(isNice: Boolean): Group() {
        var linkMeshO = BodyMesh(MdColor.BLUE_GREY.toLinear()) { addNode(it) }
        var linkMeshI = BodyMesh(MdColor.BLUE_GREY toneLin 350) { addNode(it) }
        var gearMesh = BodyMesh(MdColor.BLUE_GREY toneLin 200) { addNode(it) }
        var axleMesh = BodyMesh(MdColor.BLUE_GREY toneLin 700) { addNode(it) }

        val linksO = mutableListOf<RigidDynamic>()
        val linksI = mutableListOf<RigidDynamic>()
        val gears = mutableListOf<RigidDynamic>()
        val axles = mutableListOf<RigidStatic>()

        init {
            isFrustumChecked = false

            if (isNice) {
                linkMeshO.factory = { GearChainMeshGen.makeNiceOuterLinkMesh(ibl, aoPipeline.aoMap, shadows) }
                linkMeshI.factory = { GearChainMeshGen.makeNiceInnerLinkMesh(ibl, aoPipeline.aoMap, shadows) }
                gearMesh.factory = { GearChainMeshGen.makeNiceGearMesh(ibl, aoPipeline.aoMap, shadows) }
                axleMesh.factory = { GearChainMeshGen.makeNiceAxleMesh(ibl, aoPipeline.aoMap, shadows) }
            }

            onUpdate += {
                linkMeshO.updateInstances(linksO)
                linkMeshI.updateInstances(linksI)
                gearMesh.updateInstances(gears)
                axleMesh.updateInstances(axles)
            }
        }

        fun clearBodies() {
            linksO.clear()
            linksI.clear()
            gears.clear()
            axles.clear()
        }
    }

    private inner class ConstraintsInfoMesh : LineMesh() {
        val gradient = ColorGradient.RED_YELLOW_GREEN.inverted()

        // keep temp vectors as members to not re-allocate them all the time
        val tmpAx = MutableVec3f()
        val tmpP1 = MutableVec3f()
        val tmpP2 = MutableVec3f()
        val tmpA1 = MutableVec3f()
        val tmpA2 = MutableVec3f()

        val tmpL1 = MutableVec3f()
        val tmpL2 = MutableVec3f()

        init {
            isCastingShadow = false
            shader = unlitShader {
                lineWidth = 3f
            }
        }

        override fun update(updateEvent: RenderPass.UpdateEvent) {
            if (isVisible) {
                clear()
                joints.forEach {
                    renderRevoluteConstraint(it)
                }
            }
            super.update(updateEvent)
        }

        private fun renderRevoluteConstraint(rc: RevoluteJoint) {
            val tA = rc.bodyA.transform
            val tB = rc.bodyB.transform

            rc.frameA.transform(tmpAx.set(Vec3f.X_AXIS), 0f)
            rc.frameA.transform(tmpP1.set(Vec3f.ZERO), 1f)
            tA.transform(tmpA1.set(tmpAx), 0f)
            tA.transform(tmpP1)
            val lenA = rc.bodyA.worldBounds.size * tmpAx * 0.5f + 1f

            rc.frameB.transform(tmpAx.set(Vec3f.X_AXIS), 0f)
            rc.frameB.transform(tmpP2.set(Vec3f.ZERO), 1f)
            tB.transform(tmpA2.set(tmpAx), 0f)
            tB.transform(tmpP2)
            val lenB = rc.bodyB.worldBounds.size * tmpAx * 0.5f + 1f

            val drawLen = max(lenA, lenB)
            val diff = tmpP1.distance(tmpP2) + abs(acos(tmpA1 * tmpA2).toDeg()) / 20
            val color = gradient.getColor(diff, 0f, 0.5f)

            tmpL1.set(tmpA1).scale(drawLen).add(tmpP1)
            tmpL2.set(tmpA1).scale(-drawLen).add(tmpP1)
            addLine(tmpL1, tmpL2, color)

            tmpL1.set(tmpA2).scale(drawLen).add(tmpP2)
            tmpL2.set(tmpA2).scale(-drawLen).add(tmpP2)
            addLine(tmpL1, tmpL2, color)

            tmpL1.set(tmpA1).scale(drawLen).add(tmpP1)
            tmpL2.set(tmpA2).scale(drawLen).add(tmpP2)
            addLine(tmpL1, tmpL2, color)

            tmpL1.set(tmpA1).scale(-drawLen).add(tmpP1)
            tmpL2.set(tmpA2).scale(-drawLen).add(tmpP2)
            addLine(tmpL1, tmpL2, color)
        }
    }
}