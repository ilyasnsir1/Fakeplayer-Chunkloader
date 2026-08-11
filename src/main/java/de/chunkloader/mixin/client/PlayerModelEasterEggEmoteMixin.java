package de.chunkloader.mixin.client;

import de.chunkloader.client.EmoteReflectionHelper;
import de.chunkloader.client.FakePlayerEasterEggEmoteCache;
import de.chunkloader.client.FakePlayerEasterEggSkinCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(PlayerModel.class)
public abstract class PlayerModelEasterEggEmoteMixin {
    private static final float BEGIN_TICK = 0.0f;
    private static final float WAVE_STOP_TICK = 61.0f;
    private static final float SALUTE_STOP_TICK = 101.0f;

    private static final float[] WAVE_HEAD_PITCH_TICKS = new float[] { 7f, 18f, 32f, 45f, 60f };
    private static final float[] WAVE_HEAD_PITCH_VALUES = new float[] { 0f, -0.16508967f, 0.18413922f, 0.07767979f, 0f };

    private static final float[] WAVE_HEAD_YAW_TICKS = new float[] { 7f, 18f, 32f, 45f, 60f };
    private static final float[] WAVE_HEAD_YAW_VALUES = new float[] { 0f, 0.013277619f, 0.31855145f, -0.26205245f, 0f };

    private static final float[] WAVE_HEAD_ROLL_TICKS = new float[] { 7f, 18f, 32f, 45f, 60f };
    private static final float[] WAVE_HEAD_ROLL_VALUES = new float[] { 0f, 0.079524316f, 0.15674756f, 0.008761592f, 0f };

    private static final float[] WAVE_RIGHT_ARM_POS_TICKS = new float[] { 8f, 60f };
    private static final float[] WAVE_RIGHT_ARM_X_VALUES = new float[] { -5.0f, -5.0f };
    private static final float[] WAVE_RIGHT_ARM_Y_VALUES = new float[] { 2.0f, 2.0f };
    private static final float[] WAVE_RIGHT_ARM_Z_VALUES = new float[] { 0.0f, 0.0f };

    private static final float[] WAVE_RIGHT_ARM_PITCH_TICKS = new float[] { 0f, 12f, 53f, 60f };
    private static final float[] WAVE_RIGHT_ARM_PITCH_VALUES = new float[] { 0f, -2.7326946f, -2.7326946f, 0f };

    private static final float[] WAVE_RIGHT_ARM_YAW_TICKS = new float[] { 8f, 15f, 21f, 27f, 33f, 40f, 46f, 53f, 60f };
    private static final float[] WAVE_RIGHT_ARM_YAW_VALUES = new float[] {
            0f, -1.29921744E-8f, -1.2992174E-8f, -1.28216415E-8f, -1.28216415E-8f,
            -2.7056025E-8f, -1.0867868E-8f, -1.08678675E-8f, 0f
    };

    private static final float[] WAVE_RIGHT_ARM_ROLL_TICKS = new float[] { 8f, 15f, 21f, 27f, 33f, 40f, 46f, 53f, 60f };
    private static final float[] WAVE_RIGHT_ARM_ROLL_VALUES = new float[] {
            0f, -0.45108813f, 0.046508536f, -0.43382236f, 0.119678326f,
            -0.36637557f, 0.18602145f, -0.42936483f, 0f
    };

    private static final float[] SALUTE_HEAD_POS_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_HEAD_X_VALUES = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };
    private static final float[] SALUTE_HEAD_Y_VALUES = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };
    private static final float[] SALUTE_HEAD_Z_VALUES = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };

    private static final float[] SALUTE_HEAD_PITCH_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_HEAD_PITCH_VALUES = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };

    private static final float[] SALUTE_HEAD_YAW_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_HEAD_YAW_VALUES = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };

    private static final float[] SALUTE_HEAD_ROLL_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_HEAD_ROLL_VALUES = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };

    private static final float[] SALUTE_RIGHT_ARM_POS_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_RIGHT_ARM_X_VALUES = new float[] { -5.0f, -5.0f, -5.0f, -5.0f };
    private static final float[] SALUTE_RIGHT_ARM_Y_VALUES = new float[] { 2.0f, 2.0f, 2.0f, 2.0f };
    private static final float[] SALUTE_RIGHT_ARM_Z_VALUES = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };

    private static final float[] SALUTE_RIGHT_ARM_PITCH_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_RIGHT_ARM_PITCH_VALUES = new float[] { 0.0f, 0.48484555f, 0.48484555f, 0.0f };

    private static final float[] SALUTE_RIGHT_ARM_YAW_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_RIGHT_ARM_YAW_VALUES = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };

    private static final float[] SALUTE_RIGHT_ARM_ROLL_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_RIGHT_ARM_ROLL_VALUES = new float[] { 0.0f, -0.57699585f, -0.57699585f, 0.0f };

    private static final float[] SALUTE_LEFT_ARM_POS_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_LEFT_ARM_X_VALUES = new float[] { 5.0f, 5.0f, 5.0f, 5.0f };
    private static final float[] SALUTE_LEFT_ARM_Y_VALUES = new float[] { 2.0f, 2.0f, 2.0f, 2.0f };
    private static final float[] SALUTE_LEFT_ARM_Z_VALUES = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };

    private static final float[] SALUTE_LEFT_ARM_PITCH_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_LEFT_ARM_PITCH_VALUES = new float[] { 0.0f, -2.6045444f, -2.6045444f, 0.0f };

    private static final float[] SALUTE_LEFT_ARM_YAW_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_LEFT_ARM_YAW_VALUES = new float[] { 0.0f, 3.3995238E-9f, 3.3995238E-9f, 0.0f };

    private static final float[] SALUTE_LEFT_ARM_ROLL_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_LEFT_ARM_ROLL_VALUES = new float[] { 0.0f, -0.4006394f, -0.4006394f, 0.0f };

    private static final float[] SALUTE_RIGHT_LEG_POS_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_RIGHT_LEG_X_VALUES = new float[] { -1.9f, -1.9f, -1.9f, -1.9f };
    private static final float[] SALUTE_RIGHT_LEG_Y_VALUES = new float[] { 12.0f, 12.0f, 12.0f, 12.0f };
    private static final float[] SALUTE_RIGHT_LEG_Z_VALUES = new float[] { 0.1f, 0.1f, 0.1f, 0.1f };

    private static final float[] SALUTE_RIGHT_LEG_PITCH_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_RIGHT_LEG_PITCH_VALUES = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };

    private static final float[] SALUTE_RIGHT_LEG_YAW_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_RIGHT_LEG_YAW_VALUES = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };

    private static final float[] SALUTE_RIGHT_LEG_ROLL_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_RIGHT_LEG_ROLL_VALUES = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };

    private static final float[] SALUTE_LEFT_LEG_POS_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_LEFT_LEG_X_VALUES = new float[] { 1.9f, 1.9f, 1.9f, 1.9f };
    private static final float[] SALUTE_LEFT_LEG_Y_VALUES = new float[] { 12.0f, 12.0f, 12.0f, 12.0f };
    private static final float[] SALUTE_LEFT_LEG_Z_VALUES = new float[] { -0.1f, -0.1f, -0.1f, -0.1f };

    private static final float[] SALUTE_LEFT_LEG_PITCH_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_LEFT_LEG_PITCH_VALUES = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };

    private static final float[] SALUTE_LEFT_LEG_YAW_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_LEFT_LEG_YAW_VALUES = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };

    private static final float[] SALUTE_LEFT_LEG_ROLL_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_LEFT_LEG_ROLL_VALUES = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };

    private static final float[] SALUTE_BODY_POS_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_BODY_X_VALUES = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };
    private static final float[] SALUTE_BODY_Y_VALUES = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };
    private static final float[] SALUTE_BODY_Z_VALUES = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };

    private static final float[] SALUTE_BODY_PITCH_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_BODY_PITCH_VALUES = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };

    private static final float[] SALUTE_BODY_YAW_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_BODY_YAW_VALUES = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };

    private static final float[] SALUTE_BODY_ROLL_TICKS = new float[] { 0f, 20f, 80f, 100f };
    private static final float[] SALUTE_BODY_ROLL_VALUES = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void chunkloader$applyEasterEggEmote(AvatarRenderState renderState, CallbackInfo ci) {
        if (!((Object) this instanceof PlayerModel model)) {
            return;
        }
        Player player = EmoteReflectionHelper.resolvePlayer(renderState);
        if (player == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return;
        }
        if (client.player != null) {
            double maxDistance = 24.0;
            if (client.player.distanceToSqr(player) > (maxDistance * maxDistance)) {
                return;
            }
        }
        float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Float tick = FakePlayerEasterEggEmoteCache.getEmoteTick(
                player.getUUID(),
                client.level.getGameTime(),
                tickDelta);
        if (tick == null) {
            return;
        }
        if (tick < BEGIN_TICK) {
            return;
        }
        boolean isEasterEgg = FakePlayerEasterEggSkinCache.hasSkin(player.getUUID());
        float stopTick = isEasterEgg ? SALUTE_STOP_TICK : WAVE_STOP_TICK;
        if (tick > stopTick) {
            FakePlayerEasterEggEmoteCache.stopEmote(player.getUUID());
            return;
        }

        if (isEasterEgg) {
            applySaluteHead(model.head, tick);
            applySaluteBody(model.body, tick);
            applySaluteRightArm(model.rightArm, tick);
            applySaluteLeftArm(model.leftArm, tick);
            applySaluteRightLeg(model.rightLeg, tick);
            applySaluteLeftLeg(model.leftLeg, tick);
        } else {
            applyWaveHead(model.head, tick);
            applyWaveRightArm(model.rightArm, tick);
        }
    }

    private static void applyWaveHead(ModelPart head, float tick) {
        if (head == null) return;
        Float pitch = sample(tick, WAVE_HEAD_PITCH_TICKS, WAVE_HEAD_PITCH_VALUES);
        if (pitch != null) head.xRot = pitch;
        Float yaw = sample(tick, WAVE_HEAD_YAW_TICKS, WAVE_HEAD_YAW_VALUES);
        if (yaw != null) head.yRot = yaw;
        Float roll = sample(tick, WAVE_HEAD_ROLL_TICKS, WAVE_HEAD_ROLL_VALUES);
        if (roll != null) head.zRot = roll;
    }

    private static void applyWaveRightArm(ModelPart rightArm, float tick) {
        if (rightArm == null) return;
        applyPos(rightArm, tick, WAVE_RIGHT_ARM_POS_TICKS, WAVE_RIGHT_ARM_X_VALUES, WAVE_RIGHT_ARM_Y_VALUES, WAVE_RIGHT_ARM_Z_VALUES);
        Float pitch = sample(tick, WAVE_RIGHT_ARM_PITCH_TICKS, WAVE_RIGHT_ARM_PITCH_VALUES);
        if (pitch != null) rightArm.xRot = pitch;
        Float yaw = sample(tick, WAVE_RIGHT_ARM_YAW_TICKS, WAVE_RIGHT_ARM_YAW_VALUES);
        if (yaw != null) rightArm.yRot = yaw;
        Float roll = sample(tick, WAVE_RIGHT_ARM_ROLL_TICKS, WAVE_RIGHT_ARM_ROLL_VALUES);
        if (roll != null) rightArm.zRot = roll;
    }

    private static void applySaluteHead(ModelPart head, float tick) {
        if (head == null) return;
        applyPos(head, tick, SALUTE_HEAD_POS_TICKS, SALUTE_HEAD_X_VALUES, SALUTE_HEAD_Y_VALUES, SALUTE_HEAD_Z_VALUES);
        Float pitch = sample(tick, SALUTE_HEAD_PITCH_TICKS, SALUTE_HEAD_PITCH_VALUES);
        if (pitch != null) head.xRot = pitch;
        Float yaw = sample(tick, SALUTE_HEAD_YAW_TICKS, SALUTE_HEAD_YAW_VALUES);
        if (yaw != null) head.yRot = yaw;
        Float roll = sample(tick, SALUTE_HEAD_ROLL_TICKS, SALUTE_HEAD_ROLL_VALUES);
        if (roll != null) head.zRot = roll;
    }

    private static void applySaluteRightArm(ModelPart rightArm, float tick) {
        if (rightArm == null) return;
        applyPos(rightArm, tick, SALUTE_RIGHT_ARM_POS_TICKS, SALUTE_RIGHT_ARM_X_VALUES, SALUTE_RIGHT_ARM_Y_VALUES, SALUTE_RIGHT_ARM_Z_VALUES);
        Float pitch = sample(tick, SALUTE_RIGHT_ARM_PITCH_TICKS, SALUTE_RIGHT_ARM_PITCH_VALUES);
        if (pitch != null) rightArm.xRot = pitch;
        Float yaw = sample(tick, SALUTE_RIGHT_ARM_YAW_TICKS, SALUTE_RIGHT_ARM_YAW_VALUES);
        if (yaw != null) rightArm.yRot = yaw;
        Float roll = sample(tick, SALUTE_RIGHT_ARM_ROLL_TICKS, SALUTE_RIGHT_ARM_ROLL_VALUES);
        if (roll != null) rightArm.zRot = roll;
    }

    private static void applySaluteLeftArm(ModelPart leftArm, float tick) {
        if (leftArm == null) return;
        applyPos(leftArm, tick, SALUTE_LEFT_ARM_POS_TICKS, SALUTE_LEFT_ARM_X_VALUES, SALUTE_LEFT_ARM_Y_VALUES, SALUTE_LEFT_ARM_Z_VALUES);
        Float pitch = sample(tick, SALUTE_LEFT_ARM_PITCH_TICKS, SALUTE_LEFT_ARM_PITCH_VALUES);
        if (pitch != null) leftArm.xRot = pitch;
        Float yaw = sample(tick, SALUTE_LEFT_ARM_YAW_TICKS, SALUTE_LEFT_ARM_YAW_VALUES);
        if (yaw != null) leftArm.yRot = yaw;
        Float roll = sample(tick, SALUTE_LEFT_ARM_ROLL_TICKS, SALUTE_LEFT_ARM_ROLL_VALUES);
        if (roll != null) leftArm.zRot = roll;
    }

    private static void applySaluteRightLeg(ModelPart rightLeg, float tick) {
        if (rightLeg == null) return;
        applyPos(rightLeg, tick, SALUTE_RIGHT_LEG_POS_TICKS, SALUTE_RIGHT_LEG_X_VALUES, SALUTE_RIGHT_LEG_Y_VALUES, SALUTE_RIGHT_LEG_Z_VALUES);
        Float pitch = sample(tick, SALUTE_RIGHT_LEG_PITCH_TICKS, SALUTE_RIGHT_LEG_PITCH_VALUES);
        if (pitch != null) rightLeg.xRot = pitch;
        Float yaw = sample(tick, SALUTE_RIGHT_LEG_YAW_TICKS, SALUTE_RIGHT_LEG_YAW_VALUES);
        if (yaw != null) rightLeg.yRot = yaw;
        Float roll = sample(tick, SALUTE_RIGHT_LEG_ROLL_TICKS, SALUTE_RIGHT_LEG_ROLL_VALUES);
        if (roll != null) rightLeg.zRot = roll;
    }

    private static void applySaluteLeftLeg(ModelPart leftLeg, float tick) {
        if (leftLeg == null) return;
        applyPos(leftLeg, tick, SALUTE_LEFT_LEG_POS_TICKS, SALUTE_LEFT_LEG_X_VALUES, SALUTE_LEFT_LEG_Y_VALUES, SALUTE_LEFT_LEG_Z_VALUES);
        Float pitch = sample(tick, SALUTE_LEFT_LEG_PITCH_TICKS, SALUTE_LEFT_LEG_PITCH_VALUES);
        if (pitch != null) leftLeg.xRot = pitch;
        Float yaw = sample(tick, SALUTE_LEFT_LEG_YAW_TICKS, SALUTE_LEFT_LEG_YAW_VALUES);
        if (yaw != null) leftLeg.yRot = yaw;
        Float roll = sample(tick, SALUTE_LEFT_LEG_ROLL_TICKS, SALUTE_LEFT_LEG_ROLL_VALUES);
        if (roll != null) leftLeg.zRot = roll;
    }

    private static void applySaluteBody(ModelPart body, float tick) {
        if (body == null) return;
        applyPos(body, tick, SALUTE_BODY_POS_TICKS, SALUTE_BODY_X_VALUES, SALUTE_BODY_Y_VALUES, SALUTE_BODY_Z_VALUES);
        Float pitch = sample(tick, SALUTE_BODY_PITCH_TICKS, SALUTE_BODY_PITCH_VALUES);
        if (pitch != null) body.xRot = pitch;
        Float yaw = sample(tick, SALUTE_BODY_YAW_TICKS, SALUTE_BODY_YAW_VALUES);
        if (yaw != null) body.yRot = yaw;
        Float roll = sample(tick, SALUTE_BODY_ROLL_TICKS, SALUTE_BODY_ROLL_VALUES);
        if (roll != null) body.zRot = roll;
    }

    private static void applyPos(ModelPart modelPart, float tick, float[] posTicks, float[] xs, float[] ys, float[] zs) {
        Float x = sample(tick, posTicks, xs);
        Float y = sample(tick, posTicks, ys);
        Float z = sample(tick, posTicks, zs);
        if (x != null || y != null || z != null) {
            float currentX = EmoteReflectionHelper.getModelPartPosX(modelPart);
            float currentY = EmoteReflectionHelper.getModelPartPosY(modelPart);
            float currentZ = EmoteReflectionHelper.getModelPartPosZ(modelPart);
            EmoteReflectionHelper.setModelPartPos(modelPart,
                    x != null ? x : currentX,
                    y != null ? y : currentY,
                    z != null ? z : currentZ);
        }
    }

    private static Float sample(float tick, float[] ticks, float[] values) {
        if (ticks.length == 0) return null;
        if (tick < ticks[0] || tick > ticks[ticks.length - 1]) return null;
        for (int i = 0; i < ticks.length - 1; i++) {
            float t0 = ticks[i];
            float t1 = ticks[i + 1];
            if (tick <= t1) {
                float v0 = values[i];
                float v1 = values[i + 1];
                if (t1 == t0) return v1;
                float progress = (tick - t0) / (t1 - t0);
                float eased = easeInOutQuad(progress);
                return v0 + (v1 - v0) * eased;
            }
        }
        return values[values.length - 1];
    }

    private static float easeInOutQuad(float t) {
        if (t < 0.5f) return 2f * t * t;
        float inv = -2f * t + 2f;
        return 1f - (inv * inv) / 2f;
    }
}
