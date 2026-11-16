package im.yaHustler.render.weather;

/** Exact procedural samples reused until the moving weather grid changes. */
public final class PrecipitationPattern {
    private final double[] seeds=new double[13*13*12], phases=new double[13*13*12], offsets=new double[13*13*12];
    private final int[] sprites=new int[13*13*12];
    private boolean initialized;
    private int centerX,centerZ,layers;
    public boolean update(int x,int z,int depth) {
        if(depth<1||depth>12)throw new IllegalArgumentException("Weather layers: "+depth);
        if(initialized&&x==centerX&&z==centerZ&&depth==layers)return false;
        centerX=x;centerZ=z;layers=depth;initialized=true;
        int i=0;
        for(int gx=x-6;gx<=x+6;gx++)for(int gz=z-6;gz<=z+6;gz++)for(int layer=0;layer<depth;layer++,i++) {
            seeds[i]=random(gx,gz,layer);phases[i]=random(gx,gz,layer+17)*Math.PI*2;
            offsets[i]=random(gz,gx,layer+9)*2.5;sprites[i]=Math.min(3,(int)(random(gx,gz,layer+31)*4));
        }
        return true;
    }
    public double seed(int i){return seeds[i];}
    public double phase(int i){return phases[i];}
    public double offset(int i){return offsets[i];}
    public int sprite(int i){return sprites[i];}
    public void clear(){initialized=false;}
    private static double random(int x,int z,int layer) {
        long hash=x*374761393L+z*668265263L+layer*1274126177L;
        hash=(hash^(hash>>13))*1274126177L;
        return ((hash^(hash>>16))&0xffffff)/(double)0xffffff;
    }
}
