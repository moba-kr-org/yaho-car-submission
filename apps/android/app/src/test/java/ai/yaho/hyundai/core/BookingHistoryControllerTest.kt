package ai.yaho.hyundai.core

import ai.yaho.hyundai.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class BookingHistoryControllerTest {
    private val item = BookingStatus("11111111-1111-4111-8111-111111111111","점검","2026-11-06T00:00:00Z","needs_configuration",false,"2026-11-05T00:00:00Z")
    private class Source : BookingHistorySource {
        var pending = false
        var lists = 0
        var cancels = 0
        var listResponse: suspend () -> Result<List<BookingStatus>> = { Result.success(emptyList()) }
        var cancelResponse: suspend () -> Result<BookingStatus> = { error("unset") }
        override suspend fun list(): Result<List<BookingStatus>> { lists++; return listResponse() }
        override fun hasPending() = pending
        override suspend fun recover(): Result<BookingStatus?> = Result.failure(IllegalStateException("접수 미확인"))
        override suspend fun cancel(id: String): Result<BookingStatus> { cancels++; return cancelResponse() }
    }
    @Test fun successfulReadExpiresAndFailureNeverLooksLikeAnEmptySuccessfulHistory() = runBlocking {
        var clock = 100L
        val source=Source().apply { listResponse={Result.success(listOf(item))} }
        val controller=BookingHistoryController(source){clock}
        controller.refresh()
        assertTrue(controller.isFresh()); assertEquals(1,controller.state.value.items.size)
        clock+=30_001
        assertFalse(controller.isFresh())
        source.listResponse={Result.failure(IllegalStateException("offline"))}
        controller.refresh()
        assertNull(controller.state.value.checkedAt); assertEquals("offline",controller.state.value.error)
        assertTrue(controller.state.value.items.isEmpty()); assertFalse(controller.state.value.busy)
    }
    @Test fun lateNonCancellableHttpResultCannotRestoreAnInvalidatedOrReplacedView() = runBlocking {
        val source=Source()
        val started=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>()
        source.listResponse={withContext(NonCancellable){started.complete(Unit);release.await();Result.success(listOf(item))}}
        val controller=BookingHistoryController(source){100}
        val old=launch {controller.refresh()};started.await()
        controller.invalidate()
        source.listResponse={Result.success(emptyList())}
        controller.refresh()
        release.complete(Unit);old.join()
        assertTrue(controller.state.value.items.isEmpty());assertTrue(controller.isFresh())
        assertNull(controller.state.value.error)
    }
    @Test fun cancellationClearsBusyWithoutTurningItIntoNetworkFailure() = runBlocking {
        val source=Source();val started=CompletableDeferred<Unit>()
        source.listResponse={started.complete(Unit);awaitCancellation()}
        val controller=BookingHistoryController(source){100}
        val job=launch{controller.refresh()};started.await();job.cancelAndJoin()
        assertFalse(controller.state.value.busy);assertNull(controller.state.value.error);assertNull(controller.state.value.checkedAt)
        source.listResponse={Result.success(listOf(item))};controller.refresh();assertTrue(controller.isFresh())
    }
    @Test fun expiredOrChangedStatusCannotDispatchACancellationAndConcurrentRefreshIsBounded() = runBlocking {
        var clock=100L;val source=Source().apply {listResponse={Result.success(listOf(item))}}
        val controller=BookingHistoryController(source){clock};controller.refresh()
        clock+=30_001;controller.cancel(item.bookingId);assertEquals(0,source.cancels)
        val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        source.listResponse={started.complete(Unit);release.await();Result.success(emptyList())}
        val first=launch{controller.refresh()};started.await()
        controller.refresh();controller.cancel(item.bookingId)
        assertEquals(2,source.lists);assertEquals(0,source.cancels)
        release.complete(Unit);first.join()
    }
    @Test fun uncertainCancellationRequeriesWithoutRepeatingWriteAndRecoveryFailureKeepsPending() = runBlocking {
        val source=Source().apply{listResponse={Result.success(listOf(item))};cancelResponse={Result.failure(IllegalStateException("lost reply"))}}
        val controller=BookingHistoryController(source){100};controller.refresh();controller.cancel(item.bookingId)
        assertEquals(1,source.cancels);assertEquals(2,source.lists)
        assertTrue(controller.state.value.error!!.contains("취소 결과 미확인"))
        source.pending=true;controller.refresh()
        assertTrue(controller.state.value.pending);assertEquals("접수 미확인",controller.state.value.error)
        assertEquals(1,source.cancels)
    }
}
