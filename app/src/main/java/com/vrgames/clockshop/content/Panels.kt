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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Slider
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
const val PANEL_TUNE = "tune"
const val PANEL_NOTE = "note"

// Ink on the cream letter paper in the mahogany frames.
val LabelInk = Color(0xFF2B1D12)
val Brass = Color(0xFF7A2E12)
val Dim = Color(0xFF6B5A47)
val Jade = Color(0xFF1F5B3A)
val Clay = Color(0xFFA33A17)
private val PanelBg = Color(0xFFEFE3C8)
/** Buttons (v0.5.1): light text on dark fills, so they read on the cream paper and in the dim room. */
val Gold = Color(0xFFF2C76A)
private val PillBg = Color(0xFF3B2416)
private val PillText = Color(0xFFF6E7C8)
private val CtaBg = Color(0xFF8E2A12)
private val CtaText = Color(0xFFFFF0C8)
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
        // v0.5.1 (device 2026-11-19: dark text on dark buttons was hard to read): vermilion lacquer, light gold text.
        Modifier.clip(shape).background(CtaBg).border(c.m(0.0022f), Gold, shape)
            .clickable { onClick() }.padding(horizontal = c.m(0.018f), vertical = c.m(0.01f)),
        fontSize = c.sz(0.02f),
        color = CtaText,
    )
}

/**
 * An ordinary button. v0.5.1 (device 2026-11-19): it had no fill and dark text, so in the dim shop it read as plain
 * text or vanished; now dark walnut with light text and a brass rim, readable on the cream letters and in the room alike.
 * [color] marks an important one (gold rim and text) or a risky one (clay).
 */
@Composable
fun Pill(c: PhysicalLengthConverter, text: String, color: Color = Dim, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.01f))
    val accent = when (color) { Brass -> Gold; Clay -> Color(0xFFFFB08A); else -> PillText }
    Text(
        text,
        Modifier.clip(shape).background(PillBg).border(c.m(0.0016f), if (color == Clay) Color(0xFFE07A4F) else Gold, shape)
            .clickable { onClick() }.padding(horizontal = c.m(0.012f), vertical = c.m(0.0075f)),
        fontSize = c.sz(0.015f),
        color = accent,
    )
}

/**
 * The folded menu, alone in the room: v0.5.1 (device 2026-11-19: "菜单" was dim on a see-through button in a dark room),
 * a large vermilion tab with light text and a gold rim, as in 绣娘 and 年画坊.
 */
@Composable
fun MenuTab(c: PhysicalLengthConverter, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.012f))
    Text(
        "≡  菜单",
        Modifier.clip(shape).background(Color(0xFFB3361A)).border(c.m(0.0025f), Gold, shape)
            .clickable { onClick() }.padding(horizontal = c.m(0.02f), vertical = c.m(0.011f)),
        fontSize = c.sz(0.022f),
        color = Color(0xFFFFF4D6),
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
        // v0.5.2 (device 2026-11-20): six rooms, not four; things are carried from the tray and clocks turned by hand.
        Text("· 看着东西，捏一下：拿起、打开、拨动。\n· 拿到的东西在面前的托盘里，捏住拖到要用的地方再松手。\n· 捏住钟面绕圈拨钟，或像拧旋钮一样转手腕；房间会回到钟上的那个时刻。\n· 卡住了就点「提示」。一共六个房间，每间藏一枚怀表零件。", fontSize = c.sz(0.015f), color = LabelInk)
        CallToAction(c, if (resumed) "继续 →" else "推开店门 →") { Game.start() }
    }
}

/** Beside the tray: the room, the parts found, what is in hand, and the hint button. */
@Composable
fun HudPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val s = Game.shop
    // v0.6.1 (device 2026-11-25: 「右手边的提示+线索簿面板……非常容易遮挡向右的目光……大幅缩小……与菜单放在一起……左下方」):
    // one small wide panel low at the left, the menu in it. Row 1: where you are and the parts; a status line (one line,
    // its height kept even when empty, so the panel never changes size and longer text never wraps); row 2: three buttons
    // alike. Its size is fixed ([DOCK_W] wide): what does not fit in a line is cut, never pushed onto a second one.
    val shape = RoundedCornerShape(c.m(0.014f))
    Column(
        Modifier.width(c.m(DOCK_W)).clip(shape).background(Color(0xF02A1A10)).border(c.m(0.0016f), Gold, shape)
            .padding(horizontal = c.m(0.014f), vertical = c.m(0.01f)),
        verticalArrangement = Arrangement.spacedBy(c.m(0.006f)),
    ) {
        val playing = Game.phase == Game.Phase.PLAYING
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (playing) s.room.title else "钟表店密室", fontSize = c.sz(0.018f), color = Gold, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(c.m(0.012f)))
            Text("怀表零件 " + (1..6).joinToString("") { if (it in s.parts) "●" else "○" } + "  ${s.parts.size}/6",
                fontSize = c.sz(0.015f), color = PillText, maxLines = 1, softWrap = false)
        }
        val sel = Game.selected
        val status = when {
            !playing -> ""
            sel != null -> "手里：${sel.label}"
            s.tray.isNotEmpty() -> "捏托盘里的东西选中它"
            else -> ""
        }
        Text(status, Modifier.height(c.m(0.02f)), fontSize = c.sz(0.014f), color = if (sel != null) Jade else PillText, maxLines = 1, softWrap = false)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.008f))) {
            DockButton(c, "提示", playing) { Game.hint() }
            DockButton(c, if (Game.window == CLUES_ID) "收起线索簿" else "线索簿", true) { toggle(CLUES_ID) }
            DockButton(c, if (Game.menuOpen) "收起菜单" else "≡ 菜单", true) { Game.menuOpen = !Game.menuOpen }
        }
    }
}

/** The dock's width (m): its three buttons and the longest status line fit in it, one line each. */
const val DOCK_W = 0.34f

/** The dock's buttons, all alike: the vermilion of the old 菜单 tab and 提示 button, gold rim, light text. */
@Composable
private fun DockButton(c: PhysicalLengthConverter, text: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.011f))
    Text(
        text,
        Modifier.clip(shape).background(if (on) Color(0xFFB3361A) else Color(0xFF5A2A1E)).border(c.m(0.0022f), Gold, shape)
            .clickable(enabled = on) { onClick() }.padding(horizontal = c.m(0.016f), vertical = c.m(0.01f)),
        fontSize = c.sz(0.019f),
        color = Color(0xFFFFF4D6),
        maxLines = 1, softWrap = false,
    )
}

/** The controls of the clock or the dials just pinched (fine work is done here, close to the hand). */
@Composable
fun ToolPanel(c: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val t = Game.tool ?: return
    val s = Game.shop
    // v0.6.2b: 「好了」 beside the title (at the bottom it was pushed off the panel once the dials grew).
    @Composable
    fun Header(title: String, size: Float = 0.022f, color: Color = Brass) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = c.sz(size), color = color, maxLines = 1, softWrap = false)
        Spacer(Modifier.weight(1f))
        Pill(c, "好了") { Game.tool = null; Game.fineButtons = false }
    }
    Letter(c, TOOL_W, 0.24f) {
        when {
            t.startsWith("clock:") -> {
                val id = t.removePrefix("clock:")
                Header(CLOCK_NAMES[id] ?: "钟", 0.016f, Dim)
                // While the hands are being turned the time follows them, not the last time set.
                val sliding = Game.sliderTime?.takeIf { it.first == id }?.second
                Text(Game.turningText ?: sliding?.let { Game.timeText(it / 60, it % 60) } ?: Game.clockText(id), fontSize = c.sz(0.03f), color = Brass)
                Text("捏住钟面（分针头上的铜钮）绕圈拨，或像拧旋钮一样转手腕。", fontSize = c.sz(0.014f), color = LabelInk)
                if (Game.fineButtons) ClockSlider(c, id)
                else Pill(c, "用按钮拨", Dim) { Game.fineButtons = true }
            }
            t == "safe" -> {
                Header("保险柜的密码转盘")
                // v0.6.2: the music box's numbers, once it has sung, right where they are needed.
                if (s.musicPlays) Text("八音盒唱出的数：3 · 1 · 5", fontSize = c.sz(0.017f), color = Jade, maxLines = 1, softWrap = false)
                Dials(c, listOf("safe1" to "第一位", "safe2" to "第二位", "safe3" to "第三位")) { _, n -> "$n" }
            }
            t == "rings" -> {
                Header("天文钟的三个环")
                Dials(c, listOf("ring1" to "日环（外）", "ring2" to "月环（中）", "ring3" to "星环（内）")) { _, n -> "${if (n == 0) 12 else n} 点" }
                Text("对照墙上的三块铜牌：日、月、星。", fontSize = c.sz(0.014f), color = Dim, maxLines = 1, softWrap = false)
            }
            t == "pendulum" -> {
                Header("钟摆的摆长")
                Dials(c, listOf("pend" to "摆长")) { _, n -> "${Shop.PENDULUM_LENGTH[n]} 米" }
                Text("现在一来一回 ${Shop.PENDULUM_LABEL[s.pendulum]}。摆越长，摆得越慢。", fontSize = c.sz(0.015f), color = LabelInk)
            }
            t == "calendar" -> {
                Header("万年历")
                Dials(c, listOf("month" to "月", "day" to "日", "week" to "星期")) { d, v ->
                    if (d == "week") listOf("一", "二", "三", "四", "五", "六", "日")[v] else "${v + 1}"
                }
            }
        }
    }
}

/** The clock/dial panel's width (m; core.PanelLayout.TOOL, its frame stretched to it in ShopStage). */
const val TOOL_W = 0.50f

/**
 * A clock's time without a slider (v0.6.2, as the dials: the slider jumped to where the hand pointed and could stick):
 * − / + an hour, − / + five minutes, and a wheel between them that turns five minutes for every 2.5 cm a pinch moves.
 */
@Composable
private fun ClockSlider(c: PhysicalLengthConverter, id: String) {
    @Suppress("UNUSED_VARIABLE") val ver = Game.version
    if (Game.shop.clocks[id] == null) return
    // v0.6.2b: buttons only (a drag on the panel turned far too fast on the device); the game turns the clock on its main thread.
    fun turn(min: Int) {
        android.util.Log.i("ClockUi", "pressed $min min on $id")
        android.os.Handler(android.os.Looper.getMainLooper()).post { Game.turnClock(id, min) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(c.m(0.01f))) {
        Pill(c, "−1 小时") { turn(-60) }
        Pill(c, "−5 分") { turn(-5) }
        Pill(c, "+5 分") { turn(5) }
        Pill(c, "+1 小时") { turn(60) }
    }
}

/** The dials of one thing, each a slider; a pinch on a dial in the room, or a twist of the wrist, turns it too. */
@Composable
private fun Dials(c: PhysicalLengthConverter, dials: List<Pair<String, String>>, value: (String, Int) -> String) {
    // v0.6.3 — THE cause of 「点加号和减号……完全没有任何变化」 (found on the emulator, 2026-11-26): the panel around this was
    // redrawn on every move, but this part was skipped: its inputs (a fixed list of dials, a fixed lambda) never change, and
    // the numbers it shows are read from the game, not passed in. Reading the game's version here makes it redraw itself.
    @Suppress("UNUSED_VARIABLE") val ver = Game.version
    Column(verticalArrangement = Arrangement.spacedBy(c.m(0.004f))) {
        for ((d, name) in dials) {
            val cur = Game.shop.dials[d] ?: 0
            // v0.6.2b (device 2026-11-25, its log): − and + did turn the dial (each press logged), yet the panel kept its
            // number; and the wheel's drag turned it one notch every 20 ms. Now no drag; − and + only ask the game, on its
            // main thread, as a pinch in the room does; and what the panel shows is logged (ClockUi), to see it on the device.
            android.util.Log.i("ClockUi", "dial $d shows ${value(d, cur)}")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(c.m(0.01f))) {
                Text(name, Modifier.width(c.m(0.085f)), fontSize = c.sz(0.015f), color = Dim, maxLines = 1, softWrap = false)
                StepButton(c, "−") { stepDial(d, -1) }
                DialWheel(c, d, cur, value)
                StepButton(c, "+") { stepDial(d, 1) }
            }
        }
    }
}

/** − or + on a dial's row: the game turns it, on the main thread. */
private fun stepDial(d: String, by: Int) {
    android.util.Log.i("ClockUi", "pressed ${if (by > 0) "+" else "−"} on $d")
    android.os.Handler(android.os.Looper.getMainLooper()).post { Game.setDial(d, Shop.dialStep(d, Game.shop.dials[d] ?: 0, by)) }
}

/** A dial's number, with the one before and after it in small figures. */
@Composable
private fun DialWheel(c: PhysicalLengthConverter, d: String, cur: Int, value: (String, Int) -> String) {
    val shape = RoundedCornerShape(c.m(0.01f))
    Row(
        Modifier.width(c.m(0.22f)).height(c.m(0.044f)).clip(shape).background(PillBg).border(c.m(0.0018f), Gold, shape),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Text(value(d, Shop.dialStep(d, cur, -1)), fontSize = c.sz(0.014f), color = WheelSide, maxLines = 1, softWrap = false)
        Text(value(d, cur), fontSize = c.sz(0.026f), color = Gold, maxLines = 1, softWrap = false)
        Text(value(d, Shop.dialStep(d, cur, 1)), fontSize = c.sz(0.014f), color = WheelSide, maxLines = 1, softWrap = false)
    }
}

private val WheelSide = Color(0xFFB8A27E)

/** One notch down or up on a dial: a big square button, easy to catch with a look and a pinch. */
@Composable
private fun StepButton(c: PhysicalLengthConverter, text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.009f))
    Text(
        text,
        Modifier.width(c.m(0.05f)).clip(shape).background(Color(0xFFB3361A)).border(c.m(0.0022f), Gold, shape)
            .clickable { onClick() }.padding(vertical = c.m(0.005f)),
        fontSize = c.sz(0.026f), color = Gold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1,
    )
}

/**
 * v0.6.2 (device 2026-11-25: 「八音盒在敲响之后，其实并没有任何地方去提示显示出了315三个数字……需要让这三个数字以相对明显的
 * 方式能够显示出来」): beside the music box once it sings, until the safe is open: its three numbers in big gold figures.
 */
@Composable
fun TunePanel(c: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    val shape = RoundedCornerShape(c.m(0.012f))
    Column(
        Modifier.width(c.m(TUNE_W)).clip(shape).background(Color(0xF02A1A10)).border(c.m(0.002f), Gold, shape).padding(c.m(0.01f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(c.m(0.004f)),
    ) {
        Text("八音盒唱出的数", fontSize = c.sz(0.016f), color = PillText, maxLines = 1, softWrap = false)
        Text("3 · 1 · 5", fontSize = c.sz(0.05f), color = Gold, maxLines = 1, softWrap = false)
        Text("三根梳片依次发光", fontSize = c.sz(0.013f), color = PillText, maxLines = 1, softWrap = false)
    }
}

const val TUNE_W = 0.26f

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
    // v0.5.2: 0.44 m (was 0.50), so it stays clear of the clock/dial panel at the right (core.PanelLayout).
    Text(n, Modifier.width(c.m(com.vrgames.clockshop.core.PanelLayout.NOTE_W.toFloat())).height(c.m(com.vrgames.clockshop.core.PanelLayout.NOTE_H.toFloat())).padding(horizontal = c.m(0.014f), vertical = c.m(0.006f)), fontSize = c.sz(0.0165f), color = Brass)
}

@Composable
fun MenuPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit, faceMe: () -> Unit, quit: () -> Unit, version: String) {
    @Suppress("UNUSED_VARIABLE") val v = Game.version
    // v0.6.1: the folded menu is the dock's 菜单 button.
    if (!Game.menuOpen) return
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
