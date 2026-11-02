package com.vrgames.clockshop.render

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshInstancesResource
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Transform
import com.pico.spatial.core.math.Vector3
import com.vrgames.clockshop.core.Vec3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Short-lived glowing particles (sparks, exhaust) drawn as one instanced mesh. Particles shrink
 * to nothing over their life. Slots are reused round-robin, so emitting never allocates; when the
 * pool is full the oldest particle is recycled. Instance updates only happen while particles live.
 */
class Sparks(private val name: String, private val capacity: Int, mesh: MeshResource, material: Material) {
    val entity: Entity = ModelEntity(mesh, material)
    private val instances = MeshInstancesResource.create(
        name,
        (0 until capacity).map { MeshInstancesResource.Instance("$name$it", HIDDEN) },
    )
    private val px = DoubleArray(capacity)
    private val py = DoubleArray(capacity)
    private val pz = DoubleArray(capacity)
    private val vx = DoubleArray(capacity)
    private val vy = DoubleArray(capacity)
    private val vz = DoubleArray(capacity)
    private val age = DoubleArray(capacity) { -1.0 }
    private val life = DoubleArray(capacity)
    private val size = FloatArray(capacity)
    private var next = 0
    private var seed = 1
    private var live = 0

    init {
        entity.components[ModelComponent::class.java]!!.meshInstances = instances
    }

    private fun rnd(): Double {
        seed = seed * 1103515245 + 12345
        return ((seed ushr 8) and 0xFFFF) / 65536.0
    }

    /**
     * Emits [count] particles at [at] with speeds up to [speed] m/s in random directions, plus
     * [drift] (e.g. upward, or the opposite of the probe's motion).
     */
    fun emit(at: Vec3, count: Int, speed: Double, lifeS: Double, sizeM: Float, drift: Vec3 = Vec3.ZERO) {
        repeat(count) {
            val i = next
            next = (next + 1) % capacity
            if (age[i] < 0) live++
            // Uniform direction on a sphere, speed biased toward the maximum for a crisp shell.
            val u = rnd() * 2 - 1
            val t = rnd() * Math.PI * 2
            val r = sqrt(1 - u * u)
            val s = speed * (0.45 + 0.55 * rnd())
            px[i] = at.x; py[i] = at.y; pz[i] = at.z
            vx[i] = r * cos(t) * s + drift.x
            vy[i] = u * s + drift.y
            vz[i] = r * sin(t) * s + drift.z
            age[i] = 0.0
            life[i] = lifeS * (0.6 + 0.4 * rnd())
            size[i] = sizeM * (0.6 + 0.4 * rnd()).toFloat()
        }
    }

    /** Advances and redraws live particles. [drag] slows them (per second, 0..1). */
    fun update(dt: Double, drag: Double = 1.6) {
        if (live == 0) return
        val k = (1 - drag * dt).coerceAtLeast(0.0)
        for (i in 0 until capacity) {
            if (age[i] < 0) continue
            age[i] += dt
            if (age[i] >= life[i]) {
                age[i] = -1.0
                live--
                instances.update("$name$i", HIDDEN)
                continue
            }
            vx[i] *= k; vy[i] *= k; vz[i] *= k
            px[i] += vx[i] * dt; py[i] += vy[i] * dt; pz[i] += vz[i] * dt
            val s = size[i] * (1 - age[i] / life[i]).toFloat()
            instances.update(
                "$name$i",
                Transform(Vector3(px[i].toFloat(), py[i].toFloat(), pz[i].toFloat()), NO_ROTATION, Vector3(s)),
            )
        }
    }

    fun clear() {
        for (i in 0 until capacity) if (age[i] >= 0) { age[i] = -1.0; instances.update("$name$i", HIDDEN) }
        live = 0
    }

    private companion object {
        val NO_ROTATION = EulerAngles(0f, 0f, 0f)
        val HIDDEN = Transform(Vector3(0f, -100f, 0f), NO_ROTATION, Vector3(0f))
    }
}
