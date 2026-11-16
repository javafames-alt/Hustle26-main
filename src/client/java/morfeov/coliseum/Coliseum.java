package morfeov.coliseum;

import morfeov.coliseum.module.impl.player.GodMode;
import morfeov.coliseum.module.impl.themes.Theme;
import com.mojang.blaze3d.platform.InputConstants;
import morfeov.coliseum.config.ClientConfig;
import morfeov.coliseum.module.ModuleRegistry;
import morfeov.coliseum.module.impl.misc.FakePlayer;
import morfeov.coliseum.module.impl.movement.Sprint;
import morfeov.coliseum.module.impl.render.Hud;
import morfeov.coliseum.module.impl.render.HandShader;
import morfeov.coliseum.module.impl.render.SwingAnimations;
import morfeov.coliseum.module.impl.render.TargetESP;
import morfeov.coliseum.module.impl.render.ViewModel;
import morfeov.coliseum.punchy.PunchyBridge;
import morfeov.coliseum.rotation.Rotations;
import morfeov.coliseum.skin.SkinManager;
import morfeov.coliseum.ui.ClickGuiScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.ChatScreen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Coliseum implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("coliseum");
    public static final ModuleRegistry MODULES = new ModuleRegistry();
    public static KeyMapping OPEN_GUI;
    public static ClientConfig CONFIG;

    @Override
    public void onInitializeClient() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> morfeov.coliseum.bot.client.BotManager.instance().close());
        DiscordPresence.start();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> DiscordPresence.stop());
        MODULES.register(new Sprint());
        MODULES.register(new morfeov.coliseum.module.impl.render.Companion());
        MODULES.register(new morfeov.coliseum.module.impl.render.ItemPhysics());
        MODULES.register(new morfeov.coliseum.module.impl.render.BlockESP());
        MODULES.register(new morfeov.coliseum.module.impl.combat.AntiBot());
        MODULES.register(new morfeov.coliseum.module.impl.misc.ChestStealer());
        MODULES.register(new morfeov.coliseum.module.impl.render.Chams());
        morfeov.coliseum.friend.Friends.load();
        MODULES.register(new morfeov.coliseum.module.impl.misc.NameProtect());
        MODULES.register(new morfeov.coliseum.module.impl.misc.RWHelper());
        MODULES.register(new morfeov.coliseum.module.impl.misc.AutoSell());
        MODULES.register(new morfeov.coliseum.module.impl.combat.Criticals());
        MODULES.register(new morfeov.coliseum.module.impl.combat.AutoTotem());
        MODULES.register(new morfeov.coliseum.module.impl.movement.GuiMove());
        MODULES.register(new morfeov.coliseum.module.impl.misc.ItemScroller());
        MODULES.register(new morfeov.coliseum.module.impl.movement.ElytraMotion());
        MODULES.register(new morfeov.coliseum.module.impl.player.AHHelper());
        MODULES.register(new morfeov.coliseum.module.impl.movement.NoSlow());
        MODULES.register(new morfeov.coliseum.module.impl.player.ClanUpgrade());
        MODULES.register(new morfeov.coliseum.module.impl.movement.Velocity());
        MODULES.register(new morfeov.coliseum.module.impl.combat.TargetPearl());
        MODULES.register(new morfeov.coliseum.module.impl.movement.NoPush());
        MODULES.register(new morfeov.coliseum.module.impl.movement.NoWeb());
        var elytraBooster = MODULES.register(new morfeov.coliseum.module.impl.movement.ElytraBooster());
        MODULES.register(new morfeov.coliseum.module.impl.movement.GrimGlide());
        MODULES.register(new morfeov.coliseum.module.impl.combat.AutoSwap());
        MODULES.register(new morfeov.coliseum.module.impl.combat.AutoExplosion());
        MODULES.register(new morfeov.coliseum.module.impl.player.ClickPearl());
        MODULES.register(new morfeov.coliseum.module.impl.player.AutoTpaccept());
        MODULES.register(new morfeov.coliseum.module.impl.player.AutoDuel());
        MODULES.register(new morfeov.coliseum.module.impl.player.AutoTool());
        MODULES.register(new morfeov.coliseum.module.impl.player.AutoRespawn());
        MODULES.register(new morfeov.coliseum.module.impl.player.AutoArmor());
        MODULES.register(new morfeov.coliseum.module.impl.player.ElytraHelper());
        MODULES.register(new morfeov.coliseum.module.impl.player.AutoKeyFinder());
        MODULES.register(new morfeov.coliseum.module.impl.player.Bot());
        var godMode = MODULES.register(new GodMode());
        MODULES.register(new morfeov.coliseum.module.impl.combat.CrystalAura());
        MODULES.register(new morfeov.coliseum.module.impl.combat.aura.AttackAura());
        MODULES.register(new morfeov.coliseum.module.impl.combat.AutoMace());
        MODULES.register(new morfeov.coliseum.module.impl.movement.Speed());
        MODULES.register(new morfeov.coliseum.module.impl.movement.ElytraTarget());
        MODULES.register(new FakePlayer());
        MODULES.register(new SwingAnimations());
        MODULES.register(new morfeov.coliseum.module.impl.render.AspectRatio());
        var handShader = MODULES.register(new HandShader());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> handShader.close());
        MODULES.register(new morfeov.coliseum.module.impl.render.MotionBlur());
        var saturation=MODULES.register(new morfeov.coliseum.module.impl.render.Saturation());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->saturation.close());
        var optimization = MODULES.register(new morfeov.coliseum.module.impl.render.Optimization());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> optimization.restoreOptions());
        MODULES.register(new morfeov.coliseum.module.impl.combat.AutoWebTrap());
        MODULES.register(new morfeov.coliseum.module.impl.render.BlockOverlay());
        MODULES.register(new morfeov.coliseum.module.impl.render.CustomCrystal());
        MODULES.register(new ViewModel());
        MODULES.register(new morfeov.coliseum.module.impl.render.Gamma());
        MODULES.register(new morfeov.coliseum.module.impl.render.Music3D());
        MODULES.register(new TargetESP());
        var tags = MODULES.register(new morfeov.coliseum.module.impl.render.Tags());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> tags.close());
        MODULES.register(new morfeov.coliseum.module.impl.render.NoRender());
        MODULES.register(new morfeov.coliseum.module.impl.render.ChinaHat());
        MODULES.register(new morfeov.coliseum.module.impl.render.BetterMinecraft());
        MODULES.register(new morfeov.coliseum.module.impl.render.TotemAngel());
        MODULES.register(new morfeov.coliseum.module.impl.render.Trails());
        var ambience = MODULES.register(new morfeov.coliseum.module.impl.render.Ambience());
        var weather = MODULES.register(new morfeov.coliseum.module.impl.render.Weather(ambience));
        MODULES.hide(ambience);
        MODULES.register(new morfeov.coliseum.module.impl.render.JumpCircles());
        MODULES.register(new morfeov.coliseum.module.impl.render.ClickGui());
        MODULES.register(new Theme());
        Hud hud = MODULES.register(new Hud());
        PunchyBridge.initialize();
        SkinManager.initialize();
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> SkinManager.refresh(true));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(morfeov.coliseum.port.CombatHooks::disconnect));
        ClientConfig config = new ClientConfig(
            FabricLoader.getInstance().getConfigDir().resolve("coliseum.json"), MODULES);
        CONFIG = config;
        if (!config.load()) new ClientConfig(FabricLoader.getInstance().getConfigDir().resolve("dclient.json"), MODULES).load();
        MODULES.onChanged(config::save);
        morfeov.coliseum.command.ClientCommands.initialize();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> config.flush());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client ->
            morfeov.coliseum.module.impl.combat.aura.rotation.neuro.NeuroRotation.getInstance().recorder().stop());
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
            client.execute(() -> morfeov.coliseum.module.impl.combat.aura.rotation.neuro.NeuroRotation.getInstance().recorder().stop()));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> morfeov.coliseum.render.coliseimvisual.ColiseumVisuals.close());
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("coliseum", "gps"), morfeov.coliseum.command.GpsMarker::extractRenderState);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("coliseum", "tags"), tags::extractRenderState);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("coliseum", "hud"), hud::extractRenderState);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("coliseum", "god_mode"), godMode::extractRenderState);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("coliseum", "elytra_booster"), elytraBooster::extractRenderState);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("coliseum", "optimization"), optimization::extractRenderState);
        HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS, vanilla -> (g, delta) -> {
            if (!hud.replacesVanillaEffects()) vanilla.extractRenderState(g, delta);
        });
        HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, vanilla -> (g, delta) -> {
            if (hud.replacesVanillaHotbar() && net.minecraft.client.Minecraft.getInstance().player != null && !net.minecraft.client.Minecraft.getInstance().player.isSpectator()) hud.extractHotbar(g, delta); else vanilla.extractRenderState(g, delta);
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof ChatScreen)) return;
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) ->
                event.button() == InputConstants.MOUSE_BUTTON_RIGHT ? !hud.openElementSettings(event.x(), event.y()) : event.button() != InputConstants.MOUSE_BUTTON_LEFT || !hud.beginDrag(event.x(), event.y()));
            ScreenMouseEvents.allowMouseDrag(screen).register((s, event, dx, dy) ->
                !hud.drag(event.x(), event.y(), s.width, s.height));
            ScreenMouseEvents.allowMouseRelease(screen).register((s, event) ->
                !hud.endDrag(event.x(), event.y(), s.width, s.height));
        });
        OPEN_GUI = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.coliseum.open_gui", InputConstants.Type.KEYBOARD, InputConstants.KEY_RSHIFT,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("coliseum", "general"))));
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            morfeov.coliseum.port.CombatHooks.beginTick();
            Rotations.tick(client);
            MODULES.tick(client);
            weather.syncAmbience();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            morfeov.coliseum.bot.client.BotManager.instance().tick(client);
            morfeov.coliseum.port.SilentComponent.finishDataSetTick(client.isPaused() ? null : client.player);
            morfeov.coliseum.module.impl.combat.aura.rotation.neuro.NeuroRotation.getInstance().recorder().tick();
            while (OPEN_GUI.consumeClick()) {
                if (client.gui.screen() == null && client.player != null)
                    client.gui.setScreen(new ClickGuiScreen(MODULES));
            }
            MODULES.handleKeys(client);
            SkinManager.tick(client);
        });
        LOGGER.info("Coliseum initialized: {} modules", MODULES.all().size());
    }
}
