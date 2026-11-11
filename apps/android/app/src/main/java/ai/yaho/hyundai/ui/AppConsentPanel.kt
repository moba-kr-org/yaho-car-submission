package ai.yaho.hyundai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ai.yaho.hyundai.data.AppConsents
import ai.yaho.hyundai.data.AppConsentStore

@Composable
internal fun rememberAppConsents(): AppConsents {
    val context = LocalContext.current.applicationContext
    val store = remember { AppConsentStore(context) }
    var value by remember { mutableStateOf(store.read()) }
    DisposableEffect(store) {
        val listener = store.listen { value = store.read() }
        onDispose { store.unlisten(listener) }
    }
    return value
}

@Composable
internal fun AppConsentPanel(initial: AppConsents, onboarding: Boolean, canEnable: Boolean, onSave: (AppConsents) -> Unit,
    onDraftChange: ((AppConsents) -> Unit)? = null) {
    var choice by remember(initial) { mutableStateOf(initial) }
    fun update(next: AppConsents) {
        choice = next.normalized()
        if (!onboarding) onSave(choice.copy(reviewed = true))
        else onDraftChange?.invoke(choice)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (onboarding) 14.dp else 0.dp)) {
        if (onboarding) {
            Text("수집·이용 및 기능 사용 동의", style = MaterialTheme.typography.titleMedium)
            Text("최초 1회 선택하며, 동의 내역은 이 기기에 저장됩니다. 같은 수집 범위에서는 다시 묻지 않으며 이번 부위별 조회 확장처럼 범위가 늘어나면 다시 안내합니다. 모든 항목은 선택 사항이며 설정에서 언제든 해제할 수 있습니다. 동의하지 않아도 얼굴 화면은 사용할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
        } else {
            SettingsPanel(title = "사용 동의") {
                Text("모두 선택 항목이에요. 항목을 열면 목적과 보관 범위를 볼 수 있어요.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (onboarding) ConsentChoice("기본 항목 전체 동의", "차량 조회·내비 1회 조회·버튼 조작·샘플 네트워크·재부팅 복구를 선택합니다. 실제 위치 네트워크는 아래에서 별도로 선택합니다.", choice.vehicleRead && choice.location && choice.manualControl && choice.network && choice.resumeAfterBoot, canEnable, onboarding) {
            update(choice.copy(vehicleRead = it, location = it, manualControl = it, network = it, resumeAfterBoot = it))
        }
        ConsentChoice("[선택] 차량 상태 자동 조회", "항목: SDK의 기어·브레이크·와이퍼·문/창문/잠금·조명·배터리/충전·연료/효율·주행거리/시간·차량 경고와 부위별 실내 공조(온도·풍향·풍량·내기순환), 좌석 위치·착좌·안전벨트·에어백 상태, 미러 열선, 타이어 저압 관련 조회값입니다. 실제 공기압과 계기판 통합 경고 목록은 현재 자동 조회에 포함하지 않습니다. 추가 연결은 메서드별 제공 권한과 수집 범위를 확인한 뒤 안내합니다. 지원 범위는 차종과 권한에 따라 다릅니다. 실내 온도 설정값과 실제 측정값은 별개이며 응답이 없으면 0이나 정상으로 대체하지 않습니다.\n목적: 차량 상태 표시와 관리 안내. 시야 확인 조합은 와이퍼·앞유리 성에 제거 관측을 최대 2분간 메모리에서 함께 살피며, 조회 해제 시 비웁니다.\n보관: 최신 값을 앱 메모리에서 갱신하며, 네트워크 공유에 동의하지 않으면 서버로 보내지 않습니다. 해제하면 조회를 멈추고 메모리의 관측을 비웁니다. 앱 사용 중 시작한 관측은 알림이 표시되는 서비스로 앱 전환 후에도 이어지며, 알림이나 설정에서 중지할 수 있습니다. OS가 종료하거나 실행 시간을 제한할 수 있습니다.", choice.vehicleRead, canEnable, onboarding) { update(choice.copy(vehicleRead = it)) }
        ConsentChoice("[선택] 내비 위치 조회", "항목: 내비 SDK의 현재 위도·경도 등 위치 응답.\n목적: 위치 수신 상태 확인 및 위치 기반 기능 준비. 이 항목만으로는 좌표·주소를 저장하거나 서버로 전송하지 않습니다. 아래 실제 위치 네트워크 항목은 별도 선택입니다.\n해제: 새 위치 조회를 중단합니다. 이 동의만으로 이동 이력 수집이나 외부 제공을 시작하지 않습니다.", choice.location, canEnable, onboarding) { update(choice.copy(location = it)) }
        ConsentChoice("[선택] 창문 · 와이퍼 버튼 조작", "항목: 운전석 창문 위치, 앞 와이퍼 모드와 조작 결과.\n목적: P단 정차 시 직접 누른 조작 실행과 결과 확인. 상태는 앱 메모리에서 처리합니다.\n사전 동의 후에는 조작 때마다 동의를 묻지 않습니다. 해제하면 새 조작을 차단합니다. 터널 등 조건에 따른 자동 제어는 포함하지 않습니다.", choice.manualControl, canEnable, onboarding) { update(choice.copy(vehicleRead = choice.vehicleRead || it, manualControl = it)) }
        ConsentChoice("[선택] 차량 네트워크 · 제보 공유", "항목: 기존 공유 허용 목록의 와이퍼·브레이크·기어·에너지·주행 등 SDK 관측값, 관측 시각, 설치 구분 정보, 제보 종류와 위치. 새로 추가한 좌석·착좌·안전벨트·에어백·부위별 공조·타이어 값은 현재 로컬 조회만 하며 서버 공유 목록에 추가하지 않습니다. 현재 시험은 SDK 값과 샘플 위치를 사용합니다.\n목적: 주변 차량의 관측 집계와 도로 정보·제보 제공. 서버는 관측 위치를 약 300m 격자로 줄여 저장하고 3km 반경의 지속 와이퍼 사용을 집계합니다. 다른 참여 앱에는 집계만 제공합니다. 기상 대조가 연결되지 않으면 미확인으로 표시합니다.\n보관·해제: 자동 관측의 실시간 판단에는 최근 2분만 사용합니다. 2분은 모든 서버 기록의 삭제 시한을 뜻하지 않습니다. 만료 관측은 서버 정리 대상이며, 해제 시 신규 전송을 막고 서버의 관측·제보 기여 기록 철회를 재시도합니다.", choice.network, canEnable, onboarding) { update(choice.copy(vehicleRead = choice.vehicleRead || it, network = it)) }
        ConsentChoice("[선택] 시스템 재시작 후 관측 이어가기", "목적: 재부팅 뒤 동의한 차량 상태 조회를 복구합니다. 새 수집 항목이나 공유 범위를 추가하지 않습니다. 차량 시동·절전 복귀와는 별개입니다. Android 14 이하에서는 관측 서비스를 다시 시작하고, Android 15 이상에서는 OS 제한에 따라 앱을 열도록 알림만 제공합니다. 알림 권한이 없으면 알림을 표시하지 않습니다. 해제하면 다음 재부팅부터 자동 복구하지 않습니다.", choice.resumeAfterBoot, canEnable, onboarding) { update(choice.copy(vehicleRead = choice.vehicleRead || it, resumeAfterBoot = it)) }
        ConsentChoice("[선택] 실제 위치로 주변 차량 정보 연결", "항목: FusedLocation의 위도·경도·측정 시각·방향·제공 신뢰도와 기존 공유 허용 차량 관측입니다. 정확도 미터 값은 SDK가 제공하지 않아 미확인으로 둡니다.\n목적: 실제 주변 구간의 관측을 집계하고 관련 정보를 받습니다. 앱 전면 실행 중에만 조회하며, 좌표는 메모리에서 갱신하고 서버는 약300m 격자로 줄여 저장합니다. 원시 좌표·주소는 DB에 보관하지 않습니다. SDK 신호와15초 내의 신선한 위치만 전송하며 오래되거나 없는 위치는 샘플로 대체하지 않습니다. 에뮬레이터 관측은 시험 채널입니다.\n해제: 위치 관측·신규 위치 전송을 중단하고 메모리 위치를 비웁니다. 서버 기여 기록 회수를 재시도합니다. 수신 위치는 정확도·실차 검증 전 안전 경고의 확정 근거가 아닙니다. 별도 OS 위치 권한이 필요합니다.", choice.liveLocationNetwork, canEnable, onboarding) {
            update(choice.copy(vehicleRead = choice.vehicleRead || it, location = choice.location || it, network = choice.network || it, liveLocationNetwork = it))
        }
        if (onboarding) ConsentNotes()
        else SettingsDetails("데이터 처리 안내", "보관·철회 및 별도 권한") {
            SettingsPanel { ConsentNotes() }
        }
    }
}

@Composable
private fun ConsentNotes() {
    Text("안내 중복을 줄이기 위해 마지막 안내 분류·우선순위·표시 시각을 이 기기에 저장합니다. 주요 안내의 자동 음성은 기본 꺼짐이며 도로 화면에서 선택할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
    Text("차대번호·차량등록번호·연락처는 현재 자동 관측 항목에 포함하지 않습니다. 차량 조회를 해제하면 이에 의존하는 버튼 조작과 네트워크 공유도 함께 꺼집니다.", style = MaterialTheme.typography.bodySmall)
    Text("OS 권한 창은 별도로 표시됩니다. 전화·예약·결제 등 외부 업무 실행과 자동 차량 제어는 이 동의에 포함되지 않습니다.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ConsentChoice(title: String, detail: String, checked: Boolean, canEnable: Boolean, onboarding: Boolean, onChange: (Boolean) -> Unit) {
    if (onboarding) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Checkbox(checked = checked, onCheckedChange = onChange, enabled = canEnable || checked)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text(detail, color = SettingsMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    } else {
        SettingsDetails(title.removePrefix("[선택] "), if (checked) "동의함" else "동의 안 함") {
            SettingsPanel {
                Text(detail, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = onChange, enabled = canEnable || checked)
                    Text("이 항목에 동의", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
internal fun InitialConsentDialog(choice: AppConsents, canEnable: Boolean, onSave: (AppConsents) -> Unit) {
    if (choice.reviewed) return
    var draft by remember(choice) { mutableStateOf(choice) }
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color(0xFFF8F5F0), shape = RoundedCornerShape(32.dp), shadowElevation = 24.dp,
            modifier = Modifier.padding(28.dp).widthIn(max = 920.dp).fillMaxWidth().fillMaxHeight(.82f)) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    AppConsentPanel(draft, true, canEnable, onSave, onDraftChange = { draft = it })
                }
                HorizontalDivider()
                Button(enabled = canEnable, onClick = { onSave(AppConsents(true, true, true, true, true, true)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                    Text("기본 항목 동의하고 시작하기")
                }
                TextButton(enabled = canEnable, onClick = { onSave(draft.copy(reviewed = true)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("선택한 항목만으로 시작")
                }
            }
        }
    }
}
