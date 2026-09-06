package com.packabunch.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class, ItemEntity::class, PlanSummaryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PackDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile
        private var instance: PackDatabase? = null

        fun get(context: Context): PackDatabase = instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }

        private fun build(context: Context): PackDatabase = Room.databaseBuilder(
            context.applicationContext,
            PackDatabase::class.java,
            "pack-a-bunch.db",
        )
            // No fallbackToDestructiveMigration. Losing somebody's packs on an update is
            // not an acceptable failure mode, and a real migration must be written for
            // every schema change. Bumping the version without one should fail loudly here
            // rather than quietly wipe the device.
            .build()
    }
}
