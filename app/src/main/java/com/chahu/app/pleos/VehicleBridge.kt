package com.chahu.app.pleos

/**
 * Pleos Connect SDK 연결부.
 *
 * 지금은 에뮬레이터 없이도 앱이 돌아가도록 인터페이스 + 임시 구현만 둔다.
 * 2차 PT 전까지 아래 TODO를 Pleos SDK 호출로 교체한다.
 *  - VehicleBridge   → Vehicle SDK (CAR_* 읽기 권한만, Capability API로 지원 여부 먼저 확인)
 *  - NaviBridge      → NaviHelper SDK (requestRoute, onDestinationArrived, onCurrentLocationInfo)
 *  - VoiceBridge     → SpeechToText / TextToSpeech SDK
 * Pleos Connect 차량은 FusedLocation을 지원하지 않으므로 위치는 NaviHelper 이벤트로 받는다.
 */
interface VehicleBridge {
    /** 실내 온도(℃). 차종이 지원하지 않으면 null → 음성 피드백으로 대체 */
    fun cabinTemperatureC(): Double?
}

interface NaviBridge {
    /** 주차장 입구까지 경로 요청 */
    fun requestRouteTo(lat: Double, lon: Double, name: String)
}

/** TODO(Pleos): Vehicle SDK 공조(HVAC) 실내 온도 읽기로 교체 */
class StubVehicleBridge : VehicleBridge {
    override fun cabinTemperatureC(): Double? = null
}

/** TODO(Pleos): NaviHelper.requestRoute 로 교체 */
class StubNaviBridge(private val log: (String) -> Unit) : NaviBridge {
    override fun requestRouteTo(lat: Double, lon: Double, name: String) {
        log("[Stub] NaviHelper.requestRoute → $name ($lat, $lon)")
    }
}
