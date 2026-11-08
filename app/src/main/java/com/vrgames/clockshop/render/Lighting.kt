package com.vrgames.clockshop.render

import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.EnvironmentLightingSettingsComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ImageBasedLightComponent
import com.pico.spatial.core.ecs.ImageBasedLightReceiverComponent
import com.pico.spatial.core.ecs.ImageBasedLightSource
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.TextureCreateOption
import com.pico.spatial.core.ecs.resource.TextureEncoding
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.math.Color4

/**
 * The light of the hour a room is turned to: the image-based light (made from the shop panorama by
 * tools/ibl_source.py and Filament cmgen, at three strengths), the lamp-like directional light, and
 * how much the code-built shapes glow by themselves ([ambient]).
 */
enum class Mood(val ibl: String, val ambient: Float, val lux: Float, val light: Color4) {
    DAY("textures/ibl_day.ktx", 0.4f, 900f, Color4(1f, 0.95f, 0.85f, 1f)),
    /** Three in the afternoon in the front shop: the sun comes in. */
    SUNLIT("textures/ibl_day.ktx", 0.5f, 1700f, Color4(1f, 0.88f, 0.65f, 1f)),
    /** Ten at night in the workshop: only the desk lamp. */
    NIGHT("textures/ibl_night.ktx", 0.12f, 90f, Color4(0.6f, 0.68f, 1f, 1f)),
    /** The attic on 12 March 1936, four in the afternoon: warm and a little faded. */
    THAT_DAY("textures/ibl_old.ktx", 0.46f, 1200f, Color4(1f, 0.85f, 0.65f, 1f)),
    CELLAR("textures/ibl_night.ktx", 0.24f, 350f, Color4(0.9f, 0.8f, 0.65f, 1f)),
    /** The study before its lamp is lit. */
    DARK("textures/ibl_night.ktx", 0.08f, 40f, Color4(0.6f, 0.65f, 0.9f, 1f)),
}

/**
 * Image-based light for the shop. Full immersion has no room light, so without it the PBR models
 * are near black; each model opts in with a receiver component ([receive] walks a subtree).
 */
class Lighting(val sun: Entity) {
    val source = Entity()
    private val textures = HashMap<String, TextureResource>()

    /** Loads every mood's IBL (call off the main thread); a missing one is skipped (logged). */
    fun load() {
        for (m in Mood.entries) {
            if (m.ibl in textures) continue
            runCatching {
                TextureResource.load(m.ibl, LoadType.FROM_ASSETS, TextureCreateOption().apply { textureEncoding = TextureEncoding.LINEAR }).shared()
            }.onSuccess { textures[m.ibl] = it }.onFailure { android.util.Log.w("ClockLight", "${m.ibl}: ${it.message}") }
        }
    }

    fun use(m: Mood) {
        sun.components[DirectionalLightComponent::class.java]?.apply {
            color = m.light
            intensity = m.lux
        }
        val tex = textures[m.ibl] ?: return
        val c = source.components[ImageBasedLightComponent::class.java]
        if (c == null) source.components.set(ImageBasedLightComponent(ImageBasedLightSource.Single(tex), EXPONENT))
        else c.source = ImageBasedLightSource.Single(tex)
    }

    /** Lights every model under [e] with the shop's IBL. */
    fun receive(e: Entity) {
        if (source.components[ImageBasedLightComponent::class.java] == null) return
        if (e.components[ModelComponent::class.java] != null && e.components[ImageBasedLightReceiverComponent::class.java] == null) {
            e.components.set(ImageBasedLightReceiverComponent(source))
            e.components.set(EnvironmentLightingSettingsComponent(0f))
        }
        e.getChildren().forEach(::receive)
    }

    companion object {
        const val EXPONENT = 9f
    }
}
