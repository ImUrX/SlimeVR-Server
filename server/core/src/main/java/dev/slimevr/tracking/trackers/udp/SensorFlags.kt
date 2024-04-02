package dev.slimevr.tracking.trackers.udp

sealed class SensorFlag(val id: UShort, val state: Boolean) {

	data object Unknown : SensorFlag(0u, false)
	class Bno0XX(val flag: Bno0XXFlag, state: Boolean) : SensorFlag(flag.id, state)

	operator fun component1() = id
	operator fun component2() = state
}

enum class Bno0XXFlag(val id: UShort) {
	MAG_ENABLED(1u),
}
