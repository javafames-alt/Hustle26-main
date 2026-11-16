package im.yaHustler.module.impl.misc;

import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.module.Category;
import im.yaHustler.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;
import java.util.regex.Pattern;

@ModuleInfo(id="name_protect",name="NameProtect",category=Category.MISC,description="Скрывает ник в интерфейсе: hustle263dlc.xyz")
public final class NameProtect extends Module {
    private static NameProtect instance;
    private static Pattern cachedPattern;
    private static String cachedName = "";
    private static boolean cachedFriends;
    private static long cachedRevision = -1;
    private final BooleanSetting friends=add(new BooleanSetting("Применять к друзьям",false));
    public NameProtect(){instance=this;}
    private static Pattern pattern(){
        var client=Minecraft.getInstance();
        if(instance==null||!instance.enabled()||client.player==null)return null;
        String name=client.player.getGameProfile().name();
        boolean includeFriends=instance.friends.enabled();
        long revision=im.yaHustler.friend.Friends.revision();
        if(name.equals(cachedName)&&includeFriends==cachedFriends&&revision==cachedRevision)return cachedPattern;
        var names=new ArrayList<String>();names.add(client.player.getGameProfile().name());
        if(instance.friends.enabled())names.addAll(im.yaHustler.friend.Friends.all());
        cachedName=name;cachedFriends=includeFriends;cachedRevision=revision;
        cachedPattern=Pattern.compile("(?<![A-Za-z0-9_])(?:"+names.stream().map(Pattern::quote).collect(java.util.stream.Collectors.joining("|"))+")(?![A-Za-z0-9_])",Pattern.CASE_INSENSITIVE);
        return cachedPattern;
    }
    public static String protect(String text){var pattern=pattern();return text==null||pattern==null?text:pattern.matcher(text).replaceAll("hustle263dlc.xyz");}
    public static FormattedCharSequence protect(FormattedCharSequence sequence){
        var pattern=pattern();if(pattern==null)return sequence;
        StringBuilder text=new StringBuilder();var styles=new ArrayList<Style>();
        sequence.accept((index,style,point)->{text.appendCodePoint(point);for(int i=0;i<Character.charCount(point);i++)styles.add(style);return true;});
        var match=pattern.matcher(text);if(!match.find())return sequence;
        var pieces=new ArrayList<FormattedCharSequence>();int cursor=0;
        do {append(pieces,text.toString(),styles,cursor,match.start());pieces.add(FormattedCharSequence.forward("hustle263dlc.xyz",styles.get(match.start())));cursor=match.end();}while(match.find());
        append(pieces,text.toString(),styles,cursor,text.length());return FormattedCharSequence.composite(pieces);
    }
    private static void append(List<FormattedCharSequence> output,String text,List<Style> styles,int from,int end){for(int i=from;i<end;){int point=text.codePointAt(i);output.add(FormattedCharSequence.codepoint(point,styles.get(i)));i+=Character.charCount(point);}}
}
