package im.yaHustler.wyvern.ui.clickgui

import im.yaHustler.wyvern.duck.RenderedEntity
import im.yaHustler.wyvern.module.impl.render.NameTags
import im.yaHustler.wyvern.render.effect.GlowRenderer
import im.yaHustler.wyvern.render.font.Icons
import im.yaHustler.wyvern.render.gui.Renderer2D
import im.yaHustler.wyvern.setting.MultiSetting
import im.yaHustler.wyvern.ui.Rect
import im.yaHustler.wyvern.ui.clickgui.component.BooleanComponent
import im.yaHustler.wyvern.ui.clickgui.component.SliderComponent
import im.yaHustler.wyvern.ui.theme.Mouse
import im.yaHustler.wyvern.ui.theme.Theme
import im.yaHustler.wyvern.util.math.Animation
import im.yaHustler.wyvern.util.math.Easing
import im.yaHustler.wyvern.util.math.Smooth
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f

class EspPreview {
    private enum class Tab(val title: String) { PLAYERS("Игроки"), FRIENDS("Друзья"), MOBS("Мобы"), DROPS("Предметы") }

    private enum class Mob(val label: String, val type: EntityType<out LivingEntity>) {
        ZOMBIE("Зомби", EntityTypes.ZOMBIE),
        SKELETON("Скелет", EntityTypes.SKELETON),
        CREEPER("Крипер", EntityTypes.CREEPER),
        PIG("Свинья", EntityTypes.PIG),
    }

    private class Chip(val label: String, val on: () -> Boolean, val toggle: () -> Unit)
    private class Section(val title: String, val chips: List<Chip>)

    private val mc get() = Minecraft.getInstance()

    var isOpen = false
        private set(value) {
            field = value
            opened = value
        }
    private val dockAnim = Animation(260, Easing.OUT_CUBIC)
    val dock get() = dockAnim.update(isOpen)

    private var tab = Tab.PLAYERS
    private var mob = Mob.ZOMBIE
    private val mobs = mutableMapOf<Mob, LivingEntity>()
    private var drop: ItemEntity? = null
    private var entityLevel: Level? = null

    private var yaw = 25f
    private var pitch = 8f
    private var zoom = 1f
    private var dragging = false
    private var lastMouseX = 0f
    private var lastMouseY = 0f
    private var idleSince = System.currentTimeMillis()

    val panel = Rect()
    private val viewport = Rect()
    private val gearRect = Rect()
    private val closeRect = Rect()
    private val manageRect = Rect()
    private val sheetRect = Rect()
    private val sheetClose = Rect()
    private val popupRect = Rect()
    private val tabRects = Tab.entries.associateWith { Rect() }
    private val mobRects = Mob.entries.associateWith { Rect() }
    private val chipRects = mutableListOf<Pair<Rect, Chip>>()

    private val hover = mutableMapOf<Any, Animation>()
    private val tabPill = Smooth(-1f, speed = 24f)
    private val sheetAnim = Animation(240, Easing.OUT_CUBIC)
    private var sheetOpen = false
    private var sheetContent = 0f
    private val popupAnim = Animation(160, Easing.OUT_CUBIC)
    private var popupOpen = false

    private val popupControls = listOf(
        SliderComponent(NameTags.scaleSetting),
        BooleanComponent(NameTags.spinModel),
        SliderComponent(NameTags.spinSpeed),
        SliderComponent(NameTags.glowFill),
    )

    private val sections = listOf(
        Section("NameTags", multiChips(NameTags.elements)),
        Section("Glow ESP", multiChips(NameTags.glowTargets)),
    )

    fun toggle() = if (isOpen) close() else open()

    fun open() {
        isOpen = true
        idleSince = System.currentTimeMillis()
    }

    fun close() {
        isOpen = false
        dragging = false
        sheetOpen = false
        popupOpen = false
    }

    private fun hoverOf(key: Any, active: Boolean) = hover.getOrPut(key) { Animation(110) }.update(active)

    private fun multiChips(setting: MultiSetting) = setting.options.map { option -> Chip(option, { option in setting }) { setting.toggle(option) } }

    fun render(r: Renderer2D, x: Float, y: Float, h: Float, mx: Float, my: Float) {
        val t = dock
        if (t <= 0.001f) return
        panel.set(x + (1f - t) * 16f, y, WIDTH, h)
        val active = isOpen && t > 0.95f
        val hx = if (active) mx else -1f
        val hy = if (active) my else -1f

        r.nextLayer()
        r.withAlpha(t) {
            r.shadow(panel.x, panel.y + 4f, panel.w, panel.h, Theme.WINDOW_RADIUS, 24f, Theme.shadow)
            r.roundedRect(panel.x, panel.y, panel.w, panel.h, Theme.WINDOW_RADIUS, Theme.background)
            r.outline(panel.x, panel.y, panel.w, panel.h, Theme.WINDOW_RADIUS, 0.5f, Theme.cardBorder)
            val sheetT = sheetAnim.update(sheetOpen)
            val popupT = popupAnim.update(popupOpen)
            val noPopup = popupT < 0.01f
            val free = sheetT < 0.01f && noPopup
            val ix = if (free) hx else -1f
            val iy = if (free) hy else -1f
            renderHeader(r, if (noPopup) hx else -1f, if (noPopup) hy else -1f)
            renderTabs(r, ix, iy)
            renderViewport(r, ix, iy)
            renderManageButton(r, ix, iy)
            if (sheetT > 0.01f) renderSheet(r, sheetT, if (noPopup) hx else -1f, if (noPopup) hy else -1f)
            if (popupT > 0.01f) renderPopup(r, popupT, hx, hy)
        }
    }

    private fun renderHeader(r: Renderer2D, mx: Float, my: Float) {
        val cy = panel.y + HEADER / 2f
        gearRect.set(panel.x + 8f, cy - 8f, 16f, 16f)
        val gear = maxOf(hoverOf(gearRect, gearRect.contains(mx, my)), popupAnim.update(popupOpen))
        r.roundedRect(gearRect.x, gearRect.y, gearRect.w, gearRect.h, 4f, Theme.controlHover.withAlpha((Theme.controlHover.alpha * gear).toInt()))
        r.icon(Icons.SETTINGS, gearRect.centerX, gearRect.centerY, 9.5f, Theme.textDim.mix(Theme.text, gear))
        val title = "Предпросмотр ESP"
        r.textCenteredY(title, panel.centerX - r.textWidth(title, 7.5f, Theme.titleFont) / 2f, cy, Theme.text, 7.5f, Theme.titleFont)
        closeRect.set(panel.x + panel.w - 24f, cy - 8f, 16f, 16f)
        val close = hoverOf(closeRect, closeRect.contains(mx, my))
        r.roundedRect(closeRect.x, closeRect.y, closeRect.w, closeRect.h, 4f, Theme.controlHover.withAlpha((Theme.controlHover.alpha * close).toInt()))
        r.icon(Icons.CLOSE, closeRect.centerX, closeRect.centerY, 8f, Theme.textFaint.mix(Theme.text, close))
    }

    private fun renderTabs(r: Renderer2D, mx: Float, my: Float) {
        val x = panel.x + 8f
        val y = panel.y + HEADER
        val w = panel.w - 16f
        r.roundedRect(x, y, w, TAB_H, 5f, Theme.card)
        val tw = w / Tab.entries.size
        Tab.entries.forEachIndexed { i, entry -> tabRects.getValue(entry).set(x + tw * i, y, tw, TAB_H) }
        val active = tabRects.getValue(tab)
        if (tabPill.value < 0f) tabPill.snap(active.x)
        val px = tabPill.update(active.x)
        r.roundedRect(px + 1.5f, y + 1.5f, tw - 3f, TAB_H - 3f, 4f, Theme.controlHover.mix(Theme.accent, 0.35f))
        for (entry in Tab.entries) {
            val rect = tabRects.getValue(entry)
            val hv = hoverOf(rect, rect.contains(mx, my))
            val color = if (entry == tab) Theme.text else Theme.textFaint.mix(Theme.textDim, hv)
            r.textCenteredY(entry.title, rect.centerX - r.textWidth(entry.title, TAB_TEXT, Theme.bodyFont) / 2f, rect.centerY, color, TAB_TEXT, Theme.bodyFont)
        }
        if (tab != Tab.MOBS) return
        val widths = Mob.entries.map { r.textWidth(it.label, 6f, Theme.bodyFont) + 10f }
        var cx = panel.centerX - (widths.sum() + 3f * (widths.size - 1)) / 2f
        val cy = y + TAB_H + 4f
        Mob.entries.forEachIndexed { i, m ->
            val rect = mobRects.getValue(m).set(cx, cy, widths[i], 12f)
            val sel = m == mob
            val hv = hoverOf(rect, rect.contains(mx, my))
            r.roundedRect(rect.x, rect.y, rect.w, rect.h, 6f, if (sel) Theme.accent.withAlpha(150) else Theme.control.mix(Theme.controlHover, hv))
            r.textCenteredY(m.label, rect.x + 5f, rect.centerY, if (sel) Theme.onAccent else Theme.textDim, 6f, Theme.bodyFont)
            cx += widths[i] + 3f
        }
    }

    private fun renderViewport(r: Renderer2D, mx: Float, my: Float) {
        val top = panel.y + HEADER + TAB_H + (if (tab == Tab.MOBS) 20f else 6f)
        viewport.set(panel.x + 8f, top, panel.w - 16f, panel.y + panel.h - 30f - top)

        if (dragging) {
            yaw += (mx - lastMouseX) * 0.9f
            pitch = (pitch + (my - lastMouseY) * 0.5f).coerceIn(-20f, 40f)
            lastMouseX = mx
            lastMouseY = my
            idleSince = System.currentTimeMillis()
        } else if (NameTags.spinModel.value && System.currentTimeMillis() - idleSince > IDLE_SPIN_DELAY) {
            yaw += 0.35f * NameTags.spinSpeed.value.toFloat()
        }

        val level = mc.level
        if (level == null) {
            r.centeredText("Зайдите в мир", viewport.centerX, viewport.centerY - 4f, Theme.textFaint, Theme.NAME_SIZE, Theme.bodyFont)
            return
        }
        if (level !== entityLevel) {
            mobs.clear()
            drop = null
            entityLevel = level
        }
        if (tab == Tab.DROPS) renderDrop(r, level) else (subject(level) ?: return).let { renderModel(r, it) }
    }

    private fun subject(level: Level): LivingEntity? = when (tab) {
        Tab.MOBS -> mobs.getOrPut(mob) {
            val entity = mob.type.create(level, EntitySpawnReason.LOAD) ?: return null
            entity.id = PREVIEW_ID_BASE - mob.ordinal
            equip(entity)
            entity
        }
        else -> mc.player
    }

    private fun renderModel(r: Renderer2D, entity: LivingEntity) {
        val height = entity.bbHeight
        val width = entity.bbWidth
        val scale = minOf(viewport.h * 0.58f / height, viewport.w * 0.42f / maxOf(width, 0.6f)) * zoom
        val cx = viewport.centerX
        val cy = viewport.centerY + 10f

        val pitchRad = Math.toRadians(pitch.toDouble()).toFloat()
        val rotation = Quaternionf().rotateZ(Math.PI.toFloat()).mul(Quaternionf().rotateX(pitchRad))
        val camera = Quaternionf().rotateX(pitchRad)
        val translation = rotation.transform(Vector3f(0f, height / 2f, 0f)).negate()

        val state = mc.entityRenderDispatcher.getRenderer(entity).createRenderState(entity, 1f)
        (state as RenderedEntity).`wyvern$setEntity`(null)
        state.shadowPieces.clear()
        state.outlineColor = 0
        state.nameTag = null
        if (state is LivingEntityRenderState) {
            state.bodyRot = 180f + yaw
            state.yRot = 0f
            state.xRot = 0f
            state.walkAnimationPos = 0f
            state.walkAnimationSpeed = 0f
            state.ageInTicks = 0f
            state.boundingBoxWidth /= state.scale
            state.boundingBoxHeight /= state.scale
            state.scale = 1f
        }
        if (state is HumanoidRenderState) {
            state.isCrouching = false
            state.swimAmount = 0f
            state.speedValue = 1f
        }
        val half = minOf(cy - viewport.y, viewport.y + viewport.h - cy) - 1f
        val glowTarget = when (tab) {
            Tab.FRIENDS -> "Друзья"
            Tab.MOBS -> "Мобы"
            else -> "Игроки"
        }
        GlowRenderer.previewState = state.takeIf { glowTarget in NameTags.glowTargets }
        r.nextLayer()
        r.graphics.entity(
            state, scale, translation, rotation, camera,
            viewport.x.toInt(), (cy - half).toInt(), (viewport.x + viewport.w).toInt(), (cy + half).toInt(),
        )
        r.nextLayer()

        val model = Quaternionf(rotation).mul(Quaternionf().rotateY(Math.toRadians(-yaw.toDouble()).toFloat()))
        fun project(x: Float, y: Float, z: Float): Vector2f {
            val v = model.transform(Vector3f(x, y - height / 2f, z))
            return Vector2f(cx + v.x * scale, cy + v.y * scale)
        }
        val w = width / 2f
        val corners = listOf(
            project(-w, 0f, -w), project(w, 0f, -w), project(w, 0f, w), project(-w, 0f, w),
            project(-w, height, -w), project(w, height, -w), project(w, height, w), project(-w, height, w),
        )
        r.scissor(viewport.x, viewport.y, viewport.w, viewport.h) {
            if (NameTags.enabled) {
                val head = project(0f, height, 0f)
                val top = minOf(head.y, corners.minOf { it.y }) - 6f
                NameTags.drawPreview(r, entity, head.x, top, (scale / 70f).coerceIn(0.7f, 1.3f))
            }
        }
    }

    private fun renderDrop(r: Renderer2D, level: Level) {
        val entity = drop ?: ItemEntity(level, 0.0, 0.0, 0.0, ItemStack(Items.DIAMOND_SWORD, 3)).also {
            it.id = PREVIEW_ID_BASE - 100
            drop = it
        }
        // a real dropped-item model (not the GUI icon), so Glow ESP can glow around its silhouette like on mobs
        val scale = viewport.h * 0.42f / DROP_HEIGHT * zoom
        val cy = viewport.centerY + 6f
        val pitchRad = Math.toRadians(pitch.toDouble()).toFloat()
        val rotation = Quaternionf().rotateZ(Math.PI.toFloat())
            .mul(Quaternionf().rotateX(pitchRad))
            .mul(Quaternionf().rotateY(Math.toRadians(yaw.toDouble()).toFloat()))
        val camera = Quaternionf().rotateX(pitchRad)
        val translation = rotation.transform(Vector3f(0f, DROP_HEIGHT / 2f, 0f)).negate()

        val state = mc.entityRenderDispatcher.getRenderer(entity).createRenderState(entity, 1f)
        (state as RenderedEntity).`wyvern$setEntity`(null)
        state.shadowPieces.clear()
        state.outlineColor = 0
        state.nameTag = null
        state.lightCoords = FULL_BRIGHT
        if (state is ItemEntityRenderState) {
            state.ageInTicks = 0f
            state.bobOffset = 0f
        }
        GlowRenderer.previewState = state.takeIf { "Предметы" in NameTags.glowTargets }
        val half = minOf(cy - viewport.y, viewport.y + viewport.h - cy) - 1f
        r.nextLayer()
        r.graphics.entity(
            state, scale, translation, rotation, camera,
            viewport.x.toInt(), (cy - half).toInt(), (viewport.x + viewport.w).toInt(), (cy + half).toInt(),
        )
        r.nextLayer()
        if (NameTags.enabled) {
            r.scissor(viewport.x, viewport.y, viewport.w, viewport.h) {
                NameTags.drawDropPreview(r, entity, viewport.centerX, cy - DROP_HEIGHT / 2f * scale - 6f, 1.1f)
            }
        }
    }

    private fun renderManageButton(r: Renderer2D, mx: Float, my: Float) {
        val label = "Управление элементами"
        val tw = r.textWidth(label, 7f, Theme.bodyFont) + 14f
        manageRect.set(panel.centerX - (tw + 6f) / 2f, panel.y + panel.h - 24f, tw + 6f, 16f)
        val hv = hoverOf(manageRect, manageRect.contains(mx, my))
        r.roundedRect(manageRect.x, manageRect.y, manageRect.w, manageRect.h, 5f, Theme.control.mix(Theme.controlHover, hv))
        r.icon(Icons.WIDGETS, manageRect.x + 9f, manageRect.centerY, 8f, Theme.textDim.mix(Theme.text, hv))
        r.textCenteredY(label, manageRect.x + 17f, manageRect.centerY, Theme.textDim.mix(Theme.text, hv), 7f, Theme.bodyFont)
    }

    private fun renderSheet(r: Renderer2D, t: Float, mx: Float, my: Float) {
        val h = minOf(sheetContent + SHEET_TOP + 10f, panel.h - HEADER)
        val y = panel.y + panel.h - h * Easing.OUT_CUBIC.ease(t)
        sheetRect.set(panel.x, y, panel.w, panel.y + panel.h - y)
        r.nextLayer()
        r.scissor(panel.x, panel.y, panel.w, panel.h) {
            r.roundedRect(panel.x, y, panel.w, h + 8f, Theme.WINDOW_RADIUS, Theme.card.withAlpha(255))
            r.rect(panel.x, y, panel.w, 0.5f, Theme.cardBorder)
            r.textCenteredY("Управление элементами", panel.x + 10f, y + 11f, Theme.text, 7.5f, Theme.titleFont)
            sheetClose.set(panel.x + panel.w - 22f, y + 3f, 16f, 16f)
            r.icon(Icons.CLOSE, sheetClose.centerX, sheetClose.centerY, 8f, Theme.textFaint.mix(Theme.text, hoverOf(sheetClose, sheetClose.contains(mx, my))))

            chipRects.clear()
            val listTop = y + SHEET_TOP
            var cy = listTop
            for (section in sections) {
                r.text(section.title, panel.x + 10f, cy, Theme.textFaint, 6.5f, Theme.bodyFont)
                cy += 10f
                var cx = panel.x + 10f
                for (chip in section.chips) {
                    val cw = r.textWidth(chip.label, 6.5f, Theme.bodyFont) + 10f
                    if (cx + cw > panel.x + panel.w - 10f) {
                        cx = panel.x + 10f
                        cy += 15f
                    }
                    val rect = Rect(cx, cy, cw, 12f)
                    chipRects += rect to chip
                    val on = chip.on()
                    val hv = hoverOf("${section.title}/${chip.label}", rect.contains(mx, my))
                    r.roundedRect(rect.x, rect.y, rect.w, rect.h, 3.5f,
                        if (on) Theme.accent.withAlpha((200 + 55 * hv).toInt()) else Theme.control.mix(Theme.controlHover, hv))
                    r.textCenteredY(chip.label, rect.x + 5f, rect.centerY, if (on) Theme.onAccent else Theme.textDim, 6.5f, Theme.bodyFont)
                    cx += cw + 3f
                }
                cy += 19f
            }
            sheetContent = cy - listTop - 7f
        }
    }

    private fun renderPopup(r: Renderer2D, t: Float, mx: Float, my: Float) {
        val w = panel.w - 16f
        val h = popupControls.sumOf { it.height.toDouble() }.toFloat() + 6f
        popupRect.set(panel.x + 8f, gearRect.y + gearRect.h + 4f, w, h)
        r.nextLayer()
        r.withAlpha(t) {
            r.shadow(popupRect.x, popupRect.y + 2f, w, h, 6f, 12f, Theme.shadow)
            r.roundedRect(popupRect.x, popupRect.y, w, h, 6f, Theme.card.withAlpha(255))
            r.outline(popupRect.x, popupRect.y, w, h, 6f, 0.5f, Theme.cardBorder)
            var y = popupRect.y + 7f
            for (control in popupControls) {
                control.render(r, popupRect.x + 8f, y, w - 16f, mx, my)
                y += control.height
            }
        }
    }

    private fun equip(entity: LivingEntity) {
        when (entity.type) {
            EntityTypes.ZOMBIE -> {
                entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack(Items.DIAMOND_SWORD))
                entity.setItemSlot(EquipmentSlot.HEAD, ItemStack(Items.DIAMOND_HELMET))
                entity.setItemSlot(EquipmentSlot.CHEST, ItemStack(Items.DIAMOND_CHESTPLATE))
                entity.setItemSlot(EquipmentSlot.LEGS, ItemStack(Items.IRON_LEGGINGS))
                entity.setItemSlot(EquipmentSlot.FEET, ItemStack(Items.IRON_BOOTS))
            }
            EntityTypes.SKELETON -> {
                entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack(Items.BOW))
                entity.setItemSlot(EquipmentSlot.HEAD, ItemStack(Items.CHAINMAIL_HELMET))
            }
            else -> {}
        }
    }

    fun mouseClicked(mx: Float, my: Float, button: Int): Boolean {
        if (!isOpen || !panel.contains(mx, my)) {
            popupOpen = false
            return false
        }
        if (popupOpen) {
            if (popupRect.contains(mx, my)) popupControls.any { it.mouseClicked(mx, my, button) } else popupOpen = false
            return true
        }
        if (button != Mouse.LEFT) return true
        when {
            gearRect.contains(mx, my) -> popupOpen = true
            closeRect.contains(mx, my) -> close()
            sheetOpen -> when {
                sheetClose.contains(mx, my) -> sheetOpen = false
                sheetRect.contains(mx, my) -> chipRects.firstOrNull { it.first.contains(mx, my) }?.second?.toggle?.invoke()
                else -> sheetOpen = false
            }
            manageRect.contains(mx, my) -> sheetOpen = true
            else -> {
                Tab.entries.firstOrNull { tabRects.getValue(it).contains(mx, my) }?.let { tab = it; return true }
                if (tab == Tab.MOBS) Mob.entries.firstOrNull { mobRects.getValue(it).contains(mx, my) }?.let { mob = it; return true }
                if (viewport.contains(mx, my)) {
                    dragging = true
                    lastMouseX = mx
                    lastMouseY = my
                }
            }
        }
        return true
    }

    fun mouseReleased(button: Int) {
        if (button == Mouse.LEFT) dragging = false
        popupControls.forEach { it.mouseReleased(button) }
    }

    fun mouseScrolled(mx: Float, my: Float, amount: Float): Boolean {
        if (!isOpen || !panel.contains(mx, my)) return false
        if (!sheetOpen && viewport.contains(mx, my)) zoom = (zoom + amount * 0.1f).coerceIn(0.5f, 2f)
        return true
    }

    companion object {
        @Volatile
        var opened = false
            private set

        const val WIDTH = 200f
        const val GAP = 10f
        private const val HEADER = 26f
        private const val TAB_H = 16f
        private const val TAB_TEXT = 6.5f
        private const val SHEET_TOP = 24f
        private const val IDLE_SPIN_DELAY = 2500L
        private const val PREVIEW_ID_BASE = -0x7FFF0000
        private const val DROP_HEIGHT = 0.6f
        private const val FULL_BRIGHT = 0xF000F0
    }
}
