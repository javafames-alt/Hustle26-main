package im.yaHustler.mixin;

import im.yaHustler.module.impl.misc.NameProtect;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Font.class)
public abstract class NameProtectFontMixin {
    @ModifyVariable(method="prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",at=@At("HEAD"),argsOnly=true)
    private String hustle263$protectString(String text){return NameProtect.protect(text);}
    @ModifyVariable(method={"prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;","prepare8xTextOutline"},at=@At("HEAD"),argsOnly=true)
    private FormattedCharSequence hustle263$protectSequence(FormattedCharSequence text){return NameProtect.protect(text);}
    @ModifyVariable(method="width(Ljava/lang/String;)I",at=@At("HEAD"),argsOnly=true)
    private String hustle263$protectWidth(String text){return NameProtect.protect(text);}
    @ModifyVariable(method="width(Lnet/minecraft/util/FormattedCharSequence;)I",at=@At("HEAD"),argsOnly=true)
    private FormattedCharSequence hustle263$protectSequenceWidth(FormattedCharSequence text){return NameProtect.protect(text);}
}
