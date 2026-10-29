package at.hannibal2.skyhanni.mixins.transformers;

import at.hannibal2.skyhanni.features.commands.tabcomplete.TabComplete;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;

@Mixin(value = CommandDispatcher.class, remap = false)
public abstract class MixinCommandDispatcher<S> {

    @Inject(method = "getCompletionSuggestions(Lcom/mojang/brigadier/ParseResults;I)Ljava/util/concurrent/CompletableFuture;", at = @At(value = "INVOKE", target = "Ljava/lang/String;toLowerCase(Ljava/util/Locale;)Ljava/lang/String;"), cancellable = true)
    public void getCompletionSuggestions(
        ParseResults<S> parse,
        int cursor,
        CallbackInfoReturnable<CompletableFuture<Suggestions>> cir,
        @Local(ordinal = 1) int start,
        @Local(ordinal = 0) String fullInput,
        @Local(ordinal = 1) String beforeCursor,
        @Share("skyhanni$deferredSuggestions") LocalRef<SuggestionsBuilder> deferred
    ) {
        TabComplete.INSTANCE.onCompletionRequest(start, fullInput, beforeCursor, cir, deferred);
    }

    @ModifyReturnValue(method = "getCompletionSuggestions(Lcom/mojang/brigadier/ParseResults;I)Ljava/util/concurrent/CompletableFuture;", at = @At(value = "RETURN"))
    public CompletableFuture<Suggestions> getCompletionSuggestionsWIthExisting(
        CompletableFuture<Suggestions> original,
        @Local(ordinal = 1) int start,
        @Local(ordinal = 0) String fullInput,
        @Local(ordinal = 1) String beforeCursor,
        @Share("skyhanni$deferredSuggestions") LocalRef<SuggestionsBuilder> deferred
    ) {
        return TabComplete.INSTANCE.onCompletionReturn(original, start, fullInput, beforeCursor, deferred);
    }
}
