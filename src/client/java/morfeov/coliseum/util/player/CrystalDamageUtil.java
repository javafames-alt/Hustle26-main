package morfeov.coliseum.util.player;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;

public final class CrystalDamageUtil {
    public static int getProtectionAmount(List<ItemStack> armor) {
        int amount=0;
        for (var stack:armor) for(var entry:stack.getEnchantments().entrySet()) {
            if(entry.getKey().is(Enchantments.PROTECTION)) amount+=entry.getIntValue();
            if(entry.getKey().is(Enchantments.BLAST_PROTECTION)) amount+=entry.getIntValue()*2;
        }
        return Math.min(20,amount);
    }
}
