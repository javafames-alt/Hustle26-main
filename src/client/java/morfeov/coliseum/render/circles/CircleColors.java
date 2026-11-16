package morfeov.coliseum.render.circles;

import java.awt.Color;
import morfeov.coliseum.module.impl.themes.Theme;
import morfeov.coliseum.module.impl.themes.ThemeColor;
import net.minecraft.util.Mth;
import org.joml.Vector4f;

public final class CircleColors {
    private CircleColors() {}
    public static int primary() {return Theme.color(ThemeColor.ACCENT);}
    public static int secondary() {return Theme.color(ThemeColor.ACCENT_2);}
    public static int alpha(int color) {return color>>>24;}
    public static int red(int color) {return color>>16&255;}
    public static int green(int color) {return color>>8&255;}
    public static int blue(int color) {return color&255;}
    public static int argb(int red,int green,int blue,double alpha) {return (int)alpha<<24|red<<16|green<<8|blue;}
    public static Vector4f rgba(int color) {return new Vector4f(red(color)/255f,green(color)/255f,blue(color)/255f,alpha(color)/255f);}
    public static int opacity(int color,float alpha) {return (int)Math.clamp(alpha*255,0,255)<<24|color&0xffffff;}
    public static int lerp(int first,int second,float progress) {
        return argb((int)Mth.lerp(progress,red(first),red(second)),(int)Mth.lerp(progress,green(first),green(second)),
                (int)Mth.lerp(progress,blue(first),blue(second)),(int)Mth.lerp(progress,alpha(first),alpha(second)));
    }
    public static int themeGradient(int phase) {
        float time=((System.currentTimeMillis()%10000)/10000f+phase*16f/360f)%1;
        float blend=.5f-.5f*(float)Math.cos(time*Math.PI*2);
        return lerp(primary(),secondary(),blend)|0xff000000;
    }
    public static int saturatedPulse(int first,int second,int phase,int speed) {
        int angle=(int)((System.currentTimeMillis()/speed+phase)%360);
        angle=angle>180?360-angle:angle;
        int color=lerp(first,second,angle/180f);
        float[] hsv=Color.RGBtoHSB(red(color),green(color),blue(color),null);
        return Color.HSBtoRGB(hsv[0],Math.min(1,hsv[1]*1.5f),hsv[2]);
    }
}
