package com.vrgames.clockshop.core

import com.vrgames.clockshop.core.Shop.Item

/**
 * A way through the whole shop, step by step (the tests check it; the test hook plays it). The same
 * words record the player's moves, so a saved game is replayed move by move ([run]).
 */
object Walkthrough {
    val steps = listOf(
        "open mantel", "clock big 15", "open mantel", "take KEY_DRAWER", "use KEY_DRAWER drawer", "go workshop",
        "take GEAR_L", "take GEAR_M", "open toolbox", "take GEAR_S",
        "use GEAR_L gear1", "use GEAR_M gear2", "use GEAR_S gear3", "clock wall 22",
        "dial safe1 3", "dial safe2 1", "dial safe3 5", "open safe", "take KEY_ATTIC", "use KEY_ATTIC attic",
        "take PHOTO_1", "take PHOTO_2", "take PHOTO_3", "take PHOTO_4", "take PHOTO_5",
        "hang PHOTO_1 frame0", "hang PHOTO_2 frame1", "hang PHOTO_3 frame2", "hang PHOTO_4 frame3", "hang PHOTO_5 frame4",
        "clock attic 16", "open chest", "take KEY_CELLAR", "use KEY_CELLAR cellar",
        "dial ring1 3", "dial ring2 10", "dial ring3 4", "go tower",
        "take RATIO_2", "take RATIO_3", "take RATIO_4", "take RATIO_5", "dial pend 1",
        "use RATIO_3 train1", "use RATIO_4 train2", "open lever", "clock tower 12", "take KEY_STUDY", "use KEY_STUDY study",
        "open lamp", "open news", "dial month 2", "dial day 11", "dial week 3", "open cabinet",
    )

    fun run(s: Shop, step: String): List<Shop.Event> {
        val w = step.split(" ")
        return when (w[0]) {
            "clock" -> s.setClock(w[1], w[2].toInt(), w.getOrNull(3)?.toInt() ?: 0)
            "open" -> s.open(w[1])
            "take" -> s.take(Item.valueOf(w[1]))
            "use", "hang" -> s.use(Item.valueOf(w[1]), w[2])
            "dial" -> s.setDial(w[1], w[2].toInt())
            "go" -> if (w.getOrNull(1) == "tower") s.goTower() else s.goWorkshop()
            "untrain" -> s.untrain(w[1].toInt())
            "unhang" -> s.unhang(w[1].toInt())
            "hint" -> s.hint().let { emptyList() }
            else -> emptyList()
        }
    }
}
