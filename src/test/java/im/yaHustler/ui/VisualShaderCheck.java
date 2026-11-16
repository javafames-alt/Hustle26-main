package im.yaHustler.ui;

import com.mojang.renderpearl.api.pipeline.ShaderType;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import static org.lwjgl.sdl.SDLInit.*;
import static org.lwjgl.sdl.SDLVideo.*;
import static org.lwjgl.sdl.SDLError.*;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL20.*;

/** Opt-in GPU compiler audit. Creates an invisible context and never starts a Minecraft client. */
public final class VisualShaderCheck {
    private static boolean ringOnly;
    private static boolean saturationOnly;
    private static boolean blocksOnly;
    private static boolean chamsOnly;
    private static boolean hotbarOnly;
    private static boolean motionOnly;
    private static boolean guiOnly;
    private static boolean crystalOnly;
    private static boolean handsOnly;
    private static boolean fireOnly;
    private static boolean weatherOnly;
    private static boolean searchOnly;
    private static boolean textOnly;
    private static boolean pickerOnly;
    private static java.util.List<com.mojang.renderpearl.api.pipeline.RenderPipeline> pipelines() throws Exception {
        if(saturationOnly)return List.of(im.yaHustler.module.impl.render.Saturation.pipeline());
        if(blocksOnly)return List.of(im.yaHustler.module.impl.render.BlockESP.pipeline());
        if(ringOnly)return List.of(im.yaHustler.font.MsdfPipeline.fovRing());
        if(chamsOnly)return ChamsShaderPipelines.collect();
        return pickerOnly?List.of(im.yaHustler.font.MsdfPipeline.colorField(),im.yaHustler.font.MsdfPipeline.hueStrip()):textOnly?List.of(im.yaHustler.font.MsdfPipeline.text(),im.yaHustler.font.MsdfPipeline.crisp(),im.yaHustler.font.MsdfPipeline.get()):searchOnly?List.of(im.yaHustler.font.MsdfPipeline.searchSurface(), im.yaHustler.font.MsdfPipeline.searchIcon()):hotbarOnly?List.of(im.yaHustler.font.MsdfPipeline.slotOutline()):VisualPipelineAudit.collect().stream()
            .filter(p -> !motionOnly || p.getLocation().getPath().equals("pipeline/motion_blur"))
            .filter(p -> !guiOnly || p.getLocation().getPath().startsWith("pipeline/gui/"))
            .filter(p -> !crystalOnly || p.getLocation().getPath().startsWith("pipeline/custom_crystal_"))
            .filter(p -> !weatherOnly || p.getLocation().getPath().startsWith("pipeline/weather/"))
            .filter(p -> !handsOnly || p.getLocation().getPath().startsWith("pipeline/hands/flame_"))
            .filter(p -> !fireOnly || p.getLocation().getPath().startsWith("pipeline/hands/fire_") || p.getLocation().getPath().startsWith("pipeline/hands/hands_")).toList();
    }
    private static final Set<String> RESOURCES = new TreeSet<>();
    private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*#(?:moj_import|include)\\s+[<\"]([^>\"]+)[>\"]\\s*$");
    private static String load(String path, Set<String> included) throws Exception {
        if (!included.add(path)) return "";
        try (var input = VisualShaderCheck.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing shader dependency: " + path);
            RESOURCES.add(path);
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = IMPORT.matcher(source);
            StringBuilder output = new StringBuilder();
            while (matcher.find()) {
                String id = matcher.group(1), dependency;
                if (id.contains(":")) {
                    String[] parts=id.split(":",2);
                    dependency="/assets/"+parts[0]+"/shaders/include/"+parts[1];
                } else dependency="/assets/minecraft/shaders/include/"+id;
                matcher.appendReplacement(output, Matcher.quoteReplacement(load(dependency, included)));
            }
            matcher.appendTail(output);
            return output.toString();
        }
    }
    private static int compile(ShaderType type, String resource, String defines) throws Exception {
        String source=load(resource,new HashSet<>());
        int firstLine=source.indexOf('\n');
        source=source.substring(0,firstLine+1)+defines+"#define gl_VertexIndex gl_VertexID\n#define gl_InstanceIndex gl_InstanceID\n"+source.substring(firstLine+1);
        int shader=glCreateShader(type==ShaderType.VERTEX?GL_VERTEX_SHADER:GL_FRAGMENT_SHADER);
        glShaderSource(shader,source); glCompileShader(shader);
        if (glGetShaderi(shader,GL_COMPILE_STATUS)==0) throw new AssertionError(resource+"\n"+glGetShaderInfoLog(shader));
        return shader;
    }
    public static void main(String[] args) throws Exception {
        saturationOnly=args.length>0&&args[0].equals("saturation");
        blocksOnly=args.length>0&&args[0].equals("blocks");
        ringOnly=args.length>0&&args[0].equals("ring");
        chamsOnly=args.length>0&&args[0].equals("chams");
        motionOnly=args.length>0 && args[0].equals("motion");
        guiOnly=args.length>0 && args[0].equals("gui");
        crystalOnly=args.length>0 && args[0].equals("crystal");
        handsOnly=args.length>0 && args[0].equals("hands");
        fireOnly=args.length>0 && args[0].equals("fire");
        hotbarOnly=args.length>0 && args[0].equals("hotbar");
        weatherOnly=args.length>0 && args[0].equals("weather");
        searchOnly=args.length>0 && args[0].equals("search");
        textOnly=args.length>0 && args[0].equals("text");
        pickerOnly=args.length>0 && args[0].equals("picker");
        verifyNativeCompiler();
        if(!SDL_Init(SDL_INIT_VIDEO)) throw new IllegalStateException(SDL_GetError());
        SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION,3);
        SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION,3);
        SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK,SDL_GL_CONTEXT_PROFILE_CORE);
        long window=SDL_CreateWindow("Hustle 26.3 shader validation",32,32,SDL_WINDOW_OPENGL|SDL_WINDOW_HIDDEN);
        if(window==0) throw new IllegalStateException(SDL_GetError());
        long context=SDL_GL_CreateContext(window);
        if(context==0) throw new IllegalStateException(SDL_GetError());
        Map<String,Integer> retainedPrograms = new LinkedHashMap<>();
        try {
            SDL_GL_MakeCurrent(window,context); GL.createCapabilities();
            int programs=0;
            for(var pipeline:pipelines()) {
                int program=glCreateProgram(); List<Integer> stages=new ArrayList<>();
                try {
                    for(var entry:pipeline.getShaders().entrySet()) {
                        String suffix=entry.getKey()==ShaderType.VERTEX?".vsh":".fsh";
                        String resource="/assets/"+entry.getValue().getNamespace()+"/shaders/"+entry.getValue().getPath()+suffix;
                        int shader=compile(entry.getKey(),resource,pipeline.getShaderDefines().asSourceDirectives());
                        stages.add(shader); glAttachShader(program,shader);
                    }
                    glLinkProgram(program);
                    if(glGetProgrami(program,GL_LINK_STATUS)==0) throw new AssertionError(pipeline.getLocation()+"\n"+glGetProgramInfoLog(program));
                    if (handsOnly || guiOnly || weatherOnly || saturationOnly) retainedPrograms.put(pipeline.getLocation().getPath(), program);
                    programs++;
                } finally {
                    if (!retainedPrograms.containsValue(program)) glDeleteProgram(program);
                    stages.forEach(s->glDeleteShader(s));
                }
            }
            if (handsOnly) HandFlameImageCheck.verify(retainedPrograms);
            if (guiOnly) BackdropImageCheck.verify(retainedPrograms);
            if (textOnly) GuiTextImageCheck.verify();
            if (weatherOnly) PumpkinImageCheck.verify(retainedPrograms);
            if (saturationOnly) SaturationImageCheck.verify(retainedPrograms.get("pipeline/saturation"));
            Files.write(Path.of(".codex/ports/"+(pickerOnly?"color-picker":hotbarOnly?"hotbar":"hustle263")+"-shader-resources.txt"),RESOURCES,StandardCharsets.UTF_8);
            System.out.println("GPU-compiled and linked "+programs+" visual pipelines; "+RESOURCES.size()+" shader resources verified.");
        } finally {
            retainedPrograms.values().forEach(program -> glDeleteProgram(program));
            SDL_GL_DestroyContext(context); SDL_DestroyWindow(window); SDL_Quit();
        }
    }

    /** Run the game's actual shaderc/SPIR-V path, including its include resolver and reflection. */
    private static void verifyNativeCompiler() throws Exception {
        var deviceField=com.mojang.blaze3d.systems.RenderSystem.class.getDeclaredField("DEVICE");
        deviceField.setAccessible(true);
        Object previous=deviceField.get(null);
        var info=new com.mojang.renderpearl.api.device.DeviceInfo("audit","audit","audit",false,"audit",1f,null,null,Set.of(),
                new com.mojang.renderpearl.api.device.HintsAndWorkarounds(false,false,false,false),null);
        Object device=java.lang.reflect.Proxy.newProxyInstance(VisualShaderCheck.class.getClassLoader(),
                new Class<?>[]{com.mojang.renderpearl.api.device.GpuDevice.class},(proxy,method,arguments)->{
                    if(method.getName().equals("getDeviceInfo")) return info;
                    throw new UnsupportedOperationException(method.getName());
                });
        deviceField.set(null,device);
        var source=new com.mojang.renderpearl.api.pipeline.ShaderSource() {
            final Map<net.minecraft.resources.Identifier,CachedIncludeSource> includes=new HashMap<>();
            private String read(String resource) {
                try(var input=VisualShaderCheck.class.getResourceAsStream(resource)) {
                    if(input==null) throw new IllegalStateException(resource);
                    RESOURCES.add(resource);
                    return new String(input.readAllBytes(),StandardCharsets.UTF_8);
                } catch(java.io.IOException e) {throw new java.io.UncheckedIOException(e);}
            }
            public String getShader(net.minecraft.resources.Identifier id,ShaderType type) {
                return read("/assets/"+id.getNamespace()+"/shaders/"+id.getPath()+(type==ShaderType.VERTEX?".vsh":".fsh"));
            }
            public CachedIncludeSource getInclude(net.minecraft.resources.Identifier id) {
                return includes.computeIfAbsent(id,key->CachedIncludeSource.create(key,read("/assets/"+key.getNamespace()+"/shaders/include/"+key.getPath())));
            }
            public void close() {includes.values().forEach(CachedIncludeSource::close);}
        };
        int compiled=0;
        try(source) {
            for(boolean zeroToOne:new boolean[]{false,true}) try(var compiler=new com.mojang.renderpearl.frontend.shaders.GlslCompiler(zeroToOne,false)) {
                for(var pipeline:pipelines()) {
                    var uniformNames=new HashSet<String>();
                    for(var group:pipeline.getBindGroupLayouts()) for(var uniform:group.uniforms()) uniformNames.add(uniform.name());
                    Map<Integer,String> outputs=new HashMap<>(),inputs=new HashMap<>();
                    for(var entry:pipeline.getShaders().entrySet()) try(var module=compiler.compileToSpv(entry.getValue().toString(),source.getShader(entry.getValue(),entry.getKey()),entry.getKey(),pipeline.getShaderDefines(),source)) {
                        var reflection=module.reflect();
                        for(var descriptor:reflection.descriptors()) if(!uniformNames.contains(descriptor.name()))
                            throw new AssertionError(pipeline.getLocation()+": unbound shader uniform "+descriptor.name());
                        if(entry.getKey()==ShaderType.VERTEX) {
                            for(var input:reflection.inputs()) if(!input.name().startsWith("gl_") && pipeline.getVertexFormatBindings().stream().noneMatch(v->v.contains(input.name())))
                                throw new AssertionError(pipeline.getLocation()+": missing vertex attribute "+input.name());
                            for(var output:reflection.outputs()) if(!output.name().startsWith("gl_")) outputs.put(output.location(),output.name());
                        } else for(var input:reflection.inputs()) if(!input.name().startsWith("gl_")) inputs.put(input.location(),input.name());
                        compiled++;
                    }
                    for(var input:inputs.entrySet()) if(!Objects.equals(outputs.get(input.getKey()),input.getValue()))
                        throw new AssertionError(pipeline.getLocation()+": stage interface mismatch "+input);
                }
            }
            System.out.println("Native RenderPearl compiler verified "+compiled+" shader stages, vertex inputs, uniforms and stage interfaces across both depth conventions.");
        } finally {deviceField.set(null,previous);}
    }
}
