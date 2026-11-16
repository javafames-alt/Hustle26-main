package morfeov.coliseum.module.impl.render;
final class EspColor {
 static int alpha(int c){return c>>>24;}static int red(int c){return c>>16&255;}static int green(int c){return c>>8&255;}static int blue(int c){return c&255;}
 static int rgba(int r,int g,int b,int a){return a<<24|r<<16|g<<8|b;}static int withAlpha(int c,int a){return c&0xffffff|Math.clamp(a,0,255)<<24;}static int multiplyAlpha(int c,float a){return withAlpha(c,(int)(alpha(c)*a));}
}
