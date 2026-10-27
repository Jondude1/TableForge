package com.jondude.tableforge

import com.jondude.tableforge.data.ideaType
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jondude.tableforge.ai.AiException
import com.jondude.tableforge.ai.CampaignContext
import com.jondude.tableforge.ai.Card
import com.jondude.tableforge.ai.Forge
import com.jondude.tableforge.ai.GridRect
import com.jondude.tableforge.ai.MapForge
import com.jondude.tableforge.ai.ImagePart
import com.jondude.tableforge.ai.MonsterForge
import com.jondude.tableforge.ai.StatBlock
import com.jondude.tableforge.data.Monster
import com.jondude.tableforge.data.MapFeature
import com.jondude.tableforge.data.Terrain
import com.jondude.tableforge.data.TileMap
import com.jondude.tableforge.data.AiSettings
import com.jondude.tableforge.data.Campaign
import com.jondude.tableforge.data.Idea
import com.jondude.tableforge.data.IdeaType
import com.jondude.tableforge.data.MapEdge
import com.jondude.tableforge.data.MapNode
import com.jondude.tableforge.data.PartyItem
import com.jondude.tableforge.data.Player
import com.jondude.tableforge.data.PlotBeat
import com.jondude.tableforge.data.RollTable
import com.jondude.tableforge.data.TableEntry
import com.jondude.tableforge.data.TableMath
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Generated cards waiting for the DM to keep, save or regenerate. */
data class SuggestionState(
    val cards: List<Card> = emptyList(),
    val kept: Set<Long> = emptySet(),
    val saved: Set<Long> = emptySet(),
    val loading: Boolean = false,
    val error: String? = null,
)

/** One line in the dice log. */
data class RollRecord(val label: String, val roll: Int, val sides: Int, val text: String?, val at: Long = System.currentTimeMillis())

/** Progress and result of a "put this here" request on a tile map. */
data class MapRequestState(val loading: Boolean = false, val error: String? = null, val summary: String? = null, val canUndo: Boolean = false)

private data class MapUndo(val mapId: Long, val terrain: String, val addedIds: List<Long>, val removed: List<MapFeature>)

/** Where gap suggestions will be inserted in the outline. */
data class GapAnchor(val beforeId: Long?, val afterId: Long?)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val tf = app as TableForgeApp
    private val db = tf.db
    private val store = tf.settings

    val ai: StateFlow<AiSettings> = store.ai
    val campaignId: StateFlow<Long> = store.campaignId

    val campaigns = db.campaigns().all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun <T> perCampaign(empty: T, f: (Long) -> Flow<T>): StateFlow<T> =
        campaignId.flatMapLatest { id -> if (id > 0) f(id) else flowOf(empty) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, empty)

    val campaign: StateFlow<Campaign?> = perCampaign(null) { db.campaigns().observe(it) }
    val ideas = perCampaign(emptyList()) { db.ideas().forCampaign(it) }
    val beats = perCampaign(emptyList()) { db.plot().forCampaign(it) }
    val players = perCampaign(emptyList()) { db.party().players(it) }
    val items = perCampaign(emptyList()) { db.party().items(it) }
    val tables = perCampaign(emptyList()) { db.tables().tables(it) }
    val nodes = perCampaign(emptyList()) { db.map().nodes(it) }
    val edges = perCampaign(emptyList()) { db.map().edges(it) }
    val tileMaps = perCampaign(emptyList()) { db.tiles().maps(it) }
    val monsters = perCampaign(emptyList()) { db.monsters().forCampaign(it) }

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    fun toast(msg: String) { _toast.value = msg }
    fun toastShown() { _toast.value = null }

    init {
        viewModelScope.launch {
            if (db.campaigns().count() == 0) {
                val id = db.campaigns().insert(
                    Campaign(name = "My Campaign", premise = "", tone = "Heroic fantasy with moments of humor", setting = ""),
                )
                store.setCampaign(id)
                seedTables()
            } else if (db.campaigns().get(store.campaignId.value) == null) {
                db.campaigns().first()?.let { store.setCampaign(it.id) }
            }
        }
    }

    /** A couple of shared starter tables so the dice screen isn't empty on day one. */
    private suspend fun seedTables() {
        val t = db.tables()
        val wildId = t.upsertTable(RollTable(name = "Tavern Rumors", dieSides = 6, description = "Gossip overheard over a pint"))
        t.replaceEntries(
            wildId, TableMath.entriesFor(
                wildId, 6, listOf(
                    "The miller's daughter has been seen walking into the lake at night.",
                    "A dragon was spotted over the northern peaks, carrying a cart.",
                    "The new priest doesn't cast a shadow.",
                    "Someone is paying gold for rat tails. Lots of gold.",
                    "The old mine reopened, but nobody hired any miners.",
                    "The baron's heir is missing, and the baron hasn't told anyone.",
                ),
            ),
        )
        val wId = t.upsertTable(RollTable(name = "Weather", dieSides = 8, description = "Today's weather on the road"))
        t.replaceEntries(
            wId, TableMath.entriesFor(
                wId, 8, listOf(
                    "Clear and bright", "Light drizzle", "Thick fog until noon", "Strong winds",
                    "Heavy rain — travel speed halved", "Thunderstorm", "Unseasonable heat", "Eerie stillness, no birdsong",
                ),
            ),
        )
    }

    // ---------- Settings & campaigns ----------

    fun saveAi(s: AiSettings) = store.saveAi(s)

    private val _aiTest = MutableStateFlow<String?>(null)
    val aiTest: StateFlow<String?> = _aiTest.asStateFlow()

    /** Sends a tiny request with the given settings and reports what happened. */
    fun testAi(s: AiSettings) {
        _aiTest.value = "Testing…"
        viewModelScope.launch {
            _aiTest.value = try {
                val reply = com.jondude.tableforge.ai.AiClient.complete(
                    s, "Reply with JSON only.", "Reply with {\"items\":[{\"title\":\"ok\",\"body\":\"ready\"}]}", maxTokens = 50,
                )
                if (reply.isNotBlank()) "Connected — ready to forge." else "Connected, but the reply was empty."
            } catch (e: Exception) {
                "Failed: " + friendly(e)
            }
        }
    }

    fun selectCampaign(id: Long) = store.setCampaign(id)

    fun newCampaign(name: String, premise: String) = viewModelScope.launch {
        val id = db.campaigns().insert(Campaign(name = name.ifBlank { "New Campaign" }, premise = premise))
        store.setCampaign(id)
    }

    fun updateCampaign(c: Campaign) = viewModelScope.launch { db.campaigns().update(c) }

    fun deleteCampaign(c: Campaign) = viewModelScope.launch {
        db.tables().deleteForCampaign(c.id)
        db.monsters().deleteForCampaign(c.id)
        db.campaigns().delete(c)
        val next = db.campaigns().first()
        if (next != null) store.setCampaign(next.id)
        else store.setCampaign(db.campaigns().insert(Campaign(name = "My Campaign")))
    }

    private fun context(): CampaignContext? = campaign.value?.let {
        CampaignContext(it, beats.value, ideas.value, players.value)
    }

    private fun friendly(e: Throwable): String = when (e) {
        is AiException -> e.message ?: "Something went wrong."
        else -> e.message ?: e.javaClass.simpleName
    }

    // ---------- Forge (generators) ----------

    private val _forge = MutableStateFlow(SuggestionState())
    val forge: StateFlow<SuggestionState> = _forge.asStateFlow()
    private val _forgeType = MutableStateFlow(IdeaType.NPC)
    val forgeType: StateFlow<IdeaType> = _forgeType.asStateFlow()

    fun setForgeType(t: IdeaType) {
        if (t != _forgeType.value) {
            _forgeType.value = t
            _forge.value = SuggestionState()
        }
    }

    /** Regenerates everything except the cards the DM pinned with "keep". */
    fun forgeGenerate(count: Int, steer: String) {
        val ctx = context() ?: return
        val type = _forgeType.value
        val current = _forge.value
        val keptCards = current.cards.filter { it.key in current.kept }
        val need = (count - keptCards.size).coerceAtLeast(1)
        _forge.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val avoid = current.cards.map { it.title } + ideas.value.filter { it.ideaType == type }.map { it.title }.take(30)
                val fresh = Forge.generate(ai.value, ctx, type, need, steer, avoid)
                _forge.update { it.copy(cards = keptCards + fresh, loading = false) }
            } catch (e: Exception) {
                _forge.update { it.copy(loading = false, error = friendly(e)) }
            }
        }
    }

    /** Replaces a single card with a new idea. */
    fun forgeRegenerateOne(card: Card, steer: String) {
        val ctx = context() ?: return
        val type = _forgeType.value
        _forge.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val avoid = _forge.value.cards.map { it.title }
                val fresh = Forge.generate(ai.value, ctx, type, 1, steer, avoid).first()
                _forge.update { s -> s.copy(cards = s.cards.map { if (it.key == card.key) fresh else it }, loading = false) }
            } catch (e: Exception) {
                _forge.update { it.copy(loading = false, error = friendly(e)) }
            }
        }
    }

    fun forgeToggleKeep(card: Card) = _forge.update {
        it.copy(kept = if (card.key in it.kept) it.kept - card.key else it.kept + card.key)
    }

    fun forgeDiscard(card: Card) = _forge.update { s -> s.copy(cards = s.cards.filterNot { it.key == card.key }) }

    fun forgeSave(card: Card) {
        val id = campaignId.value
        val type = _forgeType.value
        viewModelScope.launch {
            db.ideas().upsert(Idea(campaignId = id, type = type.name, title = card.title, body = card.body, tags = card.tags))
            _forge.update { it.copy(saved = it.saved + card.key) }
            toast("Saved to Ideas")
        }
    }

    fun forgeClear() { _forge.value = SuggestionState() }

    // ---------- Story ----------

    private val _story = MutableStateFlow(SuggestionState())
    val story: StateFlow<SuggestionState> = _story.asStateFlow()
    private val _gapAnchor = MutableStateFlow<GapAnchor?>(null)
    val gapAnchor: StateFlow<GapAnchor?> = _gapAnchor.asStateFlow()

    fun storyNextBeats(steer: String) {
        val ctx = context() ?: return
        _gapAnchor.value = null
        _story.value = SuggestionState(loading = true)
        viewModelScope.launch {
            try {
                _story.value = SuggestionState(cards = Forge.nextBeats(ai.value, ctx, 4, steer))
            } catch (e: Exception) {
                _story.value = SuggestionState(error = friendly(e))
            }
        }
    }

    fun storyFillGap(before: PlotBeat?, after: PlotBeat?, steer: String) {
        val ctx = context() ?: return
        _gapAnchor.value = GapAnchor(before?.id, after?.id)
        _story.value = SuggestionState(loading = true)
        viewModelScope.launch {
            try {
                _story.value = SuggestionState(cards = Forge.fillGap(ai.value, ctx, before, after, steer))
            } catch (e: Exception) {
                _story.value = SuggestionState(error = friendly(e))
            }
        }
    }

    fun storyClearOne(card: Card) = _story.update { s -> s.copy(cards = s.cards.filterNot { it.key == card.key }) }

    fun storyClear() {
        _story.value = SuggestionState()
        _gapAnchor.value = null
    }

    /** Adds a suggestion to the outline: into the gap it was made for, or at the end. */
    fun storyAccept(card: Card) {
        val anchor = _gapAnchor.value
        val list = beats.value
        val position = when {
            anchor == null -> (list.maxOfOrNull { it.position } ?: 0.0) + 1.0
            else -> {
                val before = list.firstOrNull { it.id == anchor.beforeId }
                val after = list.firstOrNull { it.id == anchor.afterId }
                when {
                    before != null && after != null -> (before.position + after.position) / 2.0
                    after != null -> after.position - 1.0
                    before != null -> before.position + 1.0
                    else -> (list.maxOfOrNull { it.position } ?: 0.0) + 1.0
                }
            }
        }
        val act = anchor?.beforeId?.let { id -> list.firstOrNull { it.id == id }?.act } ?: list.lastOrNull()?.act ?: ""
        viewModelScope.launch {
            db.plot().upsert(PlotBeat(campaignId = campaignId.value, position = position, act = act, title = card.title, summary = card.body))
            _story.update { s -> s.copy(cards = s.cards.filterNot { it.key == card.key }) }
            toast("Added to the outline")
        }
    }

    fun storySaveAsIdea(card: Card) = viewModelScope.launch {
        db.ideas().upsert(Idea(campaignId = campaignId.value, type = IdeaType.STORY_BEAT.name, title = card.title, body = card.body, tags = card.tags))
        _story.update { it.copy(saved = it.saved + card.key) }
        toast("Saved to Ideas")
    }

    fun saveBeat(b: PlotBeat) = viewModelScope.launch {
        val pos = if (b.id == 0L) (db.plot().maxPosition(campaignId.value) ?: 0.0) + 1.0 else b.position
        db.plot().upsert(b.copy(campaignId = campaignId.value, position = pos))
    }

    fun deleteBeat(b: PlotBeat) = viewModelScope.launch { db.plot().delete(b) }

    fun moveBeat(b: PlotBeat, up: Boolean) = viewModelScope.launch {
        val list = beats.value
        val i = list.indexOfFirst { it.id == b.id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in list.indices) return@launch
        val other = list[j]
        db.plot().updateAll(listOf(b.copy(position = other.position), other.copy(position = b.position)))
    }

    // ---------- Ideas ----------

    fun saveIdea(i: Idea) = viewModelScope.launch { db.ideas().upsert(i.copy(campaignId = campaignId.value)) }
    fun deleteIdea(i: Idea) = viewModelScope.launch { db.ideas().delete(i) }

    fun ideaToOutline(i: Idea) = viewModelScope.launch {
        val pos = (db.plot().maxPosition(campaignId.value) ?: 0.0) + 1.0
        db.plot().upsert(PlotBeat(campaignId = campaignId.value, position = pos, title = i.title, summary = i.body, act = beats.value.lastOrNull()?.act ?: ""))
        toast("Added to the outline")
    }

    fun ideaToLoot(i: Idea) = viewModelScope.launch {
        val rarity = Regex("Rarity:\\s*([A-Za-z ]+)", RegexOption.IGNORE_CASE).find(i.body)?.groupValues?.get(1)?.trim()
            ?.replaceFirstChar { it.uppercase() } ?: "Uncommon"
        val attune = Regex("Attunement:\\s*yes", RegexOption.IGNORE_CASE).containsMatchIn(i.body)
        db.party().upsertItem(PartyItem(campaignId = campaignId.value, name = i.title, rarity = rarity, requiresAttunement = attune, notes = i.body))
        toast("Added to the party stash")
    }

    fun ideaToMap(i: Idea) = viewModelScope.launch {
        val n = nodes.value.size
        db.map().upsertNode(
            MapNode(campaignId = campaignId.value, label = i.title, note = i.body, kind = i.type, x = 120f + (n % 5) * 220f, y = 120f + (n / 5) * 160f, ideaId = i.id),
        )
        toast("Added to the concept map")
    }

    // ---------- Party ----------

    fun savePlayer(p: Player) = viewModelScope.launch { db.party().upsertPlayer(p.copy(campaignId = campaignId.value)) }
    fun deletePlayer(p: Player) = viewModelScope.launch { db.party().deletePlayer(p) }
    fun saveItem(i: PartyItem) = viewModelScope.launch { db.party().upsertItem(i.copy(campaignId = campaignId.value)) }
    fun deleteItem(i: PartyItem) = viewModelScope.launch { db.party().deleteItem(i) }

    // ---------- Tables ----------

    fun entries(tableId: Long): Flow<List<TableEntry>> = db.tables().entries(tableId)

    private val _tableBusy = MutableStateFlow(false)
    val tableBusy: StateFlow<Boolean> = _tableBusy.asStateFlow()

    /** Saves a table and its entries; returns via callback the table id. */
    fun saveTable(t: RollTable, texts: List<String>?, onSaved: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = if (t.id == 0L) db.tables().upsertTable(t) else { db.tables().upsertTable(t); t.id }
        if (texts != null) db.tables().replaceEntries(id, TableMath.entriesFor(id, t.dieSides, texts.filter { it.isNotBlank() }))
        onSaved(id)
    }

    private val _rolls = MutableStateFlow<List<RollRecord>>(emptyList())
    val rolls: StateFlow<List<RollRecord>> = _rolls.asStateFlow()

    /** Rolls a die, looks the result up on the table if there is one, and logs it. */
    fun roll(label: String, sides: Int, entries: List<TableEntry>? = null): RollRecord {
        val n = TableMath.roll(sides)
        val rec = RollRecord(label, n, sides, entries?.let { TableMath.lookup(it, n)?.text ?: "(no entry for $n)" })
        _rolls.update { (listOf(rec) + it).take(50) }
        return rec
    }

    fun clearRolls() { _rolls.value = emptyList() }

    fun deleteTable(t: RollTable) = viewModelScope.launch { db.tables().deleteTable(t) }

    /** Fills a table with generated entries (replacing what's there). */
    fun generateTable(t: RollTable, count: Int, onDone: (List<String>) -> Unit) {
        _tableBusy.value = true
        viewModelScope.launch {
            try {
                val texts = Forge.tableEntries(ai.value, context(), t.name, t.description, count)
                onDone(texts)
            } catch (e: Exception) {
                toast(friendly(e))
            } finally {
                _tableBusy.value = false
            }
        }
    }

    // ---------- Concept map ----------

    fun addNode(label: String, note: String, kind: IdeaType, x: Float, y: Float) = viewModelScope.launch {
        db.map().upsertNode(MapNode(campaignId = campaignId.value, label = label, note = note, kind = kind.name, x = x, y = y))
    }

    fun updateNode(n: MapNode) = viewModelScope.launch { db.map().upsertNode(n) }
    fun moveNode(id: Long, x: Float, y: Float) = viewModelScope.launch { db.map().moveNode(id, x, y) }
    fun deleteNode(n: MapNode) = viewModelScope.launch { db.map().deleteNode(n) }

    fun connect(a: Long, b: Long, label: String = "") = viewModelScope.launch {
        if (a == b) return@launch
        if (db.map().edgeCount(a, b) > 0) { toast("Those are already linked"); return@launch }
        db.map().insertEdge(MapEdge(campaignId = campaignId.value, fromId = a, toId = b, label = label))
    }

    fun saveEdge(e: MapEdge) = viewModelScope.launch { db.map().upsertEdge(e) }
    fun deleteEdge(e: MapEdge) = viewModelScope.launch { db.map().deleteEdge(e) }

    // ---------- Tile maps ----------

    fun features(mapId: Long): Flow<List<MapFeature>> = db.tiles().features(mapId)

    private val _mapReq = MutableStateFlow(MapRequestState())
    val mapRequest: StateFlow<MapRequestState> = _mapReq.asStateFlow()
    private var undo: MapUndo? = null

    fun createMap(name: String, cols: Int, rows: Int, base: Terrain, feet: Int, onCreated: (Long) -> Unit) = viewModelScope.launch {
        val id = db.tiles().upsertMap(
            TileMap(
                campaignId = campaignId.value, name = name.ifBlank { "New map" },
                cols = cols, rows = rows, terrain = MapForge.blankTerrain(cols, rows, base), feetPerSquare = feet,
            ),
        )
        onCreated(id)
    }

    fun updateMapInfo(m: TileMap) = viewModelScope.launch { db.tiles().upsertMap(m) }

    fun deleteMap(m: TileMap) = viewModelScope.launch { db.tiles().deleteMap(m) }

    /** Paints the given squares by hand (no AI). */
    fun paintCells(mapId: Long, terrain: Terrain, rect: GridRect) = viewModelScope.launch {
        val m = db.tiles().get(mapId) ?: return@launch
        val cells = buildList { for (y in rect.y until rect.y + rect.h) for (x in rect.x until rect.x + rect.w) add(x to y) }
            .filter { (x, y) -> x in 0 until m.cols && y in 0 until m.rows }
        undo = MapUndo(m.id, m.terrain, emptyList(), emptyList())
        db.tiles().setTerrain(m.id, MapForge.paint(m, listOf(com.jondude.tableforge.ai.TerrainPaint(terrain, cells))))
        _mapReq.value = MapRequestState(summary = "Painted ${rect.label} as ${terrain.label.lowercase()}.", canUndo = true)
    }

    /** Adds a feature by hand at the selection, sized to it. */
    fun addFeatureManually(mapId: Long, rect: GridRect, name: String) = viewModelScope.launch {
        val id = db.tiles().upsertFeature(
            MapFeature(mapId = mapId, x = rect.x, y = rect.y, w = rect.w, h = rect.h, name = name.trim(), kind = name.trim().lowercase(), icon = MapForge.iconFor(name)),
        )
        val m = db.tiles().get(mapId) ?: return@launch
        undo = MapUndo(mapId, m.terrain, listOf(id), emptyList())
        _mapReq.value = MapRequestState(summary = "Added ${name.trim()} at ${rect.label}.", canUndo = true)
    }

    /** Sends a free-form request ("a lighthouse that takes up 4 squares") and applies the result. */
    fun requestOnMap(mapId: Long, selection: GridRect, request: String, onDone: () -> Unit = {}) {
        if (request.isBlank()) return
        _mapReq.value = MapRequestState(loading = true)
        viewModelScope.launch {
            try {
                val m = db.tiles().get(mapId) ?: return@launch
                val existing = db.tiles().featuresNow(mapId)
                val edit = MapForge.build(ai.value, context(), m, existing, selection, request.trim())
                val removed = existing.filter { it.id in edit.removeIds }
                if (edit.removeIds.isNotEmpty()) db.tiles().deleteFeatures(mapId, edit.removeIds)
                if (edit.terrain.isNotEmpty()) db.tiles().setTerrain(mapId, MapForge.paint(m, edit.terrain))
                val added = edit.features.map { db.tiles().upsertFeature(it.copy(mapId = mapId)) }
                undo = MapUndo(mapId, m.terrain, added, removed)
                val summary = edit.summary.ifBlank {
                    listOfNotNull(
                        edit.features.takeIf { it.isNotEmpty() }?.let { "Added " + it.joinToString(", ") { f -> f.name } },
                        edit.terrain.takeIf { it.isNotEmpty() }?.let { "repainted ${it.sumOf { p -> p.cells.size }} squares" },
                        edit.removeIds.takeIf { it.isNotEmpty() }?.let { "removed ${it.size}" },
                    ).joinToString("; ") + "."
                }
                _mapReq.value = MapRequestState(summary = summary, canUndo = true)
                onDone()
            } catch (e: Exception) {
                _mapReq.value = MapRequestState(error = friendly(e))
            }
        }
    }

    fun undoMapChange() {
        val u = undo ?: return
        undo = null
        viewModelScope.launch {
            if (u.addedIds.isNotEmpty()) db.tiles().deleteFeatures(u.mapId, u.addedIds)
            if (u.removed.isNotEmpty()) db.tiles().insertFeatures(u.removed)
            db.tiles().setTerrain(u.mapId, u.terrain)
            _mapReq.value = MapRequestState(summary = "Undone.")
        }
    }

    fun dismissMapMessage() { _mapReq.value = MapRequestState(canUndo = _mapReq.value.canUndo && undo != null) }

    fun saveFeature(f: MapFeature) = viewModelScope.launch { db.tiles().upsertFeature(f) }

    fun deleteFeature(f: MapFeature) = viewModelScope.launch {
        val m = db.tiles().get(f.mapId) ?: return@launch
        db.tiles().deleteFeature(f)
        undo = MapUndo(f.mapId, m.terrain, emptyList(), listOf(f))
        _mapReq.value = MapRequestState(summary = "Removed ${f.name}.", canUndo = true)
    }

    /** Saves a placed feature as an Idea so it shows up in the library and feeds generation. */
    fun featureToIdea(f: MapFeature) = viewModelScope.launch {
        db.ideas().upsert(Idea(campaignId = campaignId.value, type = IdeaType.LOCATION.name, title = f.name, body = f.description, tags = f.kind))
        toast("Saved ${f.name} to Ideas")
    }

    // ---------- Monsters ----------

    private val _monsterReq = MutableStateFlow(MonsterRequest())
    val monsterRequest: StateFlow<MonsterRequest> = _monsterReq.asStateFlow()

    fun clearMonsterRequest() { _monsterReq.value = MonsterRequest() }

    private fun runMonster(f: suspend () -> StatBlock) {
        _monsterReq.value = MonsterRequest(loading = true)
        viewModelScope.launch {
            _monsterReq.value = try { MonsterRequest(result = f()) } catch (e: Exception) { MonsterRequest(error = friendly(e)) }
        }
    }

    fun scanMonster(images: List<ImagePart>) = runMonster { MonsterForge.fromPhotos(ai.value, images) }
    fun pasteMonster(text: String) = runMonster { MonsterForge.fromText(ai.value, text) }
    fun createMonster(idea: String) = runMonster {
        val c = campaign.value
        MonsterForge.generate(ai.value, idea, listOfNotNull(c?.name, c?.setting, c?.tone).filter { it.isNotBlank() }.joinToString(" · "))
    }

    /** Saves a stat block; new monsters start at full HP. Calls back with the row id. */
    fun saveMonster(existing: Monster?, b: StatBlock, onSaved: (Long) -> Unit = {}) = viewModelScope.launch {
        val hpChanged = existing == null || existing.data.let { StatBlock.fromJson(it).hp } != b.hp
        val row = (existing ?: Monster(campaignId = campaignId.value, name = "", cr = "", data = "", hpNow = null)).copy(
            name = b.name.trim(), cr = b.cr, data = b.toJson(),
            hpNow = if (hpChanged || existing?.hpNow == null) b.maxHp else existing?.hpNow,
        )
        val id = db.monsters().upsert(row)
        onSaved(if (row.id == 0L) id else row.id)
    }

    fun setMonsterHp(m: Monster, hp: Int?) = viewModelScope.launch { db.monsters().setHp(m.id, hp) }
    fun deleteMonster(m: Monster) = viewModelScope.launch { db.monsters().delete(m) }
}

data class MonsterRequest(val loading: Boolean = false, val error: String? = null, val result: StatBlock? = null)
