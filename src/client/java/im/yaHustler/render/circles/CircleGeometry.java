package im.yaHustler.render.circles;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.vertex.VertexFormat;

/** Render-thread scratch storage. Each MeshData is closed by the pass that consumes it. */
public final class CircleGeometry {
    private static ByteBufferBuilder storage;
    private CircleGeometry() {}
    public static BufferBuilder begin(PrimitiveTopology topology,VertexFormat format) {
        if(storage==null) storage=new ByteBufferBuilder(786432);
        return new BufferBuilder(storage,topology,format);
    }
    public static void close() {
        if(storage!=null) {storage.close();storage=null;}
    }
}
