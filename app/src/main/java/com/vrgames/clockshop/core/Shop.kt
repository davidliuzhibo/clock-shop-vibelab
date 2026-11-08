package com.vrgames.clockshop.core

/**
 * The rules of the clock shop, free of rendering: six rooms, what lies where, what is locked and
 * what opens it, the clocks whose hands turn the room back to another hour, the things carried in
 * the hand tray, and the six pocket-watch parts. Every refusal says why, in words the player sees.
 */
class Shop {
    enum class Room(val title: String) { FRONT("前店"), WORKSHOP("工作间"), ATTIC("阁楼"), CELLAR("地下室"), TOWER("钟楼机芯室"), STUDY("师傅的书房") }

    /** The brass plates each room's moment leaves behind; they tell the astronomical clock's three hours. */
    enum class Plate(val symbol: String, val ring: String, val hour: Int) { SUN("日", "日环", 3), MOON("月", "月环", 10), STAR("星", "星环", 4) }

    /** Something carried in the hand tray. */
    enum class Item(val label: String) {
        KEY_DRAWER("柜台钥匙"), GEAR_L("大齿轮"), GEAR_M("中齿轮"), GEAR_S("小齿轮"), KEY_ATTIC("阁楼钥匙"),
        PHOTO_1("少年学徒的照片"), PHOTO_2("年轻钟表匠的照片"), PHOTO_3("新婚的照片"), PHOTO_4("抱着女儿的照片"), PHOTO_5("白发老师傅的照片"),
        KEY_CELLAR("地下室钥匙"),
        RATIO_2("二比一的传动轮"), RATIO_3("三比一的传动轮"), RATIO_4("四比一的传动轮"), RATIO_5("五比一的传动轮"),
        KEY_STUDY("书房钥匙"),
    }

    /** What happened, for the scene to show and the panel to say. */
    sealed interface Event {
        data class Said(val text: String) : Event
        data class Refused(val why: String) : Event
        data class Opened(val prop: String) : Event
        data class Took(val item: Item) : Event
        data class Placed(val item: Item, val at: String) : Event
        /** The room turned to another hour (light, things in the room). */
        data class Shifted(val room: Room, val hour: Int) : Event
        data class Part(val n: Int) : Event
        /** The mantel clock was turned round, showing what is carved on its back. */
        data object MantelTurned : Event
        /** A plate was earned; the cellar shows it. */
        data class PlateFound(val plate: Plate) : Event
        /** The tower clock's movement started running. */
        data object TowerRuns : Event
        data class RoomDone(val room: Room) : Event
        data object Finished : Event
    }

    var room = Room.FRONT; private set
    val tray = LinkedHashSet<Item>()
    val parts = LinkedHashSet<Int>()
    val opened = HashSet<String>()
    val taken = HashSet<Item>()
    /** The hour each clock shows (0-23), and the minute. */
    val clocks = HashMap<String, Pair<Int, Int>>()
    /** Gears seated in the music box, by slot (1 big, 2 middle, 3 small). */
    val gears = HashMap<Int, Item>()
    /** Photos hung in the frames, left to right (0-4). */
    val frames = HashMap<Int, Item>()
    /** The safe's three digits and the astronomical clock's three rings. */
    val dials = HashMap<String, Int>()
    val clues = LinkedHashSet<String>()
    val plates = LinkedHashSet<Plate>()
    /** The two wheels in the tower's gear train, by slot (1, 2). */
    val train = HashMap<Int, Item>()
    /** The study's desk lamp. */
    var lampOn = false; private set
    var finished = false; private set
    var steps = 0; private set
    var hints = 0; private set

    init {
        clocks["big"] = 9 to 0
        clocks["wall"] = 14 to 0
        clocks["attic"] = 11 to 30
        clocks["tower"] = 7 to 0
        clues += LETTER
    }

    fun hourOf(c: String) = clocks[c]?.first ?: 0

    /** The room's light: the hour the clock that rules this room shows, and whether its moment has come. */
    val sunlit get() = room == Room.FRONT && hourOf("big") % 12 == 3
    val night get() = room == Room.WORKSHOP && hourOf("wall") == 22
    val thatDay get() = room == Room.ATTIC && hourOf("attic") % 12 == 4 && photosRight

    private val photosRight get() = (0..4).all { frames[it] == PHOTO_ORDER[it] }
    val musicBoxBuilt get() = gears[1] == Item.GEAR_L && gears[2] == Item.GEAR_M && gears[3] == Item.GEAR_S
    val musicPlays get() = musicBoxBuilt && night

    /** The tower's pendulum, set by the dial: 0 short (25 cm), 1 middle (1 m), 2 long (2.25 m). */
    val pendulum get() = dials["pend"] ?: 0
    val pendulumSeconds get() = PENDULUM_PERIOD[pendulum]
    val pendulumRight get() = pendulum == 1
    /** How many turns the minute hand makes for one of the hour hand, through the two wheels fitted. */
    val trainRatio get() = train.values.fold(1) { n, i -> n * (RATIO[i] ?: 1) }
    val trainRight get() = train.size == 2 && trainRatio == 12
    val towerRuns get() = "lever" in opened
    val calendarRight get() = dials["month"] == 2 && dials["day"] == 11 && dials["week"] == 3

    /** What can be seen and taken in this room now (things appear as the hour changes). */
    fun visible(item: Item): Boolean = when (item) {
        Item.KEY_DRAWER -> room == Room.FRONT && "mantel" in opened
        Item.GEAR_L, Item.GEAR_M -> room == Room.WORKSHOP
        Item.GEAR_S -> room == Room.WORKSHOP && "toolbox" in opened
        Item.KEY_ATTIC -> room == Room.WORKSHOP && "safe" in opened
        in PHOTO_ORDER -> room == Room.ATTIC
        Item.KEY_CELLAR -> room == Room.ATTIC && "chest" in opened
        in RATIOS -> room == Room.TOWER
        Item.KEY_STUDY -> room == Room.TOWER && 5 in parts
        else -> false
    }

    // ---- Moves ----------------------------------------------------------------------------------

    /** Sets clock [c] to [hour]:[minute] (the player turned its hands). */
    fun setClock(c: String, hour: Int, minute: Int): List<Event> {
        steps++
        val out = ArrayList<Event>()
        val owner = CLOCK_ROOM[c] ?: return listOf(Event.Refused("这座钟拨不动。"))
        if (owner != room) return listOf(Event.Refused("这座钟不在这个房间。"))
        val wasSun = sunlit
        val wasNight = night
        val wasDay = thatDay
        clocks[c] = ((hour % 24) + 24) % 24 to (((minute % 60) + 60) % 60)
        when {
            sunlit && !wasSun -> {
                out += Event.Shifted(room, 15)
                out += plate(Plate.SUN)
                out += Event.Said("大钟敲了三下。午后的阳光照进铺子，展示柜的玻璃上显出一行字：“钥匙在座钟的肚子里。”座钟的后门也“咔”地弹开了一条缝。")
                clues += "展示柜玻璃上的字：钥匙在座钟的肚子里。"
            }
            night && !wasNight -> {
                out += Event.Shifted(room, 22)
                out += plate(Plate.MOON)
                out += Event.Said(if (musicBoxBuilt) "夜深了，台灯亮起。八音盒响了，三根梳片依次发光。" else "夜深了，台灯自己亮了。桌上露出一张草图。")
                if (musicBoxBuilt) clues += "八音盒在夜里唱出的数：3、1、5。" else clues += "台灯下的草图：八音盒只在夜里唱歌，大齿轮在下，小齿轮在上。"
            }
            thatDay && !wasDay -> {
                out += Event.Shifted(room, 16)
                out += plate(Plate.STAR)
                out += Event.Said("阁楼回到了 1936 年 3 月 12 日下午四点。墙角多出一只旧皮箱。")
                clues += "1936 年 3 月 12 日下午四点：师傅完成作品的那一天。"
            }
            !sunlit && wasSun || !night && wasNight || !thatDay && wasDay -> out += Event.Shifted(room, hour)
        }
        if (c == "tower" && towerRuns && 5 !in parts && hourOf("tower") % 12 == 0 && (clocks["tower"]?.second ?: 0) == 0) {
            out += part(5, "钟楼的大钟敲响了十二下，整座楼都在震动。第五枚怀表零件从机芯里落下，旁边还有一把书房的钥匙。")
            out += Event.RoomDone(Room.TOWER)
            out += Event.Said("钟走准了，十二响传遍全城。有什么东西从机芯里掉了下来。")
        }
        return out
    }

    private fun plate(p: Plate): Event {
        plates += p
        return Event.PlateFound(p)
    }

    /** Turns the mantel clock round on the counter: its back carries the big 三 that says what hour to set. */
    fun turnMantel(): List<Event> {
        steps++
        if (room != Room.FRONT) return listOf(Event.Refused("这个不在这个房间。"))
        if ("mantelTurned" in opened) return emptyList()
        opened += "mantelTurned"
        clues += "座钟背面刻着一个大大的“三”。"
        return listOf(Event.MantelTurned)
    }

    /** Opens (or tries to open) [prop]. */
    fun open(prop: String): List<Event> {
        steps++
        if (prop in opened) return emptyList()
        val why = when (prop) {
            "mantel" -> if (room != Room.FRONT) "" else if ("mantelTurned" !in opened) return turnMantel() else if (!sunlit && "mantel" !in openedOnce) "小门锁着。" else null
            "lever" -> if (room != Room.TOWER) "" else leverRefusal()
            "lamp" -> if (room != Room.STUDY) "" else return toggleLamp()
            "news" -> if (room != Room.STUDY) "" else return readNews()
            "cabinet" -> if (room != Room.STUDY) "" else if (calendarRight) null else "柜门纹丝不动。"
            "drawer" -> if (Item.KEY_DRAWER in tray) null else "抽屉锁着。"
            "toolbox" -> null
            "safe" -> if (dials["safe1"] == 3 && dials["safe2"] == 1 && dials["safe3"] == 5) null else "柜门纹丝不动。"
            "chest" -> if (thatDay) null else "这里什么也没有。"
            else -> null
        }
        if (why == "") return listOf(Event.Refused("这个不在这个房间。"))
        if (why != null) return listOf(Event.Refused(why))
        opened += prop
        openedOnce += prop
        val out = ArrayList<Event>(listOf(Event.Opened(prop)))
        when (prop) {
            "drawer" -> {
                tray -= Item.KEY_DRAWER
                out += part(1, "抽屉里有一枚怀表零件，和一张字条：“去工作间。八音盒会告诉你保险柜的密码。”")
                clues += "抽屉里的字条：去工作间，八音盒会告诉你保险柜的密码。"
                out += done(Room.FRONT)
            }
            "toolbox" -> out += Event.Said("工具箱里有一个小齿轮。")
            "safe" -> {
                out += part(2, "保险柜开了：第二枚怀表零件，还有一把阁楼的钥匙。")
                out += Event.Said("阁楼门上挂着锁，拿着钥匙去开吧。")
            }
            "chest" -> out += part(3, "皮箱里是第三枚怀表零件和地下室的钥匙。")
            "lever" -> {
                out += Event.TowerRuns
                out += Event.Said("离合杆推上去了，大轮小轮都转了起来。")
            }
            "cabinet" -> {
                finished = true
                out += part(6, "柜门开了。第六枚零件躺在丝绒上，下面压着师傅最后的一张字条。")
                out += Event.Said("六枚零件合在一起，成了一块完整的怀表。表盖里刻着：“给愿意花时间的人。”铺子里所有的钟同时敲响了。")
                out += Event.RoomDone(Room.STUDY)
                out += Event.Finished
            }
        }
        return out
    }

    private val openedOnce = HashSet<String>()

    fun take(item: Item): List<Event> {
        steps++
        if (item in taken) return emptyList()
        if (!visible(item)) return listOf(Event.Refused("现在还拿不到。"))
        taken += item
        tray += item
        return listOf(Event.Took(item))
    }

    /** Uses [item] from the tray on [target] (a door, a slot of the music box, a photo frame). */
    fun use(item: Item, target: String): List<Event> {
        steps++
        if (item !in tray) return listOf(Event.Refused("手里没有${item.label}。"))
        return when {
            target == "drawer" && item == Item.KEY_DRAWER -> open("drawer")
            target.startsWith("gear") && item in GEARS -> {
                val slot = target.removePrefix("gear").toIntOrNull() ?: return listOf(Event.Refused("这里放不了齿轮。"))
                val want = GEAR_FOR[slot]!!
                if (gears.containsKey(slot)) return listOf(Event.Refused("这一层已经有齿轮了。"))
                if (item != want) return listOf(Event.Refused(if (GEARS.indexOf(item) < GEARS.indexOf(want)) "这个齿轮太大了，放不进这一层。" else "这个齿轮太小了，咬不住。"))
                gears[slot] = item
                tray -= item
                val out = ArrayList<Event>(listOf(Event.Placed(item, target)))
                if (musicBoxBuilt) out += Event.Said(if (night) "八音盒响了，三根梳片依次发光：三、一、五。" else "齿轮都装好了，可八音盒没有响。")
                if (musicPlays) clues += "八音盒在夜里唱出的数：3、1、5。"
                out
            }
            target.startsWith("frame") && item in PHOTO_ORDER -> {
                val i = target.removePrefix("frame").toIntOrNull() ?: return listOf(Event.Refused("这里挂不了照片。"))
                if (frames.containsKey(i)) return listOf(Event.Refused("这个相框里已经有照片了。"))
                frames[i] = item
                tray -= item
                val out = ArrayList<Event>(listOf(Event.Placed(item, target)))
                if (frames.size == 5) out += Event.Said(
                    if (photosRight) "五张照片排好了：师傅的一生从左到右。最后一张照片背面写着：“三月十二日，下午四点，它终于走起来了。”"
                    else "五张照片都挂上了，可顺序好像不对：照片里的人应该从年少到年老。捏一下相框可以取下照片重新挂。",
                )
                if (photosRight) clues += "最后一张照片背面：三月十二日，下午四点，它终于走起来了。"
                out
            }
            target.startsWith("train") && item in RATIOS -> {
                val slot = target.removePrefix("train").toIntOrNull() ?: return listOf(Event.Refused("这里装不了传动轮。"))
                if (train.containsKey(slot)) return listOf(Event.Refused("这个轴上已经有传动轮了，捏一下可以取下来。"))
                train[slot] = item
                tray -= item
                val out = ArrayList<Event>(listOf(Event.Placed(item, target)))
                if (train.size == 2) out += Event.Said(
                    if (trainRight) "两只轮子配上了：${RATIO[train[1]]} 乘 ${RATIO[train[2]]} 等于 12，分针转十二圈，时针正好转一圈。"
                    else "这样分针转 $trainRatio 圈，时针才转一圈。捏一下轮子可以取下来。",
                )
                out
            }
            target == "study" && item == Item.KEY_STUDY -> enter(Room.STUDY, item)
            target == "attic" && item == Item.KEY_ATTIC -> enter(Room.ATTIC, item)
            target == "cellar" && item == Item.KEY_CELLAR -> enter(Room.CELLAR, item)
            else -> listOf(Event.Refused("${item.label}在这里用不上。"))
        }
    }

    /** Takes a wheel back off the gear train. */
    fun untrain(slot: Int): List<Event> {
        val w = train.remove(slot) ?: return emptyList()
        tray += w
        return listOf(Event.Took(w))
    }

    private fun leverRefusal(): String? = when {
        !pendulumRight -> "离合杆推不动：摆不合拍。"
        train.size < 2 -> "离合杆推不动：传动轴上缺轮子。"
        !trainRight -> "离合杆推不动：现在分针转 $trainRatio 圈，时针才转一圈。"
        else -> null
    }

    private fun toggleLamp(): List<Event> {
        lampOn = !lampOn
        return if (lampOn) listOf(Event.Said("台灯亮了。")) else emptyList()
    }

    private fun readNews(): List<Event> {
        if (!lampOn) return listOf(Event.Refused("太暗了，看不清这张纸上写的什么。"))
        clues += "旧报纸的日期：1936 年 3 月 12 日，星期四。"
        return listOf(Event.Said("旧报纸的日期：1936 年 3 月 12 日，星期四。"))
    }

    /** Takes a photo back down from frame [i] (to rehang it). */
    fun unhang(i: Int): List<Event> {
        val p = frames.remove(i) ?: return emptyList()
        tray += p
        return listOf(Event.Took(p))
    }

    fun setDial(d: String, value: Int): List<Event> {
        steps++
        val max = DIAL_STEPS[d] ?: if (d.startsWith("safe")) 10 else 12
        dials[d] = ((value % max) + max) % max
        if (d.startsWith("ring") && room == Room.CELLAR && dials["ring1"] == 3 && dials["ring2"] == 10 && dials["ring3"] == 4 && 4 !in parts) {
            return listOf(
                part(4, "三个时刻对齐了：三点、十点、四点。天文钟转动起来，顶上的发条小鸟唱起了歌，第四枚零件从钟里落下。"),
                Event.Said("天文钟旁边打开了一道窄门。"),
                Event.RoomDone(Room.CELLAR),
            )
        }
        return emptyList()
    }

    /** Goes on from the front to the workshop once the front is done (its door is open). */
    fun goWorkshop(): List<Event> {
        if (room != Room.FRONT) return emptyList()
        if (1 !in parts) return listOf(Event.Refused("通往工作间的门锁着。"))
        room = Room.WORKSHOP
        return listOf(Event.Said("你走进了工作间。"))
    }

    /** Up the narrow stair behind the astronomical clock, once its part has fallen. */
    fun goTower(): List<Event> {
        if (room != Room.CELLAR) return emptyList()
        if (4 !in parts) return listOf(Event.Refused("天文钟的背后没有路。"))
        room = Room.TOWER
        return listOf(Event.Said("你爬上了钟楼。巨大的机芯停着。"))
    }

    private fun enter(r: Room, key: Item): List<Event> {
        val from = when (r) { Room.ATTIC -> Room.WORKSHOP; Room.STUDY -> Room.TOWER; else -> Room.ATTIC }
        if (room != from) return listOf(Event.Refused("这把钥匙不是开这里的。"))
        tray -= key
        val out = ArrayList<Event>(listOf(Event.RoomDone(from)))
        room = r
        out += Event.Said(when (r) {
            Room.ATTIC -> "你打开阁楼的门走了上去。"
            Room.STUDY -> "你打开了师傅的书房。屋里很暗。"
            else -> "你打开地下室的门走了下去。"
        })
        return out
    }

    private fun part(n: Int, text: String): Event {
        parts += n
        return Event.Part(n).also { clues += text }
    }

    private fun done(r: Room) = Event.RoomDone(r)

    /** The next step, told plainly: first a nudge, then the answer, as the player asks again. */
    fun hint(): String {
        hints++
        val list = HINTS[room]!!
        val i = list.indexOfFirst { !it.first(this) }
        if (i < 0) return "这里已经没有要做的了。"
        // Ask once for a nudge in the right direction; ask again at the same step for the plain answer.
        val key = "${room.name}$i"
        val nudge = NUDGES[key]
        if (nudge != null && nudged.add(key)) return nudge
        return list[i].second
    }

    private val nudged = HashSet<String>()

    companion object {
        const val LETTER = "师傅的信：“孩子，铺子里藏着我一生最得意的作品。钟会带你回到我想让你看见的时刻。”"
        val GEARS = listOf(Item.GEAR_L, Item.GEAR_M, Item.GEAR_S)
        val RATIOS = listOf(Item.RATIO_2, Item.RATIO_3, Item.RATIO_4, Item.RATIO_5)
        val RATIO = mapOf(Item.RATIO_2 to 2, Item.RATIO_3 to 3, Item.RATIO_4 to 4, Item.RATIO_5 to 5)
        /** T = 2 pi sqrt(L / g) for 25 cm, 1 m and 2.25 m, in round seconds. */
        val PENDULUM_PERIOD = listOf(1.0, 2.0, 3.0)
        val PENDULUM_LABEL = listOf("1 秒", "2 秒", "3 秒")
        val PENDULUM_LENGTH = listOf(.25f, 1f, 2.25f)
        val DIAL_STEPS = mapOf("pend" to 3, "month" to 12, "day" to 31, "week" to 7)
        val GEAR_FOR = mapOf(1 to Item.GEAR_L, 2 to Item.GEAR_M, 3 to Item.GEAR_S)
        val PHOTO_ORDER = listOf(Item.PHOTO_1, Item.PHOTO_2, Item.PHOTO_3, Item.PHOTO_4, Item.PHOTO_5)
        val CLOCK_ROOM = mapOf("big" to Room.FRONT, "wall" to Room.WORKSHOP, "attic" to Room.ATTIC, "tower" to Room.TOWER)

        /** First answer to a hint at a step ([HINTS] index in that room): where to look, not what to do. */
        val NUDGES = mapOf(
            "FRONT0" to "这家店的钟都停在同一个时刻。有一座摆得有点歪。",
            "FRONT1" to "座钟背后刻的字，和店里那些停了的钟，说的是同一件事。正前方那座还在走。",
            "FRONT2" to "光变了之后，再看看座钟。",
            "WORKSHOP0" to "八音盒上有三层空座。工作台上不止摆着看得见的东西。",
            "WORKSHOP2" to "八音盒上和墙上的钟旁边，刻着同一个符号。",
            "WORKSHOP3" to "八音盒唱歌时，梳片是按什么顺序亮的？",
            "ATTIC1" to "五张照片，是同一个人的一生。",
            "ATTIC2" to "翻过每一张照片了吗？最后一张的背面写着字。",
            "CELLAR0" to "天文钟每个环上都有一个符号。这些符号，你在哪儿见过？",
            "TOWER0" to "墙上那块铜牌写着什么？对照另一边的秒钟看看。",
            "TOWER1" to "铜牌上还有一句。两只轮子一起转时，转速是怎么算的？",
            "STUDY2" to "报纸的日期，和阁楼那张照片背面的日子，是同一天。",
        )

        /** Each room's steps in order: done-when, and the hint for it. */
        val HINTS: Map<Room, List<Pair<(Shop) -> Boolean, String>>> = mapOf(
            Room.FRONT to listOf(
                { s: Shop -> "mantelTurned" in s.opened } to "柜台右边的座钟有点不对劲，捏一下，把它转过来看看背面。",
                { s: Shop -> s.sunlit || "mantel" in s.opened } to "座钟背面刻着“三”。把正前方的大钟拨到三点：捏一下钟面，用弹出的按钮拨。",
                { s: Shop -> "mantel" in s.opened } to "三点的阳光照进来了，座钟的后门也松了：捏一下座钟，打开它。",
                { s: Shop -> Item.KEY_DRAWER in s.taken } to "座钟的后门开了，里面有一把钥匙，捏一下拿起来。",
                { s: Shop -> 1 in s.parts } to "钥匙在手边的托盘里了：捏一下柜台的抽屉，就用钥匙打开它。",
            ),
            Room.WORKSHOP to listOf(
                { s: Shop -> s.taken.containsAll(GEARS) || s.musicBoxBuilt } to "工作台上有两个齿轮，工具箱里还有一个：打开工具箱，把三个齿轮都拿起来。",
                { s: Shop -> s.musicBoxBuilt } to "把齿轮装进八音盒：先捏一下托盘里的齿轮选中它，再捏八音盒上方发光的一层。大的放最下层，小的放最上层。",
                { s: Shop -> s.musicPlays } to "八音盒只在夜里唱歌：把墙上的挂钟拨到晚上十点。",
                { s: Shop -> "safe" in s.opened } to "八音盒唱出的数是 3、1、5：捏保险柜上的转盘，把三个转盘依次拨到 3、1、5，再捏保险柜的门。",
                { s: Shop -> false } to "阁楼钥匙在托盘里：转向右边，捏一下发光的阁楼门。",
            ),
            Room.ATTIC to listOf(
                { s: Shop -> s.taken.containsAll(PHOTO_ORDER) || s.frames.size == 5 } to "右边的箱子上有五张照片，都拿起来。",
                { s: Shop -> (0..4).all { s.frames[it] == PHOTO_ORDER[it] } } to "先捏一下托盘里的照片选中它，再捏墙上的相框。从左到右，照片里的人从年少到年老；挂错了，捏一下相框就能取下。",
                { s: Shop -> s.thatDay } to "最后一张照片背面写着“下午四点”：把阁楼的钟拨到四点。",
                { s: Shop -> "chest" in s.opened } to "墙角多出了一只皮箱，捏一下打开它。",
                { s: Shop -> false } to "地下室钥匙在托盘里：捏一下前面地板上的活门。",
            ),
            Room.CELLAR to listOf(
                { s: Shop -> 4 in s.parts } to "墙上三块铜牌写得很清楚：日牌 3 点、月牌 10 点、星牌 4 点，就是你一路拨过的三个时刻。捏天文钟的三个环：外圈日环、中圈月环、内圈星环，依次拨到牌上的钟点。",
                { s: Shop -> false } to "天文钟背后开了一道窄门：捏一下发光的门，上钟楼。",
            ),
            Room.TOWER to listOf(
                { s: Shop -> s.pendulumRight } to "钟摆一来一回要正好两秒，摆长决定快慢：捏摆旁边的转盘，把摆长调到 1 米。可以对照墙上的秒表看。",
                { s: Shop -> s.trainRight } to "分针转 12 圈，时针转 1 圈，所以两只传动轮相乘要等于 12。架子上有 2、3、4、5 比 1 四只：3 和 4 正好。先捏托盘里的轮子，再捏传动轴。",
                { s: Shop -> s.towerRuns } to "摆和轮子都对了：捏一下红色的离合杆，让机芯转起来。",
                { s: Shop -> 5 in s.parts } to "机芯转起来了：捏塔钟的钟面，把它拨到正十二点。",
                { s: Shop -> Item.KEY_STUDY in s.taken } to "书房的钥匙落在机芯下面的托架上，捏一下拿起来。",
                { s: Shop -> false } to "书房钥匙在托盘里：捏一下发光的书房门。",
            ),
            Room.STUDY to listOf(
                { s: Shop -> s.lampOn } to "屋里太暗。先捏书桌上的台灯。",
                { s: Shop -> s.clues.any { it.startsWith("旧报纸") } } to "灯下有一张旧报纸，捏一下读它的日期。",
                { s: Shop -> s.calendarRight } to "报纸的日期是 1936 年 3 月 12 日，星期四。捏墙边的万年历，把月、日、星期分别拨到 3、12、四。",
                { s: Shop -> s.finished } to "万年历对上了：捏一下旁边的柜门。",
            ),
        )
    }
}
