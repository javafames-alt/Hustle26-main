package im.yaHustler.friend;

import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class Friends {
    private static final FriendStore store=new FriendStore(FabricLoader.getInstance().getConfigDir().resolve("hustle263/friends.json"));
    private Friends(){}
    public static void load(){try{store.load();}catch(Exception error){im.yaHustler.Hustle26.LOGGER.warn("Cannot load friends",error);}}
    public static boolean valid(String name){return FriendStore.valid(name);}
    public static boolean contains(String name){return store.contains(name);}
    public static boolean contains(Entity entity){return entity instanceof Player player&&contains(player.getGameProfile().name());}
    public static List<String> all(){return store.all();}
    public static long revision(){return store.revision();}
    public static boolean add(String name)throws java.io.IOException{return store.add(name);}
    public static boolean remove(String name)throws java.io.IOException{return store.remove(name);}
    public static void clear()throws java.io.IOException{store.clear();}
}
