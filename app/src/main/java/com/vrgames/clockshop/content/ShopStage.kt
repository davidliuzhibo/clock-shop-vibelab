package com.vrgames.clockshop.content

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.event.SceneEvents
import com.pico.spatial.core.ecs.resource.TextureCreateOption
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.pico.spatial.ui.foundation.content.toColor4
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.vrgames.clockshop.CLUES_ID
import com.vrgames.clockshop.GUIDE_ID
import com.vrgames.clockshop.core.Shop
import com.vrgames.clockshop.game.AutoPilot
import com.vrgames.clockshop.game.Game
import com.vrgames.clockshop.render.HandTurn
import com.vrgames.clockshop.render.Lighting
import com.vrgames.clockshop.render.Music
import com.vrgames.clockshop.render.RoomView
import com.vrgames.clockshop.render.Sfx
import com.vrgames.clockshop.render.Sound
import com.vrgames.clockshop.render.setPos
import com.vrgames.clockshop.render.shared
import com.vrgames.clockshop.render.transform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The clock shop (a full-immersion Stage) around the seated player. Everything is a pinch on a thing
 * the player looks at: take, open, turn a dial, pick an item in the tray and then the place to use
 * it. A clock's face opens its panel (buttons, sure on the device), and a pinch held on the face and
 * moved in a circle turns its hands as well. The tray, the panels and the notes stay in front of the
 * player as they turn about in the chair.
 */
@Composable
fun ShopStage() {
    val context = LocalContext.current
    val converter = LocalPhysicalLengthConverter.current
    val navigator = LocalSpatialNavigator.current
    val main = remember { Handler(Looper.getMainLooper()) }
    val head = remember { HMDTrackingProvider() }
    val subscription = remember { arrayOfNulls<Cancellable>(1) }
    var view by remember { mutableStateOf<RoomView?>(null) }
    val place = remember { Seat() }
    val panels = remember { HashMap<String, Entity>() }
    val turning = remember { ClockTurn() }
    val dialing = remember { DialTurn() }
    // The hand's own turn while it pinches a clock or a dial (twisting the wrist, as on a knob).
    val handTurn = remember { HandTurn() }
    val contentRef = remember { arrayOfNulls<SpatialViewContent>(1) }
    val lastTap = remember { arrayOf<Any>("", 0L) }
    val appVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }

    val toggleWindow: (String) -> Unit = { id ->
        val shown = Game.window
        if (shown != null) Game.closeWindow(shown)
        if (shown != id) {
            Game.windowShown(id)
            navigator.openWindowContainer(id)
        }
    }
    // A pinch on thing [id]. The same pinch can come as a tap and as a drag start: once is enough.
    val pinch: (String) -> Unit = { id ->
        val now = SystemClock.uptimeMillis()
        if (!(lastTap[0] == id && now - (lastTap[1] as Long) < 500)) {
            lastTap[0] = id
            lastTap[1] = now
            Log.i(INPUT_TAG, "pinch $id")
            if (Game.phase == Game.Phase.PLAYING) onThing(id, toggleWindow)
        }
    }

    DisposableEffect(Unit) {
        Game.init(context)
        Game.resumed = Game.phase != Game.Phase.CARD
        if (Game.phase == Game.Phase.PLAYING) Game.phase = Game.Phase.CARD
        Game.closeWindow = { id ->
            navigator.closeWindowContainer(id)
            Game.windowGone(id)
        }
        AutoPilot.tap = pinch
        val hd = runCatching { head.start() }
        Log.i(TAG, "head tracking ${hd.getOrNull() ?: hd.exceptionOrNull()?.message}")
        onDispose {
            runCatching { head.stop() }
            subscription[0]?.cancel()
            Game.closeWindow(CLUES_ID)
            Game.closeWindow(GUIDE_ID)
            Game.closeWindow = {}
            AutoPilot.tap = {}
        }
    }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val music = Music(context).also {
            it.muted = !Game.musicOn
            Game.music = it
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) music.resume() else if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) music.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            music.release()
            Game.music = null
        }
    }
    LaunchedEffect(Game.version, Game.roundVersion, Game.selected, view) {
        val v = view ?: return@LaunchedEffect
        synchronized(Game.lock) { v.sync(Game.shop, Game.selected) }
    }
    LaunchedEffect(Game.faceRequest) { if (Game.faceRequest > 0) place.again() }
    // A clock's slider moving: its hands follow (the move is made when the slider is let go).
    LaunchedEffect(Game.sliderTime, view) {
        val (c, t) = Game.sliderTime ?: return@LaunchedEffect
        view?.let { v -> synchronized(Game.lock) { v.showHands(c, t / 60, t % 60) } }
    }
    LaunchedEffect(Game.noteVersion) {
        val n = Game.note ?: return@LaunchedEffect
        // Long enough to turn to it and read it; the next note replaces it anyway.
        kotlinx.coroutines.delay(if (n.length > 40) 25000 else 16000)
        if (Game.note == n) Game.note = null
    }
    // Panels: one explanation at a time.
    LaunchedEffect(Game.phase, Game.menuOpen, Game.note, Game.tool, view) {
        fun show(id: String, on: Boolean) = panels[id]?.let { if (it.enabled != on) it.enabled = on }
        val playing = Game.phase == Game.Phase.PLAYING
        show(PANEL_CARD, Game.phase == Game.Phase.CARD)
        show(PANEL_RESULT, Game.phase == Game.Phase.RESULT)
        // The clock and dial controls take the hud's place at the lower right, out of the way of the room.
        show(PANEL_HUD, playing && Game.tool == null)
        show(PANEL_TOOL, playing && Game.tool != null)
        show(PANEL_NOTE, Game.note != null)
        show(PANEL_MENU, true)
        panels[PANEL_MENU]?.setPos(if (Game.menuOpen) MENU_OPEN_POS else MENU_POS)
    }
    LaunchedEffect(Game.debugOpen) {
        when (Game.debugOpen ?: return@LaunchedEffect) {
            "clues" -> if (Game.window != CLUES_ID) toggleWindow(CLUES_ID)
            "guide" -> if (Game.window != GUIDE_ID) toggleWindow(GUIDE_ID)
            "menu" -> Game.menuOpen = true
            "close" -> Game.window?.let { Game.closeWindow(it) }
        }
        Game.debugOpen = null
    }

    SpatialView(
        Modifier
            // A quick, still pinch on a thing.
            .pointerInput(0) {
                detectSpatialTapGesture(context, TargetEntity.any()) { value ->
                    val id = view?.hitOf(value.targetEntity) ?: return@detectSpatialTapGesture
                    main.post { pinch(id) }
                }
            }
            // A pinch that moves: on a clock face it turns the hands; on anything else it is a pinch.
            .pointerInput(1) {
                fun meters(px: Float) = with(density) { converter.dpToLength(px.toDp(), LengthUnit.Meters) }.toDouble()
                detectSpatialDragGesture(
                    context = context,
                    targetedToEntity = TargetEntity.any(),
                    onDragStart = { _, value ->
                        val v = view ?: return@detectSpatialDragGesture
                        val id = v.hitOf(value.targetEntity) ?: return@detectSpatialDragGesture
                        Log.i(INPUT_TAG, "drag start $id")
                        if (Game.phase != Game.Phase.PLAYING) return@detectSpatialDragGesture
                        if (id.startsWith("clock:")) {
                            val c = id.removePrefix("clock:")
                            val (h, m) = Game.shop.clocks[c] ?: return@detectSpatialDragGesture
                            turning.begin(c, h, m, v.faceYaw(c) + place.facing)
                            handTurn.reset()
                            handTurn.twist(contentRef[0], runCatching { value.inputDevicePose.rawRotation.toQuaternion() }.getOrNull(), 0.0, 0.0, 1.0)
                            main.post {
                                Game.tool = id
                                Game.turningText = Game.timeText(h, m)
                            }
                        } else if (id.startsWith("dial:")) {
                            // A dial held and moved up or down turns step by step (a still pinch turns it once).
                            val d = id.removePrefix("dial:")
                            // Its face is taken to look at the seat (dials sit all round the room).
                            val at = v.spotOf(id)
                            val toSeat = if (at != null) Math.toDegrees(atan2(-at.x, -at.z)).toFloat() else 0f
                            dialing.begin(d, toSeat + place.facing)
                            handTurn.reset()
                            handTurn.twist(contentRef[0], runCatching { value.inputDevicePose.rawRotation.toQuaternion() }.getOrNull(), 0.0, 0.0, 1.0)
                            main.post {
                                val now = SystemClock.uptimeMillis()
                                // The same pinch already came as a tap and turned it once.
                                if (lastTap[0] == id && now - (lastTap[1] as Long) < 500) dialing.tapped = true
                                lastTap[0] = id
                                lastTap[1] = now
                                Game.tool = toolOfDial(d)
                            }
                        } else {
                            main.post { pinch(id) }
                        }
                    },
                    onDragEnd = { main.post { endTurns(turning, dialing, lastTap) } },
                    onDragCancel = { main.post { endTurns(turning, dialing, lastTap) } },
                ) { value ->
                    // Compose y points down.
                    val raw = runCatching { value.inputDevicePose.rawRotation.toQuaternion() }.getOrNull()
                    if (dialing.active) {
                        // Up, or a clockwise twist of the wrist (as seen facing the dial), turns it on.
                        val yaw = Math.toRadians(dialing.faceYaw)
                        val twisted = -handTurn.twist(contentRef[0], raw, sin(yaw), 0.0, cos(yaw))
                        val n = dialing.move(-meters(value.dragAmount.y), twisted)
                        if (n != 0) main.post { dialing.name?.let { Game.turnDial(it, n) } }
                        return@detectSpatialDragGesture
                    }
                    if (!turning.active) return@detectSpatialDragGesture
                    // The face looks along its yaw; a twist about that axis toward the player is counterclockwise.
                    val yaw = turning.faceYawRad
                    val twisted = -handTurn.twist(contentRef[0], raw, sin(yaw), 0.0, cos(yaw))
                    val steps = turning.move(meters(value.dragAmount.x), -meters(value.dragAmount.y), meters(value.dragAmount.z), twisted * ClockTurn.TWIST_MIN_PER_DEG)
                    turning.preview()?.let { (c, h, m) ->
                        view?.let { v -> synchronized(Game.lock) { v.showHands(c, h, m) } }
                        if (steps != 0) main.post {
                            Game.sound?.play(Sfx.TICK)
                            if (turning.active) Game.turningText = Game.timeText(h, m)
                        }
                    }
                }
            },
        attachments = {
            AttachmentPanel(PANEL_CARD) { CardPanel(converter, Game.resumed) }
            AttachmentPanel(PANEL_HUD) { HudPanel(converter, toggleWindow) }
            AttachmentPanel(PANEL_TOOL) { ToolPanel(converter) }
            AttachmentPanel(PANEL_RESULT) { ResultPanel(converter) }
            AttachmentPanel(PANEL_NOTE) { NotePanel(converter) }
            AttachmentPanel(PANEL_MENU) {
                MenuPanel(converter, toggleWindow, faceMe = { Game.faceRequest++ }, quit = { context.findActivity()?.finishAndRemoveTask() }, version = appVersion)
            }
        },
        update = { _, attachments -> view?.let { attach(it, attachments, panels) } },
        initial = { content, attachments ->
            val sun = Entity().apply {
                components.set(DirectionalLightComponent(Color(0xFFFFF0D8).toColor4(), 900f, false))
                components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(-45f, -35f, 0f))
            }
            content.addEntity(sun)
            contentRef[0] = content
            val lighting = Lighting(sun)
            content.addEntity(lighting.source)
            val tex = HashMap<String, TextureResource>()
            withContext(Dispatchers.IO) {
                lighting.load()
                for (name in TEXTURES) {
                    runCatching { TextureResource.load("textures/$name.jpg", LoadType.FROM_ASSETS, TextureCreateOption()).shared() }
                        .onSuccess { tex[name] = it }
                        .onFailure { Log.i(TAG, "texture $name: ${it.message}") }
                }
            }
            val models = HashMap<String, Entity>()
            for (name in MODELS) {
                runCatching { Entity.loadSuspend("asset://models/$name.glb") }
                    .onSuccess { models[name] = it }
                    .onFailure { Log.i(TAG, "model $name: ${it.message}") }
            }
            val v = RoomView(models, tex, lighting)
            place.root = v.root
            content.addEntity(v.root)
            attach(v, attachments, panels)
            Game.sound = Sound(v.root).also { it.load() }
            synchronized(Game.lock) { v.sync(Game.shop, Game.selected) }
            view = v
            AutoPilot.ready()
            var tick = 0.0
            subscription[0] = content.subscribe(SceneEvents.Update::class.java) { e ->
                val dt = e.deltaTime.toDouble().coerceIn(0.0, 0.1)
                val pose = runCatching { if (head.state == DataProvider.State.STARTED) head.latestData?.hmdPose else null }.getOrNull()
                place.frame(pose?.position, pose?.rotation, dt)
                v.trayYaw(place.trayYaw)
                Game.tickPlayed(dt)
                synchronized(Game.lock) {
                    val s = Game.shop
                    if (Game.events.isNotEmpty()) {
                        val evs = Game.events.toList()
                        Game.events.clear()
                        for (ev in evs) v.on(ev)
                    }
                    v.frame(s, dt)
                    // The shop's own ticking, from the clock that rules the room.
                    tick += dt
                    if (tick >= 1.0) {
                        tick -= 1.0
                        val at = v.mainClock(s)
                        main.post { Game.sound?.play(Sfx.TICKTOCK, at) }
                    }
                }
            }
        },
    )
}

/** What a pinch on thing [id] does (main thread). */
private fun onThing(id: String, toggle: (String) -> Unit) {
    val s = Game.shop
    val sel = Game.selected
    val kind = id.substringBefore(":")
    val name = id.substringAfter(":")
    when {
        kind == "take" -> Game.act("take $name")
        kind == "tray" -> {
            val i = Shop.Item.valueOf(name)
            Game.selected = if (sel == i) null else i
            Game.sound?.play(Sfx.SELECT)
            Game.selected?.let { Game.say("手里拿着${it.label}：捏一下要用它的地方。") }
            Game.changed()
        }
        kind == "clock" -> Game.tool = id
        kind == "dial" -> {
            Game.tool = toolOfDial(name)
            Game.turnDial(name)
        }
        id == "letter" -> {
            Game.sound?.play(Sfx.PAPER)
            Game.say(Shop.LETTER)
        }
        id == "safe" -> {
            if ("safe" in s.opened) return
            Game.tool = "safe"
            Game.act("open safe")
        }
        id == "drawer" -> Game.act(if (Shop.Item.KEY_DRAWER in s.tray) "use KEY_DRAWER drawer" else "open drawer")
        id == "go:workshop" -> Game.act("go workshop")
        id == "go:tower" -> Game.act("go tower")
        id == "study" -> if (Shop.Item.KEY_STUDY in s.tray) Game.act("use KEY_STUDY study") else refuse("书房的门锁着。")
        id.startsWith("train") -> {
            val slot = id.removePrefix("train").toIntOrNull() ?: return
            when {
                sel in Shop.RATIOS -> Game.act("use ${sel!!.name} $id")
                s.train.containsKey(slot) -> Game.act("untrain $slot")
                s.tray.any { it in Shop.RATIOS } -> Game.say("先捏一下托盘里的传动轮选中它，再捏这根轴。")
                else -> Game.say("这根传动轴空着。架子上有四只传动轮，先拿起来。")
            }
        }
        id == "attic" -> if (Shop.Item.KEY_ATTIC in s.tray) Game.act("use KEY_ATTIC attic") else refuse("阁楼的门锁着。")
        id == "cellar" -> if (Shop.Item.KEY_CELLAR in s.tray) Game.act("use KEY_CELLAR cellar") else refuse("地板上的活门锁着。")
        id.startsWith("gear") -> when {
            sel in Shop.GEARS -> Game.act("use ${sel!!.name} $id")
            s.gears.containsKey(id.removePrefix("gear").toIntOrNull()) -> Game.say("这一层已经装好齿轮了。")
            s.tray.any { it in Shop.GEARS } -> Game.say("先捏一下托盘里的齿轮选中它，再捏这一层。")
            else -> Game.say("这是八音盒的齿轮座，一共三层，可齿轮都不在这里。")
        }
        id.startsWith("frame") -> {
            val i = id.removePrefix("frame").toIntOrNull() ?: return
            when {
                sel in Shop.PHOTO_ORDER -> Game.act("use ${sel!!.name} $id")
                s.frames.containsKey(i) -> Game.act("unhang $i")
                s.tray.any { it in Shop.PHOTO_ORDER } -> Game.say("先捏一下托盘里的照片选中它，再捏相框。")
                else -> Game.say("一个空相框。")
            }
        }
        // Things that open never need what is in hand.
        id in setOf("mantel", "toolbox", "chest", "lever", "lamp", "news", "cabinet") -> Game.act("open $id")
        sel != null -> Game.act("use ${sel.name} $id")
        else -> Game.act("open $id")
    }
}

/** The panel a dial belongs to. */
private fun toolOfDial(name: String) = when {
    name.startsWith("safe") -> "safe"
    name == "pend" -> "pendulum"
    name in setOf("month", "day", "week") -> "calendar"
    else -> "rings"
}

/** A held pinch let go: a clock takes the time its hands were turned to; a dial just stops. */
private fun endTurns(turning: ClockTurn, dialing: DialTurn, lastTap: Array<Any>) {
    turning.end()?.let { (c, h, m) -> Game.act("clock $c $h $m") }
    Game.turningText = null
    dialing.name?.let {
        // A pinch that hardly moved is a still pinch: one step, as a tap gives.
        if (dialing.turned == 0 && !dialing.tapped) Game.turnDial(it)
        // The pinch's own tap may come on release: it should not turn the dial once more.
        lastTap[0] = "dial:$it"
        lastTap[1] = SystemClock.uptimeMillis()
    }
    dialing.end()
}

private fun refuse(why: String) {
    Game.say(why)
    Game.sound?.play(Sfx.WRONG)
}

/** Textures loaded if present (see docs/素材来源.md). */
private val TEXTURES = listOf("photo1", "photo2", "photo3", "photo4", "photo5", "sketch", "letter", "glasswords", "plate_san", "plate_sun", "plate_moon", "plate_star", "mark_sun", "mark_moon", "mark_star", "dial_three", "floor_hours", "plate_r2", "plate_r3", "plate_r4", "plate_r5", "plate_rule", "news")

/** Props loaded if present (Seed3D + prep_ai), else simple stand-ins. */
private val MODELS = listOf("grandfather", "wallclock", "mantel", "musicbox", "gramophone", "pocketwatch", "astro", "desklamp", "safe",
    "shell_front", "shell_workshop", "shell_attic", "shell_cellar", "shell_tower", "shell_study",
    "tower_movement", "calendar_clock", "writing_desk", "cabinet", "wheel_big", "wheel_small", "plate_disc", "panel_card", "panel_tool", "panel_result", "panel_note", "panel_hud")
private val PANEL_FRAMES = mapOf(PANEL_CARD to "panel_card", PANEL_TOOL to "panel_tool", PANEL_RESULT to "panel_result", PANEL_NOTE to "panel_note", PANEL_HUD to "panel_hud")

/** The menu tab low at the left of the tray, and where the unfolded menu stands (tray frame). */
private val MENU_POS = Vector3(-0.36f, 0.82f, -0.5f)
private val MENU_OPEN_POS = Vector3(-0.42f, 1.0f, -0.55f)

private fun attach(v: RoomView, a: SpatialViewAttachments, panels: HashMap<String, Entity>) {
    fun put(id: String, pos: Vector3, pitch: Float, yaw: Float = 0f) {
        val e = a.entity(id) ?: return
        if (panels[id] === e) return
        panels[id] = e
        // In the room, not on the tray: the tray swings round after the head, and panels on it followed the head and
        // could not be read (device test 2026-11-12). Placed once with the room, they stay put.
        v.root.addChild(e)
        e.setPos(pos)
        PANEL_FRAMES[id]?.let { name -> v.frame(name)?.let { f -> f.setPos(Vector3(0f, 0f, -0.006f)); e.addChild(f) } }
        e.transform().eulerAngles = EulerAngles(pitch, yaw, 0f)
    }
    // In the room's frame (the seat's facing), fixed once placed. Card and result ahead at
    // eye height (they ask for the player's attention). While playing nothing stands straight ahead
    // (2026-11-10 review: panels hid the things to work on): the hud, or in its place the clock and dial
    // controls, low at the right of the tray; notes low at the left, above the menu tab.
    put(PANEL_CARD, Vector3(0f, 1.2f, -0.85f), 0f)
    put(PANEL_RESULT, Vector3(0f, 1.2f, -0.85f), 0f)
    put(PANEL_HUD, Vector3(0.42f, 0.9f, -0.5f), -22f, -32f)
    put(PANEL_TOOL, Vector3(0.4f, 0.93f, -0.5f), -22f, -32f)
    put(PANEL_NOTE, Vector3(-0.4f, 1.0f, -0.52f), -16f, 32f)
    put(PANEL_MENU, MENU_POS, -30f, 30f)
}

/**
 * Where the player sits and which way the shop faces: the room turns to the mean heading of the
 * first second of tracking (as in 千里眼), and the tray follows the head lazily, only once the head
 * has turned well away from it.
 */
private class Seat {
    var root: Entity? = null
    var facing = 0f; private set
    var trayYaw = 0f; private set
    private var placed = false
    private val headings = ArrayList<Float>()
    private var since = 0.0
    private var clock = 0.0
    private var trayTarget = 0f
    private var seatAt = Vector3(0f, 0f, 0f)
    private var viewed = false

    fun again() {
        placed = false
        headings.clear()
        since = 0.0
    }

    fun frame(eye: Vector3?, rot: Quat?, dt: Double) {
        clock += dt
        val look = Game.debugLook
        val yaw: Float? = when {
            look != null -> look.first.toFloat()
            rot != null -> {
                val f = rot.rotateVector(Vector3(0f, 0f, -1f))
                if (hypot(f.x, f.z) < 0.2f) null else Math.toDegrees(atan2(-f.x, -f.z).toDouble()).toFloat() - facing
            }
            else -> null
        }
        if (!placed && rot != null && eye != null) {
            val f = rot.rotateVector(Vector3(0f, 0f, -1f))
            if (hypot(f.x, f.z) >= 0.2f) {
                if (headings.isEmpty()) since = clock
                headings += Math.toDegrees(atan2(-f.x, -f.z).toDouble()).toFloat()
                if (clock - since >= 1.0) {
                    val sn = headings.sumOf { sin(Math.toRadians(it.toDouble())) }
                    val cs = headings.sumOf { cos(Math.toRadians(it.toDouble())) }
                    facing = Math.toDegrees(atan2(sn, cs)).toFloat()
                    placed = true
                    trayYaw = 0f
                    trayTarget = 0f
                    seatAt = Vector3(eye.x, 0f, eye.z)
                    root?.let {
                        it.setPos(seatAt)
                        it.transform().eulerAngles = EulerAngles(0f, facing, 0f)
                    }
                    Log.i(TAG, "facing: ${headings.size} samples, mean $facing, eye $eye")
                }
            }
        }
        // Debug only: turn and lift the room so the emulator's fixed camera sees the low corners.
        val view = Game.debugView?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }
        if (placed && (view != null || viewed)) {
            val turn = view?.getOrNull(0) ?: 0f
            val lift = view?.getOrNull(1) ?: 0f
            root?.let {
                it.setPos(Vector3(seatAt.x, lift, seatAt.z))
                it.transform().eulerAngles = EulerAngles(0f, facing + turn, 0f)
            }
            viewed = view != null
        }
        if (yaw != null) {
            val d = wrap(yaw - trayTarget)
            // Turned more than 40 degrees away: the tray comes round to where the head points.
            if (abs(d) > 40f || look != null) trayTarget = yaw
        }
        trayYaw += wrap(trayTarget - trayYaw) * (1 - exp(-dt / 0.35)).toFloat()
    }

    private fun wrap(a: Float): Float {
        var x = a % 360f
        if (x > 180f) x -= 360f
        if (x < -180f) x += 360f
        return x
    }
}

/**
 * Turning a clock's hands by a pinch moved in a circle: a point starts at the tip of the minute
 * hand (on a circle of [R], whatever the clock's size), follows the hand's motion across the face,
 * and the minute hand follows its angle; whole turns move the hour. Snaps to five minutes.
 */
private class ClockTurn {
    var active = false; private set
    private var clock = ""
    private var minutes = 0.0
    private var u = 0.0
    private var v = 0.0
    private var faceYaw = 0.0
    private var lastStep = 0
    private var startMinutes = 0

    fun begin(c: String, h: Int, m: Int, yawDeg: Float) {
        active = true
        clock = c
        minutes = h * 60.0 + m
        startMinutes = h * 60 + m
        val a = Math.toRadians(m * 6.0)
        u = R * sin(a)
        v = R * cos(a)
        faceYaw = Math.toRadians(yawDeg.toDouble())
        lastStep = (minutes / 5).roundToInt()
    }

    val faceYawRad get() = faceYaw

    /**
     * Moves by a hand motion (room frame, meters) or a twist of the wrist ([twistMinutes], already in
     * minutes; the larger of the two counts); returns how many five-minute steps it crossed.
     */
    fun move(dx: Double, dy: Double, dz: Double, twistMinutes: Double = 0.0): Int {
        // Across the face: its right is the room's x turned by the face's yaw.
        val du = dx * cos(faceYaw) - dz * sin(faceYaw)
        val before = atan2(u, v)
        var pu = u + du * GAIN
        var pv = v + dy * GAIN
        // Kept near the circle, so a long drag does not leave the point far from the middle or on it.
        val r = hypot(pu, pv).coerceAtLeast(1e-6)
        pu = pu / r * R
        pv = pv / r * R
        var d = atan2(pu, pv) - before
        if (d > Math.PI) d -= 2 * Math.PI
        if (d < -Math.PI) d += 2 * Math.PI
        val dragMinutes = Math.toDegrees(d) / 6.0
        minutes += if (kotlin.math.abs(twistMinutes) > kotlin.math.abs(dragMinutes)) twistMinutes else dragMinutes
        // The point follows the minute hand, so drag and twist can take turns.
        val a = Math.toRadians(minutes * 6.0)
        u = R * sin(a)
        v = R * cos(a)
        val step = (minutes / 5).roundToInt()
        val n = step - lastStep
        lastStep = step
        return n
    }

    fun preview(): Triple<String, Int, Int>? {
        if (!active) return null
        val total = (((lastStep * 5) % 1440) + 1440) % 1440
        return Triple(clock, total / 60, total % 60)
    }

    /** The time to set on release, or null if the hands came back to where they were. */
    fun end(): Triple<String, Int, Int>? {
        if (!active) return null
        val p = preview()
        active = false
        return p?.takeIf { lastStep * 5 != startMinutes }
    }

    companion object {
        const val R = 0.1
        const val GAIN = 1.5
        /** A quarter turn of the wrist moves the hands three hours. */
        const val TWIST_MIN_PER_DEG = 2.0
    }
}

/** A dial turned by a held pinch: one step for every [STEP] meters the hand moves up (or down, back). */
private class DialTurn {
    var name: String? = null; private set
    val active get() = name != null
    private var along = 0.0
    /** Steps turned since the pinch began. */
    var turned = 0; private set
    /** The pinch also came as a tap (which turned the dial once already). */
    var tapped = false

    /** Which way the dial's face looks (degrees about y in the content's frame). */
    var faceYaw = 0.0; private set

    fun begin(d: String, yawDeg: Float) {
        name = d
        along = 0.0
        turned = 0
        tapped = false
        faceYaw = yawDeg.toDouble()
    }

    /** Moves by [dy] meters up or [twistDeg] of clockwise wrist turn (the larger counts); returns the whole steps crossed. */
    fun move(dy: Double, twistDeg: Double = 0.0): Int {
        if (name == null) return 0
        val twisted = twistDeg / TWIST_DEG_PER_STEP * STEP
        along += if (kotlin.math.abs(twisted) > kotlin.math.abs(dy)) twisted else dy
        val n = (along / STEP).toInt()
        along -= n * STEP
        turned += kotlin.math.abs(n)
        return n
    }

    fun end() {
        name = null
    }

    companion object {
        const val STEP = 0.03
        /** Wrist degrees per step of a dial. */
        const val TWIST_DEG_PER_STEP = 25.0
    }
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

private const val TAG = "ClockStage"
/** Every pinch the stage sees (kept in release builds, for on-device reports). */
private const val INPUT_TAG = "ClockInput"
