package app.yapper.data

data class PublicUser(
    val id: Long,
    val username: String,
    val displayName: String,
    val about: String,
    val status: String,
    val customStatus: String,
    val accentColor: Long,
    val createdAt: Long,
)

fun UserEntity.toPublic() = PublicUser(
    id = id,
    username = username,
    displayName = displayName,
    about = about,
    status = status,
    customStatus = customStatus,
    accentColor = accentColor,
    createdAt = createdAt,
)

data class ServerSummary(
    @androidx.room.Embedded val server: ServerEntity,
    val unread: Int,
)

data class ChannelSummary(
    @androidx.room.Embedded val channel: ChannelEntity,
    val unread: Int,
    val voiceCount: Int,
)

data class RoleColorRow(
    val userId: Long,
    val color: Long,
    val position: Int,
)

data class MemberJoinRow(
    val userId: Long,
    val displayName: String,
    val username: String,
    val status: String,
    val customStatus: String,
    val accentColor: Long,
    val roleName: String?,
    val roleColor: Long?,
    val rolePosition: Int?,
)

data class VoicePresence(
    val channelId: Long,
    val userId: Long,
    val displayName: String,
    val username: String,
    val accentColor: Long,
    val status: String,
    val selfMuted: Boolean,
    val selfDeafened: Boolean,
)

data class MessageBundle(
    @androidx.room.Embedded val message: MessageEntity,
    @androidx.room.Relation(parentColumn = "authorId", entityColumn = "id")
    val author: UserEntity,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "messageId")
    val reactions: List<ReactionEntity>,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "messageId")
    val attachments: List<AttachmentEntity>,
)

data class InboxRow(
    val channelId: Long,
    val title: String,
    val subtitle: String,
    val accent: Long,
    val status: String?,
    val letter: String,
    val unread: Int,
    val lastActivityAt: Long,
)

data class FriendRow(
    val user: PublicUser,
    val incoming: Boolean,
    val outgoing: Boolean,
    val accepted: Boolean,
)

data class MemberUi(
    val userId: Long,
    val displayName: String,
    val username: String,
    val status: String,
    val customStatus: String,
    val accentColor: Long,
    val roleName: String?,
    val roleColor: Long,
)

data class DatabaseReport(
    val users: Int,
    val servers: Int,
    val channels: Int,
    val messages: Int,
    val friendships: Int,
    val attachments: Int,
    val tables: List<String>,
)

data class PendingAttachment(
    val name: String,
    val mime: String,
    val path: String,
)

data class ChatContext(
    val channel: ChannelEntity?,
    val me: PublicUser?,
    val members: List<PublicUser>,
    val roleColors: Map<Long, Long>,
    val canModerate: Boolean,
    val accessible: Boolean,
)

enum class Relation {
    NONE,
    FRIENDS,
    INCOMING,
    OUTGOING,
}

sealed class Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>()
    data class Err(val message: String) : Outcome<Nothing>()
}
