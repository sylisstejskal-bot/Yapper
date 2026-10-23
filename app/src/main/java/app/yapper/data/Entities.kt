package app.yapper.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)],
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val displayName: String,
    val passwordHash: String,
    val passwordSalt: String,
    val about: String,
    val status: String,
    val customStatus: String,
    val accentColor: Long,
    val createdAt: Long,
)

@Entity(
    tableName = "servers",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["ownerId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index("ownerId")],
)
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val iconLetter: String,
    val accentColor: Long,
    val ownerId: Long,
    val createdAt: Long,
)

@Entity(
    tableName = "server_members",
    primaryKeys = ["serverId", "userId"],
    foreignKeys = [
        ForeignKey(ServerEntity::class, ["id"], ["serverId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(UserEntity::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("userId")],
)
data class ServerMemberEntity(
    val serverId: Long,
    val userId: Long,
    val joinedAt: Long,
)

@Entity(
    tableName = "roles",
    foreignKeys = [
        ForeignKey(ServerEntity::class, ["id"], ["serverId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("serverId")],
)
data class RoleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long,
    val name: String,
    val color: Long,
    val position: Int,
)

@Entity(
    tableName = "member_roles",
    primaryKeys = ["serverId", "userId", "roleId"],
    foreignKeys = [
        ForeignKey(ServerEntity::class, ["id"], ["serverId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(UserEntity::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(RoleEntity::class, ["id"], ["roleId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("userId"), Index("roleId")],
)
data class MemberRoleEntity(
    val serverId: Long,
    val userId: Long,
    val roleId: Long,
)

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(ServerEntity::class, ["id"], ["serverId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("serverId")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long,
    val name: String,
    val position: Int,
)

@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(ServerEntity::class, ["id"], ["serverId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(
            CategoryEntity::class,
            ["id"],
            ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("serverId"), Index("categoryId")],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long?,
    val categoryId: Long?,
    val name: String,
    val topic: String,
    val kind: String,
    val position: Int,
    val lastActivityAt: Long,
)

@Entity(
    tableName = "channel_members",
    primaryKeys = ["channelId", "userId"],
    foreignKeys = [
        ForeignKey(ChannelEntity::class, ["id"], ["channelId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(UserEntity::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("userId")],
)
data class ChannelMemberEntity(
    val channelId: Long,
    val userId: Long,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(ChannelEntity::class, ["id"], ["channelId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(UserEntity::class, ["id"], ["authorId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("channelId"),
        Index("authorId"),
        Index(value = ["channelId", "createdAt"]),
    ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: Long,
    val authorId: Long,
    val content: String,
    val replyToId: Long?,
    val createdAt: Long,
    val editedAt: Long?,
    val pinned: Boolean,
    val deleted: Boolean,
)

@Entity(
    tableName = "reactions",
    primaryKeys = ["messageId", "userId", "emoji"],
    foreignKeys = [
        ForeignKey(MessageEntity::class, ["id"], ["messageId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(UserEntity::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("userId")],
)
data class ReactionEntity(
    val messageId: Long,
    val userId: Long,
    val emoji: String,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(MessageEntity::class, ["id"], ["messageId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("messageId")],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val fileName: String,
    val mime: String,
    val path: String,
)

@Entity(
    tableName = "friendships",
    foreignKeys = [
        ForeignKey(UserEntity::class, ["id"], ["requesterId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(UserEntity::class, ["id"], ["addresseeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index(value = ["pairKey"], unique = true),
        Index("requesterId"),
        Index("addresseeId"),
    ],
)
data class FriendshipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requesterId: Long,
    val addresseeId: Long,
    val pairKey: String,
    val status: String,
    val createdAt: Long,
)

@Entity(
    tableName = "read_states",
    primaryKeys = ["userId", "channelId"],
    foreignKeys = [
        ForeignKey(UserEntity::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ChannelEntity::class, ["id"], ["channelId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("channelId")],
)
data class ReadStateEntity(
    val userId: Long,
    val channelId: Long,
    val lastReadMessageId: Long,
)

@Entity(
    tableName = "invites",
    foreignKeys = [
        ForeignKey(ServerEntity::class, ["id"], ["serverId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(UserEntity::class, ["id"], ["inviterId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("serverId"), Index("inviterId")],
)
data class InviteEntity(
    @PrimaryKey val code: String,
    val serverId: Long,
    val inviterId: Long,
    val createdAt: Long,
)

@Entity(
    tableName = "voice_states",
    primaryKeys = ["channelId", "userId"],
    foreignKeys = [
        ForeignKey(ChannelEntity::class, ["id"], ["channelId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(UserEntity::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("userId")],
)
data class VoiceStateEntity(
    val channelId: Long,
    val userId: Long,
    val selfMuted: Boolean,
    val selfDeafened: Boolean,
    val joinedAt: Long,
)
