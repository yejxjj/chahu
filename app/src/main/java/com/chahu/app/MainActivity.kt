package com.chahu.app

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.chahu.app.data.HistoryStore
import com.chahu.app.data.LotRepository
import com.chahu.app.engine.ParkingLot
import com.chahu.app.engine.ShadeEngine
import com.chahu.app.engine.SpaceScore
import com.chahu.app.pleos.NaviBridge
import com.chahu.app.pleos.StubNaviBridge
import com.chahu.app.pleos.StubVehicleBridge
import com.chahu.app.pleos.VehicleBridge
import com.chahu.app.ui.ParkingMapView
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var lot: ParkingLot
    private lateinit var engine: ShadeEngine
    private lateinit var history: HistoryStore
    private lateinit var map: ParkingMapView
    private lateinit var timeLabel: TextView
    private lateinit var resultText: TextView
    private lateinit var logText: TextView

    private val vehicle: VehicleBridge = StubVehicleBridge()
    private lateinit var navi: NaviBridge
    private var tts: TextToSpeech? = null

    private val zone = ZoneId.of("Asia/Seoul")
    // 실내 발표장에서도 여름 그림자를 보여줄 수 있도록 시연 날짜를 고정 (설정에서 바꿀 수 있게 확장 예정)
    private val demoDate = LocalDate.of(2026, 8, 14)
    private val sliderStart = LocalTime.of(6, 0)   // 06:00
    private val stepMin = 15L                        // 슬라이더 한 칸 = 15분
    private var durationHours = 2L
    private var lastRecommended: SpaceScore? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lot = LotRepository.load(this)
        engine = ShadeEngine(lot)
        history = HistoryStore(this, lot.name)
        navi = StubNaviBridge(::log)
        tts = TextToSpeech(this, this)

        map = findViewById(R.id.map)
        timeLabel = findViewById(R.id.timeLabel)
        resultText = findViewById(R.id.resultText)
        logText = findViewById(R.id.logText)
        findViewById<TextView>(R.id.lotName).text = lot.name
        map.lot = lot

        val seek = findViewById<SeekBar>(R.id.timeSeek)
        seek.max = (13 * 60 / stepMin).toInt() // 06:00 ~ 19:00
        seek.progress = (8 * 60 / stepMin).toInt() // 14:00
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) = refreshMap()
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })

        findViewById<RadioGroup>(R.id.durationGroup).setOnCheckedChangeListener { _, id ->
            durationHours = when (id) { R.id.d1 -> 1L; R.id.d3 -> 3L; else -> 2L }
        }
        findViewById<Button>(R.id.askButton).setOnClickListener { recommend() }
        findViewById<Button>(R.id.departButton).setOnClickListener { recordDeparture() }

        refreshMap()
    }

    private fun currentTime(): ZonedDateTime {
        val p = findViewById<SeekBar>(R.id.timeSeek).progress
        return ZonedDateTime.of(demoDate, sliderStart.plusMinutes(p * stepMin), zone)
    }

    /** 슬라이더 시각의 그림자와 칸별 그늘 비율을 평면도에 반영 */
    private fun refreshMap() {
        val t = currentTime()
        val sun = engine.sunAt(t)
        val shadows = engine.shadowsFor(sun)
        map.shadows = shadows
        map.shadeBySpace = lot.spaces.associate { it.id to engine.shadeRatio(it, shadows) }
        timeLabel.text = String.format(
            Locale.KOREA, "%s  %02d:%02d   태양 방위 %.0f° · 고도 %.0f°",
            demoDate, t.hour, t.minute, sun.azimuthDeg, sun.altitudeDeg,
        )
    }

    /** "주차할 곳 알려줘" — 지금은 버튼, 2차에서 SpeechToText SDK 호출로 연결 */
    private fun recommend() {
        val start = currentTime()
        val ranked = engine.rankWithHistory(start, Duration.ofHours(durationHours), history.averages())
        val best = ranked.first()
        lastRecommended = best
        map.recommendedId = best.space.id

        val top = ranked.take(3).joinToString("\n") { "${it.space.id}   그늘 유지 ${it.percent}%" }
        resultText.text = String.format(
            Locale.KOREA, "%02d:%02d부터 %d시간 주차\n\n%s", start.hour, start.minute, durationHours, top,
        )
        val msg = "${spoken(best.space.id)}, ${durationHours}시간 동안 ${best.percent}퍼센트 그늘입니다. 안내할게요."
        speak(msg)
        navi.requestRouteTo(lot.latDeg, lot.lonDeg, "${lot.name} 입구")
        log("추천: ${best.space.id} (${best.percent}%)")
    }

    /** 출발 시 실내 온도 기록 — Vehicle SDK 미지원/에뮬레이터에서는 직접 입력(시연용) */
    private fun recordDeparture() {
        val rec = lastRecommended ?: run { log("먼저 추천을 받아 주세요."); return }
        val auto = vehicle.cabinTemperatureC()
        if (auto != null) {
            history.record(rec.space.id, auto)
            log("${rec.space.id} 출발 시 실내 ${"%.1f".format(auto)}℃ 기록")
            return
        }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "예: 34.8"
        }
        AlertDialog.Builder(this)
            .setTitle("${rec.space.id} 출발 시 실내 온도(℃)")
            .setMessage("차량이 실내 온도를 제공하지 않아 시연용으로 직접 입력합니다.")
            .setView(input)
            .setPositiveButton("기록") { _, _ ->
                input.text.toString().toDoubleOrNull()?.let {
                    history.record(rec.space.id, it)
                    log("${rec.space.id} 출발 시 실내 ${"%.1f".format(it)}℃ 기록 → 다음 추천에 반영")
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun spoken(id: String) = id.replace("-", " 구역 ").replace(" 0", " ") + "번"

    private fun speak(text: String) {
        // TODO(Pleos): TextToSpeech SDK로 교체
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "shade")
    }

    private fun log(s: String) {
        logText.text = "$s\n${logText.text}".take(600)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.KOREAN
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
