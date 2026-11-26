package io.github.robin19666.zapretmanager.core

enum class EnginePhase { STOPPED, STARTING, RUNNING, STOPPING, ERROR }

data class EngineState(
    val phase: EnginePhase = EnginePhase.STOPPED,
    val message: String = "",
    val since: Long = 0L,
    val extra: String = "",
) {
    val running: Boolean get() = phase == EnginePhase.RUNNING
    val busy: Boolean get() = phase == EnginePhase.STARTING || phase == EnginePhase.STOPPING
}
