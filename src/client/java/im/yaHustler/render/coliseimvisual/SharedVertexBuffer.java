package im.yaHustler.render.coliseimvisual;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.renderpearl.api.vertex.VertexFormat;

public final class SharedVertexBuffer implements AutoCloseable {
    private final ByteBufferBuilder byteBufferBuilder;

    public void clearBuffer() {
        this.byteBufferBuilder.clear();
    }

    @Override
    public void close() {
        this.byteBufferBuilder.close();
    }

    public static SharedVertexBuffer shared() {
        return SharedVertexBuffer.SharedHolder.INSTANCE;
    }

    public SharedVertexBuffer(int capacity) {
        this.byteBufferBuilder = new ByteBufferBuilder(capacity);
    }

    public BufferBuilder beginVertices(PrimitiveTopology topology, VertexFormat format) {
        return new BufferBuilder(this.byteBufferBuilder, topology, format);
    }

    private static final class SharedHolder {
        public static final SharedVertexBuffer INSTANCE = new SharedVertexBuffer(786432);

        private SharedHolder() {}
    }
}
