package com.vrgames.clockshop.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import com.vrgames.clockshop.CLUES_ID
import com.vrgames.clockshop.GUIDE_ID
import com.vrgames.clockshop.core.Shop
import com.vrgames.clockshop.game.Game

const val PANEL_CARD = "card"
const val PANEL_HUD = "hud"
const val PANEL_TOOL = "tool"
const val PANEL_RESULT = "result"
const val PANEL_MENU = "menu"
const val PANEL_NOTE = "note"

// Ink on the cream letter paper in the mahogany frames.
val LabelInk = Color(0xFF2B1D12)
val Brass = Color(0xFF7A2E12)
val Dim = Color(0xFF6B5A47)
val Jade = Color(0xFF1F5B3A)
val Clay = Color(0xFFA33A17)
private val PanelBg = Color(0xFFEFE3C8)
/** Window backdrop: the system glass can be light, and every text color here is meant for dark. */
val WindowBg = Color(0xFFEFE3C8)

@Composable
fun PhysicalLengthConverter.m(meters: Float): Dp = lengthToDp(meters, LengthUnit.Meters)

@Composable
fun PhysicalLengthConverter.sz(meters: Float) = m(meters).value.sp

@Composable
private fun Letter(c: PhysicalLengthConverter, width: Float, height: Float, content: @Composable () -> Unit) {
    Column(
        Modifier.width(c.m(width)).height(c.m(height)).padding(horizontal = c.m(0.016f), vertical = c.m(0.006f)),
        verticalArrangement = Arrangement.spacedBy(c.m(0.006f), Alignment.CenterVertically),
    ) { content() }
}

@Composable
private fun PanelBox(c: PhysicalLengthConverter, width: Float, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.014f))
    Column(
        Modifier.width(c.m(width)).clip(shape).background(PanelBg).border(c.m(0.0012f), Color(0x55E2B75A), shape).padding(c.m(0.014f)),
        verticalArrangement = Arrangement.spacedBy(c.m(0.007f)),
    ) { content() }
}

/** The step that moves the game on, filled in brass with dark text (the dark buttons read as plain text). */
@Composable
fun CallToAction(c: PhysicalLengthConverter, text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.012f))
    Text(
        text,
        Modifier.clip(shape).background(Brass).border(c.m(0.002f), Color(0xFFFFF1C9), shape)
            .clickable { onClick() }.padding(horizontal = c.m(0.018f), vertical = c.m(0.01f)),
        fontSize = c.sz(0.02f),
        color = Color(0xFF1B1408),
    )
}

@Composable
fun Pill(c: PhysicalLengthConverter, text: String, color: Color = Dim, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.01f))
    Text(
        text,
        Modifier.clip(shape).border(c.m(0.0015f), color, shape).clickable { onClick() }.padding(horizontal = c.m(0.011f), vertical = c.m(0.007f)),
        fontSize = c.sz(0.015f),
        color = color,
    )
}

/** Before the game: the master's letter, how to play, and the brass button. */
@Composable
fun CardPanel(c: PhysicalLengthConverter, resumed: Boolean) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    Letter(c, 0.52f, 0.3f) {
        Text("钟表店密室", fontSize = c.sz(0.032f), color = Brass)
        Text("老钟表匠走了，给你留下这间铺子和一封信：“铺子里藏着我一生最得意的作品。钟会带你回到我想让你看见的时刻。”", fontSize = c.sz(0.016f), color = LabelInk)
        Text("怎么玩", fontSize = c.sz(0.018f), color = Jade)
        Text("· 看着东西，捏一下：拿起、打开、拨动。\n· 拿到的东西放在面前的托盘里：先捏一下托盘里的东西选中它，再捏要用它的地方。\n· 捏一下钟面，用按钮拨钟；房间会回到钟上的那个时刻。\n· 卡住了就点「提示」。四个房间，每间藏一枚怀表零件。", fontSize = c.sz(0.015f), color = LabelInk)
        CallToAction(c, if (resumed) "继续 →" else "推开店门 →") { Game.start() }
    }
}

/** Beside the tray: the room, the parts found, what is in hand, and the hint button. */
@Composable
fun HudPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val s = Game.shop
    Letter(c, 0.34f, 0.27f) {
        Text(s.room.title, fontSize = c.sz(0.022f), color = Brass)
        Text("怀表零件 " + (1..6).joinToString("") { if (it in s.parts) "●" else "○" }, fontSize = c.sz(0.016f), color = LabelInk)
        val sel = Game.selected
        if (sel != null) Text("手里：${sel.label}", fontSize = c.sz(0.015f), color = Jade)
        else if (s.tray.isNotEmpty()) Text("捏托盘里的东西选中它", fontSize = c.sz(0.013f), color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
            CallToAction(c, "提示") { Game.hint() }
            Pill(c, "线索簿", Brass) { toggle(CLUES_ID) }
        }
    }
}

/** The controls of the clock or the dials just pinched (fine work is done here, close to the hand). */
@Composable
fun ToolPanel(c: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val t = Game.tool ?: return
    val s = Game.shop
    Letter(c, 0.44f, 0.24f) {
        when {
            t.startsWith("clock:") -> {
                val id = t.removePrefix("clock:")
                Text("${CLOCK_NAMES[id] ?: "钟"}　${Game.clockText(id)}", fontSize = c.sz(0.022f), color = Brass)
                Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f)), verticalAlignment = Alignment.CenterVertically) {
                    Pill(c, "−1 小时", LabelInk) { Game.turnClock(id, -60) }
                    Pill(c, "−5 分", LabelInk) { Game.turnClock(id, -5) }
                    Pill(c, "+5 分", LabelInk) { Game.turnClock(id, 5) }
                    CallToAction(c, "+1 小时") { Game.turnClock(id, 60) }
                }
                Text("也可以捏住钟面，绕着画圈拨动指针。", fontSize = c.sz(0.013f), color = Dim)
            }
            t == "safe" -> {
                Text("保险柜的密码转盘", fontSize = c.sz(0.022f), color = Brass)
                Dials(c, listOf("safe1" to "第一位", "safe2" to "第二位", "safe3" to "第三位")) { "${s.dials[it] ?: 0}" }
                if ("safe" !in s.opened) Text("三位都对了，柜门会自己打开。", fontSize = c.sz(0.013f), color = Dim)
            }
            t == "rings" -> {
                Text("天文钟的三个环", fontSize = c.sz(0.022f), color = Brass)
                Dials(c, listOf("ring1" to "日环（外）", "ring2" to "月环（中）", "ring3" to "星环（内）")) {
                    val n = s.dials[it] ?: 0
                    "${if (n == 0) 12 else n} 点"
                }
                Text("对照墙上的三块铜牌：日、月、星。", fontSize = c.sz(0.014f), color = Dim)
            }
            t == "pendulum" -> {
                Text("钟摆的摆长", fontSize = c.sz(0.022f), color = Brass)
                Dials(c, listOf("pend" to "摆长")) { "${Shop.PENDULUM_LENGTH[s.pendulum]} 米" }
                Text("现在一来一回 ${Shop.PENDULUM_LABEL[s.pendulum]}。摆越长，摆得越慢。", fontSize = c.sz(0.015f), color = LabelInk)
            }
            t == "calendar" -> {
                Text("万年历", fontSize = c.sz(0.022f), color = Brass)
                Dials(c, listOf("month" to "月", "day" to "日", "week" to "星期")) {
                    val n = (s.dials[it] ?: 0) + 1
                    if (it == "week") listOf("一", "二", "三", "四", "五", "六", "日")[n - 1] else "$n"
                }
            }
        }
        Pill(c, "好了") { Game.tool = null }
    }
}

@Composable
private fun Dials(c: PhysicalLengthConverter, dials: List<Pair<String, String>>, value: (String) -> String) {
    Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.018f))) {
        for ((d, name) in dials) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(c.m(0.005f))) {
                Text(name, fontSize = c.sz(0.014f), color = Dim)
                Pill(c, "▲", LabelInk) { Game.turnDial(d, 1) }
                Text(value(d), fontSize = c.sz(0.026f), color = Brass)
                Pill(c, "▼", LabelInk) { Game.turnDial(d, -1) }
            }
        }
    }
}

/** The end: the pocket watch made whole. */
@Composable
fun ResultPanel(c: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val s = Game.shop
    Letter(c, 0.5f, 0.27f) {
        Text("师傅的作品", fontSize = c.sz(0.03f), color = Brass)
        Text("六枚零件合成了一块完整的怀表。表盖里刻着：“给愿意花时间的人。”", fontSize = c.sz(0.017f), color = LabelInk)
        val min = (Game.played / 60).toInt()
        Text("用时${if (min < 1) "不到 1" else "约 $min"} 分钟，用了 ${s.hints} 次提示。", fontSize = c.sz(0.016f), color = Jade)
        Text(if (s.hints == 0) "一次提示也没用：真正的钟表匠！" else "线索簿里收着这一路的信、照片和草图。", fontSize = c.sz(0.015f), color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
            Pill(c, "从头再玩") { Game.restart() }
        }
    }
}

@Composable
fun NotePanel(c: PhysicalLengthConverter) {
    val n = Game.note ?: return
    Text(n, Modifier.width(c.m(0.5f)).height(c.m(0.13f)).padding(horizontal = c.m(0.014f), vertical = c.m(0.006f)), fontSize = c.sz(0.0165f), color = Brass)
}

@Composable
fun MenuPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit, faceMe: () -> Unit, quit: () -> Unit, version: String) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    if (!Game.menuOpen) {
        Pill(c, "菜单", Brass) { Game.menuOpen = true }
        return
    }
    PanelBox(c, 0.24f) {
        Pill(c, if (Game.window == CLUES_ID) "收起线索簿" else "线索簿（信、照片、草图）", Brass) { toggle(CLUES_ID) }
        Pill(c, if (Game.window == GUIDE_ID) "收起钟表图鉴" else "钟表图鉴（钟表小知识）") { toggle(GUIDE_ID) }
        Pill(c, "把铺子转到我正前方") { faceMe() }
        Pill(c, if (Game.musicOn) "音乐：开" else "音乐：关") { Game.toggleMusic() }
        RestartPill(c)
        Pill(c, "退出") { quit() }
        Text("进度自动保存。版本 $version", fontSize = c.sz(0.012f), color = Dim)
        Pill(c, "收起菜单") { Game.menuOpen = false }
    }
}

/** Starting over loses the progress, so it asks once more. */
@Composable
private fun RestartPill(c: PhysicalLengthConverter) {
    if (Game.confirmRestart) {
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.006f))) {
            Pill(c, "确定从头开始", Clay) { Game.confirmRestart = false; Game.menuOpen = false; Game.restart() }
            Pill(c, "算了") { Game.confirmRestart = false }
        }
    } else {
        Pill(c, "从头开始") { Game.confirmRestart = true }
    }
}

/**
 * A window's title row with its 关闭 button. It also tells [Game] while this copy of window [id] is
 * on screen, so a window the system closes does not count as open.
 */
@Composable
fun WindowHeader(id: String, title: String, size: TextUnit) {
    val copy = remember { Any() }
    DisposableEffect(copy) {
        Game.windowShown(id, copy)
        onDispose { Game.windowGone(id, copy) }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = size, color = Brass)
        Spacer(Modifier.weight(1f))
        Button(onClick = { Game.closeWindow(id) }) { Text("关闭", fontSize = 22.sp) }
    }
}

val CLOCK_NAMES = mapOf("big" to "前店的落地大钟", "wall" to "工作间的挂钟", "attic" to "阁楼的座钟")

/** Which clue pictures the player has seen (for the 线索簿). */
fun seenPictures(s: Shop): List<Pair<String, String>> = buildList {
    add("letter" to "师傅的信")
    if (s.clues.any { it.startsWith("台灯下的草图") } || s.night && s.room == Shop.Room.WORKSHOP || "safe" in s.opened) add("sketch" to "台灯下的草图")
    Shop.PHOTO_ORDER.forEachIndexed { k, p -> if (p in s.taken) add("photo${k + 1}" to p.label) }
}
