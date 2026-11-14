package app.passengercoach.coach

/**
 * 방금 장면 되짚기: 주행 중에는 앞을 보고, 멈췄을 때(정차, 주차) 방금 한 조작을 순서대로 다시 본다.
 * 다시 움직이면 비우고 새 구간을 모은다.
 */
class Recap {
    private val items = ArrayDeque<String>()
    private var wasStill = false

    fun add(text: String) {
        items.addLast(text)
        if (items.size > MAX) items.removeFirst()
    }

    /** [still]: 지금 멈춰 있는가. 멈춰 있으면 방금 구간의 조작을 순서대로 돌려준다 */
    fun update(still: Boolean): List<String> {
        if (wasStill && !still) items.clear()
        wasStill = still
        return if (still) items.toList() else emptyList()
    }

    companion object {
        // 알아 두기 카드와 함께 한 화면에 들어오는 줄 수
        const val MAX = 3
    }
}