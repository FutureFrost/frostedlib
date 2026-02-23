package com.futurefrost.frostedlib.data;

import com.futurefrost.frostedlib.util.NbtHelper;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class EntityDataComponentImpl implements EntityDataComponent {
    private final Map<String, PositionData> savedPositions = new HashMap<>();

    public EntityDataComponentImpl() {
        // Store the entity
    }

    @Override
    public void savePosition(String id, PositionData position) {
        savedPositions.put(id, position);
        // Note: No sync for non-player entities
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
    public void readFromNbt(@NotNull NbtCompound nbt) {
        NbtHelper.readSavedPositionsFromNbt(nbt, savedPositions);
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound nbt) {
        NbtHelper.writeSavedPositionsToNbt(nbt, savedPositions);
    }

}