package com.dontstudyy.autolog.trip

/** 엔진이 쓰는 저장소. 구현체는 Room(앱)과 InMemory(테스트). */
interface TripStore {
    /** 마지막 기록의 '주행 후'. 첫 실행이면 기준값. 아직 아무것도 없으면 null. */
    var lastEndTenths: Int?

    fun lastDone(): Trip?
    fun inProgress(): Trip?
    fun all(): List<Trip>
    fun insert(trip: Trip): Trip
    fun update(trip: Trip)
    fun delete(id: Long)
}

class InMemoryTripStore : TripStore {
    override var lastEndTenths: Int? = null
    private val trips = mutableListOf<Trip>()
    private var nextId = 1L

    override fun lastDone(): Trip? = trips.lastOrNull { it.status == TripStatus.DONE }
    override fun inProgress(): Trip? = trips.lastOrNull { it.status == TripStatus.IN_PROGRESS }
    override fun all(): List<Trip> = trips.toList()
    override fun insert(trip: Trip): Trip = trip.copy(id = nextId++).also { trips += it }
    override fun update(trip: Trip) {
        val i = trips.indexOfFirst { it.id == trip.id }
        require(i >= 0) { "없는 운행 id=${trip.id}" }
        trips[i] = trip
    }
    override fun delete(id: Long) {
        trips.removeAll { it.id == id }
    }
}
