package morfeov.coliseum.port.event;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import morfeov.coliseum.mixin.ClientInputAccessor;
public final class EventInput {
    private final ClientInput input;
    private float forward, strafe;
    private boolean jump, sprint;
    public EventInput(ClientInput input) { this.input=input; var k=input.keyPresses; forward=(k.forward()?1:0)-(k.backward()?1:0); strafe=(k.left()?1:0)-(k.right()?1:0); jump=k.jump(); sprint=k.sprint(); }
    public float getForward() { return forward; } public float getStrafe() { return strafe; }
    public void setForward(float v) { forward=v; } public void setStrafe(float v) { strafe=v; }
    public boolean isJump() { return jump; } public void setJump(boolean v) { jump=v; }
    public void setSprint(boolean v) { sprint=v; }
    public void apply() { var k=input.keyPresses; input.keyPresses=new Input(forward>0,forward<0,strafe>0,strafe<0,jump,k.shift(),sprint); ((ClientInputAccessor)input).coliseum$setMoveVector(new Vec2(strafe,forward).normalized()); }
}
