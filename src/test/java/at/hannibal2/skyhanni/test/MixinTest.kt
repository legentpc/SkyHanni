package at.hannibal2.skyhanni.test

import at.hannibal2.skyhanni.events.chat.TabCompletionEvent
import at.hannibal2.skyhanni.features.commands.suggestions.SuggestionProvider
import at.hannibal2.skyhanni.features.commands.tabcomplete.PlayerTabComplete
import at.hannibal2.skyhanni.features.commands.tabcomplete.TabComplete
import at.hannibal2.skyhanni.mixins.init.SkyHanniMixinPlugin
import com.llamalad7.mixinextras.sugar.ref.LocalRef
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.spongepowered.asm.mixin.MixinEnvironment
import org.spongepowered.asm.mixin.transformer.IMixinTransformer
import java.util.concurrent.CompletableFuture

/**
 * Audits mixins to ensure their validity without launching a full Minecraft client.
 * Implementation inspired by [Skyblocker](https://github.com/SkyblockerMod/Skyblocker).
 */
class MixinTest {

    @Test
    fun `mixins load successfully`() {
        val environment = MixinEnvironment.getCurrentEnvironment()
        Assertions.assertInstanceOf(IMixinTransformer::class.java, environment.activeTransformer)
        environment.audit()
    }

    @Test
    fun `mixin discovery is successful`() {
        val classLoader = javaClass.classLoader
        val discovered = SkyHanniMixinPlugin().mixins
        Assertions.assertTrue(discovered.isNotEmpty()) {
            "Mixin discovery returned nothing, so this test would pass without inspecting a single mixin. " +
                "SkyHanniMixinPlugin resolves them relative to its own code source, " +
                "which the mixinTest classpath has to expose."
        }
        discovered.forEach { mixin ->
            val path = "$MIXIN_PACKAGE_PATH/${mixin.replace('.', '/')}.class"
            checkNotNull(classLoader.getResource(path)) {
                "Mixin $mixin was discovered but $path is not on the classpath"
            }
        }
    }

    @Test
    fun `new contains matches keep the original completion path available`() {
        val input = "/call li"
        val containsOnly = TabCompletionEvent(input, input, emptyList())
        containsOnly.addSuggestionsContaining(listOf("Charlie", "Orville"))

        Assertions.assertEquals(listOf("Charlie"), containsOnly.intoSuggestionArray()?.toList())
        Assertions.assertFalse(containsOnly.hasBaselineSuggestions)

        val prefix = TabCompletionEvent(input, input, emptyList())
        prefix.addSuggestionsContaining(listOf("Lily", "Charlie"))
        Assertions.assertTrue(prefix.hasBaselineSuggestions)

        val root = TabCompletionEvent(input, "/call", emptyList())
        root.addSuggestionsContaining(listOf("Lily", "Charlie"))
        Assertions.assertEquals(listOf("Lily"), root.intoSuggestionArray()?.toList())

        val sack = TabCompletionEvent("/gfs melon", "/gfs melon", emptyList())
        sack.addSuggestionsUnchecked(listOf("ENCHANTED_MELON"))
        Assertions.assertTrue(sack.hasBaselineSuggestions)
    }

    @Test
    fun `whisper and message commands keep prefix matching`() {
        val provider = SuggestionProvider.build {
            parent("w", "msg", "tell", "boop", "boo") {
                literal("Steve", "MySteve", "Alex")
            }
        }

        for (command in listOf("w", "msg", "tell", "boop", "boo")) {
            Assertions.assertEquals(listOf("Steve"), provider.getSuggestions("$command StE"))
            Assertions.assertEquals(emptyList<String>(), provider.getSuggestions("$command eve"))
        }
    }

    @Test
    fun `prefix matches are ordered before contains matches`() {
        val input = "/gfs mel"
        val builder = SuggestionsBuilder(input, 5)
        builder.suggest("ENCHANTED_MELON")
        builder.suggest("MELON_SLICE")

        val result = TabComplete.prioritizeSuggestions(builder.build(), "mel")

        Assertions.assertEquals(
            listOf("MELON_SLICE", "ENCHANTED_MELON"),
            result.list.map { it.text },
        )
    }

    @Test
    fun `cursor matches use text before cursor while baseline uses full input`() {
        val containsOnly = TabCompletionEvent("/call lix", "/call li", emptyList())
        containsOnly.addSuggestionsContaining(listOf("Lily", "Charlie"))

        Assertions.assertFalse(containsOnly.hasBaselineSuggestions)
        Assertions.assertEquals(
            listOf("Lily", "Charlie"),
            containsOnly.intoSuggestionArray()?.toList(),
        )

        val baselineOnly = TabCompletionEvent("/call li or", "/call li", emptyList())
        baselineOnly.addSuggestionsContaining(listOf("Orion"))

        Assertions.assertTrue(baselineOnly.hasBaselineSuggestions)
        Assertions.assertNull(baselineOnly.intoSuggestionArray())
    }

    @Test
    fun `w keeps prefix completion and bypasses enhanced merging`() {
        mockkObject(PlayerTabComplete)
        try {
            every { PlayerTabComplete.handleTabComplete("w eve") } returns listOf("Steve", "MySteve")
            every { PlayerTabComplete.handleTabComplete("w ste") } returns listOf("Steve", "MySteve")
            every { PlayerTabComplete.handleTabComplete("w stex other") } returns emptyList()

            val containsOnly = TabCompletionEvent("/w eve", "/w eve", emptyList())
            TabComplete.handleTabComplete(containsOnly)
            Assertions.assertNull(containsOnly.intoSuggestionArray())
            Assertions.assertFalse(containsOnly.hasBaselineSuggestions)

            val prefix = TabCompletionEvent("/w ste", "/w ste", emptyList())
            TabComplete.handleTabComplete(prefix)
            Assertions.assertEquals(listOf("Steve"), prefix.intoSuggestionArray()?.toList())
            Assertions.assertTrue(prefix.hasBaselineSuggestions)

            val midCursor = TabCompletionEvent("/w stex other", "/w ste", emptyList())
            TabComplete.handleTabComplete(midCursor)
            Assertions.assertNull(midCursor.intoSuggestionArray())
            verify(exactly = 1) { PlayerTabComplete.handleTabComplete("w ste") }

            val deferred = mockk<LocalRef<SuggestionsBuilder>>()
            val original = SuggestionsBuilder("/w eve", 3).suggest("Alice").buildFuture()
            val result = TabComplete.onCompletionReturn(original, 3, "/w eve", "/w eve", deferred)
            Assertions.assertSame(original, result)
            verify(exactly = 0) { deferred.get() }
        } finally {
            unmockkObject(PlayerTabComplete)
        }
    }

    @Test
    fun `matching ranges keep existing order and prioritize added prefixes`() {
        val input = "/call ev"
        val serverBuilder = SuggestionsBuilder(input, 6)
        serverBuilder.suggest("Alpha")
        serverBuilder.suggest("Zed")
        val serverResult = serverBuilder.build()
        val original = Suggestions(serverResult.range, serverResult.list.reversed())

        val additional = SuggestionsBuilder(input, 6)
        additional.suggest("MySteve")
        additional.suggest("EveFirst")

        val deferred = mockk<LocalRef<SuggestionsBuilder>>()
        every { deferred.get() } returns additional

        val result = TabComplete.onCompletionReturn(
            CompletableFuture.completedFuture(original),
            6,
            input,
            input,
            deferred,
        ).join()

        val existingTexts = original.list.map { it.text }
        Assertions.assertEquals(existingTexts, result.list.take(existingTexts.size).map { it.text })
        Assertions.assertEquals(
            listOf("EveFirst", "MySteve"),
            result.list.drop(existingTexts.size).map { it.text },
        )
    }

    companion object {
        private const val MIXIN_PACKAGE_PATH = "at/hannibal2/skyhanni/mixins/transformers"
    }
}
