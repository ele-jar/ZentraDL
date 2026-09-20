package com.elejar.ZentraDL.engine

// Phase 0 skeleton. Real EngineClient lands in Phase 1 against
// docs/ENGINE_API.md (in-process InvokeAsync/Dispatch first, TCP+token fallback).
// TODO(P1): OkHttp + kotlinx.serialization models mirroring ENGINE_API §4.
object EngineContract {
    const val EVENT_DONE = "task.done"
    const val EVENT_PROGRESS = "task.progress"
}
