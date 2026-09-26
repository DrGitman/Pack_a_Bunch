package com.packabunch.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class, ItemEntity::class, PlanSummaryEntity::class],
    version = 5,
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
            .addMigrations(object : androidx.room.migration.Migration(3, 4) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE items_new (id TEXT NOT NULL, projectId TEXT NOT NULL, name TEXT NOT NULL, widthMm INTEGER NOT NULL, depthMm INTEGER NOT NULL, heightMm INTEGER NOT NULL, quantity INTEGER NOT NULL, keepUpright INTEGER NOT NULL, maySupportItems INTEGER NOT NULL, measurementSource TEXT NOT NULL, photoPath TEXT, position INTEGER NOT NULL, shapeGeometry BLOB, visualGeometry BLOB, PRIMARY KEY(projectId,id), FOREIGN KEY(projectId) REFERENCES projects(id) ON DELETE CASCADE)")
                    db.execSQL("INSERT INTO items_new SELECT * FROM items")
                    db.execSQL("DROP TABLE items")
                    db.execSQL("ALTER TABLE items_new RENAME TO items")
                    db.execSQL("CREATE INDEX index_items_projectId ON items(projectId)")
                }
            })
            .addMigrations(object : androidx.room.migration.Migration(4, 5) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // Existing items keep no stored form and are drawn from their names.
                    db.execSQL("ALTER TABLE items ADD COLUMN visualForm TEXT")
                }
            })
            // No fallbackToDestructiveMigration. Losing somebody's packs on an update is
            // not an acceptable failure mode, and a real migration must be written for
            // every schema change. Bumping the version without one should fail loudly here
            // rather than quietly wipe the device.
            .build()
    }
}
