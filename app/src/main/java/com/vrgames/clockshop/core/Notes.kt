package com.vrgames.clockshop.core

/**
 * v0.5.2 (device 2026-11-20: the note in front stayed too long, and after a new step the old note was still there):
 * every move that went through says something, so the note in front always tells what just happened. The shop's own
 * words come first; these lines only fill in where it says nothing.
 */
object Notes {
    /** The opening letter (and the letter on the counter), long enough to read twice. */
    const val FIRST_NOTE_MS = 15_000L
    /** Every other note. */
    /** v0.6.0: 7 s (was 10), so a note is gone soon after it is read. */
    const val NOTE_MS = 7_000L
    /** A hint the player asked for. */
    const val HINT_NOTE_MS = 15_000L

    const val MANTEL_TURNED = "座钟转过来了：背面刻着一个大大的“三”，还有一扇小门。"
    const val MANTEL_DOOR = "座钟的小门开了，里面有东西。"
    const val LAMP_OFF = "台灯关了。"

    /** What to say after move [move] whose answer was [out], when the shop itself said nothing (null: nothing to add). */
    fun fallback(move: String, out: List<Shop.Event>): String? {
        if (out.any { it is Shop.Event.Refused || it is Shop.Event.Said }) return null
        // Taken back down from a frame or off a shaft, rather than picked up in the room.
        val offFitting = move.startsWith("unhang") || move.startsWith("untrain")
        for (e in out) {
            when (e) {
                Shop.Event.MantelTurned -> return MANTEL_TURNED
                is Shop.Event.Opened -> if (e.prop == "mantel") return MANTEL_DOOR
                is Shop.Event.Took -> return if (offFitting) "取下了${e.item.label}。" else "拿到了${e.item.label}，在面前的托盘里。"
                is Shop.Event.Placed -> return if (e.item in Shop.PHOTO_ORDER) "${e.item.label}挂上了。" else "${e.item.label}装上了。"
                else -> Unit
            }
        }
        if (move == "open lamp" && out.isEmpty()) return LAMP_OFF
        return null
    }
}
