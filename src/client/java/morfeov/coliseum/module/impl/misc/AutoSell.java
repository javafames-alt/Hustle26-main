package morfeov.coliseum.module.impl.misc;
import morfeov.coliseum.module.*;
import morfeov.coliseum.setting.*;
import morfeov.coliseum.port.event.EventPacketReceive;
import morfeov.coliseum.module.impl.misc.auction.*;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

@ModuleInfo(
   id = "auto_sell", name = "AutoSell",
   description = "Выставляет предмет из руки на аукцион по минимальной цене с процентом, по второму бинду продаёт весь инвентарь и содержимое шалкеров.",
   category = Category.MISC
)
public final class AutoSell extends morfeov.coliseum.port.PortModule {
   private static AutoSell autoSellModule;
   private static final Pattern pattern = Pattern.compile(".*максимальная цена на этот товар.*");
   private static final HashMap<String, String> map = new HashMap<>();
   public NumberSetting numberSetting;
   public TextSetting searchCommand;
   public TextSetting sellCommand;
   public KeySetting sellHandKey;
   public KeySetting sellInventoryKey;
   private AutoSell.TypeOption nested0385 = AutoSell.TypeOption.IDLE;
   private final Stopwatch stopwatch = new Stopwatch();
   private final HashSet<String> set = new HashSet<>();
   private final HashSet<Integer> set2 = new HashSet<>();
   private boolean flag;
   private boolean flag2;
   private boolean flag3;
   private int intVal = -1;
   private int intVal2 = -1;
   private boolean flag4;
   private String strVal = "";
   private boolean flag5;
   private boolean flag6;
   private ItemStack listed=ItemStack.EMPTY;
   private int intVal3 = -1;
   private int intVal4;
   private final java.util.function.Consumer<net.minecraft.client.Minecraft> playerTickListener = playerTickEvent -> {
      if (mc.player != null) {
         boolean local2082_2 = mc.gui.screen()==null && this.sellHandKey.down();
         if (local2082_2 && !this.flag2) {
            this.processData(false);
         }

         this.flag2 = local2082_2;
         boolean local2082_3 = mc.gui.screen()==null && this.sellInventoryKey.down();
         if (local2082_3 && !this.flag3) {
            this.processData(true);
         }

         this.flag3 = local2082_3;
         switch (this.nested0385) {
            case IDLE:
            default:
               break;
            case PICK:
               if (InventoryActionQueue.getInstance().hasPendingActions() || InventorySlotManager.getInstance().isLocked() || !this.stopwatch.hasExceededMillis(1500L)) {
                  return;
               }

               if (mc.gui.screen() != null) {
                  mc.player.closeContainer();
                  return;
               }

               this.intVal = -1;
               this.intVal2 = -1;

               for (int index = 0; index <= 35; index++) {
                  if (!mc.player.getInventory().getItem(index).isEmpty()) {
                     if (Block.byItem(mc.player.getInventory().getItem(index).getItem()) instanceof ShulkerBoxBlock) {
                        if (this.intVal2 == -1 && !this.set2.contains(index)) {
                           this.intVal2 = index;
                        }
                     } else if (this.intVal == -1
                        && !this.set.contains(mc.player.getInventory().getItem(index).getHoverName().getString())) {
                        this.intVal = index;
                     }
                  }
               }

               if (this.intVal == -1 && this.intVal2 == -1) {
                  this.nested0385 = AutoSell.TypeOption.IDLE;
                  return;
               }

               if (this.intVal == -1) {
                  this.set2.add(this.intVal2);
                  InventoryUtils.sendSlotClickPacket(InventoryUtils.getScreenSlotId(this.intVal2), 1, ContainerInput.PICKUP, false);
                  this.stopwatch.reset();
                  this.nested0385 = AutoSell.TypeOption.SHULKER;
                  return;
               }

               if (this.intVal != InventoryUtils.getSelectedSlot()) {
                  InventoryActionQueue.getInstance().queueSlotSwap(this.intVal, InventoryUtils.getSelectedSlot(), 1);
               }

               this.stopwatch.reset();
               this.nested0385 = AutoSell.TypeOption.SEARCH;
               break;
            case SEARCH:
               if (InventoryActionQueue.getInstance().hasPendingActions() || InventorySlotManager.getInstance().isLocked() || !this.stopwatch.hasExceededMillis(200L)) {
                  return;
               }

               if (mc.player.getMainHandItem().isEmpty()) {
                  this.stopwatch.reset();
                  this.nested0385 = this.flag ? AutoSell.TypeOption.PICK : AutoSell.TypeOption.IDLE;
                  return;
               }

               this.strVal = mc.player.getMainHandItem().getHoverName().getString();
               this.listed=mc.player.getMainHandItem().copy();
               this.flag4 = false;
               this.updateState();
               this.stopwatch.reset();
               this.nested0385 = AutoSell.TypeOption.WAIT_LOTS;
               break;
            case WAIT_LOTS:
               if(!ItemStack.isSameItemSameComponents(listed,mc.player.getMainHandItem()) || listed.getCount()!=mc.player.getMainHandItem().getCount()){nested0385=TypeOption.IDLE;return;}
               if (mc.gui.screen() instanceof ContainerScreen genericContainerScreen
                  && AuctionHelperModule.getInstance().matchesCriteria3(genericContainerScreen.getTitle().getString())) {
                  if (!this.stopwatch.hasExceededMillis(250L)) {
                     return;
                  }

                  int local2082_5 = -1;

                  for (int index2 = 0; index2 < mc.player.containerMenu.slots.size() - 36; index2++) {
                     ItemStack itemStack = mc.player.containerMenu.getSlot(index2).getItem();
                     if (!itemStack.isEmpty()
                        && ItemStack.isSameItem(itemStack, mc.player.getMainHandItem())
                        && this.matchesCriteria(itemStack, mc.player.getMainHandItem())
                        && AuctionHelperModule.getInstance().computeInt(itemStack) > 0
                        && !AuctionHelperModule.getInstance().formatText2(itemStack.getHoverName().getString()).contains("не актуален")
                        && (local2082_5 == -1 || AuctionHelperModule.getInstance().computeInt(itemStack) / Math.max(1, itemStack.getCount()) < local2082_5)) {
                        local2082_5 = AuctionHelperModule.getInstance().computeInt(itemStack) / Math.max(1, itemStack.getCount());
                     }
                  }

                  if (local2082_5 == -1) {
                     if (!this.stopwatch.hasExceededMillis(600L)) {
                        return;
                     }

                     mc.player.closeContainer();
                     this.set.add(this.strVal);
                     this.stopwatch.reset();
                     this.nested0385 = this.flag ? AutoSell.TypeOption.PICK : AutoSell.TypeOption.IDLE;
                     return;
                  }

                  mc.player.closeContainer();
                  this.intVal4 = local2082_5;
                  this.flag5 = false;
                  mc.player.connection.sendCommand(this.formatText3() + " " + this.computeInt(local2082_5));
                  this.stopwatch.reset();
                  this.nested0385 = AutoSell.TypeOption.AFTER_SELL;
                  break;
               }

               if (!this.flag4 && this.stopwatch.hasExceededMillis(600L) && mc.gui.screen() == null) {
                  this.flag4 = true;
                  this.updateState();
                  return;
               }

               if (!this.stopwatch.hasExceededMillis(1800L)) {
                  return;
               }

               this.set.add(this.strVal);
               this.stopwatch.reset();
               this.nested0385 = this.flag ? AutoSell.TypeOption.PICK : AutoSell.TypeOption.IDLE;
               return;
            case AFTER_SELL:
               if (!this.stopwatch.hasExceededMillis(100L)) {
                  return;
               }

               if (this.flag5) {
                  this.flag5 = false;
                  if (mc.player.getMainHandItem().getCount() > 1 && InventoryUtils.findEmptySlot(0, 35) != -1) {
                     this.intVal3 = InventoryUtils.findEmptySlot(0, 35);
                     InventoryUtils.sendSlotClickPacket(InventoryUtils.getScreenSlotId(InventoryUtils.getSelectedSlot()), 1, ContainerInput.PICKUP, false);
                     InventoryUtils.sendSlotClickPacket(InventoryUtils.getScreenSlotId(this.intVal3), 0, ContainerInput.PICKUP, false);
                     this.flag6 = true;
                     this.stopwatch.reset();
                     return;
                  }

                  this.set.add(this.strVal);
                  this.stopwatch.reset();
                  this.nested0385 = this.flag ? AutoSell.TypeOption.PICK : AutoSell.TypeOption.IDLE;
                  return;
               }

               if (this.flag6) {
                  if (!this.stopwatch.hasExceededMillis(300L)) {
                     return;
                  }

                  this.flag6 = false;
                  mc.player.connection.sendCommand(this.formatText3() + " " + this.computeInt(this.intVal4));
                  this.stopwatch.reset();
                  return;
               }

               if (!mc.player.getMainHandItem().isEmpty() && mc.player.getMainHandItem().getHoverName().getString().equals(this.strVal)) {
                  if (!this.stopwatch.hasExceededMillis(700L)) {
                     return;
                  }

                  this.set.add(this.strVal);
               }

               this.stopwatch.reset();
               if (!this.flag
                  && this.intVal3 != -1
                  && !this.set.contains(this.strVal)
                  && !mc.player.getInventory().getItem(this.intVal3).isEmpty()) {
                  int local2082_4 = this.intVal3;
                  InventoryActionQueue.getInstance().queueSlotSwap(local2082_4, InventoryUtils.getSelectedSlot(), 1);
                  this.intVal3 = -1;
                  this.nested0385 = AutoSell.TypeOption.SEARCH;
                  return;
               }

               this.nested0385 = this.flag ? AutoSell.TypeOption.PICK : AutoSell.TypeOption.IDLE;
               break;
            case SHULKER:
               if (InventoryActionQueue.getInstance().hasPendingActions() || InventorySlotManager.getInstance().isLocked()) {
                  return;
               }

               if (!(
                  mc.gui.screen() instanceof ContainerScreen genericContainerScreen2
                     && !AuctionHelperModule.getInstance().matchesCriteria3(genericContainerScreen2.getTitle().getString())
               )) {
                  if (!this.stopwatch.hasExceededMillis(800L)) {
                     return;
                  }

                  if (!mc.player.containerMenu.getCarried().isEmpty()) {
                     InventoryUtils.sendSlotClickPacket(InventoryUtils.getScreenSlotId(this.intVal2), 0, ContainerInput.PICKUP, false);
                  }

                  this.stopwatch.reset();
                  this.nested0385 = AutoSell.TypeOption.PICK;
                  return;
               }

               if (!this.stopwatch.hasExceededMillis(200L)) {
                  return;
               }

               if (InventoryUtils.findEmptySlot(0, 35) == -1) {
                  mc.player.closeContainer();
                  this.stopwatch.reset();
                  this.nested0385 = AutoSell.TypeOption.PICK;
                  return;
               }

               for (int index3 = 0; index3 < mc.player.containerMenu.slots.size() - 36; index3++) {
                  if (mc.player.containerMenu.getSlot(index3).hasItem()) {
                     InventoryUtils.sendSlotClickPacket(index3, 0, ContainerInput.QUICK_MOVE, false);
                     this.stopwatch.reset();
                     return;
                  }
               }

               mc.player.closeContainer();
               this.stopwatch.reset();
               this.nested0385 = AutoSell.TypeOption.PICK;
         }
      }
   };
   private final java.util.function.Consumer<EventPacketReceive> packetListener = packetEvent -> {
      if (this.nested0385 == AutoSell.TypeOption.AFTER_SELL
         && packetEvent.getPacket() instanceof ClientboundSystemChatPacket gameMessageS2CPacket) {
         if (pattern.matcher(AuctionHelperModule.getInstance().formatText2(gameMessageS2CPacket.content().getString().replaceAll("(?i)§[0-9a-fk-or]", ""))).matches()) {
            this.flag5 = true;
         }
      }
   };
   private static final HashMap<String, String> map2 = new HashMap<>();
   private static final HashMap<String, String> map3 = new HashMap<>();

   private int computeInt(int local2070_1) {
      return sellingPrice(local2070_1,mc.player.getMainHandItem().getCount(),this.numberSetting.floatValue());
   }
   public static int sellingPrice(int price,int count,float percent){
      var multiplier=java.math.BigDecimal.valueOf(100).add(new java.math.BigDecimal(Float.toString(Math.clamp(percent,-90,300))));
      long unit=Math.max(1,java.math.BigDecimal.valueOf(Math.max(1,price)).multiply(multiplier).divide(java.math.BigDecimal.valueOf(100),0,java.math.RoundingMode.CEILING).longValueExact());
      int amount=Math.max(1,count);return unit>Integer.MAX_VALUE/amount?Integer.MAX_VALUE:(int)(unit*amount);
   }

   static {
      map.put(
         "minecraft:netherite_helmet minecraft:aqua_affinity1 minecraft:blast_protection5 minecraft:fire_protection5 minecraft:mending1 minecraft:projectile_protection5 minecraft:protection5 minecraft:respiration3 minecraft:unbreaking5",
         "Шлем Крушителя"
      );
      map.put(
         "minecraft:netherite_chestplate minecraft:blast_protection5 minecraft:fire_protection5 minecraft:mending1 minecraft:projectile_protection5 minecraft:protection5 minecraft:unbreaking5",
         "Нагрудник Крушителя"
      );
      map.put(
         "minecraft:netherite_leggings minecraft:blast_protection5 minecraft:fire_protection5 minecraft:mending1 minecraft:projectile_protection5 minecraft:protection5 minecraft:unbreaking5",
         "Поножи Крушителя"
      );
      map.put(
         "minecraft:netherite_boots minecraft:blast_protection5 minecraft:depth_strider3 minecraft:feather_falling4 minecraft:fire_protection5 minecraft:mending1 minecraft:projectile_protection5 minecraft:protection5 minecraft:soul_speed3 minecraft:unbreaking5",
         "Ботинки Крушителя"
      );
      map.put(
         "minecraft:netherite_sword minecraft:bane_of_arthropods7 minecraft:fire_aspect2 minecraft:looting5 minecraft:mending1 minecraft:sharpness7 minecraft:smite7 minecraft:sweeping_edge3 minecraft:unbreaking5",
         "Меч Крушителя"
      );
      map.put("minecraft:netherite_pickaxe minecraft:efficiency10 minecraft:fortune5 minecraft:mending1 minecraft:unbreaking5", "Кирка Крушителя");
      map.put(
         "minecraft:trident minecraft:channeling1 minecraft:fire_aspect2 minecraft:impaling5 minecraft:loyalty3 minecraft:mending1 minecraft:sharpness7 minecraft:unbreaking5",
         "Трезубец Крушителя"
      );
      map.put(
         "minecraft:crossbow minecraft:mending1 minecraft:multishot1 minecraft:piercing5 minecraft:quick_charge3 minecraft:unbreaking3", "Арбалет Крушителя"
      );
      map.put("minecraft:bow minecraft:flame1 minecraft:infinity1 minecraft:mending1 minecraft:power5 minecraft:unbreaking5", "Лук Крушителя");
      map.put("minecraft:elytra minecraft:mending1 minecraft:unbreaking5", "Элитры Крушителя");
      map2.put("Хаос искажает реальность", "сфера Хаоса");
      map2.put("Шёпот Сатира звучит", "сфера Сатира");
      map2.put("Звериная дикая мощь", "сфера Бестии");
      map2.put("Дух Ареса пылает внутри", "сфера Ареса");
      map2.put("Живучесть темных глубин", "сфера Гидры");
      map2.put("Хранит волю Икара", "сфера Икара");
      map2.put("Мощь Титанов крепка", "сфера Титана");
      map2.put("Холод Эриды вечен", "сфера Эрида");
      map2.put("Несокрушимая мощь", "талисман Крушителя");
      map2.put("Раздор жаждает хаоса", "талисман Раздора");
      map2.put("Тиран подавляет слабых", "талисман Тирана");
      map2.put("Чистая, дикая агрессия", "талисман Ярости");
      map2.put("Вихрь не знает покоя", "талисман Вихря");
      map2.put("Мрак сгущается рядом", "талисман Мрака");
      map2.put("Несёт строгий приговор", "талисман Карателя");
      map2.put("Печать разжигает ярость", "талисман Демона");
      map2.put("Иссушение III (0:05)", "Мучительная стрела");
      map2.put("Мучительная стрела", "Мучительная стрела");
      map2.put("Замедление III (0:03)", "Стрела Зевса");
      map2.put("Стрела Зевса", "Стрела Зевса");
      map2.put("Тошнота (0:05)", "Кровавая стрела");
      map2.put("Кровавая стрела", "Кровавая стрела");
      map2.put("Стрела обледенения", "Стрела обледенения");
      map2.put("Спешка III (0:05)", "Световая стрела");
      map2.put("Световая стрела", "Световая стрела");
      map2.put("Замедление V (0:07)", "Стрела терапии");
      map2.put("Стрела терапии", "Стрела терапии");
      map3.put("Замедление III\nИссушение III\nОтравление III", "Мучительная стрела");
      map3.put("Замедление III\nМоментальный урон II", "стрела Зевса");
      map3.put("Слабость III\nСлепота\nТошнота\nУтомление", "Кровавая стрела");
      map3.put("Скорость V\nСлепота\nСпешка III", "Световая стрела");
      map3.put("Замедление V\nИсцеление\nСлабость", "стрела терапии");
      map3.put("Замедление IX\nУтомление III", "стрела обледенения");
      map3.put("Замедление potion.potency.8\nУтомление III", "стрела обледенения");
   }

   private String formatText(ItemStack itemStack) {
      Matcher matcher = Pattern.compile(
            "(?i)(Сфера|Талисман) (Хаоса|Сатира|Бестии|Ареса|Гидры|Икара|Титана|Эрида|Крушителя|Раздора|Тирана|Ярости|Вихря|Мрака|Карателя|Демона)"
         )
         .matcher(ChatFormatting.stripFormatting(itemStack.getHoverName().getString()));
      if (matcher.find()) {
         return matcher.group(1).toLowerCase() + " " + matcher.group(2);
      }

      for (Entry<String, String> local2072_4 : map2.entrySet()) {
         if (ChatFormatting.stripFormatting(itemStack.getHoverName().getString()).contains(local2072_4.getKey())) {
            return local2072_4.getValue();
         }
      }

      if (itemStack.get(DataComponents.LORE) == null) {
         return "";
      }

      for (Component text : itemStack.get(DataComponents.LORE).lines()) {
         for (Entry<String, String> local2072_6 : map2.entrySet()) {
            if (ChatFormatting.stripFormatting(text.getString()).contains(local2072_6.getKey())) {
               return local2072_6.getValue();
            }
         }
      }

      return "";
   }

   private int computeInt2(ItemStack itemStack, String local2073_2) {
      for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Holder<Enchantment>> local2073_4 : itemStack.getEnchantments().entrySet()) {
         if (((Holder)local2073_4.getKey()).getRegisteredName().equals(local2073_2)) {
            return local2073_4.getIntValue();
         }
      }

      return 0;
   }

   private HashSet<String> getHashSet(ItemStack itemStack) {
      CompoundTag compoundOrEmpty = itemStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
         .copyTag()
         .getCompoundOrEmpty("PublicBukkitValues");
      HashSet<String> local2075_3 = new HashSet<>();

      for (String local2075_5 : compoundOrEmpty.keySet()) {
         if (local2075_5.startsWith("spookyenchantments:") && local2075_5.endsWith("lvl")) {
            local2075_3.add(local2075_5 + compoundOrEmpty.getInt(local2075_5).orElse(0));
         }
      }

      return local2075_3;
   }

   private String formatText2(ItemStack itemStack) {
      if (!this.formatText5(itemStack).isEmpty()) {
         return map3.getOrDefault(this.formatText5(itemStack), this.formatText5(itemStack).split("\n")[0]);
      } else if (!this.formatText(itemStack).isEmpty()) {
         return this.formatText(itemStack);
      } else {
         return this.formatText4(itemStack).isEmpty()
            ? ChatFormatting.stripFormatting(itemStack.getHoverName().getString()).replaceAll("[^\\p{L} ]", " ").replaceAll(" +", " ").trim()
            : this.formatText4(itemStack);
      }
   }

   private String formatText3() {
      return this.sellCommand.getValue().isBlank()
         ? "ah sell"
         : (this.sellCommand.getValue().trim().startsWith("/") ? this.sellCommand.getValue().trim().substring(1) : this.sellCommand.getValue().trim());
   }

   private boolean matchesCriteria(ItemStack itemStack, ItemStack itemStack2) {
      if (!this.formatText5(itemStack2).isEmpty()) {
         return this.formatText5(itemStack2).equals(this.formatText5(itemStack));
      } else if (!this.formatText(itemStack2).isEmpty()) {
         return this.formatText(itemStack2).equals(this.formatText(itemStack));
      } else {
         return !this.formatText4(itemStack2).isEmpty()
            ? this.formatText4(itemStack2).equals(this.formatText4(itemStack))
            : itemStack.getEnchantments().equals(itemStack2.getEnchantments())
               && itemStack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
                  .equals(itemStack2.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY))
               && this.getHashSet(itemStack).equals(this.getHashSet(itemStack2))
               && this.getHashSet2(itemStack).equals(this.getHashSet2(itemStack2));
      }
   }

   private String formatText4(ItemStack itemStack) {
      for (Entry<String, String> local2079_3 : map.entrySet()) {
         if (this.matchesCriteria2(itemStack, local2079_3.getKey())) {
            return local2079_3.getValue();
         }
      }

      return "";
   }

   private boolean matchesCriteria2(ItemStack itemStack, String local2080_2) {
      for (String local2080_6 : local2080_2.split(" ")) {
         if (!Character.isDigit(local2080_6.charAt(local2080_6.length() - 1))) {
            if (!BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString().equals(local2080_6)) {
               return false;
            }
         } else if (this.computeInt2(itemStack, local2080_6.replaceAll("\\d+$", "")) < Integer.parseInt(local2080_6.replaceAll("^.*?(\\d+)$", "$1"))) {
            return false;
         }
      }

      return true;
   }

   public AutoSell() {
      autoSellModule=this;
      numberSetting=add(new NumberSetting("Процент к цене",0,-90,300,1));
      searchCommand=add(new TextSetting("Команда поиска","ah search"));
      sellCommand=add(new TextSetting("Команда продажи","ah sell"));
      sellHandKey=add(new KeySetting("Продать с руки"));
      sellInventoryKey=add(new KeySetting("Продать инвентарь"));
   }
   @Override public void tick(net.minecraft.client.Minecraft client) {
      if(client.player==null||client.level==null||client.gameMode==null){nested0385=TypeOption.IDLE;return;}
      if(nested0385==TypeOption.IDLE && client.gui.screen()!=null)return;
      playerTickListener.accept(client);
   }
   @Override public void onPacketReceive(EventPacketReceive event){packetListener.accept(event);}
   public void start(boolean inventory){processData(inventory);}
   @Override
   protected void onDisable() {
      this.nested0385 = AutoSell.TypeOption.IDLE;
   }

   public static AutoSell getInstance() {
      if (autoSellModule == null) {
         autoSellModule = new AutoSell();
      }

      return autoSellModule;
   }

   private HashSet<String> getHashSet2(ItemStack itemStack) {
      HashSet<String> local2085_2 = new HashSet<>();

      for (MobEffectInstance statusEffectInstance : itemStack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getAllEffects()) {
         local2085_2.add(statusEffectInstance.getEffect().getRegisteredName() + statusEffectInstance.getAmplifier() + "/" + statusEffectInstance.getDuration());
      }

      return local2085_2;
   }

   private void updateState() {
      if (mc.player != null && !mc.player.getMainHandItem().isEmpty()) {
         PotionContents potionContentsComponent = mc.player.getMainHandItem().get(DataComponents.POTION_CONTENTS);
         if (potionContentsComponent != null) {
            for (MobEffectInstance statusEffectInstance : potionContentsComponent.getAllEffects()) {
               if (statusEffectInstance.getEffect().value() == MobEffects.INSTANT_HEALTH.value()
                  || statusEffectInstance.getEffect().value() == MobEffects.STRENGTH.value()) {
                  mc.player
                     .connection
                     .sendCommand(
                        (
                              this.searchCommand.getValue().isBlank()
                                 ? "ah search"
                                 : (
                                    this.searchCommand.getValue().trim().startsWith("/")
                                       ? this.searchCommand.getValue().trim().substring(1)
                                       : this.searchCommand.getValue().trim()
                                 )
                           )
                           + " "
                           + (statusEffectInstance.getEffect().value() == MobEffects.STRENGTH.value() ? "Зелье силы" : "Зелье исцеления")
                     );
                  return;
               }
            }
         }

         if (!this.formatText2(mc.player.getMainHandItem()).isEmpty()) {
            mc.player
               .connection
               .sendCommand(
                  (
                        this.searchCommand.getValue().isBlank()
                           ? "ah search"
                           : (
                              this.searchCommand.getValue().trim().startsWith("/")
                                 ? this.searchCommand.getValue().trim().substring(1)
                                 : this.searchCommand.getValue().trim()
                           )
                     )
                     + " "
                     + this.formatText2(mc.player.getMainHandItem())
               );
         }
      }
   }

   private String formatText5(ItemStack itemStack) {
      if (!itemStack.is(Items.TIPPED_ARROW)) {
         return "";
      }

      String[] stringArray = new String[]{"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};
      ArrayList<String> local2087_3 = new ArrayList<>();
      if (itemStack.get(DataComponents.POTION_CONTENTS) != null) {
         for (MobEffectInstance statusEffectInstance : itemStack.get(DataComponents.POTION_CONTENTS).getAllEffects()) {
            local2087_3.add(
               statusEffectInstance.getEffect().value().getDisplayName().getString().replace("Мгновенное исцеление", "Исцеление")
                  + stringArray[Math.max(0, Math.min(9, statusEffectInstance.getAmplifier()))]
            );
         }
      }

      if (local2087_3.isEmpty() && itemStack.get(DataComponents.LORE) != null) {
         for (Component text : itemStack.get(DataComponents.LORE).lines()) {
            if (ChatFormatting.stripFormatting(text.getString()).trim().isEmpty()) {
               break;
            }

            local2087_3.add(ChatFormatting.stripFormatting(text.getString()).trim().replaceAll("\\s*\\(.*", ""));
         }
      }

      Collections.sort(local2087_3);
      return String.join("\n", local2087_3);
   }

   private void processData(boolean local2088_1) {
      if (this.nested0385 != AutoSell.TypeOption.IDLE) {
         this.nested0385 = AutoSell.TypeOption.IDLE;
      } else {
         this.flag = local2088_1;
         this.set.clear();
         this.set2.clear();
         this.flag5 = false;
         this.flag6 = false;
         this.intVal3 = -1;
         this.stopwatch.reset();
         this.nested0385 = local2088_1 ? AutoSell.TypeOption.PICK : AutoSell.TypeOption.SEARCH;
      }
   }

   private enum TypeOption {
      IDLE,
      PICK,
      SEARCH,
      WAIT_LOTS,
      AFTER_SELL,
      SHULKER;
   }
}
