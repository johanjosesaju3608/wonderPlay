package com.wonderplay.download
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="downloads")
data class DownloadEntry(@PrimaryKey val id:String, val payload:String, val generation:String, val status:String="Queued", val bytes:Long=0, val total:Long=0, val file:String="", val artwork:String="", val license:String="", val page:String="", val error:String="")
@Dao
interface DownloadDao {
 @Query("SELECT * FROM downloads ORDER BY rowid DESC") fun observe():Flow<List<DownloadEntry>>
 @Query("SELECT * FROM downloads") suspend fun all():List<DownloadEntry>
 @Query("SELECT * FROM downloads WHERE id=:id") suspend fun get(id:String):DownloadEntry?
 @Upsert suspend fun put(value:DownloadEntry)
 @Query("DELETE FROM downloads WHERE id=:id") suspend fun delete(id:String)
 @Query("UPDATE downloads SET status=:status, bytes=:bytes, total=:total, error=:error WHERE id=:id AND generation=:generation AND status NOT IN ('Cancelled','Ready')")
 suspend fun progress(id:String,generation:String,status:String,bytes:Long,total:Long,error:String=""):Int
 @Query("UPDATE downloads SET status='Ready', bytes=:bytes, total=:bytes, file=:file, artwork=:artwork, license=:license, page=:page, error='' WHERE id=:id AND generation=:generation AND status='Downloading'")
 suspend fun complete(id:String,generation:String,bytes:Long,file:String,artwork:String,license:String,page:String):Int
}
@Database(entities=[DownloadEntry::class],version=1,exportSchema=true)
abstract class DownloadDatabase:RoomDatabase() {abstract fun downloads():DownloadDao}
