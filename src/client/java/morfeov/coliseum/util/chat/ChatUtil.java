package morfeov.coliseum.util.chat;
public final class ChatUtil {
    public static void sendMessage(String text) {
        sendMessage(net.minecraft.network.chat.Component.literal(text));
    }
    public static void sendMessage(net.minecraft.network.chat.Component message) {
        var mc=net.minecraft.client.Minecraft.getInstance();
        mc.execute(()->{if(mc.player!=null)mc.gui.chatListener().handleSystemMessage(message,false);});
    }
}


