package de.fabmax.kool.physics

import de.fabmax.kool.KoolContext
import de.fabmax.kool.util.PerfTimer
import kotlin.math.min

/**
 * PhysicsStepper provides an interface for implementing different physics simulation time strategies.
 */
interface PhysicsStepper {
    fun stepSimulation(world: CommonPhysicsWorld, ctx: KoolContext): Float
}

/**
 * Simple synchronous physics stepper, which always advances the simulation by the current frame time, resulting
 * in smooth physics simulation over a wide range of frame rates. Since the physics time step depends on the current
 * frame time, physics simulation is not deterministic with this stepper.
 * Moreover, because simulation runs synchronously, the main thread is blocked while physics simulation is done.
 */
class SimplePhysicsStepper : PhysicsStepper {
    var maxSingleStepTime: Float = 0.02f
    var simTimeFactor = 1f

    private val perf = PerfTimer()
    var perfCpuTime = 0f
    var perfTimeFactor = 1f

    override fun stepSimulation(world: CommonPhysicsWorld, ctx: KoolContext): Float {
        var remainingStepTime = min(0.1f, ctx.deltaT * simTimeFactor)
        var timeAdvance = 0f

        perf.reset()
        while (remainingStepTime > 0.001f) {
            val singleStep = min(remainingStepTime, maxSingleStepTime)
            world.singleStepSync(singleStep)
            remainingStepTime -= singleStep
            timeAdvance += singleStep
        }

        val ms = perf.takeMs().toFloat()
        perfCpuTime = perfCpuTime * 0.8f + ms * 0.2f
        perfTimeFactor = perfTimeFactor * 0.9f + (timeAdvance / (ctx.deltaT * simTimeFactor)) * 0.1f

        return timeAdvance
    }
}

/**
 * Provides deterministic physics behavior by using a constant time step. Moreover, this stepper usually uses
 * asynchronous physics stepping, i.e. the bulk of physics simulation is done in parallel to graphics stuff resulting
 * in higher performance.
 * However, because a constant time step is used, physics may becomes jittery if the frame time diverges from physics
 * time step.
 */
class ConstantPhysicsStepper(val constantTimeStep: Float = 1f / 60f) : PhysicsStepper {
    private var isStepInProgress = false
    private var internalSimTime = 0.0
    private var desiredSimTime = 0.0

    var simTimeFactor = 1f
    var maxSubSteps = 5

    private var subStepLimit = maxSubSteps

    override fun stepSimulation(world: CommonPhysicsWorld, ctx: KoolContext): Float {
        var timeAdvance = 0f
        desiredSimTime += min(0.1f, ctx.deltaT * simTimeFactor)

        if (isStepInProgress) {
            world.fetchAsyncStepResults()
            isStepInProgress = false
            internalSimTime += constantTimeStep
            timeAdvance += constantTimeStep
        }

        var subSteps = subStepLimit
        while (shouldAdvance(internalSimTime, desiredSimTime, false) && subSteps-- > 0) {
            world.singleStepSync(constantTimeStep)
            internalSimTime += constantTimeStep
            timeAdvance += constantTimeStep
        }

        if (subSteps == 0 && subStepLimit > 1) {
            subStepLimit--
        } else if(subStepLimit < maxSubSteps) {
            subStepLimit++
        }

        if (shouldAdvance(internalSimTime, desiredSimTime + constantTimeStep, true)) {
            world.singleStepAsync(constantTimeStep)
            isStepInProgress = true
        }

        return timeAdvance
    }

    private fun shouldAdvance(currentTime: Double, desiredTime: Double, isFirst: Boolean): Boolean {
        if (isFirst) {
            return currentTime + constantTimeStep < desiredTime
        } else {
            // only do additional steps if it's really necessary (i.e. allow some hysteresis to reduce jitter)
            return currentTime + constantTimeStep * 1.5f < desiredTime
        }
    }
}
