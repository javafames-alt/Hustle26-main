package morfeov.coliseum.module.impl.render;

import com.google.gson.JsonObject;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.MeshData.DrawState;
import morfeov.coliseum.Coliseum;
import morfeov.coliseum.module.Category;
import morfeov.coliseum.render.coliseimvisual.PortedModule;
import morfeov.coliseum.module.ModuleInfo;
import morfeov.coliseum.setting.*;
import morfeov.coliseum.render.coliseimvisual.RainRoofHeightmap;
import morfeov.coliseum.render.coliseimvisual.VisualColors;
import morfeov.coliseum.render.coliseimvisual.VisualPipelineBuilder;
import morfeov.coliseum.render.coliseimvisual.VisualTheme;
import morfeov.coliseum.render.coliseimvisual.MediaSessionHost;
import morfeov.coliseum.render.coliseimvisual.SharedVertexBuffer;
import morfeov.coliseum.render.coliseimvisual.TimeSetting;
import morfeov.coliseum.render.coliseimvisual.VisualEventListener;
import morfeov.coliseum.render.coliseimvisual.FogSettingsEvent;
import morfeov.coliseum.render.coliseimvisual.VolumetricFogRenderer;
import morfeov.coliseum.render.coliseimvisual.AuroraRenderer;
import morfeov.coliseum.render.coliseimvisual.WorldChangeEvent;
import morfeov.coliseum.render.coliseimvisual.VisualResources;
import morfeov.coliseum.render.coliseimvisual.VisualThemeColor;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

@ModuleInfo(id="ambience", name="Ambience", category=Category.RENDER, description="Погода, туман, время и шейдеры неба")
public class Ambience extends PortedModule {
   private final AuroraRenderer systemClass519 = new AuroraRenderer();
   private final VolumetricFogRenderer systemClass424 = new VolumetricFogRenderer();
   private final RainRoofHeightmap systemClass111 = new RainRoofHeightmap();
   private static final BlendFunction BLENDFUNCTION = new BlendFunction(BlendFactor.DST_COLOR, BlendFactor.SRC_COLOR, BlendFactor.ZERO, BlendFactor.ONE);
   private static final int COUNT82 = 240;
   private static final int COUNT83 = 80;
   private static final int COUNT84 = 160;
   private static final int COUNT85 = 32;
   private static final int COUNT86 = 48;
   private static final int COUNT87 = 144;
   private static final long VALUE1985 = 131072L;
   private static final int COUNT88 = 1;
   private static final int COUNT89 = 64;
   private static final int COUNT90 = 8192;
   private static final int COUNT91 = 400;
   private static final int COUNT92 = 2;
   private final RenderPipeline renderPipeline1;
   private final RenderPipeline renderPipeline2;
   private final RenderPipeline renderPipeline3;
   private final RenderPipeline renderPipeline4;
   private final RenderPipeline renderPipeline5;
   private final RenderPipeline renderPipeline6;
   private final RenderPipeline renderPipeline7;
   private final RenderPipeline renderPipeline8;
   private final RenderPipeline renderPipeline9;
   private final RenderPipeline renderPipeline10;
   private final RenderPipeline renderPipeline11;
   private final RenderPipeline renderPipeline12;
   private final RenderPipeline renderPipeline13;
   private final RenderPipeline renderPipeline14;
   private final RenderPipeline renderPipeline15;
   private final RenderPipeline renderPipeline16;
   private final RenderPipeline renderPipeline17;
   private final RenderPipeline renderPipeline18;
   private GpuBuffer gpuBuffer;
   private int count144;
   private float value134;
   private TextureTarget textureTarget;
   private GpuBuffer gpuBuffer1;
   private GpuBuffer gpuBuffer2;
   private GpuBuffer gpuBuffer3;
   private GpuBuffer gpuBuffer4;
   private TextureTarget textureTarget1;
   private GpuBuffer gpuBuffer5;
   private int count145;
   private long value135;
   private TextureTarget textureTarget2;
   private TextureTarget textureTarget3;
   private TextureTarget textureTarget4;
   private GpuBuffer gpuBuffer6;
   private GpuBuffer gpuBuffer7;
   private GpuBuffer gpuBuffer8;
   private GpuBuffer gpuBuffer9;
   private final GpuBuffer[] gpuBuffer10;
   private final long[] value136;
   private final GpuBuffer[] gpuBuffer11;
   private final List<Ambience.Vec3Vec3Record> items26;
   private final Matrix4f matrix4f;
   private final Matrix4f matrix4f1;
   private final Matrix4f matrix4f2;
   private final Matrix4f matrix4f3;
   private double value137;
   private long value138;
   private double value139;
   private double value140;
   private double value141;
   private double value142;
   private long value143;
   private long value144;
   private Vec3 vec3_20;
   private Vec3 vec3_21;
   private int count146;
   private Vec3 vec3_22;
   private Ambience.XjEHgoF49w8Sc100Inner f_8483;
   private final ModeSetting multiChoiceSetting56;
   private final NumberSetting sliderSetting172;
   private final NumberSetting sliderSetting173;
   private final BooleanSetting checkboxSetting152;
   private final ColorSetting colorSetting3;
   private final ModeSetting multiChoiceSetting57;
   private final NumberSetting sliderSetting174;
   private final NumberSetting sliderSetting175;
   private final BooleanSetting checkboxSetting153;
   private final NumberSetting sliderSetting176;
   private final NumberSetting sliderSetting177;
   private final NumberSetting sliderSetting178;
   private final NumberSetting sliderSetting179;
   private final NumberSetting sliderSetting180;
   private final NumberSetting sliderSetting181;
   private final NumberSetting sliderSetting182;
   private final NumberSetting sliderSetting183;
   private final ColorSetting colorSetting4;
   private final ColorSetting colorSetting5;
   private final BooleanSetting checkboxSetting154;
   private final ModeSetting multiChoiceSetting58;
   private final TimeSetting systemClass295;
   private final ModeSetting multiChoiceSetting59;
   private final ColorSetting colorSetting6;
   private final NumberSetting sliderSetting184;
   private final NumberSetting sliderSetting185;
   private final NumberSetting sliderSetting186;
   private final NumberSetting sliderSetting187;
   private final NumberSetting sliderSetting188;
   private final NumberSetting sliderSetting189;
   private final NumberSetting sliderSetting190;
   private final BooleanSetting checkboxSetting155;
   private final NumberSetting sliderSetting191;
   private final NumberSetting sliderSetting192;
   private final ColorSetting colorSetting7;
   private final ColorSetting colorSetting8;
   private final BooleanSetting checkboxSetting156;
   private final NumberSetting sliderSetting193;
   private final NumberSetting sliderSetting194;
   private final BooleanSetting checkboxSetting157;
   private final NumberSetting sliderSetting195;
   private final BooleanSetting checkboxSetting158;
   private final VisualEventListener<FogSettingsEvent> systemClass405_258;
   private final VisualEventListener<WorldChangeEvent> systemClass405_259;
   private static final String AMBIENCE = "Ambience";
   private static final String ИЗМЕНЕНИЕ_ПОГОДЫ_МИРА = "Изменение погоды мира";
   private static final String COLISEUM_WEATHER_PARTICLES = "coliseum/coliseimvisual/weather_particles";
   private static final String WEATHER_PARTICLES = "weather_particles";
   private static final String WEATHER_PARTICLES1 = "weather_particles";
   private static final String WEATHER = "Weather";
   private static final String FOG = "Fog";
   private static final String COLISEUM_WEATHER_RAIN = "coliseum/coliseimvisual/weather_rain";
   private static final String WEATHER_PARTICLES2 = "weather_particles";
   private static final String WEATHER_PARTICLES3 = "weather_particles";
   private static final String COLISEUM_RAIN = "COLISEUM_RAIN";
   private static final String WEATHER1 = "Weather";
   private static final String FOG1 = "Fog";
   private static final String RAIN_ROOF = "RainRoof";
   private static final String ROOF_SAMPLER = "RoofSampler";
   private static final String COLISEUM_WEATHER_RAIN_GROUND = "coliseum/coliseimvisual/weather_rain_ground";
   private static final String WEATHER_GROUND = "weather_ground";
   private static final String WEATHER_GROUND1 = "weather_ground";
   private static final String COLISEUM_RAIN1 = "COLISEUM_RAIN";
   private static final String WEATHER2 = "Weather";
   private static final String FOG2 = "Fog";
   private static final String RAIN_ROOF1 = "RainRoof";
   private static final String DEPTH_SAMPLER = "DepthSampler";
   private static final String ROOF_SAMPLER1 = "RoofSampler";
   private static final String COLISEUM_WEATHER_GROUND = "coliseum/coliseimvisual/weather_ground";
   private static final String WEATHER_GROUND2 = "weather_ground";
   private static final String WEATHER_GROUND3 = "weather_ground";
   private static final String WEATHER3 = "Weather";
   private static final String FOG3 = "Fog";
   private static final String DEPTH_SAMPLER1 = "DepthSampler";
   private static final String COLISEUM_AMBIENCE_BLIZZARD = "coliseum/coliseimvisual/ambience_blizzard";
   private static final String HANDS_FULLSCREEN = "hands_fullscreen";
   private static final String AMBIENCE_BLIZZARD = "ambience_blizzard";
   private static final String BLIZZARD = "Blizzard";
   private static final String DEPTH_SAMPLER2 = "DepthSampler";
   private static final String COLISEUM_AMBIENCE_SHADER_FOG = "coliseum/coliseimvisual/ambience_shader_fog";
   private static final String HANDS_FULLSCREEN1 = "hands_fullscreen";
   private static final String AMBIENCE_SHADER_FOG = "ambience_shader_fog";
   private static final String SHADER_FOG = "ShaderFog";
   private static final String DEPTH_SAMPLER3 = "DepthSampler";
   private static final String COLISEUM_SHADER_MIST_PULSAR = "coliseum/coliseimvisual/shader_mist_pulsar";
   private static final String HANDS_FULLSCREEN2 = "hands_fullscreen";
   private static final String SHADER_MIST_PULSAR = "shader_mist_pulsar";
   private static final String SHADER_MIST = "ShaderMist";
   private static final String DEPTH_SAMPLER4 = "DepthSampler";
   private static final String COLISEUM_SHADER_MIST_GALAXY = "coliseum/coliseimvisual/shader_mist_galaxy";
   private static final String HANDS_FULLSCREEN3 = "hands_fullscreen";
   private static final String SHADER_MIST_GALAXY = "shader_mist_galaxy";
   private static final String SHADER_MIST1 = "ShaderMist";
   private static final String DEPTH_SAMPLER5 = "DepthSampler";
   private static final String COLISEUM_SHADER_MIST_JUICE = "coliseum/coliseimvisual/shader_mist_juice";
   private static final String HANDS_FULLSCREEN4 = "hands_fullscreen";
   private static final String SHADER_MIST_JUICE = "shader_mist_juice";
   private static final String SKY_SHADER_DATA = "SkyShaderData";
   private static final String DEPTH_SAMPLER6 = "DepthSampler";
   private static final String COLISEUM_AMBIENCE_SHADER_SKY = "coliseum/coliseimvisual/ambience_shader_sky_composite";
   private static final String HANDS_FULLSCREEN5 = "hands_fullscreen";
   private static final String AMBIENCE_SHADER_SKY_COMPOSITE = "ambience_shader_sky_composite";
   private static final String SKY_SAMPLER = "SkySampler";
   private static final String DEPTH_SAMPLER7 = "DepthSampler";
   private static final String COLISEUM_AMBIENCE_WORLD_COLOR = "coliseum/coliseimvisual/ambience_world_color";
   private static final String HANDS_FULLSCREEN6 = "hands_fullscreen";
   private static final String AMBIENCE_WORLD_COLOR = "ambience_world_color";
   private static final String WORLD_COLOR = "WorldColor";
   private static final String COLISEUM_AMBIENCE_LIGHTNING = "coliseum/coliseimvisual/ambience_lightning";
   private static final String AMBIENCE_LIGHTNING = "ambience_lightning";
   private static final String AMBIENCE_LIGHTNING1 = "ambience_lightning";
   private static final String LIGHTNING = "Lightning";
   private static final String COLISEUM_AMBIENCE_LIGHTNING_CORE = "coliseum/coliseimvisual/ambience_lightning_core";
   private static final String AMBIENCE_LIGHTNING2 = "ambience_lightning";
   private static final String AMBIENCE_LIGHTNING_CORE = "ambience_lightning_core";
   private static final String LIGHTNING1 = "Lightning";
   private static final String COLISEUM_AMBIENCE_LIGHTNING_FLASH = "coliseum/coliseimvisual/ambience_lightning_flash";
   private static final String HANDS_FULLSCREEN7 = "hands_fullscreen";
   private static final String AMBIENCE_LIGHTNING_FLASH = "ambience_lightning_flash";
   private static final String LIGHTNING_FLASH = "LightningFlash";
   private static final String DEPTH_SAMPLER8 = "DepthSampler";
   private static final String COLISEUM_WEATHER_GROUND_COMPOSITE = "coliseum/coliseimvisual/weather_ground_composite";
   private static final String HANDS_PASS = "hands_pass";
   private static final String WEATHER_GROUND_COMPOSITE = "weather_ground_composite";
   private static final String SAMPLER0 = "Sampler0";
   private static final String COLISEUM_AMBIENCE_LIGHTNING_GLOW = "coliseum/coliseimvisual/ambience_lightning_glow_mask";
   private static final String HANDS_FULLSCREEN8 = "hands_fullscreen";
   private static final String AMBIENCE_LIGHTNING_GLOW_MASK = "ambience_lightning_glow_mask";
   private static final String IN_SAMPLER = "InSampler";
   private static final String COLISEUM_AMBIENCE_LIGHTNING_GLOW1 = "coliseum/coliseimvisual/ambience_lightning_glow_blur";
   private static final String HANDS_FULLSCREEN9 = "hands_fullscreen";
   private static final String AMBIENCE_LIGHTNING_GLOW_BLUR = "ambience_lightning_glow_blur";
   private static final String LIGHTNING_GLOW_BLUR = "LightningGlowBlur";
   private static final String IN_SAMPLER1 = "InSampler";
   private static final String COLISEUM_AMBIENCE_LIGHTNING_GLOW2 = "coliseum/coliseimvisual/ambience_lightning_glow_composite";
   private static final String HANDS_FULLSCREEN10 = "hands_fullscreen";
   private static final String AMBIENCE_LIGHTNING_GLOW_COMPOSITE = "ambience_lightning_glow_composite";
   private static final String LIGHTNING_GLOW_COMPOSITE = "LightningGlowComposite";
   private static final String BLOOM_SAMPLER = "BloomSampler";
   private static final int COUNT93 = Integer.MIN_VALUE;
   private static final long VALUE1986 = -1L;
   private static final String ТУМАН = "Туман";
   private static final String НИЧЕГО_НЕ_ДЕЛАТЬ = "Ничего не делать";
   private static final String ОЧИСТИТЬ = "Очистить";
   private static final String ПЕРЕОПРЕДЕЛИТЬ = "Переопределить";
   private static final String КОНЕЦ_ТУМАНА = "Конец тумана";
   private static final float VALUE1987 = 0.1F;
   private static final float VALUE1988 = 1.5F;
   private static final float VALUE1989 = 0.1F;
   private static final String НАЧАЛО_ТУМАНА = "Начало тумана";
   private static final float VALUE1990 = 0.5F;
   private static final float VALUE1991 = 0.1F;
   private static final float VALUE1992 = 1.4F;
   private static final float VALUE1993 = 0.1F;
   private static final String АВТО_ЦВЕТ_ПОД_ТЕМУ1 = "Авто цвет под тему";
   private static final String ЦВЕТ_ТУМАНА = "Цвет тумана";
   private static final String ШЕЙДЕР_НЕБА = "Шейдер неба";
   private static final String НИЧЕГО = "Ничего";
   private static final String ПУЛЬСАР = "Пульсар";
   private static final String ГАЛАКТИКА = "Галактика";
   private static final String СОК = "Сок";
   private static final String КОСМОС = "Космос";
   private static final String МЛЕЧНЫЙ_ПУТЬ = "Млечный Путь";
   private static final String PURPLE = "Purple";
   private static final String АВРОРА = "Аврора";
   private static final String АВРОРА_2 = "Аврора 2";
   private static final String ТУМАННОСТЬ = "Туманность";
   private static final String ПЛАЗМА = "Плазма";
   private static final String ИСТОЧНИК = "Источник";
   private static final String НЕБУЛА = "Небула";
   private static final String ИНТЕНСИВНОСТЬ_ШЕЙДЕРА = "Интенсивность шейдера";
   private static final float VALUE1994 = 1.5F;
   private static final float VALUE1995 = 0.05F;
   private static final String СКОРОСТЬ_ШЕЙДЕРА = "Скорость шейдера";
   private static final float VALUE1996 = 0.1F;
   private static final float VALUE1997 = 5.0F;
   private static final float VALUE1998 = 0.05F;
   private static final String ЗВЁЗДЫ = "Звёзды";
   private static final String СКОРОСТЬ_НЕБА = "Скорость неба";
   private static final float VALUE1999 = 0.1F;
   private static final float VALUE2000 = 5.0F;
   private static final float VALUE2001 = 0.1F;
   private static final String РАЗМЕР_НЕБА = "Размер неба";
   private static final float VALUE2002 = 5.0F;
   private static final float VALUE2003 = 20.0F;
   private static final float VALUE2004 = 0.5F;
   private static final String ИНТЕНСИВНОСТЬ_НЕБА = "Интенсивность неба";
   private static final float VALUE2005 = 0.01F;
   private static final float VALUE2006 = 0.001F;
   private static final float VALUE2007 = 0.05F;
   private static final float VALUE2008 = 0.001F;
   private static final String ПРОЗРАЧНОСТЬ_НЕБА = "Прозрачность неба";
   private static final float VALUE2009 = 0.3F;
   private static final float VALUE2010 = 0.05F;
   private static final String СИЛА_АВРОРЫ = "Сила Авроры";
   private static final float VALUE2011 = 0.1F;
   private static final float VALUE2012 = 0.05F;
   private static final String СКОРОСТЬ_АВРОРЫ = "Скорость Авроры";
   private static final float VALUE2013 = 0.1F;
   private static final float VALUE2014 = 3.0F;
   private static final float VALUE2015 = 0.1F;
   private static final String МАСШТАБ_АВРОРЫ = "Масштаб Авроры";
   private static final float VALUE2016 = 1.1F;
   private static final float VALUE2017 = 0.45F;
   private static final float VALUE2018 = 3.0F;
   private static final float VALUE2019 = 0.05F;
   private static final String ЗВЁЗДЫ_АВРОРЫ = "Звёзды Авроры";
   private static final float VALUE2020 = 0.75F;
   private static final float VALUE2021 = 0.05F;
   private static final String ЦВЕТ_АВРОРЫ_1 = "Цвет Авроры 1";
   private static final int COUNT94 = -8788225;
   private static final String ЦВЕТ_АВРОРЫ_2 = "Цвет Авроры 2";
   private static final int COUNT95 = -34074;
   private static final String ВАНИЛЬНОЕ_НЕБО = "Ванильное небо";
   private static final String ПОГОДА = "Погода";
   private static final String НИЧЕГО_НЕ_ДЕЛАТЬ1 = "Ничего не делать";
   private static final String ОЧИСТИТЬ1 = "Очистить";
   private static final String ДОЖДЬ = "Дождь";
   private static final String СНЕГ = "Снег";
   private static final String ПУРГА = "Пурга";
   private static final String ВРЕМЯ = "Время";
   private static final String ОБЫЧНОЕ = "Обычное";
   private static final String УТРО = "Утро";
   private static final String ДЕНЬ = "День";
   private static final String ВЕЧЕР = "Вечер";
   private static final String ЗАКАТ = "Закат";
   private static final String НОЧЬ = "Ночь";
   private static final String ЦВЕТ_МИРА = "Цвет мира";
   private static final String НИЧЕГО1 = "Ничего";
   private static final String ТЕМА = "Тема";
   private static final String СВОЙ = "Свой";
   private static final String ОТТЕНОК_МИРА = "Оттенок мира";
   private static final String СИЛА_ЦВЕТА_МИРА = "Сила цвета мира";
   private static final float VALUE2022 = 0.08F;
   private static final float VALUE2023 = 0.25F;
   private static final float VALUE2024 = 0.01F;
   private static final String ИНТЕНСИВНОСТЬ_ПОГОДЫ = "Интенсивность погоды";
   private static final float VALUE2025 = 0.65F;
   private static final float VALUE2026 = 0.1F;
   private static final float VALUE2027 = 0.05F;
   private static final String ДАЛЬНОСТЬ_ПОГОДЫ = "Дальность погоды";
   private static final float VALUE2028 = 32.0F;
   private static final float VALUE2029 = 12.0F;
   private static final float VALUE2030 = 64.0F;
   private static final String СКОРОСТЬ_ПАДЕНИЯ = "Скорость падения";
   private static final float VALUE2031 = 0.25F;
   private static final float VALUE2032 = 0.05F;
   private static final String РАЗМЕР_ЧАСТИЦ_ПОГОДЫ = "Размер частиц погоды";
   private static final float VALUE2033 = 0.5F;
   private static final float VALUE2034 = 0.05F;
   private static final String СКОРОСТЬ_ВЕТРА = "Скорость ветра";
   private static final float VALUE2035 = 1.5F;
   private static final float VALUE2036 = 12.0F;
   private static final float VALUE2037 = 0.1F;
   private static final String НАПРАВЛЕНИЕ_ВЕТРА = "Направление ветра";
   private static final float VALUE2038 = -180.0F;
   private static final float VALUE2039 = 180.0F;
   private static final float VALUE2040 = 5.0F;
   private static final String ЭФФЕКТЫ_ПОГОДЫ_НА_ЗЕМЛЕ = "Эффекты погоды на земле";
   private static final String ВРЕМЯ_ЭФФЕКТА_НА_ЗЕМЛЕ = "Время эффекта на земле";
   private static final float VALUE2041 = 0.6F;
   private static final float VALUE2042 = 0.15F;
   private static final float VALUE2043 = 1.5F;
   private static final float VALUE2044 = 0.05F;
   private static final String РАЗМЕР_ЭФФЕКТА_НА_ЗЕМЛЕ = "Размер эффекта на земле";
   private static final float VALUE2045 = 0.45F;
   private static final float VALUE2046 = 0.15F;
   private static final float VALUE2047 = 1.2F;
   private static final float VALUE2048 = 0.05F;
   private static final String ЦВЕТ_ДОЖДЯ = "Цвет дождя";
   private static final int COUNT96 = -4799020;
   private static final String ЦВЕТ_СНЕГА = "Цвет снега";
   private static final String МОЛНИИ4 = "Молнии";
   private static final String СКОРОСТЬ_ПОЯВЛЕНИЯ = "Скорость появления";
   private static final float VALUE2049 = 0.25F;
   private static final float VALUE2050 = 4.0F;
   private static final float VALUE2051 = 0.05F;
   private static final String КОЛИЧЕСТВО_МОЛНИЙ = "Количество молний";
   private static final float VALUE2052 = 6.0F;
   private static final String ЗВУКИ_ГРОМА = "Звуки грома";
   private static final String ГРОМКОСТЬ_ГРОМА = "Громкость грома";
   private static final float VALUE2053 = 0.5F;
   private static final float VALUE2054 = 0.05F;
   private static final String УДАЛЯТЬ_ОБЛАКА = "Удалять Облака";
   private static final String SETTINGS11 = "Settings";
   private static final String SETTINGS12 = "Settings";
   private static final String ШЕЙДЕРНОЕ_НЕБО = "Шейдерное небо";
   private static final String ШЕЙДЕРНОЕ_НЕБО1 = "Шейдерное небо";
   private static final String ПУЛЬСАР1 = "Пульсар";
   private static final String ШЕЙДЕР_ТУМАНА = "Шейдер тумана";
   private static final String ШЕЙДЕР_ТУМАНА1 = "Шейдер тумана";
   private static final String НИЧЕГО2 = "Ничего";
   private static final String ОЧИСТИТЬ2 = "Очистить";
   private static final String ПЕРЕОПРЕДЕЛИТЬ1 = "Переопределить";
   private static final String НИЧЕГО_НЕ_ДЕЛАТЬ2 = "Ничего не делать";
   private static final String ДОЖДЬ1 = "Дождь";
   private static final float VALUE2055 = 0.4F;
   private static final String ДОЖДЬ2 = "Дождь";
   private static final String СНЕГ1 = "Снег";
   private static final String ПУРГА1 = "Пурга";
   private static final String ПУРГА2 = "Пурга";
   private static final String НИЧЕГО_НЕ_ДЕЛАТЬ3 = "Ничего не делать";
   private static final float VALUE2056 = 1.0E7F;
   private static final float VALUE2057 = 1.0E7F;
   private static final float VALUE2058 = 16.0F;
   private static final float VALUE2059 = 1.0E7F;
   private static final float VALUE2060 = 1.0000001E7F;
   private static final int COUNT97 = -16777216;
   private static final float VALUE2061 = 0.1F;
   private static final float VALUE2062 = 0.85F;
   private static final float VALUE2063 = 0.38F;
   private static final float VALUE2064 = 0.12F;
   private static final int COUNT98 = -3418914;
   private static final float VALUE2065 = 96.0F;
   private static final float VALUE2066 = 16.0F;
   private static final float VALUE2067 = 16.0F;
   private static final float VALUE2068 = 4.0F;
   private static final float VALUE2069 = 8.0F;
   private static final long VALUE2070 = 240000L;
   private static final float VALUE2071 = 20.0F;
   private static final long VALUE2072 = -1L;
   private static final String ТЕМА1 = "Тема";
   private static final int COUNT99 = -16777216;
   private static final float VALUE2073 = 0.2126F;
   private static final float VALUE2074 = 0.7152F;
   private static final float VALUE2075 = 0.0722F;
   private static final float VALUE2076 = 0.08F;
   private static final float VALUE2077 = 0.32F;
   private static final float VALUE2078 = 0.32F;
   private static final float VALUE2079 = 0.32F;
   private static final String WORLD_COLOR1 = "WorldColor";
   private static final String НИЧЕГО3 = "Ничего";
   private static final long VALUE2080 = 16L;
   private static final int COUNT100 = Integer.MIN_VALUE;
   private static final int COUNT101 = Integer.MIN_VALUE;
   private static final float VALUE2081 = 0.25F;
   private static final float VALUE2082 = 0.25F;
   private static final float VALUE2083 = 0.25F;
   private static final double VALUE2084 = 255.0;
   private static final String SKY_SAMPLER1 = "SkySampler";
   private static final String DEPTH_SAMPLER9 = "DepthSampler";
   private static final float VALUE2085 = 1000.0F;
   private static final float VALUE2086 = 0.999999F;
   private static final String DEPTH_SAMPLER10 = "DepthSampler";
   private static final String SHADER_MIST2 = "ShaderMist";
   private static final float VALUE2087 = 1000.0F;
   private static final int COUNT102 = -16777216;
   private static final int COUNT103 = -16777216;
   private static final float VALUE2088 = 0.5F;
   private static final int COUNT104 = -16777216;
   private static final String DEPTH_SAMPLER11 = "DepthSampler";
   private static final String SKY_SHADER_DATA1 = "SkyShaderData";
   private static final String AMBIENCE_SHADER_SKY_LAYER = "Ambience shader sky layer";
   private static final long VALUE2089 = 80L;
   private static final long VALUE2090 = 240L;
   private static final long VALUE2091 = 160L;
   private static final long VALUE2092 = -1L;
   private static final long VALUE2093 = 240000L;
   private static final float VALUE2094 = 20.0F;
   private static final String ИСТОЧНИК1 = "Источник";
   private static final String НЕБУЛА1 = "Небула";
   private static final String КОСМОС1 = "Космос";
   private static final String PURPLE1 = "Purple";
   private static final String АВРОРА1 = "Аврора";
   private static final String ПЛАЗМА1 = "Плазма";
   private static final String МЛЕЧНЫЙ_ПУТЬ1 = "Млечный Путь";
   private static final int COUNT105 = -16777216;
   private static final int COUNT106 = -16777216;
   private static final float VALUE2095 = 0.5F;
   private static final int COUNT107 = -16777216;
   private static final int COUNT108 = -16777216;
   private static final long VALUE2096 = 1000000L;
   private static final float VALUE2097 = 1000.0F;
   private static final float VALUE2098 = 0.999999F;
   private static final float VALUE2099 = 0.5222222F;
   private static final String DEPTH_SAMPLER12 = "DepthSampler";
   private static final String SHADER_FOG1 = "ShaderFog";
   private static final String ПУРГА3 = "Пурга";
   private static final float VALUE2100 = 4.0F;
   private static final float VALUE2101 = 3.5F;
   private static final float VALUE2102 = 0.7F;
   private static final float VALUE2103 = 2.2F;
   private static final float VALUE2104 = 0.23F;
   private static final float VALUE2105 = 1.7F;
   private static final float VALUE2106 = 3.0F;
   private static final float VALUE2107 = 0.11F;
   private static final float VALUE2108 = 14.0F;
   private static final float VALUE2109 = 12.0F;
   private static final float VALUE2110 = 0.55F;
   private static final float VALUE2111 = 1.25F;
   private static final double VALUE2112 = 4096.0;
   private static final double VALUE2113 = 4096.0;
   private static final double VALUE2114 = 4096.0;
   private static final double VALUE2115 = 4096.0;
   private static final String DEPTH_SAMPLER13 = "DepthSampler";
   private static final String BLIZZARD1 = "Blizzard";
   private static final float VALUE2116 = 2.0E9F;
   private static final float VALUE2117 = 0.52F;
   private static final float VALUE2118 = 0.001F;
   private static final int COUNT109 = -16777216;
   private static final float VALUE2119 = 0.28F;
   private static final float VALUE2120 = 0.72F;
   private static final float VALUE2121 = 0.34F;
   private static final float VALUE2122 = 3.8F;
   private static final float VALUE2123 = 4.6F;
   private static final float VALUE2124 = 0.105F;
   private static final float VALUE2125 = 0.034F;
   private static final float VALUE2126 = 0.27F;
   private static final long VALUE2127 = 48L;
   private static final String DEPTH_SAMPLER14 = "DepthSampler";
   private static final String LIGHTNING_FLASH1 = "LightningFlash";
   private static final float VALUE2128 = 1.0E-4F;
   private static final float VALUE2129 = 0.5F;
   private static final float VALUE2130 = 0.5F;
   private static final float VALUE2131 = 0.5F;
   private static final float VALUE2132 = 0.5F;
   private static final String AMBIENCE_LIGHTNING_GLOW = "Ambience lightning glow";
   private static final float VALUE2133 = 0.001F;
   private static final String IN_SAMPLER2 = "InSampler";
   private static final String BLOOM_SAMPLER1 = "BloomSampler";
   private static final String LIGHTNING_GLOW_COMPOSITE1 = "LightningGlowComposite";
   private static final String IN_SAMPLER3 = "InSampler";
   private static final String LIGHTNING_GLOW_BLUR1 = "LightningGlowBlur";
   private static final float VALUE2134 = 12.0F;
   private static final float VALUE2135 = 10.0F;
   private static final float VALUE2136 = 12.0F;
   private static final float VALUE2137 = 10.0F;
   private static final int COUNT110 = -16777216;
   private static final int COUNT111 = -16777216;
   private static final String AMBIENCE_LIGHTNING_GLOW_HORIZONTAL = "Ambience lightning glow horizontal";
   private static final String AMBIENCE_LIGHTNING_GLOW_VERTICAL = "Ambience lightning glow vertical";
   private static final long VALUE2138 = 32L;
   private static final long VALUE2139 = 32L;
   private static final long VALUE2140 = 48L;
   private static final double VALUE2141 = 1.0E-8;
   private static final double VALUE2142 = 0.5;
   private static final double VALUE2143 = 1.0E-8;
   private static final double VALUE2144 = 1.0E-8;
   private static final double VALUE2145 = 2.0;
   private static final float VALUE2146 = 0.1F;
   private static final float VALUE2147 = 1.35F;
   private static final float VALUE2148 = 1.5F;
   private static final float VALUE2149 = -1.0F;
   private static final float VALUE2150 = -1.0F;
   private static final String LIGHTNING2 = "Lightning";
   private static final long VALUE2151 = 131072L;
   private static final long VALUE2152 = 144L;
   private static final double VALUE2153 = 343.0;
   private static final double VALUE2154 = 20.0;
   private static final double VALUE2155 = -1.7278759594743864;
   private static final double VALUE2156 = 1.7278759594743864;
   private static final double VALUE2157 = 52.0;
   private static final double VALUE2158 = 96.0;
   private static final double VALUE2159 = 6.0;
   private static final double VALUE2160 = 58.0;
   private static final double VALUE2161 = 92.0;
   private static final double VALUE2162 = 36.0;
   private static final double VALUE2163 = -8.0;
   private static final double VALUE2164 = 8.0;
   private static final double VALUE2165 = -8.0;
   private static final double VALUE2166 = 8.0;
   private static final double VALUE2167 = 0.5;
   private static final double VALUE2168 = 8.5;
   private static final double VALUE2169 = 8.5;
   private static final double VALUE2170 = 0.5;
   private static final float VALUE2171 = 0.68F;
   private static final double VALUE2172 = Math.PI * 2;
   private static final float VALUE2173 = 0.7F;
   private static final double VALUE2174 = 1.35;
   private static final double VALUE2175 = 0.34;
   private static final double VALUE2176 = -0.65;
   private static final double VALUE2177 = 0.65;
   private static final double VALUE2178 = 1.7;
   private static final double VALUE2179 = 3.5;
   private static final double VALUE2180 = -0.65;
   private static final double VALUE2181 = 0.65;
   private static final float VALUE2182 = 0.18F;
   private static final double VALUE2183 = 0.5;
   private static final double VALUE2184 = 2.0;
   private static final double VALUE2185 = 0.57;
   private static final double VALUE2186 = 0.57;
   private static final double VALUE2187 = 0.82;
   private static final double VALUE2188 = 1.18;
   private static final float VALUE2189 = 0.25F;
   private static final float VALUE2190 = 0.25F;
   private static final double VALUE2191 = 1.0E9;
   private static final float VALUE2192 = 0.045F;
   private static final float VALUE2193 = 0.045F;
   private static final float VALUE2194 = 0.095F;
   private static final float VALUE2195 = 0.165F;
   private static final float VALUE2196 = 0.095F;
   private static final float VALUE2197 = 0.07F;
   private static final float VALUE2198 = 0.58F;
   private static final float VALUE2199 = 0.52F;
   private static final float VALUE2200 = 0.165F;
   private static final float VALUE2201 = 0.355F;
   private static final float VALUE2202 = 0.58F;
   private static final float VALUE2203 = 3.0F;
   private static final String ДОЖДЬ3 = "Дождь";
   private static final String СНЕГ2 = "Снег";
   private static final float VALUE2204 = 1.0E9F;
   private static final float VALUE2205 = 0.05F;
   private static final float VALUE2206 = 4.0F;
   private static final float VALUE2207 = 3.5F;
   private static final float VALUE2208 = 0.7F;
   private static final float VALUE2209 = 2.2F;
   private static final float VALUE2210 = 0.23F;
   private static final float VALUE2211 = 1.7F;
   private static final float VALUE2212 = 3.0F;
   private static final float VALUE2213 = 0.11F;
   private static final float VALUE2214 = 14.0F;
   private static final float VALUE2215 = -18.0F;
   private static final float VALUE2216 = -4.5F;
   private static final float VALUE2217 = 0.01F;
   private static final float VALUE2218 = 0.35F;
   private static final float VALUE2219 = 1.0E-4F;
   private static final float VALUE2220 = 18.0F;
   private static final float VALUE2221 = 4.5F;
   private static final float VALUE2222 = 1.8F;
   private static final float VALUE2223 = 0.001F;
   private static final float VALUE2224 = 1.1F;
   private static final float VALUE2225 = 20.0F;
   private static final float VALUE2226 = 48.0F;
   private static final float VALUE2227 = 64.0F;
   private static final float VALUE2228 = 0.5F;
   private static final double VALUE2229 = 32.0;
   private static final double VALUE2230 = 32.0;
   private static final double VALUE2231 = 4096.0;
   private static final double VALUE2232 = 4096.0;
   private static final float VALUE2233 = 0.85F;
   private static final float VALUE2234 = 2.6F;
   private static final float VALUE2235 = 1.25F;
   private static final double VALUE2236 = 4096.0;
   private static final float VALUE2237 = 0.12F;
   private static final float VALUE2238 = 0.88F;
   private static final float VALUE2239 = 8192.0F;
   private static final double VALUE2240 = 4096.0;
   private static final double VALUE2241 = 4096.0;
   private static final double VALUE2242 = 4096.0;
   private static final float VALUE2243 = 0.38F;
   private static final float VALUE2244 = 0.39999998F;
   private static final String ДОЖДЬ4 = "Дождь";
   private static final String WEATHER4 = "Weather";
   private static final String FOG4 = "Fog";
   private static final String ДОЖДЬ5 = "Дождь";
   private static final String DEPTH_SAMPLER15 = "DepthSampler";
   private static final String WEATHER5 = "Weather";
   private static final String FOG5 = "Fog";
   private static final String SAMPLER0_1 = "Sampler0";
   private static final long VALUE2245 = 400L;
   private static final String AMBIENCE_WEATHER_GROUND_LAYER = "Ambience weather ground layer";
   private static final String НИЧЕГО4 = "Ничего";
   private static final String ORIGIN = "Origin";
   private static final String ИСТОЧНИК2 = "Источник";
   private static final String NEBULA = "Nebula";
   private static final String НЕБУЛА2 = "Небула";
   private static final String ORIGIN1 = "Origin";
   private static final String ИСТОЧНИК3 = "Источник";
   private static final String NEBULA1 = "Nebula";
   private static final String НЕБУЛА3 = "Небула";
   private static final String ИСТОЧНИК4 = "Источник";
   private static final String НЕБУЛА4 = "Небула";
   private static final String ПУЛЬСАР2 = "Пульсар";
   private static final String ГАЛАКТИКА1 = "Галактика";
   private static final String АВРОРА_2_1 = "Аврора 2";
   private static final String СОК1 = "Сок";
   private static final float VALUE2246 = 1.0E-4F;
   private static final float VALUE2247 = 70.0F;
   private static final double VALUE2248 = 2.0;
   private static final float VALUE2249 = -1.0F;
   private static final float VALUE2250 = -1.0F;
   private static final float VALUE2251 = -1.0F;
   private static final float VALUE2252 = -1.0F;
   private static final String AMBIENCE_SHARED_FULLSCREEN_QUAD = "Ambience shared fullscreen quad";
   private static final String AMBIENCE_CUSTOM_WEATHER_UNIFORMS = "Ambience custom weather uniforms";
   private static final String AMBIENCE_CUSTOM_WEATHER_GROUND = "Ambience custom weather ground composite";
   private static final String AMBIENCE_CUSTOM_WEATHER_GROUND1 = "Ambience custom weather ground effects";
   private static final String AMBIENCE_CUSTOM_WEATHER_PARTICLES = "Ambience custom weather particles";
   private static final String AMBIENCE_LIGHTNING3 = "Ambience lightning";
   private static final String AMBIENCE_LIGHTNING_GLOW_COMPOSITE1 = "Ambience lightning glow composite uniforms";
   private static final String AMBIENCE_LIGHTNING_VERTICAL_GLOW = "Ambience lightning vertical glow uniforms";
   private static final String AMBIENCE_LIGHTNING_HORIZONTAL_GLOW = "Ambience lightning horizontal glow uniforms";
   private static final String AMBIENCE_LIGHTNING_DUAL_GLOW = "Ambience lightning dual glow blur";
   private static final String AMBIENCE_LIGHTNING_GLOW_COMPOSITE2 = "Ambience lightning glow composite";
   private static final String AMBIENCE_LIGHTNING_GLOW_MASK1 = "Ambience lightning glow mask";
   private static final String AMBIENCE_LIGHTNING_FLASH1 = "Ambience lightning flash";
   private static final String AMBIENCE_LIGHTNING_FLASH_UNIFORMS = "Ambience lightning flash uniforms";
   private static final String AMBIENCE_BLIZZARD_OVERLAY = "Ambience blizzard overlay";
   private static final String AMBIENCE_BLIZZARD_OVERLAY_UNIFORMS = "Ambience blizzard overlay uniforms";
   private static final String AMBIENCE_PROCEDURAL_SKY = "Ambience procedural sky";
   private static final String AMBIENCE_JUICE_SKY_UNIFORMS = "Ambience juice sky uniforms";
   private static final String AMBIENCE_PROCEDURAL_SKY_UNIFORMS = "Ambience procedural sky uniforms";
   private static final String AMBIENCE_SHADER_SKY_UNIFORMS = "Ambience shader sky uniforms";
   private static final String AMBIENCE_JUICE_SKY = "Ambience juice sky";
   private static final String AMBIENCE_SHADER_SKY = "Ambience shader sky";
   private static final String AMBIENCE_SHADER_SKY_COMPOSITE1 = "Ambience shader sky composite";
   private static final String AMBIENCE_WORLD_COLOR_UNIFORMS = "Ambience world color uniforms";
   private static final String AMBIENCE_WORLD_COLOR1 = "Ambience world color";
   private static final String СНЕГ3 = "Снег";
   private static final String ПУРГА4 = "Пурга";
   private static final String ДОЖДЬ6 = "Дождь";
   private static final String НИЧЕГО5 = "Ничего";
   private static final String СВОЙ1 = "Свой";
   private static final String ПЕРЕОПРЕДЕЛИТЬ2 = "Переопределить";
   private static final String ПЕРЕОПРЕДЕЛИТЬ3 = "Переопределить";
   private static final String ПЕРЕОПРЕДЕЛИТЬ4 = "Переопределить";

   public boolean is174() {
      return this.is() && this.multiChoiceSetting56.is(ПЕРЕОПРЕДЕЛИТЬ1);
   }

   private void apply520(JsonObject var1) {
      if (var1.has(this.multiChoiceSetting57.name())) {
         com.google.gson.JsonElement el = var1.get(this.multiChoiceSetting57.name());
         if (el != null && el.isJsonPrimitive()) {
            String var2 = el.getAsString();
            if (ORIGIN.equals(var2)) {
               var1.addProperty(this.multiChoiceSetting57.name(), ИСТОЧНИК2);
            } else if (NEBULA.equals(var2)) {
               var1.addProperty(this.multiChoiceSetting57.name(), НЕБУЛА2);
            }
         }
      }

      String var4 = this.multiChoiceSetting57.name() + "";
      if (var1.has(var4) && var1.get(var4).isJsonObject()) {
         JsonObject var3 = var1.getAsJsonObject(var4);
         apply525(var3, ORIGIN1, ИСТОЧНИК3);
         apply525(var3, NEBULA1, НЕБУЛА3);
      }
   }

   private void update362() {
      if (this.textureTarget1 != null) {
         this.textureTarget1.destroyBuffers();
         this.textureTarget1 = null;
      }
   }

   private void update363() {
      if (this.gpuBuffer2 == null || this.gpuBuffer2.isClosed()) {
         this.gpuBuffer2 = RenderSystem.getDevice().createBuffer(() -> AMBIENCE_SHADER_SKY_UNIFORMS, 136, VALUE2089);
      }

      if (this.gpuBuffer1 == null || this.gpuBuffer1.isClosed()) {
         this.gpuBuffer1 = RenderSystem.getDevice().createBuffer(() -> AMBIENCE_PROCEDURAL_SKY_UNIFORMS, 136, VALUE2090);
      }

      if (this.gpuBuffer3 == null || this.gpuBuffer3.isClosed()) {
         this.gpuBuffer3 = RenderSystem.getDevice().createBuffer(() -> AMBIENCE_JUICE_SKY_UNIFORMS, 136, VALUE2091);
      }
   }

   private void apply521(RenderTarget var1, GpuBuffer var2, GpuBufferSlice var3, CommandEncoder var4, int var5) {
      RenderPass var6 = var4.createRenderPass(() -> AMBIENCE_CUSTOM_WEATHER_PARTICLES, var1.getColorTextureView(), Optional.empty(), var1.getDepthTextureView(), OptionalDouble.empty());

      try {
         boolean var7 = this.multiChoiceSetting58.is(ДОЖДЬ4);
         var6.setPipeline(RenderSystem.getCompiledPipeline(var7 ? this.renderPipeline2 : this.renderPipeline1));
         if (var7) {
            this.systemClass111.bindHeightmap(var6);
         }

         var6.setUniform(WEATHER4, var2);
         var6.setUniform(FOG4, var3);
         this.apply526(var6, var5);
      } catch (Throwable var10) {
         if (var6 != null) {
            try {
               var6.close();
            } catch (Throwable var9) {
               var10.addSuppressed(var9);
            }
         }

         throw var10;
      }

      if (var6 != null) {
         var6.close();
      }
   }

   private boolean is175() {
      return this.multiChoiceSetting57.is(СОК1);
   }

   private void apply522(RenderTarget var1, CommandEncoder var2, Matrix4f var3, Matrix4f var4, DeltaTracker var5, int var6, int var7, float var8) {
      this.matrix4f.set(var3).invert();
      this.matrix4f1.set(var4).invert();
      float var9 = ((float)(mc.level.getGameTime() % VALUE2093) + var5.getGameTimeDeltaPartialTick(false)) / VALUE2094;
      float var10 = (float)Math.toRadians(-mc.gameRenderer.mainCamera().yRot());
      float var11 = (float)Math.toRadians(mc.gameRenderer.mainCamera().xRot());
      float var12 = var8;
      boolean var13 = this.multiChoiceSetting57.is(ИСТОЧНИК1);
      boolean var14 = this.multiChoiceSetting57.is(НЕБУЛА1);
      int var15 = this.multiChoiceSetting57.is(КОСМОС1)
         ? 1
         : (
            this.multiChoiceSetting57.is(PURPLE1)
               ? 2
               : (this.multiChoiceSetting57.is(АВРОРА1) ? 3 : (this.multiChoiceSetting57.is(ПЛАЗМА1) ? 5 : (this.multiChoiceSetting57.is(МЛЕЧНЫЙ_ПУТЬ1) ? 6 : (var13 ? 7 : (var14 ? 8 : 4)))))
         );
      int var16 = VisualTheme.themeColor(VisualThemeColor.MAIN) | COUNT105;
      int var17 = VisualTheme.themeColor(VisualThemeColor.MAIN_TWO) | COUNT106;
      Vector4f var18 = VisualColors.toRgbaVector(var16);
      Vector4f var19 = VisualColors.toRgbaVector(var17);
      Vector4f var20 = VisualColors.toRgbaVector(VisualColors.lerpColor(var16, var17, VALUE2095));
      float var21 = this.is180() && this.checkboxSetting153.enabled() ? 1.0F : 0.0F;
      if (var13 || var14) {
         Vector4f var22 = VisualColors.toRgbaVector(var16 | COUNT107);
         Vector4f var23 = VisualColors.toRgbaVector(var17 | COUNT108);
         var19 = var22;
         var20 = var22;
         var18 = var23;
      }

      float var34 = this.systemClass295.dayFraction(mc.level.getOverworldClockTime());
      float var35 = mc.level.getRainLevel(var5.getGameTimeDeltaPartialTick(false));
      float var24 = !var13 && !var14 ? (float)(System.currentTimeMillis() % VALUE2096) / VALUE2097 : var21;
      MemoryStack var25 = MemoryStack.stackPush();

      try {
         ByteBuffer var26 = Std140Builder.onStack(var25, 240)
            .putMat4f(this.matrix4f)
            .putMat4f(this.matrix4f1)
            .putVec4(var10, var11, var12, var9)
            .putVec4(var6, var7, var15, this.sliderSetting174.floatValue())
            .putVec4(var18.x, var18.y, var18.z, var18.w)
            .putVec4(this.sliderSetting175.floatValue(), VALUE2098, 1.0F, var24)
            .putVec4(var19.x, var19.y, var19.z, var19.w)
            .putVec4(var20.x, var20.y, var20.z, var20.w)
            .putVec4(var21, var34, var35, VALUE2099)
            .get();
         var2.writeToBuffer(this.gpuBuffer1.slice(), var26);
         RenderPass var27 = var2.createRenderPass(() -> AMBIENCE_PROCEDURAL_SKY, this.textureTarget.getColorTextureView(), Optional.empty());

         try {
            var27.setPipeline(RenderSystem.getCompiledPipeline(this.renderPipeline6));
            var27.setUniform(DEPTH_SAMPLER12, var1.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            var27.setUniform(SHADER_FOG1, this.gpuBuffer1);
            var27.draw(3, 1, 0, 0);
         } catch (Throwable var32) {
            if (var27 != null) {
               try {
                  var27.close();
               } catch (Throwable var31) {
                  var32.addSuppressed(var31);
               }
            }

            throw var32;
         }

         if (var27 != null) {
            var27.close();
         }
      } catch (Throwable var33) {
         if (var25 != null) {
            try {
               var25.close();
            } catch (Throwable var30) {
               var33.addSuppressed(var30);
            }
         }

         throw var33;
      }

      if (var25 != null) {
         var25.close();
      }
   }

   private boolean is176() {
      return !this.multiChoiceSetting57.is(НИЧЕГО4);
   }

   private Vector4f resolveVector4f(Vec3 var1, Vec3 var2, Matrix4f var3, Matrix4f var4) {
      Vec3 var5 = var1.subtract(var2);
      Vector4f var6 = new Vector4f((float)var5.x, (float)var5.y, (float)var5.z, 1.0F).mul(var4).mul(var3);
      if (var6.w <= VALUE2128) {
         return null;
      }

      var6.div(var6.w);
      return new Vector4f(var6.x * VALUE2129 + VALUE2130, var6.y * VALUE2131 + VALUE2132, 0.0F, 0.0F);
   }

   private int compute106(int var1) {
      return this.compute113(var1, VisualThemeColor.MAIN);
   }

   private boolean is177() {
      return this.multiChoiceSetting58.is(ПУРГА2) && this.multiChoiceSetting56.is(НИЧЕГО_НЕ_ДЕЛАТЬ3);
   }

   private void apply523(RenderTarget var1, Matrix4f var2, Matrix4f var3, Vec3 var4, float var5, Vector4f var6) {
      if (this.vec3_20 != null && this.vec3_21 != null) {
         Vector4f var7 = this.resolveVector4f(this.vec3_20, var4, var2, var3);
         Vector4f var8 = this.resolveVector4f(this.vec3_21, var4, var2, var3);
         if (var7 != null && var8 != null) {
            MemoryStack var9 = MemoryStack.stackPush();

            try {
               ByteBuffer var10 = Std140Builder.onStack(var9, 48)
                  .putVec4(var7.x, var7.y, var8.x, var8.y)
                  .putVec4(var5, (float)var1.width / Math.max(1, var1.height), VALUE2126, 0.0F)
                  .putVec4(var6.x, var6.y, var6.z, 1.0F)
                  .get();
               if (this.gpuBuffer9 == null || this.gpuBuffer9.isClosed()) {
                  this.gpuBuffer9 = RenderSystem.getDevice().createBuffer(() -> AMBIENCE_LIGHTNING_FLASH_UNIFORMS, 136, VALUE2127);
               }

               CommandEncoder var11 = RenderSystem.getDevice().createCommandEncoder();
               var11.writeToBuffer(this.gpuBuffer9.slice(), var10);
               RenderPass var12 = var11.createRenderPass(() -> AMBIENCE_LIGHTNING_FLASH1, var1.getColorTextureView(), Optional.empty());

               try {
                  var12.setPipeline(RenderSystem.getCompiledPipeline(this.renderPipeline14));
                  var12.setUniform(DEPTH_SAMPLER14, var1.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                  var12.setUniform(LIGHTNING_FLASH1, this.gpuBuffer9);
                  var12.draw(3, 1, 0, 0);
               } catch (Throwable var17) {
                  if (var12 != null) {
                     try {
                        var12.close();
                     } catch (Throwable var16) {
                        var17.addSuppressed(var16);
                     }
                  }

                  throw var17;
               }

               if (var12 != null) {
                  var12.close();
               }
            } catch (Throwable var18) {
               if (var9 != null) {
                  try {
                     var9.close();
                  } catch (Throwable var15) {
                     var18.addSuppressed(var15);
                  }
               }

               throw var18;
            }

            if (var9 != null) {
               var9.close();
            }
         }
      }
   }

   private boolean is178() {
      return this.is176() && !this.is184() && !this.is175() && !this.is193();
   }

   private void update364() {
      this.count146 = -1;
      this.vec3_22 = null;
      if (this.f_8483 != null) {
         this.f_8483.finish();
         mc.getSoundManager().stop(this.f_8483);
         this.f_8483 = null;
      }
   }

   private GpuBuffer createGpuBuffer(int var1) {
      GpuBuffer var2 = this.gpuBuffer11[var1];
      if (var2 != null && !var2.isClosed()) {
         return var2;
      }

      var2 = RenderSystem.getDevice().createBuffer(() -> var1 + "", 136, VALUE2152);
      this.gpuBuffer11[var1] = var2;
      return var2;
   }

   private void apply524(RenderPass var1) {
      this.apply526(var1, 1);
   }

   private GpuBuffer getGpuBuffer() {
      if (this.gpuBuffer4 == null || this.gpuBuffer4.isClosed()) {
         this.gpuBuffer4 = RenderSystem.getDevice().createBuffer(() -> AMBIENCE_CUSTOM_WEATHER_UNIFORMS, 136, VALUE2245);
      }

      return this.gpuBuffer4;
   }

   private static void apply525(JsonObject var0, String var1, String var2) {
      if (var0.has(var1) && !var0.has(var2)) {
         var0.add(var2, var0.get(var1));
         var0.remove(var1);
      }
   }

   public long compute107(long var1) {
      return this.is() && !this.systemClass295.usesWorldTime() ? this.systemClass295.resolveDayTime(var1) : var1;
   }

   private void apply526(RenderPass var1, int var2) {
      GpuBuffer var3 = this.getGpuBuffer1();
      AutoStorageIndexBuffer var4 = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
      GpuBuffer var5 = var4.getBuffer(this.count145);
      var1.setVertexBuffer(0, var3.slice());
      var1.setIndexBuffer(var5, var4.type());
      var1.drawIndexed(this.count145, var2, 0, 0, 0);
   }

   private GpuBuffer resolveGpuBuffer(int var1, int var2) {
      GpuBuffer var3 = this.gpuBuffer10[var1];
      if (var3 != null && !var3.isClosed() && this.value136[var1] >= var2) {
         return var3;
      }

      if (var3 != null && !var3.isClosed()) {
         var3.close();
      }

      long var4 = VALUE2151;

      while (var4 < var2) {
         var4 <<= 1;
      }

      var3 = RenderSystem.getDevice().createBuffer(() -> var1 + "", 40, var4);
      this.gpuBuffer10[var1] = var3;
      this.value136[var1] = var4;
      return var3;
   }

   public boolean is179() {
      return this.is() && !this.multiChoiceSetting58.is(НИЧЕГО_НЕ_ДЕЛАТЬ2);
   }

   private void update365() {
      if (this.gpuBuffer != null) {
         this.gpuBuffer.close();
         this.gpuBuffer = null;
      }

      this.count144 = COUNT101;
      this.value134 = Float.NaN;
   }

   private int compute108(int var1) {
      int var2 = this.compute106(var1);
      return this.is190() && this.checkboxSetting152.enabled() ? VisualColors.lerpColor(var1, var2, VALUE2055) : var2;
   }

   private boolean is180() {
      return this.multiChoiceSetting57.is(ИСТОЧНИК4) || this.multiChoiceSetting57.is(НЕБУЛА4);
   }

   public boolean is181() {
      return this.is() && this.multiChoiceSetting56.is(ОЧИСТИТЬ2);
   }

   private static float compute109(float var0) {
      if (var0 < VALUE2192) {
         return compute110(var0 / VALUE2193);
      } else if (var0 < VALUE2194) {
         return 1.0F;
      } else if (var0 < VALUE2195) {
         float var2 = compute110((var0 - VALUE2196) / VALUE2197);
         return Mth.lerp(var2, 1.0F, VALUE2198);
      } else if (var0 < VALUE2199) {
         float var1 = compute110((var0 - VALUE2200) / VALUE2201);
         return VALUE2202 * (1.0F - var1);
      } else {
         return 0.0F;
      }
   }

   public boolean is182() {
      return this.is() && this.multiChoiceSetting58.is(ДОЖДЬ1);
   }

   public boolean is183() {
      return this.is() && (this.checkboxSetting158.enabled() || this.is176());
   }

   private void update366() {
      if (this.gpuBuffer4 != null && !this.gpuBuffer4.isClosed()) {
         this.gpuBuffer4.close();
      }

      this.gpuBuffer4 = null;
   }

   private void apply527(RenderTarget var1, Matrix4f var2, Matrix4f var3, Vec3 var4, float var5, Vector4f var6, int var7) {
      BufferBuilder var8 = SharedVertexBuffer.shared().beginVertices(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);
      Matrix4f var9 = new Matrix4f();

      for (Ambience.Vec3Vec3Record var11 : this.items26) {
         Vec3 var12 = var11.start.subtract(var4);
         Vec3 var13 = var11.end.subtract(var4);
         Vec3 var14 = var13.subtract(var12);
         if (!(var14.lengthSqr() < VALUE2141)) {
            Vec3 var15 = var12.add(var13).scale(VALUE2142);
            Vec3 var16 = var15.lengthSqr() > VALUE2143 ? var15.normalize() : new Vec3(0.0, 0.0, 1.0);
            Vec3 var17 = var14.normalize().cross(var16);
            var17 = var17.lengthSqr() < VALUE2144 ? new Vec3(1.0, 0.0, 0.0) : var17.normalize();
            float var18 = (float)(VALUE2145 * Math.max(1.0, var15.length()) / Math.max(1.0, Math.abs(var2.m11()) * var1.height));
            float var19 = var5 <= VALUE2146 ? VALUE2147 : VALUE2148;
            float var20 = Math.max(var5 * var11.strength, var18 * var19);
            Vec3 var21 = var17.scale(var20);
            Vec3 var22 = var12.subtract(var21);
            Vec3 var23 = var12.add(var21);
            Vec3 var24 = var13.add(var21);
            Vec3 var25 = var13.subtract(var21);
            var8.addVertex(var9, (float)var22.x, (float)var22.y, (float)var22.z).setUv(VALUE2149, 0.0F);
            var8.addVertex(var9, (float)var23.x, (float)var23.y, (float)var23.z).setUv(1.0F, 0.0F);
            var8.addVertex(var9, (float)var24.x, (float)var24.y, (float)var24.z).setUv(1.0F, 1.0F);
            var8.addVertex(var9, (float)var25.x, (float)var25.y, (float)var25.z).setUv(VALUE2150, 1.0F);
         }
      }

      MeshData var32 = var8.buildOrThrow();

      try {
         MemoryStack var33 = MemoryStack.stackPush();

         try {
            DrawState var34 = var32.drawState();
            AutoStorageIndexBuffer var35 = RenderSystem.getSequentialBuffer(var34.primitiveTopology());
            GpuBuffer var36 = var35.getBuffer(var34.indexCount());
            ByteBuffer var37 = Std140Builder.onStack(var33, 144).putMat4f(var2).putMat4f(var3).putVec4(var6).get();
            ByteBuffer var38 = var32.vertexBuffer();
            int var40 = var38.remaining();
            GpuBuffer var41 = this.resolveGpuBuffer(var7, var40);
            GpuBuffer var42 = this.createGpuBuffer(var7);
            CommandEncoder var43 = RenderSystem.getDevice().createCommandEncoder();
            var43.writeToBuffer(var41.slice(0L, var40), var38);
            var43.writeToBuffer(var42.slice(), var37);
            RenderPass var44 = var43.createRenderPass(
               () -> AMBIENCE_LIGHTNING3, var1.getColorTextureView(), Optional.empty(), var1.getDepthTextureView(), OptionalDouble.empty()
            );

            try {
               var44.setPipeline(RenderSystem.getCompiledPipeline(var7 == 2 ? this.renderPipeline13 : this.renderPipeline12));
               var44.setUniform(LIGHTNING2, var42);
               var44.setVertexBuffer(0, var41.slice());
               var44.setIndexBuffer(var36, var35.type());
               var44.drawIndexed(var34.indexCount(), 1, 0, 0, 0);
            } catch (Throwable var29) {
               if (var44 != null) {
                  try {
                     var44.close();
                  } catch (Throwable var28) {
                     var29.addSuppressed(var28);
                  }
               }

               throw var29;
            }

            if (var44 != null) {
               var44.close();
            }
         } catch (Throwable var30) {
            if (var33 != null) {
               try {
                  var33.close();
               } catch (Throwable var27) {
                  var30.addSuppressed(var27);
               }
            }

            throw var30;
         }

         if (var33 != null) {
            var33.close();
         }
      } catch (Throwable var31) {
         if (var32 != null) {
            try {
               var32.close();
            } catch (Throwable var26) {
               var31.addSuppressed(var26);
            }
         }

         throw var31;
      }

      if (var32 != null) {
         var32.close();
      }
   }

   private void update367() {
      if (this.gpuBuffer5 != null && !this.gpuBuffer5.isClosed()) {
         this.gpuBuffer5.close();
      }

      this.gpuBuffer5 = null;
      this.count145 = 0;
   }

   private void apply528(Matrix4f var1, Matrix4f var2, GpuBufferSlice var3) {
      if (this.is186() && var1 != null && var2 != null && var3 != null) {
         RenderTarget var4 = mc.gameRenderer.mainRenderTarget();
         if (var4 != null && var4.getColorTexture() != null && var4.getDepthTexture() != null && !var4.getDepthTexture().isClosed()) {
            Camera var5 = mc.gameRenderer.mainCamera();
            Vec3 var6 = var5.position();
            Matrix4f var7 = this.matrix4f2.set(var1).invert();
            Matrix4f var8 = this.matrix4f3.set(var2).invert();
            int var9 = this.multiChoiceSetting58.is(ДОЖДЬ3) ? 1 : (this.multiChoiceSetting58.is(СНЕГ2) ? 2 : 3);
            long var10 = System.nanoTime();
            float var12 = this.value138 == 0L ? 0.0F : Math.clamp((float)(var10 - this.value138) / VALUE2204, 0.0F, VALUE2205);
            this.value138 = var10;
            float var13 = !mc.isPaused() && !mc.level.tickRateManager().isFrozen() ? var12 : 0.0F;
            this.value137 += var13;
            float var14 = (float)this.value137;
            float var15 = this.sliderSetting189.floatValue();
            float var16 = this.sliderSetting190.floatValue();
            if (var9 == 3) {
               var15 = Math.max(var15, VALUE2206) + VALUE2207 + (float)Math.sin(var14 * VALUE2208) * VALUE2209 + (float)Math.sin(var14 * VALUE2210 + VALUE2211) * VALUE2212;
               var16 += (float)Math.sin(var14 * VALUE2213) * VALUE2214;
            }

            float var17 = (float)Math.toRadians(var16);
            float var18 = (float)Math.sin(var17) * var15;
            float var19 = (float)Math.cos(var17) * var15;
            float var20 = (var9 == 1 ? VALUE2215 : VALUE2216) * Math.max(this.sliderSetting187.floatValue(), VALUE2217);
            float var21 = var9 == 1 ? VALUE2218 : 1.0F;
            float var22 = var18 * var21;
            float var23 = var19 * var21;
            float var24 = (float)Math.sqrt(var22 * var22 + var20 * var20 + var23 * var23);
            float var25 = 1.0F / Math.max(var24, VALUE2219);
            float var26 = var22 * var25;
            float var27 = var20 * var25;
            float var28 = var23 * var25;
            float var29 = (float)Math.sqrt(var18 * var18 + var19 * var19);
            Vector4f var30 = VisualColors.toRgbaVector(this.compute108(var9 == 1 ? this.colorSetting7.color() : this.colorSetting8.color()));
            float var31 = var9 == 1 ? VALUE2220 : (var9 == 3 ? VALUE2221 : VALUE2222);
            this.value139 = this.value139 + var31 * this.sliderSetting187.floatValue() * var13;
            this.value140 += var22 * var13;
            this.value141 += var23 * var13;
            this.value142 = this.value142 + this.sliderSetting187.floatValue() * var13;
            if (!(var30.w <= VALUE2223)) {
               float var32 = this.sliderSetting186.floatValue();
               float var33 = Math.clamp(var32 * VALUE2224, VALUE2225, VALUE2226);
               float var34 = var32 * 2.0F / VALUE2227;
               float var35 = compute111(var6.y + var33 * VALUE2228, var33);
               double var36 = Math.floor((var6.x - this.value140) / var34) - VALUE2229;
               double var38 = Math.floor((var6.z - this.value141) / var34) - VALUE2230;
               float var40 = (float)(var36 * var34 + this.value140 - var6.x);
               float var41 = (float)(var38 * var34 + this.value141 - var6.z);
               float var42 = compute111(var36, VALUE2231);
               float var43 = compute111(var38, VALUE2232);
               float var44 = var9 == 1 ? Math.max(VALUE2233, this.sliderSetting192.floatValue() * VALUE2234) : VALUE2235;
               double var45 = var44 * VALUE2236;
               float var47 = compute111(var6.x, var45);
               float var48 = compute111(var6.z, var45);
               int var49 = Math.max(1, var4.width);
               int var50 = Math.max(1, var4.height);
               float var51 = VALUE2237 + VALUE2238 * Math.clamp(this.sliderSetting185.floatValue(), 0.0F, 1.0F);
               int var52 = Math.clamp(Math.round(VALUE2239 * var51), 1, 8192);
               MemoryStack var53 = MemoryStack.stackPush();

               try {
                  ByteBuffer var54 = Std140Builder.onStack(var53, 400)
                     .putMat4f(var1)
                     .putMat4f(var2)
                     .putMat4f(var7)
                     .putMat4f(var8)
                     .putVec4(var47, (float)var6.y, var48, var14)
                     .putVec4(var49, var50, var9, this.sliderSetting185.floatValue())
                     .putVec4(this.sliderSetting187.floatValue(), this.sliderSetting188.floatValue(), this.sliderSetting186.floatValue(), this.is190() ? 1.0F : 0.0F)
                     .putVec4(var18, var19, this.sliderSetting191.floatValue(), this.sliderSetting192.floatValue())
                     .putVec4(var30)
                     .putVec4(compute111(this.value139, var33), (float)(this.value140 % VALUE2240), (float)(this.value141 % VALUE2241), compute111(this.value142, VALUE2242))
                     .putVec4(var40, var41, var42, var43)
                     .putVec4(var26, var27, var28, var29)
                     .putVec4(var33, var34, var35, (VALUE2243 + VALUE2244 * this.sliderSetting185.floatValue()) * var30.w)
                     .get();
                  CommandEncoder var55 = RenderSystem.getDevice().createCommandEncoder();
                  if (var9 == 1) {
                     this.systemClass111.updateHeightmap(mc.level, var6, var55);
                  } else {
                     this.systemClass111.close();
                  }

                  this.gpuBuffer4 = this.getGpuBuffer();
                  var55.writeToBuffer(this.gpuBuffer4.slice(), var54);
                  if (this.checkboxSetting155.enabled()) {
                     this.apply534(var4, this.gpuBuffer4, var3, var55);
                  }

                  this.apply521(var4, this.gpuBuffer4, var3, var55, var52);
               } catch (Throwable var57) {
                  if (var53 != null) {
                     try {
                        var53.close();
                     } catch (Throwable var56) {
                        var57.addSuppressed(var56);
                     }
                  }

                  throw var57;
               }

               if (var53 != null) {
                  var53.close();
               }
            }
         } else {
            this.value138 = 0L;
         }
      } else {
         this.value138 = 0L;
         this.systemClass111.close();
      }
   }

   private void apply529(FogSettingsEvent var1) {
      if (this.check232(var1.getCamera())) {
         if (this.is181()) {
            var1.setDistances(VALUE2056, VALUE2057);
         } else {
            float var2 = Math.max(VALUE2058, var1.originalDistance());
            if (this.is174()) {
               var1.setDistances(VALUE2059, VALUE2060);
               var1.setColor(this.colorSetting3.color() | COUNT97);
            } else {
               float var3 = Math.clamp(this.sliderSetting185.floatValue(), VALUE2061, 1.0F);
               float var4 = var2 * Mth.lerp(var3, VALUE2062, VALUE2063);
               var1.setDistances(var4 * VALUE2064, var4);
               var1.setColor(COUNT98);
            }
         }
      }
   }

   private void apply530(Matrix4f var1, Matrix4f var2) {
      if (this.is186() && this.multiChoiceSetting58.is(ПУРГА3) && var1 != null && var2 != null) {
         RenderTarget var3 = mc.gameRenderer.mainRenderTarget();
         if (var3 != null && var3.getColorTexture() != null && var3.getDepthTexture() != null && !var3.getDepthTexture().isClosed()) {
            Vec3 var4 = mc.gameRenderer.mainCamera().position();
            Matrix4f var5 = this.matrix4f2.set(var1).invert();
            Matrix4f var6 = this.matrix4f3.set(var2).invert();
            float var7 = (float)this.value137;
            float var8 = Math.max(this.sliderSetting189.floatValue(), VALUE2100)
               + VALUE2101
               + (float)Math.sin(var7 * VALUE2102) * VALUE2103
               + (float)Math.sin(var7 * VALUE2104 + VALUE2105) * VALUE2106;
            float var9 = this.sliderSetting190.floatValue() + (float)Math.sin(var7 * VALUE2107) * VALUE2108;
            float var10 = (float)Math.toRadians(var9);
            float var11 = (float)Math.sin(var10);
            float var12 = (float)Math.cos(var10);
            float var13 = Math.clamp(var8 / VALUE2109, VALUE2110, VALUE2111);
            Vector4f var14 = VisualColors.toRgbaVector(this.compute108(this.colorSetting8.color()));
            MemoryStack var15 = MemoryStack.stackPush();

            try {
               ByteBuffer var16 = Std140Builder.onStack(var15, 208)
                  .putMat4f(var5)
                  .putMat4f(var6)
                  .putVec4(compute111(var4.x, VALUE2112), (float)var4.y, compute111(var4.z, VALUE2113), var7)
                  .putVec4(this.sliderSetting185.floatValue(), var13, this.sliderSetting186.floatValue(), 0.0F)
                  .putVec4((float)(this.value140 % VALUE2114), (float)(this.value141 % VALUE2115), var11, var12)
                  .putVec4(var14.x, var14.y, var14.z, 1.0F)
                  .get();
               GpuBuffer var17 = RenderSystem.getDevice().createBuffer(() -> AMBIENCE_BLIZZARD_OVERLAY_UNIFORMS, 128, var16);

               try {
                  RenderPass var18 = RenderSystem.getDevice()
                     .createCommandEncoder()
                     .createRenderPass(() -> AMBIENCE_BLIZZARD_OVERLAY, var3.getColorTextureView(), Optional.empty());

                  try {
                     var18.setPipeline(RenderSystem.getCompiledPipeline(this.renderPipeline5));
                     var18.setUniform(DEPTH_SAMPLER13, var3.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                     var18.setUniform(BLIZZARD1, var17);
                     var18.draw(3, 1, 0, 0);
                  } catch (Throwable var24) {
                     if (var18 != null) {
                        try {
                           var18.close();
                        } catch (Throwable var23) {
                           var24.addSuppressed(var23);
                        }
                     }

                     throw var24;
                  }

                  if (var18 != null) {
                     var18.close();
                  }
               } catch (Throwable var25) {
                  if (var17 != null) {
                     try {
                        var17.close();
                     } catch (Throwable var22) {
                        var25.addSuppressed(var22);
                     }
                  }

                  throw var25;
               }

               if (var17 != null) {
                  var17.close();
               }
            } catch (Throwable var26) {
               if (var15 != null) {
                  try {
                     var15.close();
                  } catch (Throwable var21) {
                     var26.addSuppressed(var21);
                  }
               }

               throw var26;
            }

            if (var15 != null) {
               var15.close();
            }
         }
      }
   }

   public void apply531(Matrix4f var1, Matrix4f var2, DeltaTracker var3) {
      if (this.is190() && var1 != null && var2 != null && var3 != null) {
         RenderTarget var4 = mc.gameRenderer.mainRenderTarget();
         if (var4 != null && var4.getColorTexture() != null && var4.getDepthTexture() != null && !var4.getDepthTexture().isClosed()) {
            if (this.value135 < 0L) {
               this.value135 = System.currentTimeMillis();
            }

            int var5 = Math.max(1, (var4.width + 1 - 1) / 1);
            int var6 = Math.max(1, (var4.height + 1 - 1) / 1);
            this.apply546(var5, var6);
            this.update363();
            CommandEncoder var7 = RenderSystem.getDevice().createCommandEncoder();
            var7.clearColorTexture(this.textureTarget.getColorTexture(), new Vector4f(0.0F));
            float var8 = compute112(var1);
            if (this.is185()) {
               this.apply536(var4, var7, var5, var6, var8, this.renderPipeline7);
            } else if (this.is189()) {
               this.apply536(var4, var7, var5, var6, var8, this.renderPipeline8);
            } else if (this.is193()) {
               int var9 = this.checkboxSetting152.enabled() ? VisualTheme.themeColor(VisualThemeColor.MAIN) : this.colorSetting4.color();
               int var10 = this.checkboxSetting152.enabled()
                  ? VisualColors.argb(
                     Math.round(VisualColors.red(var9) * VALUE2081),
                     Math.round(VisualColors.green(var9) * VALUE2082),
                     Math.round(VisualColors.blue(var9) * VALUE2083),
                     VALUE2084
                  )
                  : this.colorSetting5.color();
               this.systemClass519
                  .renderAurora(
                     this.textureTarget,
                     var1,
                     var2,
                     this.value135,
                     this.sliderSetting180.floatValue(),
                     this.sliderSetting181.floatValue(),
                     this.sliderSetting182.floatValue(),
                     this.sliderSetting183.floatValue(),
                     var9,
                     var10,
                     this.checkboxSetting154.enabled()
                  );
            } else if (this.is175()) {
               this.apply544(var4, var7, var5, var6, var8);
            } else {
               this.apply522(var4, var7, var1, var2, var3, var5, var6, var8);
            }

            RenderPass var14 = var7.createRenderPass(() -> AMBIENCE_SHADER_SKY_COMPOSITE1, var4.getColorTextureView(), Optional.empty());

            try {
               var14.setPipeline(RenderSystem.getCompiledPipeline(this.renderPipeline10));
               var14.setUniform(SKY_SAMPLER1, this.textureTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
               var14.setUniform(DEPTH_SAMPLER9, var4.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
               var14.draw(3, 1, 0, 0);
            } catch (Throwable var13) {
               if (var14 != null) {
                  try {
                     var14.close();
                  } catch (Throwable var12) {
                     var13.addSuppressed(var12);
                  }
               }

               throw var13;
            }

            if (var14 != null) {
               var14.close();
            }
         }
      } else {
         this.update375();
      }
   }

   private boolean is184() {
      return this.is185() || this.is189();
   }

   private boolean is185() {
      return this.multiChoiceSetting57.is(ПУЛЬСАР2);
   }

   private TextureTarget resolveTextureTarget(int var1, int var2) {
      if (this.textureTarget1 == null || this.textureTarget1.width != var1 || this.textureTarget1.height != var2) {
         this.update362();
         this.textureTarget1 = new TextureTarget(AMBIENCE_WEATHER_GROUND_LAYER, var1, var2, GpuFormat.RGBA8_UNORM, null);
      }

      return this.textureTarget1;
   }

   private void update368() {
      if (this.gpuBuffer == null || this.gpuBuffer.isClosed()) {
         this.gpuBuffer = RenderSystem.getDevice().createBuffer(() -> AMBIENCE_WORLD_COLOR_UNIFORMS, 136, VALUE2080);
         this.count144 = COUNT100;
         this.value134 = Float.NaN;
      }
   }

   private static float compute110(float var0) {
      var0 = Math.clamp(var0, 0.0F, 1.0F);
      return var0 * var0 * (VALUE2203 - 2.0F * var0);
   }

   private void apply532(Vec3 var1) {
      if (this.checkboxSetting157.enabled() && !(this.sliderSetting195.floatValue() <= 0.0F) && !mc.isPaused() && !false && this.vec3_21 != null) {
         if (this.count146 < 0 && (this.f_8483 == null || !mc.getSoundManager().isActive(this.f_8483))) {
            this.vec3_22 = this.vec3_21;
            this.count146 = Math.clamp((int)Math.ceil(var1.distanceTo(this.vec3_21) / VALUE2153 * VALUE2154), 2, 12);
         }
      }
   }

   public float count100() {
      return this.sliderSetting172.floatValue();
   }

   private boolean is186() {
      return this.is()
         && this.is187()
         && mc.player != null
         && mc.level != null
         && mc.gameRenderer != null
         && mc.gameRenderer.mainCamera() != null
         && mc.gameRenderer.mainCamera().getFluidInCamera() == FogType.NONE;
   }

   private boolean is187() {
      return this.multiChoiceSetting58.is(ДОЖДЬ2) || this.multiChoiceSetting58.is(СНЕГ1) || this.multiChoiceSetting58.is(ПУРГА1);
   }

   public boolean is188() {
      return this.is() && this.is176() && (!this.is193() || !this.checkboxSetting154.enabled());
   }

   private boolean is189() {
      return this.multiChoiceSetting57.is(ГАЛАКТИКА1);
   }

   private boolean is190() {
      return false
         ? false
         : this.is()
            && this.is176()
            && (!this.is178() && !this.is175() || this.sliderSetting174.floatValue() > 0.0F)
            && mc.player != null
            && mc.level != null
            && mc.gameRenderer != null
            && check231(mc.gameRenderer.mainCamera());
   }

   private void apply533(Matrix4f var1, Matrix4f var2, DeltaTracker var3) {
      if (this.is174() && mc.level != null && mc.player != null && this.check232(mc.gameRenderer.mainCamera()) && !false) {
         float var4 = Math.min(VALUE2065, Math.max(VALUE2066, mc.options.getEffectiveRenderDistance() * VALUE2067));
         float var5 = this.count101() * VALUE2068;
         float var6 = Math.max(var5 + VALUE2069, this.count100() * var4);
         float var7 = ((float)(mc.level.getGameTime() % VALUE2070) + var3.getGameTimeDeltaPartialTick(false)) / VALUE2071;
         this.systemClass424
            .renderVolumeFog(mc.gameRenderer.mainRenderTarget(), var1, var2, mc.gameRenderer.mainCamera().position(), var7, this.colorSetting3.color(), var5, var6);
      } else {
         this.systemClass424.close();
      }
   }

   private void update369() {
      if (this.gpuBuffer6 == null || this.gpuBuffer6.isClosed()) {
         this.gpuBuffer6 = RenderSystem.getDevice().createBuffer(() -> AMBIENCE_LIGHTNING_HORIZONTAL_GLOW, 136, VALUE2138);
      }

      if (this.gpuBuffer7 == null || this.gpuBuffer7.isClosed()) {
         this.gpuBuffer7 = RenderSystem.getDevice().createBuffer(() -> AMBIENCE_LIGHTNING_VERTICAL_GLOW, 136, VALUE2139);
      }

      if (this.gpuBuffer8 == null || this.gpuBuffer8.isClosed()) {
         this.gpuBuffer8 = RenderSystem.getDevice().createBuffer(() -> AMBIENCE_LIGHTNING_GLOW_COMPOSITE1, 136, VALUE2140);
      }
   }

   private void apply534(RenderTarget var1, GpuBuffer var2, GpuBufferSlice var3, CommandEncoder var4) {
      TextureTarget var5 = this.resolveTextureTarget(Math.max(1, (var1.width + 2 - 1) / 2), Math.max(1, (var1.height + 2 - 1) / 2));
      RenderPass var6 = var4.createRenderPass(() -> AMBIENCE_CUSTOM_WEATHER_GROUND1, var5.getColorTextureView(), Optional.of(new Vector4f(0.0F)));

      try {
         boolean var7 = this.multiChoiceSetting58.is(ДОЖДЬ5);
         var6.setPipeline(RenderSystem.getCompiledPipeline(var7 ? this.renderPipeline3 : this.renderPipeline4));
         if (var7) {
            this.systemClass111.bindHeightmap(var6);
         }

         var6.setUniform(DEPTH_SAMPLER15, var1.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
         var6.setUniform(WEATHER5, var2);
         var6.setUniform(FOG5, var3);
         this.apply526(var6, 1);
      } catch (Throwable var12) {
         if (var6 != null) {
            try {
               var6.close();
            } catch (Throwable var10) {
               var12.addSuppressed(var10);
            }
         }

         throw var12;
      }

      if (var6 != null) {
         var6.close();
      }

      var6 = var4.createRenderPass(() -> AMBIENCE_CUSTOM_WEATHER_GROUND, var1.getColorTextureView(), Optional.empty());

      try {
         var6.setPipeline(RenderSystem.getCompiledPipeline(this.renderPipeline15));
         var6.setUniform(SAMPLER0_1, var5.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
         this.apply524(var6);
      } catch (Throwable var11) {
         if (var6 != null) {
            try {
               var6.close();
            } catch (Throwable var9) {
               var11.addSuppressed(var9);
            }
         }

         throw var11;
      }

      if (var6 != null) {
         var6.close();
      }
   }

   @Override
   public void onVisualDisable() {
      super.onVisualDisable();
      this.update364();
      this.value137 = 0.0;
      this.value138 = 0L;
      this.value139 = 0.0;
      this.value140 = 0.0;
      this.value141 = 0.0;
      this.value142 = 0.0;
      this.items26.clear();
      this.value143 = 0L;
      this.value144 = 0L;
      this.vec3_20 = null;
      this.vec3_21 = null;
      if (this.textureTarget2 != null) {
         this.textureTarget2.destroyBuffers();
         this.textureTarget2 = null;
      }

      this.update372();
      this.update374();
      this.update375();
      this.update365();
      this.systemClass424.close();
      this.systemClass111.close();
      this.update366();
      this.update362();
      this.update367();
   }

   @Override
   public void readLegacyConfig(JsonObject var1) {
      String var2 = null;
      if (var1 != null && var1.has(SETTINGS11)) {
         JsonObject var3 = var1.getAsJsonObject(SETTINGS12);
         this.apply520(var3);
         if (!var3.has(this.multiChoiceSetting57.name())) {
            if (var3.has(ШЕЙДЕРНОЕ_НЕБО) && var3.get(ШЕЙДЕРНОЕ_НЕБО1).getAsBoolean()) {
               var2 = ПУЛЬСАР1;
            } else if (var3.has(ШЕЙДЕР_ТУМАНА)) {
               String var4 = var3.get(ШЕЙДЕР_ТУМАНА1).getAsString();
               if (!var4.equals(НИЧЕГО2)) {
                  var2 = var4;
               }
            }
         }
      }

      super.readLegacyConfig(var1);
      if (var2 != null) {
         this.apply535(var2);
      }
   }

   private static boolean check231(Camera var0) {
      if (var0 == null) {
         return false;
      }

      FogType var1 = var0.getFluidInCamera();
      return var1 == FogType.NONE || var1 == FogType.WATER;
   }

   private void apply535(String var1) { this.multiChoiceSetting57.set(var1); }

   private void apply536(RenderTarget var1, CommandEncoder var2, int var3, int var4, float var5, RenderPipeline var6) {
      float var7 = (float)(System.currentTimeMillis() - this.value135) / VALUE2085;
      Vector4f var8 = VisualColors.toRgbaVector(VisualTheme.themeColor(VisualThemeColor.MAIN));
      float var9 = (float)Math.toRadians(-mc.gameRenderer.mainCamera().yRot());
      float var10 = (float)Math.toRadians(mc.gameRenderer.mainCamera().xRot());
      float var11 = var5;
      MemoryStack var12 = MemoryStack.stackPush();

      try {
         ByteBuffer var13 = Std140Builder.onStack(var12, 80)
            .putVec4(var3, var4, var7, var11)
            .putVec4(var8.x, var8.y, var8.z, this.sliderSetting179.floatValue())
            .putVec4(this.sliderSetting176.floatValue(), this.sliderSetting177.floatValue(), this.sliderSetting178.floatValue(), 0.0F)
            .putVec4(var9, var10, 0.0F, 0.0F)
            .putVec4(0.0F, VALUE2086, 1.0F, 0.0F)
            .get();
         var2.writeToBuffer(this.gpuBuffer2.slice(), var13);
         RenderPass var14 = var2.createRenderPass(() -> AMBIENCE_SHADER_SKY, this.textureTarget.getColorTextureView(), Optional.empty());

         try {
            var14.setPipeline(RenderSystem.getCompiledPipeline(var6));
            var14.setUniform(DEPTH_SAMPLER10, var1.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            var14.setUniform(SHADER_MIST2, this.gpuBuffer2);
            var14.draw(3, 1, 0, 0);
         } catch (Throwable var19) {
            if (var14 != null) {
               try {
                  var14.close();
               } catch (Throwable var18) {
                  var19.addSuppressed(var18);
               }
            }

            throw var19;
         }

         if (var14 != null) {
            var14.close();
         }
      } catch (Throwable var20) {
         if (var12 != null) {
            try {
               var12.close();
            } catch (Throwable var17) {
               var20.addSuppressed(var17);
            }
         }

         throw var20;
      }

      if (var12 != null) {
         var12.close();
      }
   }

   private static float compute111(double var0, double var2) {
      return (float)(var0 - Math.floor(var0 / var2) * var2);
   }

   private static void apply537(double[] var0, int var1, int var2, double var3, Random var5) {
      if (var2 - var1 > 1) {
         int var6 = var1 + var2 >>> 1;
         var0[var6] = (var0[var1] + var0[var2]) * VALUE2183 + (var5.nextDouble() * VALUE2184 - 1.0) * var3;
         apply537(var0, var1, var6, var3 * VALUE2185, var5);
         apply537(var0, var6, var2, var3 * VALUE2186, var5);
      }
   }

   private TextureTarget resolveTextureTarget1(int var1, int var2) {
      var1 = Math.max(1, var1);
      var2 = Math.max(1, var2);
      if (this.textureTarget2 == null) {
         this.textureTarget2 = new TextureTarget(AMBIENCE_LIGHTNING_GLOW, var1, var2, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
      } else if (this.textureTarget2.width != var1 || this.textureTarget2.height != var2) {
         this.textureTarget2.resize(var1, var2);
      }

      return this.textureTarget2;
   }

   private static float compute112(Matrix4f var0) {
      float var1 = Math.abs(var0.m11());
      return var1 < VALUE2246 ? VALUE2247 : (float)Math.toDegrees(VALUE2248 * Math.atan(1.0 / var1));
   }

   public boolean check232(Camera var1) {
      return this.is() && !this.is194() && check231(var1) && (this.is181() || this.is174() || var1.getFluidInCamera() == FogType.NONE && this.is177());
   }

   public void apply538(Matrix4f var1, Matrix4f var2) {
      this.apply543(var1, var2);
   }

   private void apply539(long var1) {
      double var3 = ThreadLocalRandom.current().nextDouble(VALUE2187, VALUE2188);
      float var5 = Math.max(VALUE2189, 2.0F / Math.max(VALUE2190, this.sliderSetting193.floatValue()));
      this.value144 = var1 + (long)(var5 * var3 * VALUE2191);
   }

   private void apply540(RenderTarget var1, RenderTarget var2, int var3, int var4, float var5, float var6) {
      if (var1 != null && var2 != null && var1.getColorTextureView() != null && var2.getColorTextureView() != null && !(Math.max(var5, var6) <= VALUE2133)) {
         int var7 = Math.max(1, var1.width / 2);
         int var8 = Math.max(1, var1.height / 2);
         this.apply541(var7, var8);
         this.update369();
         CommandEncoder var9 = RenderSystem.getDevice().createCommandEncoder();
         this.apply545(var9, var7, var8, var3, var4, var5, var6);
         RenderPass var10 = var9.createRenderPass(() -> AMBIENCE_LIGHTNING_GLOW_MASK1, this.textureTarget3.getColorTextureView(), Optional.empty());

         try {
            var10.setPipeline(RenderSystem.getCompiledPipeline(this.renderPipeline16));
            var10.setUniform(IN_SAMPLER2, var1.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            var10.draw(3, 1, 0, 0);
         } catch (Throwable var16) {
            if (var10 != null) {
               try {
                  var10.close();
               } catch (Throwable var14) {
                  var16.addSuppressed(var14);
               }
            }

            throw var16;
         }

         if (var10 != null) {
            var10.close();
         }

         this.apply547(var9, this.textureTarget3, this.textureTarget4, this.gpuBuffer6);
         this.apply547(var9, this.textureTarget4, this.textureTarget3, this.gpuBuffer7);
         var10 = var9.createRenderPass(() -> AMBIENCE_LIGHTNING_GLOW_COMPOSITE2, var2.getColorTextureView(), Optional.empty());

         try {
            var10.setPipeline(RenderSystem.getCompiledPipeline(this.renderPipeline18));
            var10.setUniform(BLOOM_SAMPLER1, this.textureTarget3.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            var10.setUniform(LIGHTNING_GLOW_COMPOSITE1, this.gpuBuffer8);
            var10.draw(3, 1, 0, 0);
         } catch (Throwable var15) {
            if (var10 != null) {
               try {
                  var10.close();
               } catch (Throwable var13) {
                  var15.addSuppressed(var13);
               }
            }

            throw var15;
         }

         if (var10 != null) {
            var10.close();
         }
      }
   }

   private void update370() {
      if (!this.is191()) {
         this.update365();
      } else {
         RenderTarget var1 = mc.gameRenderer.mainRenderTarget();
         if (var1 != null && var1.getColorTexture() != null && var1.getColorTextureView() != null) {
            this.update368();
            CommandEncoder var2 = RenderSystem.getDevice().createCommandEncoder();
            int var3 = this.multiChoiceSetting59.is(ТЕМА1) ? VisualTheme.themeColor(VisualThemeColor.MAIN) : this.colorSetting6.color();
            float var4 = this.sliderSetting184.floatValue();
            if (var3 != this.count144 || Float.compare(var4, this.value134) != 0) {
               Vector4f var5 = VisualColors.toRgbaVector(var3 | COUNT99);
               float var6 = Math.max(var5.x * VALUE2073 + var5.y * VALUE2074 + var5.z * VALUE2075, VALUE2076);
               float var7 = Mth.lerp(VALUE2077, 1.0F, var5.x / var6);
               float var8 = Mth.lerp(VALUE2078, 1.0F, var5.y / var6);
               float var9 = Mth.lerp(VALUE2079, 1.0F, var5.z / var6);
               MemoryStack var10 = MemoryStack.stackPush();

               try {
                  ByteBuffer var11 = Std140Builder.onStack(var10, 16)
                     .putVec4(Mth.lerp(var4, 1.0F, var7), Mth.lerp(var4, 1.0F, var8), Mth.lerp(var4, 1.0F, var9), 1.0F)
                     .get();
                  var2.writeToBuffer(this.gpuBuffer.slice(), var11);
               } catch (Throwable var16) {
                  if (var10 != null) {
                     try {
                        var10.close();
                     } catch (Throwable var14) {
                        var16.addSuppressed(var14);
                     }
                  }

                  throw var16;
               }

               if (var10 != null) {
                  var10.close();
               }

               this.count144 = var3;
               this.value134 = var4;
            }

            RenderPass var17 = var2.createRenderPass(() -> AMBIENCE_WORLD_COLOR1, var1.getColorTextureView(), Optional.empty());

            try {
               var17.setPipeline(RenderSystem.getCompiledPipeline(this.renderPipeline11));
               var17.setUniform(WORLD_COLOR1, this.gpuBuffer);
               var17.draw(3, 1, 0, 0);
            } catch (Throwable var15) {
               if (var17 != null) {
                  try {
                     var17.close();
                  } catch (Throwable var13) {
                     var15.addSuppressed(var13);
                  }
               }

               throw var15;
            }

            if (var17 != null) {
               var17.close();
            }
         }
      }
   }

   public float count101() {
      return Math.min(this.sliderSetting173.floatValue(), this.sliderSetting172.floatValue());
   }

   private void apply541(int var1, int var2) {
      if (this.textureTarget3 == null || this.textureTarget4 == null) {
         this.textureTarget3 = new TextureTarget(AMBIENCE_LIGHTNING_GLOW_HORIZONTAL, var1, var2, GpuFormat.RGBA8_UNORM, null);
         this.textureTarget4 = new TextureTarget(AMBIENCE_LIGHTNING_GLOW_VERTICAL, var1, var2, GpuFormat.RGBA8_UNORM, null);
      } else if (this.textureTarget3.width != var1 || this.textureTarget3.height != var2) {
         this.textureTarget3.resize(var1, var2);
         this.textureTarget4.resize(var1, var2);
      }
   }

   public Ambience() {
      super(AMBIENCE, ИЗМЕНЕНИЕ_ПОГОДЫ_МИРА, Category.RENDER);
      this.renderPipeline1 = VisualPipelineBuilder.builder()
         .withLocation(COLISEUM_WEATHER_PARTICLES)
         .withVertexShader(VisualResources.shader(WEATHER_PARTICLES))
         .withFragmentShader(VisualResources.shader(WEATHER_PARTICLES1))
         .withDepthTest(CompareOp.GREATER_THAN_OR_EQUAL)
         .withDepthWrite(false)
         .withCull(false)
         .withBlend(BlendFunction.LIGHTNING)
         .withVertexFormat(DefaultVertexFormat.POSITION, PrimitiveTopology.QUADS)
         .withUniform(WEATHER, UniformType.UNIFORM_BUFFER)
         .withUniform(FOG, UniformType.UNIFORM_BUFFER)
         .buildPipeline();
      this.renderPipeline2 = VisualPipelineBuilder.builder()
         .withLocation(COLISEUM_WEATHER_RAIN)
         .withVertexShader(VisualResources.shader(WEATHER_PARTICLES2))
         .withFragmentShader(VisualResources.shader(WEATHER_PARTICLES3))
         .withDefine(COLISEUM_RAIN)
         .withDepthTest(CompareOp.GREATER_THAN_OR_EQUAL)
         .withDepthWrite(false)
         .withCull(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withVertexFormat(DefaultVertexFormat.POSITION, PrimitiveTopology.QUADS)
         .withUniform(WEATHER1, UniformType.UNIFORM_BUFFER)
         .withUniform(FOG1, UniformType.UNIFORM_BUFFER)
         .withUniform(RAIN_ROOF, UniformType.UNIFORM_BUFFER)
         .withSampler(ROOF_SAMPLER)
         .buildPipeline();
      this.renderPipeline3 = VisualPipelineBuilder.builder()
         .withLocation(COLISEUM_WEATHER_RAIN_GROUND)
         .withVertexShader(VisualResources.shader(WEATHER_GROUND))
         .withFragmentShader(VisualResources.shader(WEATHER_GROUND1))
         .withDefine(COLISEUM_RAIN1)
         .withDepthTest(CompareOp.ALWAYS_PASS)
         .withDepthWrite(false)
         .withCull(false)
         .withoutBlend()
         .withVertexFormat(DefaultVertexFormat.POSITION, PrimitiveTopology.QUADS)
         .withUniform(WEATHER2, UniformType.UNIFORM_BUFFER)
         .withUniform(FOG2, UniformType.UNIFORM_BUFFER)
         .withUniform(RAIN_ROOF1, UniformType.UNIFORM_BUFFER)
         .withSampler(DEPTH_SAMPLER)
         .withSampler(ROOF_SAMPLER1)
         .buildPipeline();
      this.renderPipeline4 = VisualPipelineBuilder.builder()
         .withLocation(COLISEUM_WEATHER_GROUND)
         .withVertexShader(VisualResources.shader(WEATHER_GROUND2))
         .withFragmentShader(VisualResources.shader(WEATHER_GROUND3))
         .withDepthTest(CompareOp.ALWAYS_PASS)
         .withDepthWrite(false)
         .withCull(false)
         .withoutBlend()
         .withVertexFormat(DefaultVertexFormat.POSITION, PrimitiveTopology.QUADS)
         .withUniform(WEATHER3, UniformType.UNIFORM_BUFFER)
         .withUniform(FOG3, UniformType.UNIFORM_BUFFER)
         .withSampler(DEPTH_SAMPLER1)
         .buildPipeline();
      this.renderPipeline5 = VisualPipelineBuilder.builder()
         .withLocation(COLISEUM_AMBIENCE_BLIZZARD)
         .withVertexShader(VisualResources.shader(HANDS_FULLSCREEN))
         .withFragmentShader(VisualResources.shader(AMBIENCE_BLIZZARD))
         .withDepthTest(CompareOp.ALWAYS_PASS)
         .withDepthWrite(false)
         .withCull(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withVertexFormat(VisualPipelineBuilder.VERTEXFORMAT, PrimitiveTopology.TRIANGLES)
         .withUniform(BLIZZARD, UniformType.UNIFORM_BUFFER)
         .withSampler(DEPTH_SAMPLER2)
         .buildPipeline();
      this.renderPipeline6 = VisualPipelineBuilder.builder()
         .withLocation(COLISEUM_AMBIENCE_SHADER_FOG)
         .withVertexShader(VisualResources.shader(HANDS_FULLSCREEN1))
         .withFragmentShader(VisualResources.shader(AMBIENCE_SHADER_FOG))
         .withDepthTest(CompareOp.ALWAYS_PASS)
         .withDepthWrite(false)
         .withCull(false)
         .withoutBlend()
         .withVertexFormat(VisualPipelineBuilder.VERTEXFORMAT, PrimitiveTopology.TRIANGLES)
         .withUniform(SHADER_FOG, UniformType.UNIFORM_BUFFER)
         .withSampler(DEPTH_SAMPLER3)
         .buildPipeline();
      this.renderPipeline7 = VisualPipelineBuilder.builder()
         .withLocation(COLISEUM_SHADER_MIST_PULSAR)
         .withVertexShader(VisualResources.shader(HANDS_FULLSCREEN2))
         .withFragmentShader(VisualResources.shader(SHADER_MIST_PULSAR))
         .withDepthTest(CompareOp.ALWAYS_PASS)
         .withDepthWrite(false)
         .withCull(false)
         .withoutBlend()
         .withVertexFormat(VisualPipelineBuilder.VERTEXFORMAT, PrimitiveTopology.TRIANGLES)
         .withUniform(SHADER_MIST, UniformType.UNIFORM_BUFFER)
         .withSampler(DEPTH_SAMPLER4)
         .buildPipeline();
      this.renderPipeline8 = VisualPipelineBuilder.builder()
         .withLocation(COLISEUM_SHADER_MIST_GALAXY)
         .withVertexShader(VisualResources.shader(HANDS_FULLSCREEN3))
         .withFragmentShader(VisualResources.shader(SHADER_MIST_GALAXY))
         .withDepthTest(CompareOp.ALWAYS_PASS)
         .withDepthWrite(false)
         .withCull(false)
         .withoutBlend()
         .withVertexFormat(VisualPipelineBuilder.VERTEXFORMAT, PrimitiveTopology.TRIANGLES)
         .withUniform(SHADER_MIST1, UniformType.UNIFORM_BUFFER)
         .withSampler(DEPTH_SAMPLER5)
         .buildPipeline();
      this.renderPipeline9 = VisualPipelineBuilder.builder()
         .withLocation(COLISEUM_SHADER_MIST_JUICE)
         .withVertexShader(VisualResources.shader(HANDS_FULLSCREEN4))
         .withFragmentShader(VisualResources.shader(SHADER_MIST_JUICE))
         .withDepthTest(CompareOp.ALWAYS_PASS)
         .withDepthWrite(false)
         .withCull(false)
         .withoutBlend()
         .withVertexFormat(VisualPipelineBuilder.VERTEXFORMAT, PrimitiveTopology.TRIANGLES)
         .withUniform(SKY_SHADER_DATA, UniformType.UNIFORM_BUFFER)
         .withSampler(DEPTH_SAMPLER6)
         .buildPipeline();
      this.renderPipeline10 = VisualPipelineBuilder.builder()
         .withLocation(COLISEUM_AMBIENCE_SHADER_SKY)
         .withVertexShader(VisualResources.shader(HANDS_FULLSCREEN5))
         .withFragmentShader(VisualResources.shader(AMBIENCE_SHADER_SKY_COMPOSITE))
         .withDepthTest(CompareOp.ALWAYS_PASS)
         .withDepthWrite(false)
         .withCull(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withVertexFormat(VisualPipelineBuilder.VERTEXFORMAT, PrimitiveTopology.TRIANGLES)
         .withSampler(SKY_SAMPLER)
         .withSampler(DEPTH_SAMPLER7)
         .buildPipeline();
      this.renderPipeline11 = VisualPipelineBuilder.builder()
         .withLocation(COLISEUM_AMBIENCE_WORLD_COLOR)
         .withVertexShader(VisualResources.shader(HANDS_FULLSCREEN6))
         .withFragmentShader(VisualResources.shader(AMBIENCE_WORLD_COLOR))
         .withDepthTest(CompareOp.ALWAYS_PASS)
         .withDepthWrite(false)
         .withCull(false)
         .withBlend(BLENDFUNCTION)
         .withVertexFormat(VisualPipelineBuilder.VERTEXFORMAT, PrimitiveTopology.TRIANGLES)
         .withUniform(WORLD_COLOR, UniformType.UNIFORM_BUFFER)
         .buildPipeline();
      this.renderPipeline12 = VisualPipelineBuilder.builder()
         .withLocation(COLISEUM_AMBIENCE_LIGHTNING)
         .withVertexShader(VisualResources.shader(AMBIENCE_LIGHTNING))
         .withFragmentShader(VisualResources.shader(AMBIENCE_LIGHTNING1))
         .withDepthTest(CompareOp.GREATER_THAN_OR_EQUAL)
         .withDepthWrite(false)
         .withCull(false)
         .withBlend(BlendFunction.LIGHTNING)
         .withVertexFormat(DefaultVertexFormat.POSITION_TEX, PrimitiveTopology.QUADS)
         .withUniform(LIGHTNING, UniformType.UNIFORM_BUFFER)
         .buildPipeline();
      this.renderPipeline13 = VisualPipelineBuilder.builder()
         .withLocation(COLISEUM_AMBIENCE_LIGHTNING_CORE)
         .withVertexShader(VisualResources.shader(AMBIENCE_LIGHTNING2))
         .withFragmentShader(VisualResources.shader(AMBIENCE_LIGHTNING_CORE))
         .withDepthTest(CompareOp.GREATER_THAN_OR_EQUAL)
         .withDepthWrite(false)
         .withCull(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withVertexFormat(DefaultVertexFormat.POSITION_TEX, PrimitiveTopology.QUADS)
         .withUniform(LIGHTNING1, UniformType.UNIFORM_BUFFER)
         .buildPipeline();
      this.renderPipeline14 = VisualPipelineBuilder.builder()
         .withLocation(COLISEUM_AMBIENCE_LIGHTNING_FLASH)
         .withVertexShader(VisualResources.shader(HANDS_FULLSCREEN7))
         .withFragmentShader(VisualResources.shader(AMBIENCE_LIGHTNING_FLASH))
         .withDepthTest(CompareOp.ALWAYS_PASS)
         .withDepthWrite(false)
         .withCull(false)
         .withBlend(BlendFunction.LIGHTNING)
         .withVertexFormat(VisualPipelineBuilder.VERTEXFORMAT, PrimitiveTopology.TRIANGLES)
         .withUniform(LIGHTNING_FLASH, UniformType.UNIFORM_BUFFER)
         .withSampler(DEPTH_SAMPLER8)
         .buildPipeline();
      this.renderPipeline15 = VisualPipelineBuilder.builder()
         .withLocation(COLISEUM_WEATHER_GROUND_COMPOSITE)
         .withVertexShader(VisualResources.shader(HANDS_PASS))
         .withFragmentShader(VisualResources.shader(WEATHER_GROUND_COMPOSITE))
         .withDepthTest(CompareOp.ALWAYS_PASS)
         .withDepthWrite(false)
         .withCull(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withVertexFormat(DefaultVertexFormat.POSITION, PrimitiveTopology.QUADS)
         .withSampler(SAMPLER0)
         .buildPipeline();
      this.renderPipeline16 = VisualPipelineBuilder.builder()
         .withLocation(COLISEUM_AMBIENCE_LIGHTNING_GLOW)
         .withVertexShader(VisualResources.shader(HANDS_FULLSCREEN8))
         .withFragmentShader(VisualResources.shader(AMBIENCE_LIGHTNING_GLOW_MASK))
         .withDepthTest(CompareOp.ALWAYS_PASS)
         .withDepthWrite(false)
         .withCull(false)
         .withoutBlend()
         .withVertexFormat(VisualPipelineBuilder.VERTEXFORMAT, PrimitiveTopology.TRIANGLES)
         .withSampler(IN_SAMPLER)
         .buildPipeline();
      this.renderPipeline17 = VisualPipelineBuilder.builder()
         .withLocation(COLISEUM_AMBIENCE_LIGHTNING_GLOW1)
         .withVertexShader(VisualResources.shader(HANDS_FULLSCREEN9))
         .withFragmentShader(VisualResources.shader(AMBIENCE_LIGHTNING_GLOW_BLUR))
         .withDepthTest(CompareOp.ALWAYS_PASS)
         .withDepthWrite(false)
         .withCull(false)
         .withoutBlend()
         .withVertexFormat(VisualPipelineBuilder.VERTEXFORMAT, PrimitiveTopology.TRIANGLES)
         .withUniform(LIGHTNING_GLOW_BLUR, UniformType.UNIFORM_BUFFER)
         .withSampler(IN_SAMPLER1)
         .buildPipeline();
      this.renderPipeline18 = VisualPipelineBuilder.builder()
         .withLocation(COLISEUM_AMBIENCE_LIGHTNING_GLOW2)
         .withVertexShader(VisualResources.shader(HANDS_FULLSCREEN10))
         .withFragmentShader(VisualResources.shader(AMBIENCE_LIGHTNING_GLOW_COMPOSITE))
         .withDepthTest(CompareOp.ALWAYS_PASS)
         .withDepthWrite(false)
         .withCull(false)
         .withBlend(BlendFunction.LIGHTNING)
         .withVertexFormat(VisualPipelineBuilder.VERTEXFORMAT, PrimitiveTopology.TRIANGLES)
         .withUniform(LIGHTNING_GLOW_COMPOSITE, UniformType.UNIFORM_BUFFER)
         .withSampler(BLOOM_SAMPLER)
         .buildPipeline();
      this.count144 = COUNT93;
      this.value134 = Float.NaN;
      this.value135 = VALUE1986;
      this.gpuBuffer10 = new GpuBuffer[3];
      this.value136 = new long[3];
      this.gpuBuffer11 = new GpuBuffer[3];
      this.items26 = new ArrayList<>();
      this.matrix4f = new Matrix4f();
      this.matrix4f1 = new Matrix4f();
      this.matrix4f2 = new Matrix4f();
      this.matrix4f3 = new Matrix4f();
      this.count146 = -1;
      this.multiChoiceSetting56 = new ModeSetting(ТУМАН, НИЧЕГО_НЕ_ДЕЛАТЬ, НИЧЕГО_НЕ_ДЕЛАТЬ, ОЧИСТИТЬ, ПЕРЕОПРЕДЕЛИТЬ);
      this.sliderSetting172 = new NumberSetting(КОНЕЦ_ТУМАНА, 1.0F, VALUE1987, VALUE1988, VALUE1989).visibleWhen(() -> this.multiChoiceSetting56.is(ПЕРЕОПРЕДЕЛИТЬ4));
      this.sliderSetting173 = new NumberSetting(НАЧАЛО_ТУМАНА, VALUE1990, VALUE1991, VALUE1992, VALUE1993).visibleWhen(() -> this.multiChoiceSetting56.is(ПЕРЕОПРЕДЕЛИТЬ3));
      this.checkboxSetting152 = new BooleanSetting(АВТО_ЦВЕТ_ПОД_ТЕМУ1, true);
      this.colorSetting3 = new ColorSetting(ЦВЕТ_ТУМАНА, -1).visibleWhen(() -> this.multiChoiceSetting56.is(ПЕРЕОПРЕДЕЛИТЬ2));
      this.multiChoiceSetting57 = new ModeSetting(ШЕЙДЕР_НЕБА, НИЧЕГО, НИЧЕГО, ПУЛЬСАР, ГАЛАКТИКА, СОК, КОСМОС, МЛЕЧНЫЙ_ПУТЬ, PURPLE, АВРОРА, АВРОРА_2, ТУМАННОСТЬ, ПЛАЗМА, ИСТОЧНИК, НЕБУЛА);
      this.sliderSetting174 = new NumberSetting(ИНТЕНСИВНОСТЬ_ШЕЙДЕРА, 1.0F, 0.0F, VALUE1994, VALUE1995).visibleWhen(() -> this.is178() || this.is175());
      this.sliderSetting175 = new NumberSetting(СКОРОСТЬ_ШЕЙДЕРА, VALUE1996, 0.0F, VALUE1997, VALUE1998).visibleWhen(this::is178);
      this.checkboxSetting153 = new BooleanSetting(ЗВЁЗДЫ, false).visibleWhen(this::is180);
      this.sliderSetting176 = new NumberSetting(СКОРОСТЬ_НЕБА, 1.0F, VALUE1999, VALUE2000, VALUE2001).visibleWhen(() -> this.is184() || this.is175());
      this.sliderSetting177 = new NumberSetting(РАЗМЕР_НЕБА, VALUE2002, 1.0F, VALUE2003, VALUE2004).visibleWhen(this::is185);
      this.sliderSetting178 = new NumberSetting(ИНТЕНСИВНОСТЬ_НЕБА, VALUE2005, VALUE2006, VALUE2007, VALUE2008).visibleWhen(this::is185);
      this.sliderSetting179 = new NumberSetting(ПРОЗРАЧНОСТЬ_НЕБА, 1.0F, VALUE2009, 1.0F, VALUE2010).visibleWhen(() -> this.is184() || this.is175());
      this.sliderSetting180 = new NumberSetting(СИЛА_АВРОРЫ, 1.0F, VALUE2011, 1.0F, VALUE2012).visibleWhen(this::is193);
      this.sliderSetting181 = new NumberSetting(СКОРОСТЬ_АВРОРЫ, 1.0F, VALUE2013, VALUE2014, VALUE2015).visibleWhen(this::is193);
      this.sliderSetting182 = new NumberSetting(МАСШТАБ_АВРОРЫ, VALUE2016, VALUE2017, VALUE2018, VALUE2019).visibleWhen(this::is193);
      this.sliderSetting183 = new NumberSetting(ЗВЁЗДЫ_АВРОРЫ, VALUE2020, 0.0F, 1.0F, VALUE2021).visibleWhen(this::is193);
      this.colorSetting4 = new ColorSetting(ЦВЕТ_АВРОРЫ_1, COUNT94).visibleWhen(() -> this.is193() && !this.checkboxSetting152.enabled());
      this.colorSetting5 = new ColorSetting(ЦВЕТ_АВРОРЫ_2, COUNT95).visibleWhen(() -> this.is193() && !this.checkboxSetting152.enabled());
      this.checkboxSetting154 = new BooleanSetting(ВАНИЛЬНОЕ_НЕБО, false).visibleWhen(this::is193);
      this.multiChoiceSetting58 = new ModeSetting(ПОГОДА, НИЧЕГО_НЕ_ДЕЛАТЬ1, НИЧЕГО_НЕ_ДЕЛАТЬ1, ОЧИСТИТЬ1, ДОЖДЬ, СНЕГ, ПУРГА);
      this.systemClass295 = new TimeSetting(ВРЕМЯ, ОБЫЧНОЕ, УТРО, ДЕНЬ, ВЕЧЕР, ЗАКАТ, НОЧЬ);
      this.multiChoiceSetting59 = new ModeSetting(ЦВЕТ_МИРА, НИЧЕГО1, НИЧЕГО1, ТЕМА, СВОЙ);
      this.colorSetting6 = new ColorSetting(ОТТЕНОК_МИРА, -1).visibleWhen(() -> this.multiChoiceSetting59.is(СВОЙ1));
      this.sliderSetting184 = new NumberSetting(СИЛА_ЦВЕТА_МИРА, VALUE2022, 0.0F, VALUE2023, VALUE2024).visibleWhen(() -> !this.multiChoiceSetting59.is(НИЧЕГО5));
      this.sliderSetting185 = new NumberSetting(ИНТЕНСИВНОСТЬ_ПОГОДЫ, VALUE2025, VALUE2026, 1.0F, VALUE2027).visibleWhen(this::is187);
      this.sliderSetting186 = new NumberSetting(ДАЛЬНОСТЬ_ПОГОДЫ, VALUE2028, VALUE2029, VALUE2030, 1.0F).visibleWhen(this::is187);
      this.sliderSetting187 = new NumberSetting(СКОРОСТЬ_ПАДЕНИЯ, 1.0F, VALUE2031, 2.0F, VALUE2032).visibleWhen(this::is187);
      this.sliderSetting188 = new NumberSetting(РАЗМЕР_ЧАСТИЦ_ПОГОДЫ, 1.0F, VALUE2033, 2.0F, VALUE2034).visibleWhen(this::is187);
      this.sliderSetting189 = new NumberSetting(СКОРОСТЬ_ВЕТРА, VALUE2035, 0.0F, VALUE2036, VALUE2037).visibleWhen(this::is187);
      this.sliderSetting190 = new NumberSetting(НАПРАВЛЕНИЕ_ВЕТРА, 0.0F, VALUE2038, VALUE2039, VALUE2040).visibleWhen(this::is187);
      this.checkboxSetting155 = new BooleanSetting(ЭФФЕКТЫ_ПОГОДЫ_НА_ЗЕМЛЕ, true).visibleWhen(this::is187);
      this.sliderSetting191 = new NumberSetting(ВРЕМЯ_ЭФФЕКТА_НА_ЗЕМЛЕ, VALUE2041, VALUE2042, VALUE2043, VALUE2044).visibleWhen(() -> this.is187() && this.checkboxSetting155.enabled());
      this.sliderSetting192 = new NumberSetting(РАЗМЕР_ЭФФЕКТА_НА_ЗЕМЛЕ, VALUE2045, VALUE2046, VALUE2047, VALUE2048).visibleWhen(() -> this.is187() && this.checkboxSetting155.enabled());
      this.colorSetting7 = new ColorSetting(ЦВЕТ_ДОЖДЯ, COUNT96).visibleWhen(() -> this.multiChoiceSetting58.is(ДОЖДЬ6) && !this.checkboxSetting152.enabled());
      this.colorSetting8 = new ColorSetting(ЦВЕТ_СНЕГА, -1).visibleWhen(() -> (this.multiChoiceSetting58.is(СНЕГ3) || this.multiChoiceSetting58.is(ПУРГА4)) && !this.checkboxSetting152.enabled());
      this.checkboxSetting156 = new BooleanSetting(МОЛНИИ4, true);
      this.sliderSetting193 = new NumberSetting(СКОРОСТЬ_ПОЯВЛЕНИЯ, 1.0F, VALUE2049, VALUE2050, VALUE2051).visibleWhen(this.checkboxSetting156::enabled);
      this.sliderSetting194 = new NumberSetting(КОЛИЧЕСТВО_МОЛНИЙ, 1.0F, 1.0F, VALUE2052, 1.0F).visibleWhen(this.checkboxSetting156::enabled);
      this.checkboxSetting157 = new BooleanSetting(ЗВУКИ_ГРОМА, false).visibleWhen(this.checkboxSetting156::enabled);
      this.sliderSetting195 = new NumberSetting(ГРОМКОСТЬ_ГРОМА, VALUE2053, 0.0F, 1.0F, VALUE2054).visibleWhen(() -> this.checkboxSetting156.enabled() && this.checkboxSetting157.enabled());
      this.checkboxSetting158 = new BooleanSetting(УДАЛЯТЬ_ОБЛАКА, false);
      this.systemClass405_258 = this::apply529;
      this.systemClass405_259 = var1 -> this.update373();
      this.addSettings(
         this.multiChoiceSetting56,
         this.sliderSetting173,
         this.sliderSetting172,
         this.checkboxSetting152,
         this.colorSetting3,
         this.multiChoiceSetting57,
         this.sliderSetting174,
         this.sliderSetting175,
         this.checkboxSetting153,
         this.sliderSetting176,
         this.sliderSetting177,
         this.sliderSetting178,
         this.sliderSetting179,
         this.sliderSetting180,
         this.sliderSetting181,
         this.sliderSetting182,
         this.sliderSetting183,
         this.colorSetting4,
         this.colorSetting5,
         this.checkboxSetting154,
         this.systemClass295,
         this.multiChoiceSetting59,
         this.colorSetting6,
         this.sliderSetting184,
         this.multiChoiceSetting58,
         this.sliderSetting185,
         this.sliderSetting186,
         this.sliderSetting187,
         this.sliderSetting188,
         this.sliderSetting189,
         this.sliderSetting190,
         this.checkboxSetting155,
         this.sliderSetting191,
         this.sliderSetting192,
         this.colorSetting7,
         this.colorSetting8,
         this.checkboxSetting156,
         this.sliderSetting193,
         this.sliderSetting194,
         this.checkboxSetting157,
         this.sliderSetting195,
         this.checkboxSetting158
      );
   }

   public void update371() {
      this.systemClass519.close();
      if (this.is193()) {
         this.value135 = VALUE2072;
      }

      this.update364();
      this.systemClass424.close();
      this.systemClass111.close();
      this.value138 = 0L;
   }

   private boolean is191() {
      return this.is() && !this.multiChoiceSetting59.is(НИЧЕГО3) && this.sliderSetting184.floatValue() > 0.0F && mc.level != null;
   }

   private void apply542(Vec3 var1) {
      ThreadLocalRandom var2 = ThreadLocalRandom.current();
      double var3 = Math.toRadians(-mc.gameRenderer.mainCamera().yRot());
      double var5 = var3 + var2.nextDouble(VALUE2155, VALUE2156);
      double var7 = var2.nextDouble(VALUE2157, VALUE2158);
      double var9 = var1.x + Math.sin(var5) * var7;
      double var11 = var1.z + Math.cos(var5) * var7;
      int var13 = mc.level.getHeight(Types.WORLD_SURFACE, Mth.floor(var9), Mth.floor(var11));
      if (var13 <= mc.level.getMinY() + 2) {
         var13 = Mth.floor(var1.y - VALUE2159);
      }

      double var14 = Math.max(var13 + var2.nextDouble(VALUE2160, VALUE2161), var1.y + VALUE2162);
      double var16 = var9 + var2.nextDouble(VALUE2163, VALUE2164);
      double var18 = var11 + var2.nextDouble(VALUE2165, VALUE2166);
      this.vec3_20 = new Vec3(var16, var14, var18);
      this.vec3_21 = new Vec3(var9, var13 + VALUE2167, var11);
      byte var20 = 25;
      double[] var21 = new double[var20];
      double[] var22 = new double[var20];
      Random var23 = new Random(var2.nextLong());
      apply537(var21, 0, var20 - 1, VALUE2168, var23);
      apply537(var22, 0, var20 - 1, VALUE2169, var23);
      ArrayList var24 = new ArrayList(var20);

      for (int var25 = 0; var25 < var20; var25++) {
         double var26 = (double)var25 / (var20 - 1);
         double var28 = Mth.lerp(var26, var16, var9) + var21[var25];
         double var30 = Mth.lerp(var26, var14, var13 + VALUE2170);
         double var32 = Mth.lerp(var26, var18, var11) + var22[var25];
         var24.add(new Vec3(var28, var30, var32));
         if (var25 > 0) {
            this.items26.add(new Ambience.Vec3Vec3Record((Vec3)var24.get(var25 - 1), (Vec3)var24.get(var25), 1.0F));
         }
      }

      for (int var35 = 3; var35 < var20 - 3; var35 += var2.nextInt(3, 6)) {
         if (!(var2.nextFloat() > VALUE2171)) {
            Vec3 var36 = (Vec3)var24.get(var35);
            double var27 = var2.nextDouble(0.0, VALUE2172);
            int var29 = var2.nextInt(3, 7);

            for (int var37 = 0; var37 < var29; var37++) {
               float var31 = VALUE2173 * (1.0F - (float)var37 / var29);
               double var38 = VALUE2174 + var37 * VALUE2175;
               Vec3 var34 = var36.add(
                  Math.sin(var27) * var38 + var2.nextDouble(VALUE2176, VALUE2177),
                  -var2.nextDouble(VALUE2178, VALUE2179),
                  Math.cos(var27) * var38 + var2.nextDouble(VALUE2180, VALUE2181)
               );
               this.items26.add(new Ambience.Vec3Vec3Record(var36, var34, Math.max(VALUE2182, var31)));
               var36 = var34;
            }
         }
      }
   }

   private void apply543(Matrix4f var1, Matrix4f var2) {
      if (this.is192() && var1 != null && var2 != null) {
         long var3 = System.nanoTime();
         if (this.value144 == 0L) {
            this.apply539(var3);
         }

         if (this.value143 == 0L && var3 >= this.value144) {
            this.apply549(mc.gameRenderer.mainCamera().position(), var3);
         }

         if (this.value143 != 0L && !this.items26.isEmpty()) {
            float var5 = (float)(var3 - this.value143) / VALUE2116;
            if (var5 >= VALUE2117) {
               this.items26.clear();
               this.value143 = 0L;
               this.vec3_20 = null;
               this.vec3_21 = null;
               this.apply539(var3);
            } else {
               float var6 = compute109(var5);
               if (!(var6 <= VALUE2118)) {
                  RenderTarget var7 = mc.gameRenderer.mainRenderTarget();
                  if (var7 != null && var7.getColorTexture() != null && var7.getDepthTexture() != null && !var7.getDepthTexture().isClosed()) {
                     Vec3 var8 = mc.gameRenderer.mainCamera().position();
                     int var9 = VisualTheme.themeColor(VisualThemeColor.MAIN) | COUNT109;
                     int var10 = VisualColors.lerpColor(var9, VisualColors.rgb(255, 255, 255), VALUE2119);
                     int var11 = VisualColors.lerpColor(var9, VisualColors.rgb(255, 255, 255), VALUE2120);
                     Vector4f var12 = VisualColors.toRgbaVector(var9);
                     Vector4f var13 = VisualColors.toRgbaVector(var10);
                     Vector4f var14 = VisualColors.toRgbaVector(var11);
                     this.apply523(var7, var1, var2, var8, var6, var12);
                     TextureTarget var15 = this.resolveTextureTarget1(var7.width, var7.height);
                     CommandEncoder var16 = RenderSystem.getDevice().createCommandEncoder();
                     float var17 = compute110(var6);
                     var16.clearColorTexture(var15.getColorTexture(), new Vector4f(0.0F));
                     var15.copyDepthFrom(var7);
                     this.apply527(var15, var1, var2, var8, VALUE2121, new Vector4f(var12.x, var12.y, var12.z, 1.0F), 0);
                     this.apply540(var15, var7, var9, var10, VALUE2122 * var17, VALUE2123 * var17);
                     this.apply527(var7, var1, var2, var8, VALUE2124, new Vector4f(var13.x, var13.y, var13.z, var6), 1);
                     this.apply527(var7, var1, var2, var8, VALUE2125, new Vector4f(var14.x, var14.y, var14.z, var6), 2);
                  }
               }
            }
         }
      } else {
         this.items26.clear();
         this.value143 = 0L;
         this.value144 = 0L;
         this.vec3_20 = null;
         this.vec3_21 = null;
      }
   }

   private void update372() {
      if (this.textureTarget3 != null) {
         this.textureTarget3.destroyBuffers();
      }

      if (this.textureTarget4 != null) {
         this.textureTarget4.destroyBuffers();
      }

      this.textureTarget3 = null;
      this.textureTarget4 = null;
      if (this.gpuBuffer6 != null && !this.gpuBuffer6.isClosed()) {
         this.gpuBuffer6.close();
      }

      if (this.gpuBuffer7 != null && !this.gpuBuffer7.isClosed()) {
         this.gpuBuffer7.close();
      }

      if (this.gpuBuffer8 != null && !this.gpuBuffer8.isClosed()) {
         this.gpuBuffer8.close();
      }

      this.gpuBuffer6 = null;
      this.gpuBuffer7 = null;
      this.gpuBuffer8 = null;
   }

   private void apply544(RenderTarget var1, CommandEncoder var2, int var3, int var4, float var5) {
      float var6 = (float)(System.currentTimeMillis() - this.value135) / VALUE2087;
      Quaternionf var7 = mc.gameRenderer.mainCamera().rotation();
      int var8 = VisualTheme.themeColor(VisualThemeColor.MAIN) | COUNT102;
      int var9 = VisualTheme.themeColor(VisualThemeColor.MAIN_TWO) | COUNT103;
      Vector4f var10 = VisualColors.toRgbaVector(var8);
      Vector4f var11 = VisualColors.toRgbaVector(var9);
      Vector4f var12 = VisualColors.toRgbaVector(VisualColors.lerpColor(var8, -8054, VALUE2088));
      Vector4f var13 = VisualColors.toRgbaVector(this.compute106(this.colorSetting3.color()) | COUNT104);
      MemoryStack var14 = MemoryStack.stackPush();

      try {
         ByteBuffer var15 = Std140Builder.onStack(var14, 160)
            .putVec4(var3, var4, var6, this.sliderSetting176.floatValue())
            .putVec4(this.sliderSetting174.floatValue(), this.sliderSetting179.floatValue(), var5, 0.0F)
            .putVec4(var7.x, var7.y, var7.z, var7.w)
            .putVec4(var10.x, var10.y, var10.z, var10.w)
            .putVec4(var11.x, var11.y, var11.z, var11.w)
            .putVec4(var10.x, var10.y, var10.z, var10.w)
            .putVec4(var11.x, var11.y, var11.z, var11.w)
            .putVec4(var12.x, var12.y, var12.z, var12.w)
            .putVec4(var13.x, var13.y, var13.z, var13.w)
            .putVec4(this.count101(), this.count100(), 0.0F, 0.0F)
            .get();
         var2.writeToBuffer(this.gpuBuffer3.slice(), var15);
         RenderPass var16 = var2.createRenderPass(() -> AMBIENCE_JUICE_SKY, this.textureTarget.getColorTextureView(), Optional.empty());

         try {
            var16.setPipeline(RenderSystem.getCompiledPipeline(this.renderPipeline9));
            var16.setUniform(DEPTH_SAMPLER11, var1.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            var16.setUniform(SKY_SHADER_DATA1, this.gpuBuffer3);
            var16.draw(3, 1, 0, 0);
         } catch (Throwable var21) {
            if (var16 != null) {
               try {
                  var16.close();
               } catch (Throwable var20) {
                  var21.addSuppressed(var20);
               }
            }

            throw var21;
         }

         if (var16 != null) {
            var16.close();
         }
      } catch (Throwable var22) {
         if (var14 != null) {
            try {
               var14.close();
            } catch (Throwable var19) {
               var22.addSuppressed(var19);
            }
         }

         throw var22;
      }

      if (var14 != null) {
         var14.close();
      }
   }

   private boolean is192() {
      return this.is()
         && this.checkboxSetting156.enabled()
         && mc.player != null
         && mc.level != null
         && mc.gameRenderer != null
         && mc.gameRenderer.mainCamera() != null
         && mc.gameRenderer.mainCamera().getFluidInCamera() == FogType.NONE;
   }

   private void apply545(CommandEncoder var1, int var2, int var3, int var4, int var5, float var6, float var7) {
      MemoryStack var8 = MemoryStack.stackPush();

      try {
         ByteBuffer var9 = Std140Builder.onStack(var8, 32).putVec2(1.0F / var2, 1.0F / var3).putVec2(1.0F, 0.0F).putVec2(VALUE2134, VALUE2135).align(16).get();
         var1.writeToBuffer(this.gpuBuffer6.slice(), var9);
         ByteBuffer var10 = Std140Builder.onStack(var8, 32).putVec2(1.0F / var2, 1.0F / var3).putVec2(0.0F, 1.0F).putVec2(VALUE2136, VALUE2137).align(16).get();
         var1.writeToBuffer(this.gpuBuffer7.slice(), var10);
         Vector4f var11 = VisualColors.toRgbaVector(var4 | COUNT110);
         Vector4f var12 = VisualColors.toRgbaVector(var5 | COUNT111);
         ByteBuffer var13 = Std140Builder.onStack(var8, 48)
            .putVec4(var6, var7, 0.0F, 0.0F)
            .putVec4(var11.x, var11.y, var11.z, 1.0F)
            .putVec4(var12.x, var12.y, var12.z, 1.0F)
            .get();
         var1.writeToBuffer(this.gpuBuffer8.slice(), var13);
      } catch (Throwable var15) {
         if (var8 != null) {
            try {
               var8.close();
            } catch (Throwable var14) {
               var15.addSuppressed(var14);
            }
         }

         throw var15;
      }

      if (var8 != null) {
         var8.close();
      }
   }

   private void apply546(int var1, int var2) {
      if (this.textureTarget == null) {
         this.textureTarget = new TextureTarget(AMBIENCE_SHADER_SKY_LAYER, var1, var2, GpuFormat.RGBA8_UNORM, null);
      } else if (this.textureTarget.width != var1 || this.textureTarget.height != var2) {
         this.textureTarget.resize(var1, var2);
      }
   }

   private void apply547(CommandEncoder var1, RenderTarget var2, RenderTarget var3, GpuBuffer var4) {
      RenderPass var5 = var1.createRenderPass(() -> AMBIENCE_LIGHTNING_DUAL_GLOW, var3.getColorTextureView(), Optional.empty());

      try {
         var5.setPipeline(RenderSystem.getCompiledPipeline(this.renderPipeline17));
         var5.setUniform(IN_SAMPLER3, var2.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
         var5.setUniform(LIGHTNING_GLOW_BLUR1, var4);
         var5.draw(3, 1, 0, 0);
      } catch (Throwable var9) {
         if (var5 != null) {
            try {
               var5.close();
            } catch (Throwable var8) {
               var9.addSuppressed(var8);
            }
         }

         throw var9;
      }

      if (var5 != null) {
         var5.close();
      }
   }

   private boolean is193() {
      return this.multiChoiceSetting57.is(АВРОРА_2_1);
   }

   public void apply548(Matrix4f var1, Matrix4f var2, DeltaTracker var3) {
      this.apply530(var1, var2);
      this.update370();
      this.apply533(var1, var2, var3);
   }

   private boolean is194() { return false; }

   private int compute113(int var1, VisualThemeColor var2) {
      return this.checkboxSetting152.enabled() ? VisualTheme.themeColor(var2) : var1;
   }

   private GpuBuffer getGpuBuffer1() {
      if (this.gpuBuffer5 == null || this.gpuBuffer5.isClosed()) {
         Matrix4f var1 = new Matrix4f();
         BufferBuilder var2 = SharedVertexBuffer.shared().beginVertices(PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION);
         var2.addVertex(var1, VALUE2249, VALUE2250, 0.0F);
         var2.addVertex(var1, 1.0F, VALUE2251, 0.0F);
         var2.addVertex(var1, 1.0F, 1.0F, 0.0F);
         var2.addVertex(var1, VALUE2252, 1.0F, 0.0F);
         MeshData var3 = var2.buildOrThrow();

         try {
            this.count145 = var3.drawState().indexCount();
            this.gpuBuffer5 = RenderSystem.getDevice().createBuffer(() -> AMBIENCE_SHARED_FULLSCREEN_QUAD, 32, var3.vertexBuffer());
         } catch (Throwable var7) {
            if (var3 != null) {
               try {
                  var3.close();
               } catch (Throwable var6) {
                  var7.addSuppressed(var6);
               }
            }

            throw var7;
         }

         if (var3 != null) {
            var3.close();
         }
      }

      return this.gpuBuffer5;
   }

   private void update373() {
      if (!this.is192() || !this.checkboxSetting157.enabled() || this.sliderSetting195.floatValue() <= 0.0F) {
         this.update364();
      } else if (!mc.isPaused() && this.count146 >= 0) {
         if (this.count146-- <= 0) {
            this.f_8483 = new Ambience.XjEHgoF49w8Sc100Inner(this.vec3_22);
            this.vec3_22 = null;
            mc.getSoundManager().play(this.f_8483);
         }
      }
   }

   private void apply549(Vec3 var1, long var2) {
      this.items26.clear();
      int var4 = Math.max(1, Math.round(this.sliderSetting194.floatValue()));

      for (int var5 = 0; var5 < var4; var5++) {
         this.apply542(var1);
      }

      this.value143 = var2;
      this.apply532(var1);
   }

   private void update374() {
      for (int var1 = 0; var1 < this.gpuBuffer10.length; var1++) {
         GpuBuffer var2 = this.gpuBuffer10[var1];
         if (var2 != null && !var2.isClosed()) {
            var2.close();
         }

         this.gpuBuffer10[var1] = null;
         this.value136[var1] = 0L;
         GpuBuffer var3 = this.gpuBuffer11[var1];
         if (var3 != null && !var3.isClosed()) {
            var3.close();
         }

         this.gpuBuffer11[var1] = null;
      }

      if (this.gpuBuffer9 != null && !this.gpuBuffer9.isClosed()) {
         this.gpuBuffer9.close();
      }

      this.gpuBuffer9 = null;
   }

   public void apply550(Matrix4f var1, Matrix4f var2, GpuBufferSlice var3) {
      this.apply528(var1, var2, var3);
   }

   private void update375() {
      this.systemClass519.close();
      this.value135 = VALUE2092;
      if (this.textureTarget != null) {
         this.textureTarget.destroyBuffers();
         this.textureTarget = null;
      }

      if (this.gpuBuffer2 != null) {
         this.gpuBuffer2.close();
         this.gpuBuffer2 = null;
      }

      if (this.gpuBuffer1 != null) {
         this.gpuBuffer1.close();
         this.gpuBuffer1 = null;
      }

      if (this.gpuBuffer3 != null) {
         this.gpuBuffer3.close();
         this.gpuBuffer3 = null;
      }
   }

   private record Vec3Vec3Record(Vec3 start, Vec3 end, float strength) {
   }

   private final class XjEHgoF49w8Sc100Inner extends AbstractTickableSoundInstance {
      public final ClientLevel clientLevel10 = MediaSessionHost.mc.level;
      public static final float VALUE2253 = 0.85F;
      public static final float VALUE2254 = 0.2F;

      public XjEHgoF49w8Sc100Inner(Vec3 var2) {
         super(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.MASTER, RandomSource.create());
         this.x = var2.x;
         this.y = var2.y;
         this.z = var2.z;
         this.volume = Ambience.this.sliderSetting195.floatValue();
         this.pitch = VALUE2253 + this.random.nextFloat() * VALUE2254;
         this.attenuation = Attenuation.NONE;
      }

      public void tick() {
         if (MediaSessionHost.mc.level == this.clientLevel10 && Ambience.this.is192() && Ambience.this.checkboxSetting157.enabled()) {
            this.volume = Ambience.this.sliderSetting195.floatValue();
         } else {
            this.stop();
         }
      }

      public void finish() {
         this.stop();
      }
   }
   public void modifyFog(FogSettingsEvent event) { if(enabled()) apply529(event); }

   @Override public void tick(net.minecraft.client.Minecraft client) {
      if (this.sliderSetting173.floatValue() > this.sliderSetting172.floatValue())
         this.sliderSetting173.setSilently(this.sliderSetting172.get());
   }

}
