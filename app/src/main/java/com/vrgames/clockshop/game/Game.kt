package com.vrgames.clockshop.game

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vrgames.clockshop.core.Shop
import com.vrgames.clockshop.core.Walkthrough
import com.vrgames.clockshop.render.Music
import com.vrgames.clockshop.render.Sfx
import com.vrgames.clockshop.render.Sound

/**
 * State the stage, its panels and both windows share (one process). [shop] holds the rules; every
 * move goes through [act], which records it (in [Walkthrough]'s words) so the game is saved as the
 * list of moves and replayed when the app comes back.
 */
object Game {
    private const val PREFS = "clockshop"
    private const val TAG = "ClockGame"

    val lock = Any()
    private var prefs: android.content.SharedPreferences? = null
    var shop = Shop(); private set
    private val moves = ArrayList<String>()

    enum class Phase { CARD, PLAYING, RESULT }

    var phase by mutableStateOf(Phase.CARD)
    /** Bumps on every change the scene and the panels show. */
    var version by mutableIntStateOf(0); private set
    /** Bumps when the shop starts over (the scene is rebuilt). */
    var roundVersion by mutableIntStateOf(0); private set
    /** A saved game was picked up: the opening card says 继续 instead of 推开店门. */
    var resumed by mutableStateOf(false)
    var note by mutableStateOf<String?>(null)
    var noteVersion by mutableIntStateOf(0); private set
    /** The clock or the dials whose panel is open ("clock:big", "safe", "rings"), or null. */
    var tool by mutableStateOf<String?>(null)
    /** The time shown while a clock's hands are being turned by hand ("clock:big" to "下午 3:05"), else null. */
    var turningText by mutableStateOf<String?>(null)
    /** The ±1 hour / ±5 minute buttons, folded away unless asked for (the hands are turned by hand). */
    var fineButtons by mutableStateOf(false)
    /** A clock's time while its slider is being moved (clock id to minutes of the day), shown on its hands. */
    var sliderTime by mutableStateOf<Pair<String, Int>?>(null)
    var selected by mutableStateOf<Shop.Item?>(null)
    var menuOpen by mutableStateOf(false)
    var confirmRestart by mutableStateOf(false)
    var musicOn by mutableStateOf(true); private set
    var music: Music? = null
    var sound: Sound? = null
    /** Seconds played, kept with the save (the result card shows it). */
    var played = 0.0
    /** The scene's events (the room shows them); the stage drains this on the frame thread. */
    val events = ArrayList<Shop.Event>()

    var window by mutableStateOf<String?>(null); private set
    private val windowCopies = HashMap<String, Any>()
    var closeWindow: (String) -> Unit = {}

    fun windowShown(id: String, copy: Any? = null) {
        if (copy != null) windowCopies[id] = copy
        window = id
    }

    fun windowGone(id: String, copy: Any? = null) {
        if (copy != null && windowCopies[id] !== copy) return
        windowCopies.remove(id)
        if (window == id) window = null
    }

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { p ->
            musicOn = p.getBoolean("music", true)
            played = p.getFloat("played", 0f).toDouble()
            val saved = p.getString("moves", null)?.split("|")?.filter { it.isNotBlank() }.orEmpty()
            val s = Shop()
            for (m in saved) runCatching { Walkthrough.run(s, m) }.onFailure { Log.w(TAG, "replay $m: ${it.message}") }
            moves += saved
            shop = s
            if (saved.isNotEmpty()) phase = if (s.finished) Phase.RESULT else Phase.PLAYING
            Log.i(TAG, "replayed ${saved.size} moves, room ${s.room}")
        }
        AutoPilot.register(context)
    }

    fun start() {
        phase = Phase.PLAYING
        if (moves.isEmpty()) say(Shop.LETTER)
        version++
    }

    fun say(text: String?) {
        note = text
        noteVersion++
    }

    fun changed() {
        version++
    }

    /**
     * Makes move [m] (Walkthrough's words): the shop's answer becomes the note and the sounds, the
     * events go to the scene. Returns whether it was refused.
     */
    fun act(m: String): Boolean {
        if (phase != Phase.PLAYING) return false
        val s = shop
        val playedBefore = s.musicPlays
        val roomBefore = s.room
        val out = synchronized(lock) { Walkthrough.run(s, m) }
        val refused = out.any { it is Shop.Event.Refused }
        // Every move not refused is kept (a dial turned or a clock set says nothing, yet must come back).
        if (!refused) {
            moves += m
            save()
        }
        Log.i(TAG, "move $m -> ${out.joinToString { it.toString() }}")
        synchronized(lock) { events += out }
        var said: String? = null
        for (e in out) {
            when (e) {
                is Shop.Event.Refused -> { said = e.why; sound?.play(Sfx.WRONG) }
                is Shop.Event.Said -> said = e.text
                is Shop.Event.Took -> sound?.play(if (e.item in Shop.PHOTO_ORDER) Sfx.PAPER else Sfx.PICK)
                is Shop.Event.Placed -> sound?.play(Sfx.PLACE)
                is Shop.Event.Opened -> sound?.play(Sfx.OPEN)
                is Shop.Event.Shifted -> sound?.play(Sfx.CHIME)
                is Shop.Event.Part -> {
                    sound?.play(Sfx.PART)
                    if (said == null) said = "找到第 ${e.n} 枚怀表零件！"
                }
                Shop.Event.Finished -> {
                    sound?.play(Sfx.BIRD)
                    selected = null
                    tool = null
                }
                is Shop.Event.RoomDone -> Unit
                Shop.Event.MantelTurned -> sound?.play(Sfx.OPEN)
                Shop.Event.TowerRuns -> sound?.play(Sfx.CHIME)
                is Shop.Event.PlateFound -> sound?.play(Sfx.PART)
            }
        }
        if (s.room != roomBefore) {
            sound?.play(Sfx.DOOR)
            tool = null
            selected = null
            say(said ?: s.room.title)
        } else if (said != null) {
            say(said)
        }
        if (!playedBefore && s.musicPlays) sound?.play(Sfx.MUSICBOX)
        if (selected != null && selected !in s.tray) selected = null
        version++
        if (s.finished && phase == Phase.PLAYING) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ phase = Phase.RESULT; version++ }, 4000)
        }
        return refused
    }

    /** The current time on clock [c], as the player reads it. */
    fun clockText(c: String): String {
        val (h, m) = shop.clocks[c] ?: return ""
        return timeText(h, m)
    }

    /** [h]:[m] as the player reads it (上午 9:05). */
    fun timeText(h: Int, m: Int): String {
        val part = when (h) {
            in 0..4 -> "凌晨"
            in 5..10 -> "上午"
            in 11..12 -> "中午"
            in 13..17 -> "下午"
            in 18..19 -> "傍晚"
            else -> "晚上"
        }
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$part $h12:${"%02d".format(m)}"
    }

    /** Sets clock [c] to [total] minutes of the day (from its slider) as one move. */
    fun setClock(c: String, total: Int) {
        val t = ((total % 1440) + 1440) % 1440
        sliderTime = null
        val (h, m) = shop.clocks[c] ?: return
        if (h * 60 + m == t) return
        sound?.play(Sfx.TICK)
        act("clock $c ${t / 60} ${t % 60}")
    }

    /** Sets dial [d] to [value] (from its slider) as one move; the safe opens by itself once right. */
    fun setDial(d: String, value: Int) {
        val v = shop.dials[d] ?: 0
        if (v != value) turnDial(d, value - v)
    }

    /** Turns clock [c] by [minutes] (either way) and makes it a move. */
    fun turnClock(c: String, minutes: Int) {
        val (h, m) = shop.clocks[c] ?: return
        val total = (((h * 60 + m + minutes) % 1440) + 1440) % 1440
        sound?.play(Sfx.TICK)
        act("clock $c ${total / 60} ${total % 60}")
    }

    /** Turns dial [d] one step (by [by]); the safe opens by itself once all three are right. */
    fun turnDial(d: String, by: Int = 1) {
        val v = shop.dials[d] ?: 0
        sound?.play(Sfx.DIAL)
        act("dial $d ${v + by}")
        val s = shop
        if (d.startsWith("safe") && "safe" !in s.opened && s.dials["safe1"] == 3 && s.dials["safe2"] == 1 && s.dials["safe3"] == 5) act("open safe")
    }

    fun hint() {
        val text = synchronized(lock) { shop.hint() }
        // Kept with the moves, so the count of hints comes back with the save.
        moves += "hint"
        save()
        say("提示：$text")
        version++
    }

    private fun save() {
        prefs?.edit()?.putString("moves", moves.joinToString("|"))?.putFloat("played", played.toFloat())?.apply()
    }

    fun tickPlayed(dt: Double) {
        if (phase != Phase.PLAYING) return
        val before = played.toInt() / 30
        played += dt
        if (played.toInt() / 30 != before) prefs?.edit()?.putFloat("played", played.toFloat())?.apply()
    }

    fun restart() {
        synchronized(lock) {
            shop = Shop()
            events.clear()
        }
        moves.clear()
        played = 0.0
        resumed = false
        save()
        selected = null
        tool = null
        note = null
        phase = Phase.CARD
        roundVersion++
        version++
    }

    fun toggleMusic() {
        musicOn = !musicOn
        music?.muted = !musicOn
        prefs?.edit()?.putBoolean("music", musicOn)?.apply()
    }

    // Debug hooks (AutoPilot).
    var debugOpen by mutableStateOf<String?>(null)
    var debugLook by mutableStateOf<Pair<Double, Double>?>(null)
    var debugView: String? = null
    var faceRequest by mutableIntStateOf(0)
}
