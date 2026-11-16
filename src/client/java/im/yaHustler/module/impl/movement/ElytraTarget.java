package im.yaHustler.module.impl.movement;

import java.util.ArrayList;
import java.util.List;
import im.yaHustler.port.PortModule;
import im.yaHustler.port.setting.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@im.yaHustler.module.ModuleInfo(id="elytra_target", name="ElytraTarget", description="Перегон цели и обход препятствий на элитрах", category=im.yaHustler.module.Category.MOVEMENT)
public final class ElytraTarget extends PortModule {
   private Vec3 latestPredicted;
   private LivingEntity trackedTarget;
   private int predictionTarget=-1, predictionTick=Integer.MIN_VALUE, slowPredictionTicks;
   private boolean predictionActive;
   private final SliderSetting attackDistance=add(new SliderSetting("Дистанция атаки",3.5f,1,6,.1f));
   public float getDistance() { return detectionDistance.getValue(); }
   public float getAttackDistance() { return attackDistance.getValue(); }
   public boolean isControllingFlight() { return enabled() && mc.player!=null && mc.player.isFallFlying(); }
   public boolean canTarget(LivingEntity target) { return isControllingFlight() && target!=null && target!=mc.player && target.isAlive() && !target.isRemoved() && (target.isFallFlying() || elytraLag.isValue()); }
   public boolean shouldAttackPredicted(LivingEntity target) { return target!=null && (!elytraLag.isValue() || !collisionEscape); }
   public Vec3 predictedPosition() { return renderPredict.isValue() && isControllingFlight() ? latestPredicted : null; }
   public LivingEntity trackedTarget() { return trackedTarget; }
   public String predictionRenderMode() { return renderMode.getValue(); }
   public Vec3 getPredictedPoint(net.minecraft.client.player.LocalPlayer player, LivingEntity target) { return predict(target); }
   public Vec3 predict(LivingEntity target) {
      trackedTarget=target;
      if(target==null) { latestPredicted=null; return Vec3.ZERO; }
      if(elytraLag.isValue() && !target.isFallFlying() && flightPoint!=null) return latestPredicted=flightPoint;
      Vec3 origin=target.position().add(0,.5,0);
      double speed=target.position().subtract(new Vec3(target.xOld,target.yOld,target.zOld)).length()*20;
      if(predictionTarget!=target.getId()) { predictionTarget=target.getId(); predictionTick=Integer.MIN_VALUE; predictionActive=false; slowPredictionTicks=0; }
      if(predictionTick!=mc.player.tickCount) {
         predictionTick=mc.player.tickCount;
         if(speed>minBps.getValue()) { predictionActive=true; slowPredictionTicks=0; }
         else if(++slowPredictionTicks>=3) predictionActive=false;
      }
      if(!prediction.isValue() || !target.isFallFlying() || !predictionActive) return latestPredicted=origin;
      Vec3 direction;
      double multiplier=1;
      if(predictionMode.getValue().equals("От ротации")) direction=target.getLookAngle().normalize();
      else {
         Vec3 velocity=target.getDeltaMovement(), simulated=Vec3.ZERO;
         for(int step=0;step<8;step++) { velocity=simulateGlide(velocity,target); simulated=simulated.add(velocity); }
         multiplier=simulated.length()/8;
         direction=simulated.lengthSqr()<.0001?target.getLookAngle().normalize():simulated.normalize();
      }
      double lead=predictionStrength.getValue()+(predictionMode.getValue().equals("От ротации")?2:0);
      latestPredicted=origin.add(direction.scale(lead*multiplier));
      if(chaseMode.getValue().equals("Легитный")) return target.getEyePosition();
      return latestPredicted;
   }
   private static Vec3 simulateGlide(Vec3 velocity, LivingEntity target) {
      return simulateGlide(velocity,target.getLookAngle(),target.getXRot(),target.getGravity(),target.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING));
   }
   public static Vec3 simulateGlide(Vec3 velocity, Vec3 look, float pitchDegrees, double gravity, boolean slowFalling) {
      double horizontal=look.horizontalDistance(), speed=velocity.horizontalDistance();
      double pitch=Math.toRadians(pitchDegrees), lift=Math.cos(pitch)*Math.cos(pitch);
      if(slowFalling && velocity.y<=0) gravity=Math.min(gravity,.01);
      velocity=velocity.add(0,gravity*(-1+lift*.75),0);
      if(horizontal>0 && velocity.y<0) { double dive=velocity.y*-.1*lift; velocity=velocity.add(look.x*dive/horizontal,dive,look.z*dive/horizontal); }
      if(pitch<0 && horizontal>0) { double climb=speed*-Math.sin(pitch)*.04; velocity=velocity.add(-look.x*climb/horizontal,climb*3.2,-look.z*climb/horizontal); }
      if(horizontal>0) velocity=velocity.add((look.x/horizontal*speed-velocity.x)*.1,0,(look.z/horizontal*speed-velocity.z)*.1);
      return velocity.multiply(.99,.98,.99);
   }
   private boolean visible(Vec3 point) {
      return mc.level!=null && mc.player!=null && mc.level.clip(new net.minecraft.world.level.ClipContext(mc.player.getEyePosition(),point,
         net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player)).getType()==net.minecraft.world.phys.HitResult.Type.MISS;
   }
   @Override public void onDisconnect(im.yaHustler.port.event.EventDisconnect event) { resetFlight(); trackedTarget=null; latestPredicted=null; }
   private static final class FlightTimer {
      private long started=System.currentTimeMillis();
      void reset() { started=System.currentTimeMillis(); }
      boolean elapsed(long milliseconds) { return System.currentTimeMillis()-started>milliseconds; }
      boolean elapsedAndReset(double milliseconds) { if(System.currentTimeMillis()-started<milliseconds)return false;reset();return true; }
   }

   public final SliderSetting detectionDistance;
   public final BooleanSetting elytraLag;
   public final SliderSetting lagDistance;
   public final BooleanSetting prediction;
   public final SelectSetting predictionMode;
   public final SliderSetting minBps;
   public final SliderSetting predictionStrength;
   public final SelectSetting chaseMode;
   private final BooleanSetting renderPredict;
   private final SelectSetting renderMode;
   private ElytraTarget.FlightPhase phase;
   public Vec3 flightPoint;
   private boolean collisionEscape;
   private boolean needsRetreat;
   private int escapeSide;
   private final FlightTimer rocketTimer;
   private final FlightTimer escapeTimer;
   private final FlightTimer lagTimer;
   private final FlightTimer flushTimer;
   private final List<Vec3> escapeCandidates;

   private void requestRocket() {
      if (this.rocketTimer.elapsedAndReset(250.0)) {
         this.useRocket();
      }
   }

   private Vec3 perpendicular(Vec3 direction) {
      Vec3 side = new Vec3(-direction.z, 0.0, direction.x);
      return side.lengthSqr() < 0.001 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
   }

   private void resetFlight() {
      this.flightPoint = null;
      this.phase = ElytraTarget.FlightPhase.LEAVING;
      this.collisionEscape = false;
      this.needsRetreat = false;
      this.releasePackets();
   }

   private boolean canOccupy(Vec3 point) {
      if (mc.player != null && mc.level != null) {
         double offsetX = point.x - mc.player.getX();
         double offsetY = point.y - mc.player.getEyeY();
         double offsetZ = point.z - mc.player.getZ();
         AABB bounds = mc.player.getBoundingBox().move(offsetX, offsetY, offsetZ).inflate(0.3);
         return mc.level.noCollision(mc.player, bounds) && !mc.level.containsAnyLiquid(bounds) ? !this.intersectsHazards(bounds) : false;
      } else {
         return false;
      }
   }

   private Vec3 furthestSafePoint(Vec3 origin, Vec3 direction, double distance) {
      if (direction.lengthSqr() < 0.001) {
         return null;
      }

      Vec3 normalized = direction.normalize();
      Vec3 furthest = null;
      int steps = sampleCount(distance, 0.4);

      for (int step = 1; step <= steps; step++) {
         Vec3 point = origin.add(normalized.scale(distance * step / steps));
         if (!this.canOccupy(point)) {
            break;
         }

         furthest = point;
      }

      return furthest;
   }

   @Override
   public void onDisable() {
      this.resetFlight();
      trackedTarget = null;
      latestPredicted = null;

      super.onDisable();
   }

   private void addEscapeCandidates(Vec3 forward, Vec3 side, double distance) {
      double halfWidth = distance * 0.5;
      double lift = Mth.clamp(-1.5, distance * 0.3, 1.5);
      this.escapeCandidates.add(forward.scale(distance).add(0.0, lift, 0.0));
      this.escapeCandidates.add(forward.scale(distance).add(side.scale(halfWidth)).add(0.0, lift, 0.0));
      this.escapeCandidates.add(forward.scale(distance).add(side.scale(-halfWidth)).add(0.0, lift, 0.0));
      this.escapeCandidates.add(side.scale(distance).add(0.0, lift, 0.0));
      this.escapeCandidates.add(side.scale(-distance).add(0.0, lift, 0.0));
   }

   @Override
   public void tick(net.minecraft.client.Minecraft client) {
      if (mc.player != null && mc.level != null) {
         LivingEntity target = im.yaHustler.port.CombatHooks.target();
         if (target == null || !target.isAlive()) {
            resetFlight();
            trackedTarget = null;
            latestPredicted = null;
            return;
         }
         if (this.elytraLag.isValue()
            && mc.player.isFallFlying()
            && target != null
            && !(target.position().subtract(new Vec3(target.xOld,target.yOld,target.zOld)).length()*20 >= 15.0)
            && !target.isFallFlying()) {
            Vec3 playerEye = mc.player.getEyePosition();
            Vec3 targetEye = target.getEyePosition();
            double horizontalDistance = Math.hypot(targetEye.x - playerEye.x, targetEye.z - playerEye.z);
            double distance = playerEye.distanceTo(targetEye);
            if (this.isColliding()) {
               this.releasePackets();
               this.flushTimer.reset();
               this.phase = ElytraTarget.FlightPhase.LEAVING;
               this.updateCollisionEscape(playerEye, targetEye, target);
               this.requestRocket();
               this.collisionEscape = true;
            } else {
               this.collisionEscape = false;
               if (this.phase == ElytraTarget.FlightPhase.ATTACK) {
                  this.updateAttackLeg(playerEye, targetEye, horizontalDistance);
               } else {
                  this.updateRetreatLeg(playerEye, targetEye, target, horizontalDistance);
               }

               this.updatePacketLag(distance);
            }
         } else {
            this.resetFlight();
         }
      }
   }

   public ElytraTarget() {
      detectionDistance=add(new SliderSetting("Дистанция обнаружения",30,5,100,5));
      elytraLag=add(new BooleanSetting("Элитра лаг",false));
      lagDistance=add(new SliderSetting("Дистанция залагивания",7,3,15,.5f).visibleWhen(elytraLag::isValue));
      prediction=add(new BooleanSetting("Элитра перегон",false));
      predictionMode=add(new SelectSetting("Режим предикта","От скорости",List.of("От ротации","От скорости")).visibleWhen(prediction::isValue));
      minBps=add(new SliderSetting("Предиктить от BPS",15,5,30,1));
      predictionStrength=add(new SliderSetting("Сила предикта",3,0,10,.5f).visibleWhen(prediction::isValue));
      chaseMode=add(new SelectSetting("Мод перегона","Легитный",List.of("Легитный","Полный рейдж","Полу рейдж")).visibleWhen(prediction::isValue));
      renderPredict=add(new BooleanSetting("Рендерить предикт",false).visibleWhen(prediction::isValue));
      renderMode=add(new SelectSetting("Как отображать","Моделька",List.of("Квадратик","Моделька")).visibleWhen(()->prediction.isValue()&&renderPredict.isValue()));
      phase=FlightPhase.LEAVING;
      escapeSide=1;
      rocketTimer=new FlightTimer(); escapeTimer=new FlightTimer(); lagTimer=new FlightTimer(); flushTimer=new FlightTimer();
      escapeCandidates=new ArrayList<>();
   }
   private static double escapeScore(double distance, double alignment, double vertical) {
      double positiveAlignment = Math.max(0.0, alignment);
      double penalty = Math.max(0.0, vertical) * 4.0;
      return distance * 1.5 + positiveAlignment * 2.0 - penalty;
   }

   private boolean blocksEscape(BlockState state) {
      if (state.isAir()) {
         return false;
      }

      if (state.is(BlockTags.LEAVES)) {
         return true;
      }

      String blockName = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
      return blockName.contains("glass");
   }

   private boolean pathClear(Vec3 start, Vec3 end) {
      Vec3 delta = end.subtract(start);
      double distance = delta.length();
      if (distance < 0.001) {
         return true;
      }

      int steps = sampleCount(distance, 0.4);

      for (int step = 1; step <= steps; step++) {
         double progress = (double)step / steps;
         Vec3 point = start.add(delta.scale(progress));
         if (!this.canOccupy(point)) {
            return false;
         }
      }

      return true;
   }

   private void useRocket() {
      im.yaHustler.util.InventoryActions.use(mc,net.minecraft.world.item.Items.FIREWORK_ROCKET,false);
   }

   private double rotationCost(Vec3 start, Vec3 end, float yaw, float pitch) {
      double dx = end.x - start.x;
      double dy = end.y - start.y;
      double dz = end.z - start.z;
      double horizontal = Math.sqrt(dx * dx + dz * dz);
      if (horizontal < 1.0E-4) {
         return Math.abs(pitch) + 180.0;
      }

      float targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
      float targetPitch = (float)Math.toDegrees(-Math.atan2(dy, horizontal));
      double yawDelta = Math.abs(Mth.wrapDegrees(targetYaw - yaw));
      double pitchDelta = Math.abs(targetPitch - pitch);
      return yawDelta + pitchDelta;
   }

   private boolean validRetreat(Vec3 playerEye, Vec3 targetEye, Vec3 point) {
      if (point == null) {
         return false;
      }

      double horizontalDistance = Math.hypot(point.x - targetEye.x, point.z - targetEye.z);
      return horizontalDistance < 2.5 ? false : this.canOccupy(point) && visible(point) && this.pathClear(playerEye, point);
   }

   private void releasePackets() {
      im.yaHustler.port.PacketBufferManager.INSTANCE.release("target");
   }

   private List<Vec3> escapeDirections(Vec3 backward, Vec3 side) {
      ArrayList<Vec3> directions = new ArrayList<>();
      directions.add(backward);
      directions.add(backward.add(side).normalize());
      directions.add(backward.add(side.reverse()).normalize());
      directions.add(side);
      directions.add(side.reverse());
      directions.add(backward.add(0.0, -0.5, 0.0).normalize());
      directions.add(side.add(0.0, -0.5, 0.0).normalize());
      directions.add(side.reverse().add(0.0, -0.5, 0.0).normalize());
      directions.add(new Vec3(0.0, -1.0, 0.0));
      directions.add(backward.add(0.0, 0.7, 0.0).normalize());
      directions.add(new Vec3(0.0, 1.0, 0.0));
      return directions;
   }

   private boolean intersectsHazards(AABB bounds) {
      BlockPos minimum = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
      BlockPos maximum = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);

      for (BlockPos position : BlockPos.betweenClosed(minimum, maximum)) {
         BlockState state = mc.level.getBlockState(position);
         if (this.blocksEscape(state)) {
            return true;
         }
      }

      return false;
   }

   private void updatePacketLag(double distance) {
      if (distance > this.lagDistance.getValue().floatValue() && this.lagTimer.elapsed(250L)) {
         this.releasePackets();
         this.bufferPackets();
         this.lagTimer.reset();
         this.flushTimer.reset();
      }

      if (this.flushTimer.elapsed(1000L)) {
         this.bufferPackets();
         this.releasePackets();
      }
   }

   private Vec3 awayDirection(LivingEntity target, Vec3 playerEye, Vec3 targetEye) {
      Vec3 motion = target.getDeltaMovement();
      Vec3 horizontal = new Vec3(motion.x, 0.0, motion.z);
      if (horizontal.lengthSqr() < 0.001) {
         Vec3 recorded = target.position().subtract(new Vec3(target.xOld,target.yOld,target.zOld));
         horizontal = new Vec3(recorded.x, 0.0, recorded.z);
      }

      if (horizontal.lengthSqr() < 0.001) {
         Vec3 look = target.getLookAngle();
         horizontal = new Vec3(look.x, 0.0, look.z);
      }

      if (horizontal.lengthSqr() < 0.001) {
         Vec3 offset = targetEye.subtract(playerEye);
         horizontal = new Vec3(offset.x, 0.0, offset.z);
      }

      return horizontal.lengthSqr() < 0.001 ? new Vec3(0.0, 0.0, 1.0) : horizontal.normalize().reverse();
   }

   @Override
   public void onAttack(im.yaHustler.port.event.EventAttackEntity event) {
      this.escapeSide = -this.escapeSide;
      this.phase = ElytraTarget.FlightPhase.LEAVING;
      this.needsRetreat = true;
   }

   private void updateCollisionEscape(Vec3 playerEye, Vec3 targetEye, LivingEntity target) {
      if (!this.collisionEscape || this.flightPoint == null || this.escapeTimer.elapsed(300L)) {
         Vec3 point = this.findEscapePoint(playerEye, targetEye, target, 7.0);
         if (point != null) {
            this.flightPoint = point;
         }

         this.escapeTimer.reset();
      }
   }

   private Vec3 findRetreatPoint(Vec3 playerEye, Vec3 targetEye, LivingEntity target) {
      Vec3 backward = this.awayDirection(target, playerEye, targetEye);
      Vec3 side = this.perpendicular(backward);
      double radius = Math.max(5.0, this.lagDistance.getValue().floatValue());
      this.escapeCandidates.clear();
      this.addEscapeCandidates(backward, side, radius);
      this.addEscapeCandidates(backward, side, radius + 2.5);
      this.addEscapeCandidates(backward, side, radius + 5.0);
      float yaw = mc.player.getYRot();
      float pitch = mc.player.getXRot();
      Vec3 preferred = null;
      Vec3 alternate = null;
      double preferredScore = Double.NEGATIVE_INFINITY;
      double alternateScore = Double.NEGATIVE_INFINITY;

      for (Vec3 offset : this.escapeCandidates) {
         double horizontal = Math.hypot(offset.x, offset.z);
         if (!(horizontal < 5.0)) {
            Vec3 point = targetEye.add(offset);
            if (!(point.distanceTo(playerEye) < 4.0) && this.canOccupy(point) && visible(point) && this.pathClear(playerEye, point)) {
               double slope = Math.max(0.0, offset.y) / Math.max(0.5, horizontal);
               double penalty = Math.max(0.0, slope - 0.3) * 6.0;
               double alignment = Math.max(0.0, (offset.x * backward.x + offset.z * backward.z) / horizontal);
               double turnCost = this.rotationCost(playerEye, point, yaw, pitch);
               double score = -turnCost + alignment * 6.0 + horizontal * 0.2 - penalty;
               double sideProjection = offset.x * side.x + offset.z * side.z;
               if (sideProjection * this.escapeSide > 0.0) {
                  if (score > preferredScore) {
                     preferredScore = score;
                     preferred = offset;
                  }
               } else if (score > alternateScore) {
                  alternateScore = score;
                  alternate = offset;
               }
            }
         }
      }

      Vec3 chosen = preferred != null ? preferred : alternate;
      return chosen == null ? this.findEscapePoint(playerEye, targetEye, target, radius + 5.0) : targetEye.add(chosen);
   }

   private void updateAttackLeg(Vec3 playerEye, Vec3 targetEye, double horizontalDistance) {
      boolean clear = visible(targetEye) && this.pathClear(playerEye, targetEye);
      if (clear) {
         this.flightPoint = targetEye;
         if (horizontalDistance <= this.lagDistance.getValue().floatValue() * 0.6) {
            this.phase = ElytraTarget.FlightPhase.LEAVING;
         }
      } else {
         Vec3 delta = targetEye.subtract(playerEye);
         double reach = Math.min(delta.length(), this.lagDistance.getValue().floatValue() + 4.0);
         Vec3 point = this.furthestSafePoint(playerEye, delta, reach);
         if (point != null && point.distanceTo(playerEye) >= 2.0) {
            this.flightPoint = point;
         } else {
            this.phase = ElytraTarget.FlightPhase.LEAVING;
         }
      }
   }

   private Vec3 findEscapePoint(Vec3 playerEye, Vec3 targetEye, LivingEntity target, double distance) {
      Vec3 backward = this.awayDirection(target, playerEye, targetEye);
      Vec3 side = this.perpendicular(backward);
      Vec3 best = null;
      double bestScore = Double.NEGATIVE_INFINITY;

      for (Vec3 direction : this.escapeDirections(backward, side)) {
         Vec3 point = this.furthestSafePoint(playerEye, direction, distance);
         if (point != null) {
            double travel = point.distanceTo(playerEye);
            if (!(travel < 1.0)) {
               Vec3 normalized = direction.normalize();
               double score = escapeScore(travel, normalized.dot(backward), normalized.y);
               if (score > bestScore) {
                  bestScore = score;
                  best = point;
               }
            }
         }
      }

      if (best != null) {
         return best;
      }

      Vec3 fallback = new Vec3(backward.x, 0.0, backward.z);
      if (fallback.lengthSqr() < 0.001) {
         fallback = new Vec3(targetEye.x - playerEye.x, 0.0, targetEye.z - playerEye.z);
      }

      if (fallback.lengthSqr() < 0.001) {
         fallback = new Vec3(1.0, 0.0, 0.0);
      }

      return playerEye.add(fallback.normalize().scale(2.0));
   }

   private static int sampleCount(double distance, double stepSize) {
      return Math.max(2, (int)Math.ceil(distance / stepSize));
   }

   private boolean isColliding() {
      if (mc.player == null || mc.level == null) {
         return false;
      }

      if (mc.player.horizontalCollision) {
         return true;
      }

      AABB bounds = mc.player.getBoundingBox().inflate(0.02);
      return !mc.level.noCollision(mc.player, bounds);
   }

   private void bufferPackets() {
      im.yaHustler.port.PacketBufferManager.INSTANCE.acquire("target");
   }

   private void updateRetreatLeg(Vec3 playerEye, Vec3 targetEye, LivingEntity target, double horizontalDistance) {
      boolean repath = this.needsRetreat
         || this.flightPoint == null
         || playerEye.distanceTo(this.flightPoint) < 2.5
         || !this.validRetreat(playerEye, targetEye, this.flightPoint);
      if (repath) {
         this.flightPoint = this.findRetreatPoint(playerEye, targetEye, target);
         this.needsRetreat = false;
      }

      if (horizontalDistance > this.lagDistance.getValue().floatValue()) {
         this.phase = ElytraTarget.FlightPhase.ATTACK;
         this.requestRocket();
      }
   }

   public enum FlightPhase {
      ATTACK,
      LEAVING;
   }
}
