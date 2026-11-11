package ai.yaho.hyundai.core


fun isNetworkAssessmentFresh(expiresAt: Long?, nowEpoch: Long, receivedElapsed: Long?, nowElapsed: Long): Boolean =
    expiresAt != null && expiresAt > nowEpoch && receivedElapsed != null &&
        nowElapsed >= receivedElapsed && nowElapsed - receivedElapsed < 30_000L
