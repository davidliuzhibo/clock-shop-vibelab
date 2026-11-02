package com.vrgames.clockshop

import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.vrgames.clockshop.content.CluesWindow
import com.vrgames.clockshop.content.GuideWindow
import com.vrgames.clockshop.content.ShopStage

const val CLUES_ID = "Clues"
const val GUIDE_ID = "Guide"

/**
 * Three linked spaces: the clock shop all around (a full-immersion Stage), the 线索簿 (the letter,
 * the clues, the photos and the sketch) and the 钟表图鉴 (true stories of each kind of clock). They
 * share [com.vrgames.clockshop.game.Game].
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme { ShopStage() }
        }
        WindowContainer(CLUES_ID, Form.Planar, { defaultSize = WindowContainerSize(820.dp, 1000.dp) }) {
            PicoTheme { CluesWindow() }
        }
        WindowContainer(GUIDE_ID, Form.Planar, { defaultSize = WindowContainerSize(820.dp, 1000.dp) }) {
            PicoTheme { GuideWindow() }
        }
    }
