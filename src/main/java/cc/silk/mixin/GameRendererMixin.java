package cc.silk.mixin;

import cc.silk.SilkClient;
import cc.silk.event.impl.render.Render3DEvent;
import cc.silk.module.modules.render.AspectRatio;
import cc.silk.utils.render.W2SUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @ModifyArgs(method = "getBasicProjectionMatrix", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;"))
    private void modifyAspectRatio(Args args) {
        float customRatio = AspectRatio.getAspectRatio();
        if (customRatio > 0) {
            args.set(1, customRatio);
        }
    }

    @Inject(at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/GameRenderer;renderHand:Z", opcode = Opcodes.GETFIELD, ordinal = 0), method = "renderWorld")
    private void renderHand(RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        MatrixStack matrixStack = new MatrixStack();
        Camera camera = mc.gameRenderer.getCamera();

        RenderSystem.getModelViewStack().pushMatrix().mul(matrixStack.peek().getPositionMatrix());
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0f));
        // applyModelViewMatrix removed in newer versions; the stack state is used directly

        W2SUtil.matrixProject.set(RenderSystem.getProjectionMatrix());
        W2SUtil.matrixModel.set(RenderSystem.getModelViewMatrix());
        W2SUtil.matrixWorldSpace.set(matrixStack.peek().getPositionMatrix());

        RenderSystem.getModelViewStack().popMatrix();
    }

    @Inject(method = "renderWorld", at = @At("TAIL"))
    private void onRenderWorldTail(RenderTickCounter tickCounter, CallbackInfo ci) {
        SilkClient.INSTANCE.getSilkEventBus().post(new Render3DEvent(new MatrixStack()));
    }
}
