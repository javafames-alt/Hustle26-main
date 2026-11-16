package im.yaHustler.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class GuiTransformTest {
    @Test void visibleControlsAndMouseCoordinatesStayAlignedAtEveryZoom() {
        for (float scale : new float[]{.55f, .82f, 1f, 1.15f}) {
            GuiTransform transform = GuiTransform.centered(scale, 960, 540, 650, 455);
            for (double[] point : new double[][]{{155, 65}, {180, 100}, {420, 160}, {760, 490}}) {
                assertEquals(point[0], transform.uiX(transform.screenX(point[0])), .0002);
                assertEquals(point[1], transform.uiY(transform.screenY(point[1])), .0002);
            }
            assertEquals(480, transform.screenX(480), .0001);
            assertEquals(270, transform.screenY(270), .0001);
        }
    }
    @Test void largeZoomKeepsTheWholeWindowInsideTheViewport() {
        GuiTransform transform = GuiTransform.centered(1.15f, 400, 260, 360, 252);
        float left = transform.screenX(20), right = transform.screenX(380);
        float top = transform.screenY(4), bottom = transform.screenY(256);
        assertTrue(left >= 5.99f && right <= 394.01f);
        assertTrue(top >= 5.99f && bottom <= 254.01f);
    }
}
