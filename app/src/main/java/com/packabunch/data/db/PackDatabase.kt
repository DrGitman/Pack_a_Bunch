package com.packabunch.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class, ItemEntity::class, PlanSummaryEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class PackDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile
        private var instance: PackDatabase? = null
        private val accountInstances = mutableMapOf<String, PackDatabase>()

        fun forAccount(context: Context, userId: String): PackDatabase = synchronized(this) {
            val safeId = java.util.UUID.fromString(userId).toString()
            val prefs = context.getSharedPreferences("local_pack_ownership", Context.MODE_PRIVATE)
            val firstOwner = prefs.getString("legacy_owner", null)
            if (firstOwner == null) check(prefs.edit().putString("legacy_owner", safeId).commit())
            // Preserve pre-account packs for the first authenticated owner; never expose them to another account.
            if (firstOwner == null || firstOwner == safeId) get(context)
            else accountInstances.getOrPut(safeId) { build(context, "packs-$safeId.db") }
        }

        fun get(context: Context): PackDatabase = instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }

        private fun build(context: Context, name: String = "pack-a-bunch.db"): PackDatabase = Room.databaseBuilder(
            context.applicationContext,
            PackDatabase::class.java,
            name,
        )
            .addMigrations(object : androidx.room.migration.Migration(1, 2) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE projects ADD COLUMN scanGeometry BLOB")
                    db.execSQL("ALTER TABLE items ADD COLUMN shapeGeometry BLOB")
                    db.execSQL("ALTER TABLE items ADD COLUMN visualGeometry BLOB")
                }
            })
            .addMigrations(object : androidx.room.migration.Migration(2, 3) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE plan_summaries ADD COLUMN planDetails BLOB")
                }
            })
            // No fallbackToDestructiveMigration. Losing somebody's packs on an update is
            // not an acceptable failure mode, and a real migration must be written for
            // every schema change. Bumping the version without one should fail loudly here
            // rather than quietly wipe the device.
            .build()
    }
}
