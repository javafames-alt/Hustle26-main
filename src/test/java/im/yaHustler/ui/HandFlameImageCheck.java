package im.yaHustler.ui;

import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL33.*;

/** Exercises the real flame seed, nearest-edge and additive composite passes on a synthetic held item. */
final class HandFlameImageCheck implements AutoCloseable {
    private static final int W = 512, H = 384, FW = W / 2, FH = H / 2;
    private final Map<String,Integer> programs;
    private final List<Integer> textures = new ArrayList<>(), buffers = new ArrayList<>();
    private final int fbo = glGenFramebuffers(), vao = glGenVertexArrays();
    private final int[] fields = new int[2];
    private final float[] scene = new float[W * H * 4], depth = new float[W * H];
    private final int target, depthTexture;
    private int finalField, checks;
    private byte[] original;

    private HandFlameImageCheck(Map<String,Integer> programs) {
        this.programs = programs;
        Polygon head = new Polygon(
            new int[]{190,276,276,328,328,342,342,356,356,328,328,314,314,300,300,272,272,218,218,190,190,176,176,190},
            new int[]{93,73,87,87,115,115,143,143,213,213,171,171,157,157,143,143,129,143,157,157,143,143,115,115},24);
        Polygon shaft = new Polygon(new int[]{264,292,414,385},new int[]{138,128,335,351},4);
        Polygon hand = new Polygon(new int[]{385,430,480,512,512,400,360},new int[]{285,282,320,340,384,384,340},7);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            boolean item = head.contains(x, y) || shaft.contains(x, y);
            boolean arm = hand.contains(x, y) && !item;
            int index = ((H - y - 1) * W + x);
            depth[index] = item || arm ? .65f : 0;
            float grain = ((x / 18 + y / 18) & 1) * .018f;
            float tile = ((x / 9 + y / 9) & 1) * .055f;
            scene[index * 4] = item ? .19f + tile : arm ? .36f + tile : .12f + grain;
            scene[index * 4 + 1] = item ? .075f + tile * .5f : arm ? .20f + tile : .14f + grain;
            scene[index * 4 + 2] = item ? .31f + tile : arm ? .16f + tile : .12f + grain;
            scene[index * 4 + 3] = 1;
        }
        target = texture(W,H,GL_RGBA8,GL_RGBA,scene);
        depthTexture = texture(W,H,GL_R32F,GL_RED,depth);
        fields[0] = texture(FW,FH,GL_RGBA8,GL_RGBA,new float[FW*FH*4]);
        fields[1] = texture(FW,FH,GL_RGBA8,GL_RGBA,new float[FW*FH*4]);
        glBindVertexArray(vao);
        glDisable(GL_DEPTH_TEST);
    }

    static void verify(Map<String,Integer> programs) throws Exception {
        try (var check = new HandFlameImageCheck(programs)) { check.run(); }
    }

    private int program(String suffix) { return programs.get("pipeline/hands/flame_" + suffix); }

    private int texture(int width, int height, int internal, int format, float[] values) {
        int id = glGenTextures(); textures.add(id);
        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_2D,id);
        glTexImage2D(GL_TEXTURE_2D,0,internal,width,height,0,format,GL_FLOAT,values);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        return id;
    }
    private void sampler(int program,String name,int unit,int texture) {
        glActiveTexture(GL_TEXTURE0+unit); glBindTexture(GL_TEXTURE_2D,texture);
        glUniform1i(glGetUniformLocation(program,name),unit);
    }
    private void uniform(int program,String name,float[] values) {
        int buffer=glGenBuffers(); buffers.add(buffer);
        glBindBuffer(GL_UNIFORM_BUFFER,buffer);
        glBufferData(GL_UNIFORM_BUFFER,values,GL_STATIC_DRAW);
        glUniformBlockBinding(program,glGetUniformBlockIndex(program,name),0);
        glBindBufferBase(GL_UNIFORM_BUFFER,0,buffer);
    }
    private void target(int id,int width,int height) {
        glBindFramebuffer(GL_FRAMEBUFFER,fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,id,0);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("Flame test FBO incomplete");
        glViewport(0,0,width,height);
    }
    private void buildField(int depthTexture) {
        glDisable(GL_BLEND);
        glUseProgram(program("seed"));
        sampler(program("seed"),"HandDepthSampler",0,depthTexture);
        target(fields[0],FW,FH); glDrawArrays(GL_TRIANGLES,0,3);
        int read=0;
        int program=program("distance");
        glUseProgram(program);
        for(int jump:new int[]{64,32,16,8,4,2,1}) {
            uniform(program,"FlameField",new float[]{FW,FH,jump,0});
            sampler(program,"SeedSampler",0,fields[read]);
            target(fields[1-read],FW,FH);
            glDrawArrays(GL_TRIANGLES,0,3); read=1-read;
        }
        finalField=fields[read];
    }
    private byte[] composite(float time,float height,float opacity,float red,float green,float blue,int depthTexture) {
        // Each preview starts with the same scene, matching the game's fresh hand draw each frame.
        glActiveTexture(GL_TEXTURE3); glBindTexture(GL_TEXTURE_2D,target);
        glTexSubImage2D(GL_TEXTURE_2D,0,0,0,W,H,GL_RGBA,GL_FLOAT,scene);
        target(target,W,H);
        int program=program("composite"); glUseProgram(program);
        sampler(program,"HandDepthSampler",0,depthTexture);
        sampler(program,"DistanceSampler",1,finalField);
        uniform(program,"HandFlame",new float[]{W,H,FW,FH,red,green,blue,opacity,time,height,1,.75f,1.1f,0,0,0});
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA,GL_ONE);
        glDrawArrays(GL_TRIANGLES,0,3);
        ByteBuffer pixels=BufferUtils.createByteBuffer(W*H*4);
        glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
        byte[] result=new byte[pixels.remaining()]; pixels.get(result);
        if(glGetError()!=GL_NO_ERROR) throw new AssertionError("OpenGL error in flame image test");
        return result;
    }
    private static int delta(byte[] a,byte[] b,int pixel) {
        int i=pixel*4;
        return Math.abs((a[i]&255)-(b[i]&255))+Math.abs((a[i+1]&255)-(b[i+1]&255))+Math.abs((a[i+2]&255)-(b[i+2]&255));
    }
    private static int differences(byte[] a,byte[] b) {
        int changed=0; for(int i=0;i<W*H;i++) if(delta(a,b,i)>5) changed++;
        return changed;
    }
    private void require(boolean condition,String message) {
        if(!condition) throw new AssertionError(message); checks++;
    }
    private void run() throws Exception {
        buildField(depthTexture);
        original=composite(0,.9f,0,1,.56f,.88f,depthTexture);
        byte[] first=composite(0,.9f,.65f,1,.56f,.88f,depthTexture);
        int insideChanges=0,outsideChanges=0;
        for(int i=0;i<depth.length;i++) if(delta(original,first,i)>2) {
            if(depth[i]>.1) insideChanges++; else outsideChanges++;
        }
        require(insideChanges==0,"Flame painted over the item texture");
        require(outsideChanges>500,"Flame failed to create an exterior rim and glow");
        require(delta(original,first,(H-25)*W+25)==0,"Flame leaked into the distant background");
        byte[] later=composite(1.35f,.9f,.65f,1,.56f,.88f,depthTexture);
        require(differences(first,later)>100,"Flame ribbons do not animate");
        byte[] noFlames=composite(0,0,.65f,1,.56f,.88f,depthTexture);
        require(differences(first,noFlames)>100,"Flame Height does not affect the plume");
        byte[] blue=composite(0,.9f,.65f,.25f,.6f,1,depthTexture);
        require(differences(first,blue)>500,"Flame Color does not affect the effect");
        save("hands-flame",first); save("hands-flame-next-frame",later);
        int empty=texture(W,H,GL_R32F,GL_RED,new float[W*H]);
        buildField(empty);
        require(differences(original,composite(2,.9f,.65f,1,.56f,.88f,empty))==0,"Flame left a trail after the hand disappeared");
        System.out.println("Hand Flame: "+checks+" rendered-image checks passed; previews saved to .codex/ports/visual-checks.");
    }
    private void save(String name,byte[] pixels) throws Exception {
        BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
            int i=(y*W+x)*4;
            image.setRGB(x,H-y-1,0xff000000|(pixels[i]&255)<<16|(pixels[i+1]&255)<<8|pixels[i+2]&255);
        }
        Path folder=Path.of(".codex","ports","visual-checks"); Files.createDirectories(folder);
        ImageIO.write(image,"png",folder.resolve(name+".png").toFile());
    }
    @Override public void close() {
        glUseProgram(0); glBindFramebuffer(GL_FRAMEBUFFER,0); glBindVertexArray(0);
        textures.forEach(id->glDeleteTextures(id)); buffers.forEach(id->glDeleteBuffers(id));
        glDeleteFramebuffers(fbo); glDeleteVertexArrays(vao);
    }
}
