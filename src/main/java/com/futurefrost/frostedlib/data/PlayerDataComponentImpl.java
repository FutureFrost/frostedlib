package com.futurefrost.frostedlib.data;

import com.futurefrost.frostedlib.registry.ModComponents;
import com.futurefrost.frostedlib.util.NbtHelper;
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PlayerDataComponentImpl implements PlayerDataComponent, AutoSyncedComponent {
    private final Map<String, PositionData> savedPositions = new HashMap<>();
    private final Object provider; // Store the player entity

    public PlayerDataComponentImpl(Object provider) {
        this.provider = provider;
    }

    @Override
    public void savePosition(String id, PositionData position) {
        savedPositions.put(id, position);
        // Sync when data changes
        ModComponents.PLAYER_DATA.sync(provider);
    }

    @Override
    public Optional<PositionData> getPosition(String id) {
        return Optional.ofNullable(savedPositions.get(id));
    }

    @Override
    public boolean removePosition(String id) {
        boolean removed = savedPositions.remove(id) != null;
        if (removed) {
            // Sync when data changes
            ModComponents.PLAYER_DATA.sync(provider);
        }
        return removed;
    }

    @Override
    public Map<String, PositionData> getAllPositions() {
        return new HashMap<>(savedPositions);
    }

    @Override
    public void clearAllPositions() {
        savedPositions.clear();
        // Sync when data changes
        ModComponents.PLAYER_DATA.sync(provider);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound nbt) {
        NbtHelper.readSavedPositionsFromNbt(nbt, savedPositions);
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound nbt) {
        NbtHelper.writeSavedPositionsToNbt(nbt, savedPositions);
    }

    @Override
    public void writeSyncPacket(PacketByteBuf buf, ServerPlayerEntity recipient) {
        NbtCompound nbt = new NbtCompound();
        this.writeToNbt(nbt);
        buf.writeNbt(nbt);
    }

    @Override
    public void applySyncPacket(PacketByteBuf buf) {
        NbtCompound nbt = buf.readNbt();
        if (nbt != null) {
            this.readFromNbt(nbt);
        }
    }
}