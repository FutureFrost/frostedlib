package com.futurefrost.frostedlib.data;

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

    @Override
    public void savePosition(String id, PositionData position) {
        savedPositions.put(id, position);
    }

    @Override
    public Optional<PositionData> getPosition(String id) {
        return Optional.ofNullable(savedPositions.get(id));
    }

    @Override
    public boolean removePosition(String id) {
        return savedPositions.remove(id) != null;
    }

    @Override
    public Map<String, PositionData> getAllPositions() {
        return new HashMap<>(savedPositions); // Return a copy
    }

    @Override
    public void clearAllPositions() {
        savedPositions.clear();
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound nbt) {
        NbtHelper.readSavedPositionsFromNbt(nbt, savedPositions);
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound nbt) {
        NbtHelper.writeSavedPositionsToNbt(nbt, savedPositions);
    }

    // Auto-sync component to client
    @Override
    public void writeSyncPacket(PacketByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeNbt(this.toNbt());
    }

    @Override
    public void applySyncPacket(PacketByteBuf buf) {
        NbtCompound nbt = buf.readNbt();
        if (nbt != null) {
            this.readFromNbt(nbt);
        }
    }

    private NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        this.writeToNbt(nbt);
        return nbt;
    }
}