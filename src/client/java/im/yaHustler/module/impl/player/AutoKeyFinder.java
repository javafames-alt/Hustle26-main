package im.yaHustler.module.impl.player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.chunk.LevelChunk;
import org.joml.Vector2f;
import im.yaHustler.setting.*;

@im.yaHustler.module.ModuleInfo(id="auto_key_finder", name="AutoKeyFinder", category=im.yaHustler.module.Category.PLAYER, description="Поиск и сбор ключ-карт из сундуков и вагонеток")
public class AutoKeyFinder extends im.yaHustler.port.PortModule {
   private static final double MAP_BORDER = 15999.0;
   private static final double MIN_TRAVEL_DIST = 64.0;
   private static final double INTERACT_RANGE = 5.0;
   private static final double INTERACT_RANGE_SQ = 25.0;
   private static final double CLUSTER_RADIUS = 10.0;
   private static final long GRIM_TICK_MS = 50L;
   private static final long OPEN_TIMEOUT_MS = 180L;
   private static final long OPEN_RETRY_MS = 50L;
   private static final long OPEN_TO_LOOT_MS = 50L;
   private static final long STEAL_VERIFY_MS = 50L;
   private static final long AIM_DELAY_MS = 0L;
   private static final int MAX_TP_ATTEMPTS = 10;
   private static final double SNAP_XZ_EPS = 0.06;
   private static final double UNDER_BEDROCK_Y = -66.5;
   private static final long STUCK_CHECK_MS = 2500L;
   private static final double STUCK_MOVE_MIN = 1.5;
   private static final long EC_CMD_COOLDOWN_MS = 2500L;
   private static final long EC_ACTION_MS = 50L;
   private static final long EC_OPEN_TIMEOUT_MS = 3500L;
   private static final long FLY_WARN_COOLDOWN_MS = 30000L;
   private static final long HEAL_COOLDOWN_MS = 61000L;
   private static final float HEAL_HEALTH_PERCENT = 0.35F;
   private static final String CANT_OPEN_HERE = "вы не можете открыть это здесь";
   private static final String CANT_OPEN_HERE2 = "Hey! Sorry, but you can't open that here.";
   private static final int COLOR_CHEST_RADIUS = 5;
   private static final int MIN_GLAZED = 3;
   private static final int MIN_WOOL = 3;
   private static final int EXPLORE_CELL = 768;
   private static final int VISITED_SECTORS_MAX = 16384;
   private static final int VISITED_EXPLORE_CELLS_MAX = 8192;
   private static final int EXPLORE_SECTOR_SAMPLE_RADIUS = 6;
   private static final int ARRIVE_SECTOR_MARK_RADIUS = 3;
   private final BooleanSetting loot0 = add(new BooleanSetting("Красная Ключ-Карта", true));
   private final BooleanSetting loot1 = add(new BooleanSetting("Зелёная Ключ-Карта", true));
   private final BooleanSetting loot2 = add(new BooleanSetting("Синяя Ключ-Карта", true));
   private final BooleanSetting loot3 = add(new BooleanSetting("Чарки", true));
   private final BooleanSetting loot4 = add(new BooleanSetting("Геплы", true));
   private final BooleanSetting saveToEc = add(new BooleanSetting("Сейвить в /ec", false));
   private final NumberSetting flySpeed = add(new NumberSetting("Скорость", 80.0, 20.0, 100.0, 5.0));
   private final List<AutoKeyFinder$LootTarget> lootQueue = new ArrayList<>();
   private final Set<Long> skippedTravelKeys = new LinkedHashSet<>();
   private final LinkedHashSet<Long> visitedSectors = new LinkedHashSet<>();
   private final LinkedHashSet<Long> visitedExploreCells = new LinkedHashSet<>();
   private final Set<Long> lootedKeys = new LinkedHashSet<>();
   public Vector2f server;
   private AutoKeyFinder$Phase phase = AutoKeyFinder$Phase.SEARCH;
   private AutoKeyFinder$LootTarget currentTarget;
   private long openAttemptTime;
   private long lastOpenPacketAt;
   private long openReadyAt;
   private long stealReadyAt;
   private int pendingStealSlot = -1;
   private ItemStack pendingStealStack = ItemStack.EMPTY;
   private int pendingStealInvCount;
   private long stealVerifyAt;
   private int stealRetries;
   private long resumeSearchAt;
   private long nextActionAt;
   private double travelTargetX;
   private double travelTargetZ;
   private boolean hasTravelTarget;
   private int travelAttempts;
   private double lastProgressX;
   private double lastProgressZ;
   private long lastProgressCheckAt;
   private int stuckStreak;
   private int approachTpAttempts;
   private boolean lootSessionActive;
   private boolean pendingEcSave;
   private long lastEcOpenCmdAt;
   private long ecActionAt;
   private long ecOpenStartedAt;
   private long lastFlyWarnAt;
   private long lastHealCmdAt;
   private double moveTargetX;
   private double moveTargetZ;
   private boolean approachStandAligned;
   private int exploreGridX;
   private int exploreGridZ;
   private boolean exploreStripForwardX = true;
   private boolean exploreGridInitialized;
   private long lastMarkedSectorKey = Long.MIN_VALUE;

   private void pullNearbyUnlootedIntoQueue() {
      for (AutoKeyFinder$LootTarget target : this.scanUnlooted()) {
         if (!(this.distanceSqTo(target) > 100.0)) {
            long key = this.targetKey(target);
            if (!this.lootedKeys.contains(key)) {
               boolean exists = false;

               for (AutoKeyFinder$LootTarget queued : this.lootQueue) {
                  if (this.targetKey(queued) == key) {
                     exists = true;
                     break;
                  }
               }

               if (!exists) {
                  this.lootQueue.add(target);
               }
            }
         }
      }

      this.lootQueue.sort(Comparator.comparingDouble(this::distanceSqTo));
   }

   private void finishHorizontalMoveToTarget(double tx, double tz) {
      this.snapExactUnderBedrock(tx, tz);
      if (mc.player != null) {
         mc.player.setDeltaMovement(Vec3.ZERO);
      }
   }

   private boolean isApproachFlyingHorizontally() {
      if (this.phase == AutoKeyFinder$Phase.APPROACH && this.currentTarget != null && mc.player != null) {
         if (this.distanceSqTo(this.currentTarget) <= 25.0) {
            return false;
         } else {
            Vec3 stand = this.getApproachStand(this.currentTarget);
            if (stand == null) {
               return false;
            } else {
               double px = mc.player.getX();
               double pz = mc.player.getZ();
               return this.approachStandAligned ? false : horizontalDist(px, pz, stand.x, stand.z) > 0.06;
            }
         }
      } else {
         return false;
      }
   }

   private double scoreExplorationDestination(double tx, double tz, double px, double pz, double border) {
      if (!this.isTravelDestinationValid(tx, tz, px, pz)) {
         return Double.NEGATIVE_INFINITY;
      } else {
         int cellX = exploreCellCoord(tx);
         int cellZ = exploreCellCoord(tz);
         if (this.visitedExploreCells.contains(exploreCellKey(cellX, cellZ))) {
            return -1000000.0;
         } else {
            double novel = this.unvisitedSectorRatio(tx, tz, 6);
            double dist = horizontalDist(px, pz, tx, tz);
            double distScore = Math.min(dist, 5000.0) / 5000.0;
            double edgeDist = Math.min(Math.min(border - Math.abs(tx), border - Math.abs(tz)), border);
            double edgeScore = 1.0 - Math.min(edgeDist, 5000.0) / 5000.0;
            return novel * 4.0 + distScore * 1.25 + edgeScore * 0.35 - this.revisitPenalty(tx, tz);
         }
      }
   }

   public boolean isPositionClear(double x, double y, double z) {
      if (mc.level != null && mc.player != null) {
         AABB box = new AABB(x - 0.4, y, z - 0.4, x + 0.4, y + 1.8, z + 0.4);
         if (!mc.level.noCollision(mc.player, box)) {
            return false;
         } else {
            BlockPos feetPos = BlockPos.containing(x, y + 0.1, z);
            BlockPos bodyPos = BlockPos.containing(x, y + 0.9, z);
            BlockPos headPos = BlockPos.containing(x, y + 1.7, z);
            return mc.level.getBlockState(feetPos).isAir() && mc.level.getBlockState(bodyPos).isAir() && mc.level.getBlockState(headPos).isAir();
         }
      } else {
         return false;
      }
   }

   private double findSafeClearY(double x, double startY, double z) {
      if (this.isPositionClear(x, startY, z)) {
         return startY;
      } else {
         for (int offset = 1; offset <= 30; offset++) {
            if (startY + offset <= 318.0 && this.isPositionClear(x, startY + offset, z)) {
               return startY + offset;
            }

            if (startY - offset >= -63.0 && this.isPositionClear(x, startY - offset, z)) {
               return startY - offset;
            }
         }

         return startY + 2.0;
      }
   }

   private static double horizontalDist(double x1, double z1, double x2, double z2) {
      double dx = x1 - x2;
      double dz = z1 - z2;
      return Math.sqrt(dx * dx + dz * dz);
   }

   private void tryOpen(AutoKeyFinder$LootTarget target) {
      if (mc.player != null && mc.level != null && mc.gameMode != null) {
         if (target.isMinecart()) {
            Entity entity = mc.level.getEntity(target.entityId);
            MinecartChest cart = entity instanceof MinecartChest ? (MinecartChest)entity : this.findMinecartNear(target.pos, 3.0);
            if (cart != null) {
               mc.gameMode.interact(mc.player, cart, new net.minecraft.world.phys.EntityHitResult(cart), InteractionHand.MAIN_HAND);
               mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
            }
         } else {
            BlockHitResult hit = this.buildChestHit(target.pos);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
         }
      }
   }

   private boolean isEcPhase() {
      return this.phase == AutoKeyFinder$Phase.EC_OPEN
         || this.phase == AutoKeyFinder$Phase.EC_CLICK_OPEN
         || this.phase == AutoKeyFinder$Phase.EC_DEPOSIT
         || this.phase == AutoKeyFinder$Phase.EC_CLOSE;
   }

   private static long aimDelay() {
      return jitter(0L, 25L);
   }

   private static long travelKey(double x, double z) {
      int ix = Mth.floor(x) >> 6;
      int iz = Mth.floor(z) >> 6;
      return sectorKey(ix, iz);
   }

   private static long sectorKey(int sx, int sz) {
      return (long)sx << 32 ^ sz & 4294967295L;
   }

   private boolean isLootBusy() {
      return this.phase == AutoKeyFinder$Phase.OPEN
         || this.phase == AutoKeyFinder$Phase.STEAL
         || this.isEcPhase()
         || this.pendingStealSlot >= 0
         || mc.gui.screen() instanceof ContainerScreen;
   }

   private boolean isSameLoot(ItemStack a, ItemStack b) {
      if (a.isEmpty() || b.isEmpty() || !a.is(b.getItem())) {
         return false;
      } else if (!a.is(Items.NAME_TAG)) {
         return true;
      } else {
         String nameA = a.getHoverName().getString();
         String nameB = b.getHoverName().getString();
         return nameA != null && nameB != null ? nameA.trim().equalsIgnoreCase(nameB.trim()) : false;
      }
   }

   private long targetKey(AutoKeyFinder$LootTarget target) {
      return target.isMinecart() && target.entityId >= 0 ? 281474976710656L | target.entityId & 4294967295L : target.pos.asLong();
   }

   private void tickEcOpen(long now) {
      this.clearRotation();
      if (now - this.ecOpenStartedAt > 3500L) {
         this.pendingEcSave = false;
         this.finishLootCycle(now);
      } else if (mc.gui.screen() instanceof ContainerScreen screen) {
         ChestMenu var6 = (ChestMenu)screen.getMenu();
         int openSlot = this.findEcOpenButtonSlot(var6);
         if (openSlot < 0) {
            this.ecActionAt = now + ecActionDelay();
            this.phase = AutoKeyFinder$Phase.EC_DEPOSIT;
         } else if (now >= this.ecActionAt) {
            mc.gameMode.handleContainerInput(var6.containerId, openSlot, 0, ContainerInput.PICKUP, mc.player);
            this.ecActionAt = now + ecActionDelay();
            this.ecOpenStartedAt = now;
            this.phase = AutoKeyFinder$Phase.EC_CLICK_OPEN;
         }
      } else {
         if (now - this.lastEcOpenCmdAt >= 2500L && mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendCommand("ec open");
            this.lastEcOpenCmdAt = now;
         }
      }
   }

   private void beginLoot(List<AutoKeyFinder$LootTarget> found) {
      AutoKeyFinder$LootTarget first = found.get(0);
      List<AutoKeyFinder$LootTarget> cluster = this.findUnlootedNear(first.pos, 10.0);
      if (cluster.isEmpty()) {
         cluster = found;
      }

      this.lootQueue.clear();

      for (AutoKeyFinder$LootTarget target : cluster) {
         long key = this.targetKey(target);
         if (!this.lootedKeys.contains(key)) {
            this.lootQueue.add(target);
         }
      }

      if (this.lootQueue.isEmpty()) {
         this.lootQueue.add(found.get(0));
      }

      this.lootQueue.sort(Comparator.comparingDouble(this::distanceSqTo));
      this.dedupeQueue();
      if (this.lootQueue.isEmpty()) {
         this.finishLootCycle(System.currentTimeMillis());
      } else {
         this.currentTarget = null;
         this.lootSessionActive = true;
         this.pendingEcSave = false;
         this.clearTravelTarget();
         this.lastProgressX = mc.player.getX();
         this.lastProgressZ = mc.player.getZ();
         this.lastProgressCheckAt = System.currentTimeMillis();
         this.approachTpAttempts = 0;
         this.phase = AutoKeyFinder$Phase.APPROACH;
         this.nextActionAt = 0L;
      }
   }

   private void tickOpen(long now) {
      if (mc.gui.screen() instanceof ContainerScreen) {
         this.clearRotation();
         this.clearPendingSteal();
         this.stealReadyAt = now + openToLootDelay();
         this.phase = AutoKeyFinder$Phase.STEAL;
      } else if (this.currentTarget == null) {
         this.clearRotation();
         this.phase = AutoKeyFinder$Phase.APPROACH;
      } else {
         this.refreshCurrentTarget();
         if (this.currentTarget == null) {
            this.clearRotation();
            this.phase = AutoKeyFinder$Phase.APPROACH;
         } else if (this.distanceSqTo(this.currentTarget) > 25.0) {
            this.clearRotation();
            this.phase = AutoKeyFinder$Phase.APPROACH;
         } else if (now >= this.openReadyAt) {
            this.updateServerRotation(this.currentTarget);
            if (this.openAttemptTime > 0L && now - this.openAttemptTime > 180L) {
               this.lootedKeys.add(this.targetKey(this.currentTarget));
               this.currentTarget = null;
               this.clearRotation();
               this.openAttemptTime = 0L;
               this.nextActionAt = now + aimDelay();
               this.phase = AutoKeyFinder$Phase.APPROACH;
            }
         }
      }
   }

   private boolean isHazard(BlockState state) {
      if (state.liquid()) {
         return true;
      } else if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
         return true;
      } else if (state.is(Blocks.POWDER_SNOW) || state.is(Blocks.COBWEB)) {
         return true;
      } else {
         return state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
            ? true
            : state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE);
      }
   }

   private void markSector(int sx, int sz) {
      this.visitedSectors.add(sectorKey(sx, sz));
      this.trimVisitedSectors();
   }

   private void tickSteal(long now) {
      this.clearRotation();
      if (mc.gui.screen() instanceof ContainerScreen containerScreen) {
         ChestMenu var7 = (ChestMenu)containerScreen.getMenu();
         if (now >= this.stealReadyAt) {
            if (this.pendingStealSlot >= 0) {
               if (now < this.stealVerifyAt) {
                  return;
               }

               if (!this.isStealConfirmed(var7, this.pendingStealSlot, this.pendingStealStack, this.pendingStealInvCount)) {
                  this.stealRetries++;
                  if (this.stealRetries > 5) {
                     this.clearPendingSteal();
                     this.finishStealTarget(now);
                     return;
                  }

                  this.clickStealSlot(var7, this.pendingStealSlot);
                  this.pendingStealInvCount = this.countMatchingInPlayerInv(this.pendingStealStack);
                  this.stealVerifyAt = now + stealVerifyDelay();
                  return;
               }

               this.pendingEcSave = true;
               this.clearPendingSteal();
            }

            int slot = this.findNextWantedSlot(var7);
            if (slot == -1) {
               this.clearPendingSteal();
               this.finishStealTarget(now);
            } else {
               ItemStack stack = var7.getSlot(slot).getItem().copy();
               this.pendingStealSlot = slot;
               this.pendingStealStack = stack;
               this.pendingStealInvCount = this.countMatchingInPlayerInv(stack);
               this.stealRetries = 0;
               this.clickStealSlot(var7, slot);
               this.stealVerifyAt = now + stealVerifyDelay();
            }
         }
      } else {
         this.clearPendingSteal();
         this.finishStealTarget(now);
      }
   }

   private void clipYOnly(double x, double y, double z) {
      this.teleportTo(x, y, z);
   }

   private void tickTravel(long now) {
      List<AutoKeyFinder$LootTarget> found = this.scanUnlooted();
      if (!found.isEmpty()) {
         this.clearTravelTarget();
         this.beginLoot(found);
      } else if (!this.hasTravelTarget) {
         this.phase = AutoKeyFinder$Phase.SEARCH;
         this.nextActionAt = now + actionDelay();
      } else if (!this.isUnderBedrock()) {
         this.teleportUnderBedrock();
         this.updateMoveTargetXZ(this.travelTargetX, this.travelTargetZ);
      } else {
         this.updateMoveTargetXZ(this.travelTargetX, this.travelTargetZ);
         double px = mc.player.getX();
         double pz = mc.player.getZ();
         this.markTravelProgress(px, pz);
         double distToFinal = horizontalDist(px, pz, this.travelTargetX, this.travelTargetZ);
         double flySpeed = this.getFlySpeedXZ();
         if (!(distToFinal <= 0.06) && !(distToFinal <= flySpeed)) {
            if (now - this.lastProgressCheckAt >= 2500L) {
               double moved = horizontalDist(this.lastProgressX, this.lastProgressZ, px, pz);
               this.lastProgressX = px;
               this.lastProgressZ = pz;
               this.lastProgressCheckAt = now;
               if (moved < 1.5) {
                  this.stuckStreak++;
                  if (this.stuckStreak >= 2) {
                     this.handleTravelFail(now);
                     return;
                  }
               } else {
                  this.stuckStreak = 0;
               }
            }
         } else {
            this.finishHorizontalMoveToTarget(this.travelTargetX, this.travelTargetZ);
            px = mc.player.getX();
            pz = mc.player.getZ();
            this.markExploredArea(this.travelTargetX, this.travelTargetZ);
            double[] next = this.pickExplorationDestination(15999.0);
            if (next != null) {
               this.travelTargetX = next[0];
               this.travelTargetZ = next[1];
               this.travelAttempts = 1;
               this.lastProgressX = px;
               this.lastProgressZ = pz;
               this.lastProgressCheckAt = now;
               this.stuckStreak = 0;
               this.updateMoveTargetXZ(this.travelTargetX, this.travelTargetZ);
            } else {
               this.clearTravelTarget();
               this.clearFlyMove();
               this.phase = AutoKeyFinder$Phase.SEARCH;
               this.nextActionAt = 0L;
            }
         }
      }
   }

   @Override
   public void tick(net.minecraft.client.Minecraft client) {
      if (mc.player != null && mc.level != null) {
         if (!mc.player.isDeadOrDying() && !(mc.player.getHealth() <= 0.0F)) {
            if (this.isAutoFlyMove()) this.applyDirectFlyMotion();

            if (this.server != null) {
               mc.player.setYRot(this.server.x);
               mc.player.setXRot(this.server.y);
               mc.player.yHeadRot = this.server.x;
               mc.player.yBodyRot = this.server.x;
               if (mc.player.getAbilities().flying
                  && this.phase == AutoKeyFinder$Phase.OPEN
                  && this.currentTarget != null
                  && !(mc.gui.screen() instanceof ContainerScreen)) {
                  long now = System.currentTimeMillis();
                  if (now >= this.openReadyAt && now - this.lastOpenPacketAt >= openRetryDelay()) {
                     this.tryOpen(this.currentTarget);
                     this.lastOpenPacketAt = now;
                     if (this.openAttemptTime == 0L) {
                        this.openAttemptTime = now;
                     }
                  }
               }
            }

            {
               if (mc.player.getY() < -66.5) {
                  mc.player.setPos(mc.player.getX(), -66.5, mc.player.getZ());
               }

               long now = System.currentTimeMillis();
               this.tryHealIfNeeded(now);
               if (!mc.player.getAbilities().flying) {
                  this.clearRotation();
                  this.clearTravelTarget();
                  if (now - this.lastFlyWarnAt >= 30000L) {
                     mc.gui.chatListener().handleSystemMessage(net.minecraft.network.chat.Component.literal("Для использования модуля нужно /fly"), false);
                     this.lastFlyWarnAt = now;
                  }

                  return;
               }

               if (now < this.resumeSearchAt) {
                  return;
               }

               if (this.isLootBusy() && (this.phase == AutoKeyFinder$Phase.SEARCH || this.phase == AutoKeyFinder$Phase.TRAVEL) && !this.isEcPhase()) {
                  this.clearTravelTarget();
                  if (mc.gui.screen() instanceof ContainerScreen) {
                     this.phase = AutoKeyFinder$Phase.STEAL;
                     this.stealReadyAt = Math.max(this.stealReadyAt, now);
                  }
               }

               switch (this.phase) {
                  case SEARCH:
                     this.startTravel(now);
                     break;
                  case TRAVEL:
                     this.tickTravel(now);
                     break;
                  case APPROACH:
                     this.tickApproach(now);
                     break;
                  case OPEN:
                     this.tickOpen(now);
                     break;
                  case STEAL:
                     this.tickSteal(now);
                     break;
                  case EC_OPEN:
                     this.tickEcOpen(now);
                     break;
                  case EC_CLICK_OPEN:
                     this.tickEcClickOpen(now);
                     break;
                  case EC_DEPOSIT:
                     this.tickEcDeposit(now);
                     break;
                  case EC_CLOSE:
                     this.tickEcClose(now);
               }
            }
         } else {
            this.setEnabled(false);
         }
      }
   }

   @Override
   protected void onDisable() {
      if (mc.player != null && mc.gui.screen() instanceof ContainerScreen) {
         mc.player.closeContainer();
      }

      this.resetState();
   }

   @Override
   protected void onEnable() {
      this.resetState();
      this.phase = AutoKeyFinder$Phase.SEARCH;
      this.teleportUnderBedrock();
   }

   private static long jitter(long base, long range) {
      return base + ThreadLocalRandom.current().nextLong(range + 1L);
   }

   private Vec3 aimPoint(AutoKeyFinder$LootTarget target) {
      return target.isMinecart() && mc.level != null && mc.level.getEntity(target.entityId) instanceof MinecartChest cart
         ? new Vec3(cart.getX(), cart.getY() + 0.5, cart.getZ())
         : new Vec3(target.pos.getX() + 0.5, target.pos.getY() + 0.5, target.pos.getZ() + 0.5);
   }

   private void teleportTo(double x, double y, double z) {
      if (mc.player != null && mc.player.connection != null) {
         double targetY = Math.max(y, -66.5);
         mc.player.setPos(x, targetY, z);
         mc.player.connection.send(new Pos(x, targetY, z, false, false));
      }
   }

   public AutoKeyFinder() {

   }

   private void resetState() {
      this.lootQueue.clear();
      this.currentTarget = null;
      this.clearRotation();
      this.openAttemptTime = 0L;
      this.openReadyAt = 0L;
      this.stealReadyAt = 0L;
      this.clearPendingSteal();
      this.resumeSearchAt = 0L;
      this.nextActionAt = 0L;
      this.lastOpenPacketAt = 0L;
      this.clearTravelTarget();
      this.approachTpAttempts = 0;
      this.approachStandAligned = false;
      this.lootSessionActive = false;
      this.pendingEcSave = false;
      this.lastEcOpenCmdAt = 0L;
      this.ecActionAt = 0L;
      this.ecOpenStartedAt = 0L;
      this.lastFlyWarnAt = 0L;
      this.lastHealCmdAt = 0L;
      this.clearFlyMove();
      this.skippedTravelKeys.clear();
      this.visitedSectors.clear();
      this.visitedExploreCells.clear();
      this.exploreGridInitialized = false;
      this.exploreStripForwardX = true;
      this.lastMarkedSectorKey = Long.MIN_VALUE;
      this.lootedKeys.clear();
      this.phase = AutoKeyFinder$Phase.SEARCH;
   }

   private boolean isAutoFlyMove() {
      if (mc.player == null || !mc.player.getAbilities().flying || this.isLootBusy()) {
         return false;
      } else {
         return this.phase == AutoKeyFinder$Phase.TRAVEL && this.hasTravelTarget ? true : this.isApproachFlyingHorizontally();
      }
   }

   public double getUnderBedrockY() {
      return -66.5;
   }

   private static long openRetryDelay() {
      return jitter(50L, 15L);
   }

   private List<AutoKeyFinder$LootTarget> scanUnlooted() {
      List<AutoKeyFinder$LootTarget> all = this.findUnlooted();
      List<AutoKeyFinder$LootTarget> filtered = new ArrayList<>();

      for (AutoKeyFinder$LootTarget target : all) {
         if (!this.lootedKeys.contains(this.targetKey(target))) {
            filtered.add(target);
         }
      }

      filtered.sort(Comparator.comparingDouble(this::distanceSqTo));
      return filtered;
   }

   private void markExploredArea(double x, double z) {
      this.markSectorsAround(x, z, 3);
      int cx = exploreCellCoord(x);
      int cz = exploreCellCoord(z);
      this.visitedExploreCells.add(exploreCellKey(cx, cz));
      this.trimVisitedExploreCells();
   }

   private void skipCurrentTargetInstant() {
      if (mc.player != null && mc.gui.screen() instanceof ContainerScreen) {
         mc.player.closeContainer();
      }

      this.clearPendingSteal();
      this.clearRotation();
      if (this.currentTarget != null) {
         this.lootedKeys.add(this.targetKey(this.currentTarget));
      }

      this.currentTarget = null;
      this.openAttemptTime = 0L;
      this.approachTpAttempts = 0;
      this.approachStandAligned = false;
      this.clearFlyMove();
      this.nextActionAt = System.currentTimeMillis() + aimDelay();
      this.phase = AutoKeyFinder$Phase.APPROACH;
   }

   private double distanceSqTo(AutoKeyFinder$LootTarget target) {
      if (mc.player == null) {
         return Double.MAX_VALUE;
      } else {
         Vec3 center = this.aimPoint(target);
         return mc.player.distanceToSqr(center);
      }
   }

   private void updateMoveTargetXZ(double x, double z) {
      this.moveTargetX = x;
      this.moveTargetZ = z;
   }

   private void clearPendingSteal() {
      this.pendingStealSlot = -1;
      this.pendingStealStack = ItemStack.EMPTY;
      this.pendingStealInvCount = 0;
      this.stealVerifyAt = 0L;
      this.stealRetries = 0;
   }

   private static long openToLootDelay() {
      return jitter(50L, 15L);
   }

   private void startTravel(long now) {
      if (now >= this.nextActionAt) {
         if (!this.lootSessionActive && !this.isLootBusy()) {
            if (!this.isUnderBedrock()) {
               this.teleportUnderBedrock();
               this.nextActionAt = now + actionDelay();
            } else {
               List<AutoKeyFinder$LootTarget> found = this.scanUnlooted();
               if (!found.isEmpty()) {
                  this.clearTravelTarget();
                  this.beginLoot(found);
               } else {
                  double[] dest = this.pickExplorationDestination(15999.0);
                  if (dest == null) {
                     this.nextActionAt = now + actionDelay();
                  } else {
                     this.beginTravelTo(dest[0], dest[1], now, true);
                  }
               }
            }
         }
      }
   }

   private void tickApproach(long now) {
      if (now >= this.nextActionAt) {
         if (!(mc.gui.screen() instanceof ContainerScreen) && this.pendingStealSlot < 0) {
            if (this.currentTarget == null) {
               if (this.lootQueue.isEmpty()) {
                  this.continueAfterChestLoot(now);
                  return;
               }

               this.currentTarget = this.lootQueue.remove(0);
               this.approachTpAttempts = 0;
               this.approachStandAligned = false;
            }

            this.refreshCurrentTarget();
            if (this.currentTarget == null) {
               this.clearRotation();
               this.clearFlyMove();
               this.approachTpAttempts = 0;
               this.approachStandAligned = false;
            } else {
               double distSq = this.distanceSqTo(this.currentTarget);
               if (distSq > 25.0) {
                  this.clearRotation();
                  if (this.isLootBusy()) {
                     this.clearFlyMove();
                  } else {
                     Vec3 stand = this.getApproachStand(this.currentTarget);
                     if (stand == null) {
                        this.lootedKeys.add(this.targetKey(this.currentTarget));
                        this.currentTarget = null;
                        this.clearFlyMove();
                        this.approachTpAttempts = 0;
                        this.nextActionAt = now + actionDelay();
                     } else {
                        double destX = stand.x;
                        double destY = stand.y;
                        double destZ = stand.z;
                        double underY = this.getUnderBedrockY();
                        double px = mc.player.getX();
                        double pz = mc.player.getZ();
                        if (!this.isUnderBedrock()) {
                           this.clipYOnly(px, underY, pz);
                           this.approachStandAligned = false;
                        } else if (!this.approachStandAligned) {
                           this.updateMoveTargetXZ(destX, destZ);
                           double hDist = horizontalDist(px, pz, destX, destZ);
                           double flySpeed = this.getFlySpeedXZ();
                           if (!(hDist <= 0.06) && !(hDist <= flySpeed)) {
                              if (now - this.lastProgressCheckAt >= 2500L) {
                                 double moved = horizontalDist(this.lastProgressX, this.lastProgressZ, px, pz);
                                 this.lastProgressX = px;
                                 this.lastProgressZ = pz;
                                 this.lastProgressCheckAt = now;
                                 if (moved < 1.5) {
                                    this.approachTpAttempts++;
                                    if (this.approachTpAttempts >= 10) {
                                       this.lootedKeys.add(this.targetKey(this.currentTarget));
                                       this.currentTarget = null;
                                       this.clearFlyMove();
                                       this.approachTpAttempts = 0;
                                       this.approachStandAligned = false;
                                       this.nextActionAt = now + actionDelay();
                                       return;
                                    }
                                 } else if (this.approachTpAttempts > 0) {
                                    this.approachTpAttempts = 0;
                                 }
                              }
                           } else {
                              this.clearFlyMove();
                              this.finishHorizontalMoveToTarget(destX, destZ);
                              this.approachStandAligned = true;
                           }
                        } else if (!this.isExactMoveTargetXZ(mc.player.getX(), mc.player.getZ())) {
                           this.finishHorizontalMoveToTarget(destX, destZ);
                        } else {
                           this.clearFlyMove();
                           mc.player.setDeltaMovement(Vec3.ZERO);
                           this.clipYOnly(destX, destY, destZ);
                        }
                     }
                  }
               } else {
                  this.clearFlyMove();
                  this.approachTpAttempts = 0;
                  this.approachStandAligned = false;
                  this.phase = AutoKeyFinder$Phase.OPEN;
                  this.openAttemptTime = 0L;
                  this.lastOpenPacketAt = 0L;
                  this.openReadyAt = now;
               }
            }
         } else {
            this.clearRotation();
            if (this.pendingStealSlot < 0) {
               this.clearPendingSteal();
               this.stealReadyAt = now + openToLootDelay();
            }

            this.phase = AutoKeyFinder$Phase.STEAL;
         }
      }
   }

   private void tickEcClickOpen(long now) {
      this.clearRotation();
      if (now - this.ecOpenStartedAt > 3500L) {
         this.pendingEcSave = false;
         this.finishLootCycle(now);
      } else if (mc.gui.screen() instanceof ContainerScreen screen) {
         String title = screen.getTitle().getString();
         if (title != null && title.contains("Эндер-сундук")) {
            this.ecActionAt = now + ecActionDelay();
            this.phase = AutoKeyFinder$Phase.EC_DEPOSIT;
         }
      }
   }

   public void teleportUnderBedrock() {
      if (mc.player != null && !this.isUnderBedrock()) {
         this.teleportTo(mc.player.getX(), -66.5, mc.player.getZ());
      }
   }

   private void tickEcClose(long now) {
      this.clearRotation();
      if (now >= this.ecActionAt) {
         if (mc.player != null && mc.gui.screen() instanceof ContainerScreen) {
            mc.player.closeContainer();
            this.ecActionAt = now + aimDelay();
         } else {
            this.pendingEcSave = false;
            this.finishLootCycle(now);
         }
      }
   }

   private Vec3 getApproachStand(AutoKeyFinder$LootTarget target) {
      if (mc.player != null && mc.level != null) {
         Vec3 targetCenter = this.aimPoint(target);
         return this.findSafeStandBeside(target.pos, targetCenter);
      } else {
         return null;
      }
   }

   private void tickEcDeposit(long now) {
      this.clearRotation();
      if (mc.gui.screen() instanceof ContainerScreen containerScreen) {
         if (now >= this.ecActionAt) {
            this.depositWantedToEc((ChestMenu)containerScreen.getMenu());
            this.ecActionAt = now + ecActionDelay();
            this.phase = AutoKeyFinder$Phase.EC_CLOSE;
         }
      } else {
         if (this.hasWantedInPlayerInv() && now - this.lastEcOpenCmdAt >= 2500L) {
            this.ecOpenStartedAt = now;
            this.phase = AutoKeyFinder$Phase.EC_OPEN;
         } else {
            this.pendingEcSave = false;
            this.finishLootCycle(now);
         }
      }
   }

   private void clearFlyMove() {
   }

   private static long stealVerifyDelay() {
      return jitter(50L, 15L);
   }

   private void beginTravelTo(double x, double z, long now, boolean resetAttempts) {
      if (!this.lootSessionActive && !this.isLootBusy()) {
         this.travelTargetX = x;
         this.travelTargetZ = z;
         this.hasTravelTarget = true;
         if (resetAttempts) {
            this.travelAttempts = 1;
         }

         this.lastProgressX = mc.player.getX();
         this.lastProgressZ = mc.player.getZ();
         this.lastProgressCheckAt = now;
         this.stuckStreak = 0;
         this.phase = AutoKeyFinder$Phase.TRAVEL;
         this.updateMoveTargetXZ(this.travelTargetX, this.travelTargetZ);
         if (!this.isUnderBedrock()) {
            this.teleportUnderBedrock();
         }

         this.nextActionAt = 0L;
      }
   }

   public Vec3 overrideMovement(Vec3 original) {
      if (mc.player == null || mc.level == null || !mc.player.getAbilities().flying || !isAutoFlyMove()) return original;
      applyDirectFlyMotion();
      return mc.player.getDeltaMovement();
   }

   private void applyDirectFlyMotion() {
      if (mc.player != null) {
         double px = mc.player.getX();
         double pz = mc.player.getZ();
         if (this.isExactMoveTargetXZ(px, pz)) {
            mc.player.setDeltaMovement(0.0, 0.0, 0.0);
         } else {
            double dx = this.moveTargetX - px;
            double dz = this.moveTargetZ - pz;
            double hDist = Math.hypot(dx, dz);
            if (hDist < 1.0E-6) {
               mc.player.setDeltaMovement(0.0, 0.0, 0.0);
            } else {
               double speed = this.getFlySpeedXZ();
               if (hDist <= speed) {
                  mc.player.setDeltaMovement(dx, 0.0, dz);
               } else {
                  mc.player.setDeltaMovement(dx / hDist * speed, 0.0, dz / hDist * speed);
               }
            }
         }
      }
   }

   private static long exploreCellKey(int cx, int cz) {
      return (long)cx << 32 ^ cz & 4294967295L;
   }

   private List<AutoKeyFinder$LootTarget> findUnlootedNear(BlockPos center, double radius) {
      List<AutoKeyFinder$LootTarget> nearby = new ArrayList<>();
      double radiusSq = radius * radius;

      for (AutoKeyFinder$LootTarget target : this.findUnlooted()) {
         double dx = target.pos.getX() + 0.5 - (center.getX() + 0.5);
         double dy = target.pos.getY() + 0.5 - (center.getY() + 0.5);
         double dz = target.pos.getZ() + 0.5 - (center.getZ() + 0.5);
         if (dx * dx + dy * dy + dz * dz <= radiusSq) {
            nearby.add(target);
         }
      }

      return nearby;
   }

   private void continueAfterChestLoot(long now) {
      this.pullNearbyUnlootedIntoQueue();
      if (!this.lootQueue.isEmpty()) {
         this.phase = AutoKeyFinder$Phase.APPROACH;
      } else if (this.shouldSaveToEc()) {
         this.startEcSave(now);
      } else {
         this.finishLootCycle(now);
      }
   }

   private Vector2f rotationTo(Vec3 point) {
      Vec3 delta = point.subtract(mc.player.getEyePosition());
      return new Vector2f((float)Math.toDegrees(Math.atan2(delta.z, delta.x))-90F,
          (float)-Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
   }

   @Override public void onPacketReceive(im.yaHustler.port.event.EventPacketReceive event) {
      if (mc.player != null && event.getPacket() instanceof ClientboundSystemChatPacket packet) handleCantOpenChat(packet);
   }
   @Override public void onDisconnect(im.yaHustler.port.event.EventDisconnect event) { resetState(); }

   private void updateServerRotation(AutoKeyFinder$LootTarget target) {
      this.server = rotationTo(this.aimPoint(target));
   }

   private void finishStealTarget(long now) {
      this.clearPendingSteal();
      if (mc.player != null && mc.gui.screen() instanceof ContainerScreen) {
         mc.player.closeContainer();
      }

      if (this.currentTarget != null) {
         this.lootedKeys.add(this.targetKey(this.currentTarget));
      }

      this.currentTarget = null;
      this.openAttemptTime = 0L;
      this.nextActionAt = now + aimDelay();
      this.continueAfterChestLoot(now);
   }

   private float getFlySpeedXZ() {
      return this.flySpeed.get().floatValue() / 20.0F;
   }

   private static long ecActionDelay() {
      return jitter(50L, 15L);
   }

   private void tryHealIfNeeded(long now) {
      float maxHealth = mc.player.getMaxHealth();
      if (!(maxHealth <= 0.0F)) {
         float percent = mc.player.getHealth() / maxHealth;
         if (!(percent >= 0.35F)) {
            if (now - this.lastHealCmdAt >= 61000L) {
               if (mc.player.connection != null) {
                  mc.player.connection.sendCommand("heal");
                  this.lastHealCmdAt = now;
               }
            }
         }
      }
   }

   private void handleTravelFail(long now) {
      if (this.travelAttempts >= 10) {
         this.skippedTravelKeys.add(travelKey(this.travelTargetX, this.travelTargetZ));
         if (this.skippedTravelKeys.size() > 64) {
            this.skippedTravelKeys.clear();
         }

         this.clearTravelTarget();
         this.phase = AutoKeyFinder$Phase.SEARCH;
         this.nextActionAt = now + actionDelay();
      } else {
         this.travelAttempts++;
         this.beginTravelTo(this.travelTargetX, this.travelTargetZ, now, false);
      }
   }

   private void finishLootCycle(long now) {
      this.currentTarget = null;
      this.lootQueue.clear();
      this.clearRotation();
      this.clearPendingSteal();
      this.lootSessionActive = false;
      this.pendingEcSave = false;
      this.clearTravelTarget();
      this.resumeSearchAt = now + aimDelay();
      this.nextActionAt = this.resumeSearchAt;
      this.phase = AutoKeyFinder$Phase.SEARCH;
   }

   private void dedupeQueue() {
      Set<Long> seen = new LinkedHashSet<>();
      Iterator<AutoKeyFinder$LootTarget> it = this.lootQueue.iterator();

      while (it.hasNext()) {
         AutoKeyFinder$LootTarget target = it.next();
         long key = this.targetKey(target);
         if (!seen.add(key)) {
            it.remove();
         }
      }
   }

   private boolean isExactMoveTargetXZ(double x, double z) {
      return horizontalDist(x, z, this.moveTargetX, this.moveTargetZ) <= 0.06;
   }

   private void refreshCurrentTarget() {
      if (this.currentTarget != null && mc.level != null) {
         if (this.currentTarget.isMinecart()) {
            if (mc.level.getEntity(this.currentTarget.entityId) instanceof MinecartChest cart) {
               this.currentTarget = new AutoKeyFinder$LootTarget(AutoKeyFinder$LootKind.MINECART, cart.blockPosition(), cart.getId());
            } else {
               MinecartChest nearby = this.findMinecartNear(this.currentTarget.pos, 3.0);
               if (nearby != null) {
                  this.currentTarget = new AutoKeyFinder$LootTarget(AutoKeyFinder$LootKind.MINECART, nearby.blockPosition(), nearby.getId());
               } else {
                  this.currentTarget = null;
               }
            }
         } else {
            if (!(mc.level.getBlockState(this.currentTarget.pos).getBlock() instanceof ChestBlock)) {
               this.currentTarget = null;
            }
         }
      }
   }

   public boolean isUnderBedrock() {
      return mc.player == null ? false : Math.abs(mc.player.getY() - -66.5) <= 1.5;
   }

   private boolean isStealConfirmed(ChestMenu container, int slot, ItemStack expected, int invCountBefore) {
      if (this.countMatchingInPlayerInv(expected) > invCountBefore) {
         return true;
      } else {
         int chestSize = container.getRowCount() * 9;
         if (slot >= 0 && slot < chestSize) {
            ItemStack inChest = container.getSlot(slot).getItem();
            if (inChest.isEmpty()) {
               return true;
            } else {
               return !this.isSameLoot(inChest, expected) ? true : inChest.getCount() < expected.getCount();
            }
         } else {
            return true;
         }
      }
   }

   private void clickStealSlot(ChestMenu container, int slot) {
      if (mc.gameMode != null && mc.player != null) {
         mc.gameMode.handleContainerInput(container.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);
      }
   }

   private static int exploreCellCoord(double coord) {
      return Mth.floor(coord / 768.0);
   }

   private int countMatchingInPlayerInv(ItemStack sample) {
      if (mc.player != null && sample != null && !sample.isEmpty()) {
         int count = 0;
         Inventory inv = mc.player.getInventory();

         for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && this.isSameLoot(stack, sample)) {
               count += stack.getCount();
            }
         }

         return count;
      } else {
         return 0;
      }
   }

   private void markTravelProgress(double x, double z) {
      long key = travelKey(x, z);
      if (key != this.lastMarkedSectorKey) {
         this.lastMarkedSectorKey = key;
         this.markSector(Mth.floor(x) >> 6, Mth.floor(z) >> 6);
      }
   }

   private void clearRotation() {
      this.server = null;
   }

   private double[] pickExplorationDestination(double border) {
      if (mc.player == null) {
         return null;
      } else {
         double px = mc.player.getX();
         double pz = mc.player.getZ();
         if (!this.exploreGridInitialized) {
            this.initExploreGrid(px, pz);
         }

         int gMin = this.exploreGridMin(border);
         int gMax = this.exploreGridMax(border);
         if (this.exploreGridZ > gMax) {
            this.resetExploreSweep(border);
         }

         int scanX = this.exploreGridX;
         int scanZ = this.exploreGridZ;
         boolean scanForwardX = this.exploreStripForwardX;

         for (int step = 0; step < 512 && scanZ <= gMax; step++) {
            if (scanX >= gMin && scanX <= gMax && !this.visitedExploreCells.contains(exploreCellKey(scanX, scanZ))) {
               double[] dest = this.exploreCellCenter(scanX, scanZ, border);
               if (this.isTravelDestinationValid(dest[0], dest[1], px, pz) && this.unvisitedSectorRatio(dest[0], dest[1], 4) >= 0.35) {
                  this.exploreGridX = scanX;
                  this.exploreGridZ = scanZ;
                  this.exploreStripForwardX = scanForwardX;
                  this.stepExploreCursor(border);
                  return dest;
               }
            }

            if (scanForwardX) {
               if (++scanX > gMax) {
                  scanX = gMax;
                  scanZ++;
                  scanForwardX = false;
               }
            } else if (--scanX < gMin) {
               scanX = gMin;
               scanZ++;
               scanForwardX = true;
            }
         }

         double[] best = null;
         double bestScore = Double.NEGATIVE_INFINITY;

         for (int attempt = 0; attempt < 64; attempt++) {
            double[] dest = this.pickBorderDestination(border);
            if (dest != null) {
               double score = this.scoreExplorationDestination(dest[0], dest[1], px, pz, border);
               if (score > bestScore) {
                  bestScore = score;
                  best = dest;
               }
            }
         }

         for (int attemptx = 0; attemptx < 32; attemptx++) {
            int cx = ThreadLocalRandom.current().nextInt(gMin, gMax + 1);
            int cz = ThreadLocalRandom.current().nextInt(gMin, gMax + 1);
            if (!this.visitedExploreCells.contains(exploreCellKey(cx, cz))) {
               double[] dest = this.exploreCellCenter(cx, cz, border);
               double score = this.scoreExplorationDestination(dest[0], dest[1], px, pz, border);
               if (score > bestScore) {
                  bestScore = score;
                  best = dest;
               }
            }
         }

         if (best != null && bestScore > -100000.0) {
            this.exploreGridX = exploreCellCoord(best[0]);
            this.exploreGridZ = exploreCellCoord(best[1]);
            this.stepExploreCursor(border);
            return best;
         } else {
            return this.pickBorderDestination(border);
         }
      }
   }

   private static long actionDelay() {
      return jitter(0L, 25L);
   }

   private void snapExactUnderBedrock(double tx, double tz) {
      this.teleportTo(tx, this.getUnderBedrockY(), tz);
   }

   private void handleCantOpenChat(ClientboundSystemChatPacket packet) {
      if (this.phase == AutoKeyFinder$Phase.OPEN || this.phase == AutoKeyFinder$Phase.APPROACH || this.phase == AutoKeyFinder$Phase.STEAL) {
         if (this.currentTarget != null) {
            String text = packet.content().getString();
            if (text != null) {
               String lower = text.toLowerCase(Locale.ROOT);
               if (lower.contains("вы не можете открыть это здесь") || lower.contains("hey! sorry, but you can't open that here.")) {
                  this.skipCurrentTargetInstant();
               }
            }
         }
      }
   }

   private void clearTravelTarget() {
      this.hasTravelTarget = false;
      this.travelAttempts = 0;
      this.lastProgressCheckAt = 0L;
      this.stuckStreak = 0;
      if (this.phase == AutoKeyFinder$Phase.TRAVEL) {
         this.clearFlyMove();
      }
   }

   private void trimVisitedSectors() {
      while (this.visitedSectors.size() > 16384) {
         Iterator<Long> it = this.visitedSectors.iterator();
         if (it.hasNext()) {
            it.next();
            it.remove();
            continue;
         }
         break;
      }
   }

   private void startEcSave(long now) {
      this.clearRotation();
      this.clearTravelTarget();
      this.ecOpenStartedAt = now;
      this.ecActionAt = 0L;
      this.phase = AutoKeyFinder$Phase.EC_OPEN;
      this.nextActionAt = now;
   }

   private int findEcOpenButtonSlot(ChestMenu handler) {
      int size = handler.getRowCount() * 9;

      for (int i = 0; i < size; i++) {
         ItemStack stack = handler.getSlot(i).getItem();
         if (!stack.isEmpty()) {
            String name = stack.getHoverName().getString();
            if (name != null && name.contains("Открыть Эндер-сундук")) {
               return i;
            }
         }
      }

      return -1;
   }

   private List<BlockPos> findSingleChestsNearby(BlockPos spawnerPos) {
      List<BlockPos> chests = new ArrayList<>();
      int radius = 3;

      for (int x = -radius; x <= radius; x++) {
         for (int y = -radius; y <= radius; y++) {
            for (int z = -radius; z <= radius; z++) {
               BlockPos checkPos = spawnerPos.offset(x, y, z);
               if (this.isSingleChest(checkPos)) {
                  chests.add(checkPos.immutable());
               }
            }
         }
      }

      return chests;
   }

   private boolean isColoredLootChest(BlockPos chestPos) {
      if (mc.level == null) {
         return false;
      } else if (!this.isSingleChest(chestPos)) {
         return false;
      } else if (this.hasOtherChestNearby(chestPos, 5)) {
         return false;
      } else {
         int glazed = 0;
         int wool = 0;

         for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
               for (int z = -5; z <= 5; z++) {
                  if (x != 0 || y != 0 || z != 0) {
                     Block block = mc.level.getBlockState(chestPos.offset(x, y, z)).getBlock();
                     if (block == Blocks.GLAZED_TERRACOTTA.green()) {
                        glazed++;
                     } else if (block == Blocks.WOOL.green()) {
                        wool++;
                     }
                  }
               }
            }
         }

         return glazed >= 3 && wool >= 3;
      }
   }

   private boolean hasWantedInPlayerInv() {
      if (mc.player == null) {
         return false;
      } else {
         Inventory inv = mc.player.getInventory();

         for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && this.isWantedLoot(stack)) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean hasOtherChestNearby(BlockPos chestPos, int radius) {
      if (mc.level == null) {
         return false;
      } else {
         for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
               for (int z = -radius; z <= radius; z++) {
                  if (x != 0 || y != 0 || z != 0) {
                     BlockPos check = chestPos.offset(x, y, z);
                     if (mc.level.getBlockState(check).getBlock() instanceof ChestBlock) {
                        return true;
                     }
                  }
               }
            }
         }

         return false;
      }
   }

   private boolean isPlayerWasNear(BlockPos pos) {
      if (mc.level == null) {
         return false;
      } else {
         int radius = 6;

         for (int x = -radius; x <= radius; x += 2) {
            for (int y = -radius; y <= radius; y += 2) {
               for (int z = -radius; z <= radius; z += 2) {
                  if (mc.level.getBlockState(pos.offset(x, y, z)).isAir()) {
                     return true;
                  }
               }
            }
         }

         return false;
      }
   }

   private void depositWantedToEc(ChestMenu container) {
      int chestSize = container.getRowCount() * 9;
      int totalSlots = container.slots.size();

      for (int i = chestSize; i < totalSlots; i++) {
         ItemStack stack = container.getSlot(i).getItem();
         if (!stack.isEmpty() && this.isWantedLoot(stack)) {
            mc.gameMode.handleContainerInput(container.containerId, i, 0, ContainerInput.QUICK_MOVE, mc.player);
         }
      }
   }

   private int getSpawnerDelay(SpawnerBlockEntity spawner) {
      try {
         var output = net.minecraft.world.level.storage.TagValueOutput.createWithoutContext(net.minecraft.util.ProblemReporter.DISCARDING);
         spawner.getSpawner().save(output);
         return output.buildResult().getShortOr("Delay", (short)20);
      } catch (Exception var3) {
         return 20;
      }
   }

   private boolean isSingleChest(BlockPos pos) {
      if (mc.level == null) {
         return false;
      } else {
         BlockState state = mc.level.getBlockState(pos);
         return state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) == ChestType.SINGLE;
      }
   }

   private List<AutoKeyFinder$LootTarget> findUnlooted() {
      List<AutoKeyFinder$LootTarget> targets = new ArrayList<>();
      if (mc.level != null && mc.player != null) {
         int px = mc.player.getBlockX();
         int pz = mc.player.getBlockZ();
         int chunkRadius = 6;
         int startX = (px >> 4) - chunkRadius;
         int endX = (px >> 4) + chunkRadius;
         int startZ = (pz >> 4) - chunkRadius;
         int endZ = (pz >> 4) + chunkRadius;
         Set<Long> added = new LinkedHashSet<>();

         for (int cx = startX; cx <= endX; cx++) {
            for (int cz = startZ; cz <= endZ; cz++) {
               LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
               if (chunk != null) {
                  for (BlockEntity te : chunk.getBlockEntities().values()) {
                     if (te instanceof SpawnerBlockEntity spawner) {
                        BlockPos spawnerPos = spawner.getBlockPos();
                        if (this.hasSingleChestNearby(spawnerPos) && this.getSpawnerDelay(spawner) == 20) {
                           for (BlockPos chestPos : this.findSingleChestsNearby(spawnerPos)) {
                              long key = chestPos.asLong();
                              if (added.add(key)) {
                                 targets.add(new AutoKeyFinder$LootTarget(AutoKeyFinder$LootKind.CHEST, chestPos, -1));
                              }
                           }
                        }
                     } else if (te instanceof ChestBlockEntity) {
                        BlockPos pos = te.getBlockPos();
                        long key = pos.asLong();
                        if (!added.contains(key) && this.isSingleChest(pos) && this.isColoredLootChest(pos)) {
                           added.add(key);
                           targets.add(new AutoKeyFinder$LootTarget(AutoKeyFinder$LootKind.CHEST, pos, -1));
                        }
                     }
                  }
               }
            }
         }

         for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof MinecartChest cart && !cart.isRemoved()) {
               long key = 281474976710656L | cart.getId() & 4294967295L;
               if (added.add(key)) {
                  targets.add(new AutoKeyFinder$LootTarget(AutoKeyFinder$LootKind.MINECART, cart.blockPosition(), cart.getId()));
               }
            }
         }

         return targets;
      } else {
         return targets;
      }
   }

   private MinecartChest findMinecartNear(BlockPos pos, double radius) {
      if (mc.level == null) {
         return null;
      } else {
         double radiusSq = radius * radius;
         MinecartChest best = null;
         double bestDist = Double.MAX_VALUE;

         for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof MinecartChest cart) {
               double dist = cart.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
               if (dist <= radiusSq && dist < bestDist) {
                  bestDist = dist;
                  best = cart;
               }
            }
         }

         return best;
      }
   }

   private boolean shouldSaveToEc() {
      return this.saveToEc.get() && this.pendingEcSave && this.hasWantedInPlayerInv();
   }

   private int findNextWantedSlot(ChestMenu container) {
      int chestSize = container.getRowCount() * 9;

      for (int i = 0; i < chestSize; i++) {
         ItemStack stack = container.getSlot(i).getItem();
         if (!stack.isEmpty() && this.isWantedLoot(stack)) {
            return i;
         }
      }

      return -1;
   }

   private boolean hasSingleChestNearby(BlockPos spawnerPos) {
      return !this.findSingleChestsNearby(spawnerPos).isEmpty();
   }

   private Vec3 findSafeStandBeside(BlockPos origin, Vec3 targetCenter) {
      double[][] offsets = new double[][]{
         {1.2, 0.0},
         {-1.2, 0.0},
         {0.0, 1.2},
         {0.0, -1.2},
         {1.5, 1.5},
         {-1.5, 1.5},
         {1.5, -1.5},
         {-1.5, -1.5},
         {1.8, 0.0},
         {-1.8, 0.0},
         {0.0, 1.8},
         {0.0, -1.8},
         {2.0, 0.0},
         {-2.0, 0.0},
         {0.0, 2.0},
         {0.0, -2.0},
         {0.0, 0.0}
      };
      Vec3 best = null;
      double bestDist = Double.MAX_VALUE;

      for (double[] offset : offsets) {
         double x = targetCenter.x + offset[0];
         double z = targetCenter.z + offset[1];

         for (double dy = -2.0; dy <= 3.0; dy += 0.5) {
            double y = targetCenter.y + dy;
            if (this.isPositionClear(x, y, z)) {
               double dist = targetCenter.distanceToSqr(x, y + 1.0, z);
               if (dist <= 25.0 && dist < bestDist) {
                  bestDist = dist;
                  best = new Vec3(x, y, z);
               }
            }
         }
      }

      return best;
   }

   private BlockHitResult buildChestHit(BlockPos pos) {
      Vec3 hitVec = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
      Direction face = Direction.UP;
      if (mc.player != null) {
         face = Direction.getApproximateNearest(mc.player.getX() - hitVec.x, mc.player.getEyeY() - hitVec.y, mc.player.getZ() - hitVec.z);
      }

      return new BlockHitResult(hitVec, face, pos, false);
   }

   private boolean isWantedLoot(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else if (stack.is(Items.ENCHANTED_GOLDEN_APPLE) && this.loot3.enabled()) {
         return true;
      } else if (stack.is(Items.GOLDEN_APPLE) && this.loot4.enabled()) {
         return true;
      } else if (!stack.is(Items.NAME_TAG)) {
         return false;
      } else {
         String name = stack.getHoverName().getString();
         if (name == null) {
            return false;
         } else {
            name = name.trim().toLowerCase(Locale.ROOT);
            if (this.loot0.enabled() && name.equals("красная ключ-карта")) {
               return true;
            } else {
               return !this.loot1.enabled() || !name.equals("зелёная ключ-карта") && !name.equals("зеленая ключ-карта")
                  ? this.loot2.enabled() && name.equals("синяя ключ-карта")
                  : true;
            }
         }
      }
   }

   private double revisitPenalty(double tx, double tz) {
      int sx = Mth.floor(tx) >> 6;
      int sz = Mth.floor(tz) >> 6;
      int hits = 0;

      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            if (this.visitedSectors.contains(sectorKey(sx + dx, sz + dz))) {
               hits++;
            }
         }
      }

      return hits * 0.35;
   }

   private void initExploreGrid(double px, double pz) {
      this.exploreGridX = exploreCellCoord(px);
      this.exploreGridZ = exploreCellCoord(pz);
      this.exploreStripForwardX = true;
      this.exploreGridInitialized = true;
   }

   private void resetExploreSweep(double border) {
      this.exploreGridZ = this.exploreGridMin(border);
      this.exploreGridX = this.exploreGridMin(border);
      this.exploreStripForwardX = true;
      this.visitedExploreCells.clear();
   }

   private void trimVisitedExploreCells() {
      while (this.visitedExploreCells.size() > 8192) {
         Iterator<Long> it = this.visitedExploreCells.iterator();
         if (it.hasNext()) {
            it.next();
            it.remove();
            continue;
         }
         break;
      }
   }

   private int exploreGridMax(double border) {
      return exploreCellCoord(border - 32.0);
   }

   private int exploreGridMin(double border) {
      return exploreCellCoord(-border + 32.0);
   }

   private boolean isTravelDestinationValid(double tx, double tz, double px, double pz) {
      return this.skippedTravelKeys.contains(travelKey(tx, tz)) ? false : horizontalDist(px, pz, tx, tz) >= 64.0;
   }

   private double unvisitedSectorRatio(double x, double z, int radius) {
      int sx = Mth.floor(x) >> 6;
      int sz = Mth.floor(z) >> 6;
      int total = 0;
      int unvisited = 0;

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            total++;
            if (!this.visitedSectors.contains(sectorKey(sx + dx, sz + dz))) {
               unvisited++;
            }
         }
      }

      return total == 0 ? 0.0 : (double)unvisited / total;
   }

   private double[] pickBorderDestination(double border) {
      if (mc.player == null) {
         return null;
      } else {
         double px = mc.player.getX();
         double pz = mc.player.getZ();

         for (int attempt = 0; attempt < 16; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            double tMax = Double.POSITIVE_INFINITY;
            if (dx > 1.0E-6) {
               tMax = Math.min(tMax, (border - px) / dx);
            } else if (dx < -1.0E-6) {
               tMax = Math.min(tMax, (-border - px) / dx);
            }

            if (dz > 1.0E-6) {
               tMax = Math.min(tMax, (border - pz) / dz);
            } else if (dz < -1.0E-6) {
               tMax = Math.min(tMax, (-border - pz) / dz);
            }

            if (Double.isFinite(tMax) && !(tMax < 64.0)) {
               double tx = Mth.clamp(px + dx * tMax, -border, border);
               double tz = Mth.clamp(pz + dz * tMax, -border, border);
               if (!(horizontalDist(px, pz, tx, tz) < 64.0)) {
                  return new double[]{tx, tz};
               }
            }
         }

         double[][] dirs = new double[][]{{1.0, 0.0}, {-1.0, 0.0}, {0.0, 1.0}, {0.0, -1.0}, {1.0, 1.0}, {1.0, -1.0}, {-1.0, 1.0}, {-1.0, -1.0}};
         double[] dir = dirs[ThreadLocalRandom.current().nextInt(dirs.length)];
         double len = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1]);
         double dxx = dir[0] / len;
         double dzx = dir[1] / len;
         double tMaxx = Double.POSITIVE_INFINITY;
         if (dxx > 0.0) {
            tMaxx = Math.min(tMaxx, (border - px) / dxx);
         }

         if (dxx < 0.0) {
            tMaxx = Math.min(tMaxx, (-border - px) / dxx);
         }

         if (dzx > 0.0) {
            tMaxx = Math.min(tMaxx, (border - pz) / dzx);
         }

         if (dzx < 0.0) {
            tMaxx = Math.min(tMaxx, (-border - pz) / dzx);
         }

         return Double.isFinite(tMaxx) && !(tMaxx < 1.0)
            ? new double[]{Mth.clamp(px + dxx * tMaxx, -border, border), Mth.clamp(pz + dzx * tMaxx, -border, border)}
            : new double[]{ThreadLocalRandom.current().nextDouble(-border, border), ThreadLocalRandom.current().nextDouble(-border, border)};
      }
   }

   private double[] exploreCellCenter(int cx, int cz, double border) {
      double x = (cx + 0.5) * 768.0;
      double z = (cz + 0.5) * 768.0;
      x = Mth.clamp(x, -border + 64.0, border - 64.0);
      z = Mth.clamp(z, -border + 64.0, border - 64.0);
      return new double[]{x, z};
   }

   private void stepExploreCursor(double border) {
      int gMin = this.exploreGridMin(border);
      int gMax = this.exploreGridMax(border);
      if (this.exploreStripForwardX) {
         this.exploreGridX++;
         if (this.exploreGridX > gMax) {
            this.exploreGridX = gMax;
            this.exploreGridZ++;
            this.exploreStripForwardX = false;
         }
      } else {
         this.exploreGridX--;
         if (this.exploreGridX < gMin) {
            this.exploreGridX = gMin;
            this.exploreGridZ++;
            this.exploreStripForwardX = true;
         }
      }
   }

   private void markSectorsAround(double x, double z, int radius) {
      int sx = Mth.floor(x) >> 6;
      int sz = Mth.floor(z) >> 6;

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            this.markSector(sx + dx, sz + dz);
         }
      }
   }
}
