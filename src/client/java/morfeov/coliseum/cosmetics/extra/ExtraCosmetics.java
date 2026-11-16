package morfeov.coliseum.cosmetics.extra;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import morfeov.coliseum.Coliseum;
import morfeov.coliseum.cosmetics.LocalCosmetics;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ExtraCosmetics {
    private record Item(LocalCosmetics.Entry entry,CosmeticItem cosmetic) {}
    private static final Map<Integer,Item> items=load();
    private ExtraCosmetics() {}

    private static InputStream resource(String name) {
        var input=ExtraCosmetics.class.getResourceAsStream("/assets/coliseum/"+name);
        if(input==null)throw new IllegalArgumentException("Missing cosmetic resource: "+name);
        return input;
    }
    private static Map<Integer,Item> load() {
        var result=new LinkedHashMap<Integer,Item>();
        try(var reader=new InputStreamReader(resource("cosmetics/extra/catalog.json"),StandardCharsets.UTF_8)) {
            for(var element:JsonParser.parseReader(reader).getAsJsonArray()) {
                var json=element.getAsJsonObject();
                int id=json.get("id").getAsInt();
                String type=switch(json.get("category").getAsString()) { case "hats"->"hat";case "back"->"bodywear";case "pets"->"pet";default->"wings"; };
                var cosmetic=new CosmeticItem(json.get("uuid").getAsString(),json.get("slug").getAsString(),json.get("name").getAsString(),json.get("category").getAsString(),
                    json.get("modelPath").getAsString(),json.get("texturePath").getAsString(),json.get("previewPath").getAsString(),
                    json.has("animationPath")?json.get("animationPath").getAsString():null,0,0,0);
                try(var metaReader=new InputStreamReader(resource(json.get("metaPath").getAsString()),StandardCharsets.UTF_8)) {
                    var meta=JsonParser.parseReader(metaReader).getAsJsonObject();
                    if(meta.has("locationRotation") && meta.getAsJsonObject("locationRotation").has("normal")) {
                        var normal=meta.getAsJsonObject("locationRotation").getAsJsonObject("normal");
                        cosmetic.setOrientation(vector(normal,"rotation",0),vector(normal,"scale",1));
                    }
                }
                String path=json.get("modelPath").getAsString();path=path.substring(0,path.lastIndexOf('/')+1);
                result.put(id,new Item(new LocalCosmetics.Entry(id,cosmetic.getName().trim()+" [Coliseum]",type,path),cosmetic));
            }
        } catch(Exception error) { throw new IllegalStateException("Could not read extra cosmetic catalog",error); }
        return Collections.unmodifiableMap(result);
    }
    private static float[] vector(JsonObject json,String name,float fallback) {
        float[] values={fallback,fallback,fallback};
        if(json.has(name))for(int i=0;i<3;i++)values[i]=json.getAsJsonArray(name).get(i).getAsFloat();
        return values;
    }
    public static List<LocalCosmetics.Entry> entries() { return items.values().stream().map(Item::entry).toList(); }
    public static boolean contains(int resourceIndex) { return items.containsKey(resourceIndex); }
    public static CosmeticItem model(int resourceIndex) {
        var imported=items.get(resourceIndex);
        if(imported==null)return null;
        var item=imported.cosmetic;
        if(item.getModel()==null) {
            item.setModel(BedrockModelParser.parse(resource(item.getModelPath())));
            if(item.getModel()==null)throw new IllegalStateException("Invalid cosmetic model: "+item.getSlug());
            if(item.getAnimationPath()!=null)item.setAnimation(BedrockAnimationParser.parse(resource(item.getAnimationPath())));
        }
        return item;
    }
    public static boolean submit(int index,PoseStack pose,SubmitNodeCollector queue,int light,AvatarRenderState state,PlayerModel player) {
        int id=LocalCosmetics.resourceIndex(index);
        if(!contains(id))return false;
        var item=model(id);
        pose.pushPose();
        try {
            switch(item.getCategory()) {
                case "hats"->player.head.translateAndRotate(pose);
                case "wings","back"->player.body.translateAndRotate(pose);
                case "pets"->pose.translate(-.72,1.45-Math.sin(state.ageInTicks*.12)*.045,.05);
            }
            pose.scale(-1,-1,1);
            CosmeticPlacement.applyPlacement(pose,item,item.getCategory());
            float time=state.ageInTicks*.05f;
            SubmitNodeCollector.CustomGeometryRenderer geometry=(entry,vertices)->
                item.getModel().render(entry,vertices,light,OverlayTexture.NO_OVERLAY,1,1,1,1,item.getAnimation(),time);
            queue.submitCustomGeometry(pose,RenderTypes.entityTranslucent(item.getTextureId()),geometry);
            // Chams «Обводка»: cosmetics are part of the outlined silhouette.
            morfeov.coliseum.render.chams.ChamsModels.captureGeometry(state,item.getTextureId(),pose.last(),geometry);
        } finally { pose.popPose(); }
        return true;
    }
}
