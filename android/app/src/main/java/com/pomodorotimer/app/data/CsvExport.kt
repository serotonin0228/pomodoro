package com.pomodorotimer.app.data

import android.content.Context
import android.net.Uri
import com.pomodorotimer.app.domain.FocusRecord
import com.pomodorotimer.app.domain.Mode
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

/**
 * CSV export, byte-compatible with the reference implementation: UTF-8 BOM so Excel detects the
 * encoding, every field quoted, ISO-8601 timestamps.
 *
 * The destination is chosen through the system file picker (SAF), so no storage permission and no
 * FileProvider are involved.
 */
object CsvExport {

    private const val BOM = "\uFEFF"

    fun fileName(now: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        return "Pomodoro-$stamp.csv"
    }

    fun build(records: List<FocusRecord>): String {
        val rows = buildList {
            add(listOf("记录ID", "模式", "开始时间", "结束时间", "有效专注秒数"))
            records.filter { !it.deleted }.forEach { r ->
                add(
                    listOf(
                        r.id,
                        if (r.mode == Mode.COUNTDOWN) "倒计时" else "正计时",
                        Instant.ofEpochMilli(r.start).toString(),
                        Instant.ofEpochMilli(r.end).toString(),
                        r.seconds.toString(),
                    ),
                )
            }
        }
        return BOM + rows.joinToString("\r\n") { row ->
            row.joinToString(",") { cell -> "\"" + cell.replace("\"", "\"\"") + "\"" }
        }
    }

    /** Writes the export to the document the user picked. Returns false when the target is gone. */
    fun writeTo(context: Context, uri: Uri, records: List<FocusRecord>): Boolean {
        val text = build(records)
        return runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: return false
            true
        }.getOrDefault(false)
    }
}
