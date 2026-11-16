package morfeov.coliseum.module.impl.render;

import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;

@ModuleInfo(id = "item_physics", name = "Item Physics", category = Category.RENDER,
        description = "Предметы вращаются при падении и ложатся на землю")
public final class ItemPhysics extends Module {
    public interface RenderPose {
        void coliseum$setItemTilt(float tilt);
        float coliseum$itemTilt();
    }
}
