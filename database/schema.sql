-- Yapper client database, SQLite via Room (version 1).
-- This file documents the schema. The Android app creates it from the
-- Room entities in app/src/main/java/app/yapper/data/Entities.kt.
-- Foreign keys are ON. Passwords are PBKDF2-HMAC-SHA256, never plaintext.

CREATE TABLE users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT NOT NULL UNIQUE,
  displayName TEXT NOT NULL,
  passwordHash TEXT NOT NULL,
  passwordSalt TEXT NOT NULL,
  about TEXT NOT NULL,
  status TEXT NOT NULL,          -- online | idle | dnd | offline
  customStatus TEXT NOT NULL,
  accentColor INTEGER NOT NULL,
  createdAt INTEGER NOT NULL
);

CREATE TABLE servers (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  description TEXT NOT NULL,
  iconLetter TEXT NOT NULL,
  accentColor INTEGER NOT NULL,
  ownerId INTEGER NOT NULL REFERENCES users(id),
  createdAt INTEGER NOT NULL
);

CREATE TABLE server_members (
  serverId INTEGER NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
  userId INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  joinedAt INTEGER NOT NULL,
  PRIMARY KEY (serverId, userId)
);

CREATE TABLE roles (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  serverId INTEGER NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  color INTEGER NOT NULL,
  position INTEGER NOT NULL      -- higher position wins for name color and moderation
);

CREATE TABLE member_roles (
  serverId INTEGER NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
  userId INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  roleId INTEGER NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  PRIMARY KEY (serverId, userId, roleId)
);

CREATE TABLE categories (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  serverId INTEGER NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  position INTEGER NOT NULL
);

-- serverId is NULL for direct and group messages. One message table serves both.
CREATE TABLE channels (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  serverId INTEGER REFERENCES servers(id) ON DELETE CASCADE,
  categoryId INTEGER REFERENCES categories(id) ON DELETE SET NULL,
  name TEXT NOT NULL,
  topic TEXT NOT NULL,
  kind TEXT NOT NULL,            -- TEXT | VOICE | DM | GROUP_DM
  position INTEGER NOT NULL,
  lastActivityAt INTEGER NOT NULL
);

CREATE TABLE channel_members (
  channelId INTEGER NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
  userId INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  PRIMARY KEY (channelId, userId)
);

CREATE TABLE messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  channelId INTEGER NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
  authorId INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  content TEXT NOT NULL,
  replyToId INTEGER,             -- no FK: a reply can outlive a hard delete
  createdAt INTEGER NOT NULL,
  editedAt INTEGER,
  pinned INTEGER NOT NULL,
  deleted INTEGER NOT NULL       -- soft delete keeps reply targets
);

CREATE TABLE reactions (
  messageId INTEGER NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  userId INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  emoji TEXT NOT NULL,
  PRIMARY KEY (messageId, userId, emoji)
);

CREATE TABLE attachments (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  messageId INTEGER NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  fileName TEXT NOT NULL,
  mime TEXT NOT NULL,
  path TEXT NOT NULL             -- file in app storage, not a blob in the row
);

CREATE TABLE friendships (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  requesterId INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  addresseeId INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  pairKey TEXT NOT NULL UNIQUE,  -- min(id):max(id), so a pair can only have one row
  status TEXT NOT NULL,          -- PENDING | ACCEPTED
  createdAt INTEGER NOT NULL
);

CREATE TABLE read_states (
  userId INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  channelId INTEGER NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
  lastReadMessageId INTEGER NOT NULL,
  PRIMARY KEY (userId, channelId)
);

CREATE TABLE invites (
  code TEXT PRIMARY KEY,
  serverId INTEGER NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
  inviterId INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  createdAt INTEGER NOT NULL
);

CREATE TABLE voice_states (
  channelId INTEGER NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
  userId INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  selfMuted INTEGER NOT NULL,
  selfDeafened INTEGER NOT NULL,
  joinedAt INTEGER NOT NULL,
  PRIMARY KEY (channelId, userId)
);

CREATE INDEX index_messages_channel_time ON messages(channelId, createdAt);
