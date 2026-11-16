package morfeov.coliseum.ui;

import morfeov.coliseum.config.ClientConfig;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleRegistry;
import morfeov.coliseum.module.impl.render.*;
import morfeov.coliseum.render.coliseimvisual.*;
import morfeov.coliseum.setting.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

public class ColiseumFeaturesTest {
    @TempDir Path directory;
    @Test void allDonorModesHaveNativeEditableSettingsWithoutAnOpenGlContext() {
        for (Module module : new Module[]{new Trails(), new Ambience(), new JumpCircles(), new AspectRatio(), new BetterMinecraft(), new TotemAngel(), new SwingAnimations()}) {
            assertFalse(module.enabled());
            assertFalse(module.settings().isEmpty());
            assertEquals(module.settings().size(), module.settings().stream().map(Setting::name).distinct().count());
            for (Setting<?> setting : module.settings())
                assertTrue(setting instanceof ModeSetting || setting instanceof NumberSetting || setting instanceof ColorSetting || setting instanceof BooleanSetting);
        }
        assertEquals(java.util.List.of("Длина шлейфа","Размер частиц","Скорость призраков"),
                new Trails().settings().stream().map(Setting::name).toList());
    }
    @Test void importedModesAndCustomColorsSurviveProfileRoundTrip() throws Exception {
        ModuleRegistry registry = new ModuleRegistry();
        var trails = registry.register(new Trails()); var ambience = registry.register(new Ambience());
        NumberSetting trailLength = (NumberSetting)trails.settings().getFirst();
        ModeSetting time = (ModeSetting)ambience.settings().stream().filter(s->s.name().equals("Время")).findFirst().orElseThrow();
        trailLength.set(31.0); time.set("Ночь");
        Path profile = directory.resolve("visuals.json"); ClientConfig.saveProfile(profile, registry);
        trailLength.set(12.0); time.set("Обычное");
        assertTrue(new ClientConfig(profile, registry).load());
        assertEquals(31.0, trailLength.get()); assertEquals("Ночь", time.get());
        var clock = new TimeSetting("Время", "Обычное", "Ночь");
        assertEquals(1234, clock.resolveDayTime(1234)); clock.setting.set("Ночь"); assertEquals(21000, clock.resolveDayTime(1234));
    }
    @Test void jumpCirclesShowsOnlyCircleSettings() {
        var module=new JumpCircles();
        assertTrue(module.settings().stream().noneMatch(setting -> setting.name().equals("Форма")));
        assertEquals(java.util.List.of("Радиус","Авто цвет под тему","Искажение"),
                module.settings().stream().filter(Setting::visible).map(Setting::name).toList());
        var nested=java.util.Arrays.stream(JumpCircles.class.getDeclaredClasses()).map(Class::getSimpleName).toList();
        assertTrue(nested.containsAll(java.util.List.of("Circle","TerrainCircle","LightningBranch","LightningBurst","Leech","LeechQuad","Wave")));
    }
    @Test void allTwoHundredLeechFramesAndCircleTexturesResolve() throws Exception {
        var field=JumpCircles.class.getDeclaredField("LEECH_FRAMES"); field.setAccessible(true);
        var ids=(net.minecraft.resources.Identifier[])field.get(null);
        assertEquals(200,ids.length);
        for(var id:ids) try(var input=getClass().getResourceAsStream("/assets/"+id.getNamespace()+"/"+id.getPath())) {
            assertNotNull(input,id.toString());
            var image=javax.imageio.ImageIO.read(input);
            assertNotNull(image); assertTrue(image.getWidth()>0 && image.getHeight()>0);
        }
        for(String name:java.util.List.of("CIRCLE_TEXTURE","TERRAIN_TEXTURE")) {
            var texture=JumpCircles.class.getDeclaredField(name);texture.setAccessible(true);
            var id=(net.minecraft.resources.Identifier)texture.get(null);
            try(var input=getClass().getResourceAsStream("/assets/coliseum/"+id.getPath())) {assertNotNull(input);}
        }
    }
    @Test void newRenderModulesSaveAndRestoreNativeProfileSettings() throws Exception {
        var registry=new ModuleRegistry();var circles=registry.register(new JumpCircles());
        var radius=(NumberSetting)circles.settings().getFirst();
        radius.set(1.8);
        Path profile=directory.resolve("new-render-modules.json");ClientConfig.saveProfile(profile,registry);
        radius.set(1.0);
        assertTrue(new ClientConfig(profile,registry).load());
        assertEquals(1.8,radius.get());
    }
    @Test void hudIsHiddenInClickGuiAndItsThemePopupButNotNormalScreens() throws Exception {
        // Only type/parent identity is inspected; Screen constructors require a live Minecraft font.
        var unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        var unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        var gui=(ClickGuiScreen)unsafe.allocateInstance(ClickGuiScreen.class);
        assertTrue(GuiVisibility.hidesHud(gui));
        assertTrue(GuiVisibility.hidesHud((ThemePickerScreen)unsafe.allocateInstance(ThemePickerScreen.class)));
        assertFalse(GuiVisibility.hidesHud(null));
        var chat=(net.minecraft.client.gui.screens.ChatScreen)unsafe.allocateInstance(net.minecraft.client.gui.screens.ChatScreen.class);
        assertFalse(GuiVisibility.hidesHud(chat));
        var editor=(HudEditorScreen)unsafe.allocateInstance(HudEditorScreen.class);
        var parent=HudEditorScreen.class.getDeclaredField("parent");parent.setAccessible(true);
        parent.set(editor,gui);assertTrue(GuiVisibility.hidesHud(editor));
        parent.set(editor,chat);assertFalse(GuiVisibility.hidesHud(editor));
    }
    @Test void donorTrailSamplingResetsOnTeleportAndKeepsBoundedHistory() {
        var history = new TrailHistory();
        for (int i=0;i<1000;i++) history.addSample(new Vec3(i*.01,0,0), i*.01, 40);
        var sampled = history.sampleTrail(30);
        assertTrue(sampled.points().size() <= 30);
        assertFalse(sampled.points().isEmpty());
        double previous = Double.POSITIVE_INFINITY;
        for (var point : sampled.points()) { assertTrue(point.time() <= previous); previous = point.time(); }
        history.addSample(new Vec3(200,0,0),10.01,40);
        assertEquals(1, history.sampleTrail(30).points().size());
        history.clearHistory(); assertTrue(history.sampleTrail(30).points().isEmpty());
    }
    @Test void everyImportedRenderPipelineResolvesItsNativeShaderResources() throws Exception {
        assertTrue(VisualPipelineAudit.collect().size() >= 25);
        for (var pipeline : VisualPipelineAudit.collect()) for (var entry : pipeline.getShaders().entrySet()) {
            String suffix = entry.getKey() == com.mojang.renderpearl.api.pipeline.ShaderType.VERTEX ? ".vsh" : ".fsh";
            String resource = "/assets/"+entry.getValue().getNamespace()+"/shaders/"+entry.getValue().getPath()+suffix;
            try (var input = getClass().getResourceAsStream(resource)) { assertNotNull(input, pipeline.getLocation()+": "+resource); }
        }
    }

    @Test void importedShaderInterfacesUseMinecraft263Syntax() throws Exception {
        for(var pipeline:VisualPipelineAudit.collect()) for(var entry:pipeline.getShaders().entrySet()) {
            var id=entry.getValue();
            String path="/assets/"+id.getNamespace()+"/shaders/"+id.getPath()+(entry.getKey()==com.mojang.renderpearl.api.pipeline.ShaderType.VERTEX?".vsh":".fsh");
            try(var input=getClass().getResourceAsStream(path)) {
                String source=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                assertFalse(source.contains("#moj_import"),path);
                assertFalse(java.util.regex.Pattern.compile("(?m)^(flat\\s+)?(in|out)\\s+\\w+\\s+\\w+\\s*;").matcher(source).find(),path);
            }
        }
        try(var input=getClass().getResourceAsStream("/assets/coliseum/shaders/include/coliseimvisual/depth.glsl")) {
            String source=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(source.contains("RENDERPEARL_DEPTH_IS_ZERO_TO_ONE"));
        }
    }

    @Test void importedMixinMethodsAndInvocationTargetsExistInMinecraft263() throws Exception {
        for (String name : new String[]{"ColiseumWorldRendererMixin","AmbienceFogMixin",
                "AmbienceWeatherMixin","WeatherRenderingMixin","AmbienceCloudMixin","AmbienceClockManagerMixin","AmbienceClockMixin",
                "ClickGuiHudMixin","FirstPersonHandsRendererMixin","SwingDurationMixin","BlockPlacementMixin","WeaponCosmeticsMixin","ActionMouseMixin","ElytraTakeoffMixin","AvatarRendererMixin","KeyboardInputMixin",
                "NoSlowMovementMixin","MovementEntityMixin","MovementLocalPlayerMixin","MovementPacketMixin","MovementWebBlockMixin",
                "BoosterFireworkRocketMixin","GrimGlideVelocityMixin","GrimGlidePlayerMixin","GrimGlideTimerMixin","GrimGlidePacketMixin","GrimGlideCorrectionMixin"}) {
            var mixin=bytecode("morfeov/coliseum/mixin/"+name);
            var annotations=new java.util.ArrayList<org.objectweb.asm.tree.AnnotationNode>();
            if(mixin.visibleAnnotations!=null) annotations.addAll(mixin.visibleAnnotations);
            if(mixin.invisibleAnnotations!=null) annotations.addAll(mixin.invisibleAnnotations);
            var declaration=annotations.stream().filter(a->a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
            var types=(java.util.List<org.objectweb.asm.Type>)annotationValue(declaration,"value");
            var target=bytecode(types.getFirst().getInternalName());
            for(var method:mixin.methods) {
                annotations.clear();
                if(method.visibleAnnotations!=null) annotations.addAll(method.visibleAnnotations);
                if(method.invisibleAnnotations!=null) annotations.addAll(method.invisibleAnnotations);
                for(var annotation:annotations) {
                    Object selectors=annotationValue(annotation,"method");
                    if(!(selectors instanceof java.util.List<?> list)) continue;
                    for(Object selector:list) {
                        String signature=(String)selector;
                        var matches=target.methods.stream().filter(m->signature.equals(m.name)||signature.equals(m.name+m.desc)).toList();
                        assertFalse(matches.isEmpty(),name+": missing "+signature);
                        Object at=annotationValue(annotation,"at");
                        if(at==null) continue; // WrapMethod targets the whole method.
                        var locations=at instanceof java.util.List<?> locationsList?locationsList:java.util.List.of(at);
                        for(Object location:locations) if(location instanceof org.objectweb.asm.tree.AnnotationNode node) {
                            Object invocation=annotationValue(node,"target");
                            if(invocation instanceof String expected && !expected.isEmpty()) {
                                boolean found=matches.stream().anyMatch(m->{
                                    for(var instruction:m.instructions) if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                                            && expected.equals("L"+call.owner+";"+call.name+call.desc)) return true;
                                    return false;
                                });
                                if(!Integer.valueOf(0).equals(annotationValue(annotation,"require")))
                                    assertTrue(found,name+": missing invocation "+expected);
                            }
                        }
                    }
                }
            }
        }
    }
    private static Object annotationValue(org.objectweb.asm.tree.AnnotationNode node,String key) {
        if(node.values!=null) for(int i=0;i<node.values.size();i+=2) if(key.equals(node.values.get(i))) return node.values.get(i+1);
        return null;
    }
    private static org.objectweb.asm.tree.ClassNode bytecode(String name) throws Exception {
        try(var input=ColiseumFeaturesTest.class.getResourceAsStream("/"+name+".class")) {
            assertNotNull(input,name);
            var node=new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(input).accept(node,0);
            return node;
        }
    }
}
