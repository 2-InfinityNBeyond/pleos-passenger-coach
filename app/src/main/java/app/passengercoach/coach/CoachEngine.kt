package app.passengercoach.coach

import kotlin.math.abs

enum class TurnDir { OFF, LEFT, RIGHT }

enum class GearKind { PARK, NEUTRAL, REVERSE, DRIVE, OTHER }

enum class NaviTurnKind { LEFT, RIGHT, UTURN, OTHER }

/** 해설이 속한 연습 상황: 회전(깜빡이, 핸들), 출발과 정차(브레이크, 기어), 주차(기어 R에서 일어난 모든 조작), 등화와 장치(전조등, 와이퍼, 안전벨트, 문) */
enum class CoachTopic { TURN, STOP, PARK, DEVICE }

/** 차 가까이에 있는 것(지도 자료에서 찾음). 요령을 상황에 맞게 고르는 데 쓴다 */
enum class Place { CROSSING, SIGNAL, SCHOOL }

/**
 * 코치 해설 규칙(요구서 F2 이벤트 감지, F3 코치 해설).
 * 점수나 평가 표현 없이, 일어난 일만 한 문장으로 알려 준다.
 * Android나 Pleos SDK에 의존하지 않아 단위 테스트가 가능하다.
 */
class CoachEngine {
    private var prevGear: GearKind? = null
    private var prevBrake: Boolean? = null
    private var prevTurn: TurnDir? = null

    private enum class Wheel { CENTER, SMALL, LARGE }

    private var steeringSeen = false
    private var wheel = Wheel.CENTER
    private var wheelDir = TurnDir.OFF

    // 크게 돌리는 조작(주차, 유턴)용: 마지막으로 알린 반 바퀴 수, 가장 많이 돌린 각도, 풀기 시작을 알렸는지
    private var turnStep = 0
    private var peak = 0f
    private var unwindSaid = false
    private var lastSteer = 0f

    private var turnOnAtMs = 0L
    private var turnOnDir = TurnDir.OFF
    private var steeredAfterTurn = false

    private var naviKind: NaviTurnKind? = null
    private var naviSinceMs = 0L
    private var naviDistanceM = Double.MAX_VALUE

    /**
     * 가장 최근 해설의 조작이 무엇을 위한 것인지 한 줄 설명(요구서 목표: 조작이 왜 필요한지 이해).
     * 평가가 아니라 조작의 쓰임만 말한다. shortcut: 문구는 전문가 검수 전, 검수 뒤 교체
     */
    var why: String? = null
        private set

    /** 가장 최근 해설이 속한 연습 상황 */
    var topic: CoachTopic? = null
        private set

    /**
     * 내비 안내가 [NAVI_NEAR_M] 이내로 가까워진 시점을 기억한다(안내 → 깜빡이 시차 계산용).
     * 먼 안내(예: 5km 앞 회전)부터 재면 "안내하고 N초 뒤"가 사실과 달라지기 때문이다.
     */
    fun onNavi(kind: NaviTurnKind?, distanceM: Double?, nowMs: Long) {
        if (kind == null) {
            naviKind = null
            return
        }
        val near = distanceM != null && distanceM <= NAVI_NEAR_M
        // 거리가 다시 늘었으면 같은 방향이라도 다음 안내로 넘어간 것이다
        val nextGuide = kind != naviKind || (distanceM != null && distanceM > naviDistanceM + NAVI_JUMP_M)
        naviKind = kind
        naviDistanceM = distanceM ?: Double.MAX_VALUE
        if (!near) naviSinceMs = 0L else if (nextGuide || naviSinceMs == 0L) naviSinceMs = nowMs
    }

    /** 새 신호를 반영하고, 해설할 일이 생겼으면 문장을 돌려준다. 없으면 null. */
    fun onSignals(steeringDeg: Float?, brakeOn: Boolean?, turn: TurnDir?, gear: GearKind?, nowMs: Long): String? {
        var message: String? = null
        var reason: String? = null
        var kind: CoachTopic? = null

        if (gear != null) {
            val before = prevGear
            if (before != null && gear != before) {
                message = "기어를 ${gearLabel(before)}에서 ${gearLabel(gear)}로 바꿨어요."
                reason = gearWhy(gear)
                kind = CoachTopic.STOP
            }
            prevGear = gear
        }

        if (brakeOn != null) {
            val before = prevBrake
            if (before != null && brakeOn != before) {
                message = if (brakeOn) "브레이크를 밟았어요." else "브레이크를 뗐어요."
                reason = if (brakeOn) BRAKE_ON_WHY else BRAKE_OFF_WHY
                kind = CoachTopic.STOP
                // 주차의 핵심 순서: 핸들을 먼저 돌려 놓고 브레이크를 떼서 움직인다
                val held = steeringDeg ?: lastSteer
                if (!brakeOn && prevGear == GearKind.REVERSE && abs(held) >= PARK_HOLD_DEG) {
                    // 방금 알린 바퀴 수와 어긋나지 않게(한 바퀴를 알린 뒤 350도에서 반 바퀴라고 하지 않게) 알린 값을 우선한다
                    val amount = maxOf(turnStep, (abs(held) / HALF_TURN_DEG).toInt()).let { if (it >= 1) turnsLabel(it) + " " else "" }
                    message = "핸들을 ${dirName(if (held > 0f) TurnDir.LEFT else TurnDir.RIGHT)}으로 ${amount}돌려 놓고 브레이크를 뗐어요."
                    reason = REVERSE_WHY
                }
            }
            prevBrake = brakeOn
        }

        if (turn != null) {
            val before = prevTurn
            if (before != null && turn != before) {
                message = if (turn == TurnDir.OFF) {
                    "깜빡이를 껐어요."
                } else {
                    turnOnAtMs = nowMs
                    turnOnDir = turn
                    steeredAfterTurn = false
                    naviLinkMessage(turn, nowMs) ?: "${dirName(turn)} 깜빡이를 켰어요."
                }
                reason = if (turn == TurnDir.OFF) BLINK_OFF_WHY else BLINK_ON_WHY
                kind = CoachTopic.TURN
            }
            prevTurn = turn
        }

        if (steeringDeg != null) {
            val size = abs(steeringDeg)
            val dir = if (steeringDeg > 0f) TurnDir.LEFT else TurnDir.RIGHT
            // 가운데를 거친 값 없이 반대쪽으로 넘어가면 새로 돌린 것으로 본다
            val first = !steeringSeen
            val flipped = !first && wheel != Wheel.CENTER && dir != wheelDir && size >= STEER_SMALL_DEG
            if (flipped) wheel = Wheel.CENTER
            if (size > STEER_CENTER_DEG) wheelDir = dir
            if (!steeringSeen) {
                // 첫 관측은 해설하지 않고 기준만 잡는다
                steeringSeen = true
                wheel = if (size >= STEER_START_DEG) Wheel.LARGE else if (size >= STEER_SMALL_DEG) Wheel.SMALL else Wheel.CENTER
            } else if (size <= STEER_CENTER_DEG) {
                if (wheel != Wheel.CENTER) {
                    message = "핸들이 다시 가운데로 돌아왔어요."
                    reason = "핸들이 가운데면 차가 곧게 가요."
                    kind = CoachTopic.TURN
                }
                wheel = Wheel.CENTER
            } else if (wheel != Wheel.LARGE && size >= STEER_START_DEG) {
                message = if (wheel == Wheel.SMALL) "핸들을 ${dirName(dir)}으로 더 돌렸어요." else turnedMessage(dir, "", nowMs)
                reason = STEER_WHY
                kind = CoachTopic.TURN
                wheel = Wheel.LARGE
            } else if (wheel == Wheel.CENTER && size >= STEER_SMALL_DEG) {
                message = turnedMessage(dir, "조금 ", nowMs)
                reason = STEER_WHY
                kind = CoachTopic.TURN
                wheel = Wheel.SMALL
            }

            // 크게 돌리는 조작(주차, 유턴): 반 바퀴 단위로 알리고, 다시 풀기 시작하면 한 번 알린다
            val step = (size / HALF_TURN_DEG).toInt()
            if (first) {
                turnStep = step
                peak = size
            } else {
                if (flipped || size <= STEER_CENTER_DEG) {
                    turnStep = 0
                    peak = 0f
                    unwindSaid = false
                }
                if (step > turnStep) {
                    message = "핸들을 ${dirName(dir)}으로 ${turnsLabel(step)} 돌렸어요."
                    reason = STEER_WHY
                    kind = CoachTopic.TURN
                    turnStep = step
                    unwindSaid = false
                } else if (size < turnStep * HALF_TURN_DEG - STEP_BACK_DEG) {
                    // 경계 각도에서 값이 흔들릴 때 같은 말을 반복하지 않게 여유를 둔다
                    turnStep = step
                }
                if (!unwindSaid && peak >= HALF_TURN_DEG && size <= peak - UNWIND_DEG && size > STEER_CENTER_DEG) {
                    message = "돌렸던 핸들을 다시 풀고 있어요."
                    reason = "핸들을 풀면 차가 다시 곧게 가요."
                    kind = CoachTopic.TURN
                    unwindSaid = true
                }
                if (unwindSaid || size > peak) peak = size
            }
            lastSteer = steeringDeg
        }
        if (message != null) {
            // 후진 중에는 차 뒤쪽이 어디로 가는지가 핵심이므로 핸들 설명을 바꾸고, 주차 상황으로 분류한다
            val reversing = prevGear == GearKind.REVERSE
            why = vary(if (reversing && reason == STEER_WHY) REVERSE_WHY else reason)
            topic = if (reversing) CoachTopic.PARK else kind
        }
        return message
    }

    private val shown = HashMap<String, Int>()

    /** 같은 조작이 다시 나오면 같은 말을 되풀이하지 않고, 그 조작의 다른 요령을 돌아가며 보여 준다 */
    private fun vary(reason: String?): String? {
        val byScene = VARIANTS[reason ?: return null] ?: return reason
        val scene = scene().takeIf { it in byScene } ?: Scene.GENERAL
        val pool = byScene[scene] ?: return reason
        val key = "$reason/$scene"
        val n = shown.getOrDefault(key, 0)
        shown[key] = n + 1
        return pool[n % pool.size]
    }

    /** 지금이 어떤 상황인지: 기어, 가까운 곳의 시설(횡단보도, 신호등, 학교), 내비 안내로 판단한다 */
    private fun scene(): Scene = when {
        prevGear == GearKind.REVERSE -> Scene.PARKING
        prevGear == GearKind.PARK -> Scene.PARKED
        place != null -> place!!
        naviKind != null && naviSinceMs != 0L -> Scene.TURN
        else -> Scene.GENERAL
    }

    private var place: Scene? = null

    /** 지도에서 찾은, 지금 차 가까이에 있는 것을 알려 준다(없으면 null). 요령을 상황에 맞게 고르는 데 쓴다 */
    fun onPlace(near: Place?) {
        place = when (near) {
            Place.CROSSING -> Scene.CROSSING
            Place.SIGNAL -> Scene.SIGNAL
            Place.SCHOOL -> Scene.SCHOOL
            null -> null
        }
    }

    private enum class Scene { GENERAL, TURN, CROSSING, SIGNAL, SCHOOL, PARKING, PARKED }

    private fun gearWhy(gear: GearKind): String? = when (gear) {
        GearKind.PARK -> "P는 바퀴를 잠가요. 차가 완전히 멈춘 뒤에 넣어요."
        GearKind.NEUTRAL -> "N은 힘이 끊겨요. 경사에서는 차가 저절로 굴러가니 브레이크를 밟고 있어요."
        GearKind.REVERSE -> "R은 완전히 멈춘 뒤에 넣어요. 넣자마자 발을 떼지 말고 뒤부터 살펴요."
        GearKind.DRIVE -> "D에서는 브레이크만 떼도 차가 천천히 굴러가요."
        GearKind.OTHER -> null
    }

    private val prevExtra = arrayOfNulls<Boolean>(5)

    private val pendingExtra = ArrayDeque<Triple<String, String?, Long>>()

    /**
     * 전조등, 상향등, 와이퍼, 운전석 안전벨트 경고, 문 열림의 변화를 기록한다. 해설은 [pollExtra]로 하나씩 꺼낸다.
     * 여러 신호가 한꺼번에 바뀌어도 상태를 놓치지 않도록, 모든 값을 매번 끝까지 반영하고 할 말은 줄을 세워 둔다.
     */
    fun noteExtras(lowBeamOn: Boolean?, highBeamOn: Boolean?, wiperOn: Boolean?, beltWarning: Boolean?, doorOpen: Boolean?, nowMs: Long = 0L) {
        val now = arrayOf(lowBeamOn, highBeamOn, wiperOn, beltWarning, doorOpen)
        for (i in now.indices) {
            val value = now[i] ?: continue
            val before = prevExtra[i]
            prevExtra[i] = value
            if (before == null || before == value) continue
            val (text, reason) = EXTRA_LINES[i][if (value) 0 else 1]
            pendingExtra.addLast(Triple(text, reason, nowMs))
            // 밀린 말이 쌓이면 오래된 것부터 버린다(지난 일을 뒤늦게 말하지 않으려고)
            if (pendingExtra.size > MAX_PENDING) pendingExtra.removeFirst()
        }
    }

    /** 줄 세워 둔 추가 조작 해설을 하나 꺼낸다. 없으면 null. 너무 늦은 것([EXTRA_STALE_MS] 지남)은 말하지 않고 버린다 */
    fun pollExtra(nowMs: Long = 0L): String? {
        while (nowMs > 0L && pendingExtra.isNotEmpty() && nowMs - pendingExtra.first().third > EXTRA_STALE_MS) pendingExtra.removeFirst()
        val (text, reason) = pendingExtra.removeFirstOrNull() ?: return null
        why = reason
        topic = CoachTopic.DEVICE
        return text
    }

    /** 기록하고 바로 하나 꺼낸다 */
    fun onExtras(lowBeamOn: Boolean?, highBeamOn: Boolean?, wiperOn: Boolean?, beltWarning: Boolean?, doorOpen: Boolean?): String? {
        noteExtras(lowBeamOn, highBeamOn, wiperOn, beltWarning, doorOpen)
        return pollExtra()
    }

    /** 깜빡이를 켠 직후 같은 방향으로 핸들을 돌렸으면 한 동작으로 묶어 말한다. 차선 변경이라고 단정하지는 않는다. */
    private fun turnedMessage(dir: TurnDir, amount: String, nowMs: Long): String {
        val linked = !steeredAfterTurn && dir == turnOnDir && nowMs - turnOnAtMs <= LINK_WINDOW_MS
        if (linked) steeredAfterTurn = true
        return (if (linked) "깜빡이를 켠 뒤 " else "") + "핸들을 ${dirName(dir)}으로 ${amount}돌렸어요."
    }

    private fun naviLinkMessage(turn: TurnDir, nowMs: Long): String? {
        val kind = naviKind ?: return null
        val matches = (kind == NaviTurnKind.LEFT && turn == TurnDir.LEFT) || (kind == NaviTurnKind.RIGHT && turn == TurnDir.RIGHT)
        if (!matches || naviSinceMs == 0L) return null
        val seconds = (nowMs - naviSinceMs) / 1000
        // 주차한 채 오래 있다가 켜면 "100초 뒤"처럼 뜻 없는 숫자가 되므로, 오래 지났으면 초를 말하지 않는다
        if (seconds > 60) return "내비가 ${if (turn == TurnDir.LEFT) "좌회전" else "우회전"}을 안내한 뒤, ${dirName(turn)} 깜빡이를 켰어요."
        return "내비가 ${if (turn == TurnDir.LEFT) "좌회전" else "우회전"}을 안내하고 ${seconds}초 뒤, ${dirName(turn)} 깜빡이를 켰어요."
    }

    private fun dirName(dir: TurnDir): String = if (dir == TurnDir.LEFT) "왼쪽" else "오른쪽"

    private fun gearLabel(gear: GearKind): String = when (gear) {
        GearKind.PARK -> "P(주차)"
        GearKind.NEUTRAL -> "N(중립)"
        GearKind.REVERSE -> "R(후진)"
        GearKind.DRIVE -> "D(주행)"
        GearKind.OTHER -> "기타"
    }

    companion object {
        // 추가 조작별 (켜짐/참일 때, 꺼짐/거짓일 때)의 해설과 쓰임. 순서: 전조등, 상향등, 와이퍼, 안전벨트 경고, 문 열림
        private val EXTRA_LINES = listOf(
            listOf("전조등을 켰어요." to "전조등은 남에게 내 차를 보이려고도 켜요. 비 오는 낮, 터널에서도 켜요.", "전조등을 껐어요." to null),
            listOf("상향등을 켰어요." to "상향등은 더 멀리 비춰요. 마주 오는 차가 있으면 눈이 부시지 않게 낮춰요.", "상향등을 껐어요." to null),
            listOf("와이퍼를 켰어요." to "앞이 흐려지기 전에 켜요. 큰 차 옆을 지날 때 튀는 물에도 앞이 가려요.", "와이퍼를 껐어요." to null),
            listOf("운전석 안전벨트가 풀렸어요." to "안전벨트는 모든 좌석이 매요. (도로교통법 제50조)", "운전자가 안전벨트를 맸어요." to "안전벨트는 모든 좌석이 매요. (도로교통법 제50조)"),
            listOf("문이 열렸어요." to "문을 열기 전에는 뒤에서 오는 차와 자전거를 확인해요.", "문이 모두 닫혔어요." to null),
        )

        private const val MAX_PENDING = 3

        /** 줄 세워 둔 해설이 이보다 늦어지면 말하지 않는다(지난 일을 지금 일처럼 말하지 않으려고) */
        const val EXTRA_STALE_MS = 5_000L
        private const val BLINK_ON_WHY = "움직이기 전에 켜요. 회전은 30m 전부터, 차로 변경은 3초쯤 먼저 켜 둬요."
        private const val BLINK_OFF_WHY = "핸들이 풀리면 저절로 꺼져요. 차로 변경처럼 조금만 돌렸을 땐 직접 꺼요."
        private const val BRAKE_ON_WHY = "미리 가볍게 밟고, 멈추기 직전에 살짝 풀면 몸이 앞으로 쏠리지 않아요."
        private const val BRAKE_OFF_WHY = "발만 떼도 차가 천천히 나가요. 좁은 곳에서는 이 속도로만 움직여요."

        /**
         * 같은 조작이라도 상황에 맞는 요령만 고르고, 그 안에서 돌아가며 보여 준다. 맨 앞이 처음 나오는 말이다.
         * 상황에 맞는 묶음이 없으면 GENERAL을 쓴다. 주차 중에 횡단보도 얘기를 하는 것처럼 상황과 안 맞는 말은 하지 않는다(사용자 피드백).
         * shortcut: 초안, 전문가 검수 뒤 교체
         */
        private val VARIANTS: Map<String, Map<Scene, List<String>>> by lazy {
            mapOf(
                BLINK_ON_WHY to mapOf(
                    Scene.GENERAL to listOf(
                        BLINK_ON_WHY,
                        "차로를 바꿀 땐 깜빡이 3초, 사이드미러, 고개 돌려 옆 확인, 그다음에 옮겨요.",
                        "깜빡이는 비켜 달라는 뜻이 아니라 알리는 거예요. 틈이 있을 때만 들어가요.",
                    ),
                    Scene.TURN to listOf(
                        "회전할 곳 30m 전부터 켜요. 너무 일찍 켜면 그 앞 골목으로 들어가는 줄 알아요.",
                        "깜빡이를 켜면서 그쪽 사이드미러를 봐요. 옆으로 오토바이가 붙어 있을 수 있어요.",
                        "회전 차로에 미리 들어가 있어야 해요. 교차로 바로 앞 실선에서는 못 바꿔요.",
                    ),
                    Scene.PARKING to listOf(
                        "주차 칸에 들어갈 때도 깜빡이를 켜 두면 뒤차가 기다려 줘요.",
                        "주차장에서는 비상등을 켜고 후진하는 차도 많아요. 뒤차에 알리는 거예요.",
                    ),
                    Scene.PARKED to listOf(
                        "길가에서 출발할 땐 깜빡이를 켜고, 어깨 너머로 뒤에서 오는 차를 직접 봐요.",
                        "주차 칸에서 나갈 때도 나갈 방향 깜빡이를 켜요.",
                    ),
                ),
                BLINK_OFF_WHY to mapOf(
                    Scene.GENERAL to listOf(
                        BLINK_OFF_WHY,
                        "깜빡이를 켠 채 계속 달리면 뒤차가 헷갈려요. 끝나면 바로 꺼요.",
                    ),
                ),
                BRAKE_ON_WHY to mapOf(
                    Scene.GENERAL to listOf(
                        BRAKE_ON_WHY,
                        "앞차만 보지 말고 두세 대 앞의 브레이크등을 보면 미리 밟을 수 있어요.",
                        "멈출 땐 앞차 뒷바퀴가 보이는 거리에 서요.",
                        "긴 내리막에서는 계속 밟고 있지 말고 나눠 밟아요.",
                    ),
                    Scene.TURN to listOf(
                        "돌기 전, 곧게 가는 동안 속도를 줄여요. 도는 중에 세게 밟으면 차가 흔들려요.",
                        "회전해서 들어가는 길의 횡단보도에 사람이 있으면 그 앞에서 멈춰요.",
                    ),
                    Scene.CROSSING to listOf(
                        "횡단보도 앞이에요. 건너려는 사람이 서 있기만 해도 멈춰요.",
                        "횡단보도에서는 정지선 앞에 서요. 선을 넘어 서면 건너는 사람 길을 막아요.",
                    ),
                    Scene.SIGNAL to listOf(
                        "신호등 앞이에요. 노란불에 멈출 수 있으면 멈추고, 정지선을 넘지 않아요.",
                        "신호 대기 중에는 앞차 뒷바퀴가 보이는 거리에 서요.",
                    ),
                    Scene.SCHOOL to listOf(
                        "학교 근처예요. 어린이 보호구역이면 시속 30 이하로 미리 줄여요.",
                        "주차된 차 사이에서 아이가 나올 수 있어요. 발을 브레이크 위에 올려 둬요.",
                    ),
                    Scene.PARKING to listOf(
                        "주차할 땐 브레이크에서 발을 다 떼지 않고, 살짝 풀었다 밟았다 하며 속도를 맞춰요.",
                        "헷갈리면 일단 멈춰요. 멈춘 채로 핸들을 돌리고 다시 움직여도 돼요.",
                        "다 들어왔으면 완전히 멈춘 뒤에 P로 바꿔요.",
                    ),
                    Scene.PARKED to listOf(
                        "기어는 브레이크를 밟은 채로 바꿔요.",
                        "브레이크를 끝까지 밟았을 때 무릎이 살짝 굽혀지면 좌석 거리가 맞아요.",
                    ),
                ),
                BRAKE_OFF_WHY to mapOf(
                    Scene.GENERAL to listOf(
                        "출발할 땐 가속 페달을 살살 밟아요. 확 밟으면 몸이 뒤로 젖혀져요.",
                        "앞차가 출발하고 한 박자 뒤에 따라가요. 바짝 붙어 출발하지 않아요.",
                        BRAKE_OFF_WHY,
                    ),
                    Scene.SIGNAL to listOf(
                        "초록불로 바뀌어도 한 박자 쉬고 출발해요. 늦게 지나가는 차가 있어요.",
                        "출발하기 전에 횡단보도에 아직 사람이 남아 있는지 한 번 더 봐요.",
                    ),
                    Scene.CROSSING to listOf(
                        "사람이 다 건넌 걸 확인하고 출발해요. 뒤늦게 뛰어오는 사람도 있어요.",
                    ),
                    Scene.PARKING to listOf(
                        BRAKE_OFF_WHY,
                        "후진은 발만 살짝 떼서 걷는 속도로 움직여요. 가속 페달은 밟지 않아요.",
                    ),
                    Scene.PARKED to listOf(
                        "P에서는 발을 떼도 차가 움직이지 않아요. 내리기 전에 주차 브레이크도 걸어요.",
                    ),
                ),
                STEER_WHY to mapOf(
                    Scene.GENERAL to listOf(
                        STEER_WHY,
                        "차로를 바꿀 땐 핸들을 아주 조금만 돌려요. 시선은 옮겨 갈 차로 멀리.",
                        "핸들은 양손으로 9시와 3시 자리를 잡아요. 크게 돌릴 땐 손을 번갈아 넘겨요.",
                    ),
                    Scene.TURN to listOf(
                        STEER_WHY,
                        "돌고 나면 핸들이 저절로 풀리려 해요. 놓지 말고 손으로 따라가며 풀어요.",
                        "교차로에서는 바닥의 유도선을 따라 돌아요. 들어갈 차로를 미리 정해 둬요.",
                    ),
                ),
                REVERSE_WHY to mapOf(
                    Scene.GENERAL to listOf(
                        REVERSE_WHY,
                        "후진 주차는 사이드미러로 주차선과 뒷바퀴 사이를 봐요. 양쪽 간격이 같아지면 핸들을 풀어요.",
                        "한 번에 넣으려 하지 않아요. 틀어지면 앞으로 조금 뺐다가 다시 들어가요.",
                        "T자 주차는 칸을 조금 지나쳐 세운 뒤, 핸들을 끝까지 돌리고 아주 천천히 들어가요.",
                        "뒤가 들어갈 때 앞머리는 반대쪽으로 크게 돌아요. 앞 모서리와 옆 차 사이도 봐요.",
                    ),
                ),
            )
        }
        private const val STEER_WHY = "시선을 가려는 곳 멀리 두면 핸들은 따라가요. 돌린 만큼 풀어야 곧게 가요."
        private const val REVERSE_WHY = "후진할 때는 핸들을 돌린 쪽으로 차 뒤쪽이 움직여요."

        /** 핸들 반 바퀴(도) */
        const val HALF_TURN_DEG = 180f

        /** 후진에서 "돌려 놓고 브레이크를 뗐다"고 보는 핸들 각도. 임시값 */
        const val PARK_HOLD_DEG = 90f

        /** 가장 많이 돌린 각도에서 이만큼 되돌리면 "풀고 있다"고 본다. 임시값 */
        const val UNWIND_DEG = 90f
        private const val STEP_BACK_DEG = 30f

        /** 반 바퀴 수를 말로: 1 → 반 바퀴, 2 → 한 바퀴, 3 → 한 바퀴 반 */
        fun turnsLabel(halfTurns: Int): String {
            val whole = when (halfTurns / 2) {
                0 -> ""
                1 -> "한 바퀴"
                2 -> "두 바퀴"
                else -> "${halfTurns / 2}바퀴"
            }
            return if (halfTurns % 2 == 0) whole else if (whole.isEmpty()) "반 바퀴" else "$whole 반"
        }

        // shortcut: 아래 각도는 실차 데이터 없이 정한 임시값(센서 흔들림 약 ±1~2도 가정). 실차 측정 뒤 조정
        /** 핸들이 "돌아갔다"고 보는 각도(절댓값) */
        const val STEER_START_DEG = 20f

        /** 핸들이 "조금 돌아갔다"고 보는 각도(절댓값). 차선 변경처럼 작은 조작 */
        const val STEER_SMALL_DEG = 6f

        /** 핸들이 "가운데로 돌아왔다"고 보는 각도(절댓값). 작은 조작 시작 각도보다 작아야 한다 */
        const val STEER_CENTER_DEG = 3f

        /** 내비 안내가 이 거리(미터) 이내일 때부터 "안내한 뒤" 시간을 잰다. 임시값 */
        const val NAVI_NEAR_M = 300.0

        /** 남은 거리가 이만큼(미터) 넘게 늘면 다음 안내로 본다(위치 흔들림과 구분). 임시값 */
        const val NAVI_JUMP_M = 50.0

        /** 깜빡이를 켠 뒤 핸들 조작을 같은 동작으로 묶는 시간(밀리초) */
        const val LINK_WINDOW_MS = 10_000L
    }
}
