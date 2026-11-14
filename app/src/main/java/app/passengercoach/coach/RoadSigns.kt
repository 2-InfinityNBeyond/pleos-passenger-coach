package app.passengercoach.coach

/** 화면에 표지판처럼 보여 줄 도로 정보 한 건 */
data class RoadSign(val label: String, val distanceM: Int)

/**
 * 내비가 알려 주는 단속 카메라와 주의 지점을 필기시험에 나오는 말로 바꾼다.
 * 모르는 종류는 보여 주지 않는다(틀린 이름을 붙이지 않으려고).
 */
object RoadSigns {
    fun caution(type: String): String? = when (type) {
        "SCHOOL_ZONE" -> "어린이 보호구역"
        "SPEED_BUMP" -> "과속방지턱"
        "ACCIDENT_PRONE_AREA" -> "사고 잦은 곳"
        "RAILROAD_CROSSING" -> "철길 건널목"
        "FOG_AREA" -> "안개 잦은 곳"
        "FALLING_ROCK_AREA" -> "낙석 주의"
        "DOWNHILL_CAUTION_AREA" -> "내리막 주의"
        "WILDLIFE_CROSSING" -> "야생동물 주의"
        "NARROW_ROAD" -> "도로 폭 좁아짐"
        "HIGHWAY_CONSTRUCTION" -> "공사 구간"
        "NO_PARKING_ZONE" -> "주정차 금지"
        "TRAFFIC_LIGHT_VIOLATION_ENFORCEMENT" -> "신호 위반 단속"
        "BUS_LANE_VIOLATION_ENFORCEMENT" -> "버스전용차로 단속"
        "CUT_IN_VIOLATION_ENFORCEMENT" -> "끼어들기 단속"
        "BLOCK_INTERSECTION_ENFORCEMENT" -> "꼬리물기 단속"
        "SLIPPERY_ROAD_ALERT" -> "미끄러운 도로"
        else -> null
    }

    fun camera(type: String, speedLimit: Int): String? {
        val limit = if (speedLimit > 0) " $speedLimit" else ""
        return when {
            type.contains("SCHOOL_ZONE") -> "어린이 보호구역 단속$limit"
            type.contains("TRAFFIC_LIGHT") -> "신호·과속 단속$limit"
            type.contains("SECTION") -> "구간 단속$limit"
            type.startsWith("SPEED_CAMERA") || type == "VARIABLE_SPEED_CAMERA" -> "과속 단속$limit"
            else -> null
        }
    }

    /** 가장 가까운 것 하나만 고른다(화면에 글자를 늘리지 않으려고) */
    fun nearest(signs: List<RoadSign>): RoadSign? = signs.filter { it.distanceM >= 0 }.minByOrNull { it.distanceM }
}
