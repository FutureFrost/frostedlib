package com.futurefrost.frostedlib.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class EntityDataComponentImpl implements EntityDataComponent {
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
        return new HashMap<>(savedPositions);
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
            entryNbt.putString("id", entry.getKey());
            entryNbt.put("position", entry.getValue().toNbt());
            positionsList.add(entryNbt);
        }

        nbt.put("saved_positions", positionsList);
    }
}