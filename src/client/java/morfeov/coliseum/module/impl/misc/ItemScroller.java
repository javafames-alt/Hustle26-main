package morfeov.coliseum.module.impl.misc;

import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.Module;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.setting.NumberSetting;

@ModuleInfo(id = "item_scroller", name = "ItemScroller", category = Category.MISC,
        description = "Быстрый перенос стопок при удержании Shift и ЛКМ")
public final class ItemScroller extends Module {
    public final NumberSetting delay = add(new NumberSetting("Задержка", 0, 0, 100, 10));
}
