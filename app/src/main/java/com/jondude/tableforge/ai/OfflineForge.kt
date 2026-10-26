package com.jondude.tableforge.ai

import com.jondude.tableforge.data.IdeaType
import kotlin.random.Random

/**
 * Generator that works with no network at all, by combining built-in word tables.
 * Less clever than a model, but always available at the table.
 */
object OfflineForge {
    private val rng = Random(System.nanoTime())
    private fun <T> List<T>.pick(): T = this[rng.nextInt(size)]

    private val firstNames = listOf(
        "Aldric", "Brenna", "Cassius", "Dagny", "Elowen", "Fenwick", "Gisela", "Horace", "Ilsa", "Jory",
        "Kestrel", "Lucan", "Maren", "Nils", "Odessa", "Perrin", "Quill", "Rosalind", "Silas", "Tamsin",
        "Ulric", "Vesna", "Wren", "Yorick", "Zara", "Bram", "Coralie", "Dmitri", "Esme", "Garrick",
        "Hesper", "Ivo", "Juniper", "Korrin", "Lark", "Mordecai", "Nyx", "Orrin", "Pell", "Sable",
    )
    private val surnames = listOf(
        "Ashdown", "Blackbriar", "Copperkettle", "Dunmore", "Emberly", "Fallowmere", "Greaves", "Hollowell",
        "Ironsong", "Kettleby", "Larkspur", "Marsh", "Nettlefield", "Oakhart", "Pennywhistle", "Quarry",
        "Ravensworth", "Saltmarsh", "Thorne", "Underbough", "Vane", "Whitlock", "Yarrow", "Grimsby",
    )
    private val ancestries = listOf("human", "dwarf", "elf", "halfling", "gnome", "half-orc", "tiefling", "dragonborn", "half-elf", "goliath")
    private val roles = listOf(
        "innkeeper", "disgraced knight", "smuggler", "temple acolyte", "cartographer", "retired adventurer",
        "fence for stolen goods", "town guard sergeant", "traveling tinker", "noble's steward", "herbalist",
        "gravedigger", "bard with a grudge", "dockmaster", "bounty hunter", "hedge wizard", "blacksmith", "spy",
    )
    private val looks = listOf(
        "a burn scar along one arm", "mismatched eyes", "far too much jewelry", "ink-stained fingers",
        "a missing front tooth", "an immaculate coat that's clearly borrowed", "a nervous twitch",
        "braids threaded with tiny bones", "a heavy limp", "a booming laugh", "a pet raven on one shoulder",
    )
    private val personalities = listOf(
        "suspicious of strangers", "relentlessly cheerful", "greedy but honest about it", "quietly grieving",
        "pompous", "curious to a fault", "superstitious", "loyal to a fault", "bored and looking for trouble",
        "overly formal", "a compulsive liar", "kind but terrified",
    )
    private val wants = listOf(
        "to pay off a dangerous debt", "revenge on a former partner", "to find a missing sibling",
        "a way out of town", "proof that a rival is cheating", "a rare ingredient", "to be taken seriously",
        "to protect a secret child", "an heirloom that was stolen", "to join the party",
    )
    private val secrets = listOf(
        "is an informant for the villain", "is a noble in hiding", "killed someone years ago",
        "is cursed and hides the symptoms", "knows where the treasure is", "is a doppelganger",
        "owes the thieves' guild everything", "is secretly a cultist having second thoughts",
        "has a map they can't read", "is the real heir to the barony",
    )
    private val voices = listOf(
        "speaks in a whisper", "ends every sentence like a question", "uses old-fashioned words",
        "never makes eye contact", "talks with their hands", "hums between sentences", "laughs at their own jokes",
    )

    private val eventNames = listOf(
        "The Bells Ring at Midnight", "A Wedding Gone Wrong", "The Bridge Collapses", "Plague Ship in the Harbor",
        "The Festival of Masks", "Riot in the Market", "An Unexpected Heir", "The Comet", "Wolves in the Village",
        "The Missing Caravan", "Duel at Dawn", "The Mayor Vanishes",
    )
    private val triggers = listOf(
        "the party arrives in town", "a messenger collapses at their feet", "a bell tolls at an odd hour",
        "the party takes a long rest", "someone recognizes a party member", "a storm rolls in",
    )
    private val happenings = listOf(
        "a crowd turns on an innocent stranger", "an explosion rocks the docks", "a noble offers a suspicious job",
        "the dead walk for one night", "a fey bargain comes due", "the town's water turns red",
        "a rival adventuring party claims the same bounty", "a prisoner escapes the stocks",
    )
    private val stakes = listOf(
        "innocent lives", "the party's reputation", "a powerful ally's trust", "a key clue", "the town's safety",
        "a treasure that will be lost forever",
    )
    private val complications = listOf(
        "the obvious villain is innocent", "the guards are bribed", "it's a distraction for something worse",
        "a party member's past is involved", "there's a time limit", "the victim doesn't want to be saved",
    )

    private val placeAdjs = listOf("Sunken", "Whispering", "Gilded", "Ashen", "Drowned", "Hollow", "Thorned", "Silver", "Broken", "Crimson", "Frozen", "Lantern")
    private val placeNouns = listOf("Abbey", "Crossing", "Market", "Tower", "Mill", "Hollow", "Keep", "Bazaar", "Lighthouse", "Vineyard", "Quarry", "Harbor", "Shrine")
    private val placeTypes = listOf("ruined monastery", "riverside trading post", "haunted manor", "goblin-run toll bridge", "druid grove", "underground market", "mountain pass fort", "smugglers' cove")
    private val impressions = listOf(
        "Fog clings to everything and the air smells of salt and smoke.",
        "Lanterns sway overhead, and every conversation stops when you enter.",
        "Moss covers the old stones, and somewhere water is dripping in a steady rhythm.",
        "The place is too clean, too quiet, and every door is freshly painted.",
        "Colorful banners snap in the wind above a crowd of haggling merchants.",
        "Bones crunch underfoot. Something has been nesting here.",
    )
    private val features = listOf(
        "a statue whose eyes follow you", "a well that hums at night", "a locked door with no keyhole",
        "an enormous dead tree", "a shrine to a forgotten god", "a collapsed tunnel", "a tavern built into a shipwreck",
    )

    private val roomNames = listOf(
        "The Flooded Crypt", "Hall of Broken Mirrors", "The Fungal Garden", "Collapsed Library", "The Chained Altar",
        "Forge of Cold Iron", "Bone Pit", "The Weeping Gallery", "Spider Nursery", "The Clockwork Vault",
    )
    private val readAlouds = listOf(
        "Knee-deep black water fills this chamber. Something brushes past your leg.",
        "Dozens of cracked mirrors line the walls, and your reflections move a moment too late.",
        "Glowing mushrooms the size of trees fill the room with a soft blue light and a sweet smell.",
        "Shelves have toppled like dominoes. Pages drift in a breeze that has no source.",
        "Rusted chains hang from the ceiling over a stone altar stained dark.",
    )
    private val threats = listOf(
        "pressure plate releases a scything blade (DC 14 Dex save, 2d10 slashing)",
        "3 ghouls hide under the water", "a mimic posing as a treasure chest",
        "poison gas vents when the door closes (DC 13 Con save)", "a gelatinous cube patrols the corridor",
        "swarm of rats bursts from the walls", "a sleeping ogre the party can sneak past (DC 13 Stealth)",
    )
    private val treasures = listOf(
        "a pouch with 42 gp and a silver ring", "a potion of healing hidden in a skull", "a scroll of misty step",
        "an ornate dagger worth 75 gp", "a map to the next level", "nothing — it's already been looted",
    )
    private val twists = listOf(
        "the room slowly rotates", "a ghost asks for help", "the exit seals until a riddle is answered",
        "the monsters are guarding something, not attacking", "another party is already here",
    )

    private val itemAdjs = listOf("Whispering", "Gravebound", "Stormcaller's", "Moonlit", "Ember", "Hollow", "Thief's", "Wanderer's", "Starforged", "Briar")
    private val itemNouns = listOf("Blade", "Lantern", "Cloak", "Ring", "Gauntlet", "Compass", "Lute", "Amulet", "Boots", "Quill", "Shield", "Mask")
    private val itemProps = listOf(
        "once per long rest, cast misty step", "+1 to attack and damage rolls", "advantage on Stealth checks in dim light",
        "glows when undead are within 60 feet", "resistance to fire damage", "can speak with animals at will",
        "once per day, reroll a failed saving throw", "deals an extra 1d6 lightning damage on a hit",
        "lets you breathe underwater", "has 3 charges; spend 1 to cast shield",
    )
    private val quirks = listOf(
        "it hums when lies are told nearby", "it's always slightly warm", "it whispers its previous owner's name",
        "it hates being put down", "it smells of lavender", "it occasionally shows you a stranger's memory",
    )

    private val beatNames = listOf(
        "Betrayal from Within", "The Map Is Wrong", "An Old Friend Returns", "The Villain Makes an Offer",
        "Race Against the Eclipse", "The Ally Was the Key", "Fall of the City Gate", "A Prophecy Misread",
        "The Heist", "Into the Underdark",
    )

    fun generate(type: IdeaType, count: Int): List<Card> = List(count) { one(type) }

    private fun one(type: IdeaType): Card = when (type) {
        IdeaType.NPC -> {
            val name = "${firstNames.pick()} ${surnames.pick()}"
            Card(
                name,
                listOf(
                    "Role: ${ancestries.pick()} ${roles.pick()}",
                    "Look: ${looks.pick()}",
                    "Personality: ${personalities.pick()}",
                    "Wants: ${wants.pick()}",
                    "Secret: ${secrets.pick()}",
                    "Voice: ${voices.pick()}",
                ).joinToString("\n"),
                "npc",
            )
        }
        IdeaType.EVENT -> Card(
            eventNames.pick(),
            listOf(
                "Trigger: ${triggers.pick()}",
                "What happens: ${happenings.pick()}",
                "Stakes: ${stakes.pick()}",
                "Complication: ${complications.pick()}",
            ).joinToString("\n"),
            "event",
        )
        IdeaType.LOCATION -> Card(
            "The ${placeAdjs.pick()} ${placeNouns.pick()}",
            listOf(
                "Type: ${placeTypes.pick()}",
                "First impression: ${impressions.pick()}",
                "Notable feature: ${features.pick()}",
                "Who's here: ${firstNames.pick()}, a ${roles.pick()} who ${secrets.pick()}",
            ).joinToString("\n"),
            "location",
        )
        IdeaType.DUNGEON_ROOM -> Card(
            roomNames.pick(),
            listOf(
                "Read-aloud: ${readAlouds.pick()}",
                "Threat: ${threats.pick()}",
                "Treasure: ${treasures.pick()}",
                "Exits: ${rng.nextInt(1, 4)} (${listOf("north", "east", "south", "west").shuffled(rng).take(2).joinToString(", ")})",
                "Twist: ${twists.pick()}",
            ).joinToString("\n"),
            "dungeon",
        )
        IdeaType.MAGIC_ITEM -> Card(
            "${itemAdjs.pick()} ${itemNouns.pick()}",
            listOf(
                "Rarity: ${listOf("Uncommon", "Uncommon", "Rare", "Rare", "Very rare").pick()}",
                "Attunement: ${if (rng.nextBoolean()) "yes" else "no"}",
                "Properties: ${itemProps.pick()}",
                "Quirk: ${quirks.pick()}",
            ).joinToString("\n"),
            "magic item",
        )
        IdeaType.STORY_BEAT -> Card(
            beatNames.pick(),
            listOf(
                "What happens: ${happenings.pick()}",
                "Why it matters: it puts ${stakes.pick()} at risk",
                "Complication: ${complications.pick()}",
            ).joinToString("\n"),
            "story",
        )
        IdeaType.NOTE -> Card("Idea", happenings.pick())
    }

    fun tableEntries(name: String, count: Int): List<String> {
        val n = name.lowercase()
        val pool: List<String> = when {
            "npc" in n || "name" in n -> List(count) { "${firstNames.pick()} ${surnames.pick()}, ${ancestries.pick()} ${roles.pick()}" }
            "loot" in n || "treasure" in n || "item" in n -> (treasures + itemProps.map { "${itemAdjs.pick()} ${itemNouns.pick()}: $it" })
            "event" in n || "encounter" in n -> happenings + threats
            "rumor" in n || "rumour" in n -> secrets.map { "They say ${firstNames.pick()} $it." }
            "room" in n || "dungeon" in n -> readAlouds + twists
            else -> happenings + features + complications
        }
        val shuffled = pool.shuffled(rng)
        return List(count) { i -> if (i < shuffled.size) shuffled[i] else pool.pick() }
    }
}
