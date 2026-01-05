package com.futurefrost.frostedlib.util;

import com.futurefrost.frostedlib.data.EntityDataComponent;
import com.futurefrost.frostedlib.data.PlayerDataComponent;
import com.futurefrost.frostedlib.data.PositionData;
import com.futurefrost.frostedlib.registry.ModComponents;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;

public class NbtHelper {

    public static void readSavedPositionsFromNbt(NbtCompound nbt, Map<String, PositionData> savedPositions) {
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

    public static void writeSavedPositionsToNbt(@NotNull NbtCompound nbt, Map<String, PositionData> savedPositions) {
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

    public static Optional<PositionData> getSavedPosition(Entity entity, String id) {
        if (entity instanceof ServerPlayerEntity player) {
            PlayerDataComponent data = ModComponents.PLAYER_DATA.get(player);
            return data.getPosition(id);
        } else {
            EntityDataComponent data = ModComponents.ENTITY_DATA.get(entity);
            return data.getPosition(id);
        }
    }
}