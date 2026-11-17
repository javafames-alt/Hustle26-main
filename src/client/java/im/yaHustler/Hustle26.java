package im.yaHustler;

import im.yaHustler.module.impl.player.GodMode;
import im.yaHustler.module.impl.themes.Theme;
import com.mojang.blaze3d.platform.InputConstants;
import im.yaHustler.config.ClientConfig;
import im.yaHustler.module.ModuleRegistry;
import im.yaHustler.module.impl.misc.FakePlayer;
import im.yaHustler.module.impl.movement.Sprint;
import im.yaHustler.module.impl.render.Hud;
import im.yaHustler.module.impl.render.HandShader;
import im.yaHustler.module.impl.render.SwingAnimations;
import im.yaHustler.module.impl.render.TargetESP;
import im.yaHustler.module.impl.render.ViewModel;
import im.yaHustler.punchy.PunchyBridge;
import im.yaHustler.rotation.Rotations;
import im.yaHustler.skin.SkinManager;
import im.yaHustler.ui.ClickGuiScreen;
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

public final class Hustle26 implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("hustle263");
    public static final ModuleRegistry MODULES = new ModuleRegistry();
    public static KeyMapping OPEN_GUI;
    public static ClientConfig CONFIG;

    @Override
    public void onInitializeClient() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> im.yaHustler.bot.client.BotManager.instance().close());
        DiscordPresence.start();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> DiscordPresence.stop());
        MODULES.register(new Sprint());
        MODULES.register(new im.yaHustler.module.impl.render.Companion());
        MODULES.register(new im.yaHustler.module.impl.render.ItemPhysics());
        MODULES.register(new im.yaHustler.module.impl.render.BlockESP());
        MODULES.register(new im.yaHustler.module.impl.combat.AntiBot());
        MODULES.register(new im.yaHustler.module.impl.combat.AimAssist());
        MODULES.register(new im.yaHustler.module.impl.combat.TriggerBot());
        MODULES.register(new im.yaHustler.module.impl.combat.AutoPotion());
        MODULES.register(new im.yaHustler.module.impl.misc.ChestStealer());
        MODULES.register(new im.yaHustler.module.impl.render.Chams());
        im.yaHustler.friend.Friends.load();
        MODULES.register(new im.yaHustler.module.impl.misc.NameProtect());
        MODULES.register(new im.yaHustler.module.impl.misc.RWHelper());
        MODULES.register(new im.yaHustler.module.impl.misc.AutoSell());
        MODULES.register(new im.yaHustler.module.impl.combat.Criticals());
        MODULES.register(new im.yaHustler.module.impl.combat.AutoTotem());
        MODULES.register(new im.yaHustler.module.impl.movement.GuiMove());
        MODULES.register(new im.yaHustler.module.impl.misc.ItemScroller());
        MODULES.register(new im.yaHustler.module.impl.movement.ElytraMotion());
        MODULES.register(new im.yaHustler.module.impl.player.AHHelper());
        MODULES.register(new im.yaHustler.module.impl.movement.NoSlow());
        MODULES.register(new im.yaHustler.module.impl.player.ClanUpgrade());
        MODULES.register(new im.yaHustler.module.impl.movement.Velocity());
        MODULES.register(new im.yaHustler.module.impl.combat.TargetPearl());
        MODULES.register(new im.yaHustler.module.impl.movement.NoPush());
        MODULES.register(new im.yaHustler.module.impl.movement.NoWeb());
        var elytraBooster = MODULES.register(new im.yaHustler.module.impl.movement.ElytraBooster());
        MODULES.register(new im.yaHustler.module.impl.movement.GrimGlide());
        MODULES.register(new im.yaHustler.module.impl.combat.AutoSwap());
        MODULES.register(new im.yaHustler.module.impl.combat.AutoExplosion());
        MODULES.register(new im.yaHustler.module.impl.player.ClickPearl());
        MODULES.register(new im.yaHustler.module.impl.player.AutoTpaccept());
        MODULES.register(new im.yaHustler.module.impl.player.AutoDuel());
        MODULES.register(new im.yaHustler.module.impl.player.AutoTool());
        MODULES.register(new im.yaHustler.module.impl.player.AutoRespawn());
        MODULES.register(new im.yaHustler.module.impl.player.AutoArmor());
        MODULES.register(new im.yaHustler.module.impl.player.ElytraHelper());
        MODULES.register(new im.yaHustler.module.impl.player.AutoKeyFinder());
        MODULES.register(new im.yaHustler.module.impl.player.Bot());
        var godMode = MODULES.register(new GodMode());
        MODULES.register(new im.yaHustler.module.impl.combat.CrystalAura());
        MODULES.register(new im.yaHustler.module.impl.combat.aura.AttackAura());
        MODULES.register(new im.yaHustler.module.impl.combat.AutoMace());
        MODULES.register(new im.yaHustler.module.impl.movement.Speed());
        MODULES.register(new im.yaHustler.module.impl.movement.ElytraTarget());
        MODULES.register(new FakePlayer());
        MODULES.register(new SwingAnimations());
        MODULES.register(new im.yaHustler.module.impl.render.AspectRatio());
        var handShader = MODULES.register(new HandShader());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> handShader.close());
        MODULES.register(new im.yaHustler.module.impl.render.MotionBlur());
        var saturation=MODULES.register(new im.yaHustler.module.impl.render.Saturation());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->saturation.close());
        var optimization = MODULES.register(new im.yaHustler.module.impl.render.Optimization());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> optimization.restoreOptions());
        MODULES.register(new im.yaHustler.module.impl.combat.AutoWebTrap());
        MODULES.register(new im.yaHustler.module.impl.render.BlockOverlay());
        MODULES.register(new im.yaHustler.module.impl.render.CustomCrystal());
        MODULES.register(new ViewModel());
        MODULES.register(new im.yaHustler.module.impl.render.Gamma());
        MODULES.register(new im.yaHustler.module.impl.render.Music3D());
        MODULES.register(new TargetESP());
        var tags = MODULES.register(new im.yaHustler.module.impl.render.Tags());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> tags.close());
        MODULES.register(new im.yaHustler.module.impl.render.NoRender());
        MODULES.register(new im.yaHustler.module.impl.render.ChinaHat());
        MODULES.register(new im.yaHustler.module.impl.render.BetterMinecraft());
        MODULES.register(new im.yaHustler.module.impl.render.TotemAngel());
        MODULES.register(new im.yaHustler.module.impl.render.Trails());
        var prediction = MODULES.register(new im.yaHustler.module.impl.render.Prediction());
        var ambience = MODULES.register(new im.yaHustler.module.impl.render.Ambience());
        var weather = MODULES.register(new im.yaHustler.module.impl.render.Weather(ambience));
        MODULES.hide(ambience);
        MODULES.register(new im.yaHustler.module.impl.render.JumpCircles());
        MODULES.register(new im.yaHustler.module.impl.render.ClickGui());
        MODULES.register(new Theme());
        Hud hud = MODULES.register(new Hud());
        PunchyBridge.initialize();
        SkinManager.initialize();
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> SkinManager.refresh(true));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(im.yaHustler.port.CombatHooks::disconnect));
        ClientConfig config = new ClientConfig(
            FabricLoader.getInstance().getConfigDir().resolve("hustle263.json"), MODULES);
        CONFIG = config;
        if (!config.load()) new ClientConfig(FabricLoader.getInstance().getConfigDir().resolve("dclient.json"), MODULES).load();
        MODULES.onChanged(config::save);
        im.yaHustler.command.ClientCommands.initialize();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> config.flush());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client ->
            im.yaHustler.module.impl.combat.aura.rotation.neuro.NeuroRotation.getInstance().recorder().stop());
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
            client.execute(() -> im.yaHustler.module.impl.combat.aura.rotation.neuro.NeuroRotation.getInstance().recorder().stop()));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> im.yaHustler.render.coliseimvisual.HustleVisuals.close());
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("hustle263", "gps"), im.yaHustler.command.GpsMarker::extractRenderState);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("hustle263", "prediction"), prediction::extractHud);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("hustle263", "tags"), tags::extractRenderState);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("hustle263", "hud"), hud::extractRenderState);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("hustle263", "god_mode"), godMode::extractRenderState);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("hustle263", "elytra_booster"), elytraBooster::extractRenderState);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("hustle263", "optimization"), optimization::extractRenderState);
        HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS, vanilla -> (g, delta) -> {
            if (!hud.replacesVanillaEffects()) vanilla.extractRenderState(g, delta);
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
            "key.hustle263.open_gui", InputConstants.Type.KEYBOARD, InputConstants.KEY_RSHIFT,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("hustle263", "general"))));
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            im.yaHustler.port.CombatHooks.beginTick();
            Rotations.tick(client);
            MODULES.tick(client);
            weather.syncAmbience();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            im.yaHustler.bot.client.BotManager.instance().tick(client);
            im.yaHustler.port.SilentComponent.finishDataSetTick(client.isPaused() ? null : client.player);
            im.yaHustler.module.impl.combat.aura.rotation.neuro.NeuroRotation.getInstance().recorder().tick();
            while (OPEN_GUI.consumeClick()) {
                if (client.gui.screen() == null && client.player != null)
                    client.gui.setScreen(new ClickGuiScreen(MODULES));
            }
            MODULES.handleKeys(client);
            SkinManager.tick(client);
        });
        LOGGER.info("Hustle 26.3 initialized: {} modules", MODULES.all().size());
    }
}
