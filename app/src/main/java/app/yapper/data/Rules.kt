package app.yapper.data

object Presence {
    const val ONLINE = "online"
    const val IDLE = "idle"
    const val DND = "dnd"
    const val OFFLINE = "offline"
    val all = listOf(ONLINE, IDLE, DND, OFFLINE)
}

object ChannelKind {
    const val TEXT = "TEXT"
    const val VOICE = "VOICE"
    const val DM = "DM"
    const val GROUP_DM = "GROUP_DM"
}

object FriendStatus {
    const val PENDING = "PENDING"
    const val ACCEPTED = "ACCEPTED"
}

object Limits {
    const val MESSAGE = 2000
    const val ABOUT = 190
    const val STATUS = 128
    const val ATTACHMENT_BYTES = 8 * 1024 * 1024
}

const val DEFAULT_ROLE_COLOR = 0xFF949BA4L
const val BLURPLE = 0xFF5865F2L
const val MOD_POSITION = 5

object DemoAccounts {
    const val PASSWORD = "yapper"
    val usernames = listOf("demo", "nova", "pixel", "moss", "juniper", "rio")
}

object Usernames {
    private val pattern = Regex("^[a-z0-9_]{2,20}$")

    fun normalize(raw: String): String = raw.trim().lowercase()

    fun validate(raw: String): String? {
        val name = normalize(raw)
        if (!pattern.matches(name)) return "Use 2–20 letters, numbers, or underscores"
        return null
    }
}

object ChannelNames {
    fun slug(raw: String): String {
        return raw.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
    }
}

object SearchQuery {
    fun toLikePattern(raw: String): String {
        val cleaned = raw.trim().replace("%", "").replace("_", "").replace("\\", "")
        return "%$cleaned%"
    }
}

fun friendshipPairKey(a: Long, b: Long): String {
    val low = minOf(a, b)
    val high = maxOf(a, b)
    return "$low:$high"
}
