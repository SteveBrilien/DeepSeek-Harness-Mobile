package com.stevebrilien.dshmobile.runtime

import android.content.Context
import android.os.SystemClock
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
    data class StartupMetrics(
        val activeSinceElapsedMillis: Long? = null,
        val lastDurationMillis: Long? = null,
        val averageDurationMillis: Long? = null,
        val sampleCount: Int = 0,
    )

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
    private val metricsPrefs = appContext.getSharedPreferences("runtime-startup-metrics", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val attemptCounter = AtomicLong(0L)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private val _startupMetrics = MutableStateFlow(loadStartupMetrics())
    val startupMetrics: StateFlow<StartupMetrics> = _startupMetrics.asStateFlow()

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
        beginStartupAttempt(attemptId)
        updateIfCurrent(attemptId, State.Inspecting())

        val inventory = runCatching { runtime.inspectResources() }.getOrElse { failure ->
            failIfCurrent(attemptId, "无法读取 Runtime 状态：${shortMessage(failure)}")
            return
        }
        if (!inventory.reusableInstalledRuntime || inventory.activeSlot == null) {
            failIfCurrent(attemptId, "尚未安装本地 Runtime")
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

        repeat(STARTUP_POLL_COUNT) { index ->
            if (!isCurrent(attemptId)) return
            val snapshot = telemetry.snapshot(maxLogLines = 12)
            val presentation = runCatching { runtime.webPresentation() }.getOrNull()
            if (presentation != null) {
                updateIfCurrent(attemptId, State.Ready(presentation))
                finishStartupAttempt(attemptId)
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
            if (index < STARTUP_POLL_COUNT - 1) delay(STARTUP_POLL_INTERVAL_MILLIS)
        }

        failIfCurrent(attemptId, "DSH 启动超时，可在设置 → 调试与日志中查看诊断")
    }

    private fun isCurrent(attemptId: Long): Boolean = attemptCounter.get() == attemptId

    private fun updateIfCurrent(attemptId: Long, next: State) {
        if (isCurrent(attemptId)) _state.value = next
    }

    private fun failIfCurrent(attemptId: Long, detail: String) {
        if (!isCurrent(attemptId)) return
        _state.value = State.Failed(detail)
        _startupMetrics.value = _startupMetrics.value.copy(activeSinceElapsedMillis = null)
    }

    private fun beginStartupAttempt(attemptId: Long) {
        if (!isCurrent(attemptId)) return
        _startupMetrics.value = _startupMetrics.value.copy(
            activeSinceElapsedMillis = SystemClock.elapsedRealtime(),
        )
    }

    private fun finishStartupAttempt(attemptId: Long) {
        if (!isCurrent(attemptId)) return
        val current = _startupMetrics.value
        val started = current.activeSinceElapsedMillis ?: return
        val duration = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0L)
        val count = current.sampleCount.coerceAtLeast(0)
        val nextCount = (count + 1).coerceAtMost(10_000)
        val previousAverage = current.averageDurationMillis ?: duration
        val average = if (count <= 0) duration else ((previousAverage * count) + duration) / (count + 1)
        val next = StartupMetrics(
            activeSinceElapsedMillis = null,
            lastDurationMillis = duration,
            averageDurationMillis = average,
            sampleCount = nextCount,
        )
        _startupMetrics.value = next
        metricsPrefs.edit()
            .putLong(KEY_LAST_DURATION_MILLIS, duration)
            .putLong(KEY_AVERAGE_DURATION_MILLIS, average)
            .putInt(KEY_SAMPLE_COUNT, nextCount)
            .apply()
    }

    private fun loadStartupMetrics(): StartupMetrics {
        val count = metricsPrefs.getInt(KEY_SAMPLE_COUNT, 0).coerceAtLeast(0)
        val last = metricsPrefs.getLong(KEY_LAST_DURATION_MILLIS, -1L).takeIf { it >= 0L }
        val average = metricsPrefs.getLong(KEY_AVERAGE_DURATION_MILLIS, -1L).takeIf { it >= 0L }
        return StartupMetrics(
            lastDurationMillis = last,
            averageDurationMillis = average,
            sampleCount = count,
        )
    }

    private fun shortMessage(failure: Throwable): String =
        failure.message?.lineSequence()?.firstOrNull()?.take(160)
            ?: failure::class.java.simpleName

    override fun close() {
        scope.cancel()
    }

    private companion object {
        const val STARTUP_POLL_INTERVAL_MILLIS = 250L
        const val STARTUP_TIMEOUT_MILLIS = 210_000L
        const val STARTUP_POLL_COUNT = (STARTUP_TIMEOUT_MILLIS / STARTUP_POLL_INTERVAL_MILLIS).toInt()
        const val KEY_LAST_DURATION_MILLIS = "last-duration-ms"
        const val KEY_AVERAGE_DURATION_MILLIS = "average-duration-ms"
        const val KEY_SAMPLE_COUNT = "sample-count"
    }
}
