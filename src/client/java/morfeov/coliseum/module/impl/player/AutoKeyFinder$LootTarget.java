package morfeov.coliseum.module.impl.player;

import net.minecraft.core.BlockPos;

public final class AutoKeyFinder$LootTarget {
   public final AutoKeyFinder$LootKind kind;
   public final BlockPos pos;
   public final int entityId;

   public boolean isMinecart() {
      return this.kind == AutoKeyFinder$LootKind.MINECART;
   }

   public AutoKeyFinder$LootTarget(AutoKeyFinder$LootKind kind, BlockPos pos, int entityId) {
      this.kind = kind;
      this.pos = pos.immutable();
      this.entityId = entityId;
   }
}
