package com.jondude.tableforge.ai

import com.jondude.tableforge.data.ideaType
import com.jondude.tableforge.data.AiProvider
import com.jondude.tableforge.data.AiSettings
import com.jondude.tableforge.data.BeatStatus
import com.jondude.tableforge.data.Campaign
import com.jondude.tableforge.data.Idea
import com.jondude.tableforge.data.IdeaType
import com.jondude.tableforge.data.Player
import com.jondude.tableforge.data.PlotBeat

/** A generated suggestion before the DM saves it. */
data class Card(
    val title: String,
    val body: String,
    val tags: String = "",
    val key: Long = nextKey(),
) {
    companion object {
        private var counter = 0L
        @Synchronized fun nextKey(): Long = ++counter
    }
}

/** Everything the model should know about the campaign so ideas fit the story. */
data class CampaignContext(
    val campaign: Campaign,
    val beats: List<PlotBeat> = emptyList(),
    val ideas: List<Idea> = emptyList(),
    val players: List<Player> = emptyList(),
) {
    fun describe(): String = buildString {
        appendLine("CAMPAIGN: ${campaign.name}")
        if (campaign.premise.isNotBlank()) appendLine("Premise / overall plot: ${campaign.premise}")
        if (campaign.setting.isNotBlank()) appendLine("Setting: ${campaign.setting}")
        if (campaign.tone.isNotBlank()) appendLine("Tone: ${campaign.tone}")
        if (players.isNotEmpty()) {
            appendLine("Party: " + players.joinToString("; ") {
                listOf(it.characterName, it.characterClass).filter(String::isNotBlank).joinToString(", ")
            })
        }
        if (beats.isNotEmpty()) {
            appendLine("Plot outline so far (in order):")
            beats.forEachIndexed { i, b ->
                val act = if (b.act.isNotBlank()) "[${b.act}] " else ""
                append("${i + 1}. $act${b.title} (${BeatStatus.from(b.status).label})")
                if (b.summary.isNotBlank()) append(" — ${b.summary.take(240)}")
                appendLine()
            }
        }
        val named = ideas.filter { it.ideaType == IdeaType.NPC || it.ideaType == IdeaType.LOCATION }
        if (named.isNotEmpty()) {
            appendLine("Established NPCs and locations: " + named.take(40).joinToString(", ") { it.title })
        }
        val own = ideas.filter { it.isOwn }.take(15)
        if (own.isNotEmpty()) {
            appendLine("The DM's own ideas to weave in where it fits:")
            own.forEach { appendLine("- ${it.title}: ${it.body.take(160)}") }
        }
    }
}

/** Turns generation requests into prompts (AI) or rolls on built-in tables (offline). */
object Forge {

    private const val SYSTEM = """You are a creative assistant for a Dungeons & Dragons 5e Dungeon Master preparing sessions.
Give vivid, specific, table-ready ideas a DM can use immediately. Avoid generic fantasy clichés unless asked.
Keep each idea consistent with the campaign details provided. Never reuse names already established.
Always reply with JSON only, in exactly this shape: {"items":[{"title":"...","body":"...","tags":"..."}]}
"body" is plain text using short labeled lines separated by \n. "tags" is 1-4 comma-separated keywords."""

    private fun shape(type: IdeaType): String = when (type) {
        IdeaType.NPC -> "title = the NPC's name. body lines: Role, Look, Personality, Wants, Secret, Voice/mannerism, Hook (how the party meets them)."
        IdeaType.EVENT -> "title = a short event name. body lines: Trigger, What happens, Stakes, Complication, Possible outcomes."
        IdeaType.LOCATION -> "title = the place name. body lines: Type, First impression (read-aloud, 2 sentences), Notable features, Who's here, Secret, Hooks."
        IdeaType.DUNGEON_ROOM -> "title = a room name. body lines: Read-aloud (2-3 sentences), Contents, Threat or trap (with a DC if relevant), Treasure, Exits, Twist."
        IdeaType.MAGIC_ITEM -> "title = the item name. body lines: Rarity, Attunement (yes/no and by whom), Appearance, Properties (5e mechanics), Quirk, History."
        IdeaType.STORY_BEAT -> "title = a short beat name. body lines: What happens, Why it matters, Connects to (existing beats/NPCs), Foreshadowing, Player choice."
        IdeaType.NOTE -> "title = a short name. body = a few useful lines."
    }

    suspend fun generate(
        settings: AiSettings,
        ctx: CampaignContext,
        type: IdeaType,
        count: Int,
        steer: String,
        avoid: List<String>,
    ): List<Card> {
        if (settings.provider == AiProvider.OFFLINE) return OfflineForge.generate(type, count)
        val user = buildString {
            appendLine(ctx.describe())
            appendLine()
            appendLine("Generate $count distinct ${type.singular} ideas for this campaign.")
            appendLine(shape(type))
            if (steer.isNotBlank()) appendLine("The DM wants: $steer")
            if (avoid.isNotEmpty()) appendLine("Do not repeat or closely resemble these: ${avoid.joinToString("; ")}")
        }
        return toCards(AiClient.complete(settings, SYSTEM, user))
    }

    /** Recommends what could happen next given the outline and what's done. */
    suspend fun nextBeats(settings: AiSettings, ctx: CampaignContext, count: Int, steer: String): List<Card> {
        if (settings.provider == AiProvider.OFFLINE) return OfflineForge.generate(IdeaType.STORY_BEAT, count)
        val user = buildString {
            appendLine(ctx.describe())
            appendLine()
            appendLine("Recommend $count possible next story beats for the coming sessions. Build on beats marked In play or Done, pay off foreshadowing, and move toward the overall plot.")
            appendLine(shape(IdeaType.STORY_BEAT))
            if (steer.isNotBlank()) appendLine("The DM wants: $steer")
        }
        return toCards(AiClient.complete(settings, SYSTEM, user))
    }

    /** Proposes beats that bridge two points in the outline. before/after may be null for the start/end. */
    suspend fun fillGap(
        settings: AiSettings,
        ctx: CampaignContext,
        before: PlotBeat?,
        after: PlotBeat?,
        steer: String,
    ): List<Card> {
        if (settings.provider == AiProvider.OFFLINE) return OfflineForge.generate(IdeaType.STORY_BEAT, 3)
        val user = buildString {
            appendLine(ctx.describe())
            appendLine()
            when {
                before != null && after != null ->
                    appendLine("There is a gap between \"${before.title}\" and \"${after.title}\". Propose 3 alternative bridging beats that get the story logically from one to the other.")
                before == null && after != null ->
                    appendLine("Propose 3 alternative opening beats that lead into \"${after.title}\".")
                before != null ->
                    appendLine("Propose 3 alternative beats that follow \"${before.title}\" and push toward the campaign's climax.")
                else -> appendLine("Propose 3 alternative opening beats for this campaign.")
            }
            appendLine("Each option should be a different direction, not variations of one idea.")
            appendLine(shape(IdeaType.STORY_BEAT))
            if (steer.isNotBlank()) appendLine("The DM wants: $steer")
        }
        return toCards(AiClient.complete(settings, SYSTEM, user))
    }

    /** Builds entries for a dN table. Returns exactly [count] texts when possible. */
    suspend fun tableEntries(
        settings: AiSettings,
        ctx: CampaignContext?,
        tableName: String,
        description: String,
        count: Int,
    ): List<String> {
        if (settings.provider == AiProvider.OFFLINE) return OfflineForge.tableEntries(tableName, count)
        val user = buildString {
            if (ctx != null) { appendLine(ctx.describe()); appendLine() }
            appendLine("Write $count entries for a random table called \"$tableName\".")
            if (description.isNotBlank()) appendLine("What the table is for: $description")
            appendLine("Each entry is one or two punchy sentences a DM can read aloud or use immediately. All entries must be different.")
            appendLine("Put each entry's text in \"body\"; \"title\" can be a 1-3 word label.")
        }
        val cards = toCards(AiClient.complete(settings, SYSTEM, user, maxTokens = 4000))
        return cards.map { if (it.body.isNotBlank()) it.body else it.title }.take(count)
    }

    private fun toCards(raw: String): List<Card> {
        val cards = AiClient.parseItems(raw).mapNotNull { o ->
            val title = o.optString("title").trim()
            val body = o.optString("body").trim().replace("\\n", "\n")
            if (title.isBlank() && body.isBlank()) null
            else Card(title.ifBlank { body.take(40) }, body, o.optString("tags").trim())
        }
        if (cards.isEmpty()) throw AiException("The model didn't return any ideas. Try again.")
        return cards
    }
}
