package com.chiou.javabridge.Models;

import net.minecraft.server.network.ServerPlayerEntity;

public class PlayerMap {
    public static String GetValue(String query, ServerPlayerEntity player) {
        switch (query) {
            case "PLAYER_AGE" -> {
                return String.valueOf(player.age);
            }
            case "PLAYER_BODY_YAW" -> {
                return String.valueOf(player.bodyYaw);
            }
            case "PLAYER_COLLIDED_SOFTLY" -> {
                return String.valueOf(player.collidedSoftly);
            }
            case "PLAYER_CURRENT_EXPLOSION_IMPACT_POS" -> {
                return String.valueOf(player.currentExplosionImpactPos);
            }
            case "PLAYER_DEATH_TIME" -> {
                return String.valueOf(player.deathTime);
            }
            case "PLAYER_DEFAULT_MAX_HEALTH" -> {
                return String.valueOf(player.defaultMaxHealth);
            }
            case "PLAYER_DISTANCE_TRAVELED" -> {
                return String.valueOf(player.distanceTraveled);
            }
            case "PLAYER_EXPERIENCE_LEVEL" -> {
                return String.valueOf(player.experienceLevel);
            }
            case "PLAYER_EXPERIENCE_PICK_UP_DELAY" -> {
                return String.valueOf(player.experiencePickUpDelay);
            }
            case "PLAYER_EXPERIENCE_PROGRESS" -> {
                return String.valueOf(player.experienceProgress);
            }
            case "PLAYER_EXPLODED_BY" -> {
                return String.valueOf(player.explodedBy);
            }
            case "PLAYER_FALL_DISTANCE" -> {
                return String.valueOf(player.fallDistance);
            }
            case "PLAYER_FORWARD_SPEED" -> {
                return String.valueOf(player.forwardSpeed);
            }
            case "PLAYER_GROUND_COLLISION" -> {
                return String.valueOf(player.groundCollision);
            }
            case "PLAYER_HAND_SWINGING" -> {
                return String.valueOf(player.handSwinging);
            }
            case "PLAYER_HAND_SWING_PROGRESS" -> {
                return String.valueOf(player.handSwingProgress);
            }
            case "PLAYER_HAND_SWING_TICKS" -> {
                return String.valueOf(player.handSwingTicks);
            }
            case "PLAYER_HEAD_YAW" -> {
                return String.valueOf(player.headYaw);
            }
            case "PLAYER_HORIZONTAL_COLLISION" -> {
                return String.valueOf(player.horizontalCollision);
            }
            case "PLAYER_HURT_TIME" -> {
                return String.valueOf(player.hurtTime);
            }
            case "PLAYER_IN_POWDER_SNOW" -> {
                return String.valueOf(player.inPowderSnow);
            }
            case "PLAYER_NOT_IN_ANY_WORLD" -> {
                return String.valueOf(player.notInAnyWorld);
            }
            case "PLAYER_NO_CLIP" -> {
                return String.valueOf(player.noClip);
            }
            case "PLAYER_SEEN_CREDITS" -> {
                return String.valueOf(player.seenCredits);
            }
            case "PLAYER_SIDEWAYS_SPEED" -> {
                return String.valueOf(player.sidewaysSpeed);
            }
            case "PLAYER_SPEED" -> {
                return String.valueOf(player.speed);
            }
            case "PLAYER_STRIDE_DISTANCE" -> {
                return String.valueOf(player.strideDistance);
            }
            case "PLAYER_STUCK_ARROW_TIMER" -> {
                return String.valueOf(player.stuckArrowTimer);
            }
            case "PLAYER_STUCK_STINGER_TIMER" -> {
                return String.valueOf(player.stuckStingerTimer);
            }
            case "PLAYER_TIME_UNTIL_REGEN" -> {
                return String.valueOf(player.timeUntilRegen);
            }
            case "PLAYER_TOTAL_EXPERIENCE" -> {
                return String.valueOf(player.totalExperience);
            }
            case "PLAYER_UPWARD_SPEED" -> {
                return String.valueOf(player.upwardSpeed);
            }
            case "PLAYER_WAS_IN_POWDER_SNOW" -> {
                return String.valueOf(player.wasInPowderSnow);
            }
        }

        return "Error";
    }
}
