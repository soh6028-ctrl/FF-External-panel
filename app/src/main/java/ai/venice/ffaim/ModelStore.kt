package ai.venice.ffaim

import android.content.Context
import java.io.File

object ModelStore {

    private fun modelFile(ctx: Context) = File(ctx.filesDir, "model.json")
    fun sessionsDir(ctx: Context) = File(ctx.filesDir, "sessions").also { it.mkdirs() }

    fun exists(ctx: Context): Boolean = modelFile(ctx).exists()

    fun save(ctx: Context, m: AimModel) {
        modelFile(ctx).writeText(m.toJson())
    }

    fun load(ctx: Context): AimModel? {
        val f = modelFile(ctx)
        if (!f.exists()) return null
        return try {
            AimModel.fromJson(f.readText())
        } catch (t: Throwable) {
            null
        }
    }

    fun archive(ctx: Context) {
        val f = modelFile(ctx)
        if (f.exists()) {
            val dst = File(ctx.filesDir, "model_${System.currentTimeMillis()}.json")
            f.copyTo(dst, overwrite = true)
            f.delete()
        }
        val d = sessionsDir(ctx)
        d.listFiles()?.forEach { it.delete() }
    }

    fun sessionCount(ctx: Context): Int =
        sessionsDir(ctx).listFiles()?.count { it.name.endsWith(".json") } ?: 0
}
