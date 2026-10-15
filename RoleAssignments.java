package com.halloweenhunt;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Explicit choices survive round resets; auto removes only the selected player's override. */
public final class RoleAssignments {
    private final Map<UUID, MonsterRole> overrides = new HashMap<>();
    public MonsterRole resolve(UUID player, String name) {
        return overrides.getOrDefault(player, MonsterRole.defaultFor(name));
    }
    public void assign(UUID player, MonsterRole role) {
        if (role == null) overrides.remove(player); else overrides.put(player, role);
    }
    public void clear() { overrides.clear(); }
}
