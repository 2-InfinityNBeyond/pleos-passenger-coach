package app.passengercoach.demo

import app.passengercoach.coach.LatLon
import app.passengercoach.coach.RouteMath

/**
 * 데모(시험 자료)에서 지도에 그릴 길. 실제 주행이 아니다.
 * 길 모양은 OpenStreetMap 자료(공개 OSRM 시험 서버로 2026-11-15에 한 번 받음, © OpenStreetMap contributors)이며,
 * 에뮬레이터 기본 위치(성남) 근처의 실제 길이라 지도 위에 어긋나지 않게 그려진다.
 */
object DemoRoute {
    val points: List<LatLon> = listOf(
        37.420363 to 127.126591, 37.420362 to 127.125644, 37.420261 to 127.125648, 37.419928 to 127.125644,
        37.419883 to 127.125647, 37.419816 to 127.125648, 37.419324 to 127.125666, 37.419213 to 127.12569,
        37.418909 to 127.125703, 37.418769 to 127.125709, 37.418771 to 127.125473, 37.418774 to 127.125009,
        37.418774 to 127.124906, 37.418769 to 127.124808, 37.418759 to 127.124735, 37.418744 to 127.124677,
        37.418714 to 127.12463, 37.418676 to 127.124594, 37.418612 to 127.124584, 37.417693 to 127.124572,
        37.41758 to 127.12457, 37.417457 to 127.124566, 37.416985 to 127.124552, 37.416824 to 127.124547,
    ).map { LatLon(it.first, it.second) }

    /** 꺾이는 지점들: (출발점부터의 거리, 왼쪽이면 true) */
    val turns: List<Pair<Double, Boolean>> = RouteMath.turns(points)

    /** 첫 회전까지의 거리(미터). 데모 대본의 속도를 여기에 맞춘다 */
    val firstTurnM: Double = turns.first().first
}