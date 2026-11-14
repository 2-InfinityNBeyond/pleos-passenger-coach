package app.passengercoach.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RecordStoreTest {
    private fun store() = RecordStore(File.createTempFile("records", ".tsv").apply { delete(); deleteOnExit() })

    @Test
    fun `저장한 기록을 순서대로 읽고 지울 수 있다`() {
        val s = store()
        assertTrue(s.load().isEmpty())
        s.add(1_000, "브레이크를 밟았어요.")
        s.add(2_000, "브레이크를 뗐어요.")
        assertEquals(listOf(Record(1_000, "브레이크를 밟았어요."), Record(2_000, "브레이크를 뗐어요.")), s.load())
        s.clear()
        assertTrue(s.load().isEmpty())
    }

    @Test
    fun `기록이 많아져도 최근 것만 남긴다`() {
        val s = store()
        repeat(3_000) { s.add(it.toLong(), "기어를 P(주차)에서 D(주행)로 바꿨어요.") }
        val list = s.load()
        assertEquals(RecordStore.MAX, list.size)
        assertEquals(2_999L, list.last().atMs)
    }
}