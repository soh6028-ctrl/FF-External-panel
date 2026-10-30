package ai.venice.ffaim

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect

class Prefs(ctx: Context) {

    private val sp: SharedPreferences = ctx.getSharedPreferences("ffaim", Context.MODE_PRIVATE)

    @Volatile var aimbot: Boolean = sp.getBoolean("aimbot", false)
        set(v) { field = v; sp.edit().putBoolean("aimbot", v).apply() }

    @Volatile var headshot: Boolean = sp.getBoolean("headshot", true)
        set(v) { field = v; sp.edit().putBoolean("headshot", v).apply() }

    @Volatile var headshotPct: Int = sp.getInt("headshot_pct", 100).coerceIn(10, 100)
        set(v) { val c = v.coerceIn(10, 100); field = c; sp.edit().putInt("headshot_pct", c).apply() }

    @Volatile var fovRadius: Int = sp.getInt("fov_r", 180).coerceIn(90, 340)
        set(v) { val c = v.coerceIn(90, 340); field = c; sp.edit().putInt("fov_r", c).apply() }

    @Volatile var fovOffX: Int = sp.getInt("fov_x", 0).coerceIn(-300, 300)
        set(v) { field = v; sp.edit().putInt("fov_x", v).apply() }

    @Volatile var fovOffY: Int = sp.getInt("fov_y", 0).coerceIn(-300, 300)
        set(v) { field = v; sp.edit().putInt("fov_y", v).apply() }

    @Volatile var debug: Boolean = sp.getBoolean("debug", false)
        set(v) { field = v; sp.edit().putBoolean("debug", v).apply() }

    @Volatile var ring: Boolean = sp.getBoolean("ring", true)
        set(v) { field = v; sp.edit().putBoolean("ring", v).apply() }

    @Volatile var learningMode: Boolean = sp.getBoolean("learn_mode", true)
        set(v) { field = v; sp.edit().putBoolean("learn_mode", v).apply() }

    @Volatile var trained: Boolean = sp.getBoolean("trained", false)
        set(v) { field = v; sp.edit().putBoolean("trained", v).apply() }

    @Volatile var backend: Int = sp.getInt("backend", 0)
        set(v) { field = v; sp.edit().putInt("backend", v).apply() }

    @Volatile var panelX: Int = sp.getInt("panel_x", 40)
        set(v) { field = v; sp.edit().putInt("panel_x", v).apply() }

    @Volatile var panelY: Int = sp.getInt("panel_y", 60)
        set(v) { field = v; sp.edit().putInt("panel_y", v).apply() }

    @Volatile var bubbleX: Int = sp.getInt("bubble_x", 40)
        set(v) { field = v; sp.edit().putInt("bubble_x", v).apply() }

    @Volatile var bubbleY: Int = sp.getInt("bubble_y", 60)
        set(v) { field = v; sp.edit().putInt("bubble_y", v).apply() }

    fun selfZone(): Rect {
        val d = defaultSelfZone()
        return Rect(
            sp.getInt("self_l", d.left),
            sp.getInt("self_t", d.top),
            sp.getInt("self_r", d.right),
            sp.getInt("self_b", d.bottom)
        )
    }

    fun setSelfZone(r: Rect) {
        sp.edit().putInt("self_l", r.left).putInt("self_t", r.top)
            .putInt("self_r", r.right).putInt("self_b", r.bottom).apply()
    }

    fun defaultSelfZone(): Rect {
        val w = Geometry.screenW
        val h = Geometry.screenH
        return Rect((w * 0.34f).toInt(), (h * 0.36f).toInt(), (w * 0.58f).toInt(), (h * 0.62f).toInt())
    }

    fun resetSelfZone() {
        sp.edit().remove("self_l").remove("self_t").remove("self_r").remove("self_b").apply()
    }

    fun ammoBox(): Rect {
        val d = defaultAmmoBox()
        return Rect(
            sp.getInt("ammo_l", d.left),
            sp.getInt("ammo_t", d.top),
            sp.getInt("ammo_r", d.right),
            sp.getInt("ammo_b", d.bottom)
        )
    }

    fun setAmmoBox(r: Rect) {
        sp.edit().putInt("ammo_l", r.left).putInt("ammo_t", r.top)
            .putInt("ammo_r", r.right).putInt("ammo_b", r.bottom).apply()
    }

    fun defaultAmmoBox(): Rect {
        val w = Geometry.screenW
        val h = Geometry.screenH
        return Rect((w * 0.78f).toInt(), (h * 0.02f).toInt(), (w * 0.985f).toInt(), (h * 0.17f).toInt())
    }

    fun resetAmmoBox() {
        sp.edit().remove("ammo_l").remove("ammo_t").remove("ammo_r").remove("ammo_b").apply()
    }

    fun resetFov() {
        fovRadius = 180
        fovOffX = 0
        fovOffY = 0
    }
}
