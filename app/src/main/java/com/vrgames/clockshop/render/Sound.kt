package com.vrgames.clockshop.render

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.ObjectAudioComponent
import com.pico.spatial.core.ecs.audio.AudioPlayerController
import com.pico.spatial.core.ecs.resource.AudioResource
import com.vrgames.clockshop.core.Vec3

/**
 * CC0 clips from Kenney's RPG Audio, Interface Sounds and Impact Sounds, and the chime, the music box
 * and the bird made by tools/make_sfx.py; see docs/素材来源.md.
 */
enum class Sfx(val file: String, val volume: Float) {
    /** A clock's hand moves one step. */
    TICK("tick", 0.7f),
    DIAL("dial", 0.8f),
    /** The shop's own ticking, from the clock that rules the room. */
    TICKTOCK("ticktock", 0.25f),
    OPEN("open", 0.9f),
    DOOR("door", 0.9f),
    PICK("pick", 0.8f),
    PAPER("paper", 0.8f),
    PLACE("place", 0.8f),
    SELECT("select", 0.5f),
    WRONG("wrong", 0.5f),
    PART("part", 0.8f),
    /** A clock strikes: the room turns to another hour. */
    CHIME("chime", 1f),
    /** The music box sings 3, 1, 5. */
    MUSICBOX("musicbox", 1f),
    BIRD("bird", 1f),
}

/**
 * Spatial sound from the scene: each effect has one long-lived emitter with a prepared player,
 * moved to where the sound happens and restarted (creating players per event drops frames).
 * Main thread only.
 */
class Sound(private val parent: Entity) {
    private val emitters = HashMap<Sfx, Entity>()
    private val controllers = HashMap<Sfx, AudioPlayerController>()
    var enabled = true

    /** Loads every clip; a clip that fails is skipped (logged). */
    fun load() {
        for (s in Sfx.entries) {
            runCatching {
                val res = AudioResource.load(s.file, "asset://audio/${s.file}.wav", LoadType.FROM_ASSETS)
                val e = Entity()
                e.components.set(ObjectAudioComponent(s.volume))
                parent.addChild(e)
                val c = e.prepareAudio(res)
                if (c.valid) {
                    emitters[s] = e
                    controllers[s] = c
                }
            }.onFailure { android.util.Log.w(TAG, "sfx ${s.file}: ${it.message}") }
        }
    }

    /** Plays [s] at [at] (in [parent]'s frame; by default the middle of the parent). */
    fun play(s: Sfx, at: Vec3 = Vec3(0.0, 0.1, 0.0)) {
        if (!enabled) return
        val c = controllers[s] ?: return
        emitters[s]?.setPos(at)
        runCatching {
            if (c.isPlaying()) c.stop()
            c.seekTo(0)
            c.play()
        }
    }

    companion object {
        private const val TAG = "GameSound"
    }
}
