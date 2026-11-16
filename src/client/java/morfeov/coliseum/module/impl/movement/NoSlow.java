package morfeov.coliseum.module.impl.movement;

import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.setting.ModeSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

/** the original implementation's five item-use slowdown modes, mapped to native 26.3 movement input. */
@ModuleInfo(id = "no_slow", name = "NoSlow", category = Category.MOVEMENT,
    description = "Убирает замедление при использовании предметов")
public final class NoSlow extends Module {
    public static final String VANILLA = "Vanilla";
    public static final String GRIM_TICKS = "Grim Ticks";
    public static final String GRIM_NEW = "Grim New";
    public static final String REALLYWORLD = "Reallyworld";
    public static final String FUNTIME_CROSSBOW = "Funtime арбалет";
    public static final String COLISEUM_GRIM_NEW = "Coliseum Grim New";
    public static final String COLISEUM_GRIM_OLD = "Coliseum Grim Old";

    private static NoSlow instance;
    public final ModeSetting mode = add(new ModeSetting("Режим", VANILLA,
        VANILLA, GRIM_TICKS, GRIM_NEW, REALLYWORLD, FUNTIME_CROSSBOW, COLISEUM_GRIM_NEW, COLISEUM_GRIM_OLD) {
        @Override public void setSilently(String next) {
            super.setSilently(switch (next) {
                case "Zenith Grim New" -> COLISEUM_GRIM_NEW;
                case "Zenith Grim Old" -> COLISEUM_GRIM_OLD;
                default -> next;
            });
        }
    });

    private final UseCycle cycle = new UseCycle();
    private LocalPlayer controlledPlayer;
    private String activeMode;

    public NoSlow() { instance = this; }

    @Override
    protected void onEnable() { reset(); }

    @Override
    protected void onDisable() { reset(); }

    private void reset() {
        cycle.reset(monotonicMillis());
        controlledPlayer = null;
        activeMode = null;
    }

    private static long monotonicMillis() { return System.nanoTime() / 1_000_000; }

    /** Runs immediately before LocalPlayer's inherited tick, as in the donor. */
    public static void beforePlayerTick(LocalPlayer player) {
        NoSlow module = instance;
        if (module == null || !module.enabled()) return;
        long now = monotonicMillis();
        if (module.controlledPlayer != player || !module.mode.get().equals(module.activeMode)) {
            module.cycle.reset(now);
            module.controlledPlayer = player;
            module.activeMode = module.mode.get();
        }
        if (player.isFallFlying()) {
            module.cycle.reset(now);
            return;
        }

        boolean usingItem = player.isUsingItem();
        module.cycle.advance(module.mode.get(), usingItem, now);
        if (usingItem && module.mode.is(GRIM_TICKS) && module.cycle.pulse()) {
            // Preserve the donor's extra main-hand use packet without resetting local use animation.
            player.connection.send(new ServerboundUseItemPacket(
                InteractionHand.MAIN_HAND, 0, player.getYRot(), player.getXRot()));
        }
        if (usingItem && module.mode.is(GRIM_NEW)
            && player.getUsedItemHand() == InteractionHand.MAIN_HAND && player.getTicksUsingItem() == 1) {
            // The donor's Grim New mode explicitly sends a drop-stack action on the first use tick.
            player.connection.send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS, BlockPos.ZERO, player.getDirection()));
        }
        if (usingItem && module.mode.is(COLISEUM_GRIM_OLD)) {
            InteractionHand other = player.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            player.connection.send(new ServerboundUseItemPacket(other, 0, player.getYRot(), player.getXRot()));
        }
    }

    /** Changes only item-use slowdown; sneaking, collision and riding retain their own rules. */
    public static float itemUseMultiplier(LocalPlayer player, float vanillaMultiplier) {
        NoSlow module = instance;
        if (module == null || !module.enabled()) return vanillaMultiplier;
        boolean crossbow = player.getUseItem().is(Items.CROSSBOW)
            && (player.getMainHandItem().is(Items.CROSSBOW) || player.getOffhandItem().is(Items.CROSSBOW));
        return cancelSlowdown(module.mode.get(), player.isUsingItem(), player.isFallFlying(),
            player.getUsedItemHand() == InteractionHand.MAIN_HAND, player.getTicksUsingItem(),
            crossbow, module.cycle.pulse()) ? 1.0f : vanillaMultiplier;
    }

    public static boolean cancelSlowdown(String mode, boolean usingItem, boolean gliding,
                                         boolean mainHand, int usedTicks, boolean usingCrossbow,
                                         boolean pulse) {
        if (!usingItem || gliding) return false;
        return switch (mode) {
            case VANILLA -> true;
            case GRIM_TICKS, REALLYWORLD -> pulse;
            // The original implementation resets its unrelated Reallyworld counter to zero in this mode;
            // its counter % 2 check consequently always succeeds after the warm-up.
            case GRIM_NEW -> mainHand && usedTicks > 1;
            case FUNTIME_CROSSBOW -> usingCrossbow;
            case COLISEUM_GRIM_NEW -> usedTicks % 2 == 0;
            case COLISEUM_GRIM_OLD -> true;
            default -> false;
        };
    }

    /** Timing shared by the native movement hook and deterministic regression checks. */
    public static final class UseCycle {
        private static final int[] REALLYWORLD_DELAYS = {2, 3, 3};
        private int useTicks;
        private int burstStep;
        private long lastGrimPulse;
        private boolean pulse;

        public void reset(long now) {
            useTicks = burstStep = 0;
            lastGrimPulse = now;
            pulse = false;
        }

        public void advance(String mode, boolean usingItem, long now) {
            pulse = false;
            if (REALLYWORLD.equals(mode)) {
                if (!usingItem) {
                    useTicks = burstStep = 0;
                } else if (++useTicks >= REALLYWORLD_DELAYS[burstStep]) {
                    pulse = true;
                    useTicks = 0;
                    burstStep = (burstStep + 1) % REALLYWORLD_DELAYS.length;
                }
            } else {
                useTicks = burstStep = 0;
            }
            if (GRIM_TICKS.equals(mode) && usingItem && now - lastGrimPulse >= 125) {
                pulse = true;
                lastGrimPulse = now;
            }
        }

        public boolean pulse() { return pulse; }
    }
}
