package morfeov.coliseum.mixin;
@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.multiplayer.MultiPlayerGameMode.class)
public interface CombatGameModeAccessor {
    @org.spongepowered.asm.mixin.gen.Accessor("destroyBlockPos") net.minecraft.core.BlockPos coliseum$destroyBlock();
    @org.spongepowered.asm.mixin.gen.Accessor("destroyProgress") float coliseum$destroyProgress();
    @org.spongepowered.asm.mixin.gen.Invoker("ensureHasSentCarriedItem") void coliseum$syncSelectedSlot();
}

