package ai.venice.ffaim

import org.json.JSONArray
import org.json.JSONObject

data class AimModel(
    val minR: Int = 120,
    val redOverGreen: Int = 55,
    val redOverBlue: Int = 60,
    val greenMin: Int = 115,
    val greenOverRed: Int = 35,
    val greenOverBlue: Int = 30,
    val brightMin: Int = 190,
    val minHeight: Int = 16,
    val minAspect: Float = 1.45f,
    val maxFill: Float = 0.62f,
    val maxGapRatio: Float = 0.34f,
    val headBand: Float = 0.16f,
    val headBias: Float = 0.60f,
    val selfZone: IntArray = IntArray(0)
) {
    fun toJson(): String {
        val o = JSONObject()
        o.put("minR", minR)
        o.put("rog", redOverGreen)
        o.put("rob", redOverBlue)
        o.put("gmin", greenMin)
        o.put("gor", greenOverRed)
        o.put("gob", greenOverBlue)
        o.put("bmin", brightMin)
        o.put("minH", minHeight)
        o.put("minA", minAspect.toDouble())
        o.put("maxF", maxFill.toDouble())
        o.put("maxG", maxGapRatio.toDouble())
        o.put("band", headBand.toDouble())
        o.put("bias", headBias.toDouble())
        val a = JSONArray()
        for (v in selfZone) a.put(v)
        o.put("self", a)
        return o.toString()
    }

    companion object {
        fun defaultModel(): AimModel = AimModel()

        fun fromJson(s: String): AimModel? {
            return try {
                val o = JSONObject(s)
                val arr = o.optJSONArray("self")
                val sz = IntArray(arr?.length() ?: 0) { arr!!.getInt(it) }
                AimModel(
                    o.optInt("minR", 120),
                    o.optInt("rog", 55),
                    o.optInt("rob", 60),
                    o.optInt("gmin", 115),
                    o.optInt("gor", 35),
                    o.optInt("gob", 30),
                    o.optInt("bmin", 190),
                    o.optInt("minH", 16),
                    o.optDouble("minA", 1.45).toFloat(),
                    o.optDouble("maxF", 0.62).toFloat(),
                    o.optDouble("maxG", 0.34).toFloat(),
                    o.optDouble("band", 0.16).toFloat(),
                    o.optDouble("bias", 0.60).toFloat(),
                    sz
                )
            } catch (t: Throwable) {
                null
            }
        }
    }
}
