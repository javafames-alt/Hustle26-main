package morfeov.coliseum.ui;

import java.util.*;
import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL33.*;

/** Compares scissored blur passes with full-screen reference passes on the GPU. */
final class BackdropImageCheck implements AutoCloseable {
    private static final int W = 384, H = 216, GW = 192, GH = 108;
    private final int fbo = glGenFramebuffers(), vao = glGenVertexArrays();
    private final List<Integer> textures = new ArrayList<>(), buffers = new ArrayList<>();
    private final int blur, composite, original, result;
    private final float[] scene = new float[W * H * 4];

    private BackdropImageCheck(Map<String, Integer> programs) {
        blur = programs.get("pipeline/gui/figma_blur");
        composite = programs.get("pipeline/gui/figma_backdrop");
        var random = new Random(37);
        for (int i = 0; i < scene.length; i++) scene[i] = i % 4 == 3 ? 1 : random.nextFloat();
        original = texture(W, H, scene);
        result = texture(W, H, scene);
        glBindVertexArray(vao);
        glDisable(GL_DEPTH_TEST); glDisable(GL_BLEND); glDisable(GL_CULL_FACE);
    }

    static void verify(Map<String, Integer> programs) {
        try (var check = new BackdropImageCheck(programs)) { check.run(); }
    }

    private int texture(int w, int h, float[] data) {
        int id = glGenTextures(); textures.add(id);
        glActiveTexture(GL_TEXTURE3); glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_FLOAT, data);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return id;
    }

    private void target(int id, int w, int h, BackdropRegion region) {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, id, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("Backdrop FBO incomplete");
        glViewport(0, 0, w, h);
        if (region == null) glDisable(GL_SCISSOR_TEST);
        else { glEnable(GL_SCISSOR_TEST); glScissor(region.x(), h - region.y() - region.height(), region.width(), region.height()); }
    }

    private void uniform(int program, String name, int binding, float[] data) {
        int buffer = glGenBuffers(); buffers.add(buffer);
        glBindBuffer(GL_UNIFORM_BUFFER, buffer); glBufferData(GL_UNIFORM_BUFFER, data, GL_STATIC_DRAW);
        glUniformBlockBinding(program, glGetUniformBlockIndex(program, name), binding);
        glBindBufferBase(GL_UNIFORM_BUFFER, binding, buffer);
    }

    private void sampler(int program, String name, int unit, int texture) {
        glActiveTexture(GL_TEXTURE0 + unit); glBindTexture(GL_TEXTURE_2D, texture);
        glUniform1i(glGetUniformLocation(program, name), unit);
    }

    private byte[] render(float[][] panels, int divisor, float sigma, boolean clipped) {
        int bw = W / divisor, bh = H / divisor;
        // Poison unwritten pixels, so accidental reads of stale targets are visible.
        float[] poison = new float[bw * bh * 4]; Arrays.fill(poison, 1);
        int horizontal = texture(bw, bh, poison), vertical = texture(bw, bh, poison);
        double guiPerPixel = .5, filterPixel = divisor;
        var xr = BackdropRegion.of(GW, GH, panels, bw, bh, (3 * Math.abs(sigma) + 4 * filterPixel + 4) * guiPerPixel);
        var yr = BackdropRegion.of(GW, GH, panels, bw, bh, (2 * filterPixel + 2) * guiPerPixel);
        glUseProgram(blur);
        target(horizontal, bw, bh, clipped ? xr : null);
        uniform(blur, "BlurStyle", 0, new float[]{1f / W, 0, sigma, 0});
        sampler(blur, "InputSampler", 0, original); glDrawArrays(GL_TRIANGLES, 0, 3);
        target(vertical, bw, bh, clipped ? yr : null);
        uniform(blur, "BlurStyle", 0, new float[]{0, 1f / H, sigma, 0});
        sampler(blur, "InputSampler", 0, horizontal); glDrawArrays(GL_TRIANGLES, 0, 3);

        glActiveTexture(GL_TEXTURE3); glBindTexture(GL_TEXTURE_2D, result);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, W, H, GL_RGBA, GL_FLOAT, scene);
        var cr = BackdropRegion.of(GW, GH, panels, W, H, 2 * guiPerPixel);
        target(result, W, H, clipped ? cr : null);
        glUseProgram(composite);
        uniform(composite, "SurfaceStyle", 0, BackdropStyle.pack(GW, GH, panels, 5, 0xffabcdef, .12f));
        uniform(composite, "GlassStyle", 1, new float[]{1.7f, 3, .4f, 1});
        sampler(composite, "OriginalSampler", 0, original);
        sampler(composite, "BlurredSampler", 1, vertical);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        var pixels = BufferUtils.createByteBuffer(W * H * 4);
        glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        byte[] data = new byte[pixels.remaining()]; pixels.get(data);
        if (glGetError() != GL_NO_ERROR) throw new AssertionError("Backdrop GPU error");
        return data;
    }

    private void run() {
        int checks = 0;
        for (int divisor : new int[]{2, 4}) for (float sigma : new float[]{0, 2.4f, 60})
            for (float angle : new float[]{0, .45f}) for (float[] position : new float[][]{{6,6}, {148,90}, {-10,-5}}) {
                float[][] panels = {{position[0], position[1], 44, 15, 5, angle}, {70, 42, 20, 11, 3, -angle}};
                byte[] expected = render(panels, divisor, sigma, false);
                byte[] actual = render(panels, divisor, sigma, true);
                int maxError = 0;
                for (int i = 0; i < actual.length; i++) maxError = Math.max(maxError, Math.abs((actual[i] & 255) - (expected[i] & 255)));
                if (maxError > 1) throw new AssertionError("Clipped backdrop differs: divisor=" + divisor + " sigma=" + sigma + " angle=" + angle + " error=" + maxError);
                checks++;
            }
        System.out.println("Backdrop GPU image comparisons passed: " + checks + " cases, <=1/255 channel difference.");
    }

    @Override public void close() {
        glDisable(GL_SCISSOR_TEST);
        for (int id : buffers) glDeleteBuffers(id);
        for (int id : textures) glDeleteTextures(id);
        glDeleteFramebuffers(fbo); glDeleteVertexArrays(vao);
    }
}
