package com.futurefrost.frostedlib.data;

import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

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
    public void readFromNbt(NbtCompound nbt) {
        savedPositions.clear();

        if (nbt.contains("saved_positions")) {
            NbtList positionsList = nbt.getList("saved_positions", NbtCompound.COMPOUND_TYPE);

            for (int i = 0; i < positionsList.size(); i++) {
                NbtCompound entryNbt = positionsList.getCompound(i);

                String id = entryNbt.getString("id");
                PositionData position = PositionData.fromNbt(entryNbt.getCompound("position"));

                savedPositions.put(id, position);
            }
        }
    }

    @Override
    public void writeToNbt(NbtCompound nbt) {
        NbtList positionsList = new NbtList();

        for (Map.Entry<String, PositionData> entry : savedPositions.entrySet()) {
            NbtCompound entryNbt = new NbtCompound();

            // Save the ID
            entryNbt.putString("id", entry.getKey());

            // Save the position data
            entryNbt.put("position", entry.getValue().toNbt());

            positionsList.add(entryNbt);
        }

        nbt.put("saved_positions", positionsList);
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