package com.halloweenhunt;

import java.util.Locale;

public enum MonsterRole {
    MONSTER("몬스터"), MANGTAE("망태할아버지"), FRANKENSTEIN("프랑켄슈타인"), MEDUSA("메두사"), JACK("잭");
    public final String label;
    MonsterRole(String label) { this.label = label; }
    public static MonsterRole parse(String id) {
        if (id.equalsIgnoreCase("franken")) return FRANKENSTEIN;
        try { return valueOf(id.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
    }
    public static MonsterRole defaultFor(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "backbugi624" -> MANGTAE;
            case "night_way" -> FRANKENSTEIN;
            case "seolhee1207" -> MEDUSA;
            case "damdadam" -> JACK;
            default -> MONSTER;
        };
    }
}
