package dev.prisonsutils.mixin.client;

import dev.prisonsutils.client.damage.DamageIndicatorManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow public abstract float getHealth();
    @Shadow public abstract float getMaxHealth();

    @Unique private float prisonsutils$lastHealth = -1;
    @Unique private static long prisonsutils$lastHitTime = 0;
    @Unique private static java.util.UUID prisonsutils$lastHitEntityId = null;
    @Unique private static boolean prisonsutils$wasCriticalHit = false;

    @Inject(method = "tick", at = @At("TAIL"))
    private void prisonsutils$onTick(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!entity.getEntityWorld().isClient()) return;

        float currentHealth = this.getHealth();
        if (prisonsutils$lastHealth == -1) {
            prisonsutils$lastHealth = currentHealth;
            return;
        }

        if (currentHealth != prisonsutils$lastHealth) {
            float diff = prisonsutils$lastHealth - currentHealth;
            PlayerEntity localPlayer = MinecraftClient.getInstance().player;

            if (localPlayer != null && entity != localPlayer) {
                long now = System.currentTimeMillis();
                long sinceLastHit = now - prisonsutils$lastHitTime;
                boolean recentHit = entity.getUuid().equals(prisonsutils$lastHitEntityId)
                        && sinceLastHit < 1000;
                double distSqr = entity.squaredDistanceTo(localPlayer);
                boolean nearby = distSqr < 49.0;
                boolean isPlayer = entity instanceof PlayerEntity;

                if (diff > 0 && (recentHit || nearby)) {
                    boolean isCrit = recentHit && prisonsutils$wasCriticalHit;
                    DamageIndicatorManager.addIndicator(entity, diff, isCrit, isPlayer);
                }
            }
            prisonsutils$lastHealth = currentHealth;
        }
    }

    @Inject(method = "handleStatus", at = @At("HEAD"))
    private void prisonsutils$onHandleStatus(byte status, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!entity.getEntityWorld().isClient()) return;

        if (prisonsutils$lastHealth == -1) {
            prisonsutils$lastHealth = this.getHealth();
        }

        if (status == 2 || status == 33 || status == 36 || status == 37 || status == 44 || status == 60) {
            PlayerEntity localPlayer = MinecraftClient.getInstance().player;
            if (localPlayer == null) return;

            float current = this.getHealth();
            float diff = prisonsutils$lastHealth - current;
            if (diff == 0) {
                long now = System.currentTimeMillis();
                if (entity.getUuid().equals(prisonsutils$lastHitEntityId)
                        && now - prisonsutils$lastHitTime < 1000
                        && entity != localPlayer) {
                    DamageIndicatorManager.addIndicator(entity, 0,
                            prisonsutils$wasCriticalHit, entity instanceof PlayerEntity);
                }
            }
        }
    }

    @Inject(method = "setHealth", at = @At("HEAD"))
    private void prisonsutils$onSetHealth(float health, CallbackInfo ci) {
        if (prisonsutils$lastHealth == -1) prisonsutils$lastHealth = this.getHealth();
    }

    @Inject(method = "swingHand(Lnet/minecraft/util/Hand;)V", at = @At("HEAD"))
    private void prisonsutils$onSwingHand(Hand hand, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();
        if (entity != client.player) return;
        if (client.targetedEntity instanceof LivingEntity target) {
            prisonsutils$lastHitEntityId = target.getUuid();
            prisonsutils$lastHitTime = System.currentTimeMillis();
            prisonsutils$wasCriticalHit = client.player.fallDistance > 0.0f
                    && !client.player.isOnGround()
                    && !client.player.isClimbing()
                    && !client.player.isTouchingWater()
                    && !client.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS)
                    && !client.player.hasVehicle();
        }
    }
}
