package morfeov.coliseum.misc;

import java.nio.file.Files;
import java.util.*;
import morfeov.coliseum.config.ClientConfig;
import morfeov.coliseum.module.ModuleRegistry;
import morfeov.coliseum.module.impl.misc.*;
import morfeov.coliseum.module.impl.misc.auction.AuctionHelperModule;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;

public final class ColiseumModulesCheck {
    private record Hook(String method,String target){}
    private static ClassReader read(String name)throws Exception{try(var input=ColiseumModulesCheck.class.getResourceAsStream("/"+name+".class")){assertNotNull(input,name);return new ClassReader(input);}}
    private static void audit(String mixin,String owner)throws Exception{
        var hooks=new ArrayList<Hook>();
        read("morfeov/coliseum/mixin/"+mixin).accept(new ClassVisitor(Opcodes.ASM9){
            @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){return new MethodVisitor(Opcodes.ASM9){
                @Override public AnnotationVisitor visitAnnotation(String type,boolean visible){
                    var methods=new ArrayList<String>();var targets=new ArrayList<String>();
                    return new AnnotationVisitor(Opcodes.ASM9){
                        @Override public AnnotationVisitor visitArray(String field){return new AnnotationVisitor(Opcodes.ASM9){@Override public void visit(String name,Object value){if(field.equals("method")&&value instanceof String text)methods.add(text);}};}
                        @Override public void visit(String field,Object value){if(field.equals("method")&&value instanceof String text)methods.add(text);}
                        @Override public AnnotationVisitor visitAnnotation(String field,String type){return new AnnotationVisitor(Opcodes.ASM9){@Override public void visit(String name,Object value){if(name.equals("target")&&value instanceof String text)targets.add(text);}};}
                        @Override public void visitEnd(){for(String method:methods){if(targets.isEmpty())hooks.add(new Hook(method,""));for(String target:targets)hooks.add(new Hook(method,target));}}
                    };
                }
            };}
        },0);
        assertFalse(hooks.isEmpty(),mixin);
        for(var hook:hooks){boolean[] matched={false};read(owner).accept(new ClassVisitor(Opcodes.ASM9){
            @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){if(!hook.method.equals(name)&&!hook.method.equals(name+descriptor))return null;if(hook.target.isEmpty())matched[0]=true;return new MethodVisitor(Opcodes.ASM9){@Override public void visitMethodInsn(int opcode,String owner,String called,String descriptor,boolean itf){if(hook.target.equals("L"+owner+";"+called+descriptor))matched[0]=true;}};}
        },0);assertTrue(matched[0],mixin+" "+hook);}
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var registries=net.minecraft.data.registries.VanillaRegistries.createWorldLookup();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
        assertEquals(1100,AutoSell.sellingPrice(100,10,10));assertEquals(100,AutoSell.sellingPrice(100,10,-90));assertEquals(Integer.MAX_VALUE,AutoSell.sellingPrice(Integer.MAX_VALUE,64,300));
        ItemStack lot=new ItemStack(Items.DIAMOND,10);lot.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("Цена: 12 345 $"))));assertEquals(12345,AuctionHelperModule.getInstance().computeInt(lot));
        assertEquals(-1,AuctionHelperModule.getInstance().computeInt(new ItemStack(Items.PAPER)));
        lot.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("12 345 монет"))));assertEquals(12345,AuctionHelperModule.getInstance().computeInt(lot));
        assertTrue(AuctionHelperModule.getInstance().matchesCriteria3("Категория: Алмазы"));assertTrue(AuctionHelperModule.getInstance().matchesCriteria3("1A0ꀄ"));
        var registry=new ModuleRegistry();var sell=registry.register(new AutoSell());
        sell.searchCommand.set("market search");sell.sellCommand.set("market sell");
        var path=Files.createTempFile("coliseum-modules-",".json");
        try{ClientConfig.saveProfile(path,registry);sell.searchCommand.set("wrong");assertTrue(new ClientConfig(path,registry).load());assertEquals("market search",sell.searchCommand.get());assertEquals("market sell",sell.sellCommand.get());}finally{Files.deleteIfExists(path);}
        assertTrue(morfeov.coliseum.port.PortModule.class.isAssignableFrom(morfeov.coliseum.module.impl.combat.AutoWebTrap.class));
        System.out.println("Coliseum modules verified: auction price and overflow, settings persistence and AutoWebTrap.");
    }
}
