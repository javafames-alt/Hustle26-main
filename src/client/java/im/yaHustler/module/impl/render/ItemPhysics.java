package im.yaHustler.module.impl.render;

import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;

@ModuleInfo(id = "item_physics", name = "Item Physics", category = Category.RENDER,
        description = "Предметы вращаются при падении и ложатся на землю")
public final class ItemPhysics extends Module {
    public interface RenderPose {
        void hustle263$setItemTilt(float tilt);
        float hustle263$itemTilt();
    }
}
