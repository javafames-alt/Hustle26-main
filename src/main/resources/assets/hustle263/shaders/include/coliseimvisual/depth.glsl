#ifndef HUSTLE_VISUAL_DEPTH_GLSL
#define HUSTLE_VISUAL_DEPTH_GLSL
#ifdef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
#define HUSTLE_DEPTH_TO_CLIP(value) (value)
#else
#define HUSTLE_DEPTH_TO_CLIP(value) ((value) * 2.0 - 1.0)
#endif
#endif
