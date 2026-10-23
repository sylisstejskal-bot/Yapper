@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package app.yapper.data

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class YapperRepository(
    context: Context,
    private val db: YapperDatabase,
    private val prefs: SharedPreferences,
) {
    private val app = context.applicationContext
    private val dao = db.dao()
    private val session = MutableStateFlow(prefs.getLong(KEY_USER, -1L).takeIf { it > 0 })

    fun currentUserId(): Long? = session.value

    fun requireUserId(): Long = currentUserId() ?: error("Sign in first")

    fun observeSession(): Flow<PublicUser?> = session.flatMapLatest { id ->
        if (id == null) flowOf(null) else dao.observeUserList(id).map { rows -> rows.firstOrNull()?.toPublic() }
    }

    suspend fun seedIfNeeded() {
        if (dao.userCount() > 0) return
        db.withTransaction { SeedData.insert(dao) }
    }

    suspend fun databaseReport(): DatabaseReport = DatabaseReport(
        users = dao.userCount(),
        servers = dao.countServers(),
        channels = dao.countChannels(),
        messages = dao.countMessages(),
        friendships = dao.countFriendships(),
        attachments = dao.countAttachments(),
        tables = dao.tableNames(),
    )

    suspend fun register(usernameRaw: String, displayRaw: String, password: String): Outcome<Unit> {
        val usernameError = Usernames.validate(usernameRaw)
        if (usernameError != null) return Outcome.Err(usernameError)
        val username = Usernames.normalize(usernameRaw)
        val display = displayRaw.trim().ifBlank { username }
        if (display.length !in 1..32) return Outcome.Err("Display name should be 1–32 characters")
        if (password.length !in 6..100) return Outcome.Err("Use at least 6 characters for a password")
        if (dao.findUserByUsername(username) != null) return Outcome.Err("That username is taken")
        val salt = PasswordHasher.newSalt()
        val hash = withContext(Dispatchers.Default) { PasswordHasher.hash(password, salt) }
        val id = try {
            dao.insertUser(
                UserEntity(
                    username = username,
                    displayName = display,
                    passwordHash = hash,
                    passwordSalt = salt,
                    about = "",
                    status = Presence.ONLINE,
                    customStatus = "",
                    accentColor = BLURPLE,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        } catch (_: SQLiteConstraintException) {
            return Outcome.Err("That username is taken")
        }
        startSession(id)
        return Outcome.Ok(Unit)
    }

    suspend fun login(usernameRaw: String, password: String): Outcome<Unit> {
        val user = dao.findUserByUsername(Usernames.normalize(usernameRaw))
            ?: return Outcome.Err("No user with that name")
        val ok = withContext(Dispatchers.Default) {
            PasswordHasher.verify(password, user.passwordSalt, user.passwordHash)
        }
        if (!ok) return Outcome.Err("Wrong password")
        startSession(user.id)
        return Outcome.Ok(Unit)
    }

    fun logout() {
        prefs.edit().remove(KEY_USER).apply()
        session.value = null
    }

    suspend fun updateProfile(
        displayName: String,
        about: String,
        customStatus: String,
        status: String,
        accentColor: Long,
    ): Outcome<Unit> {
        val name = displayName.trim()
        if (name.length !in 1..32) return Outcome.Err("Display name should be 1–32 characters")
        if (about.trim().length > Limits.ABOUT) return Outcome.Err("About me stops at ${Limits.ABOUT} characters")
        if (customStatus.trim().length > Limits.STATUS) {
            return Outcome.Err("Custom status stops at ${Limits.STATUS} characters")
        }
        if (status !in Presence.all) return Outcome.Err("Unknown status")
        dao.updateProfile(requireUserId(), name, about.trim(), customStatus.trim(), status, accentColor)
        return Outcome.Ok(Unit)
    }

    suspend fun changePassword(current: String, next: String): Outcome<Unit> {
        if (next.length !in 6..100) return Outcome.Err("Use at least 6 characters for a password")
        val user = dao.findUserById(requireUserId()) ?: return Outcome.Err("Missing account")
        val matches = withContext(Dispatchers.Default) {
            PasswordHasher.verify(current, user.passwordSalt, user.passwordHash)
        }
        if (!matches) return Outcome.Err("Current password is wrong")
        val salt = PasswordHasher.newSalt()
        val hash = withContext(Dispatchers.Default) { PasswordHasher.hash(next, salt) }
        dao.updatePassword(user.id, hash, salt)
        return Outcome.Ok(Unit)
    }

    fun observeServers(): Flow<List<ServerSummary>> = session.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else dao.observeServerSummaries(id)
    }

    fun observeServer(serverId: Long): Flow<ServerEntity?> = dao.observeServerList(serverId).map { it.firstOrNull() }

    fun observeCategories(serverId: Long): Flow<List<CategoryEntity>> = dao.observeCategories(serverId)

    fun observeChannelSummaries(serverId: Long): Flow<List<ChannelSummary>> = session.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else dao.observeChannelSummaries(serverId, id)
    }

    fun observeServerVoice(serverId: Long): Flow<List<VoicePresence>> = dao.observeServerVoice(serverId)

    fun observeMemberCount(serverId: Long): Flow<Int> = dao.observeMemberCount(serverId)

    fun observeInviteCode(serverId: Long): Flow<String?> =
        dao.observePrimaryInvite(serverId).map { invites -> invites.firstOrNull()?.code }

    fun observeMembers(serverId: Long): Flow<List<MemberUi>> {
        return dao.observeMemberJoinRows(serverId).map { rows ->
            rows.groupBy { it.userId }.map { (_, copies) ->
                val top = copies.maxBy { it.rolePosition ?: -1 }
                val color = top.roleColor ?: DEFAULT_ROLE_COLOR
                MemberUi(
                    userId = top.userId,
                    displayName = top.displayName,
                    username = top.username,
                    status = top.status,
                    customStatus = top.customStatus,
                    accentColor = top.accentColor,
                    roleName = top.roleName,
                    roleColor = color,
                )
            }.sortedWith(compareBy({ statusRank(it.status) }, { it.displayName.lowercase() }))
        }
    }

    suspend fun createServer(rawName: String): Outcome<Long> {
        val name = rawName.trim()
        if (name.length !in 2..40) return Outcome.Err("Server name should be 2–40 characters")
        val me = requireUserId()
        val now = System.currentTimeMillis()
        val serverId = db.withTransaction {
            val id = dao.insertServer(
                ServerEntity(
                    name = name,
                    description = "",
                    iconLetter = name.take(1).uppercase(),
                    accentColor = BLURPLE,
                    ownerId = me,
                    createdAt = now,
                ),
            )
            val founder = dao.insertRole(RoleEntity(serverId = id, name = "Founder", color = 0xFFF0B232, position = 10))
            dao.insertRole(RoleEntity(serverId = id, name = "Moderator", color = 0xFF57F287, position = MOD_POSITION))
            val member = dao.insertRole(RoleEntity(serverId = id, name = "Member", color = DEFAULT_ROLE_COLOR, position = 1))
            dao.insertMember(ServerMemberEntity(id, me, now))
            dao.insertMemberRole(MemberRoleEntity(id, me, founder))
            dao.insertMemberRole(MemberRoleEntity(id, me, member))
            val textCategory = dao.insertCategory(CategoryEntity(serverId = id, name = "Text Channels", position = 0))
            val voiceCategory = dao.insertCategory(CategoryEntity(serverId = id, name = "Voice Channels", position = 1))
            dao.insertChannel(
                ChannelEntity(serverId = id, categoryId = textCategory, name = "general", topic = "Talk about anything", kind = ChannelKind.TEXT, position = 0, lastActivityAt = now),
            )
            dao.insertChannel(
                ChannelEntity(serverId = id, categoryId = voiceCategory, name = "Lounge", topic = "", kind = ChannelKind.VOICE, position = 1, lastActivityAt = now),
            )
            val code = uniqueInviteCode(name)
            dao.insertInvite(InviteEntity(code, id, me, now))
            id
        }
        return Outcome.Ok(serverId)
    }

    suspend fun joinServer(rawCode: String): Outcome<Long> {
        val code = rawCode.trim().lowercase()
        if (code.isBlank()) return Outcome.Err("Enter an invite code")
        val invite = dao.findInvite(code) ?: return Outcome.Err("That invite code doesn't exist")
        val me = requireUserId()
        if (dao.serverMemberCount(invite.serverId, me) > 0) return Outcome.Ok(invite.serverId)
        val roleId = dao.defaultRoleId(invite.serverId)
        db.withTransaction {
            dao.insertMember(ServerMemberEntity(invite.serverId, me, System.currentTimeMillis()))
            if (roleId != null) dao.insertMemberRole(MemberRoleEntity(invite.serverId, me, roleId))
        }
        return Outcome.Ok(invite.serverId)
    }

    suspend fun leaveServer(serverId: Long): Outcome<Unit> {
        val me = requireUserId()
        val server = dao.getServer(serverId) ?: return Outcome.Err("Server is gone")
        if (server.ownerId == me) return Outcome.Err("Owners can't leave. Delete the server instead.")
        db.withTransaction {
            dao.deleteVoiceForUserInServer(me, serverId)
            dao.deleteMemberRolesForUser(serverId, me)
            dao.deleteMember(serverId, me)
        }
        return Outcome.Ok(Unit)
    }

    suspend fun deleteServer(serverId: Long): Outcome<Unit> {
        val me = requireUserId()
        val server = dao.getServer(serverId) ?: return Outcome.Err("Server is gone")
        if (server.ownerId != me) return Outcome.Err("Only the owner can delete this server")
        val paths = dao.attachmentPathsForServer(serverId)
        db.withTransaction { dao.deleteServer(serverId) }
        paths.forEach(::discardFile)
        return Outcome.Ok(Unit)
    }

    suspend fun kick(serverId: Long, userId: Long): Outcome<Unit> {
        val me = requireUserId()
        val server = dao.getServer(serverId) ?: return Outcome.Err("Missing server")
        if (server.ownerId != me) return Outcome.Err("Only the owner can kick people")
        if (userId == me) return Outcome.Err("You can't kick yourself")
        db.withTransaction {
            dao.deleteVoiceForUserInServer(userId, serverId)
            dao.deleteMemberRolesForUser(serverId, userId)
            dao.deleteMember(serverId, userId)
        }
        return Outcome.Ok(Unit)
    }

    suspend fun createChannel(serverId: Long, rawName: String, kind: String): Outcome<Long> {
        if (kind != ChannelKind.TEXT && kind != ChannelKind.VOICE) return Outcome.Err("Unknown channel type")
        val me = requireUserId()
        if (!isModerator(serverId, me)) return Outcome.Err("You can't add channels here")
        val name = if (kind == ChannelKind.TEXT) ChannelNames.slug(rawName) else rawName.trim().take(40)
        if (name.isBlank()) return Outcome.Err("Give the channel a name")
        val categories = dao.getCategories(serverId)
        val categoryId = pickCategory(categories, kind)
        val position = dao.maxChannelPosition(serverId) + 1
        val id = dao.insertChannel(
            ChannelEntity(
                serverId = serverId,
                categoryId = categoryId,
                name = name,
                topic = "",
                kind = kind,
                position = position,
                lastActivityAt = System.currentTimeMillis(),
            ),
        )
        return Outcome.Ok(id)
    }

    suspend fun deleteChannel(channelId: Long): Outcome<Unit> {
        val channel = dao.getChannel(channelId) ?: return Outcome.Err("Channel is gone")
        val serverId = channel.serverId ?: return Outcome.Err("Direct messages stay until you leave them")
        if (!isModerator(serverId, requireUserId())) return Outcome.Err("You can't delete that channel")
        val paths = dao.attachmentPathsForChannel(channelId)
        db.withTransaction { dao.deleteChannel(channelId) }
        paths.forEach(::discardFile)
        return Outcome.Ok(Unit)
    }

    fun observeChatContext(channelId: Long): Flow<ChatContext> {
        return observeSession().flatMapLatest { me ->
            dao.observeChannelList(channelId).flatMapLatest { channels ->
                val channel = channels.firstOrNull()
                if (me == null || channel == null) {
                    flowOf(ChatContext(channel, me, emptyList(), emptyMap(), canModerate = false, accessible = false))
                } else {
                    val people = if (channel.serverId == null) {
                        dao.observeChannelMembers(channel.id).map { users -> users.map { it.toPublic() } }
                    } else {
                        dao.observeServerUsers(channel.serverId).map { users -> users.map { it.toPublic() } }
                    }
                    val colors = if (channel.serverId == null) {
                        flowOf(emptyMap())
                    } else {
                        dao.observeRoleColorRows(channel.serverId).map { rows ->
                            rows.groupBy { it.userId }.mapValues { (_, items) -> items.maxBy { it.position }.color }
                        }
                    }
                    val access = if (channel.serverId == null) {
                        dao.observeChannelMemberCount(channel.id, me.id).map { it > 0 }
                    } else {
                        dao.observeServerMemberCount(channel.serverId, me.id).map { it > 0 }
                    }
                    val moderator = if (channel.serverId == null) {
                        flowOf(false)
                    } else {
                        combine(
                            dao.observeServerList(channel.serverId),
                            dao.observeMaxRolePosition(channel.serverId, me.id),
                        ) { servers, position ->
                            servers.firstOrNull()?.ownerId == me.id || position >= MOD_POSITION
                        }
                    }
                    combine(people, colors, access, moderator) { members, roleColors, ok, mod ->
                        ChatContext(channel, me, members, roleColors, mod, ok)
                    }
                }
            }
        }
    }

    fun observeBundles(channelId: Long): Flow<List<MessageBundle>> = dao.observeRecentMessages(channelId)

    fun searchBundles(channelId: Long, raw: String): Flow<List<MessageBundle>> {
        return dao.searchMessages(channelId, SearchQuery.toLikePattern(raw))
    }

    suspend fun markRead(channelId: Long, messageId: Long) {
        val me = currentUserId() ?: return
        dao.upsertRead(ReadStateEntity(me, channelId, messageId))
    }

    suspend fun sendMessage(
        channelId: Long,
        content: String,
        replyToId: Long?,
        attachment: PendingAttachment?,
    ): Outcome<Unit> {
        val me = requireUserId()
        val channel = dao.getChannel(channelId) ?: return Outcome.Err("Channel is gone")
        if (!canAccess(channel, me)) return Outcome.Err("You don't have access to this channel")
        if (channel.kind == ChannelKind.VOICE) return Outcome.Err("Voice channels don't take messages")
        val text = content.trim()
        if (text.isEmpty() && attachment == null) return Outcome.Err("Write a message first")
        if (text.length > Limits.MESSAGE) return Outcome.Err("Messages stop at ${Limits.MESSAGE} characters")
        if (replyToId != null) {
            val reply = dao.getMessage(replyToId)
            if (reply == null || reply.channelId != channelId) return Outcome.Err("That reply target is gone")
        }
        val now = System.currentTimeMillis()
        db.withTransaction {
            val id = dao.insertMessage(
                MessageEntity(
                    channelId = channelId,
                    authorId = me,
                    content = text,
                    replyToId = replyToId,
                    createdAt = now,
                    editedAt = null,
                    pinned = false,
                    deleted = false,
                ),
            )
            if (attachment != null) {
                dao.insertAttachment(
                    AttachmentEntity(
                        messageId = id,
                        fileName = attachment.name,
                        mime = attachment.mime,
                        path = attachment.path,
                    ),
                )
            }
            dao.touchChannel(channelId, now)
            dao.upsertRead(ReadStateEntity(me, channelId, id))
        }
        return Outcome.Ok(Unit)
    }

    suspend fun editMessage(id: Long, content: String): Outcome<Unit> {
        val message = dao.getMessage(id) ?: return Outcome.Err("Message is gone")
        if (message.authorId != requireUserId()) return Outcome.Err("You can only edit your own messages")
        if (message.deleted) return Outcome.Err("That message was deleted")
        val text = content.trim()
        if (text.isEmpty()) return Outcome.Err("Message can't be empty")
        if (text.length > Limits.MESSAGE) return Outcome.Err("Messages stop at ${Limits.MESSAGE} characters")
        dao.editMessage(id, text, System.currentTimeMillis())
        return Outcome.Ok(Unit)
    }

    suspend fun deleteMessage(id: Long): Outcome<Unit> {
        val me = requireUserId()
        val message = dao.getMessage(id) ?: return Outcome.Err("Message is gone")
        val channel = dao.getChannel(message.channelId) ?: return Outcome.Err("Channel is gone")
        val allowed = message.authorId == me || (channel.serverId != null && isModerator(channel.serverId, me))
        if (!allowed) return Outcome.Err("You can't delete that message")
        val paths = dao.attachmentPathsForMessage(id)
        db.withTransaction {
            dao.deleteAttachmentsForMessage(id)
            dao.deleteReactionsForMessage(id)
            dao.softDeleteMessage(id)
        }
        paths.forEach(::discardFile)
        return Outcome.Ok(Unit)
    }

    suspend fun togglePin(id: Long): Outcome<Unit> {
        val me = requireUserId()
        val message = dao.getMessage(id) ?: return Outcome.Err("Message is gone")
        if (message.deleted) return Outcome.Err("Deleted messages stay unpinned")
        val channel = dao.getChannel(message.channelId) ?: return Outcome.Err("Channel is gone")
        val allowed = message.authorId == me || (channel.serverId != null && isModerator(channel.serverId, me))
        if (!allowed) return Outcome.Err("You can't pin that")
        dao.setPinned(id, !message.pinned)
        return Outcome.Ok(Unit)
    }

    suspend fun toggleReaction(messageId: Long, emoji: String): Outcome<Unit> {
        if (emoji.isBlank() || emoji.length > 16) return Outcome.Err("Pick an emoji")
        val me = requireUserId()
        val message = dao.getMessage(messageId) ?: return Outcome.Err("Message is gone")
        if (message.deleted) return Outcome.Err("Reactions stay on live messages")
        val channel = dao.getChannel(message.channelId) ?: return Outcome.Err("Channel is gone")
        if (!canAccess(channel, me)) return Outcome.Err("You don't have access to this channel")
        if (dao.reactionCount(messageId, me, emoji) > 0) {
            dao.deleteReaction(messageId, me, emoji)
        } else {
            dao.insertReaction(ReactionEntity(messageId, me, emoji))
        }
        return Outcome.Ok(Unit)
    }

    suspend fun importAttachment(uri: Uri): Outcome<PendingAttachment> = withContext(Dispatchers.IO) {
        val name = queryDisplayName(uri)
        val mime = app.contentResolver.getType(uri) ?: "application/octet-stream"
        val dir = File(app.filesDir, "attachments").apply { mkdirs() }
        val dest = File(dir, UUID.randomUUID().toString())
        val input = app.contentResolver.openInputStream(uri)
            ?: return@withContext Outcome.Err("Couldn't open that file")
        input.use { stream ->
            dest.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > Limits.ATTACHMENT_BYTES) {
                        dest.delete()
                        return@withContext Outcome.Err("Attachments are free up to 8 MB on this phone")
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
        Outcome.Ok(PendingAttachment(name = name, mime = mime, path = dest.absolutePath))
    }

    fun discardFile(path: String) {
        val root = File(app.filesDir, "attachments").canonicalFile
        val file = File(path)
        if (!file.exists()) return
        val canonical = file.canonicalFile
        if (canonical.path.startsWith(root.path) && canonical.isFile) canonical.delete()
    }

    fun observeInbox(): Flow<List<InboxRow>> = observeSession().flatMapLatest { me ->
        if (me == null) {
            flowOf(emptyList())
        } else {
            dao.observeDmChannels(me.id).flatMapLatest { channels ->
                if (channels.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combineInbox(channels.map { channel -> inboxItem(me, channel) })
                }
            }
        }
    }

    fun observeDmUnread(): Flow<Int> = session.flatMapLatest { id ->
        if (id == null) flowOf(0) else dao.observeDmUnread(id)
    }

    fun observeServerUnread(): Flow<Int> = session.flatMapLatest { id ->
        if (id == null) flowOf(0) else dao.observeServerUnread(id)
    }

    fun observePendingCount(): Flow<Int> = session.flatMapLatest { id ->
        if (id == null) flowOf(0) else dao.observePendingCount(id)
    }

    fun observeFriends(): Flow<List<FriendRow>> = session.flatMapLatest { id ->
        if (id == null) {
            flowOf(emptyList())
        } else {
            val accepted = combine(dao.observeFriendsIRequested(id), dao.observeFriendsWhoRequested(id)) { a, b ->
                (a + b).distinctBy { it.id }
            }
            combine(accepted, dao.observeIncomingRequests(id), dao.observeOutgoingRequests(id)) { friends, incoming, outgoing ->
                buildList {
                    friends.forEach { add(FriendRow(it, incoming = false, outgoing = false, accepted = true)) }
                    incoming.forEach { add(FriendRow(it, incoming = true, outgoing = false, accepted = false)) }
                    outgoing.forEach { add(FriendRow(it, incoming = false, outgoing = true, accepted = false)) }
                }.sortedBy { it.user.displayName.lowercase() }
            }
        }
    }

    suspend fun requestFriend(rawUsername: String): Outcome<Unit> {
        val username = Usernames.normalize(rawUsername)
        val error = Usernames.validate(username)
        if (error != null) return Outcome.Err(error)
        val me = requireUserId()
        val other = dao.findUserByUsername(username) ?: return Outcome.Err("Nobody goes by @$username")
        if (other.id == me) return Outcome.Err("That's you")
        val existing = dao.friendshipBetween(me, other.id)
        if (existing != null) {
            return when (existing.status) {
                FriendStatus.ACCEPTED -> Outcome.Err("You're already friends")
                FriendStatus.PENDING -> Outcome.Err("There's already a request waiting")
                else -> Outcome.Err("Can't add them")
            }
        }
        return try {
            dao.insertFriendship(
                FriendshipEntity(
                    requesterId = me,
                    addresseeId = other.id,
                    pairKey = friendshipPairKey(me, other.id),
                    status = FriendStatus.PENDING,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            Outcome.Ok(Unit)
        } catch (_: SQLiteConstraintException) {
            Outcome.Err("There's already a request waiting")
        }
    }

    suspend fun acceptFriend(otherId: Long): Outcome<Unit> {
        val me = requireUserId()
        val row = dao.friendshipBetween(me, otherId) ?: return Outcome.Err("No request")
        if (row.status != FriendStatus.PENDING || row.addresseeId != me) return Outcome.Err("Nothing to accept")
        dao.updateFriendshipStatus(row.id, FriendStatus.ACCEPTED)
        return Outcome.Ok(Unit)
    }

    suspend fun declineOrCancel(otherId: Long): Outcome<Unit> {
        val me = requireUserId()
        val row = dao.friendshipBetween(me, otherId) ?: return Outcome.Err("Nothing waiting")
        val mine = row.requesterId == me || row.addresseeId == me
        if (!mine) return Outcome.Err("Nothing waiting")
        dao.deleteFriendship(row.id)
        return Outcome.Ok(Unit)
    }

    suspend fun openDm(otherId: Long): Outcome<Long> {
        val me = requireUserId()
        if (otherId == me) return Outcome.Err("That's you")
        val relation = dao.friendshipBetween(me, otherId)
        if (relation?.status != FriendStatus.ACCEPTED) return Outcome.Err("Add them as a friend first")
        val existing = dao.findDm(me, otherId)
        if (existing != null) return Outcome.Ok(existing.id)
        val now = System.currentTimeMillis()
        val id = db.withTransaction {
            val channelId = dao.insertChannel(
                ChannelEntity(
                    serverId = null,
                    categoryId = null,
                    name = "",
                    topic = "",
                    kind = ChannelKind.DM,
                    position = 0,
                    lastActivityAt = now,
                ),
            )
            dao.insertChannelMember(ChannelMemberEntity(channelId, me))
            dao.insertChannelMember(ChannelMemberEntity(channelId, otherId))
            channelId
        }
        return Outcome.Ok(id)
    }

    suspend fun createGroup(rawName: String, memberIds: List<Long>): Outcome<Long> {
        val me = requireUserId()
        val others = memberIds.distinct().filter { it != me }
        if (others.size < 2) return Outcome.Err("Pick at least two friends")
        for (other in others) {
            val relation = dao.friendshipBetween(me, other)
            if (relation?.status != FriendStatus.ACCEPTED) return Outcome.Err("Everyone in the group needs to be a friend")
        }
        val name = rawName.trim().ifBlank {
            val users = others.mapNotNull { dao.findUserById(it)?.displayName }
            users.joinToString(", ").take(40)
        }.take(40)
        val now = System.currentTimeMillis()
        val id = db.withTransaction {
            val channelId = dao.insertChannel(
                ChannelEntity(
                    serverId = null,
                    categoryId = null,
                    name = name,
                    topic = "",
                    kind = ChannelKind.GROUP_DM,
                    position = 0,
                    lastActivityAt = now,
                ),
            )
            dao.insertChannelMember(ChannelMemberEntity(channelId, me))
            others.forEach { dao.insertChannelMember(ChannelMemberEntity(channelId, it)) }
            channelId
        }
        return Outcome.Ok(id)
    }

    fun observeVoice(channelId: Long): Flow<List<VoicePresence>> = dao.observeVoiceForChannel(channelId)

    fun observeChannelName(channelId: Long): Flow<String> =
        dao.observeChannelList(channelId).map { it.firstOrNull()?.name ?: "Voice" }

    suspend fun joinVoice(channelId: Long): Outcome<Unit> {
        val me = requireUserId()
        val channel = dao.getChannel(channelId) ?: return Outcome.Err("Channel is gone")
        if (channel.kind != ChannelKind.VOICE) return Outcome.Err("That isn't a voice lounge")
        if (!canAccess(channel, me)) return Outcome.Err("You don't have access to this lounge")
        db.withTransaction {
            dao.deleteVoiceForUser(me)
            dao.insertVoice(
                VoiceStateEntity(
                    channelId = channelId,
                    userId = me,
                    selfMuted = false,
                    selfDeafened = false,
                    joinedAt = System.currentTimeMillis(),
                ),
            )
        }
        return Outcome.Ok(Unit)
    }

    suspend fun leaveVoice(channelId: Long) {
        dao.deleteVoice(channelId, requireUserId())
    }

    suspend fun toggleMute(channelId: Long) {
        val me = requireUserId()
        val state = dao.getVoiceState(channelId, me) ?: return
        if (state.selfDeafened) return
        dao.updateVoiceFlags(channelId, me, muted = !state.selfMuted, deafened = false)
    }

    suspend fun toggleDeafen(channelId: Long) {
        val me = requireUserId()
        val state = dao.getVoiceState(channelId, me) ?: return
        dao.updateVoiceFlags(channelId, me, muted = state.selfMuted, deafened = !state.selfDeafened)
    }

    suspend fun findUser(userId: Long): PublicUser? = dao.findUserById(userId)?.toPublic()

    fun observeUser(userId: Long): Flow<PublicUser?> =
        dao.observeUserList(userId).map { rows -> rows.firstOrNull()?.toPublic() }

    fun observeRelation(otherId: Long): Flow<Relation> = observeFriends().map { rows ->
        val row = rows.firstOrNull { it.user.id == otherId }
        when {
            row == null -> Relation.NONE
            row.accepted -> Relation.FRIENDS
            row.incoming -> Relation.INCOMING
            else -> Relation.OUTGOING
        }
    }

    private fun startSession(userId: Long) {
        prefs.edit().putLong(KEY_USER, userId).apply()
        session.value = userId
    }

    private suspend fun isModerator(serverId: Long, userId: Long): Boolean {
        val server = dao.getServer(serverId) ?: return false
        return server.ownerId == userId || dao.maxRolePosition(serverId, userId) >= MOD_POSITION
    }

    private suspend fun canAccess(channel: ChannelEntity, userId: Long): Boolean {
        return if (channel.serverId == null) {
            dao.channelMemberCount(channel.id, userId) > 0
        } else {
            dao.serverMemberCount(channel.serverId, userId) > 0
        }
    }

    private suspend fun uniqueInviteCode(name: String): String {
        val base = name.lowercase().replace(Regex("[^a-z0-9]+"), "").take(12).ifBlank { "hangout" }
        var code = base
        var suffix = 0
        while (dao.findInvite(code) != null) {
            suffix += 1
            code = "$base$suffix"
        }
        return code
    }

    private fun pickCategory(categories: List<CategoryEntity>, kind: String): Long? {
        if (categories.isEmpty()) return null
        val voiceCategory = categories.firstOrNull { it.name.contains("voice", ignoreCase = true) }
        val textCategory = categories.firstOrNull { !it.name.contains("voice", ignoreCase = true) }
        return if (kind == ChannelKind.VOICE) {
            (voiceCategory ?: categories.last()).id
        } else {
            (textCategory ?: categories.first()).id
        }
    }

    private fun inboxItem(me: PublicUser, channel: ChannelEntity): Flow<InboxRow> {
        return combine(
            dao.observeLatestMessages(channel.id),
            dao.observeChannelMembers(channel.id),
            dao.observeUnreadCount(channel.id, me.id),
        ) { latest, members, unread ->
            val others = members.filter { it.id != me.id }
            val title = if (channel.kind == ChannelKind.GROUP_DM) {
                channel.name.ifBlank { others.joinToString { it.displayName } }
            } else {
                others.firstOrNull()?.displayName ?: "Direct message"
            }
            val last = latest.firstOrNull()
            val subtitle = when {
                last == null -> "No messages yet"
                last.authorId == me.id -> "You: ${last.content}"
                channel.kind == ChannelKind.GROUP_DM -> {
                    val author = members.firstOrNull { it.id == last.authorId }?.displayName ?: "Message"
                    "$author: ${last.content}"
                }
                else -> last.content
            }
            InboxRow(
                channelId = channel.id,
                title = title,
                subtitle = subtitle.replace('\n', ' ').take(80),
                accent = if (channel.kind == ChannelKind.DM) others.firstOrNull()?.accentColor ?: BLURPLE else BLURPLE,
                status = if (channel.kind == ChannelKind.DM) others.firstOrNull()?.status else null,
                letter = title.take(1).uppercase(),
                unread = unread,
                lastActivityAt = maxOf(channel.lastActivityAt, last?.createdAt ?: 0),
            )
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)
                if (!name.isNullOrBlank()) return name
            }
        }
        return "attachment"
    }

    companion object {
        private const val KEY_USER = "user_id"
    }
}

private fun combineInbox(flows: List<Flow<InboxRow>>): Flow<List<InboxRow>> {
    val seed: Flow<List<InboxRow>> = flowOf(emptyList())
    val merged: Flow<List<InboxRow>> = flows.fold(seed) { acc, next ->
        combine(acc, next) { rows, row -> rows + row }
    }
    return merged.map { rows -> rows.sortedByDescending { row -> row.lastActivityAt } }
}

private fun statusRank(status: String): Int = when (status) {
    Presence.ONLINE -> 0
    Presence.IDLE -> 1
    Presence.DND -> 2
    else -> 3
}
