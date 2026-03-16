package com.ihsanharh.hiveutils.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.cloudburstmc.math.vector.Vector3f;

@Data
@AllArgsConstructor
public class PlayerData {
    private String uuid;
    private String playerName;
    private long runtimeId;
    private Vector3f position;

    public void updatePosition(Vector3f pos) {
        this.position = pos;
    }
}