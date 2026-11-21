package com.vrgames.clockshop.platform

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator

/**
 * Leaving the game closes every space it opened (device 2026-11-14, 捏糖人: after quitting, the levels window stayed alone
 * in the home space). The levels window is a separate window with its own task, so finishing the stage's task alone left
 * it behind; and a close asked of the navigator while the stage is being torn down did not always arrive.
 */
object AppExit {
    private const val TAG = "AppExit"

    /** True while the stage is up. The levels window closes itself when it goes down. */
    var stageAlive by mutableStateOf(false)

    /**
     * v3 (emulator 2026-11-19: after 退出 the side window stayed; `appTasks` listed only the stage's task): every open window
     * registers how to close itself, and quitting closes each one directly before finishing the app's tasks.
     */
    private val windowClosers = linkedMapOf<String, () -> Unit>()

    /** Finishes every task of this app: each open window first, then the stage. */
    fun finishAll(context: Context) {
        val closers = windowClosers.values.toList()
        windowClosers.clear()
        Log.i(TAG, "closing ${closers.size} window(s)")
        closers.forEach { runCatching { it() } }
        val am = context.getSystemService(ActivityManager::class.java)
        val tasks = runCatching { am?.appTasks }.getOrNull().orEmpty()
        Log.i(TAG, "finish ${tasks.size} task(s)")
        tasks.forEach { runCatching { it.finishAndRemoveTask() } }
        context.activity()?.let { runCatching { it.finishAndRemoveTask() } }
    }

    /**
     * Put inside the stage's composable (v2, device 2026-11-17, 方块室友: after leaving with the Home button, 「选关和奖杯」
     * stayed open). Home does not dispose the stage, it stops it: a stopped stage counts as gone, so its windows close too.
     */
    @Composable
    fun TrackStage() {
        val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
        androidx.compose.runtime.DisposableEffect(lifecycle) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
                when (e) {
                    androidx.lifecycle.Lifecycle.Event.ON_START -> stageAlive = true
                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> { Log.i(TAG, "stage stopped"); stageAlive = false }
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
    }

    /** Put inside the levels window: once the stage is gone, the window goes too. */
    @Composable
    fun CloseWithStage(windowId: String) {
        val context = LocalContext.current
        val navigator = LocalSpatialNavigator.current
        androidx.compose.runtime.DisposableEffect(windowId) {
            val own = context.activity()
            windowClosers[windowId] = {
                runCatching { navigator.closeWindowContainer(windowId) }
                own?.let { runCatching { it.finishAndRemoveTask() } }
            }
            onDispose { windowClosers.remove(windowId) }
        }
        LaunchedEffect(stageAlive) {
            if (stageAlive) return@LaunchedEffect
            // A moment's grace, in case the window is drawn before the stage on a restore.
            kotlinx.coroutines.delay(1500)
            if (stageAlive) return@LaunchedEffect
            Log.i(TAG, "stage gone: closing $windowId")
            runCatching { navigator.closeWindowContainer(windowId) }
            context.activity()?.let { runCatching { it.finishAndRemoveTask() } }
        }
    }

    private fun Context.activity(): Activity? {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
