package app.passengercoach.coach

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 합성 후방 화면(시뮬레이션)의 계산. 실제 카메라 영상이 아니라, 핸들 각도로 "차가 지나갈 길"을 계산해 그린다.
 * 좌표: l = 운전자 기준 오른쪽(미터), b = 차 뒤쪽(미터). alpha = 차 뒤쪽이 향하는 방향(0 = 곧은 뒤, 양수 = 왼쪽).
 * shortcut: 핸들 비율, 축 거리, 차 폭은 일반 승용차를 가정한 값. 차종별 값이 공개되면 교체
 */
object RearMath {
    /** 핸들 각도 ÷ 앞바퀴 각도 */
    const val STEER_RATIO = 15f
    const val WHEELBASE_M = 2.9f
    const val HALF_TRACK_M = 0.95f

    /** 데모 장면에서만 쓰는 후진 속도(미터/초). 실제 차량 속도 신호는 SDK에 없다 */
    const val DEMO_SPEED_MPS = 0.8f

    data class Pose(val l: Float = 0f, val b: Float = 0f, val alpha: Float = 0f)

    data class Point(val l: Float, val b: Float)

    /** 회전 반지름(미터). 양수 = 차 뒤쪽이 왼쪽으로 감, 음수 = 오른쪽, null = 곧게 감 */
    fun radius(steeringDeg: Float): Float? {
        val wheel = Math.toRadians((steeringDeg / STEER_RATIO).toDouble())
        return if (abs(wheel) < 0.005) null else (WHEELBASE_M / tan(wheel)).toFloat()
    }

    /** 지금 핸들 각도로 [dist] 미터 후진했을 때의 위치와 방향 */
    fun advance(p: Pose, steeringDeg: Float, dist: Float): Pose {
        val r = radius(steeringDeg)
        // 차 기준 이동량: 왼쪽으로 휘면 l이 줄어든다
        val dl: Float
        val db: Float
        val dAlpha: Float
        if (r == null) {
            dl = 0f; db = dist; dAlpha = 0f
        } else {
            dAlpha = dist / r
            dl = -r * (1 - cos(dAlpha))
            db = r * sin(dAlpha)
        }
        val (rightL, rightB) = cos(p.alpha) to sin(p.alpha)
        val (backL, backB) = -sin(p.alpha) to cos(p.alpha)
        return Pose(p.l + rightL * dl + backL * db, p.b + rightB * dl + backB * db, p.alpha + dAlpha)
    }

    /** 가이드선: 한쪽 뒷바퀴([side] = -1 왼쪽, +1 오른쪽)가 지나갈 길을 차 기준 좌표로 돌려준다 */
    fun guide(steeringDeg: Float, side: Int, lengthM: Float = 5f, stepM: Float = 0.25f): List<Point> {
        val points = ArrayList<Point>()
        var s = 0f
        while (s <= lengthM + 0.001f) {
            val c = advance(Pose(), steeringDeg, s)
            points += Point(c.l + cos(c.alpha) * side * HALF_TRACK_M, c.b + sin(c.alpha) * side * HALF_TRACK_M)
            s += stepM
        }
        return points
    }

    /** 바닥의 한 점(같은 좌표계)을 차 [p]에서 본 좌표로 바꾼다 */
    fun seenFrom(p: Pose, l: Float, b: Float): Point {
        val (dl, db) = (l - p.l) to (b - p.b)
        return Point(dl * cos(p.alpha) + db * sin(p.alpha), -dl * sin(p.alpha) + db * cos(p.alpha))
    }
}
