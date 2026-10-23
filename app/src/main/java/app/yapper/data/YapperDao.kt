package app.yapper.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface YapperDao {
    @Query("SELECT COUNT(*) FROM users")
    suspend fun userCount(): Int

    @Query("SELECT COUNT(*) FROM servers")
    suspend fun countServers(): Int

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun countChannels(): Int

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun countMessages(): Int

    @Query("SELECT COUNT(*) FROM friendships")
    suspend fun countFriendships(): Int

    @Query("SELECT COUNT(*) FROM attachments")
    suspend fun countAttachments(): Int

    @Query(
        """
        SELECT name FROM sqlite_master
        WHERE type = 'table'
          AND name NOT LIKE 'sqlite_%'
          AND name NOT LIKE 'android_%'
          AND name NOT LIKE 'room_%'
        ORDER BY name
        """,
    )
    suspend fun tableNames(): List<String>

    @Query("SELECT * FROM users WHERE id = :id")
    fun observeUserList(id: Long): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun findUserById(id: Long): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun findUserByUsername(username: String): UserEntity?

    @Insert
    suspend fun insertUser(user: UserEntity): Long

    @Query(
        """
        UPDATE users
        SET displayName = :displayName, about = :about, customStatus = :customStatus,
            status = :status, accentColor = :accentColor
        WHERE id = :id
        """,
    )
    suspend fun updateProfile(
        id: Long,
        displayName: String,
        about: String,
        customStatus: String,
        status: String,
        accentColor: Long,
    )

    @Query("UPDATE users SET passwordHash = :hash, passwordSalt = :salt WHERE id = :id")
    suspend fun updatePassword(id: Long, hash: String, salt: String)

    @Insert
    suspend fun insertServer(server: ServerEntity): Long

    @Query("SELECT * FROM servers WHERE id = :id")
    fun observeServerList(id: Long): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getServer(id: Long): ServerEntity?

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun deleteServer(id: Long)

    @Query(
        """
        SELECT s.id, s.name, s.description, s.iconLetter, s.accentColor, s.ownerId, s.createdAt,
        (
          SELECT COUNT(*) FROM messages m
          INNER JOIN channels c ON c.id = m.channelId
          LEFT JOIN read_states rs ON rs.channelId = c.id AND rs.userId = :userId
          WHERE c.serverId = s.id AND c.kind = 'TEXT' AND m.deleted = 0
            AND m.authorId != :userId
            AND m.id > IFNULL(rs.lastReadMessageId, 0)
        ) AS unread
        FROM servers s
        INNER JOIN server_members sm ON sm.serverId = s.id AND sm.userId = :userId
        ORDER BY s.name COLLATE NOCASE
        """,
    )
    fun observeServerSummaries(userId: Long): Flow<List<ServerSummary>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMember(member: ServerMemberEntity)

    @Query("DELETE FROM server_members WHERE serverId = :serverId AND userId = :userId")
    suspend fun deleteMember(serverId: Long, userId: Long)

    @Query("SELECT COUNT(*) FROM server_members WHERE serverId = :serverId AND userId = :userId")
    suspend fun serverMemberCount(serverId: Long, userId: Long): Int

    @Query("SELECT COUNT(*) FROM server_members WHERE serverId = :serverId AND userId = :userId")
    fun observeServerMemberCount(serverId: Long, userId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM server_members WHERE serverId = :serverId")
    fun observeMemberCount(serverId: Long): Flow<Int>

    @Query(
        """
        SELECT u.* FROM server_members sm
        JOIN users u ON u.id = sm.userId
        WHERE sm.serverId = :serverId
        ORDER BY u.displayName COLLATE NOCASE
        """,
    )
    fun observeServerUsers(serverId: Long): Flow<List<UserEntity>>

    @Insert
    suspend fun insertRole(role: RoleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemberRole(memberRole: MemberRoleEntity)

    @Query("DELETE FROM member_roles WHERE serverId = :serverId AND userId = :userId")
    suspend fun deleteMemberRolesForUser(serverId: Long, userId: Long)

    @Query(
        """
        SELECT IFNULL(MAX(r.position), 0) FROM member_roles mr
        JOIN roles r ON r.id = mr.roleId
        WHERE mr.serverId = :serverId AND mr.userId = :userId
        """,
    )
    suspend fun maxRolePosition(serverId: Long, userId: Long): Int

    @Query(
        """
        SELECT IFNULL(MAX(r.position), 0) FROM member_roles mr
        JOIN roles r ON r.id = mr.roleId
        WHERE mr.serverId = :serverId AND mr.userId = :userId
        """,
    )
    fun observeMaxRolePosition(serverId: Long, userId: Long): Flow<Int>

    @Query("SELECT id FROM roles WHERE serverId = :serverId ORDER BY position ASC LIMIT 1")
    suspend fun defaultRoleId(serverId: Long): Long?

    @Query(
        """
        SELECT mr.userId AS userId, r.color AS color, r.position AS position
        FROM member_roles mr
        JOIN roles r ON r.id = mr.roleId
        WHERE mr.serverId = :serverId
        """,
    )
    fun observeRoleColorRows(serverId: Long): Flow<List<RoleColorRow>>

    @Query(
        """
        SELECT u.id AS userId, u.displayName AS displayName, u.username AS username,
               u.status AS status, u.customStatus AS customStatus, u.accentColor AS accentColor,
               r.name AS roleName, r.color AS roleColor, r.position AS rolePosition
        FROM server_members sm
        JOIN users u ON u.id = sm.userId
        LEFT JOIN member_roles mr ON mr.serverId = sm.serverId AND mr.userId = sm.userId
        LEFT JOIN roles r ON r.id = mr.roleId
        WHERE sm.serverId = :serverId
        """,
    )
    fun observeMemberJoinRows(serverId: Long): Flow<List<MemberJoinRow>>

    @Insert
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("SELECT * FROM categories WHERE serverId = :serverId ORDER BY position ASC")
    fun observeCategories(serverId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE serverId = :serverId ORDER BY position ASC")
    suspend fun getCategories(serverId: Long): List<CategoryEntity>

    @Insert
    suspend fun insertChannel(channel: ChannelEntity): Long

    @Query("SELECT * FROM channels WHERE id = :id")
    fun observeChannelList(id: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getChannel(id: Long): ChannelEntity?

    @Query("DELETE FROM channels WHERE id = :id")
    suspend fun deleteChannel(id: Long)

    @Query("UPDATE channels SET lastActivityAt = :at WHERE id = :id")
    suspend fun touchChannel(id: Long, at: Long)

    @Query("SELECT IFNULL(MAX(position), -1) FROM channels WHERE serverId = :serverId")
    suspend fun maxChannelPosition(serverId: Long): Int

    @Query(
        """
        SELECT c.id, c.serverId, c.categoryId, c.name, c.topic, c.kind, c.position, c.lastActivityAt,
        (
          SELECT COUNT(*) FROM messages m
          LEFT JOIN read_states rs ON rs.channelId = c.id AND rs.userId = :userId
          WHERE m.channelId = c.id AND m.deleted = 0 AND m.authorId != :userId
            AND m.id > IFNULL(rs.lastReadMessageId, 0)
        ) AS unread,
        (
          SELECT COUNT(*) FROM voice_states v WHERE v.channelId = c.id
        ) AS voiceCount
        FROM channels c
        WHERE c.serverId = :serverId
        ORDER BY c.position ASC, c.id ASC
        """,
    )
    fun observeChannelSummaries(serverId: Long, userId: Long): Flow<List<ChannelSummary>>

    @Query(
        """
        SELECT c.id, c.serverId, c.categoryId, c.name, c.topic, c.kind, c.position, c.lastActivityAt
        FROM channels c
        JOIN channel_members cm ON cm.channelId = c.id AND cm.userId = :userId
        WHERE c.kind IN ('DM', 'GROUP_DM')
        ORDER BY c.lastActivityAt DESC
        """,
    )
    fun observeDmChannels(userId: Long): Flow<List<ChannelEntity>>

    @Query(
        """
        SELECT c.id, c.serverId, c.categoryId, c.name, c.topic, c.kind, c.position, c.lastActivityAt
        FROM channels c
        WHERE c.kind = 'DM'
          AND EXISTS (SELECT 1 FROM channel_members m WHERE m.channelId = c.id AND m.userId = :a)
          AND EXISTS (SELECT 1 FROM channel_members m2 WHERE m2.channelId = c.id AND m2.userId = :b)
        LIMIT 1
        """,
    )
    suspend fun findDm(a: Long, b: Long): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChannelMember(member: ChannelMemberEntity)

    @Query(
        """
        SELECT u.* FROM channel_members cm
        JOIN users u ON u.id = cm.userId
        WHERE cm.channelId = :channelId
        """,
    )
    fun observeChannelMembers(channelId: Long): Flow<List<UserEntity>>

    @Query("SELECT COUNT(*) FROM channel_members WHERE channelId = :channelId AND userId = :userId")
    suspend fun channelMemberCount(channelId: Long, userId: Long): Int

    @Query("SELECT COUNT(*) FROM channel_members WHERE channelId = :channelId AND userId = :userId")
    fun observeChannelMemberCount(channelId: Long, userId: Long): Flow<Int>

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessage(id: Long): MessageEntity?

    @Transaction
    @Query(
        """
        SELECT * FROM messages
        WHERE channelId = :channelId
        ORDER BY createdAt DESC, id DESC
        LIMIT 400
        """,
    )
    fun observeRecentMessages(channelId: Long): Flow<List<MessageBundle>>

    @Transaction
    @Query(
        """
        SELECT * FROM messages
        WHERE channelId = :channelId AND deleted = 0 AND content LIKE :query
        ORDER BY createdAt DESC
        LIMIT 80
        """,
    )
    fun searchMessages(channelId: Long, query: String): Flow<List<MessageBundle>>

    @Query(
        """
        SELECT * FROM messages
        WHERE channelId = :channelId AND deleted = 0
        ORDER BY createdAt DESC, id DESC
        LIMIT 1
        """,
    )
    fun observeLatestMessages(channelId: Long): Flow<List<MessageEntity>>

    @Query("UPDATE messages SET content = :content, editedAt = :editedAt WHERE id = :id")
    suspend fun editMessage(id: Long, content: String, editedAt: Long)

    @Query("UPDATE messages SET deleted = 1, content = '', pinned = 0 WHERE id = :id")
    suspend fun softDeleteMessage(id: Long)

    @Query("UPDATE messages SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query(
        """
        SELECT COUNT(*) FROM messages m
        LEFT JOIN read_states rs ON rs.channelId = :channelId AND rs.userId = :userId
        WHERE m.channelId = :channelId AND m.deleted = 0 AND m.authorId != :userId
          AND m.id > IFNULL(rs.lastReadMessageId, 0)
        """,
    )
    fun observeUnreadCount(channelId: Long, userId: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM messages m
        JOIN channels c ON c.id = m.channelId
        JOIN channel_members cm ON cm.channelId = c.id AND cm.userId = :userId
        LEFT JOIN read_states rs ON rs.channelId = c.id AND rs.userId = :userId
        WHERE c.kind IN ('DM', 'GROUP_DM')
          AND m.deleted = 0
          AND m.authorId != :userId
          AND m.id > IFNULL(rs.lastReadMessageId, 0)
        """,
    )
    fun observeDmUnread(userId: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM messages m
        JOIN channels c ON c.id = m.channelId
        JOIN server_members sm ON sm.serverId = c.serverId AND sm.userId = :userId
        LEFT JOIN read_states rs ON rs.channelId = c.id AND rs.userId = :userId
        WHERE c.kind = 'TEXT'
          AND m.deleted = 0
          AND m.authorId != :userId
          AND m.id > IFNULL(rs.lastReadMessageId, 0)
        """,
    )
    fun observeServerUnread(userId: Long): Flow<Int>

    @Insert
    suspend fun insertAttachment(attachment: AttachmentEntity): Long

    @Query("SELECT path FROM attachments WHERE messageId = :messageId")
    suspend fun attachmentPathsForMessage(messageId: Long): List<String>

    @Query(
        """
        SELECT a.path FROM attachments a
        JOIN messages m ON m.id = a.messageId
        WHERE m.channelId = :channelId
        """,
    )
    suspend fun attachmentPathsForChannel(channelId: Long): List<String>

    @Query(
        """
        SELECT a.path FROM attachments a
        JOIN messages m ON m.id = a.messageId
        JOIN channels c ON c.id = m.channelId
        WHERE c.serverId = :serverId
        """,
    )
    suspend fun attachmentPathsForServer(serverId: Long): List<String>

    @Query("DELETE FROM attachments WHERE messageId = :messageId")
    suspend fun deleteAttachmentsForMessage(messageId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReaction(reaction: ReactionEntity)

    @Query("DELETE FROM reactions WHERE messageId = :messageId AND userId = :userId AND emoji = :emoji")
    suspend fun deleteReaction(messageId: Long, userId: Long, emoji: String)

    @Query("DELETE FROM reactions WHERE messageId = :messageId")
    suspend fun deleteReactionsForMessage(messageId: Long)

    @Query(
        """
        SELECT COUNT(*) FROM reactions
        WHERE messageId = :messageId AND userId = :userId AND emoji = :emoji
        """,
    )
    suspend fun reactionCount(messageId: Long, userId: Long, emoji: String): Int

    @Insert
    suspend fun insertFriendship(friendship: FriendshipEntity): Long

    @Query(
        """
        SELECT * FROM friendships
        WHERE (requesterId = :a AND addresseeId = :b)
           OR (requesterId = :b AND addresseeId = :a)
        LIMIT 1
        """,
    )
    suspend fun friendshipBetween(a: Long, b: Long): FriendshipEntity?

    @Query("UPDATE friendships SET status = :status WHERE id = :id")
    suspend fun updateFriendshipStatus(id: Long, status: String)

    @Query("DELETE FROM friendships WHERE id = :id")
    suspend fun deleteFriendship(id: Long)

    @Query(
        """
        SELECT u.id AS id, u.username AS username, u.displayName AS displayName, u.about AS about,
               u.status AS status, u.customStatus AS customStatus, u.accentColor AS accentColor,
               u.createdAt AS createdAt
        FROM friendships f
        JOIN users u ON u.id = f.addresseeId
        WHERE f.requesterId = :userId AND f.status = 'ACCEPTED'
        """,
    )
    fun observeFriendsIRequested(userId: Long): Flow<List<PublicUser>>

    @Query(
        """
        SELECT u.id AS id, u.username AS username, u.displayName AS displayName, u.about AS about,
               u.status AS status, u.customStatus AS customStatus, u.accentColor AS accentColor,
               u.createdAt AS createdAt
        FROM friendships f
        JOIN users u ON u.id = f.requesterId
        WHERE f.addresseeId = :userId AND f.status = 'ACCEPTED'
        """,
    )
    fun observeFriendsWhoRequested(userId: Long): Flow<List<PublicUser>>

    @Query(
        """
        SELECT u.id AS id, u.username AS username, u.displayName AS displayName, u.about AS about,
               u.status AS status, u.customStatus AS customStatus, u.accentColor AS accentColor,
               u.createdAt AS createdAt
        FROM friendships f
        JOIN users u ON u.id = f.requesterId
        WHERE f.addresseeId = :userId AND f.status = 'PENDING'
        """,
    )
    fun observeIncomingRequests(userId: Long): Flow<List<PublicUser>>

    @Query(
        """
        SELECT u.id AS id, u.username AS username, u.displayName AS displayName, u.about AS about,
               u.status AS status, u.customStatus AS customStatus, u.accentColor AS accentColor,
               u.createdAt AS createdAt
        FROM friendships f
        JOIN users u ON u.id = f.addresseeId
        WHERE f.requesterId = :userId AND f.status = 'PENDING'
        """,
    )
    fun observeOutgoingRequests(userId: Long): Flow<List<PublicUser>>

    @Query("SELECT COUNT(*) FROM friendships WHERE addresseeId = :userId AND status = 'PENDING'")
    fun observePendingCount(userId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRead(state: ReadStateEntity)

    @Insert
    suspend fun insertInvite(invite: InviteEntity)

    @Query("SELECT * FROM invites WHERE code = :code LIMIT 1")
    suspend fun findInvite(code: String): InviteEntity?

    @Query("SELECT * FROM invites WHERE serverId = :serverId ORDER BY createdAt ASC LIMIT 1")
    fun observePrimaryInvite(serverId: Long): Flow<List<InviteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoice(state: VoiceStateEntity)

    @Query("DELETE FROM voice_states WHERE userId = :userId")
    suspend fun deleteVoiceForUser(userId: Long)

    @Query("DELETE FROM voice_states WHERE channelId = :channelId AND userId = :userId")
    suspend fun deleteVoice(channelId: Long, userId: Long)

    @Query(
        """
        DELETE FROM voice_states
        WHERE userId = :userId
          AND channelId IN (SELECT id FROM channels WHERE serverId = :serverId)
        """,
    )
    suspend fun deleteVoiceForUserInServer(userId: Long, serverId: Long)

    @Query(
        """
        UPDATE voice_states
        SET selfMuted = :muted, selfDeafened = :deafened
        WHERE channelId = :channelId AND userId = :userId
        """,
    )
    suspend fun updateVoiceFlags(channelId: Long, userId: Long, muted: Boolean, deafened: Boolean)

    @Query("SELECT * FROM voice_states WHERE channelId = :channelId AND userId = :userId")
    suspend fun getVoiceState(channelId: Long, userId: Long): VoiceStateEntity?

    @Query(
        """
        SELECT v.channelId AS channelId, v.userId AS userId, u.displayName AS displayName,
               u.username AS username, u.accentColor AS accentColor, u.status AS status,
               v.selfMuted AS selfMuted, v.selfDeafened AS selfDeafened
        FROM voice_states v
        JOIN users u ON u.id = v.userId
        WHERE v.channelId = :channelId
        ORDER BY v.joinedAt ASC
        """,
    )
    fun observeVoiceForChannel(channelId: Long): Flow<List<VoicePresence>>

    @Query(
        """
        SELECT v.channelId AS channelId, v.userId AS userId, u.displayName AS displayName,
               u.username AS username, u.accentColor AS accentColor, u.status AS status,
               v.selfMuted AS selfMuted, v.selfDeafened AS selfDeafened
        FROM voice_states v
        JOIN users u ON u.id = v.userId
        JOIN channels c ON c.id = v.channelId
        WHERE c.serverId = :serverId
        ORDER BY v.joinedAt ASC
        """,
    )
    fun observeServerVoice(serverId: Long): Flow<List<VoicePresence>>
}
