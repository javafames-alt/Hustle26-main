package morfeov.coliseum.module.impl.combat.aura;

import morfeov.coliseum.module.Category;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.module.impl.combat.aura.sprint.SprintResetHelper;
import morfeov.coliseum.module.impl.combat.aura.sprint.SpookySprintReset;
import morfeov.coliseum.module.impl.combat.rotation.AttackContext;
import morfeov.coliseum.module.impl.combat.rotation.AuraAimPoint;
import morfeov.coliseum.module.impl.combat.rotation.CombatPredicates;
import morfeov.coliseum.module.impl.combat.rotation.CombatTagManager;
import morfeov.coliseum.module.impl.combat.rotation.CooldownTimer;
import morfeov.coliseum.module.impl.combat.rotation.RotationComponent;
import morfeov.coliseum.module.impl.combat.aura.rotation.SpookyTimeRotation;
import morfeov.coliseum.module.impl.combat.aura.rotation.neuro.NeuroRotation;
import morfeov.coliseum.module.impl.movement.ElytraTarget;
import morfeov.coliseum.port.InvActionUtil;
import morfeov.coliseum.port.ModuleManager;
import morfeov.coliseum.port.MoveUtil;
import morfeov.coliseum.port.PortModule;
import morfeov.coliseum.port.RotationHandler;
import morfeov.coliseum.port.SilentComponent;
import morfeov.coliseum.port.event.EventAttackSubmitted;
import morfeov.coliseum.port.event.EventDisconnect;
import morfeov.coliseum.port.event.EventInput;
import morfeov.coliseum.port.setting.BooleanSetting;
import morfeov.coliseum.port.setting.SelectSetting;
import morfeov.coliseum.port.setting.SliderSetting;
import morfeov.coliseum.util.target.CombatPriority;
import morfeov.coliseum.util.target.TargetSelector;
import morfeov.coliseum.util.target.TargetSelectorConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;

@ModuleInfo(id = "attack_aura", name = "AttackAura", description = "Автоматически атакует цели вокруг игрока", category = Category.COMBAT)
public class AttackAura extends PortModule {
    public static int stoptick = 0;
    private float pendingMotionAttackReach;

    private final SelectSetting aimMode = add(new SelectSetting(
            "Режим наводки",
            "ReallyWorld",
            Rotation.getModeNames()
    ).normalizeWith(value -> Rotation.fromString(value).getName()).onChange(value -> {
        AimController.reset();
    }));

    private final SliderSetting dataSetEpochs = add(new SliderSetting("Эпохи обучения", 10, 1, 1000, 1)
            .<SliderSetting>visibleWhen(() -> Rotation.fromString(aimMode.getValue()) == Rotation.DataSet));

    public int dataSetTrainingEpochs() {
        return dataSetEpochs.intValue();
    }

    private final SliderSetting range = add(new SliderSetting("Дистанция", 3.0f, 2.5f, 6.0f, 0.1f));
    private final SliderSetting preRange = add(new SliderSetting("Преддистанция", 0.3f, 0.0f, 3.0f, 0.1f));

    private final BooleanSetting onlyCrits = add(new BooleanSetting("Только критами", true));
    private final BooleanSetting smartCrits = add(new BooleanSetting("Умные криты", true).visibleWhen(onlyCrits::isValue));

    private final SelectSetting priority = add(new SelectSetting("Приоритет", "Distance",
            Arrays.stream(CombatPriority.values()).map(Enum::name).toList()));

    public final BooleanSetting checkRay = add(new BooleanSetting("Проверка на луч", true));
    private final BooleanSetting targetPlayers = add(new BooleanSetting("Цели: игроки", true));
    private final BooleanSetting targetMobs = add(new BooleanSetting("Цели: мобы", true));
    private final BooleanSetting targetAnimals = add(new BooleanSetting("Цели: животные", true));
    private final BooleanSetting targetInvisibles = add(new BooleanSetting("Цели: невидимые", true));

    public final SelectSetting typeSprint = add(new SelectSetting(
            "Тип спринта",
            "Packet",
            List.of("Packet", "Legit", "RW", "None")
    ).<SelectSetting>visibleWhen(() -> !isSprintTypeSpooky()));
    private final SelectSetting moveCorrection = add(new SelectSetting(
            "Коррекция",
            "Фокус",
            List.of("Фокус", "Свободная", "Выкл")
    ));
    private final BooleanSetting throughWalls = add(new BooleanSetting("Бить через стены", false));
    private final SelectSetting wallBypass = add(new SelectSetting(
            "Обход",
            "Никакой",
            List.of("Никакой", "ReallyWorld")
    ).visibleWhen(throughWalls::isValue));
    private final BooleanSetting noAttackIfEat = add(new BooleanSetting("Не бить при еде", false));
    public final BooleanSetting clientLook = add(new BooleanSetting("Клиентский взгляд", false));


    private final CooldownTimer cooldownTimer = new CooldownTimer();
    private final TargetSelector targetSelector = new TargetSelector(null);
    private final LastAimAngles lastAimAngles = new LastAimAngles();

    private static AttackAura instance;

    private LivingEntity currentTarget;
    private int lastTargetId = -1;
    private int lastSpookyAttackTick = -1;
    private Rotation lastRotationMode;


    public AttackAura() {
        instance = this;
    }

    public static AttackAura getInstance() {
        return instance;
    }

    @Override
    public void onDisconnect(EventDisconnect event) {
        lastSpookyAttackTick = -1;
        lastAimAngles.clear();
        clearTargetState();
        CriticalUtils.reset();
    }

    public LivingEntity getCurrentTarget() {
        return currentTarget;
    }

    public float getCameraYaw() {
        if (mc.player == null) return 0.0f;
        return SilentComponent.isActive() ? SilentComponent.getFreeYaw() : mc.player.getYRot();
    }


    public boolean isOnlyCrits() {
        return onlyCrits.isValue();
    }


    public boolean isMovementCorrectionActive() {
        return enabled() && (currentTarget != null || SilentComponent.isReleasing()) && !moveCorrection.getValue().equalsIgnoreCase("Выкл");
    }

    public boolean isFocusCorrection() {
        return enabled() && currentTarget != null &&
                (moveCorrection.getValue().equalsIgnoreCase("Фокус") || moveCorrection.getValue().equalsIgnoreCase("Focus"));
    }

    public boolean isFreeCorrection() {
        return enabled() && currentTarget != null &&
                (moveCorrection.getValue().equalsIgnoreCase("Свободная") || moveCorrection.getValue().equalsIgnoreCase("Free"));
    }

    @Override
    public void onEnable() {
        lastAimAngles.clear();
        lastRotationMode = null;
        cooldownTimer.reset();
        targetSelector.clearFocus();
        AuraAimPoint.reset();
        AimController.reset();
        cancelSprintResets();
        RotationComponent.reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        pendingMotionAttackReach = 0;
        morfeov.coliseum.module.impl.combat.aura.sprint.RwSprintReset.clear();
        RotationComponent.cancelPullAway();
        targetSelector.clearFocus();
        AimController.reset();
        AuraAimPoint.reset();
        releaseAuraAim();
        cancelSprintResets();
        currentTarget = null;
        lastTargetId = -1;
        CriticalUtils.reset();
        super.onDisable();
    }

    public void tick(Minecraft ignored) {
        pendingMotionAttackReach = 0;
        if (mc.player == null || mc.level == null) return;
        var clickPearl = morfeov.coliseum.module.impl.player.ClickPearl.getInstance();
        if (clickPearl != null && clickPearl.isThrowing()) {
            lastAimAngles.clear();
            cancelSprintResets();
            return;
        }

        var crystalAura = ModuleManager.getStaticModule(morfeov.coliseum.module.impl.combat.CrystalAura.class);
        if (crystalAura != null && crystalAura.isCrystalBusy()) {
            lastAimAngles.clear();
            cancelSprintResets();
            return;
        }
        CriticalUtils.tickMaceCooldown();
        if (!isSprintTypeLegit() && Rotation.fromString(aimMode.getValue()) != Rotation.HvH) {
            SprintResetHelper.cancelLegitForwardRelease();
        }
        if (stoptick > 0) {
            stoptick--;
            return;
        }

        Rotation mode = Rotation.fromString(aimMode.getValue());
        if (mode != lastRotationMode) {
            lastAimAngles.clear();
            RotationComponent.reset();
            lastRotationMode = mode;
        }

        boolean isGliding = mc.player.isFallFlying();
        float attackRange = getAttackRange();
        float searchRange = attackRange + getPreRange();
        ElytraTarget elytraTarget = ModuleManager.getStaticModule(ElytraTarget.class);
        if (isGliding && elytraTarget != null && elytraTarget.enabled()) {
            searchRange = Math.max(searchRange, elytraTarget.getDistance());
        }

        targetSelector.setConfig(new TargetSelectorConfig(
                true,
                targetPlayers.isValue(),
                targetMobs.isValue(),
                targetAnimals.isValue(),
                targetInvisibles.isValue(),
                true,
                false,
                CombatPriority.valueOf(priority.getValue()),
                throughWalls.isValue()
        ));

        currentTarget = targetSelector.select(mc.player, searchRange);

        if (currentTarget == null || CombatPredicates.strictDistanceTo(mc.player, currentTarget) > searchRange) {
            boolean justLost = lastTargetId != -1;
            if (justLost) releaseAuraAim();
            if (justLost) SpookyTimeRotation.getInstance().reset();
            NeuroRotation.getInstance().targetLost();
            RotationComponent.cancelPullAway();
            currentTarget = null;
            lastTargetId = -1;
            AimController.reset();
            AuraAimPoint.reset();
            cancelSprintResets();
            targetSelector.clearFocus();
            return;
        }


        if (lastTargetId != currentTarget.getId()) {
            cancelSprintResets();
            AimController.reset();
            AuraAimPoint.reset();
            lastTargetId = currentTarget.getId();
        }

        Vec3 eyePos = mc.player.getEyePosition();
        float effectiveReach = isGliding ? Math.max(attackRange + 1.5f, 5.0f) : attackRange;
        if (elytraTarget != null && elytraTarget.isControllingFlight()) {
            effectiveReach = Math.min(effectiveReach, elytraTarget.getAttackDistance());
        }
        float distance = (float) CombatPredicates.strictDistanceTo(mc.player, currentTarget);

        Vec3 aimPoint = RotationComponent.getAimPoint(currentTarget, mode);
        if (isGliding) {
            if (elytraTarget != null && elytraTarget.enabled() && elytraTarget.canTarget(currentTarget)) {
                aimPoint = elytraTarget.getPredictedPoint(mc.player, currentTarget);
            }
        }


        Vec3 diff = aimPoint.subtract(eyePos);
        double horizontalDist = Math.hypot(diff.x, diff.z);

        float targetYaw;
        float targetPitch;
        if (CombatPredicates.isMeleeContact(mc.player, currentTarget)) {
            targetYaw = mc.player.getYRot();
            targetPitch = mc.player.getXRot();
        } else if (horizontalDist < 0.05) {
            targetYaw = mc.player.getYRot();
            targetPitch = isGliding ? 0.0f : (diff.y < 0 ? 60.0f : -60.0f);
        } else {
            targetYaw = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
            targetPitch = (float) -Math.toDegrees(Math.atan2(diff.y, horizontalDist));
            if (isGliding) {
                targetPitch = Mth.clamp(targetPitch, -89.0f, 89.0f);
            }
        }

        float deltaYaw = (float) Mth.wrapDegrees(targetYaw - mc.player.getYRot());
        float deltaPitch = (float) Mth.wrapDegrees(targetPitch - mc.player.getXRot());

        if (isGliding) {
            float gcd = RotationComponent.getGCD();
            if (gcd > 0.0001f) {
                deltaYaw -= deltaYaw % gcd;
                deltaPitch -= deltaPitch % gcd;
            }
        }
        boolean aimingAtTarget = CombatPredicates.isAimingAtTarget(mc.player, currentTarget, effectiveReach, throughWalls.isValue())
                || (isGliding && distance <= effectiveReach + 0.5f);
        boolean airborne = !mc.player.onGround();
        boolean moving = MoveUtil.isMoving();

        if (isGliding) {
            if (clientLook.isValue()) {
                RotationHandler.applyClient(deltaYaw, deltaPitch);
            } else {
                RotationHandler.apply(deltaYaw, deltaPitch);
            }
        } else {
            AimController.apply(
                    new AimPart(
                            deltaYaw,
                            deltaPitch,
                            distance,
                            aimingAtTarget,
                            airborne,
                            currentTarget,
                            aimPoint,
                            attackRange,
                            moving,
                            mode.getName()
                    ),
                    mode,
                    clientLook.isValue()
            );
        }

        if (mode == Rotation.SpookyTime) lastAimAngles.remember(mc.player, mc.player.getYRot(), mc.player.getXRot());
        boolean canAttackNow = distance <= effectiveReach || CombatPredicates.isMeleeContact(mc.player, currentTarget);
        if (canAttackNow) {
            if (usesMotionAttack(mode) && !isGliding && !mc.player.isPassenger()) {
                pendingMotionAttackReach = effectiveReach;
            } else {
                tickAttack(effectiveReach);
            }
        } else {
            cancelSprintResets();
        }
    }

    @Override
    public void onPostMotion(morfeov.coliseum.port.event.EventPostMotion event) {
        float reach = pendingMotionAttackReach;
        pendingMotionAttackReach = 0;
        Rotation mode = Rotation.fromString(aimMode.getValue());
        if (reach > 0 && usesMotionAttack(mode)) {
            tickAttack(reach);
        }
    }

    private static boolean usesMotionAttack(Rotation mode) {
        return mode == Rotation.ReallyWorld || mode == Rotation.DataSet || mode == Rotation.HvH;
    }

    public void onAttackSubmitted(EventAttackSubmitted event) {
        if (mc.player != null) {
            lastSpookyAttackTick = mc.player.tickCount;
            CriticalUtils.onMaceAttack(mc.player);
            if (mc.player.isFallFlying()) {
                return;
            }
        }
        RotationComponent.onAttack();
    }

    @Override
    public void onInput(EventInput event) {
        if (!enabled() || mc.player == null) return;
        var clickPearl = morfeov.coliseum.module.impl.player.ClickPearl.getInstance();
        var autoMace = ModuleManager.getStaticModule(morfeov.coliseum.module.impl.combat.AutoMace.class);
        var crystalAura = ModuleManager.getStaticModule(morfeov.coliseum.module.impl.combat.CrystalAura.class);
        boolean paused = (clickPearl != null && clickPearl.isThrowing())
                || (crystalAura != null && crystalAura.isCrystalBusy())
                || (autoMace != null && autoMace.shouldHoldAttack());
        if (!paused && isSprintTypeSpooky() && shouldResetSpookySprint(mc.player)) {
            event.setForward(0);
            event.setStrafe(0);
            event.setJump(false);
            return;
        }
        if (currentTarget == null && !SilentComponent.isActive()) return;
        if (moveCorrection.getValue().equalsIgnoreCase("Выкл")) return;

        float rotationYaw = mc.player.getYRot();
        float referenceYaw = getCameraYaw();

        if (!paused && isFocusCorrection() && currentTarget != null) {
            Vec3 playerNext = mc.player.position().add(mc.player.getDeltaMovement());
            Vec3 targetNext = currentTarget.getBoundingBox().getCenter();
            if (currentTarget instanceof Player playerTarget) {
                targetNext = targetNext.add(playerTarget.getDeltaMovement());
            }
            Vec3 direction = targetNext.subtract(playerNext);
            if ((direction.x * direction.x + direction.z * direction.z) > 1.0e-8) {
                referenceYaw = (float) (Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90);
            }
        } else if (!paused && currentTarget != null && !isFreeCorrection()) {
            return;
        }

        float[] input = ColiseumMovementCorrection.correct(
                rotationYaw, referenceYaw, event.getForward(), event.getStrafe(), mc.player.isFallFlying());
        event.setForward(input[0]);
        event.setStrafe(input[1]);
    }





    public void spoofWallBypass(LivingEntity target) {
        if (target == null || mc.player == null || mc.level == null || mc.getConnection() == null) return;
        try {
            Vec3 eye = mc.player.getEyePosition();
            Vec3 aimPoint = targetPos(target).add(
                    0,
                    target.getBbHeight() / 1.2f + (target.getY() - target.yo),
                    0
            );

            Vec3 dirVec = aimPoint.subtract(eye).normalize();
            Vec3 currentStart = eye;

            for (int i = 0; i < 2; i++) {
                HitResult result = mc.level.clip(new ClipContext(
                        currentStart,
                        aimPoint,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        mc.player
                ));

                if (result.getType() != HitResult.Type.BLOCK) break;

                BlockHitResult blockHit = (BlockHitResult) result;
                BlockPos pos = blockHit.getBlockPos();
                Direction face = blockHit.getDirection();

                mc.getConnection().send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, face, 0));
                mc.getConnection().send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, pos, face, 0));

                Vec3 hit = blockHit.getLocation();
                currentStart = new Vec3(
                        hit.x + dirVec.x * 0.05,
                        hit.y + dirVec.y * 0.05,
                        hit.z + dirVec.z * 0.05
                );

                if (currentStart.distanceToSqr(aimPoint) < 0.0025) break;
            }
        } catch (Throwable ignored) {
        }
    }

    public boolean isTargetBehindWall(LivingEntity target) {
        if (target == null || mc.player == null || mc.level == null) return false;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 targetEye = target.getEyePosition();
        HitResult result = mc.level.clip(new ClipContext(
                eye,
                targetEye,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));
        return result.getType() == HitResult.Type.BLOCK;
    }

    private static Vec3 targetPos(LivingEntity entity) {
        return new Vec3(entity.getX(), entity.getY(), entity.getZ());
    }

    private boolean tickAttack(float range) {
        LocalPlayer player = mc.player;
        var autoMace = ModuleManager.getStaticModule(morfeov.coliseum.module.impl.combat.AutoMace.class);
        if (autoMace != null && autoMace.shouldHoldAttack()) return false;
        if (player == null || currentTarget == null || !currentTarget.isAlive()) {
            cancelSprintResets();
            return false;
        }

        ElytraTarget elytraTarget = ModuleManager.getStaticModule(ElytraTarget.class);
        boolean elytraPursuit = elytraTarget != null && elytraTarget.isControllingFlight();
        if (elytraPursuit && !elytraTarget.shouldAttackPredicted(currentTarget)) {
            cancelSprintResets();
            return false;
        }

        boolean isGliding = player.isFallFlying();
        float effectiveRange = isGliding ? Math.max(range, 5.0f) : range;
        if (elytraPursuit) effectiveRange = Math.min(effectiveRange, elytraTarget.getAttackDistance());

        double curDist = CombatPredicates.strictDistanceTo(player, currentTarget);
        double speedLength = Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z);
        double predDist = speedLength > 0.15
                ? CombatPredicates.strictDistanceTo(player.getEyePosition().add(player.getDeltaMovement()), currentTarget)
                : curDist;
        float distance = (float) Math.min(curDist, predDist);
        boolean meleeContact = CombatPredicates.isMeleeContact(player, currentTarget);
        float rangeTolerance = (float) Math.min(0.50, speedLength * 0.45);
        if (distance > effectiveRange + rangeTolerance && !meleeContact) {
            cancelSprintResets();
            return false;
        }

        float baseThreshold = isGliding ? 0.91f : CombatPredicates.attackCooldownThreshold(player, onlyCrits.isValue());
        float cooldownThreshold = baseThreshold;
        float cooldownProgress = player.getAttackStrengthScale(0.5f);
        Rotation aimRotation = Rotation.fromString(aimMode.getValue());
        boolean hvh = aimRotation == Rotation.HvH;
        boolean cooldownReady = cooldownProgress >= (hvh ? .93f : cooldownThreshold);

        if (!isGliding && player.isUsingItem() && noAttackIfEat.isValue()) {
            cancelSprintResets();
            return false;
        }

        boolean wallBypassRw = wallBypass.getValue() != null && wallBypass.getValue().equalsIgnoreCase("ReallyWorld");
        boolean ignoreWalls = throughWalls.isValue() || wallBypassRw;

        if (hvh && (isGliding || !CombatPredicates.isExactAimAtTarget(player, currentTarget, range, ignoreWalls))) {
            cancelSprintResets();
            return false;
        }

        if (aimRotation == Rotation.DataSet && (!NeuroRotation.getInstance().canUse()
                || !isGliding && checkRay.isValue() && !CombatPredicates.isExactAimAtTarget(player, currentTarget, range, ignoreWalls))) {
            cancelSprintResets();
            return false;
        }

        if (!hvh && checkRay.isValue() && (aimRotation != Rotation.DataSet || isGliding)) {
            float checkRange = isGliding ? effectiveRange : range;
            boolean aiming = meleeContact
                    || CombatPredicates.isAimingAtTarget(player, currentTarget, checkRange, ignoreWalls);
            if (!aiming && isGliding) {
                Vec3 toTarget = currentTarget.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
                Vec3 lookVec = player.getViewVector(1.0f);
                if (lookVec.dot(toTarget) > -0.2 || distance <= 4.2f || (isGliding && distance <= effectiveRange)) {
                    aiming = true;
                }
            }
            if (!aiming) {
                cancelSprintResets();
                return false;
            }
        }

        boolean rwMode = !isGliding && (aimRotation == Rotation.ReallyWorld);
        if (rwMode && !meleeContact && !RotationComponent.canReallyWorldAttack(player, currentTarget)) {
            cancelSprintResets();
            return false;
        }

        if (morfeov.coliseum.module.impl.combat.Criticals.startJumpIfNeeded(player, this)) return false;
        boolean criticalReady = hvh ? AttackAura.isPlayerInCriticalState(player)
                : !onlyCrits.isValue() || isGliding || AttackAura.canAttack(player, smartCrits.isValue());

        if (!cooldownReady || !criticalReady) return false;

        if ((isSprintTypeLegit() || hvh) && canResetLegitSprint(player, isGliding)
                && (player.isSprinting() || ((morfeov.coliseum.mixin.LocalPlayerAccessor) player).coliseum$wasSprinting())) {
            player.setSprinting(false);
            SprintResetHelper.startLegitForwardRelease(1);
            return false;
        }

        if (wallBypassRw && isTargetBehindWall(currentTarget)) {
            spoofWallBypass(currentTarget);
        }

        boolean criticalAttack = isGliding || AttackAura.isPlayerInCriticalState(player);
        if (isGliding) {
            player.fallDistance = Math.max(player.fallDistance, 0.08);
        }

        boolean resetSprintPacket = shouldResetSprintWithPacket(player, isGliding);
        if (resetSprintPacket) {
            player.setSprinting(false);
            sendSprintCommand(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING);
        }

        boolean submitted;
        try {
            submitted = submitAttack(player, currentTarget, criticalAttack,
                    player.getMainHandItem().is(Items.MACE));
        } finally {
            if (resetSprintPacket) {
                player.setSprinting(true);
                sendSprintCommand(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING);
            }
        }

        if (!submitted) {
            cancelSprintResets();
            return false;
        }
        cooldownTimer.reset();
        cancelSprintResets();
        return true;
    }

    private boolean submitAttack(LocalPlayer player, LivingEntity target,
                                 boolean criticalAttack, boolean maceAttack) {
        if (mc.gameMode == null) return false;
        boolean submitted = AttackContext.run(criticalAttack, maceAttack,
                () -> mc.gameMode.attack(player, target));
        if (submitted) {
            Rotation mode = Rotation.fromString(aimMode.getValue());
            if (usesMotionAttack(mode)) {
                // Match Minecraft.startAttack's swing and punch after the entity attack.
                player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getAttackAnimation(), false);
                player.connection.send(net.minecraft.network.protocol.game.ServerboundPunchPacket.INSTANCE);
            } else {
                player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
            }
            CombatTagManager.onAttack(target);
        }
        return submitted;
    }

    private boolean shouldResetSprintWithPacket(LocalPlayer player, boolean gliding) {
        return player != null
                && !gliding
                && !player.isInWater()
                && player.isSprinting()
                && player.connection != null
                && isSprintTypePacket();
    }

    private boolean shouldResetSpookySprint(LocalPlayer player) {
        if (currentTarget == null || !currentTarget.isAlive()) return false;
        var stack = player.getMainHandItem();
        boolean mace = stack.is(Items.MACE);
        double effectiveRange = mace ? 3 : Math.max(3, range.floatValue());
        int ticksSinceAttack = lastSpookyAttackTick < 0 || player.tickCount < lastSpookyAttackTick
            ? Integer.MAX_VALUE : player.tickCount - lastSpookyAttackTick;
        return SpookySprintReset.shouldRelease(true, player.onGround(), player.isUnderWater(),
            CombatPredicates.strictDistanceTo(player, currentTarget), effectiveRange, player.getDeltaMovement().y,
            player.getCooldowns().isOnCooldown(stack), net.minecraft.world.item.MaceItem.canSmashAttack(player), mace,
            player.fallDistance, player.getAttackStrengthScale(3.5f), ticksSinceAttack);
    }

    private boolean canResetLegitSprint(LocalPlayer player, boolean gliding) {
        return player != null
                && !player.isInWater()
                && !player.isInLava()
                && !player.onClimbable()
                && !player.isPassenger()
                && !player.getAbilities().flying
                && !gliding;
    }

    private void cancelSprintResets() {
        SprintResetHelper.cancelReset();
    }

    public boolean shouldPauseAutoSprint() {
        return enabled() && ((isSprintTypeLegit() || Rotation.fromString(aimMode.getValue()) == Rotation.HvH)
                && SprintResetHelper.isLegitForwardReleaseActive()
            || morfeov.coliseum.module.impl.combat.aura.sprint.RwSprintReset.isBlocking());
    }

    public boolean isSprintTypeRw() { return !isSprintTypeSpooky() && typeSprint.getValue().equals("RW"); }

    private void sendSprintCommand(LocalPlayer player, ServerboundPlayerCommandPacket.Action mode) {
        if (player != null && player.connection != null) {
            player.connection.send(new ServerboundPlayerCommandPacket(player, mode));
        }
    }

    private boolean tryBreakShield(LocalPlayer player, Player target) {
        int axeSlot = findAxeInHotbar(player);
        if (axeSlot == -1) return false;

        InvActionUtil swaps = InvActionUtil.get();
        if (!swaps.tryAcquire("AttackAura", InvActionUtil.PRIORITY_COMBAT, 120L)) {
            return false;
        }
        int previousSlot = player.getInventory().getSelectedSlot();
        selectHotbarSlot(player, axeSlot);
        try {
            return submitAttack(player, target, false, false);
        } finally {
            selectHotbarSlot(player, previousSlot);
            swaps.release("AttackAura");
        }
    }

    private boolean canBreakShield(LocalPlayer player, Player target) {
        return target.isBlocking()
                && isLookingAtPlayer(player, target)
                && findAxeInHotbar(player) != -1;
    }

    private void selectHotbarSlot(LocalPlayer player, int slot) {
        if (slot < 0 || slot >= 9 || player.getInventory().getSelectedSlot() == slot) {
            return;
        }
        InvActionUtil swaps = InvActionUtil.get();
        swaps.selectHotbar("AttackAura", InvActionUtil.PRIORITY_COMBAT,
                slot, true, 120L);
    }

    private int findAxeInHotbar(LocalPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(ItemTags.AXES)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isLookingAtPlayer(LocalPlayer player, Player target) {
        Vec3 look = target.getViewVector(1.0f).normalize();
        Vec3 directionToPlayer = player.getEyePosition().subtract(target.getEyePosition()).normalize();
        double dot = Math.clamp(look.dot(directionToPlayer), -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot)) < 100.0;
    }

    public float getAttackRange() {
        return range.getValue();
    }

    public float getPreRange() {
        return preRange.getValue();
    }

    public boolean isSprintTypePacket() {
        return !isSprintTypeSpooky() && typeSprint.getValue().equals("Packet");
    }

    public boolean isSprintTypeLegit() {
        return !isSprintTypeSpooky() && typeSprint.getValue().equals("Legit");
    }

    public boolean isSprintTypeSpooky() { return Rotation.fromString(aimMode.getValue()) == Rotation.SpookyTime; }

    public boolean isSprintTypeNone() {
        return !isSprintTypeSpooky() && typeSprint.getValue().equals("None");
    }

    private void clearTargetState() {
        if (SilentComponent.isActive()) SilentComponent.forceClear();
        RotationComponent.cancelPullAway();
        cancelSprintResets();
        AimController.reset();
        AuraAimPoint.reset();
        targetSelector.clearFocus();
        currentTarget = null;
        lastTargetId = -1;
    }

    public String getAimModeName() {
        return aimMode.getValue();
    }

    private void releaseAuraAim() {
        if (mc.player != null && Rotation.fromString(aimMode.getValue()) == Rotation.ReallyWorld) {
            SilentComponent.finishAt(mc.player.getYRot(), mc.player.getXRot());
        } else if (!releaseSpookyAim()) SilentComponent.forceClear();
    }

    private boolean releaseSpookyAim() {
        var last = lastAimAngles.consume(mc.player);
        if (Rotation.fromString(aimMode.getValue()) != Rotation.SpookyTime || last == null || mc.player == null) return false;
        SilentComponent.finishAt(last.yaw(), last.pitch());
        return true;
    }

    /** Rotation anticipation only; does not advance attack or mace-delay state. */
    public boolean rotationAttackReady() {
        var player = mc.player;
        if (player == null || currentTarget == null || player.getAttackStrengthScale(.5f)
                < CombatPredicates.attackCooldownThreshold(player, onlyCrits.isValue())) return false;
        if (player.isUsingItem() && noAttackIfEat.isValue()) return false;
        if (!throughWalls.isValue() && !player.hasLineOfSight(currentTarget)) return false;
        return !onlyCrits.isValue() || player.isInWater() || player.isUnderWater()
            || CriticalUtils.isInCobweb(player)
            || morfeov.coliseum.module.impl.combat.Criticals.isActive() && morfeov.coliseum.module.impl.combat.Criticals.allowsAuraAttack(player)
            || CriticalUtils.isPlayerInCriticalState(player)
            || smartCrits.isValue() && player.onGround() && !player.input.keyPresses.jump();
    }

    public static boolean canAttack(LocalPlayer player, boolean smartCrits) {
        if (player == null) return true;
        AttackAura aura = ModuleManager.getStaticModule(AttackAura.class);
        LivingEntity target = aura != null ? aura.getCurrentTarget() : null;
        boolean onlyCrits = aura == null || aura.onlyCrits.isValue();
        return CriticalUtils.checkCritical(player, target, onlyCrits, smartCrits);
    }

    public static boolean isPlayerInCriticalState(LocalPlayer player) {
        return CriticalUtils.isPlayerInCriticalState(player);
    }

    public static boolean canPerformCriticalHit(LocalPlayer player) {
        return CriticalUtils.canPerformCriticalHit(player);
    }
}
