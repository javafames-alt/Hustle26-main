package morfeov.coliseum.ui;

import morfeov.coliseum.module.impl.render.Saturation;
import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL33.*;
import static org.junit.jupiter.api.Assertions.*;

final class SaturationImageCheck {
    static void verify(int program) {
        int vao=glGenVertexArrays(),uniform=glGenBuffers(),framebuffer=glGenFramebuffers();
        int source=glGenTextures(),target=glGenTextures();
        int[][] colors={{210,96,48},{25,155,220},{128,128,128},{245,220,20}};
        float[][] settings={{1,0,1,1},{0,0,1,1},{1.3f,.2f,1.05f,1},{1,.8f,1,1},{3,1,2,1.5f},{1,0,.5f,.5f}};
        try {
            var pixels=BufferUtils.createByteBuffer(16);
            for(var color:colors)pixels.put((byte)color[0]).put((byte)color[1]).put((byte)color[2]).put((byte)255);
            pixels.flip();
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,source);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,2,2,0,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
            glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,target);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,2,2,0,GL_RGBA,GL_FLOAT,(java.nio.ByteBuffer)null);
            glBindFramebuffer(GL_FRAMEBUFFER,framebuffer);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,target,0);
            assertEquals(GL_FRAMEBUFFER_COMPLETE,glCheckFramebufferStatus(GL_FRAMEBUFFER));
            glUseProgram(program);glBindVertexArray(vao);glViewport(0,0,2,2);
            glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);glDisable(GL_SCISSOR_TEST);glDisable(GL_FRAMEBUFFER_SRGB);
            glUniform1i(glGetUniformLocation(program,"Sampler0"),0);
            glUniformBlockBinding(program,glGetUniformBlockIndex(program,"SaturationData"),0);
            glBindBuffer(GL_UNIFORM_BUFFER,uniform);glBindBufferBase(GL_UNIFORM_BUFFER,0,uniform);
            var result=BufferUtils.createFloatBuffer(16);
            for(var parameters:settings) {
                glBufferData(GL_UNIFORM_BUFFER,parameters,GL_STREAM_DRAW);
                glDrawArrays(GL_TRIANGLES,0,3);
                result.clear();glReadPixels(0,0,2,2,GL_RGBA,GL_FLOAT,result);
                for(int i=0;i<colors.length;i++) {
                    var expected=reference(colors[i],parameters);
                    for(int channel=0;channel<3;channel++)assertEquals(expected[channel],result.get(i*4+channel),.0001,"Saturation pixel "+i+" channel "+channel);
                    assertEquals(1,result.get(i*4+3),.0001);
                }
            }
            assertTrue(Saturation.neutral(1,0,1,1));
            assertFalse(Saturation.neutral(1.3f,.2f,1.05f,1));
            var module=new Saturation();assertFalse(module.enabled());assertEquals(4,module.settings().size());
            assertEquals(GL_NO_ERROR,glGetError());
            System.out.println("Saturation GPU pixels verified: identity and orientation, grayscale, donor defaults, vibrance, clipping, contrast and brightness; 24 color samples.");
        } finally {
            glBindFramebuffer(GL_FRAMEBUFFER,0);glUseProgram(0);glBindVertexArray(0);
            glDeleteFramebuffers(framebuffer);glDeleteTextures(source);glDeleteTextures(target);glDeleteBuffers(uniform);glDeleteVertexArrays(vao);
        }
    }
    private static double[] reference(int[] input,float[] parameters) {
        double[] color=new double[3];
        for(int i=0;i<3;i++)color[i]=(input[i]/255.0*parameters[3]-.5)*parameters[2]+.5;
        double gray=luma(color),spread=Math.max(color[0],Math.max(color[1],color[2]))-Math.min(color[0],Math.min(color[1],color[2]));
        for(int i=0;i<3;i++)color[i]=gray+(color[i]-gray)*(1+parameters[1]*(1-spread));
        gray=luma(color);
        for(int i=0;i<3;i++)color[i]=Math.clamp(gray+(color[i]-gray)*parameters[0],0,1);
        return color;
    }
    private static double luma(double[] color) { return color[0]*.2125+color[1]*.7154+color[2]*.0721; }
}
