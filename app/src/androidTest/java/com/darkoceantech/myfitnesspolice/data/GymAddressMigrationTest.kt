package com.darkoceantech.myfitnesspolice.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class GymAddressMigrationTest {
    @Test fun migrate19KeepsGymsRelationshipsAndReloadsInternationalAddresses() = runBlocking {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val name = "gym-address-" + UUID.randomUUID() + ".db"
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val schema = JSONObject(instrument.context.assets.open("com.darkoceantech.myfitnesspolice.data.FitnessDatabase/19.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i); val placeholder = "$" + "{TABLE_NAME}"
                old.execSQL(entity.getString("createSql").replace(placeholder, entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace(placeholder, entity.getString("tableName")))
            }
            old.execSQL("INSERT INTO gyms (id,name,isArchived) VALUES ('g','Old gym',0)")
            old.execSQL("INSERT INTO equipment (id,type,name,makeModel,gymId,isArchived) VALUES ('e','Cable','Station','Model','g',0)")
            old.version = 19
        }
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).addMigrations(FitnessDatabase.MIGRATION_19_20).build()
        var db = open()
        try {
            assertEquals(emptyList<String>(), db.equipmentLibraryDao().gym("g")!!.addressLines())
            assertEquals("g", db.equipmentLibraryDao().equipment("e")!!.gymId)
            val address = listOf("12 Rue de la Paix", "Étage 2", "Paris", "", "75002", "France")
            EquipmentLibraryRepository(db).saveGym("g", "Paris gym", address)
            val partial = EquipmentLibraryRepository(db).saveGym(null, "Tokyo", listOf("", "", "東京都", "", "", "日本"))
            EquipmentLibraryRepository(db).saveGym("g", "Renamed gym")
            db.close(); db = open()
            assertEquals(address.filter(String::isNotBlank), db.equipmentLibraryDao().gym("g")!!.addressLines())
            assertEquals(listOf("東京都", "日本"), db.equipmentLibraryDao().gym(partial)!!.addressLines())
            assertEquals("g", db.equipmentLibraryDao().equipment("e")!!.gymId)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}