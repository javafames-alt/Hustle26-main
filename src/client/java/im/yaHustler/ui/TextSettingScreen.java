package im.yaHustler.ui;
import im.yaHustler.setting.TextSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class TextSettingScreen extends Screen {
    private final Screen parent;
    private final TextSetting setting;
    public TextSettingScreen(Screen parent,TextSetting setting){super(Component.literal(setting.name()));this.parent=parent;this.setting=setting;}
    @Override protected void init(){var input=addRenderableWidget(new EditBox(minecraft.font,width/2-120,height/2-10,240,20,title));input.setMaxLength(100);input.setValue(setting.get());input.setResponder(setting::set);setInitialFocus(input);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta){Draw.rounded(g,width/2-134,height/2-46,268,94,8,0xf018211c);g.text(minecraft.font,title,width/2-120,height/2-32,0xffffffff);super.extractRenderState(g,x,y,delta);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
