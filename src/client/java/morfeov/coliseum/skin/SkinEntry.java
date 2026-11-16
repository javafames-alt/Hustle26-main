package morfeov.coliseum.skin;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.resources.Identifier;

public final class SkinEntry {
    private final String file, name;
    private final Identifier texture;
    private final int[] pixels;
    private final int size;
    private final boolean slim;
    private final long stamp;
    private final Map<PlayerModel, SkinVoxels> voxels = new IdentityHashMap<>();

    SkinEntry(String file, String name, Identifier texture, int[] pixels, int size, boolean slim, long stamp) {
        this.file = file;
        this.name = name;
        this.texture = texture;
        this.pixels = pixels;
        this.size = size;
        this.slim = slim;
        this.stamp = stamp;
    }

    public String file() { return file; }
    public String name() { return name; }
    public Identifier texture() { return texture; }
    public int[] pixels() { return pixels; }
    public int size() { return size; }
    public boolean slim() { return slim; }
    long stamp() { return stamp; }

    public SkinVoxels voxels(PlayerModel model) {
        return voxels.computeIfAbsent(model, m -> SkinVoxels.build(this, m));
    }
}
