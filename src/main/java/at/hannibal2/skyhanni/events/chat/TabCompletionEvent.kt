package at.hannibal2.skyhanni.events.chat

import at.hannibal2.skyhanni.api.event.SkyHanniEvent

/**
 * Fired when the command dispatcher collects completion suggestions.
 *
 * @param leftOfCursor First dispatcher text; currently the complete input.
 * @param fullText Second dispatcher text; currently the text before the cursor.
 * @param originalCompletions Suggestions already supplied by the dispatcher.
 */
class TabCompletionEvent(
    val leftOfCursor: String,
    val fullText: String,
    private val originalCompletions: List<String>,
) : SkyHanniEvent() {
    val lastWord = leftOfCursor.substringAfterLast(' ')
    private val additionalSuggestions = mutableSetOf<String>()
    var hasBaselineSuggestions = false
        private set

    fun addSuggestion(suggestion: String) {
        if (!suggestion.startsWith(lastWord, ignoreCase = true)) return
        val adjustedSuggestion = suggestion.removePrefix("/")
        additionalSuggestions.add(adjustedSuggestion)
        hasBaselineSuggestions = true
    }

    fun addSuggestions(suggestions: Iterable<String>) {
        suggestions.forEach(this::addSuggestion)
    }

    fun addSuggestionUnchecked(suggestion: String) {
        val adjustedSuggestion = suggestion.removePrefix("/")
        additionalSuggestions.add(adjustedSuggestion)
        hasBaselineSuggestions = true
    }

    fun addSuggestionsUnchecked(suggestions: Iterable<String>) {
        suggestions.forEach(this::addSuggestionUnchecked)
    }

    fun addSuggestionsContaining(suggestions: Iterable<String>) {
        if (!fullText.contains(' ')) {
            addSuggestions(suggestions)
            return
        }

        val query = fullText.substringAfterLast(' ')
        for (suggestion in suggestions) {
            if (suggestion.startsWith(lastWord, ignoreCase = true)) {
                hasBaselineSuggestions = true
            }
            if (suggestion.contains(query, ignoreCase = true)) {
                additionalSuggestions.add(suggestion.removePrefix("/"))
            }
        }
    }

    fun addSuggestionsWithBaseline(suggestions: Iterable<String>, hasBaseline: Boolean) {
        if (hasBaseline) hasBaselineSuggestions = true
        for (suggestion in suggestions) {
            additionalSuggestions.add(suggestion.removePrefix("/"))
        }
    }

    val command = if (leftOfCursor.startsWith("/"))
        leftOfCursor.substring(1).substringBefore(" ").lowercase()
    else ""

    fun isCommand(commandName: String): Boolean {
        return commandName.equals(command, ignoreCase = true)
    }

    fun intoSuggestionArray(): Array<String>? {
        if (additionalSuggestions.isEmpty()) return null
        return (originalCompletions + additionalSuggestions).toTypedArray()
    }
}
