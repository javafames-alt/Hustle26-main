package morfeov.coliseum.ui;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Map;
import javax.imageio.ImageIO;
import morfeov.coliseum.render.weather.PumpkinMesh;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL33.*;

/** Real mesh and shaders, checked offscreen with culling and both depth conventions. */
final class PumpkinImageCheck {
    private static final int SIZE=256;
    static void verify(Map<String,Integer> programs) throws Exception {
        int program=programs.get("pipeline/weather/weather_pumpkin");
        int vao=glGenVertexArrays(),vbo=glGenBuffers(),ubo=glGenBuffers(),fbo=glGenFramebuffers();
        int color=glGenTextures(),depth=glGenTextures();
        try {
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);
            glBindTexture(GL_TEXTURE_2D,color);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,SIZE,SIZE,0,GL_RGBA,GL_UNSIGNED_BYTE,(java.nio.ByteBuffer)null);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,color,0);
            glBindTexture(GL_TEXTURE_2D,depth);
            glTexImage2D(GL_TEXTURE_2D,0,GL_DEPTH_COMPONENT24,SIZE,SIZE,0,GL_DEPTH_COMPONENT,GL_UNSIGNED_INT,(java.nio.ByteBuffer)null);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_TEXTURE_2D,depth,0);
            if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("Pumpkin framebuffer");
            glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);
            glEnableVertexAttribArray(0);glVertexAttribPointer(0,3,GL_FLOAT,false,24,0L);
            glEnableVertexAttribArray(1);glVertexAttribPointer(1,2,GL_FLOAT,false,24,12L);
            glEnableVertexAttribArray(2);glVertexAttribPointer(2,4,GL_UNSIGNED_BYTE,true,24,20L);
            glUseProgram(program);
            glUniformBlockBinding(program,glGetUniformBlockIndex(program,"SnowfallMatrices"),0);
            glBindBuffer(GL_UNIFORM_BUFFER,ubo);glBindBufferBase(GL_UNIFORM_BUFFER,0,ubo);
            glViewport(0,0,SIZE,SIZE);glEnable(GL_DEPTH_TEST);glDepthMask(true);
            glEnable(GL_CULL_FACE);glCullFace(GL_BACK);glFrontFace(GL_CCW);glDisable(GL_BLEND);
            var preview=new BufferedImage(SIZE*3,SIZE*2,BufferedImage.TYPE_INT_RGB);
            var vertices=BufferUtils.createByteBuffer(PumpkinMesh.VERTEX_COUNT*24);
            var pixels=BufferUtils.createByteBuffer(SIZE*SIZE*4);
            float[] angles={(float)Math.PI,(float)Math.PI+.7f,(float)Math.PI+1.57f,0,.4f,2.2f};
            int cases=0;
            for(boolean reversed:new boolean[]{false,true}) {
                float[] matrix=new float[16];
                new Matrix4f().perspective((float)Math.toRadians(45),1,reversed?100:.05f,reversed?.05f:100).get(matrix);
                glBindBuffer(GL_UNIFORM_BUFFER,ubo);glBufferData(GL_UNIFORM_BUFFER,matrix,GL_STREAM_DRAW);
                glDepthFunc(reversed?GL_GREATER:GL_LESS);
                int frontGlow=0;
                for(int frame=0;frame<angles.length;frame++) {
                    glClearColor(.025f,.03f,.045f,0);glClearDepth(reversed?0:1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
                    vertices.clear();PumpkinMesh.append(vertices,0,0,-4,1,angles[frame],frame>=4?.2f:0,1,.9f);vertices.flip();
                    glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,vertices,GL_STREAM_DRAW);
                    glDrawArrays(GL_TRIANGLES,0,PumpkinMesh.VERTEX_COUNT);
                    pixels.clear();glReadPixels(0,0,SIZE,SIZE,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
                    int ink=0,glow=0;
                    for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++) {
                        int i=(y*SIZE+x)*4,r=pixels.get(i)&255,g=pixels.get(i+1)&255,b=pixels.get(i+2)&255;
                        if((pixels.get(i+3)&255)>0)ink++;
                        if(r>160&&g>120)glow++;
                        if(reversed)preview.setRGB(frame%3*SIZE+x,frame/3*SIZE+SIZE-y-1,(r<<16)|(g<<8)|b);
                    }
                    if(ink<5000)throw new AssertionError("Missing/cull-reversed pumpkin, angle="+angles[frame]+", pixels="+ink);
                    if(frame==0){frontGlow=glow;if(glow<100)throw new AssertionError("Missing glowing carving");}
                    if(frame==3&&glow>=frontGlow/4)throw new AssertionError("Carving leaked onto pumpkin back");
                    cases++;
                }
                glClearDepth(reversed?1:0);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
                glDrawArrays(GL_TRIANGLES,0,PumpkinMesh.VERTEX_COUNT);
                pixels.clear();glReadPixels(0,0,SIZE,SIZE,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
                for(int i=3;i<pixels.limit();i+=4)if(pixels.get(i)!=0)throw new AssertionError("Pumpkin visible through nearer depth");
                cases++;
            }
            Path output=Path.of("build/reports/weather-pumpkins-3d.png");Files.createDirectories(output.getParent());ImageIO.write(preview,"png",output.toFile());
            if(glGetError()!=GL_NO_ERROR)throw new AssertionError("Pumpkin GPU error");
            System.out.println("3D pumpkin images passed: "+cases+" rotation/culling/depth cases; "+output);
        } finally {
            glBindFramebuffer(GL_FRAMEBUFFER,0);glDisable(GL_CULL_FACE);glDisable(GL_DEPTH_TEST);glClearDepth(1);
            glDeleteFramebuffers(fbo);glDeleteTextures(color);glDeleteTextures(depth);
            glDeleteBuffers(ubo);glDeleteBuffers(vbo);glDeleteVertexArrays(vao);
        }
    }
}
