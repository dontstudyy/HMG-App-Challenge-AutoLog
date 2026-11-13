package com.dontstudyy.autolog.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.dontstudyy.autolog.classify.Place
import com.dontstudyy.autolog.classify.PlaceKind
import com.dontstudyy.autolog.trip.Category
import com.dontstudyy.autolog.trip.Trip
import com.dontstudyy.autolog.trip.TripKind
import com.dontstudyy.autolog.trip.TripStatus
import com.dontstudyy.autolog.trip.TripStore

@Entity(tableName = "trip")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeMs: Long,
    val endTimeMs: Long?,
    val userId: Long,
    val startTenths: Int,
    val endTenths: Int?,
    val category: String,
    val note: String,
    val kind: String,
    val status: String,
    val startPlace: String,
    val endPlace: String,
    val endLat: Double?,
    val endLon: Double?,
    val tollWon: Int,
    val naviDest: String,
)

@Entity(tableName = "place")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val lat: Double,
    val lon: Double,
    val radiusM: Float,
)

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dept: String,
    val name: String,
)

@Entity(tableName = "setting")
data class SettingEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "value") val value: String,
)

@Dao
interface AutoLogDao {
    @Query("SELECT * FROM trip ORDER BY id")
    fun allTrips(): List<TripEntity>

    @Query("SELECT * FROM trip WHERE status = 'DONE' ORDER BY id DESC LIMIT 1")
    fun lastDone(): TripEntity?

    @Query("SELECT * FROM trip WHERE status = 'IN_PROGRESS' ORDER BY id DESC LIMIT 1")
    fun inProgress(): TripEntity?

    @Insert
    fun insert(t: TripEntity): Long

    @Update
    fun update(t: TripEntity)

    @Query("DELETE FROM trip WHERE id = :id")
    fun delete(id: Long)

    @Query("SELECT * FROM place ORDER BY id")
    fun places(): List<PlaceEntity>

    @Insert
    fun insertPlace(p: PlaceEntity): Long

    @Query("DELETE FROM place WHERE id = :id")
    fun deletePlace(id: Long)

    @Query("SELECT * FROM profile ORDER BY id")
    fun profiles(): List<ProfileEntity>

    @Insert
    fun insertProfile(p: ProfileEntity): Long

    @Query("DELETE FROM profile WHERE id = :id")
    fun deleteProfile(id: Long)

    @Query("SELECT value FROM setting WHERE `key` = :key")
    fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putSetting(s: SettingEntity)

    @Query("DELETE FROM setting WHERE `key` = :key")
    fun removeSetting(key: String)
}

@Database(entities = [TripEntity::class, SettingEntity::class, PlaceEntity::class, ProfileEntity::class], version = 3, exportSchema = false)
abstract class AutoLogDb : RoomDatabase() {
    abstract fun dao(): AutoLogDao

    companion object {
        fun create(ctx: Context): AutoLogDb =
            Room.databaseBuilder(ctx.applicationContext, AutoLogDb::class.java, "autolog.db")
                // 개발 중 스키마 변경은 기록을 지우고 새로 만든다(제출 전 확정). 서비스 시작 시 기준값부터 다시 잡는다.
                .fallbackToDestructiveMigration(true)
                .allowMainThreadQueries() // 데이터가 작고, 엔진 호출을 한 스레드(메인)에 모으기 위해
                .build()
    }
}

private fun TripEntity.toTrip() = Trip(
    id, startTimeMs, endTimeMs, userId, startTenths, endTenths,
    Category.valueOf(category), note, TripKind.valueOf(kind), TripStatus.valueOf(status),
    startPlace, endPlace, endLat, endLon, tollWon, naviDest,
)

private fun Trip.toEntity() = TripEntity(
    id, startTimeMs, endTimeMs, userId, startTenths, endTenths,
    category.name, note, kind.name, status.name,
    startPlace, endPlace, endLat, endLon, tollWon, naviDest,
)

/** 엔진이 쓰는 [TripStore]의 Room 구현. */
class RoomTripStore(private val dao: AutoLogDao) : TripStore {
    override var lastEndTenths: Int?
        get() = dao.getSetting(KEY_LAST_END)?.toIntOrNull()
        set(v) {
            if (v == null) dao.removeSetting(KEY_LAST_END) else dao.putSetting(SettingEntity(KEY_LAST_END, v.toString()))
        }

    override fun lastDone(): Trip? = dao.lastDone()?.toTrip()
    override fun inProgress(): Trip? = dao.inProgress()?.toTrip()
    override fun all(): List<Trip> = dao.allTrips().map { it.toTrip() }
    override fun insert(trip: Trip): Trip = trip.copy(id = dao.insert(trip.toEntity().copy(id = 0)))
    override fun update(trip: Trip) = dao.update(trip.toEntity())
    override fun delete(id: Long) = dao.delete(id)

    private companion object {
        const val KEY_LAST_END = "lastEndTenths"
    }
}

fun PlaceEntity.toPlace() = Place(id, name, PlaceKind.valueOf(kind), lat, lon, radiusM)

fun Place.toEntity() = PlaceEntity(id, name, kind.name, lat, lon, radiusM)

/** 사용자 프로필. 부서와 이름. */
data class Profile(val id: Long = 0, val dept: String, val name: String) {
    val label: String get() = if (dept.isBlank()) name else "$dept $name"
}

fun ProfileEntity.toProfile() = Profile(id, dept, name)
