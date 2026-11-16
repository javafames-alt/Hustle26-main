package im.yaHustler.ui;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.Blaze3D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import im.yaHustler.Hustle26;
import im.yaHustler.config.ClientConfig;
import im.yaHustler.font.FigmaFont;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleRegistry;
import im.yaHustler.setting.Setting;
import im.yaHustler.util.ColorUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class GuiConfigsPage {
    private record Profile(String name, String metadata, long modified) {}
    private record Hit(Profile profile, int x, int y, int w) {}
    private final ModuleRegistry registry;
    private final Path directory;
    private final List<Hit> hits = new ArrayList<>();
    private List<Profile> profiles = List.of();
    private static String loaded = "";
    private String pendingDelete = "", status = "";
    private boolean resetPending;
    private int x, y, w, h, scroll, total, bw;
    GuiConfigsPage(ModuleRegistry registry) {
        this.registry = registry;
        directory = FabricLoader.getInstance().getConfigDir().resolve("hustle263/profiles");
        Path legacy = FabricLoader.getInstance().getConfigDir().resolve("dclient/profiles");
        try {
            if (!Files.exists(directory) && Files.isDirectory(legacy)) { Files.createDirectories(directory.getParent()); Files.move(legacy, directory); }
            Files.createDirectories(directory);
        } catch (IOException e) { status = "Не удалось открыть папку конфигов"; }
        refresh();
    }
    void refresh() {
        try (var files = Files.list(directory)) {
            var result = new ArrayList<Profile>();
            for (Path file : files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                String name = file.getFileName().toString().replaceFirst("\\.json$", "");
                if (!valid(name)) continue;
                var attributes = Files.readAttributes(file, BasicFileAttributes.class);
                int modules = 0;
                try (var reader = Files.newBufferedReader(file)) {
                    var root = JsonParser.parseReader(reader).getAsJsonObject();
                    if (root.has("modules")) modules = root.getAsJsonObject("modules").size();
                } catch (RuntimeException ignored) {}
                var created = attributes.creationTime().toInstant().atZone(ZoneId.systemDefault());
                var changed = attributes.lastModifiedTime().toInstant().atZone(ZoneId.systemDefault());
                String meta = "Создан: " + DateTimeFormatter.ofPattern("dd.MM.yyyy").format(created)
                    + "   Изм: " + DateTimeFormatter.ofPattern("HH:mm").format(changed) + "   Модулей: " + modules;
                result.add(new Profile(name, meta, attributes.lastModifiedTime().toMillis()));
            }
            result.sort(Comparator.comparingLong(Profile::modified).reversed()); profiles = List.copyOf(result);
        } catch (IOException e) { status = "Не удалось прочитать конфиги"; }
    }
    private static boolean valid(String name) { return name != null && name.matches("[\\p{L}\\p{N} _-]{1,48}"); }
    private Path path(String name) {
        if (!valid(name)) throw new IllegalArgumentException("Недопустимое имя конфига");
        return directory.resolve(name + ".json");
    }
    String create(String name) {
        if (!valid(name)) return "Имя: 1–48 букв, цифр, пробелов, - или _";
        if (Files.exists(path(name))) return "Конфиг с таким именем уже существует";
        save(name); return status.startsWith("Сохранено") ? "" : status;
    }
    private void save(String name) {
        try { ClientConfig.saveProfile(path(name), registry); status = "Сохранено: " + name; refresh(); }
        catch (IOException e) { status = "Не удалось сохранить конфиг"; }
    }
    private void load(String name) {
        if (new ClientConfig(path(name), registry).load()) {
            loaded = name; status = "Загружено: " + name;
            if (Hustle26.CONFIG != null) Hustle26.CONFIG.save();
        } else status = "Не удалось загрузить конфиг";
    }
    private static <T> void reset(Setting<T> setting) { setting.setSilently(setting.defaultValue()); }
    private void resetAll() {
        for (Module module : registry.all()) {
            for (Setting<?> setting : module.settings()) reset(setting);
            module.setEnabled(module.id().equals("hud") || module.id().equals("theme"));
            module.setKey(-1); module.setFavorite(false);
        }
        loaded = ""; resetPending = false; status = "Настройки модулей сброшены";
        if (Hustle26.CONFIG != null) Hustle26.CONFIG.save();
    }
    void draw(GuiGraphicsExtractor g,int x,int y,int w,int h,String query,int mx,int my){
        this.x=x+4;this.y=y+2;this.w=w-8;this.h=h-2;x=this.x;y=this.y;w=this.w;h=this.h;bw=54;
        int accent=FigmaGuiRenderer.accent(),text=0xffffffff,muted=0xff8591a7;
        FigmaFont.draw(g,profiles.size()+" конфигов",x,y+6,muted,5.2f/11);
        String[] top={"Папка",resetPending?"Точно?":"Сброс","Создать"};
        for(int i=0;i<3;i++)button(g,x+w-3*(bw+6)+i*(bw+6),y,bw,18,top[i],i==2,false,mx,my);
        List<Profile> shown=profiles.stream().filter(p->p.name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        total=shown.size()*39;scroll=Math.clamp(scroll,0,Math.max(0,total-(h-37)));hits.clear();
        g.enableScissor(x,y+26,x+w,y+h-10);
        for(int i=0;i<shown.size();i++){
            Profile p=shown.get(i);int ry=y+28+i*39-scroll;if(ry+34<=y+26||ry>=y+h-10)continue;
            boolean active=p.name.equals(loaded);
            Draw.rounded(g,x,ry,w,34,6,active?ColorUtil.alpha(accent,40):0x8c10121b);
            if(active)Draw.roundedF(g,x+2,ry+6,1.5f,22,.75f,accent);
            int ax=x+w-148;
            FigmaFont.drawMedium(g,FigmaFont.fitMedium(p.name,ax-x-14,7f/11),x+8,ry+7,text,7f/11);
            FigmaFont.draw(g,FigmaFont.fit(p.metadata,w-16,4.2f/11),x+8,ry+24,muted,4.2f/11);
            button(g,ax,ry+5,50,16,active?"Загружен":"Загрузить",false,false,mx,my);
            button(g,ax+54,ry+5,48,16,"Сохранить",false,false,mx,my);
            button(g,ax+106,ry+5,38,16,pendingDelete.equals(p.name)?"Точно?":"Удалить",false,true,mx,my);
            hits.add(new Hit(p,x,ry,0));
        }
        if(shown.isEmpty())FigmaFont.draw(g,"Конфигов нет — нажмите «Создать»",x+8,y+42,muted,5.5f/11);
        g.disableScissor();FigmaFont.draw(g,FigmaFont.fit(status,w,4.5f/11),x,y+h-6,muted,4.5f/11);
    }
    private static void button(GuiGraphicsExtractor g,int x,int y,int w,int h,String text,boolean primary,boolean danger,int mx,int my){
        boolean hot=Draw.inside(mx,my,x,y,w,h);int color=danger?0x38ff5365:primary?FigmaGuiRenderer.accent():0x1fffffff;
        Draw.rounded(g,x,y,w,h,5,hot?ColorUtil.lerp(color,0x66ffffff,.15f):color);
        FigmaFont.drawMedium(g,text,x+(w-FigmaFont.mediumWidth(text,5.2f/11))/2,y+(h-FigmaFont.mediumHeight(5.2f/11))/2,danger?0xffff808c:0xffffffff,5.2f/11);
    }
    boolean click(double mx,double my,Runnable create){
        for(int i=0;i<3;i++)if(Draw.inside(mx,my,x+w-3*(bw+6)+i*(bw+6),y,bw,18)){
            if(i==0)Blaze3D.openPath(directory);
            else if(i==1){if(resetPending)resetAll();else{resetPending=true;status="Повторный клик сбросит настройки модулей";}}
            else create.run();return true;
        }
        if(my<y+26||my>=y+h-10)return false;
        int[] offsets={0,54,106},widths={50,48,38};
        for(Hit hit:hits){int ax=x+w-148;
            for(int i=0;i<3;i++)if(Draw.inside(mx,my,ax+offsets[i],hit.y+5,widths[i],16)){
                String name=hit.profile.name;
                if(i==0){if(!name.equals(loaded))load(name);}
                else if(i==1)save(name);
                else if(name.equals(loaded))status="Загруженный конфиг нельзя удалить";
                else if(pendingDelete.equals(name)){try{Files.delete(path(name));pendingDelete="";status="Удалено: "+name;refresh();}catch(IOException e){status="Не удалось удалить конфиг";}}
                else{pendingDelete=name;status="Нажмите «Точно?» для удаления "+name;}
                return true;
            }
        }return false;
    }
    void scroll(double amount){scroll=Math.clamp(scroll-(int)(amount*18),0,Math.max(0,total-(h-37)));}
}
