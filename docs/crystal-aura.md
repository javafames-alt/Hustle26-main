# CrystalAura

Source: `ZenithSRC-clear/src/main/java/org/zenith/client/modules/impl/combat/CrystalAuraModule.java`,
`utility/misc/Data3.java` (presets), and `utility/game/Class35.java` (damage).
The source project is unchanged. The previous Coliseum CrystalAura is not restored.

The adaptation uses native Coliseum settings and inventory leases, Mojang 26.3 names,
pre-motion rotation packets / the post-motion hook, and collector-based world rendering.
Grim is the default; DonutSMP and the donor's unrestricted preset retain their declared values.
The module starts disabled and saves through ClientConfig.

Implemented: target/friend filtering, explosion exposure with hypothetical placement/mining,
armor/resistance/enchantment reductions, movement prediction, self-damage margin, Faceplace,
armor durability, totem-pop scoring, hit windows, action budgets and delays, inventory/offhand
use, silent/keep/off swaps, anti-weakness, confirmed placements, rejected-position timeouts,
adaptive strict reach, spawn-triggered break/replacement, explosion chain tracking,
obsidian preparation, city/overhead mining, anchor place/charge/detonate, protective blocks,
anti-mining covers, cobweb fallback, four bound surround layouts, and melee/knock-up prediction.
The ClientLevel spawn hook runs after entity insertion. Aura yields while crystal work is active.

Port-specific differences: the original combat logger/AutoTotem diagnostic pipeline is replaced
by local SLF4J action logs; the local AutoTotem coordinates through Coliseum inventory leases.
Hit-window and knockback estimates use client-visible state and are discarded when observed
movement disagrees. Mining uses the native game mode's progress and server confirmation instead
of the donor's forced local block removal. The melee fallback requires an equipped sword/axe;
it does not take a weapon out of the inventory for the combo. Native rotations own mouse
sensitivity snapping and movement correction. These are adaptations, not an unchanged binary port.

Silent rotations have an owner token. Starting a request always enables free look even if
another controller disabled it during AIM/RESET. Aura waits for owned crystal rotations to
end. Disabling CrystalAura clears only its own request, restores the camera direction, aligns
current/previous yaw, pitch, head and body angles, and clears interpolation and hand sway.

Validation: `gradlew.bat verifyCrystalAura verifyAuraRotations verifyProfiles jar`.
Tests cover donor preset values, timing, safety boundaries, Faceplace, scoring, profile round trips,
the spawn hook's target method, camera independence while aiming, interrupted free-look recovery,
owner priority, and stable current/previous head/body angles after disabling. Rotation tests run
the actual client methods against an isolated client/player fixture without opening a window.
Actual server behavior and visual tuning require gameplay.
