package ai.yaho.hyundai.core

enum class ExperimentVariant(val wireKey: String, val label: String, val subtitle: String) {
    A("a", "A · 빠른 제보", "핵심만 빠르게 확인"),
    B("b", "B · 맞춤 안내", "상황을 확인하고 진행"),
}
