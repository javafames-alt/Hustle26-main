# Visual and cosmetics class names

Package: `im.yaHustler.render.coliseimvisual`.

| Previous class | Current class |
| --- | --- |
| `SystemClass33` | `VisualMath` |
| `SystemClass86` | `GlowQuadRenderer` |
| `SystemClass111` | `RainRoofHeightmap` |
| `SystemClass122` | `VisualColors` |
| `SystemClass154` | `WorldRenderMatrices` |
| `SystemClass158` | `VisualPipelineBuilder` |
| `SystemClass175` | `TrailHistory` |
| `SystemClass180` | `TransientGpuBuffers` |
| `SystemClass218` | `VisualTheme` |
| `SystemClass233` | `PrismaticChamsRenderer` |
| `SystemClass254` | `SharedVertexBuffer` |
| `SystemClass290` | `WorldRenderFrame` |
| `SystemClass332` | `VisualTickEvent` |
| `SystemClass333` | `PostTick` |
| `SystemClass405` | `VisualEventListener` |
| `SystemClass413` | `FogSettingsEvent` |
| `SystemClass424` | `VolumetricFogRenderer` |
| `SystemClass519` | `AuroraRenderer` |
| `SystemClass521` | `EntityGlowRenderer` |
| `SystemClass534` | `WorldChangeEvent` |
| `SystemClass535` | `EntityIdAccess` |
| `SystemClass576` | `VisualResources` |
| `SystemClass603` | `VisualThemeColor` |

Removed 116 generated literal aliases from the visual helpers.

Resource directories use `coliseimvisual` too (images, core shaders, shader includes).
Cosmetics code and resources use `cosmetics/hustle263`; catalog IDs and replacement
mappings retain their values so saved cosmetic selections continue to load.

Visual helpers use descriptive method, field and parameter names. Generated
literal aliases are removed and GPU passes use try-with-resources for cleanup.
The resource helpers preserve the font/image/shader lookup conventions.
