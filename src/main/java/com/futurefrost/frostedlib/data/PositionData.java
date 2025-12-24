package com.futurefrost.frostedlib.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.entity.Entity;

public record PositionData(
        RegistryKey<World> dimension,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {

    // Serialize to NBT for saving
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();

        // Save dimension
        nbt.putString("dimension", dimension.getValue().toString());

        // Save position
        nbt.putDouble("x", x);
        nbt.putDouble("y", y);
        nbt.putDouble("z", z);

        // Save rotation
        nbt.putFloat("yaw", yaw);
        nbt.putFloat("pitch", pitch);

        return nbt;
    }

    // Deserialize from NBT
    public static PositionData fromNbt(NbtCompound nbt) {
        // Read dimension
        Identifier dimId = new Identifier(nbt.getString("dimension"));
        RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, dimId);

        // Read position
        double x = nbt.getDouble("x");
        double y = nbt.getDouble("y");
        double z = nbt.getDouble("z");

        // Read rotation
        float yaw = nbt.getFloat("yaw");
        float pitch = nbt.getFloat("pitch");

        return new PositionData(dimension, x, y, z, yaw, pitch);
    }

    // Convert to Vec3d for easy use
    public Vec3d toVec3d() {
        return new Vec3d(x, y, z);
    }

    // Helper to create from any entity's current position
    public static PositionData fromEntity(Entity entity) {
        return new PositionData(
                entity.getWorld().getRegistryKey(),
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                entity.getYaw(),
                entity.getPitch()
        );
    }
}