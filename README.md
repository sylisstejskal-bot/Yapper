# Yapper

Yapper is an Android chat app in the shape of Discord: servers, categories, text channels, voice lounges, direct messages, group chats, friends, roles, reactions, replies, pins, edits, and attachments. There is no paid tier. If a feature is in the app, every account can use it.

The app is local. Every account you create on the phone shares one SQLite database, so you can log out, sign in as someone else, and see the other side of the same conversation. It does not talk to a server, and voice lounges track who is sitting in them without opening the microphone.

## Install

The debug APK is `dist/yapper.apk`. It is signed with the debug key, so Android will ask you to allow installs from your files app or browser.

```bash
adb install -r dist/yapper.apk
```

On a phone: copy the APK over, open it, and allow the install.

## Demo logins

Every seeded account uses the password `yapper`.

| Username | Who they are |
| --- | --- |
| demo | Owner of Yapper HQ. Has unread messages. |
| nova | Runs Night Owls. |
| pixel | Sitting muted in the Chill voice lounge. |
| moss | Moderator in Yapper HQ, also in that lounge. |
| juniper | Hosts Film Club. |
| rio | Not in a server yet. Has a pending friend request to demo. |

Invite codes already in the database: `yapper`, `owls`, `reels`.

## Is the database any good?

There was no database in this repo, only a one-line README. This is a new schema, reviewed below. The SQL shape is in `database/schema.sql`. Room creates the same tables from `app/src/main/java/app/yapper/data/Entities.kt`. The You tab shows live row counts after you sign in.

It is a **good client database** and a **bad production multiplayer database**. That is the right level for an APK that runs with no backend. Don't throw the model away, and don't pretend two phones can share it.

What is solid:

- Users, membership, and roles are separate. A person's highest role (by `position`) is what colors their name, and position 5 or higher can moderate. The owner is also a column, so kicking the founder is not required to know who owns the server.
- One `messages` table serves server channels and DMs. Replies, reactions, pins, edits, and attachments all work the same way.
- Reactions are `(message, user, emoji)`, so one person can add many emoji but cannot stack the same one.
- Read state is per user per channel (`lastReadMessageId`), not a boolean stuck on the message. Unread counts fall out of that.
- Friendships store one row per pair (`pairKey` is `minId:maxId`, unique) plus who asked and whether it is pending.
- Invites are codes that point at a server. Joining inserts a membership row. It does not copy the server.
- Deletes cascade through foreign keys. Messages are soft-deleted so a reply still has something to point at.
- Attachments store a file path, not a blob, so the message table stays small.
- Voice state is presence (`selfMuted`, `selfDeafened`), separate from the channel row.
- Passwords are PBKDF2-HMAC-SHA256 with a per-user salt. The demo password is still easy to guess on purpose.

What I would not call good enough yet:

- Nothing syncs. There is no change cursor, outbox, or server id. Two phones do not see each other. A real backend wants this schema moved to Postgres, message ids that are not local autoincrement, and object storage for attachments.
- Search is `LIKE '%text%'`. That is fine for a phone. Past a lot of messages it needs FTS5.
- Roles are a color and a position, not Discord's permission bitset and channel overwrites.
- `lastActivityAt` on a channel is copied data. It has to be updated on every send. It is a cache, not a source of truth.
- The password hash lives on the same phone as the app. That stops casual peeking at the row. It does not protect an account from someone who has the device.
- Voice is not audio. The table is ready for a call, but this build does not record or transmit one.
- Custom emoji images are not a table yet. Reactions are Unicode. A later `custom_emojis(serverId, name, path)` table fits the same shape as attachments.

So: the model is normalized where it matters, indexed on the columns the app filters, and honest about being a local store. It is not "too good" for the app you can install. It is not good enough to be Discord's backend, and it does not try to be.

## Rebuild

You need JDK 17+ and an Android SDK with platform 35 and build-tools 35.

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Copy it to `dist/yapper.apk` if you want the same path as the committed build.

Yapper is not affiliated with Discord.
