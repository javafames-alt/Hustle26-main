package im.yaHustler.module.impl.misc;

import im.yaHustler.module.Category;
import im.yaHustler.module.Module;
import im.yaHustler.module.ModuleInfo;
import im.yaHustler.setting.NumberSetting;

@ModuleInfo(id = "item_scroller", name = "ItemScroller", category = Category.MISC,
        description = "Быстрый перенос стопок при удержании Shift и ЛКМ")
public final class ItemScroller extends Module {
    public final NumberSetting delay = add(new NumberSetting("Задержка", 0, 0, 100, 10));
}
