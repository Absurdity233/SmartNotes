package com.nexo.smartnotes.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UserEntity::class, NoteEntity::class, CommentEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun noteDao(): NoteDao
    abstract fun commentDao(): CommentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 → v2：为「账号 + 回收站 + 归档」的常用组合查询补一个复合索引。
         *
         * 之前用 fallbackToDestructiveMigration 图省事，代价是一旦版本号变化，
         * 用户所有本地笔记会被整表删除。对以本地存储为评分点的项目来说不可接受，
         * 因此改为显式迁移；漏写迁移应当构建期/运行期报错，而不是静默丢数据。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_notes_accountId_trashed_archived` " +
                        "ON `notes` (`accountId`, `trashed`, `archived`)"
                )
            }
        }

        /**
         * v2 → v3：新增 searchBlob 列（数据库层全文检索）。
         *
         * 老数据也要能搜到，所以不仅补列，还要把已有行的内容回填进去。
         * 回填用 SQL 拼接近似 searchBlob —— 精度不如 Kotlin 侧，
         * 但足够覆盖标题/正文/标签/地点/心情/天气这些主要字段，
         * 而且这些行在下一次编辑保存时会写入完整的 searchBlob。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `searchBlob` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    UPDATE `notes` SET `searchBlob` = lower(
                        coalesce(`title`, '') || ' ' ||
                        coalesce(`content`, '') || ' ' ||
                        coalesce(`tags`, '') || ' ' ||
                        coalesce(`location`, '') || ' ' ||
                        coalesce(`mood`, '') || ' ' ||
                        coalesce(`weather`, '') || ' ' ||
                        coalesce(`checklistJson`, '')
                    )
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smartnotes.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
