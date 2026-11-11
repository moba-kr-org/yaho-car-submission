package ai.yaho.hyundai.core

data class BookingStatusDisplay(val title: String, val detail: String, val canCancel: Boolean)

fun bookingStatusDisplay(status: String, cancellationRequested: Boolean): BookingStatusDisplay {
    if (status == "cancelled") return BookingStatusDisplay("요청 취소 완료", "서버의 요청이 취소 상태입니다.", false)
    if (cancellationRequested || status == "cancel_requested") return BookingStatusDisplay(
        "취소 요청 접수 · 완료 미확인",
        if (status == "unknown") "원래 요청 결과도 불명확합니다. 새 요청을 보내지 말고 처리 결과를 확인해 주세요."
        else "취소 의사를 접수했습니다. 처리 중인 작업의 종료나 업체 취소 완료를 뜻하지 않습니다.", false)
    return when (status) {
        "needs_configuration" -> BookingStatusDisplay("제공자 연동 전", "요청을 서버에 보관했습니다. 업체에 전달하거나 예약을 확정한 상태가 아닙니다.", true)
        "queued" -> BookingStatusDisplay("처리 대기", "서버 작업 대기 상태이며 예약 확정이 아닙니다.", true)
        "leased" -> BookingStatusDisplay("처리 중 · 확정 전", "작업 처리 중입니다. 새 요청으로 다시 보내지 마세요.", true)
        "unknown" -> BookingStatusDisplay("처리 결과 미확인", "전달 여부를 확정할 수 없습니다. 자동 재전송하지 않으며 기존 요청을 확인해야 합니다.", true)
        "failed" -> BookingStatusDisplay("요청 처리 실패", "서버에 기록된 실패 상태입니다. 예약이 확정되지 않았습니다.", false)
        else -> BookingStatusDisplay("알 수 없는 처리 상태", "서버 상태를 해석하지 못했습니다. 임의로 완료·실패를 판단하지 않습니다.", false)
    }
}
