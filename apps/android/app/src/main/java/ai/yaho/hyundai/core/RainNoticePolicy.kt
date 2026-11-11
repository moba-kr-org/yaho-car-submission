package ai.yaho.hyundai.core


fun rainNoticeText(visible: Boolean, interpretation: String, crossCheck: String, simulation: Boolean): String? {
    if (!visible || interpretation != "possible_precipitation") return null
    val prefix = if (simulation) "시험 안내입니다. " else ""
    return prefix + when (crossCheck) {
        "supports" -> "주변의 지속 와이퍼 사용이 늘었고, 기상 격자에도 강수 실황이 있습니다. 시야와 노면을 확인하세요."
        "conflicts" -> "주변 와이퍼 관측과 기상정보가 다릅니다. 강수 여부는 미확인입니다. 시야와 노면을 확인하세요."
        else -> "주변의 지속 와이퍼 사용이 늘었습니다. 강수 여부는 미확인입니다. 시야와 노면을 확인하세요."
    }
}

fun mayPresentRainNotice(eligible: Boolean, lastPresentedAt: Long?, now: Long): Boolean =
    eligible && now > 0 && (lastPresentedAt == null || (lastPresentedAt > 0 && now >= lastPresentedAt && now - lastPresentedAt >= 300_000))
