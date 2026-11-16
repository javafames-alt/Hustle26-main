package morfeov.coliseum.friend;

import java.nio.file.*;
import java.util.*;
import com.google.gson.Gson;

public final class FriendStore {
    private final Path file;
    private final Map<String,String> names=new LinkedHashMap<>();
    private long revision;
    public FriendStore(Path file){this.file=file;}
    public void load() throws java.io.IOException {if(!Files.isRegularFile(file))return;String[] values=new Gson().fromJson(Files.readString(file),String[].class);names.clear();if(values!=null)for(String name:values)if(valid(name))names.put(name.toLowerCase(Locale.ROOT),name);revision++;}
    public static boolean valid(String name){return name!=null&&name.matches("[A-Za-z0-9_]{1,16}");}
    public boolean contains(String name){return name!=null&&names.containsKey(name.toLowerCase(Locale.ROOT));}
    public List<String> all(){return List.copyOf(names.values());}
    public long revision(){return revision;}
    public boolean add(String name) throws java.io.IOException {if(!valid(name))throw new IllegalArgumentException("Некорректный ник");if(contains(name))return false;var next=new LinkedHashMap<>(names);next.put(name.toLowerCase(Locale.ROOT),name);commit(next);return true;}
    public boolean remove(String name) throws java.io.IOException {if(!contains(name))return false;var next=new LinkedHashMap<>(names);next.remove(name.toLowerCase(Locale.ROOT));commit(next);return true;}
    public void clear() throws java.io.IOException {commit(new LinkedHashMap<>());}
    private void commit(Map<String,String> next) throws java.io.IOException {Files.createDirectories(file.getParent());Path temp=Files.createTempFile(file.getParent(),"friends-",".tmp");try{Files.writeString(temp,new Gson().toJson(next.values()));Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}names.clear();names.putAll(next);revision++;}
}
