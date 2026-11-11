package ai.yaho.hyundai.data

import ai.yaho.hyundai.core.bookingStatusDisplay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


interface BookingHistorySource {
    suspend fun list(): Result<List<BookingStatus>>
    fun hasPending(): Boolean
    suspend fun recover(): Result<BookingStatus?>
    suspend fun cancel(id: String): Result<BookingStatus>
}

data class BookingHistoryState(
    val items: List<BookingStatus> = emptyList(), val busy: Boolean = false,
    val checkedAt: Long? = null, val error: String? = null, val pending: Boolean = false,
)

class BookingHistoryController(private val source: BookingHistorySource, private val elapsed: () -> Long) {
    private val lock = Any()
    private var generation = 0L
    private val mutableState = MutableStateFlow(BookingHistoryState())
    val state = mutableState.asStateFlow()


    fun invalidate() = synchronized(lock) {
        generation++
        mutableState.value = BookingHistoryState()
    }

    fun isFresh(): Boolean = mutableState.value.checkedAt?.let { elapsed()-it in 0..30_000 } == true

    private fun begin(cancelId: String?): Long? = synchronized(lock) {
        val current = mutableState.value
        if (current.busy) return null
        if (cancelId != null) {
            val item = current.items.singleOrNull { it.bookingId == cancelId } ?: return null
            if (!isFresh() || !bookingStatusDisplay(item.status,item.cancellationRequested).canCancel) return null
        }
        generation++
        mutableState.value = BookingHistoryState(busy = true,pending = source.hasPending())
        generation
    }

    suspend fun refresh() { val ticket = begin(null) ?: return; run(ticket,null) }
    suspend fun cancel(id: String) { val ticket = begin(id) ?: return; run(ticket,id) }

    private suspend fun run(ticket: Long, cancelId: String?) {
        try {
            var error: String? = null
            if (cancelId != null) {
                val cancellation = source.cancel(cancelId)
                if (cancellation.isFailure) error = "취소 결과 미확인 · 기존 요청의 상태를 다시 확인해 주세요."
            }
            if (!isCurrent(ticket)) return
            if (source.hasPending()) {
                val recovery = source.recover()
                if (recovery.isFailure) error = recovery.exceptionOrNull()?.message ?: "이전 요청 결과 미확인"
            }
            if (!isCurrent(ticket)) return
            val result = source.list()
            synchronized(lock) {
                if (ticket != generation) return
                mutableState.value = BookingHistoryState(
                    items = result.getOrNull().orEmpty(),
                    checkedAt = if (result.isSuccess) elapsed() else null,
                    error = if (result.isFailure) result.exceptionOrNull()?.message ?: "상태를 조회하지 못했습니다." else error,
                    pending = source.hasPending(),
                )
            }
        } catch (cancelled: CancellationException) {
            synchronized(lock) { if (ticket == generation) mutableState.value = BookingHistoryState() }
            throw cancelled
        } catch (_: Exception) {
            synchronized(lock) {
                if (ticket == generation) mutableState.value = BookingHistoryState(error = "현재 요청 상태를 확인하지 못했습니다.",pending = source.hasPending())
            }
        }
    }
    private fun isCurrent(ticket: Long) = synchronized(lock) { ticket == generation }
}
