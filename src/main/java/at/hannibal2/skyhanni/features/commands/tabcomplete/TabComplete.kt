package at.hannibal2.skyhanni.features.commands.tabcomplete

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.events.chat.TabCompletionEvent
import at.hannibal2.skyhanni.features.commands.PartyCommands
import at.hannibal2.skyhanni.features.commands.ViewRecipeCommand
import at.hannibal2.skyhanni.features.misc.pathfind.NavigationHelper
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.test.command.ErrorManager
import com.llamalad7.mixinextras.sugar.ref.LocalRef
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import java.util.concurrent.CompletableFuture

@SkyHanniModule
object TabComplete {

    private val eventCommands = setOf("call", "gfs", "getfromsacks", "viewrecipe", "warp")
    private val navigationCommands = setOf("shnav", "shnavigate")

    @HandleEvent
    fun handleTabComplete(event: TabCompletionEvent) {
        val splits = event.leftOfCursor.split(" ")
        if (splits.size <= 1) return
        var command = splits.first().lowercase()
        if (!command.startsWith("/")) return
        command = command.substring(1)

        customTabComplete(event.leftOfCursor.substring(1), command)?.let {
            if (command == "viewrecipe") {
                event.addSuggestionsContaining(it)
            } else {
                event.addSuggestions(it)
            }
        }
    }

    fun onCompletionRequest(
        start: Int,
        fullInput: String,
        beforeCursor: String,
        cir: CallbackInfoReturnable<CompletableFuture<Suggestions>>,
        deferred: LocalRef<SuggestionsBuilder>,
    ) {
        if (!beforeCursor.contains(' ')) return

        val command = argumentCommand(beforeCursor)
        if (command !in eventCommands) {
            val builder = buildFromEvent(start, fullInput, beforeCursor, emptyList(), null) ?: return
            cir.returnValue = builder.buildFuture()
            return
        }

        try {
            val builder = buildFromEvent(start, fullInput, beforeCursor, emptyList(), deferred) ?: return
            val query = beforeCursor.substringAfterLast(' ')
            val ordered = prioritizeSuggestions(builder.build(), query)
            cir.returnValue = CompletableFuture.completedFuture(ordered)
        } catch (error: Throwable) {
            ErrorManager.logErrorWithData(error, "Failed to prepare tab suggestions")
        }
    }

    fun onCompletionReturn(
        original: CompletableFuture<Suggestions>,
        start: Int,
        fullInput: String,
        beforeCursor: String,
        deferred: LocalRef<SuggestionsBuilder>,
    ): CompletableFuture<Suggestions> {
        return if (!beforeCursor.contains(' ')) {
            original.thenApply { suggestions ->
                val existing = suggestions.list.map { it.text }
                buildFromEvent(start, fullInput, beforeCursor, existing, null)?.build() ?: suggestions
            }
        } else {
            val command = argumentCommand(beforeCursor)
            if (command !in eventCommands && command !in navigationCommands) {
                original
            } else {
                try {
                    val builder = deferred.get()
                    when {
                        builder != null -> mergeSuggestions(original, builder, beforeCursor)
                        command in navigationCommands -> NavigationHelper.prioritizeSuggestions(original, beforeCursor)
                        else -> original
                    }
                } catch (error: Throwable) {
                    ErrorManager.logErrorWithData(error, "Failed to update tab suggestions")
                    original
                }
            }
        }
    }

    fun prioritizeSuggestions(suggestions: Suggestions, query: String): Suggestions =
        Suggestions(
            suggestions.range,
            suggestions.list.sortedBy { !it.text.startsWith(query, ignoreCase = true) },
        )

    private fun mergeSuggestions(
        original: CompletableFuture<Suggestions>,
        builder: SuggestionsBuilder,
        beforeCursor: String,
    ): CompletableFuture<Suggestions> {
        val query = beforeCursor.substringAfterLast(' ')
        val additional = prioritizeSuggestions(builder.build(), query)
        if (additional.list.isEmpty()) return original

        return original.thenApply { existing ->
            try {
                when {
                    existing.list.isEmpty() -> additional

                    existing.range == additional.range -> {
                        val seen = existing.list.toHashSet()
                        val combined = existing.list.toMutableList()

                        for (suggestion in additional.list) {
                            if (seen.add(suggestion)) combined.add(suggestion)
                        }

                        Suggestions(existing.range, combined)
                    }

                    else -> Suggestions.merge(beforeCursor, listOf(existing, additional))
                }
            } catch (error: Throwable) {
                ErrorManager.logErrorWithData(error, "Failed to merge tab suggestions")
                existing
            }
        }
    }

    private fun argumentCommand(input: String): String {
        if (!input.startsWith("/") || !input.contains(' ')) return ""
        return input.substring(1).substringBefore(' ').lowercase()
    }

    private fun buildFromEvent(
        start: Int,
        fullInput: String,
        beforeCursor: String,
        existing: List<String>,
        deferred: LocalRef<SuggestionsBuilder>?,
    ): SuggestionsBuilder? {
        val event = TabCompletionEvent(fullInput, beforeCursor, existing)
        event.post()

        val additional = event.intoSuggestionArray()
        if (additional == null && (deferred == null || !event.hasBaselineSuggestions)) return null

        val builder = SuggestionsBuilder(beforeCursor, start)
        additional?.forEach { builder.suggest(it) }

        if (deferred != null && !event.hasBaselineSuggestions) {
            deferred.set(builder)
            return null
        }

        return builder
    }

    @Suppress("ReturnCount")
    private fun customTabComplete(fullCommand: String, command: String): List<String>? {
        PlayerTabComplete.handleTabComplete(fullCommand)?.let { return it }
        PartyCommands.customTabComplete(command)?.let { return it }
        ViewRecipeCommand.customTabComplete(command)?.let { return it }

        return null
    }
}
