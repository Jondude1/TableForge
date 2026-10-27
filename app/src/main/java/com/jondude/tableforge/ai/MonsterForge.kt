package com.jondude.tableforge.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import com.jondude.tableforge.data.AiProvider
import com.jondude.tableforge.data.AiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.random.Random

data class NamedText(val name: String, val text: String)

/** A 5e-style stat block; same fields as the web app so data looks the same everywhere. */
data class StatBlock(
    val name: String = "",
    val meta: String = "",
    val ac: String = "",
    val hp: String = "",
    val speed: String = "30 ft.",
    val str: Int = 10, val dex: Int = 10, val con: Int = 10, val int: Int = 10, val wis: Int = 10, val cha: Int = 10,
    val saves: String = "",
    val skills: String = "",
    val vulnerabilities: String = "",
    val resistances: String = "",
    val immunities: String = "",
    val conditionImmunities: String = "",
    val senses: String = "",
    val languages: String = "",
    val cr: String = "",
    val traits: List<NamedText> = emptyList(),
    val actions: List<NamedText> = emptyList(),
    val bonusActions: List<NamedText> = emptyList(),
    val reactions: List<NamedText> = emptyList(),
    val legendaryIntro: String = "",
    val legendary: List<NamedText> = emptyList(),
    val notes: String = "",
) {
    val scores: List<Pair<String, Int>> get() = listOf("STR" to str, "DEX" to dex, "CON" to con, "INT" to int, "WIS" to wis, "CHA" to cha)
    val maxHp: Int? get() = Regex("""^\s*(\d+)""").find(hp)?.groupValues?.get(1)?.toIntOrNull()

    fun toJson(): String = JSONObject().apply {
        put("name", name); put("meta", meta); put("ac", ac); put("hp", hp); put("speed", speed)
        put("str", str); put("dex", dex); put("con", con); put("int", int); put("wis", wis); put("cha", cha)
        put("saves", saves); put("skills", skills); put("vulnerabilities", vulnerabilities); put("resistances", resistances)
        put("immunities", immunities); put("conditionImmunities", conditionImmunities); put("senses", senses)
        put("languages", languages); put("cr", cr)
        put("traits", arr(traits)); put("actions", arr(actions)); put("bonusActions", arr(bonusActions))
        put("reactions", arr(reactions)); put("legendaryIntro", legendaryIntro); put("legendary", arr(legendary)); put("notes", notes)
    }.toString()

    companion object {
        private fun arr(l: List<NamedText>) = JSONArray().apply { l.forEach { put(JSONObject().put("name", it.name).put("text", it.text)) } }

        fun fromJson(s: String): StatBlock = runCatching { normalize(JSONObject(s)) }.getOrDefault(StatBlock(name = "Unreadable"))

        private fun JSONObject.str(k: String) = optString(k, "").let { if (it == "null") "" else it.trim() }
        private fun JSONObject.score(k: String): Int =
            optString(k).replace(Regex("[^\\d-]"), "").toIntOrNull()?.takeIf { it in 1..40 } ?: 10

        private fun JSONObject.list(k: String): List<NamedText> {
            val a = optJSONArray(k) ?: return emptyList()
            return (0 until a.length()).mapNotNull { i ->
                when (val x = a.opt(i)) {
                    is JSONObject -> NamedText(x.str("name").removeSuffix("."), x.str("text"))
                    is String -> splitNamed(x)
                    else -> null
                }
            }.filter { it.name.isNotBlank() || it.text.isNotBlank() }
        }

        fun normalize(o: JSONObject) = StatBlock(
            name = o.str("name").ifBlank { "Unnamed creature" }, meta = o.str("meta"), ac = o.str("ac"), hp = o.str("hp"),
            speed = o.str("speed").ifBlank { "30 ft." },
            str = o.score("str"), dex = o.score("dex"), con = o.score("con"), int = o.score("int"), wis = o.score("wis"), cha = o.score("cha"),
            saves = o.str("saves"), skills = o.str("skills"), vulnerabilities = o.str("vulnerabilities"), resistances = o.str("resistances"),
            immunities = o.str("immunities"), conditionImmunities = o.str("conditionImmunities"), senses = o.str("senses"),
            languages = o.str("languages"), cr = o.str("cr"),
            traits = o.list("traits"), actions = o.list("actions"), bonusActions = o.list("bonusActions"), reactions = o.list("reactions"),
            legendaryIntro = o.str("legendaryIntro"), legendary = o.list("legendary"), notes = o.str("notes"),
        )

        /** "Bite. Melee Weapon Attack: ..." -> NamedText("Bite", "Melee Weapon Attack: ...") */
        fun splitNamed(s: String): NamedText {
            val m = Regex("""^\s*([^.]{1,60}?(?:\s*\([^)]*\))?)\.\s+([\s\S]*)$""").find(s.trim())
            return if (m != null) NamedText(m.groupValues[1].trim(), m.groupValues[2].trim()) else NamedText("", s.trim())
        }

        fun mod(score: Int) = Math.floorDiv(score - 10, 2)
        fun fmtMod(n: Int) = if (n >= 0) "+$n" else "$n"
    }
}

/** Reads stat blocks from photos or text, or designs new ones, using the configured model. */
object MonsterForge {
    private const val SHAPE = """Reply with JSON only, exactly this shape (use "" or [] when something isn't on the stat block):
{"name":"","meta":"size type, alignment","ac":"","hp":"","speed":"","str":10,"dex":10,"con":10,"int":10,"wis":10,"cha":10,
"saves":"","skills":"","vulnerabilities":"","resistances":"","immunities":"","conditionImmunities":"","senses":"","languages":"","cr":"",
"traits":[{"name":"","text":""}],"actions":[{"name":"","text":""}],"bonusActions":[],"reactions":[],"legendaryIntro":"","legendary":[],"notes":""}
Rules: "immunities" is damage immunities. Ability scores are numbers (scores, not modifiers). Keep each action's text exactly as written,
including attack bonuses ("+7 to hit") and damage dice ("2d6 + 4"). "cr" includes XP if shown, e.g. "5 (1,800 XP)".
"meta" is the line under the name, e.g. "Medium humanoid (goblinoid), neutral evil". Put anything that doesn't fit (lair actions, flavor) in "notes"."""

    private const val READ = """You transcribe Dungeons & Dragons 5e monster stat blocks into structured data for a Dungeon Master's app.
Read carefully: photos may be angled, glared, or split across columns or pages. Fix obvious OCR-style errors, but never invent numbers that aren't there.
If several images are given they are parts of the same stat block, in order. """ + SHAPE

    private const val MAKE = """You design balanced, flavorful Dungeons & Dragons 5e monsters following the official stat block conventions and CR guidelines
(Dungeon Master's Guide monster creation tables). """ + SHAPE

    private fun need(s: AiSettings) {
        if (s.provider == AiProvider.OFFLINE || !s.isConfigured) {
            throw AiException("Reading stat blocks needs Claude. Add your Claude API key in Settings, or use \"Start blank\" and type it in.")
        }
    }

    private fun parse(raw: String): StatBlock {
        val t = raw.replace("```json", "").replace("```", "").trim()
        val start = t.indexOf('{'); val end = t.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiException("The answer wasn't in the expected format. Try again.")
        return runCatching { StatBlock.normalize(JSONObject(t.substring(start, end + 1))) }
            .getOrElse { throw AiException("The answer wasn't in the expected format. Try again.") }
    }

    suspend fun fromPhotos(s: AiSettings, images: List<ImagePart>): StatBlock {
        need(s); return parse(AiClient.complete(s, READ, "Transcribe this stat block.", 4000, images))
    }

    suspend fun fromText(s: AiSettings, text: String): StatBlock {
        need(s); return parse(AiClient.complete(s, READ, "Transcribe this stat block:\n\n$text", 4000))
    }

    suspend fun generate(s: AiSettings, idea: String, campaign: String): StatBlock {
        need(s)
        val user = (if (campaign.isNotBlank()) "$campaign\n\n" else "") + "Create this monster: $idea"
        return parse(AiClient.complete(s, MAKE, user, 4000))
    }

    /** Loads a photo, fixes its rotation, shrinks it to 1568 px on the long side and encodes it as JPEG. */
    suspend fun prepareImage(context: Context, uri: Uri): ImagePart = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) throw AiException("Couldn't open that image.")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1568) sample *= 2
        val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: throw AiException("Couldn't open that image.")
        val degrees = runCatching {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        val scale = minOf(1f, 1568f / maxOf(raw.width, raw.height))
        val m = Matrix().apply { postScale(scale, scale); postRotate(degrees) }
        val bmp = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        ImagePart("image/jpeg", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
    }
}

// ---------- Dice inside stat block text ----------

sealed interface Seg {
    val s: String
    data class Text(override val s: String) : Seg
    data class Hit(override val s: String, val bonus: Int) : Seg
    data class Dmg(override val s: String, val n: Int, val d: Int, val plus: Int) : Seg
}

/** Finds "+7 to hit" and "2d6 + 4" so they can be tapped to roll. */
fun rollable(text: String): List<Seg> {
    val re = Regex("""([+-]\d+)\s+to hit|(\d+)d(\d+)(?:\s*([+-])\s*(\d+))?""")
    val out = mutableListOf<Seg>()
    var last = 0
    re.findAll(text).forEach { m ->
        if (m.range.first > last) out += Seg.Text(text.substring(last, m.range.first))
        val g = m.groupValues
        out += if (g[1].isNotEmpty()) Seg.Hit(m.value, g[1].toInt())
        else Seg.Dmg(m.value, g[2].toInt(), g[3].toInt(), if (g[4].isEmpty()) 0 else (if (g[4] == "-") -1 else 1) * g[5].toInt())
        last = m.range.last + 1
    }
    if (last < text.length) out += Seg.Text(text.substring(last))
    return out
}

data class DiceResult(val total: Int, val rolls: List<Int>)

fun rollDice(n: Int, d: Int, plus: Int = 0): DiceResult {
    val rolls = List(n.coerceIn(1, 100)) { Random.nextInt(1, d.coerceAtLeast(1) + 1) }
    return DiceResult(rolls.sum() + plus, rolls)
}
