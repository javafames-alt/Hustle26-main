package im.yaHustler.module.impl.movement;

import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.setting.BooleanSetting;

/** the original implementation's four independent push filters. Ordinary damage knockback is unaffected. */
@ModuleInfo(id = "no_push", name = "NoPush", category = Category.MOVEMENT,
    description = "Убирает отталкивание от блоков, существ, воды и удочки")
public final class NoPush extends Module {
    private static NoPush instance;
    public final BooleanSetting players = add(new BooleanSetting("Игроки", true));
    public final BooleanSetting blocks = add(new BooleanSetting("Блоки", false));
    public final BooleanSetting water = add(new BooleanSetting("Вода", false));
    public final BooleanSetting fishingRod = add(new BooleanSetting("Удочка", false));

    public NoPush() { instance = this; }

    public static boolean blocksEntityPush() { return active() && instance.players.enabled(); }
    public static boolean blocksSuffocationPush() { return active() && instance.blocks.enabled(); }
    public static boolean blocksFluidPush() { return active() && instance.water.enabled(); }
    public static boolean blocksFishingPull() { return active() && instance.fishingRod.enabled(); }
    private static boolean active() { return instance != null && instance.enabled(); }
}
