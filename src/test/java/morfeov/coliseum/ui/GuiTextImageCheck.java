package morfeov.coliseum.ui;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL33.*;

/** Offscreen regression for the actual MSDF fragment shader at fractional GUI scales. */
final class GuiTextImageCheck implements AutoCloseable {
    private static final int W = 900, H = 300;
    private final int program, vao = glGenVertexArrays(), vbo = glGenBuffers(), fbo = glGenFramebuffers();
    private final int atlas, target, uniform;
    private final Map<Integer, JsonObject> glyphs = new HashMap<>();
    private final int aw, ah;
    private final float cap;

    private GuiTextImageCheck() throws Exception {
        JsonObject root;
        try (var in = getClass().getResourceAsStream("/assets/coliseum/textures/font/coliseum_semi.json")) {
            root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        aw = root.getAsJsonObject("atlas").get("width").getAsInt();
        ah = root.getAsJsonObject("atlas").get("height").getAsInt();
        for (var element : root.getAsJsonArray("glyphs")) {
            var glyph = element.getAsJsonObject(); glyphs.put(glyph.get("unicode").getAsInt(), glyph);
        }
        cap = glyphs.get((int)'H').getAsJsonObject("planeBounds").get("top").getAsFloat() - 5f / 64;
        String vertex = """
            #version 330
            #extension GL_ARB_separate_shader_objects : require
            layout(location=0) in vec2 Position;
            layout(location=1) in vec2 UV;
            layout(location=0) out vec2 texCoord0;
            layout(location=1) out vec4 vertexColor;
            void main(){gl_Position=vec4(Position,0.0,1.0);texCoord0=UV;vertexColor=vec4(1.0);}
            """;
        String fragment;
        try (var in = getClass().getResourceAsStream("/assets/coliseum/shaders/core/gui_text.fsh")) {
            fragment = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        program = glCreateProgram();
        int vs = compile(GL_VERTEX_SHADER, vertex), fs = compile(GL_FRAGMENT_SHADER, fragment);
        glAttachShader(program, vs); glAttachShader(program, fs); glLinkProgram(program);
        glDeleteShader(vs); glDeleteShader(fs);
        if (glGetProgrami(program, GL_LINK_STATUS) == 0) throw new AssertionError(glGetProgramInfoLog(program));
        glUseProgram(program);
        uniform = glGenBuffers(); glBindBuffer(GL_UNIFORM_BUFFER, uniform);
        float[] transform = new float[40]; Arrays.fill(transform, 32, 36, 1);
        glBufferData(GL_UNIFORM_BUFFER, transform, GL_STATIC_DRAW);
        glUniformBlockBinding(program, glGetUniformBlockIndex(program, "DynamicTransforms"), 0);
        glBindBufferBase(GL_UNIFORM_BUFFER, 0, uniform);
        BufferedImage image;
        try (var in = getClass().getResourceAsStream("/assets/coliseum/textures/font/coliseum_semi.png")) { image = ImageIO.read(in); }
        var pixels = BufferUtils.createByteBuffer(aw * ah * 4);
        for (int y = 0; y < ah; y++) for (int x = 0; x < aw; x++) {
            int c = image.getRGB(x, y); pixels.put((byte)(c >> 16)).put((byte)(c >> 8)).put((byte)c).put((byte)(c >>> 24));
        }
        pixels.flip();
        glActiveTexture(GL_TEXTURE0); atlas = glGenTextures(); glBindTexture(GL_TEXTURE_2D, atlas);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, aw, ah, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glUniform1i(glGetUniformLocation(program, "Sampler0"), 0);
        glActiveTexture(GL_TEXTURE1); target = glGenTextures(); glBindTexture(GL_TEXTURE_2D, target);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, W, H, 0, GL_RGBA, GL_UNSIGNED_BYTE, (java.nio.ByteBuffer)null);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo); glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("GUI sampling FBO incomplete");
        glViewport(0, 0, W, H); glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 16, 0); glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 16, 8); glEnableVertexAttribArray(1);
        glDisable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE); glDisable(GL_SCISSOR_TEST);
        glEnable(GL_BLEND); glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type); glShaderSource(shader, source); glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) throw new AssertionError(glGetShaderInfoLog(shader));
        return shader;
    }
    private void clear() { glClearColor(0, 0, 0, 0); glClear(GL_COLOR_BUFFER_BIT); }
    private void line(float x, float y, float size) {
        var vertices = new ArrayList<Float>();
        for (int cp : "Coliseum  AttackAura  Настройки".codePoints().toArray()) {
            var glyph = glyphs.get(cp); if (glyph == null) continue;
            if (glyph.has("planeBounds")) {
                var p = glyph.getAsJsonObject("planeBounds"); var a = glyph.getAsJsonObject("atlasBounds");
                float x0 = x + p.get("left").getAsFloat() * size, x1 = x + p.get("right").getAsFloat() * size;
                float y0 = y + (cap - p.get("top").getAsFloat()) * size, y1 = y + (cap - p.get("bottom").getAsFloat()) * size;
                float u0 = a.get("left").getAsFloat() / aw, u1 = a.get("right").getAsFloat() / aw;
                float v0 = (ah - a.get("top").getAsFloat()) / ah, v1 = (ah - a.get("bottom").getAsFloat()) / ah;
                float[][] quad = {{x0,y0,u0,v0},{x0,y1,u0,v1},{x1,y1,u1,v1},{x1,y0,u1,v0}};
                for (int i : new int[]{0,1,2,0,2,3}) {
                    vertices.add(quad[i][0] * 2 / W - 1); vertices.add(1 - quad[i][1] * 2 / H);
                    vertices.add(quad[i][2]); vertices.add(quad[i][3]);
                }
            }
            x += glyph.get("advance").getAsFloat() * size;
        }
        float[] data = new float[vertices.size()]; for (int i = 0; i < data.length; i++) data[i] = vertices.get(i);
        glBufferData(GL_ARRAY_BUFFER, data, GL_STREAM_DRAW); glDrawArrays(GL_TRIANGLES, 0, data.length / 4);
    }
    private byte[] read() {
        var pixels = BufferUtils.createByteBuffer(W * H * 4); glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        byte[] data = new byte[pixels.remaining()]; pixels.get(data); return data;
    }
    private double centroid(byte[] data) {
        double weight = 0, moment = 0; int partial = 0, ink = 0;
        for (int i = 0; i < W * H; i++) {
            int alpha = data[i * 4 + 3] & 255;
            weight += alpha; moment += alpha * (i % W + .5);
            if (alpha > 0) ink++; if (alpha > 0 && alpha < 255) partial++;
        }
        if (ink < 200 || partial < ink / 20) throw new AssertionError("Text lost its antialiased strokes");
        return moment / weight;
    }
    static void verify() throws Exception {
        try (var check = new GuiTextImageCheck()) {
            int cases = 0;
            for (float scale : new float[]{.55f,.8f,1,1.35f,1.5f}) {
                double previous = 0;
                for (int phase = 0; phase < 4; phase++) {
                    check.clear(); check.line(30 + phase * .25f, 30, 21 * scale);
                    double centre = check.centroid(check.read());
                    if (phase > 0 && (centre - previous < .01 || centre - previous > .6))
                        throw new AssertionError("Subpixel text jumped at scale " + scale + ": " + (centre - previous));
                    previous = centre; cases++;
                }
            }
            check.clear(); int row = 0;
            for (float scale : new float[]{.55f,.8f,1,1.35f,1.5f}) check.line(30.25f, 25 + row++ * 52, 21 * scale);
            byte[] data = check.read(); var image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int i = (y * W + x) * 4;
                int grey = 24 + Math.round((data[i+3] & 255) * .9f);
                image.setRGB(x, H-y-1, 0xff000000 | grey << 16 | grey << 8 | grey);
            }
            Path output = Path.of("build/reports/gui-text-sampling.png"); Files.createDirectories(output.getParent()); ImageIO.write(image, "png", output.toFile());
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("GUI text GPU error");
            System.out.println("GUI text sampling passed: " + cases + " fractional scale/offset images; " + output);
        }
    }
    @Override public void close() {
        glDeleteBuffers(uniform); glDeleteBuffers(vbo); glDeleteVertexArrays(vao); glDeleteFramebuffers(fbo);
        glDeleteTextures(atlas); glDeleteTextures(target); glDeleteProgram(program);
    }
}
