package de.fabmax.kool.pipeline.shadermodel

import de.fabmax.kool.pipeline.AttributeType
import de.fabmax.kool.pipeline.ShaderStage

abstract class ShaderNode(val name: String, val graph: ShaderGraph, val allowedStages: Int = ShaderStage.ALL.mask) {
    val dependencies = mutableSetOf<ShaderNode>()
    val nodeId = graph.nextNodeId++

    fun dependsOn(nd: ShaderNode?) {
        if (nd != null) {
            dependencies += nd
        }
    }

    fun dependsOn(ndVar: ShaderNodeIoVar) {
        dependsOn(ndVar.node)
    }

    fun dependsOn(vararg ndVars: ShaderNodeIoVar) {
        ndVars.forEach { dependsOn(it) }
    }

    open fun setup(shaderGraph: ShaderGraph) {
        check(shaderGraph.stage.mask and allowedStages != 0) {
            "Unallowed shader stage (${shaderGraph.stage} for node $name"
        }
    }

    open fun generateCode(generator: CodeGenerator) { }
}

class ShaderNodeIoVar(val variable: ModelVar, val node: ShaderNode? = null) {
    val name: String get() = variable.name

    fun declare() = variable.declare()

    fun ref1f() = variable.ref1f()
    fun ref2f() = variable.ref2f()
    fun ref3f() = variable.ref3f()
    fun ref4f() = variable.ref4f()

    fun refAsType(targetType: AttributeType) = variable.refAsType(targetType)
}
