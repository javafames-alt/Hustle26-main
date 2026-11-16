package im.yaHustler.module.impl.player;

enum AutoKeyFinder$Phase {
   SEARCH,
   TRAVEL,
   APPROACH,
   OPEN,
   STEAL,
   EC_OPEN,
   EC_CLICK_OPEN,
   EC_DEPOSIT,
   EC_CLOSE;

   // $VF: synthetic method
   private static AutoKeyFinder$Phase[] $values() {
      return new AutoKeyFinder$Phase[]{SEARCH, TRAVEL, APPROACH, OPEN, STEAL, EC_OPEN, EC_CLICK_OPEN, EC_DEPOSIT, EC_CLOSE};
   }
}
