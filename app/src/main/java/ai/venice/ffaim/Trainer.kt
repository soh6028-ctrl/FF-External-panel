package ai.venice.ffaim

import android.content.Context
import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Passive observer. During a recording it keeps a capped set of geometric feature
 * samples, classifies each one, and at training time turns them into thresholds.
 */
class Trainer(private val ctx: Context) {

    private companion object {
        const val MAX_SAMPLES = 500
    }

    var recording = false
        private set

    private val samples = ArrayList<FloatArray>(MAX_SAMPLES)
    private val labels = ArrayList<Int>(MAX_SAMPLES)
    private val selfUnion = Rect()

    fun start() {
        samples.clear()
        labels.clear()
        selfUnion.setEmpty()
        recording = true
    }

    fun stop() {
        recording = false
    }

    fun addCount(): Int = samples.size

    fun save(): Boolean {
        if (samples.isEmpty()) return false
        val o = JSONObject()
        val arr = JSONArray()
        for (i in samples.indices) {
            val s = samples[i]
            val e = JSONObject()
            e.put("h", s[0].toDouble())
            e.put("a", s[1].toDouble())
            e.put("f", s[2].toDouble())
            e.put("g", s[3].toDouble())
            e.put("b", s[4].toDouble())
            e.put("r", s[5].toDouble())
            e.put("lab", labels[i])
            arr.put(e)
        }
        o.put("s", arr)
        o.put("sl", selfUnion.left)
        o.put("st", selfUnion.top)
        o.put("sr", selfUnion.right)
        o.put("sb", selfUnion.bottom)
        val f = File(ModelStore.sessionsDir(ctx), "s_${System.currentTimeMillis()}.json")
        return try {
            f.writeText(o.toString())
            true
        } catch (t: Throwable) {
            Logx.e("session save failed", t)
            false
        }
    }

    fun sample(det: Detector, count: Int) {
        if (!recording) return
        val zone = det.model.selfZone
        for (i in 0 until count) {
            if (samples.size >= MAX_SAMPLES) return
            val d = det.items[i]
            val big = d.height > Geometry.screenH * 0.20f
            val inSelf = zone.size == 4 &&
                d.centreX >= zone[0] && d.centreX <= zone[2] &&
                d.centreY >= zone[1] && d.centreY <= zone[3]
            val label = if (big && inSelf) 2 else if (d.gapRatio < 0.05f) 0 else 1
            val meanR = if (d.area > 0) (d.sumR / d.area).toFloat() else 0f
            val headBias = (d.headY - d.top) / maxOf(1f, d.bottom - d.top)
            samples.add(
                floatArrayOf(
                    d.height / 2f,
                    d.aspect,
                    d.fill,
                    d.gapRatio,
                    headBias.coerceIn(0f, 1f),
                    meanR
                )
            )
            labels.add(label)
            if (label == 2) {
                val l = d.left.toInt()
                val t = d.top.toInt()
                val r = d.right.toInt()
                val b = d.bottom.toInt()
                if (selfUnion.isEmpty) selfUnion.set(l, t, r, b)
                else selfUnion.union(l, t, r, b)
            }
        }
    }

    private fun pick(list: List<Float>, frac: Float): Float {
        if (list.isEmpty()) return 0f
        val i = ((list.size - 1) * frac).toInt().coerceIn(0, list.size - 1)
        return list[i]
    }

    fun build(): AimModel? {
        val enemies = ArrayList<FloatArray>()
        for (i in samples.indices) if (labels[i] == 0) enemies.add(samples[i])
        if (enemies.size < 12) return null

        val heights = enemies.map { it[0] }.sorted()
        val aspects = enemies.map { it[1] }.sorted()
        val fills = enemies.map { it[2] }.sorted()
        val gaps = enemies.map { it[3] }.sorted()
        val biases = enemies.map { it[4] }.filter { it > 0.02f }.sorted()
        val reds = enemies.map { it[5] }.sorted()

        val minHeight = (pick(heights, 0.10f) * 0.75f).toInt().coerceIn(8, 60)
        val minAspect = (pick(aspects, 0.10f) * 0.85f).coerceIn(1.15f, 2.6f)
        val maxFill = (pick(fills, 0.95f) * 1.15f).coerceIn(0.35f, 0.85f)
        val maxGap = (pick(gaps, 0.90f) * 1.4f).coerceIn(0.12f, 0.55f)
        val bias = if (biases.isEmpty()) 0.60f else biases[biases.size / 2].coerceIn(0.35f, 0.85f)
        val meanRed = if (reds.isEmpty()) 160f else reds[reds.size / 2]
        val minR = (meanRed * 0.62f).toInt().coerceIn(70, 180)

        val z = if (selfUnion.isEmpty) IntArray(0)
        else intArrayOf(
            selfUnion.left.coerceAtLeast(0),
            selfUnion.top.coerceAtLeast(0),
            selfUnion.right.coerceAtMost(Geometry.screenW),
            selfUnion.bottom.coerceAtMost(Geometry.screenH)
        )

        val m = AimModel(
            minR = minR,
            redOverGreen = 38,
            redOverBlue = 48,
            greenMin = 112,
            greenOverRed = 32,
            greenOverBlue = 28,
            brightMin = 186,
            minHeight = minHeight,
            minAspect = minAspect,
            maxFill = maxFill,
            maxGapRatio = maxGap,
            headBand = 0.16f,
            headBias = bias,
            selfZone = z
        )
        Logx.d(String.format(Locale.US, "model built from %d samples", enemies.size))
        return m
    }
}
