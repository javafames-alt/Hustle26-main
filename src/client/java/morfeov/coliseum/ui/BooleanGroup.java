package morfeov.coliseum.ui;

import java.util.*;
import morfeov.coliseum.setting.*;

/** A view of existing flags: profile keys and module behavior remain on the original settings. */
final class BooleanGroup extends ModeSetting {
    private final List<BooleanSetting> members;
    private int lastEnabled = -1, lastVisible = -1;
    BooleanGroup(String name,List<BooleanSetting> members) {
        super(name,label(members.getFirst()),members.stream().map(BooleanGroup::label).toArray(String[]::new));
        this.members=List.copyOf(members);
        visibleWhen(()->{for(var member:members)if(member.visible())return true;return false;});
        refresh();
    }
    private static String label(BooleanSetting setting){return setting.menuGroup()!=null?setting.name():setting.name().substring(setting.name().indexOf(':')+1).strip();}
    private static String groupOf(Setting<?> setting){
        if(setting.menuGroup()!=null)return setting.menuGroup();
        int colon=setting.name().indexOf(':');
        return colon>0?setting.name().substring(0,colon).strip():"";
    }
    void refresh(){
        int visible=0,enabled=0;
        for(var member:members)if(member.visible()){visible++;if(member.enabled())enabled++;}
        if(visible!=lastVisible||enabled!=lastEnabled){lastVisible=visible;lastEnabled=enabled;value="Выбрано "+enabled+"/"+visible;}
    }
    @Override public List<String> modes(){return members.stream().filter(Setting::visible).map(BooleanGroup::label).toList();}
    @Override public boolean is(String mode){return members.stream().anyMatch(s->label(s).equals(mode)&&s.enabled());}
    @Override public void setSilently(String mode){for(var s:members)if(label(s).equals(mode)&&s.visible()){s.toggle();break;}refresh();}
    static List<Setting<?>> combine(List<Setting<?>> settings){
        Map<String,List<BooleanSetting>> groups=new LinkedHashMap<>();
        for(var setting:settings)if(setting instanceof BooleanSetting b&&!groupOf(b).isEmpty())
            groups.computeIfAbsent(groupOf(b),key->new ArrayList<>()).add(b);
        List<Setting<?>> result=new ArrayList<>();Set<String> emitted=new HashSet<>();
        for(var setting:settings){
            String prefix=groupOf(setting);
            var group=groups.get(prefix);
            if(setting instanceof BooleanSetting&&group!=null&&group.size()>1){if(emitted.add(prefix))result.add(new BooleanGroup(prefix,group));}
            else result.add(setting);
        }
        return List.copyOf(result);
    }
}
