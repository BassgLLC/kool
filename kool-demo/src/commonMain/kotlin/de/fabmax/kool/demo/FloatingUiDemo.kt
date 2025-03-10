package de.fabmax.kool.demo

import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.pipeline.shading.ModeledShader
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.scene.colorMesh
import de.fabmax.kool.scene.group
import de.fabmax.kool.scene.orbitInputTransform
import de.fabmax.kool.scene.ui.*

/**
 * @author fabmax
 */

class FloatingUiDemo : DemoScene("Floating UI") {
    override fun Scene.setupMainScene(ctx: KoolContext) {
        +orbitInputTransform { +camera }

        +group {
            onUpdate += {
                setIdentity()
                translate(0f, 0f, -7f)
                rotate((it.time * 60).toFloat(), Vec3f.X_AXIS)
                rotate((it.time * 17).toFloat(), Vec3f.Y_AXIS)
            }
            +colorMesh {
                generate {
                    scale(5f, 5f, 5f)
                    cube {
                        centered()
                        colored()
                    }
                }
                shader = ModeledShader.VertexColor()
            }
        }

        +embeddedUi(10f, 10f, dps(400f)) {
            content.customTransform = { translate(-content.dp(200f), -content.dp(200f), 0f) }

            +toggleButton("toggle-button") {
                layoutSpec.setOrigin(pcs(15f), pcs(-25f), zero())
                layoutSpec.setSize(pcs(70f), pcs(15f), full())

                text = "Toggle Button"
            }

            +label("label") {
                layoutSpec.setOrigin(pcs(15f), pcs(-45f), zero())
                layoutSpec.setSize(pcs(20f), pcs(15f), full())

                text = "Slider"
            }

            +slider("slider", 0.4f, 1f, 1f) {
                layoutSpec.setOrigin(pcs(35f), pcs(-45f), zero())
                layoutSpec.setSize(pcs(50f), pcs(15f), full())
                padding.left = uns(0f)

                onValueChanged += { value ->
                    root.content.alpha = value
                }
            }

            +textField("text-field") {
                layoutSpec.setOrigin(pcs(15f), pcs(-65f), zero())
                layoutSpec.setSize(pcs(70f), pcs(15f), full())
            }

            +button("toggle-theme") {
                layoutSpec.setOrigin(pcs(15f), pcs(-85f), zero())
                layoutSpec.setSize(pcs(70f), pcs(15f), full())
                text = "Toggle Theme"

                onClick += { _,_,_ ->
                    theme = if (theme == UiTheme.DARK) {
                        UiTheme.LIGHT
                    } else {
                        UiTheme.DARK
                    }
                }
            }
        }
    }
}

