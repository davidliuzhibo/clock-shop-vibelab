package com.vrgames.clockshop.render

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.vrgames.clockshop.core.Shop
import com.vrgames.clockshop.core.Shop.Item
import com.vrgames.clockshop.core.Shop.Room
import com.vrgames.clockshop.core.Vec3
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The clock shop around the seated player (full immersion). Frame: the player's feet at the origin,
 * facing -z; everything to touch stands ahead, within about 100 degrees either side, at most three
 * meters off, and in plain sight from the chair. A room is built when entered and again when its
 * hour changes the light ([Mood]); [sync] brings it in line with the [Shop]: hands of clocks, drawers
 * and lids, things to take, gears and photos put in place, dials, and the hand tray.
 */
class RoomView(private val models: Map<String, Entity>, private val tex: Map<String, TextureResource>, private val lighting: Lighting) {
    val root = Entity()
    private var roomRoot = Entity().also { root.addChild(it) }
    private var built: Room? = null
    private var builtMood: Mood? = null
    private val hits = HashMap<Entity, String>()
    private val parts = HashMap<String, Entity>()
    private val hands = HashMap<String, Pair<Entity, Entity>>()
    private val sphere = MeshResource.createSphere(1f).shared()
    private val sparkle = Sparks("sparkle", 160, sphere, glow(color(1f, 0.85f, 0.45f, 1f)).shared()).also { root.addChild(it.entity) }
    private val dust = Sparks("dust", 80, sphere, glow(color(1f, 0.95f, 0.8f, 0.35f)).shared()).also { root.addChild(it.entity) }
    /** The hand tray, turned (about the player) toward where the player faces. */
    val trayRoot = Entity().also { root.addChild(it) }
    private val trayTokens = HashMap<Item, Entity>()
    private val ringOf = HashMap<Item, Entity>()
    private var clock = 0.0
    var mood = Mood.DAY; private set

    // Materials: remade with each build, since how much they glow by themselves depends on the hour.
    private lateinit var woodDark: Material
    private lateinit var woodMid: Material
    private lateinit var floorMat: Material
    private lateinit var brass: Material
    private lateinit var paper: Material
    private lateinit var dark: Material
    private val glass = pbr(color(0.75f, 0.85f, 0.9f, 0.25f), roughness = 0.1f, blending = BlendingMode.TRANSPARENT).shared()
    private val selectedMat = glow(color(1f, 0.85f, 0.35f, 0.9f)).shared()
    private val slotMat = glow(color(1f, 0.85f, 0.35f, 0.35f)).shared()
    private val lampLight = glow(color(1f, 0.85f, 0.5f, 0.85f)).shared()
    private val sunMat = glow(color(1f, 0.9f, 0.6f, 0.22f)).shared()
    private val trayBrass = pbr(color(0.85f, 0.66f, 0.3f), metallic = 0.85f, roughness = 0.35f, emissive = color(0.35f, 0.27f, 0.12f)).shared()

    private fun makeMaterials() {
        woodDark = pbr(color(0.32f, 0.2f, 0.12f), roughness = 0.7f)
        woodMid = pbr(color(0.5f, 0.33f, 0.2f), roughness = 0.75f)
        floorMat = pbr(color(0.42f, 0.28f, 0.18f), roughness = 0.8f)
        brass = pbr(color(0.85f, 0.66f, 0.3f), metallic = 0.85f, roughness = 0.35f)
        paper = pbr(color(0.93f, 0.9f, 0.8f), roughness = 0.95f)
        dark = pbr(color(0.1f, 0.1f, 0.1f))
    }

    /** A loaded panel frame, given the shop's light. */
    fun frame(name: String): Entity? = models[name]?.clone(Entity.CloneOptions(true, true))?.also { lighting.receive(it) }

    /** The baked room shell: one mesh, one unlit atlas, tinted for the hour instead of relit. */
    private var shellTexture = HashMap<String, TextureResource>()
    private fun shell(name: String, tint: Color4): Entity? {
        val mesh = models["shell_$name"]?.clone(Entity.CloneOptions(true, true)) ?: return null
        val t = shellTexture.getOrPut(name) { TextureResource.load("textures/shell_$name.jpg", com.pico.spatial.core.ecs.LoadType.FROM_ASSETS, com.pico.spatial.core.ecs.resource.TextureCreateOption()).shared() }
        setAllMaterials(mesh, UnlitMaterial.create().apply { setBaseColorTexture(t); setBaseColor(tint) })
        return mesh
    }

    private fun setAllMaterials(e: Entity, m: Material) {
        e.components[com.pico.spatial.core.ecs.ModelComponent::class.java]?.materials?.let { mats -> for (i in 0 until mats.size) mats[i] = m }
        e.getChildren().forEach { setAllMaterials(it, m) }
    }

    private fun textured(name: String, tint: Color4 = color(1f, 1f, 1f), glowShare: Float = 0.3f): Material? = tex[name]?.let { t ->
        PhysicallyBasedMaterial.create().apply {
            setBaseColorTexture(t)
            setBaseColor(tint)
            setRoughness(0.9f)
            val g = max(glowShare, ambient)
            setEmissiveTexture(t)
            setEmissiveColor(color(tint.red * g, tint.green * g, tint.blue * g))
        }
    }

    // ---- Building blocks ----

    private fun box(size: Vector3, at: Vector3, m: Material, parent: Entity = roomRoot) =
        model(MeshResource.createBox(size, 0.005f), m).also { it.setPos(at); parent.addChild(it) }

    private fun piece(name: String, height: Float): Entity? = models[name]?.clone(Entity.CloneOptions(true, true))?.also {
        it.setPos(Vector3(0f, 0f, 0f))
        it.setScale(height)
    }

    /**
     * Makes [e] something to pinch, known as [id]. The box is at least [MIN_HIT] across, so things a
     * few meters off are easy to catch.
     */
    private fun hit(e: Entity, id: String, size: Vector3, center: Vector3 = Vector3(0f, 0f, 0f)) {
        val s = Vector3(max(size.x, MIN_HIT), max(size.y, MIN_HIT), max(size.z, 0.05f))
        e.components.set(CollisionComponent(listOf(ShapeResource.createBox(s).offsetByTranslation(center)), PhysicsMaterialResource()))
        e.components.set(InteractableComponent())
        e.components.set(HoverEffectComponent())
        hits[e] = id
        parts[id] = e
    }

    fun hitOf(e: Entity?): String? {
        var cur = e
        while (cur != null) {
            hits[cur]?.let { return it }
            cur = cur.getParent()
        }
        return null
    }

    /** Where thing [id] stands (room frame), for sounds, sparkles and the panels. */
    fun spotOf(id: String): Vec3? = parts[id]?.let { e ->
        val p = e.convertPositionTo(Vector3(0f, 0f, 0f), root)
        Vec3(p.x.toDouble(), p.y.toDouble(), p.z.toDouble())
    }

    /** A clock face with two hands; [id] is the clock's name in the [Shop]. Faces +z unless turned. */
    private fun clockFace(id: String, at: Vector3, r: Float, yaw: Float = 0f, parent: Entity = roomRoot) {
        val face = Entity().apply { setPos(at); transform().eulerAngles = EulerAngles(0f, yaw, 0f) }
        face.addChild(model(MeshResource.createCylinder(0.02f, r), paper).apply { transform().eulerAngles = EulerAngles(90f, 0f, 0f) })
        face.addChild(model(MeshResource.createTorus(r + 0.02f, r), brass).apply { transform().eulerAngles = EulerAngles(90f, 0f, 0f) })
        for (k in 0 until 12) {
            val a = Math.toRadians(k * 30.0)
            face.addChild(model(MeshResource.createBox(Vector3(0.012f, if (k % 3 == 0) 0.05f else 0.03f, 0.005f), 0f), dark).apply {
                setPos(Vector3((sin(a) * r * 0.85).toFloat(), (cos(a) * r * 0.85).toFloat(), 0.015f))
                transform().eulerAngles = EulerAngles(0f, 0f, -k * 30f)
            })
        }
        val hour = Entity().apply { setPos(Vector3(0f, 0f, 0.022f)) }
        hour.addChild(model(MeshResource.createBox(Vector3(0.025f, r * 0.55f, 0.006f), 0f), dark).apply { setPos(Vector3(0f, r * 0.25f, 0f)) })
        val minute = Entity().apply { setPos(Vector3(0f, 0f, 0.028f)) }
        minute.addChild(model(MeshResource.createBox(Vector3(0.016f, r * 0.85f, 0.006f), 0f), dark).apply { setPos(Vector3(0f, r * 0.38f, 0f)) })
        // A brass knob at the minute hand's tip: the thing to hold and turn round (2026-11-10 review).
        minute.addChild(model(sphere, brass).apply { setScale(max(0.022f, r * 0.12f)); setPos(Vector3(0f, r * 0.72f, 0.008f)) })
        face.addChild(hour)
        face.addChild(minute)
        face.addChild(model(sphere, brass).apply { setScale(0.018f); setPos(Vector3(0f, 0f, 0.034f)) })
        parent.addChild(face)
        hit(face, "clock:$id", Vector3(r * 2.4f, r * 2.4f, 0.12f))
        hands[id] = hour to minute
    }

    private fun gearMesh(r: Float, m: Material) = Entity().apply {
        addChild(model(MeshResource.createCylinder(0.012f, r), m))
        addChild(model(MeshResource.createCylinder(0.016f, r * 0.25f), m))
        for (k in 0 until 10) {
            val a = Math.toRadians(k * 36.0)
            addChild(model(MeshResource.createBox(Vector3(r * 0.35f, 0.012f, r * 0.22f), 0f), m).apply {
                setPos(Vector3((sin(a) * r).toFloat(), 0f, (cos(a) * r).toFloat()))
                transform().eulerAngles = EulerAngles(0f, k * 36f, 0f)
            })
        }
    }

    /** A transmission wheel with its ratio cast on a plate: bigger ratio, bigger wheel. */
    private fun ratioWheel(i: Item, m: Material) = Entity().apply {
        val n = Shop.RATIO[i] ?: 2
        val r = 0.04f + n * 0.008f
        addChild((models["wheel_small"]?.clone(Entity.CloneOptions(true, true)) ?: gearMesh(r, m)).apply { setScale(if (models["wheel_small"] != null) r * 2 else 1f) })
        addChild(plate("plate_r$n", 0.05f).apply { setPos(Vector3(0f, 0f, 0.016f)) })
    }

    /** A round brass plate facing +z, from a texture drawn with its symbol (tools/make_plates.py). */
    private fun plate(name: String, radius: Float): Entity =
        (models["plate_disc"]?.clone(Entity.CloneOptions(true, true)) ?: model(MeshResource.createSphere(1f), brass)).apply {
            setScale(radius)
            textured(name, glowShare = 0.5f)?.let { setAllMaterials(this, it) }
        }

    private fun keyMesh(m: Material) = Entity().apply {
        addChild(model(MeshResource.createTorus(0.025f, 0.017f), m).apply { setPos(Vector3(-0.04f, 0f, 0f)); transform().eulerAngles = EulerAngles(90f, 0f, 0f) })
        addChild(model(MeshResource.createBox(Vector3(0.07f, 0.008f, 0.008f), 0f), m).apply { setPos(Vector3(0.015f, 0f, 0f)) })
        addChild(model(MeshResource.createBox(Vector3(0.01f, 0.02f, 0.008f), 0f), m).apply { setPos(Vector3(0.045f, -0.012f, 0f)) })
    }

    /** A photo: the old print on a card (0.15 x 0.2 m). */
    private fun photoMesh(i: Item): Entity {
        val n = Shop.PHOTO_ORDER.indexOf(i) + 1
        return model(MeshResource.createBox(Vector3(0.15f, 0.2f, 0.004f), 0f), textured("photo$n", glowShare = 0.35f) ?: paper)
    }

    /** A thing to take, as it looks lying in the room or in the hand tray. */
    private fun itemLook(i: Item, m: Material = brass): Entity = when (i) {
        Item.GEAR_L, Item.GEAR_M, Item.GEAR_S -> gearMesh(GEAR_R[i]!!, m)
        Item.KEY_DRAWER, Item.KEY_ATTIC, Item.KEY_CELLAR, Item.KEY_STUDY -> keyMesh(m)
        Item.RATIO_2, Item.RATIO_3, Item.RATIO_4, Item.RATIO_5 -> ratioWheel(i, m)
        else -> photoMesh(i)
    }

    // ---- Rooms ----

    /** Builds [r] around the player in the light of [m] (the old room goes). */
    fun build(r: Room, m: Mood) {
        if (built == r && builtMood == m) return
        built = r
        builtMood = m
        mood = m
        ambient = m.ambient
        makeMaterials()
        root.removeChild(roomRoot)
        hits.keys.toList().forEach { if (it !in trayTokens.values) hits.remove(it) }
        parts.clear()
        hands.clear()
        roomRoot = Entity()
        root.addChild(roomRoot)
        val tint = when (m) {
            Mood.SUNLIT -> color(1f, 0.95f, 0.8f)
            Mood.NIGHT -> color(0.34f, 0.32f, 0.42f)
            Mood.THAT_DAY -> color(1f, 0.86f, 0.66f)
            Mood.DARK -> color(0.22f, 0.2f, 0.22f)
            Mood.CELLAR, Mood.DAY -> color(0.9f, 0.9f, 0.9f)
        }
        // The shell: floor, ceiling and walls, baked with their own light (tools/build_shells.py).
        shell(r.name.lowercase(), tint)?.let { roomRoot.addChild(it) }
        // Clocks everywhere in the shop downstairs: its tick, hung high on the walls around (not to touch).
        for ((at, yaw) in DECOR_CLOCKS) {
            if (r != Room.FRONT && r != Room.WORKSHOP) break
            (piece("wallclock", 0.42f) ?: Entity().also { c ->
                c.addChild(model(MeshResource.createBox(Vector3(0.3f, 0.42f, 0.08f), 0.01f), woodMid).apply { setPos(Vector3(0f, 0.21f, 0f)) })
                c.addChild(model(MeshResource.createCylinder(0.02f, 0.11f), paper).apply { setPos(Vector3(0f, 0.28f, 0.045f)); transform().eulerAngles = EulerAngles(90f, 0f, 0f) })
            }).apply {
                setPos(at)
                transform().eulerAngles = EulerAngles(0f, yaw, 0f)
                roomRoot.addChild(this)
                // Every clock on these walls stopped at the same hour: a pattern to notice.
                if (r == Room.FRONT) addChild(plate("dial_three", 0.082f / 0.42f).apply { setPos(Vector3(0f, 0.68f, 0.135f)) })
            }
        }
        when (r) {
            Room.FRONT -> buildFront()
            Room.WORKSHOP -> buildWorkshop()
            Room.ATTIC -> buildAttic()
            Room.CELLAR -> buildCellar()
            Room.TOWER -> buildTower()
            Room.STUDY -> buildStudy()
        }
        lighting.use(m)
        lighting.receive(root)
    }

    private fun buildFront() {
        // The grandfather clock straight ahead: pinch its face to set the hour.
        (piece("grandfather", 2.1f) ?: box(Vector3(0.6f, 2.1f, 0.4f), Vector3(0f, 1.05f, 0f), woodDark)).also { it.setPos(Vector3(0f, 0f, -2.6f)); roomRoot.addChild(it) }
        clockFace("big", Vector3(0f, 1.62f, -2.36f), 0.2f)
        // The counter to the right front, the mantel clock on it, the drawer facing the player.
        box(Vector3(1.6f, 1.0f, 0.5f), Vector3(0.75f, 0.5f, -1.45f), woodMid)
        box(Vector3(1.66f, 0.04f, 0.56f), Vector3(0.75f, 1.02f, -1.45f), woodDark)
        // Worn into the counter by years of turning: a faint ring under the mantel clock, which stands a little askew.
        roomRoot.addChild(model(MeshResource.createTorus(0.215f, 0.205f), pbr(color(0.13f, 0.08f, 0.04f), roughness = 0.9f)).apply {
            setPos(Vector3(1.15f, 1.042f, -1.5f))
        })
        val mantel = Entity().apply { setPos(Vector3(1.15f, 1.04f, -1.5f)); transform().eulerAngles = EulerAngles(0f, MANTEL_ASKEW, 0f) }
        mantelYaw = MANTEL_ASKEW
        mantel.addChild(piece("mantel", 0.42f) ?: box(Vector3(0.35f, 0.42f, 0.2f), Vector3(0f, 0.21f, 0f), woodDark, mantel))
        // On its back, away from the player until it is turned: the little door and the carved 三.
        mantel.addChild(plate("dial_three", 0.075f).apply { setPos(Vector3(0f, 0.29f, 0.115f)) })
        val back = box(Vector3(0.2f, 0.22f, 0.012f), Vector3(0f, 0.2f, -0.11f), woodDark, mantel)
        back.addChild(plate("plate_san", 0.085f).apply { setPos(Vector3(0f, 0f, -0.008f)); transform().eulerAngles = EulerAngles(0f, 180f, 0f) })
        parts["mantelDoor"] = back
        roomRoot.addChild(mantel)
        hit(mantel, "mantel", Vector3(0.42f, 0.48f, 0.32f), Vector3(0f, 0.22f, 0f))
        val drawer = box(Vector3(0.5f, 0.15f, 0.06f), Vector3(0.6f, 0.8f, -1.18f), woodDark)
        drawer.addChild(model(sphere, brass).apply { setScale(0.02f); setPos(Vector3(0f, 0f, 0.04f)) })
        hit(drawer, "drawer", Vector3(0.55f, 0.2f, 0.12f))
        item(Item.KEY_DRAWER, Vector3(0.85f, 1.06f, -1.38f))
        // The master's letter, lying on the counter.
        val letter = box(Vector3(0.22f, 0.004f, 0.3f), Vector3(0.35f, 1.045f, -1.35f), textured("letter", glowShare = 0.35f) ?: paper)
        letter.transform().eulerAngles = EulerAngles(0f, 12f, 0f)
        hit(letter, "letter", Vector3(0.26f, 0.06f, 0.32f))
        // The display case to the left, its glass where the afternoon sun writes.
        box(Vector3(1.0f, 0.9f, 0.5f), Vector3(-1.4f, 0.45f, -1.6f), woodMid)
        parts["case"] = box(Vector3(1.0f, 0.5f, 0.5f), Vector3(-1.4f, 1.15f, -1.6f), glass)
        for (k in 0 until 3) (piece("pocketwatch", 0.12f) ?: model(sphere, brass).apply { setScale(0.04f) }).apply {
            setPos(Vector3(-1.7f + k * 0.3f, 0.92f, -1.6f)); roomRoot.addChild(this)
        }
        (piece("gramophone", 0.6f))?.apply { setPos(Vector3(-2.3f, 0f, -0.6f)); transform().eulerAngles = EulerAngles(0f, 70f, 0f); roomRoot.addChild(this) }
        // The words on the glass and the beam of sun, when the hour comes.
        val words = box(Vector3(0.6f, 0.12f, 0.004f), Vector3(-1.4f, 1.2f, -1.34f), tex["glasswords"]?.let { t -> UnlitMaterial.create(BlendingMode.ADD).apply { setBaseColorTexture(t) } } ?: glow(color(1f, 0.9f, 0.6f, 0.6f)))
        parts["words"] = words
        val beam = box(Vector3(0.7f, 3.4f, 0.5f), Vector3(-0.6f, 1.6f, -1.5f), sunMat)
        beam.transform().eulerAngles = EulerAngles(0f, 0f, 38f)
        parts["sun"] = beam
        // A strip of brass-set numerals in the floor: where the sun's patch falls, hour by hour.
        val inlay = box(Vector3(0.14f, 0.004f, 1.15f), Vector3(-2.05f, 0.004f, -0.98f), textured("floor_hours", glowShare = 0.4f) ?: woodMid)
        inlay.transform().eulerAngles = EulerAngles(0f, -60f, 0f)
        val patch = model(MeshResource.createBox(Vector3(0.3f, 0.002f, 0.2f), 0f), sunMat).apply { setPos(Vector3(-2.6f, 0.008f, -0.7f)) }
        roomRoot.addChild(patch)
        parts["sunPatch"] = patch
        door("go:workshop", Vector3(2.95f, 1.05f, -1.2f), -90f)
    }

    private fun buildWorkshop() {
        box(Vector3(2.0f, 0.9f, 0.8f), Vector3(0f, 0.45f, -1.25f), woodMid)
        box(Vector3(2.06f, 0.04f, 0.86f), Vector3(0f, 0.92f, -1.25f), woodDark)
        // The music box in the middle of the bench, with three gear seats above it (big at the bottom).
        val mb = Entity().apply { setPos(Vector3(0f, 0.94f, -1.25f)) }
        mb.addChild(piece("musicbox", 0.22f) ?: box(Vector3(0.3f, 0.15f, 0.2f), Vector3(0f, 0.075f, 0f), woodDark, mb))
        // The same moon is cut in the music box's front and beside the wall clock's face.
        mb.addChild(plate("mark_moon", 0.03f).apply { setPos(Vector3(0f, 0.08f, 0.115f)) })
        roomRoot.addChild(mb)
        parts["musicbox"] = mb
        for (k in 1..3) {
            val seat = Entity().apply { setPos(Vector3(0f, 1.2f + (k - 1) * 0.1f, -1.25f)) }
            val r = GEAR_R[Shop.GEAR_FOR[k]!!]!!
            seat.addChild(model(MeshResource.createTorus(r + 0.012f, r + 0.002f), slotMat).apply { setName("ring") })
            seat.addChild(model(MeshResource.createCylinder(0.09f, 0.006f), brass).apply { setPos(Vector3(0f, -0.05f, 0f)) })
            roomRoot.addChild(seat)
            hit(seat, "gear$k", Vector3(0.26f, 0.09f, 0.26f))
        }
        item(Item.GEAR_L, Vector3(-0.5f, 0.95f, -1.4f))
        item(Item.GEAR_M, Vector3(0.45f, 0.95f, -1.0f))
        // The toolbox at the left end of the bench, the small gear inside.
        val tb = box(Vector3(0.36f, 0.16f, 0.22f), Vector3(-0.75f, 1.02f, -1.0f), pbr(color(0.55f, 0.15f, 0.12f)))
        parts["toolboxLid"] = box(Vector3(0.37f, 0.03f, 0.23f), Vector3(-0.75f, 1.115f, -1.0f), pbr(color(0.6f, 0.18f, 0.14f)))
        hit(tb, "toolbox", Vector3(0.4f, 0.25f, 0.26f))
        item(Item.GEAR_S, Vector3(-0.75f, 1.14f, -0.98f))
        (piece("desklamp", 0.45f) ?: box(Vector3(0.12f, 0.45f, 0.12f), Vector3(0f, 0.22f, 0f), brass)).apply { setPos(Vector3(0.75f, 0.94f, -1.5f)); roomRoot.addChild(this) }
        // A green glass shade over the model's own (its texture came out smeared).
        roomRoot.addChild(model(MeshResource.createCylinder(0.34f, 0.075f), pbr(color(0.1f, 0.45f, 0.22f), metallic = 0.1f, roughness = 0.25f)).apply {
            setPos(Vector3(0.75f, 1.345f, -1.45f))
            transform().eulerAngles = EulerAngles(0f, 0f, 90f)
        })
        val bulb = model(sphere, lampLight).apply { setScale(0.04f); setPos(Vector3(0.75f, 1.3f, -1.47f)) }
        roomRoot.addChild(bulb)
        parts["lamp"] = bulb
        val pool = model(MeshResource.createCylinder(0.002f, 0.4f), glow(color(1f, 0.8f, 0.45f, 0.35f))).apply { setPos(Vector3(0.55f, 0.945f, -1.15f)) }
        roomRoot.addChild(pool)
        parts["pool"] = pool
        // The sketch the lamp shows at night.
        parts["sketch"] = box(Vector3(0.4f, 0.004f, 0.3f), Vector3(0.45f, 0.945f, -1.05f), textured("sketch", glowShare = 0.45f) ?: paper).also {
            it.transform().eulerAngles = EulerAngles(0f, -8f, 0f)
        }
        // The wall clock straight ahead rules the hour here.
        // (Its face sits high enough that the line from the chair passes over the gear seats.)
        (piece("wallclock", 0.75f) ?: box(Vector3(0.5f, 0.75f, 0.1f), Vector3(0f, 0.37f, 0f), woodDark)).apply { setPos(Vector3(0f, 1.55f, -2.9f)); roomRoot.addChild(this) }
        clockFace("wall", Vector3(0f, 2.05f, -2.78f), 0.17f)
        roomRoot.addChild(plate("plate_moon", 0.085f).apply { setPos(Vector3(0.34f, 2.05f, -2.8f)) })
        // The safe on the floor to the right, three dials on its door, turned to the chair; it stands
        // clear of the bench, whose corner would otherwise hide the dials.
        val safe = Entity().apply { setPos(Vector3(1.6f, 0f, -1.1f)); transform().eulerAngles = EulerAngles(0f, -50f, 0f) }
        safe.addChild(piece("safe", 0.7f) ?: box(Vector3(0.55f, 0.7f, 0.5f), Vector3(0f, 0.35f, 0f), pbr(color(0.2f, 0.32f, 0.25f)), safe))
        roomRoot.addChild(safe)
        hit(safe, "safe", Vector3(0.6f, 0.75f, 0.55f), Vector3(0f, 0.38f, 0f))
        for (k in 1..3) {
            val d = Entity().apply { setPos(Vector3(-0.15f + (k - 1) * 0.15f, 0.45f, 0.29f)) }
            d.addChild(model(MeshResource.createCylinder(0.03f, 0.055f), brass).apply { transform().eulerAngles = EulerAngles(90f, 0f, 0f) })
            d.addChild(model(MeshResource.createBox(Vector3(0.012f, 0.045f, 0.01f), 0f), dark).apply { setPos(Vector3(0f, 0.028f, 0.02f)) })
            safe.addChild(d)
            hit(d, "dial:safe$k", Vector3(0.13f, 0.13f, 0.08f))
        }
        item(Item.KEY_ATTIC, Vector3(1.6f, 0.74f, -1.1f))
        door("attic", Vector3(2.95f, 1.05f, -0.4f), -90f)
    }

    private fun buildAttic() {
        // Five empty frames on the wall ahead; the photos lie on the trunk to the right.
        for (i in 0..4) {
            val f = Entity().apply { setPos(Vector3(-1.2f + i * 0.6f, 1.5f, -2.92f)) }
            f.addChild(model(MeshResource.createBox(Vector3(0.44f, 0.56f, 0.04f), 0.006f), woodDark))
            f.addChild(model(MeshResource.createBox(Vector3(0.37f, 0.49f, 0.01f), 0f), pbr(color(0.85f, 0.82f, 0.75f))).apply { setPos(Vector3(0f, 0f, 0.02f)) })
            f.addChild(model(MeshResource.createBox(Vector3(0.44f, 0.56f, 0.01f), 0f), slotMat).apply { setName("glow"); setPos(Vector3(0f, 0f, -0.02f)); setScale(1.08f) })
            roomRoot.addChild(f)
            hit(f, "frame$i", Vector3(0.48f, 0.6f, 0.12f))
        }
        box(Vector3(0.9f, 0.45f, 0.5f), Vector3(1.25f, 0.225f, -1.1f), woodMid)
        Shop.PHOTO_ORDER.forEachIndexed { k, p ->
            // Shuffled on the trunk, so the order is the player's to find.
            val slot = listOf(3, 0, 4, 1, 2)[k]
            item(p, Vector3(0.93f + slot * 0.16f, 0.46f, -1.1f), lying = true)
        }
        // The attic's clock on a shelf to the left.
        box(Vector3(0.35f, 0.05f, 0.6f), Vector3(-2.8f, 1.2f, -1.0f), woodMid)
        (piece("mantel", 0.42f) ?: box(Vector3(0.3f, 0.42f, 0.2f), Vector3(0f, 0.21f, 0f), woodDark)).apply {
            setPos(Vector3(-2.82f, 1.23f, -1.0f)); transform().eulerAngles = EulerAngles(0f, 90f, 0f); roomRoot.addChild(this)
        }
        // A plain wooden wall-clock case behind the attic's face, so it hangs on the wall rather than floats.
        roomRoot.addChild(model(MeshResource.createCylinder(0.06f, 0.27f), woodDark).apply {
            setPos(Vector3(-2.66f, 1.9f, -1.0f))
            transform().eulerAngles = EulerAngles(0f, 0f, 90f)
        })
        clockFace("attic", Vector3(-2.6f, 1.9f, -1.0f), 0.2f, yaw = 90f)
        roomRoot.addChild(plate("mark_star", 0.07f).apply { setPos(Vector3(-2.64f, 1.54f, -1.0f)); transform().eulerAngles = EulerAngles(0f, 90f, 0f) })
        // The chest that is only there on that afternoon.
        val chest = box(Vector3(0.6f, 0.4f, 0.4f), Vector3(-1.2f, 0.2f, -1.5f), pbr(color(0.45f, 0.28f, 0.16f)))
        chest.addChild(model(MeshResource.createBox(Vector3(0.62f, 0.06f, 0.42f), 0.01f), pbr(color(0.35f, 0.2f, 0.1f))).apply { setName("lid"); setPos(Vector3(0f, 0.22f, 0f)) })
        hit(chest, "chest", Vector3(0.65f, 0.45f, 0.45f))
        item(Item.KEY_CELLAR, Vector3(-1.2f, 0.5f, -1.3f))
        // The trapdoor down to the cellar, on the floor ahead.
        val trap = box(Vector3(0.8f, 0.03f, 0.6f), Vector3(0.1f, 0.015f, -1.3f), woodDark)
        trap.addChild(model(MeshResource.createTorus(0.05f, 0.04f), brass).apply { setPos(Vector3(0f, 0.02f, 0.2f)) })
        trap.addChild(model(MeshResource.createBox(Vector3(0.86f, 0.01f, 0.66f), 0f), slotMat).apply { setName("glow"); setPos(Vector3(0f, -0.005f, 0f)) })
        hit(trap, "cellar", Vector3(0.85f, 0.1f, 0.65f))
    }

    private fun buildCellar() {
        // The astronomical clock: three rings to set (sun, moon, stars) and the clockwork bird on top.
        (piece("astro", 2.5f) ?: box(Vector3(1.2f, 1.7f, 0.4f), Vector3(0f, 0.85f, 0f), woodDark)).apply { setPos(Vector3(0f, 0f, -2.55f)); roomRoot.addChild(this) }
        // The three plates earned upstairs, on the wall left of the clock: what to set each ring to.
        for ((k, p) in Shop.Plate.values().withIndex()) {
            val e = plate("plate_${p.name.lowercase()}", 0.17f).apply { setPos(Vector3(-1.45f, 1.85f - k * 0.42f, -2.2f)); }
            roomRoot.addChild(e)
            hit(e, "plate:${p.name}", Vector3(0.36f, 0.36f, 0.1f))
        }
        door("go:tower", Vector3(1.55f, 1.05f, -2.4f), 0f)
        val backing = model(MeshResource.createCylinder(0.02f, 0.66f), pbr(color(0.12f, 0.14f, 0.3f))).apply {
            setPos(Vector3(0f, 1.25f, -1.8f)); transform().eulerAngles = EulerAngles(90f, 0f, 0f)
        }
        roomRoot.addChild(backing)
        val ringColors = listOf(color(0.95f, 0.72f, 0.3f), color(0.8f, 0.83f, 0.9f), color(0.45f, 0.55f, 0.95f))
        for (k in 1..3) {
            val ring = Entity().apply { setPos(Vector3(0f, 1.25f, -1.77f + k * 0.012f)) }
            val r = 0.56f - (k - 1) * 0.16f
            ring.addChild(model(MeshResource.createTorus(r, r - 0.045f), pbr(ringColors[k - 1], metallic = 0.7f, roughness = 0.35f)).apply {
                transform().eulerAngles = EulerAngles(90f, 0f, 0f)
            })
            // The marker on each ring (a sun, a moon, a star): it points at the hour the ring is set to.
            ring.addChild(plate(listOf("mark_sun", "mark_moon", "mark_star")[k - 1], 0.045f).apply { setPos(Vector3(0f, r - 0.022f, 0.03f)) })
            roomRoot.addChild(ring)
            hit(ring, "dial:ring$k", Vector3(r * 2, r * 2, 0.05f))
            parts["ringVis$k"] = ring
        }
        // The twelve hours around the outside.
        for (s in 0 until 12) {
            val a = Math.toRadians(s * 30.0)
            roomRoot.addChild(model(MeshResource.createBox(Vector3(0.025f, if (s % 3 == 0) 0.08f else 0.05f, 0.01f), 0f), brass).apply {
                setPos(Vector3((sin(a) * 0.63).toFloat(), (1.25 + cos(a) * 0.63).toFloat(), -1.76f))
                transform().eulerAngles = EulerAngles(0f, 0f, -s * 30f)
            })
        }
        val bird = Entity().apply { setPos(Vector3(0f, 2.0f, -1.9f)) }
        bird.addChild(model(sphere, brass).apply { transform().setScaleVector(Vector3(0.08f, 0.06f, 0.06f)) })
        bird.addChild(model(sphere, brass).apply { setScale(0.04f); setPos(Vector3(0.07f, 0.04f, 0f)) })
        bird.addChild(model(MeshResource.createCone(0.04f, 0.012f), pbr(color(0.9f, 0.5f, 0.2f))).apply { setPos(Vector3(0.11f, 0.04f, 0f)); transform().eulerAngles = EulerAngles(0f, 0f, -90f) })
        bird.addChild(model(MeshResource.createBox(Vector3(0.1f, 0.01f, 0.05f), 0f), brass).apply { setPos(Vector3(-0.08f, 0.02f, 0f)); transform().eulerAngles = EulerAngles(0f, 0f, 25f) })
        roomRoot.addChild(bird)
        parts["bird"] = bird
    }

    private fun buildTower() {
        // Ahead: the great movement on its beams, the tower clock's face above it, the pendulum in the middle.
        (piece("tower_movement", 1.25f) ?: box(Vector3(1.6f, 1.2f, 0.7f), Vector3(0f, 0.6f, 0f), dark)).apply { setPos(Vector3(0f, 0f, -2.15f)); roomRoot.addChild(this) }
        clockFace("tower", Vector3(0f, 2.05f, -2.72f), 0.46f)
        // Wheels that turn once the lever is thrown.
        for ((k, spec) in listOf(Triple(-0.72f, 0.26f, "towerWheelA"), Triple(0.72f, 0.2f, "towerWheelB")).withIndex()) {
            val w = Entity().apply { setPos(Vector3(spec.first, 1.0f + k * 0.08f, -1.72f)) }
            w.addChild((piece("wheel_big", spec.second * 2) ?: gearMesh(spec.second, brass)))
            roomRoot.addChild(w)
            parts[spec.third] = w
        }
        // The pendulum hangs from a beam; its length follows the dial.
        val pivot = Entity().apply { setPos(Vector3(0f, 2.95f, -1.55f)) }
        val rod = model(MeshResource.createCylinder(1f, 0.008f), brass).apply { setPos(Vector3(0f, -0.5f, 0f)) }
        val bob = model(MeshResource.createCylinder(0.03f, 0.09f), brass).apply { transform().eulerAngles = EulerAngles(90f, 0f, 0f) }
        pivot.addChild(rod)
        pivot.addChild(bob)
        roomRoot.addChild(pivot)
        parts["pendulum"] = pivot
        parts["pendRod"] = rod
        parts["pendBob"] = bob
        // The dial that sets its length, the seconds clock to compare it with, and the rule cast in brass.
        val dial = Entity().apply { setPos(Vector3(-0.55f, 1.05f, -1.15f)) }
        dial.addChild(model(MeshResource.createCylinder(0.03f, 0.08f), brass).apply { transform().eulerAngles = EulerAngles(90f, 0f, 0f) })
        dial.addChild(model(MeshResource.createBox(Vector3(0.016f, 0.065f, 0.012f), 0f), dark).apply { setPos(Vector3(0f, 0.034f, 0.02f)) })
        roomRoot.addChild(dial)
        hit(dial, "dial:pend", Vector3(0.2f, 0.2f, 0.1f))
        val seconds = Entity().apply { setPos(Vector3(-1.45f, 1.55f, -2.2f)) }
        seconds.addChild(plate("plate_rule", 0.34f))
        roomRoot.addChild(seconds)
        val hand = Entity().apply { setPos(Vector3(1.4f, 1.6f, -2.2f)) }
        val secFace = Entity().apply { setPos(Vector3(1.4f, 1.6f, -2.21f)) }
        secFace.addChild(model(MeshResource.createCylinder(0.015f, 0.2f), paper).apply { transform().eulerAngles = EulerAngles(90f, 0f, 0f) })
        secFace.addChild(model(MeshResource.createTorus(0.215f, 0.2f), brass).apply { transform().eulerAngles = EulerAngles(90f, 0f, 0f) })
        roomRoot.addChild(secFace)
        hand.addChild(model(MeshResource.createBox(Vector3(0.012f, 0.18f, 0.006f), 0f), dark).apply { setPos(Vector3(0f, 0.08f, 0.02f)) })
        roomRoot.addChild(hand)
        parts["secondHand"] = hand
        // The gear train: two shafts on the bench, the four wheels on the shelf beside them.
        box(Vector3(1.6f, 0.06f, 0.5f), Vector3(0f, 0.86f, -1.1f), woodDark)
        for (k in 1..2) {
            val shaft = Entity().apply { setPos(Vector3(-0.1f + (k - 1) * 0.36f, 1.08f, -1.15f)) }
            shaft.addChild(model(MeshResource.createCylinder(0.34f, 0.012f), dark).apply { setPos(Vector3(0f, -0.06f, -0.02f)) })
            shaft.addChild(model(MeshResource.createTorus(0.11f, 0.1f), slotMat).apply { setName("glow"); transform().eulerAngles = EulerAngles(90f, 0f, 0f) })
            roomRoot.addChild(shaft)
            hit(shaft, "train$k", Vector3(0.26f, 0.26f, 0.12f))
        }
        Shop.RATIOS.forEachIndexed { k, i -> item(i, Vector3(0.45f + (k % 2) * 0.2f, 0.98f + (k / 2) * 0.17f, -1.05f)) }
        // The red clutch lever at the bench's left.
        val lever = Entity().apply { setPos(Vector3(-0.3f, 0.9f, -0.95f)) }
        lever.addChild(model(MeshResource.createCylinder(0.26f, 0.014f), pbr(color(0.7f, 0.12f, 0.08f))).apply { setPos(Vector3(0f, 0.13f, 0f)) })
        lever.addChild(model(sphere, pbr(color(0.85f, 0.15f, 0.1f), roughness = 0.3f)).apply { setScale(0.032f); setPos(Vector3(0f, 0.27f, 0f)) })
        roomRoot.addChild(lever)
        hit(lever, "lever", Vector3(0.14f, 0.34f, 0.14f), Vector3(0f, 0.14f, 0f))
        parts["leverArm"] = lever
        item(Item.KEY_STUDY, Vector3(0.3f, 0.92f, -0.98f))
        door("study", Vector3(-2.95f, 1.05f, -0.8f), 90f)
    }

    private fun buildStudy() {
        (piece("writing_desk", 0.8f) ?: box(Vector3(1.3f, 0.76f, 0.7f), Vector3(0f, 0.38f, 0f), woodMid)).apply { setPos(Vector3(0f, 0f, -1.35f)); roomRoot.addChild(this) }
        // The lamp: dark room until it is pinched.
        val lamp = Entity().apply { setPos(Vector3(0.45f, 0.8f, -1.5f)) }
        lamp.addChild(piece("desklamp", 0.45f) ?: box(Vector3(0.12f, 0.45f, 0.12f), Vector3(0f, 0.22f, 0f), brass, lamp))
        lamp.addChild(model(sphere, lampLight).apply { setName("bulb"); setScale(0.04f); setPos(Vector3(-0.03f, 0.36f, 0.03f)) })
        roomRoot.addChild(lamp)
        hit(lamp, "lamp", Vector3(0.22f, 0.5f, 0.22f), Vector3(0f, 0.22f, 0f))
        parts["studyLamp"] = lamp
        // The newspaper under it.
        val news = box(Vector3(0.42f, 0.004f, 0.3f), Vector3(0.05f, 0.81f, -1.25f), textured("news", glowShare = 0.4f) ?: paper)
        news.transform().eulerAngles = EulerAngles(0f, 6f, 0f)
        hit(news, "news", Vector3(0.44f, 0.06f, 0.32f))
        // The perpetual calendar against the left wall, three dials to set.
        val cal = Entity().apply { setPos(Vector3(-1.5f, 0f, -2.0f)); transform().eulerAngles = EulerAngles(0f, 25f, 0f) }
        cal.addChild(piece("calendar_clock", 2.2f) ?: box(Vector3(0.7f, 2.2f, 0.35f), Vector3(0f, 1.1f, 0f), woodDark, cal))
        for ((k, d) in listOf("month", "day", "week").withIndex()) {
            // On the model's own three brass dials.
            val e = Entity().apply { setPos(Vector3(0f, 1.37f - k * 0.21f, 0.17f)) }
            e.addChild(model(MeshResource.createBox(Vector3(0.016f, 0.07f, 0.01f), 0f), dark).apply { setPos(Vector3(0f, 0.04f, 0.012f)) })
            cal.addChild(e)
            hit(e, "dial:$d", Vector3(0.2f, 0.2f, 0.08f))
            parts["calDial$k"] = e
        }
        roomRoot.addChild(cal)
        // The cabinet on the right, its door tied to the calendar; the finished watch comes out of it.
        val cabinet = Entity().apply { setPos(Vector3(1.55f, 0f, -2.0f)); transform().eulerAngles = EulerAngles(0f, -25f, 0f) }
        cabinet.addChild(piece("cabinet", 2f) ?: box(Vector3(0.9f, 2f, 0.45f), Vector3(0f, 1f, 0f), woodDark, cabinet))
        roomRoot.addChild(cabinet)
        hit(cabinet, "cabinet", Vector3(0.9f, 2f, 0.5f), Vector3(0f, 1f, 0f))
        // A brass rod runs from the calendar along the wall into the cabinet's side: one moves the other.
        box(Vector3(2.5f, 0.016f, 0.016f), Vector3(0.02f, 0.42f, -2.36f), brass)
        // The reward: the finished watch, big and turning in the air above the desk.
        val watch = Entity().apply { setPos(Vector3(0f, 1.42f, -1.3f)); enabled = false }
        watch.addChild(piece("pocketwatch", 0.42f) ?: model(sphere, brass).apply { setScale(0.2f) })
        roomRoot.addChild(watch)
        parts["bigWatch"] = watch
    }

    private fun door(id: String, at: Vector3, yaw: Float) {
        val d = Entity().apply { setPos(at); transform().eulerAngles = EulerAngles(0f, yaw, 0f) }
        d.addChild(model(MeshResource.createBox(Vector3(0.9f, 2.1f, 0.06f), 0.01f), woodDark))
        for (y in listOf(-0.5f, 0.45f)) d.addChild(model(MeshResource.createBox(Vector3(0.7f, 0.75f, 0.02f), 0.01f), woodMid).apply { setPos(Vector3(0f, y, 0.035f)) })
        d.addChild(model(sphere, brass).apply { setScale(0.035f); setPos(Vector3(0.32f, 0f, 0.06f)) })
        d.addChild(model(MeshResource.createBox(Vector3(1.0f, 2.2f, 0.02f), 0f), slotMat).apply { setName("glow"); setPos(Vector3(0f, 0f, -0.02f)); enabled = false })
        roomRoot.addChild(d)
        hit(d, id, Vector3(1f, 2.2f, 0.2f))
    }

    private fun item(i: Item, at: Vector3, lying: Boolean = false) {
        val e = itemLook(i).apply {
            setPos(at)
            if (lying) transform().eulerAngles = EulerAngles(-90f, 0f, 0f)
        }
        roomRoot.addChild(e)
        hit(e, "take:${i.name}", if (lying) Vector3(0.18f, 0.24f, 0.06f) else Vector3(0.18f, 0.12f, 0.18f))
    }

    // ---- The hand tray ----

    private var selectedItem: Item? = null

    /** The tray floats low in front of the player: what is carried, to pinch (select) and then use. */
    private fun syncTray(s: Shop, selected: Item?) {
        val carried = s.tray.toList()
        for (i in trayTokens.keys.filter { it !in carried }) {
            val e = trayTokens.remove(i)!!
            ringOf.remove(i)
            hits.remove(e)
            trayRoot.removeChild(e)
        }
        carried.forEachIndexed { k, i ->
            val e = trayTokens.getOrPut(i) {
                Entity().also { holder ->
                    holder.addChild(itemLook(i, trayBrass).apply {
                        if (i in Shop.PHOTO_ORDER) { setScale(0.7f); transform().eulerAngles = EulerAngles(-35f, 0f, 0f) }
                        if (i in Shop.GEARS) transform().eulerAngles = EulerAngles(60f, 0f, 0f)
                    })
                    trayRoot.addChild(holder)
                    hits[holder] = "tray:${i.name}"
                    holder.components.set(CollisionComponent(listOf(ShapeResource.createBox(Vector3(0.12f, 0.15f, 0.1f))), PhysicsMaterialResource()))
                    holder.components.set(InteractableComponent())
                    holder.components.set(HoverEffectComponent())
                    lighting.receive(holder)
                }
            }
            val n = carried.size
            e.setPos(Vector3((-0.065 * (n - 1) + k * 0.13).toFloat(), TRAY_Y, TRAY_Z))
        }
        for ((i, e) in trayTokens) {
            val ring = ringOf.getOrPut(i) { model(MeshResource.createTorus(0.065f, 0.058f), selectedMat).also { e.addChild(it); it.setPos(Vector3(0f, -0.06f, 0f)) } }
            ring.enabled = i == selected
            if (i != selected) e.transform().setScaleVector(Vector3(1f))
        }
        selectedItem = selected
    }

    /** Turns the tray (about the player) to [yaw] degrees, so it stays in front as the player turns. */
    fun trayYaw(yaw: Float) {
        trayRoot.transform().eulerAngles = EulerAngles(0f, yaw, 0f)
    }

    /** The middle of the tray (room frame). */
    fun traySpot(): Vec3 {
        val p = trayRoot.convertPositionTo(Vector3(0f, TRAY_Y, TRAY_Z), root)
        return Vec3(p.x.toDouble(), p.y.toDouble(), p.z.toDouble())
    }

    // ---- Keeping the room in line with the shop ----

    /** The light the room is in now. */
    fun moodOf(s: Shop) = when (s.room) {
        Room.FRONT -> if (s.sunlit) Mood.SUNLIT else Mood.DAY
        Room.WORKSHOP -> if (s.night) Mood.NIGHT else Mood.DAY
        Room.ATTIC -> if (s.thatDay) Mood.THAT_DAY else Mood.DAY
        Room.CELLAR, Room.TOWER -> Mood.CELLAR
        Room.STUDY -> if (s.lampOn) Mood.DAY else Mood.DARK
    }

    fun sync(s: Shop, selected: Item?) {
        build(s.room, moodOf(s))
        // Hands of every clock in this room.
        for (id in hands.keys) {
            val (h, m) = s.clocks[id] ?: continue
            showHands(id, h, m)
        }
        // Things to take: shown while they can be taken and are not yet in hand.
        for (i in Item.entries) parts["take:${i.name}"]?.let { it.enabled = s.visible(i) && i !in s.taken }
        when (s.room) {
            Room.FRONT -> {
                parts["sun"]?.enabled = s.sunlit
                parts["words"]?.enabled = s.sunlit
                s.clocks["big"]?.let { (h, m) -> placeSunPatch(h, m) }
                val turned = "mantelTurned" in s.opened
                mantelTarget = if (turned) 180f else MANTEL_ASKEW
                parts["mantelDoor"]?.let { d ->
                    val open = "mantel" in s.opened
                    d.setPos(Vector3(if (open) 0.1f else 0f, 0.2f, if (open) -0.17f else -0.11f))
                    d.transform().eulerAngles = EulerAngles(0f, if (open) 70f else 0f, 0f)
                }
                parts["drawer"]?.setPos(Vector3(0.6f, 0.8f, if ("drawer" in s.opened) -0.98f else -1.18f))
                glowOf(parts["go:workshop"], 1 in s.parts)
            }
            Room.WORKSHOP -> {
                parts["lamp"]?.enabled = s.night
                parts["pool"]?.enabled = s.night
                parts["sketch"]?.enabled = s.night
                parts["toolboxLid"]?.let { l ->
                    val open = "toolbox" in s.opened
                    l.setPos(Vector3(-0.75f, if (open) 1.2f else 1.115f, if (open) -1.12f else -1.0f))
                    l.transform().eulerAngles = EulerAngles(if (open) -75f else 0f, 0f, 0f)
                }
                val gearInHand = s.tray.any { it in Shop.GEARS }
                for (k in 1..3) {
                    val seat = parts["gear$k"] ?: continue
                    val g = s.gears[k]
                    if (g != null && seat.getChildren().none { it.getName() == "seated" }) {
                        seat.addChild(gearMesh(GEAR_R[g]!!, brass).apply { setName("seated") }.also { lighting.receive(it) })
                    }
                    // An empty seat glows while a gear is in hand.
                    glowOf(seat, g == null && gearInHand, "ring")
                }
                for (k in 1..3) parts["dial:safe$k"]?.transform()?.eulerAngles = EulerAngles(0f, 0f, -(s.dials["safe$k"] ?: 0) * 36f)
                glowOf(parts["attic"], Item.KEY_ATTIC in s.tray)
            }
            Room.ATTIC -> {
                parts["chest"]?.let { c ->
                    c.enabled = s.thatDay || "chest" in s.opened
                    c.getChildren().firstOrNull { it.getName() == "lid" }?.let { lid ->
                        val open = "chest" in s.opened
                        lid.setPos(Vector3(0f, if (open) 0.3f else 0.22f, if (open) -0.22f else 0f))
                        lid.transform().eulerAngles = EulerAngles(if (open) -70f else 0f, 0f, 0f)
                    }
                }
                for (i in 0..4) {
                    val f = parts["frame$i"] ?: continue
                    val p = s.frames[i]
                    val shown = f.getChildren().firstOrNull { it.getName().startsWith("photo:") }
                    if (shown != null && shown.getName() != "photo:${p?.name}") f.removeChild(shown)
                    if (p != null && f.getChildren().none { it.getName() == "photo:${p.name}" }) {
                        f.addChild(photoMesh(p).apply { setName("photo:${p.name}"); setPos(Vector3(0f, 0f, 0.03f)); transform().setScaleVector(Vector3(2.4f, 2.4f, 1f)) }.also { lighting.receive(it) })
                    }
                    // An empty frame glows while a photo is in hand.
                    glowOf(f, p == null && s.tray.any { it in Shop.PHOTO_ORDER })
                }
                glowOf(parts["cellar"], Item.KEY_CELLAR in s.tray)
            }
            Room.CELLAR -> {
                for (k in 1..3) parts["ringVis$k"]?.transform()?.eulerAngles = EulerAngles(0f, 0f, -(s.dials["ring$k"] ?: 0) * 30f)
                glowOf(parts["go:tower"], 4 in s.parts)
                parts["go:tower"]?.enabled = 4 in s.parts
            }
            Room.TOWER -> {
                val len = Shop.PENDULUM_LENGTH[s.pendulum]
                parts["pendRod"]?.let { it.setPos(Vector3(0f, -len / 2, 0f)); it.transform().setScaleVector(Vector3(1f, len, 1f)) }
                parts["pendBob"]?.setPos(Vector3(0f, -len, 0f))
                parts["dial:pend"]?.transform()?.eulerAngles = EulerAngles(0f, 0f, -(s.pendulum) * 120f)
                for (k in 1..2) {
                    val shaft = parts["train$k"] ?: continue
                    val w = s.train[k]
                    val shown = shaft.getChildren().firstOrNull { it.getName().startsWith("wheel:") }
                    if (shown != null && shown.getName() != "wheel:${w?.name}") shaft.removeChild(shown)
                    if (w != null && shaft.getChildren().none { it.getName() == "wheel:${w.name}" }) {
                        shaft.addChild(ratioWheel(w, brass).apply { setName("wheel:${w.name}") }.also { lighting.receive(it) })
                    }
                    glowOf(shaft, w == null && s.tray.any { it in Shop.RATIOS })
                }
                parts["leverArm"]?.transform()?.eulerAngles = EulerAngles(if (s.towerRuns) 35f else -20f, 0f, 0f)
                glowOf(parts["study"], Item.KEY_STUDY in s.tray)
            }
            Room.STUDY -> {
                parts["studyLamp"]?.getChildren()?.firstOrNull { it.getName() == "bulb" }?.enabled = s.lampOn
                for ((k, d) in listOf("month" to 30f, "day" to 11.61f, "week" to 51.4f).withIndex()) {
                    parts["calDial$k"]?.transform()?.eulerAngles = EulerAngles(0f, 0f, -(s.dials[d.first] ?: 0) * d.second)
                }
                parts["bigWatch"]?.enabled = s.finished
            }
        }
        syncTray(s, selected)
    }

    private fun glowOf(e: Entity?, on: Boolean, name: String = "glow") {
        e?.getChildren()?.firstOrNull { it.getName() == name }?.let { if (it.enabled != on) it.enabled = on }
    }

    /** Shows what a move did: sparkles where something opened, appeared or fell into place. */
    fun on(ev: Shop.Event) {
        when (ev) {
            is Shop.Event.Opened -> spotOf(ev.prop)?.let { sparkle.emit(it, 24, 0.4, 0.9, 0.012f) }
            is Shop.Event.Placed -> spotOf(ev.at)?.let { sparkle.emit(it, 30, 0.4, 1.0, 0.012f) }
            is Shop.Event.Part -> sparkle.emit(traySpot() + Vec3(0.0, 0.15, 0.0), 60, 0.6, 1.4, 0.014f)
            is Shop.Event.Shifted -> dust.emit(Vec3(0.0, 1.5, -1.5), 80, 0.5, 3.0, 0.01f, drift = Vec3(0.0, 0.05, 0.0))
            Shop.Event.Finished -> sparkle.emit(Vec3(0.0, 1.42, -1.3), 140, 1.0, 2.2, 0.016f)
            Shop.Event.TowerRuns -> spotOf("clock:tower")?.let { sparkle.emit(it, 50, 0.6, 1.2, 0.012f) }
            else -> Unit
        }
    }

    private var mantelTarget = 0f
    private var mantelYaw = MANTEL_ASKEW

    fun frame(s: Shop, dt: Double) {
        clock += dt
        if (s.room == Room.FRONT) parts["mantel"]?.let { m ->
            if (mantelYaw != mantelTarget) {
                mantelYaw = (mantelYaw + (if (mantelTarget > mantelYaw) 1 else -1) * (dt * 140).toFloat()).let { if (kotlin.math.abs(it - mantelTarget) < 3f) mantelTarget else it }
                m.transform().eulerAngles = EulerAngles(0f, mantelYaw, 0f)
            }
        }
        if (s.room == Room.TOWER) {
            val period = s.pendulumSeconds
            parts["pendulum"]?.transform()?.eulerAngles = EulerAngles(0f, 0f, (sin(clock * 2 * Math.PI / period) * 7).toFloat())
            parts["secondHand"]?.transform()?.eulerAngles = EulerAngles(0f, 0f, -(clock.toInt() % 60) * 6f)
            if (s.towerRuns) {
                parts["towerWheelA"]?.transform()?.eulerAngles = EulerAngles(0f, 0f, (clock * 14).toFloat())
                parts["towerWheelB"]?.transform()?.eulerAngles = EulerAngles(0f, 0f, (-clock * 34).toFloat())
            }
        }
        if (s.room == Room.STUDY && s.finished) parts["bigWatch"]?.let { w ->
            w.transform().eulerAngles = EulerAngles(12f, (clock * 28).toFloat(), 0f)
            if ((clock * 3).toInt() != ((clock - dt) * 3).toInt()) sparkle.emit(Vec3(0.0, 1.42, -1.3), 8, 0.45, 1.3, 0.012f)
        }
        sparkle.update(dt, 1.0)
        dust.update(dt, 0.3)
        // The clockwork bird sings once its ring is set.
        if (4 in s.parts && s.room == Room.CELLAR) parts["bird"]?.let { b ->
            b.transform().eulerAngles = EulerAngles(0f, (sin(clock * 2) * 30).toFloat(), (sin(clock * 9) * 8).toFloat())
            if ((clock * 2).toInt() != ((clock - dt) * 2).toInt()) sparkle.emit(Vec3(0.0, 2.1, -1.9), 6, 0.3, 1.2, 0.01f)
        }
        // The seated gears turn while the music box plays.
        if (s.musicPlays) for (k in 1..3) parts["gear$k"]?.getChildren()?.firstOrNull { it.getName() == "seated" }?.let { g ->
            g.transform().eulerAngles = EulerAngles(0f, (clock * 40 * (if (k % 2 == 0) -1 else 1) * k).toFloat(), 0f)
        }
        // Dust in the air of an old shop.
        if ((clock * 1.5).toInt() != ((clock - dt) * 1.5).toInt()) dust.emit(Vec3(sin(clock) * 1.5, 1.6, -1.8 + cos(clock * 0.7)), 2, 0.05, 6.0, 0.006f, drift = Vec3(0.0, -0.02, 0.0))
        // The selected tray item breathes, so the player sees what is in hand.
        selectedItem?.let { i -> trayTokens[i]?.transform()?.setScaleVector(Vector3(1f + 0.08f * sin(clock * 5).toFloat())) }
    }

    /** The sun's patch on the floor follows the big clock, along the numerals, reaching the display case at three. */
    private fun placeSunPatch(h: Int, m: Int) {
        val patch = parts["sunPatch"] ?: return
        val hour = h + m / 60f
        val daytime = hour in 7f..18f
        patch.enabled = daytime
        if (!daytime) return
        val t = ((hour - 9f) / 6f)
        patch.setPos(Vector3(-2.55f + 1.1f * t, 0.008f, -0.66f - 0.64f * t))
    }

    /** Which way clock [c]'s face is turned (degrees about y, in the room). */
    fun faceYaw(c: String) = if (c == "attic") 90f else 0f

    /** Shows [h]:[m] on clock [c] while its hands are being turned (the move comes on release). */
    fun showHands(c: String, h: Int, m: Int) {
        if (c == "big") placeSunPatch(h, m)
        val (hour, minute) = hands[c] ?: return
        hour.transform().eulerAngles = EulerAngles(0f, 0f, -((h % 12) * 30f + m * 0.5f))
        minute.transform().eulerAngles = EulerAngles(0f, 0f, -m * 6f)
    }

    /** The clock that rules this room (its ticking comes from there). */
    fun mainClock(s: Shop): Vec3 = when (s.room) {
        Room.FRONT -> if ("mantelTurned" in s.opened) spotOf("clock:big") else spotOf("mantel")
        Room.WORKSHOP -> spotOf("clock:wall")
        Room.ATTIC -> spotOf("clock:attic")
        Room.CELLAR -> spotOf("dial:ring1")
        Room.TOWER -> spotOf("clock:tower")
        Room.STUDY -> spotOf("cabinet")
    } ?: Vec3(0.0, 1.5, -2.0)

    companion object {
        const val MIN_HIT = 0.14f
        const val MANTEL_ASKEW = 32f
        const val TRAY_Y = 0.86f
        const val TRAY_Z = -0.46f
        val GEAR_R = mapOf(Item.GEAR_L to 0.075f, Item.GEAR_M to 0.055f, Item.GEAR_S to 0.038f)

        /** Clocks hung on the walls for the look of the shop: front wall either side, and both side walls. */
        val DECOR_CLOCKS = listOf(
            Vector3(-2.3f, 2.15f, -2.95f) to 0f, Vector3(-1.55f, 2.35f, -2.95f) to 0f, Vector3(1.55f, 2.35f, -2.95f) to 0f, Vector3(2.3f, 2.15f, -2.95f) to 0f,
            Vector3(-2.95f, 2.25f, -2.1f) to 90f, Vector3(-2.95f, 1.75f, 0.3f) to 90f, Vector3(2.95f, 2.3f, -2.3f) to -90f, Vector3(2.95f, 2.2f, 0.8f) to -90f,
        )
    }
}
