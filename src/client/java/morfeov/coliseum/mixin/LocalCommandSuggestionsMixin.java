package morfeov.coliseum.mixin;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CommandSuggestions.class)
public abstract class LocalCommandSuggestionsMixin {
    @Unique private static final Map<String, List<String>> COLISEUM_COMMANDS = Map.of(
        ".help", List.of(), ".vclip", List.of(), ".bind", List.of("add", "remove", "list", "clear"),
        ".gps", List.of("off", "remove"), ".friend", List.of("add", "remove", "list", "clear"),
        ".blockesp", List.of("add", "remove", "clear", "list"), ".autosell", List.of("hand", "inventory", "stop"),
        ".dataset", List.of("record", "stop", "status", "setup", "train", "use", "cancel"));

    @Shadow @Final private Screen screen;
    @Shadow @Final private EditBox input;
    @Shadow @Final private List<?> commandUsage;
    @Shadow private boolean allowSuggestions;
    @Shadow private boolean keepSuggestions;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow public abstract void showSuggestions(boolean narrate);
    @Shadow public abstract void hide();

    @Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
    private void coliseum$completeLocalCommand(CallbackInfo ci) {
        String value = input.getValue();
        if (!(screen instanceof ChatScreen) || !value.startsWith(".")) return;
        ci.cancel();
        if (keepSuggestions) return;
        hide();
        commandUsage.clear();
        input.setSuggestion(null);
        String prefix = value.substring(0, input.getCursorPosition());
        int separator = prefix.indexOf(' ');
        String command = (separator < 0 ? prefix : prefix.substring(0, separator)).toLowerCase(Locale.ROOT);
        if (command.equals(".neuro")) command = ".dataset";
        int start = separator < 0 ? 0 : separator + 1;
        var builder = new SuggestionsBuilder(prefix, start);
        var candidates = separator < 0 ? COLISEUM_COMMANDS.keySet() : COLISEUM_COMMANDS.getOrDefault(command, List.of());
        for (String candidate : candidates) {
            if (candidate.startsWith(builder.getRemainingLowerCase())) builder.suggest(candidate);
        }
        pendingSuggestions = builder.buildFuture();
        if (allowSuggestions) showSuggestions(false);
    }
}
