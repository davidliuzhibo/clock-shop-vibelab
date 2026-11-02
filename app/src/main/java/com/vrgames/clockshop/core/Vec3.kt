package com.vrgames.clockshop.core

import kotlin.math.sqrt

/** Immutable 3D vector (meters, +y up, -z away from the player). */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(x * x + y * y + z * z)
    fun distanceTo(o: Vec3): Double {
        val dx = x - o.x
        val dy = y - o.y
        val dz = z - o.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun normalized(): Vec3 {
        val l = length()
        return if (l < 1e-12) ZERO else this * (1.0 / l)
    }

    /** Returns this vector shortened to [max] if it is longer. */
    fun clampLength(max: Double): Vec3 {
        val l = length()
        return if (l <= max) this else this * (max / l)
    }

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
        val UP = Vec3(0.0, 1.0, 0.0)
    }
}

fun v(x: Double, y: Double, z: Double) = Vec3(x, y, z)
