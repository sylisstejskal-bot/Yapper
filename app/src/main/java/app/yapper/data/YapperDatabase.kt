package app.yapper.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        ServerEntity::class,
        ServerMemberEntity::class,
        RoleEntity::class,
        MemberRoleEntity::class,
        CategoryEntity::class,
        ChannelEntity::class,
        ChannelMemberEntity::class,
        MessageEntity::class,
        ReactionEntity::class,
        AttachmentEntity::class,
        FriendshipEntity::class,
        ReadStateEntity::class,
        InviteEntity::class,
        VoiceStateEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class YapperDatabase : RoomDatabase() {
    abstract fun dao(): YapperDao

    companion object {
        fun create(context: Context): YapperDatabase {
            return Room.databaseBuilder(context, YapperDatabase::class.java, "yapper.db").build()
        }
    }
}
