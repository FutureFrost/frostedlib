package com.futurefrost.frostedlib.data;

import dev.onyxstudios.cca.api.v3.component.Component;
import net.minecraft.nbt.NbtCompound;

import java.util.Map;
import java.util.Optional;

public interface EntityDataComponent extends Component {
    // Save a position with a specific ID
    void savePosition(String id, PositionData position);

    // Get a position by ID, returns Optional.empty() if not found
    Optional<PositionData> getPosition(String id);

    // Remove a saved position
    boolean removePosition(String id);

    // Get all saved positions (for debugging/commands)
    Map<String, PositionData> getAllPositions();

    // Clear all saved positions
    void clearAllPositions();
}