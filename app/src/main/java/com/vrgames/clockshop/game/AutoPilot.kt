package com.vrgames.clockshop.game

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.vrgames.clockshop.core.Walkthrough

/**
 * Test hook, debug builds only: plays the shop through without hands, so every room can be checked
 * on the emulator. Results go to logcat (tag ClockTest).
 *
 *   adb shell am broadcast -a com.vrgames.clockshop.TEST -p com.vrgames.clockshop --es cmd walk
 *
 * cmd: walk (the whole walkthrough, one move every --es id seconds, default 1.5) · step (the next
 * move) · act --es id "open mantel" · tap --es id "clock:big" (as if pinched) · tool --es id clock:big|safe|rings|none ·
 * look --es id "yaw,pitch" (degrees; "reset" gives the head back) · view --es id "turn,lift" (turns and lifts
 * the room for the emulator's fixed camera; "reset") · open --es id clues|guide|menu|close ·
 * start · hint · face · reset · stop
 */
object AutoPilot {
    const val ACTION = "com.vrgames.clockshop.TEST"
    const val TAG = "ClockTest"
    private val main = Handler(Looper.getMainLooper())
    private var next = 0
    private var every = 1500L
    /** What the stage does with a pinch on a thing (set by the stage). */
    var tap: (String) -> Unit = {}

    fun register(context: Context) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = command(i.getStringExtra("cmd") ?: "", i.getStringExtra("id"))
        }
        ContextCompat.registerReceiver(context.applicationContext, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
        Log.i(TAG, "ready")
    }

    /** The stage has built the room: tests wait for this line. */
    fun ready() = Log.i(TAG, "shop ready")

    private fun command(cmd: String, id: String?) {
        Log.i(TAG, "cmd $cmd ${id.orEmpty()}")
        when (cmd) {
            "walk" -> {
                stop()
                every = ((id?.toDoubleOrNull() ?: 1.5) * 1000).toLong()
                if (Game.phase == Game.Phase.CARD) Game.start()
                next = 0
                main.postDelayed(::walk, 500)
            }
            "step" -> {
                if (Game.phase == Game.Phase.CARD) Game.start()
                step()
            }
            "act" -> Game.act(id ?: return)
            "tap" -> tap(id ?: return)
            "tool" -> Game.tool = id?.takeIf { it != "none" }
            "look" -> Game.debugLook = id?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }?.takeIf { it.size == 2 }?.let { it[0] to it[1] }
            "view" -> Game.debugView = id?.takeIf { it != "reset" }
            "open" -> Game.debugOpen = id
            "start" -> Game.start()
            "hint" -> Game.hint()
            "face" -> Game.faceRequest++
            "reset" -> {
                stop()
                Game.restart()
            }
            "stop" -> stop()
            else -> Log.w(TAG, "unknown command $cmd")
        }
    }

    private fun stop() {
        main.removeCallbacksAndMessages(null)
    }

    /** Makes the next walkthrough move the shop has not seen yet (a saved game skips ahead). */
    private fun step(): Boolean {
        val steps = Walkthrough.steps
        if (next < steps.size) {
            val m = steps[next++]
            val refused = Game.act(m)
            Log.i(TAG, "step $next/${steps.size} $m refused=$refused room=${Game.shop.room} parts=${Game.shop.parts}")
            return true
        }
        Log.i(TAG, "walk done finished=${Game.shop.finished} steps=${Game.shop.steps}")
        return false
    }

    private fun walk() {
        if (step()) main.postDelayed(::walk, every)
    }
}
