package morfeov.coliseum.ui;

import org.objectweb.asm.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public final class ChamsRenderAudit {
    private static ClassReader read(String name)throws Exception{try(var stream=ChamsRenderAudit.class.getResourceAsStream("/"+name+".class")){assertNotNull(stream,name);return new ClassReader(stream);}}
    private static void invocation(String type,String method,String owner,String mixin)throws Exception{
        var targets=new ArrayList<String>();
        read(mixin).accept(new ClassVisitor(Opcodes.ASM9){
            @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){return new MethodVisitor(Opcodes.ASM9){
                @Override public AnnotationVisitor visitAnnotation(String descriptor,boolean visible){return new AnnotationVisitor(Opcodes.ASM9){
                    @Override public AnnotationVisitor visitAnnotation(String name,String descriptor){return this;}
                    @Override public AnnotationVisitor visitArray(String name){return this;}
                    @Override public void visit(String name,Object value){if("target".equals(name)&&value instanceof String target&&target.contains(";submitModel("))targets.add(target);}
                };}
            };}
        },0);
        assertEquals(1,targets.size(),mixin+" submitModel hook");
        boolean[] found={false};
        read(type).accept(new ClassVisitor(Opcodes.ASM9){@Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){if(!name.equals(method))return null;return new MethodVisitor(Opcodes.ASM9){@Override public void visitMethodInsn(int opcode,String target,String called,String descriptor,boolean itf){if(target.equals(owner)&&called.equals("submitModel")&&targets.contains("L"+target+";"+called+descriptor))found[0]=true;}};}},0);
        assertTrue(found[0],type+"."+method+" model hook");
    }
    public static void main(String[] args)throws Exception{
        var constructors=new HashSet<String>();
        read("morfeov/coliseum/render/chams/ChamsModels$Draw").accept(new ClassVisitor(Opcodes.ASM9){
            @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){if(name.equals("<init>"))constructors.add(descriptor);return null;}
        },0);
        int[] captures={0};
        read("morfeov/coliseum/render/chams/ChamsModels").accept(new ClassVisitor(Opcodes.ASM9){
            @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){return new MethodVisitor(Opcodes.ASM9){
                @Override public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean itf){
                    if(owner.equals("morfeov/coliseum/render/chams/ChamsModels$Draw")&&method.equals("<init>")){assertTrue(constructors.contains(descriptor),"Chams capture constructor must match loaded Draw class: "+descriptor);captures[0]++;}
                }
            };}
        },0);
        assertTrue(captures[0]>0,"Chams capture constructor checked");
        invocation("net/minecraft/client/renderer/entity/LivingEntityRenderer","submit","net/minecraft/client/renderer/SubmitNodeCollector","morfeov/coliseum/mixin/ChamsLivingMixin");
        invocation("net/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer","renderLayers","net/minecraft/client/renderer/OrderedSubmitNodeCollector","morfeov/coliseum/mixin/ChamsEquipmentMixin");
        boolean[] submit={false};read("net/minecraft/client/renderer/LevelRenderer").accept(new ClassVisitor(Opcodes.ASM9){@Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){if(name.equals("submitEntities"))submit[0]=true;return null;}},0);assertTrue(submit[0]);
        var module=new morfeov.coliseum.module.impl.render.Chams();
        assertTrue(module.mode.is("Обводка"));
        assertTrue(module.settings().stream().noneMatch(s->s.name().equals("Режим")));
        assertFalse(module.enabled());assertEquals(module.settings().size(),module.settings().stream().map(morfeov.coliseum.setting.Setting::name).distinct().count());
        assertTrue(module.outlineWidth.visible());
        assertTrue(module.settings().stream().anyMatch(morfeov.coliseum.setting.Setting::visible));
        System.out.println("Minecraft 26.3 body, armor, world submission hooks and Javelin Chams settings verified.");
    }
}
