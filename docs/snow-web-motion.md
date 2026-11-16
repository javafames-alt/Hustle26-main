# MotionBlur and AutoWebTrap

- MotionBlur: restored the missing depth include; default smoothness is 0.42. Blur is capped at 18 pixels at maximum strength, has depth rejection and no RGB separation. It runs before hands and HUD.
- AutoWebTrap: Hustle 26.3 web-interception behavior adapted to native Hustle 26.3 APIs with readable names. Selects a nearby non-allied survival player, predicts movement with collision checks, places cobwebs against supporting faces within 4.5 blocks, throttles retries and restores the selected hotbar slot. Cobwebs must be in the hotbar. This adaptation does not import the original implementation's Matrix-specific predictor, mining or rotation-manager internals.

Validation: compileClientJava; verifyHustleShaders --args=motion (native shader compiler with both depth conventions and GPU compile/link); jar. Gameplay and final visual tuning require an in-game check.
