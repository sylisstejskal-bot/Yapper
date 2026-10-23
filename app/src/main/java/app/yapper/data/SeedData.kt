package app.yapper.data

/**
 * First-launch world. Every seeded account uses [DemoAccounts.PASSWORD].
 * The rows are inserted in one transaction by [YapperRepository.seedIfNeeded].
 */
object SeedData {
    suspend fun insert(dao: YapperDao) {
        val now = System.currentTimeMillis()
        val people = listOf(
            person("demo", "Demo", Presence.ONLINE, "building yapper", BLURPLE, "I keep the lights on in Yapper HQ."),
            person("nova", "Nova", Presence.ONLINE, "in a queue", 0xFF57F287, "Two-stack, not a grind."),
            person("pixel", "Pixel", Presence.IDLE, "listening", 0xFFFEE75C, "I will memorialize the missing comma."),
            person("moss", "Moss", Presence.DND, "afk but not really", 0xFFED4245, "Moderator energy, free of charge."),
            person("juniper", "Juniper", Presence.ONLINE, "spoilers on sunday", 0xFFEB459E, "Film club host. Endings wait."),
            person("rio", "Rio", Presence.OFFLINE, "new here", 0xFF00A8FC, "Trying to get into a server."),
        ).map { it.hashed() }

        val ids = people.associate { spec ->
            spec.username to dao.insertUser(
                UserEntity(
                    username = spec.username,
                    displayName = spec.displayName,
                    passwordHash = spec.hash,
                    passwordSalt = spec.salt,
                    about = spec.about,
                    status = spec.status,
                    customStatus = spec.customStatus,
                    accentColor = spec.accent,
                    createdAt = now,
                ),
            )
        }
        val demo = ids.getValue("demo")
        val nova = ids.getValue("nova")
        val pixel = ids.getValue("pixel")
        val moss = ids.getValue("moss")
        val juniper = ids.getValue("juniper")
        val rio = ids.getValue("rio")

        suspend fun friends(a: Long, b: Long) {
            dao.insertFriendship(
                FriendshipEntity(
                    requesterId = a,
                    addresseeId = b,
                    pairKey = friendshipPairKey(a, b),
                    status = FriendStatus.ACCEPTED,
                    createdAt = now,
                ),
            )
        }
        friends(demo, nova)
        friends(demo, pixel)
        friends(demo, moss)
        friends(demo, juniper)
        friends(nova, pixel)
        dao.insertFriendship(
            FriendshipEntity(
                requesterId = rio,
                addresseeId = demo,
                pairKey = friendshipPairKey(rio, demo),
                status = FriendStatus.PENDING,
                createdAt = now,
            ),
        )

        val writer = Writer(dao)
        val hq = makeServer(
            dao = dao,
            name = "Yapper HQ",
            description = "Home base. Nothing here is paid.",
            accent = BLURPLE,
            ownerId = demo,
            founderColor = 0xFFF0B232,
            now = now,
        )
        val owls = makeServer(
            dao = dao,
            name = "Night Owls",
            description = "Late games, low volume.",
            accent = 0xFF9B59B6,
            ownerId = nova,
            founderName = "Captain",
            founderColor = 0xFFA78BFA,
            now = now,
        )
        val film = makeServer(
            dao = dao,
            name = "Film Club",
            description = "One movie, no endings until Sunday.",
            accent = 0xFFEB459E,
            ownerId = juniper,
            founderName = "Host",
            founderColor = 0xFFEB459E,
            now = now,
        )

        val hqMembers = listOf(demo, nova, pixel, moss, juniper)
        val owlMembers = listOf(nova, demo, pixel, moss, juniper)
        val filmMembers = listOf(juniper, demo, nova, moss)
        joinAll(dao, hq, hqMembers, now)
        joinAll(dao, owls, owlMembers, now)
        joinAll(dao, film, filmMembers, now)
        dao.insertMemberRole(MemberRoleEntity(hq.serverId, moss, hq.modRoleId))

        val info = dao.insertCategory(CategoryEntity(serverId = hq.serverId, name = "Information", position = 0))
        val hangout = dao.insertCategory(CategoryEntity(serverId = hq.serverId, name = "Hangout", position = 1))
        val hqVoice = dao.insertCategory(CategoryEntity(serverId = hq.serverId, name = "Voice", position = 2))
        val start = dao.insertCategory(CategoryEntity(serverId = owls.serverId, name = "Start Here", position = 0))
        val owlHang = dao.insertCategory(CategoryEntity(serverId = owls.serverId, name = "Hangout", position = 1))
        val owlVoice = dao.insertCategory(CategoryEntity(serverId = owls.serverId, name = "Voice", position = 2))
        val watch = dao.insertCategory(CategoryEntity(serverId = film.serverId, name = "Watch", position = 0))
        val filmVoice = dao.insertCategory(CategoryEntity(serverId = film.serverId, name = "Voice", position = 1))

        val welcome = text(dao, hq.serverId, info, "welcome", "Read this once. Then go say hi.", 0)
        val announcements = text(dao, hq.serverId, info, "announcements", "Shipped things, not ads.", 1)
        val general = text(dao, hq.serverId, hangout, "general", "The room everyone actually uses.", 2)
        val memes = text(dao, hq.serverId, hangout, "memes", "Comma crimes and other exhibits.", 3)
        voice(dao, hq.serverId, hqVoice, "Lounge", 4)
        voice(dao, hq.serverId, hqVoice, "Late Night", 5)

        val rules = text(dao, owls.serverId, start, "rules", "Short on purpose.", 0)
        val lfg = text(dao, owls.serverId, start, "lfg", "Who is playing, and how serious.", 1)
        val offTopic = text(dao, owls.serverId, owlHang, "off-topic", "No ranked talk after midnight.", 2)
        voice(dao, owls.serverId, owlVoice, "Ranked", 3)
        val chill = voice(dao, owls.serverId, owlVoice, "Chill", 4)

        val nowWatching = text(dao, film.serverId, watch, "now-watching", "Logistics only. Endings live next door.", 0)
        val spoilers = text(dao, film.serverId, watch, "spoilers", "Closed until Sunday.", 1)
        voice(dao, film.serverId, filmVoice, "Watch Party", 2)

        writer.clock = now - 20 * 60 * 60 * 1000
        writer.say(welcome, moss, "Welcome to Yapper HQ. If a feature exists, it is free. No boosts, no tiers.")
        writer.say(welcome, demo, "Accounts on this phone share one database. Log out and open Nova if you want the other side of a conversation.")
        writer.say(welcome, juniper, "Invite code is yapper.")

        writer.say(announcements, demo, "Voice lounges remember who is sitting in them. Pixel and Moss are already in Night Owls.")
        writer.say(announcements, nova, "Reactions, replies, pins, edits, and attachments are all on the house.")

        writer.say(nowWatching, juniper, "This week's pick is the rainy-city detective movie. Keep endings out of this channel.")
        writer.say(nowWatching, demo, "I have only seen the poster, so I am safe to be here.")
        writer.say(nowWatching, nova, "Thursday works for me.")
        writer.say(spoilers, juniper, "Spoilers open Sunday. Until then, talk about the weather.")
        writer.say(spoilers, moss, "Understood. It was a foggy ending. Wait, ignore that.")

        val comma = writer.say(memes, pixel, "I lost an hour to a missing comma and I want it memorialized.")
        writer.say(memes, nova, "A classic. We should pin it.")
        writer.say(memes, moss, "Pinned in spirit. I can actually pin it, so I will.")
        writer.say(memes, juniper, "Put it beside the temporary voice channel from March.")
        dao.setPinned(comma, true)

        writer.say(offTopic, juniper, "The credits song is stuck in my head and I am not naming it in spoilers either.")
        writer.say(offTopic, nova, "Coward.")
        writer.say(offTopic, pixel, "I brought snacks. They are conceptual.")
        writer.say(rules, nova, "Be decent. Ranked talk stays in #lfg. Movie endings stay in Film Club.")

        writer.clock = now - 3 * 60 * 60 * 1000
        writer.say(general, pixel, "Who is queueing tonight?")
        val tilting = writer.say(general, nova, "I'm in, but I am one game from tilting.")
        writer.say(general, pixel, "Then we play unranked. @nova", tilting)
        writer.say(general, moss, "Lounge is open. Come muted if your room is loud.")
        val checkpointGeneral = writer.say(general, juniper, "Drop the clip in #memes when you whiff.")
        writer.say(general, demo, "I'll be there after this build.")
        writer.say(general, nova, "Leaving a spot for you.")
        writer.say(general, pixel, "We're starting in ten.")
        writer.say(general, moss, "@everyone I saved you a corner of the voice channel.")

        writer.say(lfg, nova, "Two stack, chill, not a grind.")
        val checkpointLfg = writer.say(lfg, pixel, "I can play in twenty.")
        writer.say(lfg, moss, "Fill. I'm on.")
        writer.say(lfg, nova, "Ranked is optional. Snacks are not.")

        val novaDm = dm(dao, demo, nova, now)
        val pixelDm = dm(dao, demo, pixel, now)
        val group = group(dao, "weekend plans", listOf(demo, moss, juniper), now)

        writer.say(novaDm, nova, "Did free reactions ship?")
        val checkpointNova = writer.say(novaDm, demo, "They were never going to be paid. That was the whole point.")
        writer.say(novaDm, nova, "Good. Then this one doesn't cost anything.")
        writer.say(novaDm, nova, "🎉")
        dao.insertReaction(ReactionEntity(checkpointNova, nova, "🎉"))

        writer.say(pixelDm, pixel, "Mic check later?")
        writer.say(pixelDm, demo, "Night Owls chill is up. Join deafened if you need to.")
        writer.say(pixelDm, pixel, "I live in a loud room. Deafened it is.")

        writer.say(group, moss, "Saturday market, or the late showing?")
        writer.say(group, juniper, "Late showing. I already did the poster homework.")
        writer.say(group, demo, "I'm in if we don't start a second server about it.")
        writer.say(group, moss, "Too late. Film Club exists.")

        writer.lastTime.forEach { (channelId, at) -> dao.touchChannel(channelId, at) }

        val serverText = mapOf(
            hq.serverId to listOf(welcome, announcements, general, memes),
            owls.serverId to listOf(rules, lfg, offTopic),
            film.serverId to listOf(nowWatching, spoilers),
        )
        val serverUsers = mapOf(
            hq.serverId to hqMembers,
            owls.serverId to owlMembers,
            film.serverId to filmMembers,
        )
        for ((serverId, users) in serverUsers) {
            for (channelId in serverText.getValue(serverId)) {
                val last = writer.lastIds[channelId] ?: continue
                for (userId in users) {
                    dao.upsertRead(ReadStateEntity(userId, channelId, last))
                }
            }
        }
        for ((channelId, users) in mapOf(novaDm to listOf(demo, nova), pixelDm to listOf(demo, pixel), group to listOf(demo, moss, juniper))) {
            val last = writer.lastIds[channelId] ?: continue
            for (userId in users) dao.upsertRead(ReadStateEntity(userId, channelId, last))
        }
        dao.upsertRead(ReadStateEntity(demo, general, checkpointGeneral))
        dao.upsertRead(ReadStateEntity(demo, lfg, checkpointLfg))
        dao.upsertRead(ReadStateEntity(demo, novaDm, checkpointNova))

        dao.insertVoice(
            VoiceStateEntity(
                channelId = chill,
                userId = pixel,
                selfMuted = true,
                selfDeafened = false,
                joinedAt = now,
            ),
        )
        dao.insertVoice(
            VoiceStateEntity(
                channelId = chill,
                userId = moss,
                selfMuted = false,
                selfDeafened = false,
                joinedAt = now,
            ),
        )

        dao.insertInvite(InviteEntity("yapper", hq.serverId, demo, now))
        dao.insertInvite(InviteEntity("owls", owls.serverId, nova, now))
        dao.insertInvite(InviteEntity("reels", film.serverId, juniper, now))
    }

    private data class Person(
        val username: String,
        val displayName: String,
        val status: String,
        val customStatus: String,
        val accent: Long,
        val about: String,
    )

    private data class HashedPerson(
        val username: String,
        val displayName: String,
        val status: String,
        val customStatus: String,
        val accent: Long,
        val about: String,
        val hash: String,
        val salt: String,
    )

    private fun person(
        username: String,
        displayName: String,
        status: String,
        customStatus: String,
        accent: Long,
        about: String,
    ) = Person(username, displayName, status, customStatus, accent, about)

    private fun Person.hashed(): HashedPerson {
        val salt = PasswordHasher.newSalt()
        return HashedPerson(
            username, displayName, status, customStatus, accent, about,
            PasswordHasher.hash(DemoAccounts.PASSWORD, salt),
            salt,
        )
    }

    private data class BuiltServer(
        val serverId: Long,
        val founderRoleId: Long,
        val memberRoleId: Long,
        val modRoleId: Long,
    )

    private suspend fun makeServer(
        dao: YapperDao,
        name: String,
        description: String,
        accent: Long,
        ownerId: Long,
        founderColor: Long,
        now: Long,
        founderName: String = "Founder",
    ): BuiltServer {
        val serverId = dao.insertServer(
            ServerEntity(
                name = name,
                description = description,
                iconLetter = name.take(1).uppercase(),
                accentColor = accent,
                ownerId = ownerId,
                createdAt = now,
            ),
        )
        val founder = dao.insertRole(RoleEntity(serverId = serverId, name = founderName, color = founderColor, position = 10))
        val mod = dao.insertRole(RoleEntity(serverId = serverId, name = "Moderator", color = 0xFF57F287, position = MOD_POSITION))
        val member = dao.insertRole(RoleEntity(serverId = serverId, name = "Member", color = DEFAULT_ROLE_COLOR, position = 1))
        dao.insertMemberRole(MemberRoleEntity(serverId, ownerId, founder))
        return BuiltServer(serverId, founder, member, mod)
    }

    private suspend fun joinAll(dao: YapperDao, server: BuiltServer, users: List<Long>, now: Long) {
        for (userId in users) {
            dao.insertMember(ServerMemberEntity(server.serverId, userId, now))
            dao.insertMemberRole(MemberRoleEntity(server.serverId, userId, server.memberRoleId))
        }
    }

    private suspend fun text(
        dao: YapperDao,
        serverId: Long,
        categoryId: Long,
        name: String,
        topic: String,
        position: Int,
    ): Long {
        return dao.insertChannel(
            ChannelEntity(
                serverId = serverId,
                categoryId = categoryId,
                name = name,
                topic = topic,
                kind = ChannelKind.TEXT,
                position = position,
                lastActivityAt = 0,
            ),
        )
    }

    private suspend fun voice(dao: YapperDao, serverId: Long, categoryId: Long, name: String, position: Int): Long {
        return dao.insertChannel(
            ChannelEntity(
                serverId = serverId,
                categoryId = categoryId,
                name = name,
                topic = "",
                kind = ChannelKind.VOICE,
                position = position,
                lastActivityAt = 0,
            ),
        )
    }

    private suspend fun dm(dao: YapperDao, a: Long, b: Long, now: Long): Long {
        val id = dao.insertChannel(
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
        dao.insertChannelMember(ChannelMemberEntity(id, a))
        dao.insertChannelMember(ChannelMemberEntity(id, b))
        return id
    }

    private suspend fun group(dao: YapperDao, name: String, users: List<Long>, now: Long): Long {
        val id = dao.insertChannel(
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
        users.forEach { dao.insertChannelMember(ChannelMemberEntity(id, it)) }
        return id
    }

    private class Writer(val dao: YapperDao) {
        var clock: Long = 0
        val lastIds = mutableMapOf<Long, Long>()
        val lastTime = mutableMapOf<Long, Long>()

        suspend fun say(channel: Long, author: Long, text: String, reply: Long? = null): Long {
            clock += 8 * 60_000L
            val id = dao.insertMessage(
                MessageEntity(
                    channelId = channel,
                    authorId = author,
                    content = text,
                    replyToId = reply,
                    createdAt = clock,
                    editedAt = null,
                    pinned = false,
                    deleted = false,
                ),
            )
            lastIds[channel] = id
            lastTime[channel] = clock
            return id
        }
    }
}
