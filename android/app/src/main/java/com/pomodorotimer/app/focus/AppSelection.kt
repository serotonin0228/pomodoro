package com.pomodorotimer.app.focus

/** Selected apps are grouped first when the picker opens; search matches name or package. */
object AppSelection {
    fun order(apps: List<Pair<String, String>>, selected: Set<String>): List<Pair<String, String>> =
        apps.sortedWith(compareBy<Pair<String, String>> { it.first !in selected }.thenBy { it.second.lowercase() })

    fun filter(apps: List<Pair<String, String>>, query: String): List<Pair<String, String>> {
        val needle = query.trim()
        return if (needle.isEmpty()) apps else apps.filter {
            it.first.contains(needle, ignoreCase = true) || it.second.contains(needle, ignoreCase = true)
        }
    }

    fun selectAll(selected: Set<String>, visible: Set<String>): Set<String> = selected + visible
    fun invert(selected: Set<String>, visible: Set<String>): Set<String> = (selected - visible) + (visible - selected)
}
