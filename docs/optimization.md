# Optimization

Enable **Render -> Optimization** in ClickGUI.

- Reduces vanilla particles to Decreased, preserving an existing Minimal setting.
- Disables vanilla clouds and entity shadows while the corresponding switches are on.
- Hides dropped items and experience orbs beyond the configured range (48 blocks by default). Glowing entities keep their normal rendering.
- Uses quarter-resolution instead of half-resolution GUI blur buffers. The two blur passes process about four times fewer pixels, with a softer image; the original scene and final composition remain at full resolution.
- Disabling a graphics switch or the module restores its previous option value. A manual change made afterwards is preserved. No view-distance or frame-rate limit is forced.

Always-active internal changes: cached module type lookups, cached GUI module search and grid placement.

Verification: verifyOptimization (8 tests), verifyGuiMotion (8 tests), compileClientJava and jar. No before/after in-game FPS benchmark was taken; gains depend on enabled effects and the scene.
