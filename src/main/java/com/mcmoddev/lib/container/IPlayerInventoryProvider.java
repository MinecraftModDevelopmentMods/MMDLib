package com.mcmoddev.lib.container;

import java.util.List;

public interface IPlayerInventoryProvider {
    List<PlayerInventoryInfo> getPlayerSlots(MMDContainer container);
}
