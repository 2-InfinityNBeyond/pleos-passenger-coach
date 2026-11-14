package app.passengercoach.coach

import java.io.File

data class Record(val atMs: Long, val text: String)

/**
 * 학습 기록: 해설 문장과 시각을 이 기기 안의 파일 하나에만 저장한다. 밖으로 보내지 않는다.
 * 사용자가 설정에서 켠 경우에만 쓰고, 동의를 철회하면 파일을 지운다.
 */
class RecordStore(private val file: File) {
    fun add(atMs: Long, text: String) {
        file.appendText("$atMs\t$text\n")
        // 파일이 끝없이 커지지 않게 최근 기록만 남긴다
        if (file.length() > TRIM_BYTES) file.writeText(load().joinToString("") { "${it.atMs}\t${it.text}\n" })
    }

    /** 최근 [MAX] 개를 오래된 것부터 돌려준다. 깨진 줄은 건너뛴다. */
    fun load(): List<Record> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            val at = line.substringBefore('\t').toLongOrNull() ?: return@mapNotNull null
            Record(at, line.substringAfter('\t', ""))
        }.takeLast(MAX)
    }

    fun clear() {
        file.delete()
    }

    companion object {
        const val FILE_NAME = "records.tsv"

        /** "물어볼 것"으로 표시한 순간을 따로 담는 파일 */
        const val ASKS_FILE = "asks.tsv"
        const val MAX = 300
        const val TRIM_BYTES = 64_000L
    }
}
