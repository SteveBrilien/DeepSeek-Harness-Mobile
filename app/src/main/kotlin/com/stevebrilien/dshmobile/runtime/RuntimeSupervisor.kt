package com.stevebrilien.dshmobile.runtime

import android.content.Context
import com.stevebrilien.dshmobile.core.runtimeandroid.DshWebPresentationDescriptor
import com.stevebrilien.dshmobile.core.runtimeandroid.RuntimeControlPlane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * App-process runtime state machine.
 *
 * Compose observes this class; it does not implement install/start polling itself. Calls
 * to [ensureStarted] are idempotent, so multiple UI observers cannot fan out duplicate
 * foreground-service operations.
 */
class RuntimeSupervisor(context: Context) : AutoCloseable {
    sealed interface State {
        data object Idle : State
        data class Inspecting(val detail: String = "正在检查本地 Runtime…") : State
        data class Starting(val detail: String) : State
        data class Ready(val presentation: DshWebPresentationDescriptor) : State
        data class Failed(val detail: String) : State
    }

    private val appContext = context.applicationContext
    private val runtime = RuntimeControlPlane(appContext)
    private val telemetry = RuntimeInstallTelemetry(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val attemptCounter = AtomicLong(0L)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var activeAttempt: Job? = null

    fun ensureStarted() {
        synchronized(lock) {
            if (_state.value is State.Ready || activeAttempt?.isActive == true) return
            activeAttempt = launchAttempt(attemptCounter.incrementAndGet())
        }
    }

    fun retry() {
        synchronized(lock) {
            activeAttempt?.cancel()
            activeAttempt = launchAttempt(attemptCounter.incrementAndGet())
        }
    }

    /**
     * Convert a Web-host failure into an explicit native state instead of leaving a blank
     * WebView. Runtime state itself is not mutated here; Retry performs a fresh probe.
     */
    fun reportPresentationFailure(detail: String) {
        val clean = detail.lineSequence().firstOrNull()?.take(220).orEmpty().ifBlank { "DSH 页面未就绪" }
        _state.value = State.Failed(clean)
    }

    private fun launchAttempt(attemptId: Long): Job = scope.launch {
        runAttempt(attemptId)
    }.also { job ->
        job.invokeOnCompletion {
            synchronized(lock) {
                if (activeAttempt === job) activeAttempt = null
            }
        }
    }

    private suspend fun runAttempt(attemptId: Long) {
        updateIfCurrent(attemptId, State.Inspecting())

        val inventory = runCatching { runtime.inspectResources() }.getOrElse { failure ->
            failIfCurrent(attemptId, "无法读取 Runtime 状态：${shortMessage(failure)}")
            return
        }
        if (!inventory.reusableInstalledRuntime || inventory.activeSlot == null) {
            failIfCurrent(attemptId, "尚未安装本地 Runtime")
            return
        }

        // Fast path: an already-running runtime should not be restarted just because a
        // Compose surface appeared again.
        val alreadyReady = runCatching {
            if (runtime.isWebReady()) runtime.webPresentation() else null
        }.getOrNull()
        if (alreadyReady != null) {
            updateIfCurrent(attemptId, State.Ready(alreadyReady))
            return
        }

        val dispatchedAt = System.currentTimeMillis()
        val action = if (inventory.updateAvailable) {
            updateIfCurrent(
                attemptId,
                State.Starting(
                    "检测到 DSH ${inventory.installedDshVersion ?: "旧版"} → ${inventory.targetDshVersion}，正在更新 Runtime…",
                ),
            )
            RuntimeForegroundService.ACTION_INSTALL
        } else {
            updateIfCurrent(attemptId, State.Starting("正在启动本地 Runtime…"))
            RuntimeForegroundService.ACTION_START
        }

        runCatching { RuntimeForegroundService.dispatch(appContext, action) }
            .onFailure { failure ->
                failIfCurrent(attemptId, "无法启动 Runtime 服务：${shortMessage(failure)}")
                return
            }

        repeat(210) { index ->
            if (!isCurrent(attemptId)) return
            val snapshot = telemetry.snapshot(maxLogLines = 12)
            val presentation = runCatching {
                if (runtime.isWebReady()) runtime.webPresentation() else null
            }.getOrNull()
            if (presentation != null) {
                updateIfCurrent(attemptId, State.Ready(presentation))
                return
            }

            // Ignore a stale terminal snapshot from an earlier app/service process until
            // telemetry has been updated for this explicit request.
            val belongsToAttempt = snapshot.updatedAtMillis >= dispatchedAt &&
                snapshot.startedAtMillis >= dispatchedAt
            if (belongsToAttempt && snapshot.failed) {
                failIfCurrent(attemptId, snapshot.message.ifBlank { "DSH 启动失败" })
                return
            }
            if (belongsToAttempt && snapshot.running) {
                updateIfCurrent(
                    attemptId,
                    State.Starting(snapshot.message.ifBlank { "正在启动本地 Runtime…" }),
                )
            }
            if (index < 209) delay(1_000)
        }

        failIfCurrent(attemptId, "DSH 启动超时，可在设置 → 调试与日志中查看诊断")
    }

    private fun isCurrent(attemptId: Long): Boolean = attemptCounter.get() == attemptId

    private fun updateIfCurrent(attemptId: Long, next: State) {
        if (isCurrent(attemptId)) _state.value = next
    }

    private fun failIfCurrent(attemptId: Long, detail: String) {
        updateIfCurrent(attemptId, State.Failed(detail))
    }

    private fun shortMessage(failure: Throwable): String =
        failure.message?.lineSequence()?.firstOrNull()?.take(160)
            ?: failure::class.java.simpleName

    override fun close() {
        scope.cancel()
    }
}
