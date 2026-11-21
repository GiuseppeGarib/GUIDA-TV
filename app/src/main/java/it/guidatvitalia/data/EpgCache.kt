package it.guidatvitalia.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Cache locale: permette di consultare l'ultimo palinsesto scaricato anche offline. */
class EpgCache(context: Context) : SQLiteOpenHelper(context, "guida_tv_epg.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE channels (id TEXT PRIMARY KEY, name TEXT NOT NULL, icon TEXT)")
        db.execSQL("""CREATE TABLE programs (
            channel_id TEXT NOT NULL, title TEXT NOT NULL, description TEXT, category TEXT,
            start_ms INTEGER NOT NULL, stop_ms INTEGER,
            PRIMARY KEY(channel_id, start_ms, title))""")
        db.execSQL("CREATE INDEX idx_programs_channel_time ON programs(channel_id, start_ms)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { }

    @Synchronized fun replace(data: EpgData) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("channels", null, null)
            writableDatabase.delete("programs", null, null)
            val channelInsert = writableDatabase.compileStatement(
                "INSERT OR REPLACE INTO channels(id,name,icon) VALUES(?,?,?)")
            data.channels.forEach {
                channelInsert.clearBindings()
                channelInsert.bindString(1, it.id); channelInsert.bindString(2, it.name)
                if (it.icon == null) channelInsert.bindNull(3) else channelInsert.bindString(3, it.icon)
                channelInsert.executeInsert()
            }
            val programInsert = writableDatabase.compileStatement(
                "INSERT OR REPLACE INTO programs(channel_id,title,description,category,start_ms,stop_ms) VALUES(?,?,?,?,?,?)")
            data.programs.forEach {
                programInsert.clearBindings()
                programInsert.bindString(1, it.channelId); programInsert.bindString(2, it.title)
                if (it.description == null) programInsert.bindNull(3) else programInsert.bindString(3, it.description)
                if (it.category == null) programInsert.bindNull(4) else programInsert.bindString(4, it.category)
                programInsert.bindLong(5, it.startMillis)
                if (it.stopMillis == null) programInsert.bindNull(6) else programInsert.bindLong(6, it.stopMillis)
                programInsert.executeInsert()
            }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }

    @Synchronized fun read(): EpgData {
        val channels = mutableListOf<EpgChannel>()
        val programs = mutableListOf<EpgProgram>()
        readableDatabase.rawQuery("SELECT id,name,icon FROM channels ORDER BY name", null).use { c ->
            while (c.moveToNext()) channels += EpgChannel(c.getString(0), c.getString(1), c.getString(2))
        }
        readableDatabase.rawQuery("SELECT channel_id,title,description,category,start_ms,stop_ms FROM programs ORDER BY start_ms", null).use { c ->
            while (c.moveToNext()) programs += EpgProgram(
                c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                c.getLong(4), if (c.isNull(5)) null else c.getLong(5))
        }
        return EpgData(channels, programs)
    }
}
