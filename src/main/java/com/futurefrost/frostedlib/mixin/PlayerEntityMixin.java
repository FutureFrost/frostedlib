package com.futurefrost.frostedlib.mixin;

import com.futurefrost.frostedlib.data.PositionData;
import java.util.Optional;
import java.util.Map;
import com.futurefrost.frostedlib.data.PlayerDataComponent;
import com.futurefrost.frostedlib.data.PlayerDataComponentImpl;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin implements PlayerDataComponent {
    @Unique
    private final PlayerDataComponentImpl frostedLibData = new PlayerDataComponentImpl();

    // Interface implementation - NO @Override annotation!
    public void savePosition(String id, PositionData position) {
        frostedLibData.savePosition(id, position);
    }

    public Optional<PositionData> getPosition(String id) {
        return frostedLibData.getPosition(id);
    }

    public boolean removePosition(String id) {
        return frostedLibData.removePosition(id);
    }

    public Map<String, PositionData> getAllPositions() {
        return frostedLibData.getAllPositions();
    }

    public void clearAllPositions() {
        frostedLibData.clearAllPositions();
    }

    // Component interface methods - KEEP @Override
    @Override
    public void readFromNbt(net.minecraft.nbt.NbtCompound tag) {
        frostedLibData.readFromNbt(tag);
    }

    @Override
    public void writeToNbt(net.minecraft.nbt.NbtCompound tag) {
        frostedLibData.writeToNbt(tag);
    }
}