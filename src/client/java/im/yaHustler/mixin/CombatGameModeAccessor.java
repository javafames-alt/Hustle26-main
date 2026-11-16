package im.yaHustler.mixin;
@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.multiplayer.MultiPlayerGameMode.class)
public interface CombatGameModeAccessor {
    @org.spongepowered.asm.mixin.gen.Accessor("destroyBlockPos") net.minecraft.core.BlockPos hustle263$destroyBlock();
    @org.spongepowered.asm.mixin.gen.Accessor("destroyProgress") float hustle263$destroyProgress();
    @org.spongepowered.asm.mixin.gen.Invoker("ensureHasSentCarriedItem") void hustle263$syncSelectedSlot();
}

