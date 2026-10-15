package com.halloweenhunt;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class RoleCommands {
    private RoleCommands() {}
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("role")
                .executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("/hunt role <플레이어> <mangtae|frankenstein|medusa|jack|monster|auto> — 지정 후 /hunt start로 체험"), false);
                    return 1;
                })
                .then(Commands.argument("players", EntityArgument.players())
                    .then(Commands.argument("role", StringArgumentType.word())
                        .suggests((c, builder) -> SharedSuggestionProvider.suggest(
                                new String[]{"mangtae", "frankenstein", "medusa", "jack", "monster", "auto"}, builder))
                        .executes(c -> {
                            String id = StringArgumentType.getString(c, "role");
                            MonsterRole role = MonsterRole.parse(id);
                            if (role == null && !id.equalsIgnoreCase("auto")) {
                                c.getSource().sendFailure(Component.literal("역할: mangtae, frankenstein, medusa, jack, monster, auto"));
                                return 0;
                            }
                            int changed = 0;
                            for (ServerPlayer p : EntityArgument.getPlayers(c, "players")) {
                                String error = MonsterRoles.assign(p, role);
                                if (error != null) { c.getSource().sendFailure(Component.literal(p.getName().getString() + ": " + error)); continue; }
                                changed++;
                                String result = role == null ? "자동 배정 (" + MonsterRoles.role(p).label + ")" : role.label;
                                c.getSource().sendSuccess(() -> Component.literal("§a" + p.getName().getString() + " → " + result), false);
                            }
                            return changed;
                        })));
    }
}
