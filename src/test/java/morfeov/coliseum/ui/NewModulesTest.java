package morfeov.coliseum.ui;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import morfeov.coliseum.config.ClientConfig;
import morfeov.coliseum.cosmetics.coliseum.ColiseumAnimationSelector;
import morfeov.coliseum.cosmetics.geo.*;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleRegistry;
import morfeov.coliseum.module.impl.combat.*;
import morfeov.coliseum.module.impl.player.*;
import morfeov.coliseum.module.impl.render.SwingAnimations;
import morfeov.coliseum.animation.Swing3DAnimation;
import morfeov.coliseum.setting.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class NewModulesTest {
    @TempDir Path directory;
    @Test void newCodeLootFiltersAndTargetModesSurviveProfiles() throws Exception {
        var registry = new ModuleRegistry();
        var finder = registry.register(new AutoKeyFinder());
        var esp = registry.register(new morfeov.coliseum.module.impl.render.TargetESP());
        var mode = (ModeSetting) esp.settings().getFirst();
        assertTrue(mode.modes().containsAll(List.of("Ромб", "Кольцо", "Призрак", "Духи", "Мечи", "Молния")));
        var redCard = (BooleanSetting) finder.settings().stream().filter(s -> s.name().equals("Красная Ключ-Карта")).findFirst().orElseThrow();
        var save = (BooleanSetting) finder.settings().stream().filter(s -> s.name().equals("Сейвить в /ec")).findFirst().orElseThrow();
        var speed = (NumberSetting) finder.settings().stream().filter(s -> s.name().equals("Скорость")).findFirst().orElseThrow();
        redCard.set(false); save.set(true); speed.set(95.0); mode.set("Молния");
        Path profile = directory.resolve("newcode.json");
        ClientConfig.saveProfile(profile, registry);
        redCard.set(true); save.set(false); speed.set(20.0); mode.set("Кольцо");
        assertTrue(new ClientConfig(profile, registry).load());
        assertFalse(redCard.enabled()); assertTrue(save.enabled());
        assertEquals(95.0, speed.get()); assertTrue(mode.is("Молния"));
        assertEquals(7, finder.settings().size());
        for (String name : List.of("glow.png", "target1.png", "bloom.png")) {
            try (var input = getClass().getResourceAsStream("/assets/coliseum/textures/targetesp/" + name)) {
                assertNotNull(input, name); assertNotNull(javax.imageio.ImageIO.read(input), name);
            }
        }
    }
    @Test void quickActionKeysAndCapturedComponentsSurviveProfiles() throws Exception {
        var registry=new ModuleRegistry();
        var swap=registry.register(new AutoSwap());var pearl=registry.register(new ClickPearl());
        var elytra=registry.register(new ElytraHelper());var explosion=registry.register(new AutoExplosion());
        swap.button.set(-104);swap.wheelBind.set(72);swap.mod.set("Колесо");
        swap.wheel[0].set("{\"id\":\"minecraft:player_head\",\"count\":1,\"components\":{\"minecraft:custom_name\":\"Сфера\"}}");
        elytra.fireworkKey.set(70);elytra.autoTakeoff.set(true);pearl.throwKey.set(86);pearl.mode.set("Легитно");
        explosion.matrix.set(true);
        var profile=directory.resolve("native-modules.json");ClientConfig.saveProfile(profile,registry);
        swap.button.set(-1);swap.wheelBind.set(-1);swap.mod.set("Дуо");swap.wheel[0].set("");
        elytra.fireworkKey.set(-1);pearl.throwKey.set(-1);explosion.matrix.set(false);
        assertTrue(new ClientConfig(profile,registry).load());
        assertEquals(-104,swap.button.get());assertEquals(72,swap.wheelBind.get());
        assertEquals("Колесо",swap.mod.get());assertTrue(swap.wheel[0].get().contains("Сфера"));
        assertEquals(70,elytra.fireworkKey.get());assertEquals(86,pearl.throwKey.get());
        assertTrue(elytra.autoTakeoff.enabled());assertTrue(explosion.matrix.enabled());
        for(Module m:registry.all())assertEquals(m.settings().size(),m.settings().stream().map(Setting::name).distinct().count());
    }
    @Test void openingMotionSurvivesViewportFittingAndUsesTheSameInputTransform(){
        var base=GuiTransform.centered(1,400,260,496,324);
        assertTrue(base.scale()<.8);
        var opening=base.animated(.86f,28,200,130);
        assertEquals(base.scale()*.86f,opening.scale(),.00001);
        assertEquals(base.screenX(200),opening.screenX(200),.0001);
        assertEquals(55,opening.uiX(opening.screenX(55)),.0001);
        assertEquals(90,opening.uiY(opening.screenY(90)),.0001);
        assertEquals(base,base.animated(1,0,200,130));
    }
    @Test void threeDSettingsPersistAndOwnTheRenderer() throws Exception {
        var registry=new ModuleRegistry();var swing=registry.register(new SwingAnimations());
        assertEquals("3D",swing.mode.get());swing.setEnabled(true);
        assertTrue(SwingAnimations.is3DActive());assertFalse(SwingAnimations.wantsPunchy());
        swing.leftArm.set(true);swing.speed.set(4.0);
        Path profile=directory.resolve("3d.json");ClientConfig.saveProfile(profile,registry);
        swing.mode.set("Классический");assertTrue(SwingAnimations.wantsPunchy());
        swing.leftArm.set(false);swing.speed.set(7.0);
        assertTrue(new ClientConfig(profile,registry).load());
        assertTrue(SwingAnimations.is3DActive());assertTrue(swing.leftArm.enabled());assertEquals(4,swing.speed.get());
        assertFalse(SwingAnimations.wantsPunchy());
        swing.setEnabled(false);assertFalse(SwingAnimations.is3DActive());
    }
    @Test void threeDSpeedKeepsTheNativeDurationAndClampsZero() {
        var swing=new SwingAnimations();swing.setEnabled(true);
        assertEquals(6,SwingAnimations.swingDuration(6));assertEquals(4,SwingAnimations.swingDuration(4));
        swing.speed.set(0.0);assertEquals(1,SwingAnimations.swingDuration(6));
        swing.speed.set(10.0);assertEquals(9,SwingAnimations.swingDuration(6));
        swing.setEnabled(false);assertEquals(6,SwingAnimations.swingDuration(6));
    }
    @Test void threeDPosesMirrorCorrectlyAndStayInsideTheirPoseFrame() {
        var right=new com.mojang.blaze3d.vertex.PoseStack();var left=new com.mojang.blaze3d.vertex.PoseStack();
        right.pushPose();left.pushPose();
        Swing3DAnimation.applyHandBase(right,.5f,net.minecraft.world.entity.HumanoidArm.RIGHT);
        Swing3DAnimation.applyHandBase(left,.5f,net.minecraft.world.entity.HumanoidArm.LEFT);
        assertEquals(-right.last().pose().m30(),left.last().pose().m30(),.00001);
        assertEquals(right.last().pose().m31(),left.last().pose().m31(),.00001);
        assertEquals(right.last().pose().m32(),left.last().pose().m32(),.00001);
        for(int i=0;i<=100;i++) {
            float progress=i/100f;
            assertTrue(Float.isFinite(Swing3DAnimation.swingCurve(progress)));
            assertTrue(Float.isFinite(Swing3DAnimation.swingRotation(progress)));
        }
        assertEquals(0,Swing3DAnimation.swingCurve(0),.0001);
        assertEquals(0,Swing3DAnimation.swingCurve(1),.0001);
        right.popPose();left.popPose();
        assertEquals(new org.joml.Matrix4f(),right.last().pose());
        assertEquals(new org.joml.Matrix4f(),left.last().pose());
    }
    @Test void everyImportedCosmeticHasParsableGeometryAndCompleteArtwork() throws Exception {
        int count=0,animated=0;
        for(int i=0;i<256;i++){
            String root="/assets/pulse/cosmetics/models/cosmetic_"+i+".json";
            try(var in=getClass().getResourceAsStream(root)){
                if(in==null)continue;
                JsonObject json=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
                if(json.has("model"))geometry(json,root);
                else assertEquals("cape",json.get("type").getAsString(),root);
                png("/assets/pulse/textures/cosmetics/cosmetic_"+i+".png");
                png("/assets/pulse/textures/cosmetics/item_"+i+".png");count++;
            }
        }
        var cosmetics=json("/assets/coliseum/cosmetics/coliseum/catalog.json").getAsJsonArray();
        for(var entry:cosmetics){
            String root="/assets/coliseum/"+entry.getAsJsonObject().get("path").getAsString();
            JsonObject model=json(root+"model.json").getAsJsonObject();geometry(model,root);
            png(root+"texture.png");png(root+"preview.png");
            if(model.has("animation")){
                var animations=model.getAsJsonObject("animation").getAsJsonObject("animations");
                for(int i=0;i<16;i++)assertTrue(animations.has(ColiseumAnimationSelector.select(animations,(i&1)!=0,(i&2)!=0,(i&4)!=0,(i&8)!=0)),root);
                animated++;
            }
            count++;
        }
        assertTrue(count>50);assertTrue(animated>0);
    }
    @Test void weaponItemDefinitionsResolveAllCustomModelsTexturesAndParents() throws Exception {
        Set<String> seen=new HashSet<>();int count=0;
        for(var entry:json("/assets/coliseum/cosmetics/weapons/catalog.json").getAsJsonArray()){
            String model=entry.getAsJsonObject().get("model").getAsString();
            var definition=json("/assets/coliseum/items/"+model+".json");
            visit(definition,seen);count++;
        }
        assertTrue(count>10);assertTrue(seen.size()>count);
    }
    private void visit(JsonElement json,Set<String> seen) throws Exception {
        if(json.isJsonArray())for(var child:json.getAsJsonArray())visit(child,seen);
        if(!json.isJsonObject())return;
        for(var e:json.getAsJsonObject().entrySet()){
            var value=e.getValue();
            if((e.getKey().equals("parent")||e.getKey().equals("model"))&&value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()){
                String id=value.getAsString();
                if(id.startsWith("coliseum:")&&seen.add(id)){
                    visit(json("/assets/coliseum/models/"+id.substring(9)+".json"),seen);
                }
            }
            if(e.getKey().equals("textures")&&value.isJsonObject())for(var tex:value.getAsJsonObject().entrySet()){
                if(!tex.getValue().isJsonPrimitive())continue;
                String id=tex.getValue().getAsString();if(id.startsWith("coliseum:")&&seen.add(id))png("/assets/coliseum/textures/"+id.substring(9)+".png");
            }
            visit(value,seen);
        }
    }
    private void geometry(JsonObject model,String name){
        GeoModel geometry=GeoModelParser.parse(model.getAsJsonObject("model").toString());
        assertNotNull(geometry,name);assertTrue(geometry.topLevelBones.stream().mapToInt(this::cubes).sum()>0,name);
    }
    private int cubes(GeoBone b){return b.childCubes.size()+b.childBones.stream().mapToInt(this::cubes).sum();}
    private JsonElement json(String path) throws Exception {
        try(var in=getClass().getResourceAsStream(path)){assertNotNull(in,path);return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
    }
    private void png(String path) throws Exception {
        try(var in=getClass().getResourceAsStream(path)){assertNotNull(in,path);assertNotNull(javax.imageio.ImageIO.read(in),path);}
    }
}
