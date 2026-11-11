package ai.yaho.hyundai.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable


@Composable
internal fun AccountPreviewPanel() {
    SettingsPanel(title = "계정 연결", category = "연동 예정") {
        SettingsStatusLine("아직 계정 연결 전이에요", false)
        Text("휴대폰 QR로 연결하는 방식을 준비 중이에요.", style = MaterialTheme.typography.bodySmall)
        SettingsFinePrint {
            Text("휴대전화 번호 인증 후 차량 화면의 QR로 계정을 연결하는 방식과 카카오·네이버·구글 간편가입을 검토하고 있습니다.", style = MaterialTheme.typography.bodySmall)
            Text("현재 회원가입, 로그인, 인증 메시지 발송은 제공하지 않습니다. 차량 정보 수집 동의는 권한 탭에서 관리합니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
