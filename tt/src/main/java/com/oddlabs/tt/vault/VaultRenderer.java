package com.oddlabs.tt.vault;

import com.oddlabs.tt.camera.CameraState;
import com.oddlabs.tt.event.LocalEventQueue;
import com.oddlabs.tt.gui.GUIRoot;
import com.oddlabs.tt.gui.ToolTip;
import com.oddlabs.tt.render.MatrixStack;
import com.oddlabs.tt.render.Sprite;
import com.oddlabs.tt.render.SpriteList;
import com.oddlabs.tt.render.UIRenderer;
import com.oddlabs.tt.render.shader.SpriteShader;
import com.oddlabs.tt.render.state.BlendMode;
import com.oddlabs.tt.render.state.CullMode;
import com.oddlabs.tt.render.state.DepthMode;
import com.oddlabs.tt.render.state.GLRenderContext;
import com.oddlabs.tt.render.state.GlobalUniforms;
import com.oddlabs.tt.render.state.RenderContext;
import com.oddlabs.tt.viewer.AmbientAudio;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * DRAWS THE CURRENT VAULT SPRITE WITH THE SAME SPRITE SHADER PATH AS THE GAME.
 */
public final class VaultRenderer implements UIRenderer {
    private static final Logger logger = Logger.getLogger(VaultRenderer.class.getName());

    private final @NonNull VaultController controller;
    private final @NonNull SpriteShader spriteShader = new SpriteShader();
    private final @NonNull GlobalUniforms globalUniforms = new GlobalUniforms();
    private final @NonNull MatrixStack modelViewStack = new MatrixStack();
    private final @NonNull Vector3f sunDirection = new Vector3f(-0.9f, 0.7f, 0.7f).normalize();
    private final @NonNull Vector3f skyAmbient = new Vector3f(0.55f, 0.55f, 0.6f);
    private final @NonNull Vector3f groundAmbient = new Vector3f(0.35f, 0.32f, 0.3f);

    public VaultRenderer(@NonNull VaultController controller) {
        this.controller = controller;
    }

    @Override
    public void startFrame(@NonNull RenderContext context) {
        // AFTER SPRITE RELOAD (Next), DELETED TEXTURE HANDLES CAN BE REUSED WHILE THE
        // CONTEXT CACHE STILL THINKS THEY'RE BOUND — FORCE REBIND NEXT PASS.
        if (context instanceof GLRenderContext gl)
            gl.reset();

        // FIXED STUDIO BACKDROP — DO NOT USE PER-SPRITE clear_color (JUMPS EVERY Next).
        GL11.glClearColor(0.18f, 0.20f, 0.24f, 1f);
        context.clear(true, true);
    }

    @Override
    public void render(@NonNull RenderContext context, AmbientAudio ambient, @NonNull CameraState camera_state,
            GUIRoot gui_root) {
        globalUniforms.update(camera_state, sunDirection, skyAmbient, groundAmbient,
                (float) LocalEventQueue.getQueue().getTime());
        context.updateGlobalState(globalUniforms.getBuffer());

        SpriteList list = controller.getSpriteList();
        if (list == null)
            return;

        int lod = Math.min(controller.getLodIndex(), Math.max(0, list.getNumSprites() - 1));
        Sprite sprite = list.getSprite(lod);
        int anim = Math.min(controller.getAnimIndex(), Math.max(0, list.getAnimationNames().length - 1));
        int tex = Math.min(controller.getTexIndex(), Math.max(0, sprite.getNumTextures() - 1));
        float scale = controller.getCurrentEntry() != null ? controller.getCurrentEntry().scale() : 1f;

        modelViewStack.current().set(camera_state.getModelView());
        modelViewStack.push();
        modelViewStack.rotate(controller.getModelYaw() * (180f / (float) Math.PI), 0f, 0f, 1f);
        if (scale != 1f)
            modelViewStack.scale(scale, scale, scale);

        try (var _ = spriteShader.use()) {
            spriteShader.setUniformMatrix4(SpriteShader.Uniforms.MODEL_VIEW_MATRIX, false, modelViewStack.current());
            spriteShader.setUniform(SpriteShader.Uniforms.COLOR, 1f, 1f, 1f, 1f);
            spriteShader.setUniform(SpriteShader.Uniforms.DECAL_COLOR, 0.2f, 0.55f, 0.2f, 1f);
            spriteShader.setUniform(SpriteShader.Uniforms.DESATURATE, 0f);
            sprite.setupShaderUniforms(context, spriteShader, tex, false);

            try (var _ = context.withCullMode(CullMode.BACK);
                    var _ = context.withDepthMode(DepthMode.READ_WRITE);
                    var _ = context.withBlendMode(BlendMode.ALPHA)) {
                sprite.renderShader(spriteShader, anim, controller.getAnimTicks(), list);
            }
        } catch (Exception e) {
            logger.warning("Vault render failed: " + e.getMessage());
        } finally {
            modelViewStack.pop();
        }
    }

    @Override
    public void endFrame(@NonNull RenderContext context, @NonNull Consumer<@NonNull RenderContext> guiRenderCallback) {
        // SPRITE PASS LEAVES TEXTURES / TBO / BIND CACHE DIRTY. GUI THEN SKIPS REBINDS AND
        // DRAWS BLACK / STREAKED GLYPHS (SAME CLASS OF BUG AS Renderer texture-bleeding resets).
        prepareGuiState(context);
        guiRenderCallback.accept(context);
    }

    private static void prepareGuiState(@NonNull RenderContext context) {
        if (context instanceof GLRenderContext gl)
            gl.reset();

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        for (int i = 0; i < 4; i++) {
            context.setActiveTexture(i);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
            context.setTexture(i, 0);
        }
        context.setActiveTexture(0);
        context.setBlendMode(BlendMode.ALPHA);
        context.setDepthMode(DepthMode.NONE);
        context.setCullMode(CullMode.NONE);
        context.setColorMask(true, true, true, true);
    }

    @Override
    public void pickHover(boolean can_hover_behind, CameraState camera, int x, int y) {
    }

    @Override
    public @Nullable ToolTip getToolTip() {
        return null;
    }

    @Override
    public boolean isCheater() {
        return false;
    }
}
