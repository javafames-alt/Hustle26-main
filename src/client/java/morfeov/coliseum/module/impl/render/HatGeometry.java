package morfeov.coliseum.module.impl.render;

import java.util.ArrayList;

/** Immutable quad meshes in head-local metres. No geometry allocation per frame. */
public final class HatGeometry {
    public static final float[] CHINA=china(), SANTA=santa();
    private static final int SEGMENTS=48;
    private HatGeometry(){}
    private static float[] china(){
        var out=new ArrayList<Float>();
        ring(out,0,0,0,.68f,0,.35f,0,.015f,false);
        ring(out,0,-.025f,0,.68f,0,0,0,.68f,false);
        ring(out,0,-.025f,0,.68f,0,.31f,0,0,false);
        return array(out);
    }
    private static float[] santa(){
        var out=new ArrayList<Float>();
        float[][] rings={{0,-.035f,0,.285f},{0,.12f,0,.28f},{.018f,.30f,0,.235f},{.075f,.48f,0,.18f},{.18f,.61f,0,.12f},{.31f,.61f,0,.075f},{.39f,.50f,0,.025f}};
        for(int i=0;i<rings.length-1;i++){var a=rings[i];var b=rings[i+1];ring(out,a[0],a[1],a[2],a[3],b[0],b[1],b[2],b[3],false);}
        for(int i=0;i<24;i++)sphere(out,(float)Math.cos(i*Math.PI/12)*.277f,.035f,(float)Math.sin(i*Math.PI/12)*.277f,.075f);
        sphere(out,.40f,.47f,0,.105f);
        return array(out);
    }
    private static void sphere(ArrayList<Float> out,float x,float y,float z,float radius){
        for(int j=0;j<6;j++){
            double a=-Math.PI/2+j*Math.PI/6,b=a+Math.PI/6;
            ring(out,x,y+(float)Math.sin(a)*radius,z,(float)Math.cos(a)*radius,x,y+(float)Math.sin(b)*radius,z,(float)Math.cos(b)*radius,true);
        }
    }
    private static void ring(ArrayList<Float> out,float x,float y,float z,float r,float nx,float ny,float nz,float nr,boolean white){
        int segments=white?12:SEGMENTS;
        for(int i=0;i<segments;i++){
            double a=i*Math.PI*2/segments,b=(i+1)*Math.PI*2/segments;
            float shade=white?-1:.66f+.27f*(float)(.5+.5*Math.cos(a-.7));
            vertex(out,x+(float)Math.cos(a)*r,y,z+(float)Math.sin(a)*r,shade);
            vertex(out,x+(float)Math.cos(b)*r,y,z+(float)Math.sin(b)*r,shade);
            vertex(out,nx+(float)Math.cos(b)*nr,ny,nz+(float)Math.sin(b)*nr,shade);
            vertex(out,nx+(float)Math.cos(a)*nr,ny,nz+(float)Math.sin(a)*nr,shade);
        }
    }
    private static void vertex(ArrayList<Float> out,float x,float y,float z,float color){out.add(x);out.add(y);out.add(z);out.add(color);}
    private static float[] array(ArrayList<Float> out){float[] result=new float[out.size()];for(int i=0;i<result.length;i++)result[i]=out.get(i);return result;}
}

