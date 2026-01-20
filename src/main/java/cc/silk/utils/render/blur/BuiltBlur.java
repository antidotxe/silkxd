package cc.silk.utils.render.blur;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

public record BuiltBlur(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        float smoothness,
        float blurRadius
) {
    private static final ShaderProgramKey BLUR_SHADER_KEY = new ShaderProgramKey(
            Identifier.of("silk", "core/blur"),
            VertexFormats.POSITION_COLOR,
            Defines.EMPTY
    );
    
    private static final Supplier<SimpleFramebuffer> TEMP_FBO_SUPPLIER = Suppliers
            .memoize(() -> new SimpleFramebuffer(1920, 1024, false));
    
    private static final Framebuffer MAIN_FBO = MinecraftClient.getInstance().getFramebuffer();

    public void render(Matrix4f matrix, float x, float y, float z) {
        SimpleFramebuffer fbo = TEMP_FBO_SUPPLIER.get();
        if (fbo.textureWidth != MAIN_FBO.textureWidth || fbo.textureHeight != MAIN_FBO.textureHeight) {
            fbo.resize(MAIN_FBO.textureWidth, MAIN_FBO.textureHeight);
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        fbo.beginWrite(false);
        MAIN_FBO.draw(fbo.textureWidth, fbo.textureHeight);
        MAIN_FBO.beginWrite(false);

        RenderSystem.setShaderTexture(0, fbo.getColorAttachment());

        float width = this.size.width();
        float height = this.size.height();
        
        ShaderProgram shader = RenderSystem.setShader(BLUR_SHADER_KEY);
        shader.getUniform("Size").set(width, height);
        shader.getUniform("Radius").set(
                this.radius.radius1(),
                this.radius.radius2(),
                this.radius.radius3(),
                this.radius.radius4()
        );
        shader.getUniform("Smoothness").set(this.smoothness);
        shader.getUniform("BlurRadius").set(this.blurRadius);

        BufferBuilder builder = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix, x, y, z).color(this.color.color1());
        builder.vertex(matrix, x, y + height, z).color(this.color.color2());
        builder.vertex(matrix, x + width, y + height, z).color(this.color.color3());
        builder.vertex(matrix, x + width, y, z).color(this.color.color4());

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableCull();
    }
}

// i dont think there was a reason to delete ts but wtv 