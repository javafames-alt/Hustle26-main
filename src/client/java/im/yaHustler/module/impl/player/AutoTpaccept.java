package im.yaHustler.module.impl.player;
import java.util.Locale;
import java.util.regex.Pattern;
import im.yaHustler.module.*;
import im.yaHustler.port.PortModule;
import im.yaHustler.port.event.EventPacketReceive;
import im.yaHustler.setting.BooleanSetting;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

@ModuleInfo(id="auto_tpaccept",name="AutoTpaccept",category=Category.PLAYER,description="Автоматически принимает запросы на телепортацию")
public final class AutoTpaccept extends PortModule {
    private final BooleanSetting friends=add(new BooleanSetting("Только друзья",true));
    private final BooleanSetting byName=add(new BooleanSetting("Принимать по нику",true));
    private final BooleanSetting avoidCombat=add(new BooleanSetting("Не принимать в бою",true));
    private long lastAccept;
    private final Pattern[] requests={
        Pattern.compile("(\\w{3,16})[\\s'\"«»\\[\\]()<>:,.\\-]{1,4}(?:просит|хочет|желает|запрашивает)[^\\n]{0,40}?телепорт", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
        Pattern.compile("(\\w{3,16})[\\s'\"«»\\[\\]()<>:,.\\-]{1,4}отправил[^\\n]{0,30}?запрос[^\\n]{0,20}?телепорт", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
        Pattern.compile("(\\w{3,16})[\\s'\"«»\\[\\]()<>:,.\\-]{1,4}(?:tpa|тпа)[\\s'\"«»\\[\\]()<>:,.\\-]{1,4}(?:к вам|к тебе|to you)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
        Pattern.compile("(\\w{3,16})[\\s'\"«»\\[\\]()<>:,.\\-]{1,4}(?:has\\s+)?request(?:ed|s)?[^\\n]{0,30}?teleport", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
        Pattern.compile("(\\w{3,16})[\\s'\"«»\\[\\]()<>:,.\\-]{1,4}wants?[\\s'\"«»\\[\\]()<>:,.\\-]{1,4}to[\\s'\"«»\\[\\]()<>:,.\\-]{1,4}teleport", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
        Pattern.compile("телепорт[^\\n]{0,40}?(?:от игрока|игрока|от)[\\s'\"«»\\[\\]()<>:,.\\-]{1,4}(\\w{3,16})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
        Pattern.compile("запрос[^\\n]{0,30}?телепорт[^\\n]{0,30}?от[\\s'\"«»\\[\\]()<>:,.\\-]{1,4}(\\w{3,16})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
    };
    public String requester(String message){String text=message.replaceAll("§[0-9a-fk-orA-FK-OR]","");String lower=text.toLowerCase(Locale.ROOT);if(java.util.List.of("вы отправили","вы запросили","вы телепорт","запрос отправлен","you sent","request sent").stream().anyMatch(lower::contains))return null;for(var pattern:requests){var match=pattern.matcher(text);if(match.find())return match.group(1);}return null;}
    @Override public void onPacketReceive(EventPacketReceive event){
        if(mc.player==null||mc.level==null||mc.getConnection()==null||!(event.getPacket() instanceof ClientboundSystemChatPacket packet)||packet.overlay())return;
        String name=requester(packet.content().getString());if(name==null||name.equalsIgnoreCase(mc.player.getGameProfile().name()))return;
        if(friends.enabled()&&!im.yaHustler.friend.Friends.contains(name))return;
        if(avoidCombat.enabled()&&(mc.player.hurtTime>0||im.yaHustler.module.impl.combat.rotation.CombatTagManager.isInCombat()))return;
        long now=System.currentTimeMillis();if(now-lastAccept<1500)return;lastAccept=now;
        mc.getConnection().sendCommand("tpaccept"+(friends.enabled()||byName.enabled()?" "+name:""));
    }
    @Override protected void onDisable(){lastAccept=0;}
}