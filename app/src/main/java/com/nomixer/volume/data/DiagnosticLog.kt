package com.nomixer.volume.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * A small in-memory log the app itself can show and let the user copy in
 * full -- for diagnostics that need to survive intact rather than being cut
 * down to whatever a two-line Toast happens to fit (the glass backdrop
 * capture, in Service.kt, is the first thing that needed this: its most
 * useful detail, an exception's class name, kept landing exactly where a
 * Toast on one real device truncated). The accessibility service and the
 * main activity run in the same process, so a plain in-memory object is
 * enough to share entries between them -- nothing here needs to survive a
 * process death, only a foreground app switch to go look at it.
 */
object DiagnosticLog {
    data class Entry(val timestampMs: Long, val tag: String, val message: String)

    private const val MAX_ENTRIES = 300

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    fun log(tag: String, message: String) {
        _entries.update { current -> (current + Entry(System.currentTimeMillis(), tag, message)).takeLast(MAX_ENTRIES) }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

fun DiagnosticLog.Entry.formatted(): String = "${timeFormat.format(Date(timestampMs))} [$tag] $message"

fun List<DiagnosticLog.Entry>.formatted(): String = joinToString("\n") { it.formatted() }
