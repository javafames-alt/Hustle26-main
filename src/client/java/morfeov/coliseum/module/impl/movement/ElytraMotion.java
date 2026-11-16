package morfeov.coliseum.module.impl.movement;
import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.port.CombatHooks;
import morfeov.coliseum.setting.ModeSetting;
import morfeov.coliseum.setting.NumberSetting;
import morfeov.coliseum.util.InventoryActions;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(id="elytra_motion", name="ElytraMotion", category=Category.MOVEMENT, description="Остановка на цели при полёте на элитрах")
public final class ElytraMotion extends Module {
    public final ModeSetting mode=add(new ModeSetting("Режим", "Дистанция", "Дистанция", "Умный"));
    public final NumberSetting distance=add(new NumberSetting("Дистанция", 0.1, 0.1, 4, 0.1).visibleWhen(() -> mode.is("Дистанция")));
    private long lastRocket;
    private boolean stopping;
    @Override public void tick(Minecraft mc) {
        stopping=false;
        var target=CombatHooks.target();
        var flight=Coliseum.MODULES.get(ElytraTarget.class);
        if (mc.player==null || mc.level==null || !mc.player.isFallFlying() || target==null || flight==null || !flight.canTarget(target)) return;
        var box=target.getBoundingBox();
        boolean close=mode.is("Дистанция") ? mc.player.position().distanceToSqr(box.getCenter()) <= distance.get()*distance.get() : mc.player.getBoundingBox().intersects(box);
        if (!close) return;
        boolean rocket=false;
        for (var entity:mc.level.entitiesForRendering()) {
            if (entity instanceof net.minecraft.world.entity.projectile.FireworkRocketEntity firework && firework.getOwner()==mc.player) { rocket=true; break; }
        }
        if (rocket) { stopping=true; mc.player.setDeltaMovement(Vec3.ZERO); }
        else if (System.currentTimeMillis()-lastRocket>=300 && InventoryActions.use(mc, Items.FIREWORK_ROCKET, false)) lastRocket=System.currentTimeMillis();
    }
    public boolean stopping() { return enabled() && stopping; }
    @Override protected void onDisable() { stopping=false; lastRocket=0; }
}
