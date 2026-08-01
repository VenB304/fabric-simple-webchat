package com.example.webchat.command;

import com.example.webchat.bridge.ChatBridge;
import com.example.webchat.config.ModConfig;
import com.example.webchat.link.LinkManager;
import com.example.webchat.web.ModerationManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * In-game commands. {@code link}/{@code unlink} (self) are open to any player;
 * everything else requires the admin permission level.
 */
public final class WebChatCommand {
    private WebChatCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("webchat")
                .then(Commands.literal("ban")
                        .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .then(Commands.argument("ip", StringArgumentType.word())
                                .executes(ctx -> {
                                    String ip = StringArgumentType.getString(ctx, "ip");
                                    ModerationManager.ban(ip);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Banned " + ip)
                                            .withStyle(ChatFormatting.YELLOW), true);
                                    return 1;
                                })))
                .then(Commands.literal("unban")
                        .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .then(Commands.argument("ip", StringArgumentType.word())
                                .executes(ctx -> {
                                    String ip = StringArgumentType.getString(ctx, "ip");
                                    ModerationManager.unban(ip);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Unbanned " + ip)
                                            .withStyle(ChatFormatting.YELLOW), true);
                                    return 1;
                                })))
                .then(Commands.literal("kick")
                        .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .then(Commands.argument("username", StringArgumentType.word())
                                .executes(ctx -> {
                                    String username = StringArgumentType.getString(ctx, "username");
                                    boolean kicked = ChatBridge.kickWebUser(username);
                                    if (kicked) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("Kicked web user " + username)
                                                .withStyle(ChatFormatting.YELLOW), true);
                                    } else {
                                        ctx.getSource().sendFailure(Component.literal("No connected web user named " + username));
                                    }
                                    return kicked ? 1 : 0;
                                })))
                .then(Commands.literal("list")
                        .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal(ChatBridge.getWebUserCount() + " web user(s) connected: "
                                            + String.join(", ", ChatBridge.sessionUsernames.values())),
                                    false);
                            return 1;
                        }))
                .then(Commands.literal("reload")
                        .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .executes(ctx -> {
                            ModConfig.load();
                            ctx.getSource().sendSuccess(() -> Component.literal("Web Chat config reloaded")
                                    .withStyle(ChatFormatting.GREEN), true);
                            return 1;
                        }))
                .then(Commands.literal("link")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String code = LinkManager.generateCode(player.getUUID().toString(),
                                    player.getName().getString());
                            player.sendSystemMessage(Component.literal("Your web chat link code: ")
                                    .withStyle(ChatFormatting.GREEN)
                                    .append(Component.literal(code).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                                    .append(Component.literal(" (expires in 5 minutes). Enter it on the web chat page to link your account.")
                                            .withStyle(ChatFormatting.GREEN)));
                            return 1;
                        }))
                .then(Commands.literal("unlink")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            int removed = LinkManager.revokeByUsername(player.getName().getString());
                            ctx.getSource().sendSuccess(() -> Component.literal(removed > 0
                                    ? "Unlinked your web chat account."
                                    : "You have no linked web chat account.").withStyle(ChatFormatting.YELLOW), false);
                            return 1;
                        })
                        .then(Commands.argument("username", StringArgumentType.word())
                                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                                .executes(ctx -> {
                                    String username = StringArgumentType.getString(ctx, "username");
                                    int removed = LinkManager.revokeByUsername(username);
                                    ctx.getSource().sendSuccess(() -> Component.literal(removed > 0
                                            ? "Unlinked " + username
                                            : "No linked account found for " + username)
                                            .withStyle(ChatFormatting.YELLOW), true);
                                    return 1;
                                }))));
    }
}
