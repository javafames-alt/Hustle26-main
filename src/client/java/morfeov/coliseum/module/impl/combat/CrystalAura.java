package morfeov.coliseum.module.impl.combat;

import java.util.*;
import java.util.function.Predicate;
import morfeov.coliseum.Coliseum;
import morfeov.coliseum.mixin.CombatGameModeAccessor;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.module.impl.combat.crystal.CrystalDamage;
import morfeov.coliseum.module.impl.combat.crystal.CrystalProfile;
import morfeov.coliseum.module.impl.combat.crystal.CrystalProfile.Swap;
import morfeov.coliseum.port.*;
import morfeov.coliseum.port.event.*;
import morfeov.coliseum.rotation.MoveFix;
import morfeov.coliseum.rotation.Rotation;
import morfeov.coliseum.rotation.Rotations;
import morfeov.coliseum.setting.*;
import morfeov.coliseum.util.InventoryActions;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.*;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Zenith CrystalAuraModule adapted from Yarn/eventapi to Coliseum's 26.3 client APIs. */
@ModuleInfo(id="crystal_aura", name="CrystalAura", category=Category.COMBAT,
        description="Кристаллы, якоря, подкоп и защита")
public final class CrystalAura extends PortModule {
    private static final String OWNER = "CrystalAura";
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private final ModeSetting preset = add(new ModeSetting("Пресет", "Grim", "Grim", "DonutSMP", "Без ограничений"));
    private final NumberSetting targetRange = number("Радиус цели", 10, 4, 16, .5);
    private final NumberSetting minDamage = number("Мин. урон", 6, 1, 20, .5);
    private final NumberSetting maxSelfDamage = number("Макс. урон себе", 8, 0, 20, .5);
    private final NumberSetting faceplace = number("Faceplace: здоровье", 8, 0, 36, 1);
    private final NumberSetting armorBreak = number("Faceplace: прочность %", 15, 0, 50, 5);
    private final NumberSetting predictTicks = number("Предикт тиков", 1, 0, 6, 1);
    private final BooleanSetting prePlace = flag("Предустановка", true);
    private final BooleanSetting timing = flag("Учёт неуязвимости", true);
    private final BooleanSetting anchors = flag("Якоря", false);
    private final BooleanSetting autoObsidian = flag("Автообсидиан", true);
    private final BooleanSetting autoDig = flag("Автоподкоп", false);
    private final BooleanSetting fromInventory = flag("Из инвентаря", true);
    private final BooleanSetting shield = flag("Блок прикрытия", false);
    private final BooleanSetting antiWeakness = flag("Антислабость", true);
    private final BooleanSetting antiSuicide = flag("Антисуицид", true);
    private final BooleanSetting pauseOnUse = flag("Пауза при использовании", true);
    private final BooleanSetting yieldWebTrap = flag("Уступать AutoWebTrap", true);
    private final BooleanSetting render = flag("Визуализация", true);
    private final BooleanSetting knockup = flag("Подброс цели", true);
    private final BooleanSetting cityMine = flag("CityMine", true);
    private final BooleanSetting bomber = flag("Bomber", false);
    private final BooleanSetting antiTotem = flag("Антитотем", true);
    private final BooleanSetting fallback = flag("Убирать мешающие кристаллы", true);
    private final BooleanSetting slowPlace = flag("Медленная установка", false);
    private final BooleanSetting explosionSync = flag("Синхронизация взрывов", true);
    private final BooleanSetting combatLog = flag("Журнал боя", true);
    private final BooleanSetting antiMine = flag("Антиподкоп", true);
    private final BooleanSetting crystalDefense = flag("Защита от кристаллов", true);
    private final BooleanSetting webTrap = flag("Паутина под цель", true);
    private final KeySetting surroundKey = add(new KeySetting("Клавиша Surround"));
    private final ModeSetting surroundMode = add(new ModeSetting("Surround", "Обычный", "Обычный", "Двойной", "City", "Бункер"));
    private final NumberSetting mineDamage = number("Урон для копания", 10, 4, 36, .5);
    private final NumberSetting mineTime = number("Макс. время копания (с)", 3, .5, 8, .5);
    private final NumberSetting shieldDamage = number("Урон для прикрытия", 6, 1, 36, .5);

    public enum Action { BREAK, MELEE, CRYSTAL, OBSIDIAN, DIG, SHIELD, WEB, ANCHOR_PLACE, ANCHOR_CHARGE, ANCHOR_EXPLODE }
    public record Visual(BlockPos pos, int tick, int color) {}
    private record Candidate(Action action, BlockPos pos, Vec3 blast, float power, BlockPos added,
                             Set<BlockPos> removed, EndCrystal crystal, float bonus, int mineTicks) {}
    private record Plan(Candidate candidate, Player target, float enemy, float self, float score, boolean ready, BlockHitResult hit, Vec3 aim) {}
    private record Pending(Action action, Item item, int expires) {}
    private record HitWindow(float damage, int expires) {}
    private record KnockPrediction(AABB box, Vec3 velocity, int starts) {}
    private final CrystalDamage damage = new CrystalDamage();
    private final Map<BlockPos, Pending> pending = new HashMap<>();
    private final Map<BlockPos, Integer> blacklist = new HashMap<>();
    private final Map<Integer, Integer> attacked = new HashMap<>();
    private final Map<Integer, Double> attackDistances = new HashMap<>();
    private final Map<Integer, Integer> popped = new HashMap<>();
    private final Map<Integer, HitWindow> hitWindows = new HashMap<>();
    private final Map<BlockPos, Integer> enemyMining = new HashMap<>();
    private final Set<Integer> knownCrystals = new HashSet<>();
    private final Map<Integer, KnockPrediction> knockPredictions = new HashMap<>();
    private final Map<Integer, AABB> immediateBoxes = new HashMap<>(), futureBoxes = new HashMap<>();
    private final List<Visual> visuals = new ArrayList<>();
    private List<Player> targets = List.of(), friends = List.of();
    private CrystalProfile profile;
    private Plan plan;
    private Object world;
    private Player session, target;
    private int tick = Integer.MIN_VALUE, usedActions, lastPlace = Integer.MIN_VALUE, lastBreak = Integer.MIN_VALUE;
    private int originalSlot = -1, selectedSlot = -1, lastActive, lastWeb = -100, surroundUntil;
    private float adaptiveReach = 3;
    private BlockPos mining, surroundOrigin, lastBase;
    private int miningStarted;
    private boolean executing, ownsRotation;

    private NumberSetting number(String name, double value, double min, double max, double step) { return add(new NumberSetting(name, value, min, max, step)); }
    private BooleanSetting flag(String name, boolean value) { return add(new BooleanSetting(name, value)); }
    private CrystalProfile settingsProfile() {
        return CrystalProfile.preset(preset.get());
    }
    public Player target() { return enabled() ? target : null; }
    public boolean isCrystalBusy() { return enabled() && !paused() && (Rotations.owns(this) || plan != null || mining != null || !pending.isEmpty()); }
    public List<Visual> visibleBlocks() { return enabled() && render.enabled() ? List.copyOf(visuals) : List.of(); }
    private int now() { return mc.player == null ? 0 : mc.player.tickCount; }
    private int pingTicks() {
        var info = mc.getConnection() == null || mc.player == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : Math.clamp(Math.round(info.getLatency() / 50f), 0, 20);
    }
    private int confirmTicks() { return Math.clamp(pingTicks() + 3, 3, 20); }
    private double reach() { return profile.strictReach() ? Math.min(profile.breakRange(), adaptiveReach) : profile.breakRange(); }
    private boolean paused() {
        if (!InventoryActions.ready(mc) || mc.level == null || !mc.player.isAlive() || mc.player.isSpectator() || mc.player.isPassenger()) return true;
        var pearl = Coliseum.MODULES.get(morfeov.coliseum.module.impl.player.ClickPearl.class);
        return pauseOnUse.enabled() && mc.player.isUsingItem() || pearl != null && pearl.isThrowing();
    }

    @Override public void tick(Minecraft ignored) {
        prepare();
        if (profile != null && !profile.postMotion() && plan != null && !paused() && aligned(plan)) {
            mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(mc.player.getYRot(), mc.player.getXRot(), mc.player.onGround(), mc.player.horizontalCollision));
            executeBudget();
        }
    }
    private void prepare() {
        if (world != mc.level || session != mc.player || tick > now()) { reset(); world = mc.level; session = mc.player; }
        if (mc.player == null || mc.level == null) return;
        if (tick == now()) return;
        tick = now(); usedActions = 0; profile = settingsProfile(); plan = null; target = null;
        damage.reset(); immediateBoxes.clear(); futureBoxes.clear();
        cleanup();
        if (surroundKey.pressed(mc)) { surroundOrigin = mc.player.blockPosition(); surroundUntil = now() + 60; }
        if (paused()) { stopMining(); releaseRotation(); restoreSlot(); return; }
        targets = mc.level.players().stream().map(p -> (Player) p).filter(this::enemy).sorted(Comparator.comparingDouble(mc.player::distanceToSqr)).limit(6).toList();
        friends = mc.level.players().stream().map(p -> (Player) p).filter(p -> p != mc.player && p.isAlive() && (mc.player.isAlliedTo(p) || morfeov.coliseum.friend.Friends.contains(p)) && mc.player.distanceToSqr(p) <= 256).toList();
        for (EndCrystal crystal : crystals(12)) confirmCrystal(crystal);
        plan = selectPlan();
        if (plan == null) { stopMining(); releaseRotation(); if (now() - lastActive >= 10) restoreSlot(); return; }
        target = plan.target(); lastActive = now();
        if (mining != null && (plan.candidate.action != Action.DIG || !mining.equals(plan.candidate.pos))) stopMining();
        aim(plan.aim);
    }

    @Override public void onInput(EventInput event) {
        prepare();
        if (plan != null && ownsRotation) MoveFix.request(SilentComponent.getFreeYaw(), 1);
    }
    @Override public void onPostMotion(EventPostMotion event) {
        prepare();
        if (plan == null || paused() || executing) return;
        if (profile.postMotion()) executeBudget();
    }
    private void executeBudget() {
        executing = true;
        try {
            while (plan != null && usedActions < profile.actions() && !paused()) {
                Plan current = plan;
                if (!aligned(current) || !execute(current)) break;
                damage.reset();
                if (current.candidate.action == Action.DIG) break;
                plan = selectPlan();
                target = plan == null ? current.target : plan.target;
                // Further actions wait for a server-visible rotation when they require another angle.
                if (plan != null && !aligned(plan)) break;
            }
        } finally { executing = false; }
    }

    private boolean enemy(Player p) {
        return p != mc.player && !AntiBot.rejects(p) && p.isAlive() && !p.isCreative() && !p.isSpectator() && !p.isInvulnerable()
                && !(mc.player.isAlliedTo(p) || morfeov.coliseum.friend.Friends.contains(p)) && mc.player.distanceToSqr(p) <= targetRange.get() * targetRange.get();
    }
    private List<EndCrystal> crystals(double range) {
        return mc.level.getEntitiesOfClass(EndCrystal.class, mc.player.getBoundingBox().inflate(range), Entity::isAlive);
    }
    private Plan selectPlan() {
        Plan defensive = surroundPlan();
        if (defensive != null) return defensive;
        if (targets.isEmpty()) return null;
        List<Candidate> candidates = new ArrayList<>();
        for (EndCrystal crystal : crystals(Math.max(reach() + 2, 6))) {
            if (!attacked.containsKey(crystal.getId()) && distanceSquared(crystal.getBoundingBox(), mc.player.getEyePosition()) <= reach() * reach()) {
                candidates.add(new Candidate(Action.BREAK, crystal.blockPosition().below(), crystal.position(), 6, null, Set.of(), crystal, 0, 0));
            }
        }
        boolean placeReady = placeReady();
        Vec3 eye = mc.player.getEyePosition();
        int radius = (int) Math.ceil(profile.placeRange()) + 1;
        BlockPos center = mc.player.blockPosition();
        for (BlockPos mutable : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            BlockPos pos = mutable.immutable();
            if (distanceSquared(new AABB(pos), eye) > profile.placeRange() * profile.placeRange() || unavailable(pos)) continue;
            BlockState state = mc.level.getBlockState(pos);
            if (anchors.enabled() && anchorsExplode() && state.is(Blocks.RESPAWN_ANCHOR)) {
                int charge = state.getValue(RespawnAnchorBlock.CHARGE);
                if (charge > 0 || has(Items.GLOWSTONE)) candidates.add(new Candidate(charge > 0 ? Action.ANCHOR_EXPLODE : Action.ANCHOR_CHARGE,
                        pos, Vec3.atCenterOf(pos), 5, null, Set.of(pos), null, charge > 0 ? 3 : 1.5f, 0));
                continue;
            }
            if (!placeReady) continue;
            Vec3 blast = crystalPoint(pos);
            boolean breakable = distanceSquared(crystalBox(pos), eye) <= reach() * reach();
            if (base(state) && has(Items.END_CRYSTAL) && breakable) {
                BlockPos above = pos.above();
                if (mc.level.getBlockState(above).isAir() && clearCrystalSpace(pos, null)) {
                    candidates.add(new Candidate(Action.CRYSTAL, pos, blast, 6, null, Set.of(), null, pos.equals(lastBase) ? 1.5f : 0, 0));
                } else if (autoDig.enabled() && !mc.level.getBlockState(above).isAir() && clearCrystalSpace(pos, null)) {
                    addMine(candidates, above, blast, 0);
                } else if (fallback.enabled()) addBlockingCrystal(candidates, pos);
            } else if (state.canBeReplaced() && mc.level.getBlockState(pos.above()).isAir()) {
                if (autoObsidian.enabled() && has(Items.OBSIDIAN) && has(Items.END_CRYSTAL) && breakable && clearBlock(pos) && clearCrystalSpace(pos, null)) {
                    candidates.add(new Candidate(Action.OBSIDIAN, pos, blast, 6, pos, Set.of(), null, pos.equals(lastBase) ? -2.5f : -4, 0));
                }
                if (anchors.enabled() && anchorsExplode() && has(Items.RESPAWN_ANCHOR) && has(Items.GLOWSTONE) && clearBlock(pos)) {
                    candidates.add(new Candidate(Action.ANCHOR_PLACE, pos, Vec3.atCenterOf(pos), 5, null, Set.of(pos), null, -3, 0));
                }
            }
        }
        if (placeReady && (cityMine.enabled() || bomber.enabled())) addCityMines(candidates);
        // Zenith uses an inexpensive maximum-damage pass before its limited exact exposure pass.
        Map<Candidate, Float> upperScores = new HashMap<>();
        for (Candidate candidate : candidates) upperScores.put(candidate, upperScore(candidate));
        candidates.sort(Comparator.comparingDouble((Candidate c) -> -upperScores.get(c)));
        EnumMap<Action, Integer> counts = new EnumMap<>(Action.class);
        Plan bestBreak = null, bestPlace = null;
        for (Candidate candidate : candidates) {
            int quota = candidate.action == Action.DIG ? 18 : candidate.action == Action.BREAK ? 12 : 10;
            if (counts.merge(candidate.action, 1, Integer::sum) > quota) continue;
            Plan evaluated = evaluate(candidate);
            if (evaluated == null) continue;
            if (candidate.action == Action.BREAK) { if (bestBreak == null || evaluated.score > bestBreak.score) bestBreak = evaluated; }
            else if (bestPlace == null || evaluated.score > bestPlace.score) bestPlace = evaluated;
        }
        if (shield.enabled() || crystalDefense.enabled() || antiMine.enabled()) {
            defensive = defensePlan();
            if (defensive != null) return defensive;
        }
        if (bestBreak != null && breakReady()) return bestBreak;
        Plan melee = meleePlan(bestPlace == null);
        if (melee != null) return melee;
        if (bestPlace != null) return bestPlace;
        return webPlan();
    }

    private float upperScore(Candidate c) {
        float best = 0;
        for (Player p : targets) best = Math.max(best, damage.maximum(c.blast, c.power, p, predicted(p, c.action == Action.BREAK)));
        return best + c.bonus;
    }
    private AABB predicted(Player player, boolean immediate) {
        return (immediate ? immediateBoxes : futureBoxes).computeIfAbsent(player.getId(), id -> predictPlayer(player, immediate));
    }
    private AABB predictPlayer(Player player, boolean immediate) {
        int latency = Math.max(1, (pingTicks() + 1) / 2);
        int ticks = predictTicks.intValue() + (immediate ? latency : pingTicks() + latency + 1);
        KnockPrediction lift = knockup.enabled() ? knockPredictions.get(player.getId()) : null;
        if (lift != null) {
            int elapsed = now() - lift.starts;
            AABB expected = CrystalDamage.simulate(player, lift.box, lift.velocity, false, Math.max(0, elapsed));
            if (elapsed > 1 && (Math.abs(player.getY() - expected.minY) > .45 || player.getBoundingBox().getCenter().subtract(expected.getCenter()).horizontalDistanceSqr() > .64)) knockPredictions.remove(player.getId());
            else return CrystalDamage.simulate(player, lift.box, lift.velocity, false, Math.max(0, elapsed + ticks));
        }
        return CrystalDamage.predict(player, ticks);
    }
    private Plan evaluate(Candidate c) {
        if (c.action == Action.BREAK && !breakReady()) return null;
        boolean immediate = c.action == Action.BREAK || c.action == Action.ANCHOR_CHARGE || c.action == Action.ANCHOR_EXPLODE;
        float self = damage.damage(c.blast, c.power, mc.player, predicted(mc.player, immediate), c.added, c.removed);
        float ally = 0;
        for (Player friend : friends) {
            float value = damage.damage(c.blast, c.power, friend, friend.getBoundingBox(), c.added, c.removed);
            if (value > maxSelfDamage.floatValue() || value >= health(friend) - .5f) return null;
            ally = Math.max(ally, value);
        }
        Player chosen = null;
        float chosenDamage = 0, best = -Float.MAX_VALUE;
        boolean ready = false;
        for (Player p : targets) {
            if (blockedByShield(p, c.blast)) continue;
            float dealt = damage.damage(c.blast, c.power, p, predicted(p, immediate), c.added, c.removed);
            HitWindow window = hitWindows.get(p.getId());
            if (timing.enabled() && window != null && window.expires > now() + pingTicks() / 2) dealt = Math.max(0, dealt - window.damage);
            float minimum = CrystalProfile.minimumDamage(minDamage.floatValue(), health(p), faceplace.floatValue(), brokenArmor(p));
            boolean lethal = dealt >= health(p), hasTotem = totem(p);
            if (c.action == Action.BREAK && !lethal && waitForLift(p, c.blast, dealt)) continue;
            boolean enough = dealt >= minimum || lethal;
            if (!enough && (immediate || !prePlace.enabled() || (c.action != Action.CRYSTAL && c.action != Action.OBSIDIAN))) continue;
            if (!enough && damage.maximum(c.blast, c.power, p, predicted(p, false)) < minimum) continue;
            if (!CrystalProfile.safe(self, health(mc.player), maxSelfDamage.floatValue(), antiSuicide.enabled(), lethal && !hasTotem)) continue;
            if (c.action == Action.DIG && c.mineTicks > 2 && p.position().subtract(p.xo, p.yo, p.zo).horizontalDistanceSqr() > .0064) continue;
            if (c.mineTicks > 10 && dealt < mineDamage.floatValue()) continue;
            float value = CrystalProfile.score(dealt, self, ally, health(p), health(mc.player), lethal, hasTotem, now() - popped.getOrDefault(p.getId(), -100), antiTotem.enabled());
            value += c.bonus - (enough ? 0 : 6);
            if (!(value > 0) && !lethal) continue;
            if (value > best) { best = value; chosen = p; chosenDamage = dealt; ready = enough; }
        }
        if (chosen == null) return null;
        BlockHitResult hit = c.action == Action.BREAK ? null : hit(c.pos, c.action == Action.CRYSTAL || c.action == Action.DIG || c.action == Action.ANCHOR_CHARGE || c.action == Action.ANCHOR_EXPLODE);
        if (c.action != Action.BREAK && hit == null) return null;
        Vec3 point = c.action == Action.BREAK ? crystalAim(c.crystal) : hit.getLocation();
        return point == null ? null : new Plan(c, chosen, chosenDamage, self, best, ready, hit, point);
    }
    private void addMine(List<Candidate> list, BlockPos pos, Vec3 blast, float bonus) {
        if (unavailable(pos)) return;
        int ticks = miningTicks(pos);
        if (ticks <= 0 || ticks > mineTime.get() * 20) return;
        float progress = pos.equals(mining) ? ((CombatGameModeAccessor) mc.gameMode).coliseum$destroyProgress() : 0;
        list.add(new Candidate(Action.DIG, pos, blast, 6, null, Set.of(pos), null,
                bonus - 3 - ticks * .08f + (pos.equals(mining) ? 4 + 8 * progress : 0), ticks));
    }
    private boolean waitForLift(Player p, Vec3 blast, float current) {
        KnockPrediction lift = knockup.enabled() ? knockPredictions.get(p.getId()) : null;
        if (lift == null) return false;
        int elapsed = now() - lift.starts;
        if (elapsed > 0 && p.getY() <= lift.box.minY + .1) return false;
        for (int next = 1; next <= 12; next++) {
            AABB future = CrystalDamage.simulate(p, lift.box, lift.velocity, false, Math.max(0, elapsed + next));
            float potential = damage.damage(blast, 6, p, future, null, Set.of()) * (1 - .02f * next);
            if (potential > current * 1.15f + 1.5f) return true;
        }
        return false;
    }
    private Vec3 meleeKnockback(Player p) {
        double resistance = Mth.clamp(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0, 1);
        Vec3 previous = p.position().subtract(p.xo, p.yo, p.zo);
        Vec3 direction = p.position().subtract(mc.player.position()).multiply(1, 0, 1).normalize();
        double force = .4 * (1 - resistance);
        Vec3 result = new Vec3(previous.x / 2 + direction.x * force, Math.min(.4, -.0784 / 2 + force), previous.z / 2 + direction.z * force);
        int enchantment = 0;
        for (var entry : mc.player.getMainHandItem().getEnchantments().entrySet()) if (entry.getKey().is(net.minecraft.world.item.enchantment.Enchantments.KNOCKBACK)) enchantment = entry.getIntValue();
        double extra = (enchantment * .5 + (mc.player.isSprinting() ? .5 : 0)) * (1 - resistance);
        if (extra > 0) result = new Vec3(result.x / 2 + direction.x * extra, Math.min(.4, result.y / 2 + extra), result.z / 2 + direction.z * extra);
        return result;
    }
    private Plan meleePlan(boolean allowFallback) {
        if (!knockup.enabled() || mc.player.getAttackStrengthScale(.5f) < .9f) return null;
        var aura = Coliseum.MODULES.get(morfeov.coliseum.module.impl.combat.aura.AttackAura.class);
        if (aura != null && aura.enabled()) return null;
        // As in Zenith, the combo waits for an equipped weapon and a full attack cooldown.
        if (!mc.player.getMainHandItem().is(ItemTags.SWORDS) && !mc.player.getMainHandItem().is(ItemTags.AXES)) return null;
        for (Player p : targets) {
            if (!p.onGround() || p.hurtTime > 0 || knockPredictions.containsKey(p.getId()) || blockedByShield(p, mc.player.position())) continue;
            AABB box = p.getBoundingBox();
            if (distanceSquared(box, mc.player.getEyePosition()) > reach() * reach()) continue;
            Vec3 point = new Vec3(Mth.clamp(mc.player.getEyePosition().x, box.minX + .05, box.maxX - .05),
                    Mth.clamp(mc.player.getEyePosition().y, box.minY + .2, box.maxY - .2), Mth.clamp(mc.player.getEyePosition().z, box.minZ + .05, box.maxZ - .05));
            if (!profile.throughWalls() && mc.level.clip(new ClipContext(mc.player.getEyePosition(), point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getType() != HitResult.Type.MISS) continue;
            Vec3 velocity = meleeKnockback(p);
            float gain = 0;
            if (velocity.y >= .15) for (EndCrystal crystal : crystals(reach() + 1)) {
                if (attacked.containsKey(crystal.getId()) || distanceSquared(crystal.getBoundingBox(), mc.player.getEyePosition()) > reach() * reach()) continue;
                float current = damage.damage(crystal.position(), 6, p, box, null, Set.of());
                for (int next = 2; next <= 12; next++) {
                    AABB future = CrystalDamage.simulate(p, box, velocity, false, next);
                    float potential = damage.damage(crystal.position(), 6, p, future, null, Set.of());
                    if (potential >= minDamage.floatValue()) gain = Math.max(gain, potential - current);
                }
            }
            // Also retain the donor's sword fallback when no useful explosion is available.
            if (gain >= 4 || allowFallback) return new Plan(new Candidate(Action.MELEE, p.blockPosition(), p.position(), 0, null, Set.of(), null, 0, 0), p, 0, 0, gain >= 4 ? gain : 1, true, null, point);
        }
        return null;
    }
    private void addCityMines(List<Candidate> list) {
        for (Player p : targets) {
            BlockPos feet = BlockPos.containing(p.getX(), p.getY() + .001, p.getZ());
            if (cityMine.enabled()) for (Direction direction : HORIZONTAL) {
                BlockPos wall = feet.relative(direction);
                if (base(mc.level.getBlockState(wall.below()))) addMine(list, wall, crystalPoint(wall.below()), 0);
                for (Direction outside : HORIZONTAL) {
                    BlockPos crystalBase = wall.relative(outside).below();
                    if (base(mc.level.getBlockState(crystalBase)) && mc.level.getBlockState(crystalBase.above()).isAir()) addMine(list, wall, crystalPoint(crystalBase), -1);
                }
            }
            if (bomber.enabled()) {
                BlockPos overhead = feet.above(2);
                if (!mc.level.getBlockState(overhead).canBeReplaced()) addMine(list, overhead, crystalPoint(overhead), 0);
                else if (autoObsidian.enabled() && has(Items.OBSIDIAN) && has(Items.END_CRYSTAL) && clearBlock(overhead))
                    list.add(new Candidate(Action.OBSIDIAN, overhead, crystalPoint(overhead), 6, overhead, Set.of(overhead), null, -6, 0));
            }
        }
        for (EndCrystal crystal : crystals(6)) {
            if (attacked.containsKey(crystal.getId())) continue;
            BlockPos base = crystal.blockPosition().below();
            if (targets.stream().anyMatch(p -> p.distanceToSqr(crystal) <= 9)) addMine(list, base, crystal.position(), 1);
        }
    }
    private void addBlockingCrystal(List<Candidate> list, BlockPos pos) {
        for (EndCrystal crystal : mc.level.getEntitiesOfClass(EndCrystal.class, placementBox(pos), Entity::isAlive)) {
            if (!attacked.containsKey(crystal.getId()) && distanceSquared(crystal.getBoundingBox(), mc.player.getEyePosition()) <= reach() * reach())
                list.add(new Candidate(Action.BREAK, pos, crystal.position(), 6, null, Set.of(), crystal, -4, 0));
        }
    }

    private Plan simplePlace(Action action, BlockPos pos, Item item, Player p, float score) {
        if (!placeReady() || unavailable(pos) || !mc.level.getBlockState(pos).canBeReplaced() || !has(item)) return null;
        if (action != Action.WEB && !clearBlock(pos)) return null;
        BlockHitResult hit = hit(pos, false);
        if (hit == null) return null;
        return new Plan(new Candidate(action, pos, Vec3.atCenterOf(pos), 0, null, Set.of(), null, 0, 0), p, 0, 0, score, true, hit, hit.getLocation());
    }
    private Plan surroundPlan() {
        if (surroundOrigin == null || now() > surroundUntil || !has(Items.OBSIDIAN)) return null;
        List<BlockPos> positions = new ArrayList<>();
        positions.add(surroundOrigin.below());
        for (Direction d : HORIZONTAL) {
            positions.add(surroundOrigin.relative(d));
            if (!surroundMode.is("Обычный")) positions.add(surroundOrigin.relative(d).above());
            if (surroundMode.is("City")) positions.add(surroundOrigin.relative(d, 2));
        }
        if (surroundMode.is("Бункер")) positions.add(surroundOrigin.above(2));
        for (BlockPos pos : positions) { Plan value = simplePlace(Action.SHIELD, pos, Items.OBSIDIAN, null, 100); if (value != null) return value; }
        return null;
    }
    private Plan webPlan() {
        if (!webTrap.enabled() || now() - lastWeb < 20 || !has(Items.COBWEB)) return null;
        if (yieldWebTrap.enabled()) {
            var external = Coliseum.MODULES.get(AutoWebTrap.class);
            if (external != null && external.enabled() && external.target() != null) return null;
        }
        for (Player p : targets) if (p.onGround()) {
            Plan value = simplePlace(Action.WEB, BlockPos.containing(p.getX(), p.getY() + .1, p.getZ()), Items.COBWEB, p, 1);
            if (value != null) return value;
        }
        return null;
    }
    private Plan defensePlan() {
        if (!placeReady() || !has(Items.OBSIDIAN)) return null;
        if (antiMine.enabled()) for (BlockPos wall : enemyMining.keySet()) {
            if (Vec3.atCenterOf(wall).distanceToSqr(mc.player.position()) > 16) continue;
            for (Direction d : HORIZONTAL) {
                Plan cover = simplePlace(Action.SHIELD, wall.relative(d), Items.OBSIDIAN, null, 50);
                if (cover != null && cover.candidate.pos.distManhattan(mc.player.blockPosition()) <= 2) return cover;
            }
        }
        for (EndCrystal crystal : crystals(8)) {
            if (attacked.containsKey(crystal.getId())) continue;
            float incoming = damage.damage(crystal.position(), 6, mc.player, predicted(mc.player, true), null, Set.of());
            if (incoming < shieldDamage.floatValue() && incoming < health(mc.player) - 2) continue;
            boolean ourThreat = targets.stream().anyMatch(p -> damage.damage(crystal.position(), 6, p, predicted(p, true), null, Set.of()) >= minDamage.floatValue());
            if (!shield.enabled() && (!crystalDefense.enabled() || ourThreat)) continue;
            BlockPos feet = mc.player.blockPosition();
            Plan best = null;
            for (Direction d : HORIZONTAL) for (int dy = 0; dy <= 1; dy++) {
                BlockPos pos = feet.relative(d).above(dy);
                Plan cover = simplePlace(Action.SHIELD, pos, Items.OBSIDIAN, null, incoming);
                if (cover == null) continue;
                float after = damage.damage(crystal.position(), 6, mc.player, predicted(mc.player, true), pos, Set.of());
                if (incoming - after >= 1 && (best == null || incoming - after > best.score))
                    best = new Plan(cover.candidate, null, 0, incoming, incoming - after, true, cover.hit, cover.aim);
            }
            if (best != null) return best;
        }
        return null;
    }

    private boolean execute(Plan p) {
        Candidate c = p.candidate;
        if (c.action == Action.MELEE) {
            if (!p.target.isAlive() || mc.player.getAttackStrengthScale(.5f) < .9f || !InvActionUtil.get().tryAcquire(OWNER, InvActionUtil.PRIORITY_COMBAT, 150)) return false;
            try {
                Vec3 velocity = meleeKnockback(p.target);
                mc.gameMode.attack(mc.player, p.target); swing(InteractionHand.MAIN_HAND); usedActions++;
                if (velocity.y >= .15) knockPredictions.put(p.target.getId(), new KnockPrediction(p.target.getBoundingBox(), velocity, now() + Math.max(1, pingTicks() / 2)));
                immediateBoxes.clear(); futureBoxes.clear(); log(p); return true;
            } finally { InvActionUtil.get().release(OWNER); }
        }
        if (c.action == Action.BREAK) {
            if (!p.ready || !breakReady() || !c.crystal.isAlive() || attacked.containsKey(c.crystal.getId())) return false;
            // Recheck self damage immediately before committing the explosion.
            if (!safeToExplode(p)) return false;
            if (!InvActionUtil.get().tryAcquire(OWNER, InvActionUtil.PRIORITY_COMBAT, 150)) return false;
            int previous = mc.player.getInventory().getSelectedSlot();
            try {
                if (antiWeakness.enabled() && mc.player.getAttributeValue(Attributes.ATTACK_DAMAGE) <= 0) {
                    int weapon = find(stack -> stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES));
                    if (weapon < 0 || weapon >= 9) return false;
                    select(weapon);
                }
                mc.gameMode.attack(mc.player, c.crystal); swing(InteractionHand.MAIN_HAND);
                usedActions++; lastBreak = now(); attacked.put(c.crystal.getId(), now() + confirmTicks());
                attackDistances.put(c.crystal.getId(), Math.sqrt(distanceSquared(c.crystal.getBoundingBox(), mc.player.getEyePosition())));
                if (explosionSync.enabled()) markExplosion(c.blast, 6, null);
                predictKnockback(c.blast, 6);
                if (timing.enabled()) for (Player player : targets) {
                    float value = damage.damage(c.blast, 6, player, player.getBoundingBox(), null, Set.of());
                    if (value > 0) hitWindows.put(player.getId(), new HitWindow(value, now() + 10));
                }
                visual(c.pos, 0xffef655b); log(p);
                return true;
            } finally {
                if (mc.player.getInventory().getSelectedSlot() != previous) select(previous);
                InvActionUtil.get().release(OWNER);
            }
        }
        if (unavailable(c.pos) || c.action != Action.DIG && !placeReady()) return false;
        if (c.action == Action.DIG) return mine(p);
        if (c.action == Action.CRYSTAL && (!base(mc.level.getBlockState(c.pos)) || !mc.level.getBlockState(c.pos.above()).isAir() || !clearCrystalSpace(c.pos, null))) return false;
        if (c.action == Action.ANCHOR_EXPLODE && (!anchorsExplode() || mc.player.isShiftKeyDown() || mc.player.getOffhandItem().is(Items.GLOWSTONE))) return false;
        if (c.action == Action.ANCHOR_EXPLODE && !safeToExplode(p)) return false;
        Item item = switch (c.action) {
            case CRYSTAL -> Items.END_CRYSTAL;
            case OBSIDIAN, SHIELD -> Items.OBSIDIAN;
            case WEB -> Items.COBWEB;
            case ANCHOR_PLACE -> Items.RESPAWN_ANCHOR;
            case ANCHOR_CHARGE -> Items.GLOWSTONE;
            default -> null;
        };
        if (!useItem(item, c.action == Action.ANCHOR_EXPLODE, p.hit)) { blacklist.put(c.pos, now() + 3); return false; }
        usedActions++; lastPlace = now();
        pending.put(c.pos, new Pending(c.action, item, now() + confirmTicks()));
        if (c.action == Action.CRYSTAL || c.action == Action.OBSIDIAN) lastBase = c.pos;
        if (c.action == Action.WEB) lastWeb = now();
        if (c.action == Action.ANCHOR_EXPLODE && explosionSync.enabled()) markExplosion(c.blast, 5, c.pos);
        if (c.action == Action.ANCHOR_EXPLODE) predictKnockback(c.blast, 5);
        visual(c.pos, c.action == Action.DIG ? 0xffefbf55 : 0xff68bfff); log(p);
        return true;
    }

    private boolean safeToExplode(Plan p) {
        Candidate c = p.candidate;
        float self = damage.damage(c.blast, c.power, mc.player, mc.player.getBoundingBox(), c.added, c.removed);
        if (!CrystalProfile.safe(self, health(mc.player), maxSelfDamage.floatValue(), antiSuicide.enabled(), p.target != null && p.enemy >= health(p.target) && !totem(p.target))) return false;
        for (Player friend : friends) {
            float value = damage.damage(c.blast, c.power, friend, friend.getBoundingBox(), c.added, c.removed);
            if (value > maxSelfDamage.floatValue() || value >= health(friend) - .5f) return false;
        }
        return true;
    }

    private boolean useItem(Item item, boolean detonate, BlockHitResult hit) {
        if (!InventoryActions.ready(mc) || !InvActionUtil.get().tryAcquire(OWNER, InvActionUtil.PRIORITY_COMBAT, 150)) return false;
        int previous = mc.player.getInventory().getSelectedSlot(), slot = -1;
        boolean inventorySwap = false;
        try {
            InteractionHand hand;
            Predicate<ItemStack> matches = detonate ? stack -> !stack.is(Items.GLOWSTONE) : stack -> stack.is(item);
            if (!detonate && matches.test(mc.player.getOffhandItem())) hand = InteractionHand.OFF_HAND;
            else {
                if (matches.test(mc.player.getMainHandItem())) slot = previous;
                else if (profile.swap() != Swap.OFF) slot = find(matches);
                if (slot < 0) return false;
                if (slot >= 9) {
                    if (!fromInventory.enabled()) return false;
                    mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, InventoryActions.menuSlot(slot), previous, ContainerInput.SWAP, mc.player);
                    inventorySwap = true;
                } else if (slot != previous) {
                    if (profile.swap() == Swap.KEEP && originalSlot < 0) originalSlot = previous;
                    select(slot); selectedSlot = slot;
                }
                hand = InteractionHand.MAIN_HAND;
            }
            var result = mc.gameMode.useItemOn(mc.player, hand, hit);
            if (result.consumesAction()) swing(hand);
            return result.consumesAction();
        } finally {
            if (inventorySwap) mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, InventoryActions.menuSlot(slot), previous, ContainerInput.SWAP, mc.player);
            if (profile.swap() == Swap.SILENT && mc.player.getInventory().getSelectedSlot() != previous) select(previous);
            InvActionUtil.get().release(OWNER);
        }
    }
    private int find(Predicate<ItemStack> test) {
        int limit = fromInventory.enabled() ? 36 : 9;
        for (int i = 0; i < limit; i++) if (test.test(mc.player.getInventory().getItem(i))) return i;
        return -1;
    }
    private boolean has(Item item) {
        if (mc.player.getMainHandItem().is(item) || mc.player.getOffhandItem().is(item)) return true;
        return profile.swap() != Swap.OFF && find(stack -> stack.is(item)) >= 0;
    }
    private void select(int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        ((CombatGameModeAccessor) mc.gameMode).coliseum$syncSelectedSlot();
    }
    private int tool(BlockState state) {
        int best = mc.player.getInventory().getSelectedSlot(); float speed = 1;
        for (int i = 0; i < 9; i++) {
            float candidate = toolSpeed(mc.player.getInventory().getItem(i), state);
            if (candidate > speed) { speed = candidate; best = i; }
        }
        return best;
    }
    private float toolSpeed(ItemStack stack, BlockState state) {
        float speed = stack.getDestroySpeed(state);
        if (speed > 1) for (var entry : stack.getEnchantments().entrySet()) {
            if (entry.getKey().is(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY)) speed += entry.getIntValue() * entry.getIntValue() + 1;
        }
        var haste = mc.player.getEffect(net.minecraft.world.effect.MobEffects.HASTE);
        if (haste != null) speed *= 1 + (haste.getAmplifier() + 1) * .2f;
        var fatigue = mc.player.getEffect(net.minecraft.world.effect.MobEffects.MINING_FATIGUE);
        if (fatigue != null) speed *= switch (fatigue.getAmplifier()) { case 0 -> .3f; case 1 -> .09f; case 2 -> .0027f; default -> .00081f; };
        speed *= (float) mc.player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
        if (mc.player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) speed *= (float) mc.player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
        return mc.player.onGround() ? speed : speed / 5;
    }
    private int miningTicks(BlockPos pos) {
        var state = mc.level.getBlockState(pos);
        float hardness = state.getDestroySpeed(mc.level, pos);
        if (hardness < 0 || state.canBeReplaced() || state.is(Blocks.RESPAWN_ANCHOR) || !mc.level.getFluidState(pos).isEmpty()) return -1;
        if (hardness == 0) return 1;
        var stack = mc.player.getInventory().getItem(tool(state));
        float speed = toolSpeed(stack, state);
        float divisor = !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state) ? 30 : 100;
        return (int) Math.ceil(hardness * divisor / Math.max(.0001f, speed));
    }
    private boolean mine(Plan p) {
        BlockPos pos = p.candidate.pos;
        if (distanceSquared(new AABB(pos), mc.player.getEyePosition()) > profile.placeRange() * profile.placeRange()) return false;
        if (!InvActionUtil.get().tryAcquire(OWNER, InvActionUtil.PRIORITY_COMBAT, 150)) return false;
        try {
            if (!pos.equals(mining)) { stopMining(); mining = pos; miningStarted = now(); }
            if (now() - miningStarted > mineTime.get() * 20) { blacklist.put(pos, now() + 20); stopMining(); return false; }
            int slot = tool(mc.level.getBlockState(pos));
            if (slot != mc.player.getInventory().getSelectedSlot()) {
                if (originalSlot < 0) originalSlot = mc.player.getInventory().getSelectedSlot();
                select(slot); selectedSlot = slot;
            }
            mc.gameMode.continueDestroyBlock(pos, p.hit.getDirection()); swing(InteractionHand.MAIN_HAND);
            usedActions++; visual(pos, 0xffefbf55);
            if (mc.level.getBlockState(pos).canBeReplaced()) mining = null;
            return true;
        } finally { InvActionUtil.get().release(OWNER); }
    }
    private void stopMining() { if (mining != null && mc.gameMode != null) mc.gameMode.stopDestroyBlock(); mining = null; }
    private void restoreSlot() {
        if (originalSlot >= 0 && mc.player != null && mc.gameMode != null && mc.player == session && mc.player.getInventory().getSelectedSlot() == selectedSlot) select(originalSlot);
        originalSlot = selectedSlot = -1;
    }

    private BlockHitResult hit(BlockPos pos, boolean direct) {
        BlockHitResult best = null; double score = Double.MAX_VALUE;
        for (Direction face : Direction.values()) {
            BlockPos support = direct ? pos : pos.relative(face.getOpposite());
            var state = mc.level.getBlockState(support);
            if (state.canBeReplaced() || state.getCollisionShape(mc.level, support).isEmpty()) continue;
            if (!direct && !mc.player.isShiftKeyDown() && state.hasBlockEntity()) continue;
            for (double a : new double[]{.5, .3, .7}) for (double b : new double[]{.5, .3, .7}) {
                double offset = profile.varyRotation() ? Math.sin(now() * .7) * .025 : 0;
                Vec3 point = facePoint(support, face, a + offset, b - offset);
                double distance = mc.player.getEyePosition().distanceToSqr(point);
                if (distance > profile.placeRange() * profile.placeRange()) continue;
                BlockHitResult visible = mc.level.clip(new ClipContext(mc.player.getEyePosition(), point.add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(-.001)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
                if (!profile.throughWalls() && (visible.getType() != HitResult.Type.BLOCK || !visible.getBlockPos().equals(support) || visible.getDirection() != face)) continue;
                double value = distance + (face == Direction.UP ? 0 : .25) + Math.abs(a - .5) + Math.abs(b - .5);
                if (value < score) { score = value; best = new BlockHitResult(point, face, support, false); }
            }
        }
        return best;
    }
    private Vec3 crystalAim(EndCrystal crystal) {
        Vec3 eye = mc.player.getEyePosition(); AABB box = crystal.getBoundingBox();
        Vec3 best = null; double score = Double.MAX_VALUE;
        for (double x : new double[]{.5, .2, .8}) for (double y : new double[]{.5, .2, .8}) for (double z : new double[]{.5, .2, .8}) {
            Vec3 point = new Vec3(Mth.lerp(x, box.minX, box.maxX), Mth.lerp(y, box.minY, box.maxY), Mth.lerp(z, box.minZ, box.maxZ));
            var entry = box.clip(eye, eye.add(point.subtract(eye).normalize().scale(reach())));
            if (!box.contains(eye) && entry.isEmpty()) continue;
            Vec3 endpoint = entry.orElse(eye);
            if (!profile.throughWalls() && mc.level.clip(new ClipContext(eye, endpoint, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getType() != HitResult.Type.MISS) continue;
            double value = eye.distanceToSqr(point);
            if (value < score) { score = value; best = point; }
        }
        return best;
    }
    private void aim(Vec3 point) {
        Vec3 delta = point.subtract(mc.player.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
        ownsRotation = Rotations.aim(this, new Rotation(yaw, pitch), profile.rotationSpeed(), 0, 80);
    }
    private boolean aligned(Plan p) {
        Vec3 start = mc.player.getEyePosition();
        Vec3 end = start.add(mc.player.getViewVector(1).scale(p.candidate.action == Action.BREAK || p.candidate.action == Action.MELEE ? reach() : profile.placeRange()));
        if (p.candidate.action == Action.MELEE) {
            var entry = p.target.getBoundingBox().clip(start, end);
            return entry.isPresent() && (profile.throughWalls() || mc.level.clip(new ClipContext(start, entry.get(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS);
        }
        if (p.candidate.action == Action.BREAK) {
            Optional<Vec3> entry = p.candidate.crystal.getBoundingBox().clip(start, end);
            return (p.candidate.crystal.getBoundingBox().contains(start) || entry.isPresent()) && (profile.throughWalls()
                    || mc.level.clip(new ClipContext(start, entry.orElse(start), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS);
        }
        if (profile.throughWalls()) return new AABB(p.hit.getBlockPos()).inflate(.01).clip(start, end).isPresent();
        var ray = mc.level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        return ray.getType() == HitResult.Type.BLOCK && ray.getBlockPos().equals(p.hit.getBlockPos()) && ray.getDirection() == p.hit.getDirection();
    }
    private void releaseRotation() { SilentComponent.releaseOwnedRotation(this); ownsRotation = false; }
    private boolean placeReady() { return CrystalProfile.delayReady(now(), lastPlace, Math.max(profile.placeDelay(), slowPlace.enabled() ? 100 : 0)); }
    private boolean breakReady() { return CrystalProfile.delayReady(now(), lastBreak, profile.breakDelay()); }
    private boolean unavailable(BlockPos pos) { return pending.containsKey(pos) || blacklist.containsKey(pos); }
    private boolean anchorsExplode() { return !mc.level.environmentAttributes().getDimensionValue(net.minecraft.world.attribute.EnvironmentAttributes.RESPAWN_ANCHOR_WORKS); }
    private static boolean base(BlockState state) { return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK); }
    private static float health(Player player) { return player.getHealth() + player.getAbsorptionAmount(); }
    private static boolean totem(Player p) { return p.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || p.getOffhandItem().is(Items.TOTEM_OF_UNDYING); }
    private boolean brokenArmor(Player p) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = p.getItemBySlot(slot);
            if (stack.isDamageableItem() && (stack.getMaxDamage() - stack.getDamageValue()) * 100f / stack.getMaxDamage() <= armorBreak.get()) return true;
        }
        return false;
    }
    private static boolean blockedByShield(Player p, Vec3 blast) {
        if (!p.isBlocking()) return false;
        Vec3 horizontal = blast.subtract(p.position()).multiply(1, 0, 1);
        return horizontal.lengthSqr() >= .000001 && horizontal.normalize().dot(p.getViewVector(1).multiply(1, 0, 1).normalize()) >= 0;
    }
    private boolean clearBlock(BlockPos pos) {
        return mc.level.isInWorldBounds(pos) && mc.level.getWorldBorder().isWithinBounds(pos) && mc.level.getFluidState(pos).isEmpty()
                && mc.level.getEntities((Entity) null, new AABB(pos).deflate(.001), Entity::isAlive).isEmpty();
    }
    private boolean clearCrystalSpace(BlockPos pos, Entity ignore) {
        return mc.level.getEntities((Entity) null, placementBox(pos), e -> e.isAlive() && e != ignore && !(e instanceof EndCrystal crystal && attacked.containsKey(crystal.getId()))).isEmpty();
    }
    private static AABB placementBox(BlockPos pos) { return new AABB(pos.getX(), pos.getY() + 1, pos.getZ(), pos.getX() + 1, pos.getY() + 3, pos.getZ() + 1); }
    private static AABB crystalBox(BlockPos pos) { return AABB.ofSize(crystalPoint(pos).add(0, 1, 0), 2, 2, 2); }
    private static Vec3 crystalPoint(BlockPos pos) { return new Vec3(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5); }
    private static double distanceSquared(AABB box, Vec3 p) {
        double x = Mth.clamp(p.x, box.minX, box.maxX) - p.x, y = Mth.clamp(p.y, box.minY, box.maxY) - p.y, z = Mth.clamp(p.z, box.minZ, box.maxZ) - p.z;
        return x * x + y * y + z * z;
    }
    private static Vec3 facePoint(BlockPos p, Direction face, double a, double b) {
        return switch (face) {
            case UP -> new Vec3(p.getX() + a, p.getY() + 1, p.getZ() + b);
            case DOWN -> new Vec3(p.getX() + a, p.getY(), p.getZ() + b);
            case NORTH -> new Vec3(p.getX() + a, p.getY() + b, p.getZ());
            case SOUTH -> new Vec3(p.getX() + a, p.getY() + b, p.getZ() + 1);
            case WEST -> new Vec3(p.getX(), p.getY() + a, p.getZ() + b);
            case EAST -> new Vec3(p.getX() + 1, p.getY() + a, p.getZ() + b);
        };
    }
    private void swing(InteractionHand hand) { mc.player.swing(hand, SwingAnimation.DEFAULT, false); }
    private void visual(BlockPos pos, int color) { if (render.enabled()) { visuals.removeIf(v -> v.pos.equals(pos)); visuals.add(new Visual(pos, now(), color)); } }
    private void log(Plan p) { if (combatLog.enabled()) org.slf4j.LoggerFactory.getLogger(OWNER).info("{} {} target={} damage={} self={}", p.candidate.action, p.candidate.pos, p.target == null ? "-" : p.target.getName().getString(), p.enemy, p.self); }

    /** Invoked on ClientLevel.addEntity RETURN, after the crystal actually exists in the world. */
    public void entityAdded(Entity entity) {
        if (!enabled() || !(entity instanceof EndCrystal crystal) || mc.player == null || mc.level == null) return;
        confirmCrystal(crystal);
        if (profile == null || !profile.instantBreak() || executing || paused() || usedActions >= profile.actions() || !breakReady() || targets.isEmpty()) return;
        damage.reset();
        Plan immediate = evaluate(new Candidate(Action.BREAK, crystal.blockPosition().below(), crystal.position(), 6, null, Set.of(), crystal, 0, 0));
        if (immediate != null && aligned(immediate)) {
            executing = true;
            try {
                if (execute(immediate) && profile.instantPlace() && usedActions < profile.actions() && placeReady()) {
                    BlockPos pos = crystal.blockPosition().below();
                    Plan replace = evaluate(new Candidate(Action.CRYSTAL, pos, crystalPoint(pos), 6, null, Set.of(), null, 0, 0));
                    if (replace != null && aligned(replace)) execute(replace);
                }
            } finally { executing = false; }
        }
    }
    private void confirmCrystal(EndCrystal crystal) {
        if (!knownCrystals.add(crystal.getId())) return;
        BlockPos pos = crystal.blockPosition().below();
        Pending waiting = pending.get(pos);
        if (waiting != null && waiting.action == Action.CRYSTAL) { pending.remove(pos); visual(pos, 0xff65dc91); }
    }
    private void markExplosion(Vec3 center, float power, BlockPos removed) {
        for (EndCrystal crystal : crystals(power * 2 + 1)) if (crystal.position().distanceTo(center) <= power * 2 - .5) attacked.put(crystal.getId(), now() + confirmTicks());
    }
    private void predictKnockback(Vec3 blast, float power) {
        if (!knockup.enabled()) return;
        for (Player p : targets) {
            float maximum = damage.maximum(blast, power, p, p.getBoundingBox());
            float actual = damage.damage(blast, power, p, p.getBoundingBox(), null, Set.of());
            if (actual <= 0 || maximum <= 0) continue;
            Vec3 delta = p.getEyePosition().subtract(blast);
            double impact = Math.max(0, 1 - p.position().distanceTo(blast) / (power * 2)) * Math.clamp(actual / maximum, 0, 1);
            Vec3 velocity = p.position().subtract(p.xo, p.yo, p.zo).add(delta.normalize().scale(impact));
            if (velocity.y > .1) knockPredictions.put(p.getId(), new KnockPrediction(p.getBoundingBox(), velocity, now() + Math.max(1, pingTicks() / 2)));
        }
        immediateBoxes.clear(); futureBoxes.clear();
    }
    @Override public void onPacketReceive(EventPacketReceive event) {
        if (mc.level == null || mc.player == null) return;
        var packet = event.getPacket();
        if (packet instanceof ClientboundBlockUpdatePacket update) confirmBlock(update.getPos(), update.getBlockState());
        else if (packet instanceof ClientboundSectionBlocksUpdatePacket updates) updates.runUpdates(this::confirmBlock);
        else if (packet instanceof ClientboundEntityEventPacket status && status.getEventId() == 35) {
            Entity entity = status.getEntity(mc.level);
            if (entity != null) { popped.put(entity.getId(), now()); hitWindows.remove(entity.getId()); }
        } else if (packet instanceof ClientboundExplodePacket explosion && explosionSync.enabled()) markExplosion(explosion.center(), explosion.radius(), null);
        else if (packet instanceof ClientboundBlockDestructionPacket progress && progress.getId() != mc.player.getId()) {
            if (progress.getProgress() >= 0 && progress.getProgress() <= 9 && progress.getPos().distManhattan(mc.player.blockPosition()) <= 6) enemyMining.put(progress.getPos().immutable(), now() + 40);
            else enemyMining.remove(progress.getPos());
        }
    }
    private void confirmBlock(BlockPos pos, BlockState state) {
        Pending waiting = pending.get(pos);
        if (waiting != null && waiting.action != Action.CRYSTAL && (waiting.action == Action.ANCHOR_EXPLODE ? !state.is(Blocks.RESPAWN_ANCHOR)
                : waiting.action == Action.ANCHOR_CHARGE ? state.is(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) > 0
                : state.getBlock().asItem() == waiting.item)) { pending.remove(pos); visual(pos.immutable(), 0xff65dc91); }
        if (state.canBeReplaced() && pos.equals(mining)) stopMining();
    }
    private void cleanup() {
        pending.entrySet().removeIf(entry -> {
            if (entry.getValue().expires >= now()) return false;
            blacklist.put(entry.getKey(), now() + 4 + pingTicks()); return true;
        });
        attacked.entrySet().removeIf(entry -> {
            if (entry.getValue() >= now()) return false;
            Double distance = attackDistances.remove(entry.getKey());
            Entity entity = mc.level.getEntity(entry.getKey());
            if (distance != null && profile.strictReach()) adaptiveReach = entity != null && entity.isAlive()
                    ? Math.max(2.3f, Math.min(adaptiveReach, distance.floatValue() - .05f)) : Math.min(3, adaptiveReach + .02f);
            return true;
        });
        knownCrystals.removeIf(id -> mc.level.getEntity(id) == null);
        blacklist.values().removeIf(expiry -> expiry < now()); enemyMining.values().removeIf(expiry -> expiry < now());
        popped.entrySet().removeIf(entry -> now() - entry.getValue() > 60); hitWindows.values().removeIf(window -> window.expires < now());
        knockPredictions.entrySet().removeIf(entry -> now() - entry.getValue().starts > 20 || mc.level.getEntity(entry.getKey()) == null);
        visuals.removeIf(visual -> now() - visual.tick > 10);
    }
    @Override protected void onEnable() { reset(); }
    @Override protected void onDisable() { reset(); }
    @Override public void onDisconnect(EventDisconnect event) { reset(); }
    private void reset() {
        stopMining(); restoreSlot(); releaseRotation(); InvActionUtil.get().release(OWNER);
        pending.clear(); blacklist.clear(); attacked.clear(); attackDistances.clear(); popped.clear(); hitWindows.clear();
        enemyMining.clear(); knownCrystals.clear(); knockPredictions.clear(); immediateBoxes.clear(); futureBoxes.clear(); visuals.clear(); damage.reset();
        targets = friends = List.of(); plan = null; target = null; world = null; session = null; profile = null;
        tick = lastPlace = lastBreak = Integer.MIN_VALUE; usedActions = 0; lastActive = lastWeb = -100;
        adaptiveReach = 3; surroundOrigin = lastBase = null; surroundUntil = 0; executing = false;
    }
}
