package im.yaHustler.ui;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CosmeticsInteractionTest {
    @Test void avatarPreviewFollowsTheWindowScaleAndPageTranslation() {
        var pose=new org.joml.Matrix3x2f().translation(40,27).scale(.78f).translate(9,3);
        var placed=GuiEntityPlacement.from(pose,300,60,420,230,45);
        assertEquals(45*.78f,placed.scale(),.0001);
        var center=pose.transformPosition(360,145,new org.joml.Vector2f());
        assertEquals(center.x,(placed.left()+placed.right())/2f,1);
        assertEquals(center.y,(placed.top()+placed.bottom())/2f,1);
        assertTrue(placed.right()-placed.left()<120);
    }

    @Test void transparentSpritePaddingDoesNotShrinkCatalogueThumbnails() {
        var cape=CosmeticArtwork.region(net.minecraft.resources.Identifier.fromNamespaceAndPath("pulse","textures/cosmetics/item_0.png"));
        assertTrue(cape.w()<cape.textureWidth()/2);
        assertTrue(cape.h()<cape.textureHeight());
        var hat=CosmeticArtwork.region(net.minecraft.resources.Identifier.fromNamespaceAndPath("pulse","textures/cosmetics/item_50.png"));
        assertTrue(hat.u()>0&&hat.v()>0);
        assertTrue(hat.w()>0&&hat.h()>0);
    }

    @Test void nativeMouseButtonsSwitchEveryWardrobeTab() throws Exception {
        var page = new GuiCosmeticsPage();
        page.layout(112, 40, 376, 282);
        var layout = new CosmeticsLayout(112, 40, 376, 282);
        for (int i = 1; i < CosmeticsLayout.CATEGORY_COUNT; i++) {
            var button = layout.category(i);
            assertTrue(page.click(button.centerX(), button.centerY(), InputConstants.MOUSE_BUTTON_LEFT));
            assertEquals(i, field(page, "category"));
            var previous = layout.category(i - 1);
            assertFalse(page.click(previous.centerX(), previous.centerY(), InputConstants.MOUSE_BUTTON_RIGHT));
            assertEquals(i, field(page, "category"));
        }
        // SDL left mouse is 1 in 26.3. The old literal zero rejected every real click.
        var all = layout.category(0);
        assertFalse(page.click(all.centerX(), all.centerY(), 0));
        assertTrue(page.click(all.centerX(), all.centerY(), InputConstants.MOUSE_BUTTON_LEFT));
        assertEquals(0, field(page, "category"));
    }

    @Test void clippingAndScrollKeepCardsInsideTheCatalogue() {
        for (int offset : new int[]{0, 75, 230}) {
            var layout = new CosmeticsLayout(offset, 38, 376, 282);
            for (int i = 0; i < CosmeticsLayout.CATEGORY_COUNT; i++) {
                var tab = layout.category(i);
                assertTrue(layout.catalog.contains(tab.x(), tab.y()));
                assertTrue(layout.catalog.contains(tab.right() - 1, tab.bottom() - 1));
                assertEquals(0, tab.intersect(layout.grid).h());
                assertEquals(0, tab.intersect(layout.preview).w());
            }
            for (int count : new int[]{0, 1, 3, 12, 193}) {
                int max = layout.maxScroll(count);
                if (count == 0) { assertEquals(0, max); continue; }
                var last = layout.card(count - 1, max);
                assertTrue(last.bottom() <= layout.grid.bottom());
                assertTrue(last.intersect(layout.grid).h() > 0);
                if (max > 0) assertEquals(layout.grid.bottom(), last.bottom());
            }
            var partial = layout.card(0, CosmeticsLayout.CARD_HEIGHT - 12).intersect(layout.grid);
            assertEquals(12, partial.h());
            assertFalse(partial.contains(partial.centerX(), layout.grid.y() - 1));
            assertTrue(partial.contains(partial.centerX(), layout.grid.y() + 1));
        }
    }

    @Test void zoomRotationAndResetRespondToNativeClicksAndRelease() throws Exception {
        var page = new GuiCosmeticsPage();
        page.layout(10, 20, 376, 282);
        var layout = new CosmeticsLayout(10, 20, 376, 282);
        var plus = layout.zoomIn;
        assertTrue(page.click(plus.centerX(), plus.centerY(), InputConstants.MOUSE_BUTTON_LEFT));
        assertEquals(1.1f, (float)field(page, "zoom"), .001f);
        var zoom = layout.zoomTrack;
        page.click(zoom.centerX(), zoom.centerY(), InputConstants.MOUSE_BUTTON_LEFT);
        assertTrue(page.drag(zoom.right() + 30, zoom.centerY(), 30, 0));
        assertEquals(1.5f, field(page, "zoom"));
        assertTrue(page.release());
        assertFalse(page.drag(0, 0, 12, 12));
        page.click(layout.model.centerX(), layout.model.centerY(), InputConstants.MOUSE_BUTTON_LEFT);
        assertTrue(page.drag(0, 0, 10, 100));
        assertEquals(39f, field(page, "yaw")); assertEquals(35f, field(page, "pitch"));
        page.release();
        page.click(layout.autoRotate.centerX(), layout.autoRotate.centerY(), InputConstants.MOUSE_BUTTON_LEFT);
        assertEquals(true, field(page, "autoRotate"));
        page.click(layout.resetView.centerX(), layout.resetView.centerY(), InputConstants.MOUSE_BUTTON_LEFT);
        assertEquals(1f, field(page, "zoom")); assertEquals(25f, field(page, "yaw"));
        assertEquals(0f, field(page, "pitch")); assertEquals(false, field(page, "autoRotate"));
    }

    private static Object field(GuiCosmeticsPage page, String name) throws Exception {
        var field = GuiCosmeticsPage.class.getDeclaredField(name);
        field.setAccessible(true); return field.get(page);
    }
}
