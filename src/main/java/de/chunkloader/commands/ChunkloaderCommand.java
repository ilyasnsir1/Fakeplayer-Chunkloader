package de.chunkloader.commands;

import de.chunkloader.network.ChunkloaderNetworking;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.ChunkloaderConstants;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.permissions.PermissionManager;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class ChunkloaderCommand {

    private static boolean canModifyNamed(CommandContext<CommandSourceStack> context, String name) {
        return canAccessNamed(context, name, false);
    }

    private static boolean canAccessNamed(CommandContext<CommandSourceStack> context, String name, boolean allowShared) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || PermissionManager.isAdmin(player)) {
            return true;
        }
        var config = ChunkloaderForgeMod.getConfig();
        if (config == null || name == null) {
            return true;
        }
        ChunkloaderTarget entry = config.getEntryByName(name);
        if (entry == null) {
            return true;
        }
        if (allowShared ? PermissionManager.canOperateEntry(player, entry) : PermissionManager.canModifyEntry(player, entry)) {
            return true;
        }
        context.getSource().sendFailure(Component.literal("You don't own this player.").withStyle(ChatFormatting.RED));
        return false;
    }

    private static ChunkloaderTarget findSavedLoader(String name) {
        var config = ChunkloaderForgeMod.getConfig();
        return config == null ? null : config.getEntryByName(name);
    }

    private static boolean canListEntry(ServerPlayer player, ChunkloaderTarget entry) {
        return player == null || PermissionManager.canOperateEntry(player, entry);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, net.minecraft.core.HolderLookup.Provider registryAccess) {
        registerCommands(dispatcher, registryAccess);
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, net.minecraft.core.HolderLookup.Provider registryAccess) {
        dispatcher.register(Commands.literal("fakeplayer")
            .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_USE))
            .then(Commands.literal("help").executes(ChunkloaderCommand::openHelp))
            .then(addCommand())
            .then(moveCommand())
            .then(Commands.literal("remove")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OWNED_LOADERS)
                    .executes(ChunkloaderCommand::removeFakePlayer)))
            .then(Commands.literal("list")
                .executes(ChunkloaderCommand::listFakePlayers))
            .then(Commands.literal("info")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .executes(ChunkloaderCommand::infoFakePlayer)))
            .then(Commands.literal("reload")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::reloadConfig))
            .then(Commands.literal("disable")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .executes(ChunkloaderCommand::disableFakePlayer)))
            .then(Commands.literal("stats")
                .executes(ChunkloaderCommand::statsFakePlayers))
            .then(Commands.literal("namevisible")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .then(Commands.argument("visible", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setNameVisible))))
            .then(Commands.literal("setradius")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .then(Commands.argument("radius", IntegerArgumentType.integer(ChunkloaderConstants.MIN_RADIUS, ChunkloaderConstants.MAX_RADIUS))
                        .executes(ChunkloaderCommand::setRadius))))
            .then(Commands.literal("setmobtarget")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setMobTarget))))
            .then(mobActivityCommand("pausemobs", false))
            .then(mobActivityCommand("resumemobs", true))
            .then(Commands.literal("toggle")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .executes(ChunkloaderCommand::toggleMobSpawning)))
            .then(Commands.literal("tablist")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(Commands.argument("visible", BoolArgumentType.bool())
                    .executes(ChunkloaderCommand::setTabListVisibleAll)))
            .then(Commands.literal("rename")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .then(Commands.argument("newName", StringArgumentType.string())
                        .executes(ChunkloaderCommand::renameChunkloader))))
            .then(Commands.literal("restore")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.DISABLED_LOADERS)
                    .executes(ChunkloaderCommand::restoreFakePlayer)))
            .then(Commands.literal("restoreall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::enableAllFakePlayers))
            .then(Commands.literal("disableall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::previewDisableAll)
                .then(Commands.literal("confirm")
                    .executes(ChunkloaderCommand::disableAllFakePlayers)))
            .then(Commands.literal("removeall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::previewRemoveAll)
                .then(Commands.literal("confirm")
                    .executes(ChunkloaderCommand::removeAllFakePlayers))
                .then(Commands.literal("enabled")
                    .executes(ChunkloaderCommand::previewRemoveEnabled)
                    .then(Commands.literal("confirm")
                        .executes(ChunkloaderCommand::removeAllEnabledFakePlayers)))
                .then(Commands.literal("disabled")
                    .executes(ChunkloaderCommand::previewRemoveDisabled)
                    .then(Commands.literal("confirm")
                        .executes(ChunkloaderCommand::removeAllDisabledFakePlayers))))
            .then(Commands.literal("visualize")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.ACTIVE_LOADERS)
                    .executes(ChunkloaderCommand::toggleVisualization)))
            .then(Commands.literal("visualize3d")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.ACTIVE_LOADERS)
                    .executes(ChunkloaderCommand::toggleVisualization3D)
                    .then(Commands.argument("minY", IntegerArgumentType.integer(-64, 320))
                        .then(Commands.argument("maxY", IntegerArgumentType.integer(-64, 320))
                            .executes(ChunkloaderCommand::toggleVisualization3DWithHeight)))))
            .then(ChunkloaderLimitCommands.createNode())
            .then(ChunkloaderSharingCommands.share())
            .then(ChunkloaderSharingCommands.unshare())
            .then(ChunkloaderSharingCommands.shares())
            .then(Commands.literal("owner")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OWNED_LOADERS)
                    .then(Commands.argument("player", StringArgumentType.string())
                        .suggests(ChunkloaderSuggestions.ONLINE_PLAYERS)
                        .executes(ChunkloaderCommand::assignOwner))))
            .then(Commands.literal("permission")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(Commands.literal("grant")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .suggests(ChunkloaderSuggestions.ONLINE_PLAYERS)
                        .executes(ChunkloaderCommand::grantAllPermissions)))
                .then(Commands.literal("revoke")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .suggests(ChunkloaderSuggestions.ONLINE_PLAYERS)
                        .executes(ChunkloaderCommand::revokeAllPermissions)))));

        var fpNode = Commands.literal("fp")
            .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_USE))
            .then(Commands.literal("help").executes(ChunkloaderCommand::openHelp))
            .then(addCommand())
            .then(moveCommand())
            .then(Commands.literal("remove")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OWNED_LOADERS)
                    .executes(ChunkloaderCommand::removeFakePlayer)))
            .then(Commands.literal("list")
                .executes(ChunkloaderCommand::listFakePlayers))
            .then(Commands.literal("info")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .executes(ChunkloaderCommand::infoFakePlayer)))
            .then(Commands.literal("reload")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::reloadConfig))
            .then(Commands.literal("disable")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .executes(ChunkloaderCommand::disableFakePlayer)))
            .then(Commands.literal("stats")
                .executes(ChunkloaderCommand::statsFakePlayers))
            .then(Commands.literal("namevisible")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .then(Commands.argument("visible", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setNameVisible))))
            .then(Commands.literal("setradius")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .then(Commands.argument("radius", IntegerArgumentType.integer(ChunkloaderConstants.MIN_RADIUS, ChunkloaderConstants.MAX_RADIUS))
                        .executes(ChunkloaderCommand::setRadius))))
            .then(Commands.literal("setmobtarget")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setMobTarget))))
            .then(mobActivityCommand("pausemobs", false))
            .then(mobActivityCommand("resumemobs", true))
            .then(Commands.literal("toggle")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .executes(ChunkloaderCommand::toggleMobSpawning)))
            .then(Commands.literal("tablist")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(Commands.argument("visible", BoolArgumentType.bool())
                    .executes(ChunkloaderCommand::setTabListVisibleAll)))
            .then(Commands.literal("rename")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .then(Commands.argument("newName", StringArgumentType.string())
                        .executes(ChunkloaderCommand::renameChunkloader))))
            .then(Commands.literal("restore")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.DISABLED_LOADERS)
                    .executes(ChunkloaderCommand::restoreFakePlayer)))
            .then(Commands.literal("restoreall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::enableAllFakePlayers))
            .then(Commands.literal("disableall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::previewDisableAll)
                .then(Commands.literal("confirm")
                    .executes(ChunkloaderCommand::disableAllFakePlayers)))
            .then(Commands.literal("removeall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::previewRemoveAll)
                .then(Commands.literal("confirm")
                    .executes(ChunkloaderCommand::removeAllFakePlayers))
                .then(Commands.literal("enabled")
                    .executes(ChunkloaderCommand::previewRemoveEnabled)
                    .then(Commands.literal("confirm")
                        .executes(ChunkloaderCommand::removeAllEnabledFakePlayers)))
                .then(Commands.literal("disabled")
                    .executes(ChunkloaderCommand::previewRemoveDisabled)
                    .then(Commands.literal("confirm")
                        .executes(ChunkloaderCommand::removeAllDisabledFakePlayers))))
            .then(Commands.literal("visualize")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.ACTIVE_LOADERS)
                    .executes(ChunkloaderCommand::toggleVisualization)))
            .then(Commands.literal("visualize3d")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.ACTIVE_LOADERS)
                    .executes(ChunkloaderCommand::toggleVisualization3D)
                    .then(Commands.argument("minY", IntegerArgumentType.integer(-64, 320))
                        .then(Commands.argument("maxY", IntegerArgumentType.integer(-64, 320))
                            .executes(ChunkloaderCommand::toggleVisualization3DWithHeight)))))
            .then(ChunkloaderLimitCommands.createNode())
            .then(ChunkloaderSharingCommands.share())
            .then(ChunkloaderSharingCommands.unshare())
            .then(ChunkloaderSharingCommands.shares())
            .then(Commands.literal("owner")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OWNED_LOADERS)
                    .then(Commands.argument("player", StringArgumentType.string())
                        .suggests(ChunkloaderSuggestions.ONLINE_PLAYERS)
                        .executes(ChunkloaderCommand::assignOwner))))
            .then(Commands.literal("permission")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(Commands.literal("grant")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .suggests(ChunkloaderSuggestions.ONLINE_PLAYERS)
                        .executes(ChunkloaderCommand::grantAllPermissions)))
                .then(Commands.literal("revoke")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .suggests(ChunkloaderSuggestions.ONLINE_PLAYERS)
                        .executes(ChunkloaderCommand::revokeAllPermissions))));

        dispatcher.register(fpNode);
    }

    private static int openHelp(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "Run /fp help in-game to open the guide. Use /fp add <x> <y> <z> to place a Fakeplayer from the console."), false);
        } else if (!ChunkloaderNetworking.tryOpenHelp(player)) {
            context.getSource().sendSuccess(() -> Component.literal(
                    "Use /fp add to create a Fakeplayer where you stand. With the client mod, right-click it to open its map."), false);
        }
        return 1;
    }

    private static int addFakePlayer(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("This command can only be executed by a player"));
            return 0;
        }
        return createFakePlayer(context, player, player.blockPosition(), (net.minecraft.server.level.ServerLevel) player.level(), player.getYRot());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> addCommand() {
        return Commands.literal("add")
                .executes(ChunkloaderCommand::addFakePlayer)
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> addFakePlayerAt(context, context.getSource().getLevel()))
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(context -> addFakePlayerAt(context, DimensionArgument.getDimension(context, "dimension")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> moveCommand() {
        return Commands.literal("move")
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> movePlayer(context, null))
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(context -> movePlayer(context, DimensionArgument.getDimension(context, "dimension"))))));
    }

    private static int addFakePlayerAt(CommandContext<CommandSourceStack> context, ServerLevel world) {
        ServerPlayer player = context.getSource().getPlayer();
        BlockPos position = BlockPosArgument.getBlockPos(context, "pos");
        return createFakePlayer(context, player, position, world, context.getSource().getRotation().y);
    }

    private static int createFakePlayer(CommandContext<CommandSourceStack> context, ServerPlayer player,
            BlockPos pos, ServerLevel world, float yaw) {
        try {
            ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
            if (manager == null) {
                context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
                return 0;
            }
            if (world == null) {
                context.getSource().sendFailure(Component.literal("Level is not available"));
                return 0;
            }
            if (!world.isInWorldBounds(pos) || pos.getY() < ChunkloaderConstants.MIN_BLOCK_Y
                    || pos.getY() > ChunkloaderConstants.MAX_BLOCK_Y) {
                context.getSource().sendFailure(Component.literal("Position is outside the world.").withStyle(ChatFormatting.RED));
                return 0;
            }
            var config = ChunkloaderForgeMod.getConfig();
            String owner = player != null ? player.getGameProfile().name() : null;
            var ownerUuid = player != null ? player.getUUID() : null;
            String limitMessage = config != null ? config.getActivationLimitMessage(ownerUuid, owner) : null;
            if (limitMessage != null) {
                context.getSource().sendFailure(Component.literal(limitMessage));
                return 0;
            }
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            String dimension = ChunkloaderManager.getDimensionString(world);
            String overlappingName = manager.getOverlappingChunkloaderName(chunkX, chunkZ, 0, dimension, null);
            if (overlappingName != null) {
                context.getSource().sendFailure(Component.literal(
                        "Cannot add player: Position is already covered by a player '" + overlappingName + "'").withStyle(ChatFormatting.RED));
                return 0;
            }
            if (!manager.addChunkloader(chunkX, chunkZ, pos, null, world, owner, yaw, ownerUuid)) {
                String failureLimit = config != null ? config.getActivationLimitMessage(ownerUuid, owner) : null;
                context.getSource().sendFailure(Component.literal(failureLimit != null ? failureLimit : "Failed to create player")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

            ChunkloaderTarget entry = config != null ? config.getEntry(chunkX, chunkZ, dimension) : null;
            if (entry != null && entry.easterEggSkinIndex() == null) {
                String name = entry.name() != null ? entry.name() : "Unnamed";
                context.getSource().sendSuccess(() -> Component.literal("Player '" + name + "' created at position ("
                        + entry.blockX() + ", " + entry.blockY() + ", " + entry.blockZ() + ") in " + entry.dimension())
                        .withStyle(ChatFormatting.GREEN), true);
                if (player != null && (net.minecraft.server.level.ServerLevel) player.level() == world) {
                    context.getSource().sendSuccess(() -> Component.literal("Right-click the player to open the map."), false);
                }
            }
            return 1;
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error("Error in addFakePlayer command", e);
            context.getSource().sendFailure(Component.literal("An error occurred: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int movePlayer(CommandContext<CommandSourceStack> context, ServerLevel targetWorld) {
        String name = StringArgumentType.getString(context, "name");
        if (!canAccessNamed(context, name, true)) {
            return 0;
        }

        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = findSavedLoader(name);
        if (entry == null) {
            context.getSource().sendFailure(Component.literal("Player '" + name + "' not found."));
            return 0;
        }

        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        String dimension = targetWorld == null ? entry.dimension() : ChunkloaderManager.getDimensionString(targetWorld);
        String failure = manager.updateDisabledChunkloaderCoordsWithMessage(
                entry.chunkX(), entry.chunkZ(), entry.dimension(), pos.getX() >> 4, pos.getZ() >> 4,
                pos.getX(), pos.getY(), pos.getZ(), dimension);
        if (failure != null) {
            context.getSource().sendFailure(Component.literal(failure));
            return 0;
        }

        ChunkloaderNetworking.refreshOpenChunkMapMarkers(context.getSource().getServer(), manager);
        context.getSource().sendSuccess(() -> Component.literal("Moved '" + entry.name() + "' to ("
                + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ") in " + dimension).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removeFakePlayer(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!canModifyNamed(context, name)) {
            return 0;
        }

        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        boolean success = ChunkloaderForgeMod.getChunkloaderManager().removeChunkloaderByName(name);

        if (success) {
                context.getSource().sendSuccess(() -> Component.literal("Player '" + name + "' removed").withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            var config = ChunkloaderForgeMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }
    }

    private static int listFakePlayers(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        var fakePlayers = ChunkloaderForgeMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(entry -> canListEntry(context.getSource().getPlayer(), entry))
            .toList();

        if (fakePlayers.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No saved players"), false);
        } else {
            int count = fakePlayers.size();
            context.getSource().sendSuccess(() -> Component.literal("Saved " + playerNoun(count) + " (" + count + "):"), false);
            for (var entry : fakePlayers) {
                String name = entry.name() != null ? entry.name() : "Unnamed";
                context.getSource().sendSuccess(() -> Component.literal("  - " + name + (entry.enabled() ? " (active)" : " (disabled)") + " @ Block (" +
                    entry.blockX() + ", " + entry.blockY() + ", " + entry.blockZ() + ") in " + entry.dimension()), false);
            }
        }

        return fakePlayers.size();
    }

    private static int infoFakePlayer(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!canAccessNamed(context, name, true)) {
            return 0;
        }

        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = findSavedLoader(name);

        if (entry == null) {
            var config = ChunkloaderForgeMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("=== Player Info ===").withStyle(ChatFormatting.GOLD), false);
        context.getSource().sendSuccess(() -> Component.literal("Name: " + entry.name()).withStyle(ChatFormatting.YELLOW), false);
        context.getSource().sendSuccess(() -> Component.literal("Status: " + (entry.enabled() ? "Active" : "Inactive"))
            .withStyle(entry.enabled() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        String mode = entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
        ChatFormatting modeColor = entry.allowMobSpawning() ? ChatFormatting.GREEN : ChatFormatting.BLUE;
        context.getSource().sendSuccess(() -> Component.literal("Mode: " + mode).withStyle(modeColor), false);
        String mobSetting = entry.allowMobSpawning()
                ? "Mob target: " + (entry.mobTarget() ? "Enabled" : "Disabled")
                : "Mob activity: " + (entry.mobAi() ? "Enabled" : "Paused");
        context.getSource().sendSuccess(() -> Component.literal(mobSetting), false);
        context.getSource().sendSuccess(() -> Component.literal("Dimension: " + entry.dimension()), false);
        context.getSource().sendSuccess(() -> Component.literal("Chunk: (" + entry.chunkX() + ", " + entry.chunkZ() + ")"), false);
        context.getSource().sendSuccess(() -> Component.literal("Block: (" + entry.blockX() + ", " + entry.blockY() + ", " + entry.blockZ() + ")"), false);
        context.getSource().sendSuccess(() -> Component.literal("Name visible: " + (entry.nameVisible() ? "Yes" : "No")), false);
        context.getSource().sendSuccess(() -> Component.literal(describeRadius(entry.chunkRadius())), false);

        return 1;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderForgeMod.getChunkloaderManager().reloadConfig();
        context.getSource().sendSuccess(() -> Component.literal("Config reloaded").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int disableFakePlayer(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!canAccessNamed(context, name, true)) {
            return 0;
        }

        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget existingEntry = findSavedLoader(name);

        if (existingEntry == null) {
            var config = ChunkloaderForgeMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }

        if (!existingEntry.enabled()) {
            context.getSource().sendSuccess(() -> Component.literal("Player '" + name + "' is already disabled")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        ChunkloaderForgeMod.getChunkloaderManager().toggleChunkloaderByName(name);
        ChunkloaderTarget updatedEntry = findSavedLoader(name);
        if (updatedEntry == null || updatedEntry.enabled()) {
            context.getSource().sendFailure(Component.literal("Failed to disable player '" + name + "'")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        String keyName = de.chunkloader.util.KeybindHelper.getDisabledChunkloadersKeyName();
        context.getSource().sendSuccess(() -> Component.literal(
                "Player '" + name + "' disabled (Press " + keyName + " to open disabled list)").withStyle(ChatFormatting.RED), true);
        return 1;
    }

    private static int statsFakePlayers(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderManager.ChunkloaderStats stats = ChunkloaderForgeMod.getChunkloaderManager().getStats();
        ChunkloaderManager.ChunkloaderPerformanceStats perfStats = ChunkloaderForgeMod.getChunkloaderManager().getPerformanceStats();

        context.getSource().sendSuccess(() -> Component.literal("=== Player Statistics ===").withStyle(ChatFormatting.GOLD), false);
        context.getSource().sendSuccess(() -> Component.literal("Total: " + stats.total()).withStyle(ChatFormatting.AQUA), false);
        context.getSource().sendSuccess(() -> Component.literal("Active players: " + stats.enabled()).withStyle(ChatFormatting.GREEN), false);
        context.getSource().sendSuccess(() -> Component.literal("Inactive: " + stats.disabled()).withStyle(ChatFormatting.RED), false);
        context.getSource().sendSuccess(() -> Component.literal("Selected chunks: " + stats.coreChunks()), false);

        long usedMB = perfStats.usedMemory() / (1024 * 1024);
        long maxMB = perfStats.maxMemory() / (1024 * 1024);
        ChatFormatting memoryColor = perfStats.memoryUsagePercent() > 80 ? ChatFormatting.RED : perfStats.memoryUsagePercent() > 60 ? ChatFormatting.YELLOW : ChatFormatting.GREEN;

        context.getSource().sendSuccess(() -> Component.literal("Memory used: " + usedMB + " MB / " + maxMB + " MB (" + String.format("%.1f", perfStats.memoryUsagePercent()) + "%)")
            .withStyle(memoryColor), false);

        if (perfStats.memoryUsagePercent() > 85) {
            context.getSource().sendSuccess(() -> Component.literal("⚠ Warning: High memory usage!").withStyle(ChatFormatting.RED), false);
        }

        if (stats.chunksWithRings() > 1000) {
            context.getSource().sendSuccess(() -> Component.literal("⚠ Note: Many loaded chunks may impact performance").withStyle(ChatFormatting.YELLOW), false);
        }

        return 1;
    }

    private static int setMobTarget(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!canAccessNamed(context, name, true)) {
            return 0;
        }
        boolean enabled = BoolArgumentType.getBool(context, "enabled");

        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = findSavedLoader(name);

        if (entry == null) {
            var config = ChunkloaderForgeMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }

        if (!entry.allowMobSpawning()) {
            context.getSource().sendFailure(Component.literal("Mob target is only available for Fakeplayers").withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean success = ChunkloaderForgeMod.getChunkloaderManager().setChunkloaderMobTarget(name, enabled);

        if (success) {
            ChunkloaderNetworking.refreshOpenChunkMapMarkers(context.getSource().getServer(),
                    ChunkloaderForgeMod.getChunkloaderManager());
            context.getSource().sendSuccess(() -> Component.literal("Mob target for '" + name + "' set to " + (enabled ? "enabled" : "disabled"))
                .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Error setting mob target").withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mobActivityCommand(String command, boolean enabled) {
        return Commands.literal(command)
                .then(Commands.argument("name", StringArgumentType.string())
                        .suggests(ChunkloaderSuggestions.OPERABLE_CHUNKPLAYERS)
                        .executes(context -> setMobAi(context, enabled)));
    }

    private static int setMobAi(CommandContext<CommandSourceStack> context, boolean enabled) {
        String name = StringArgumentType.getString(context, "name");
        if (!canAccessNamed(context, name, true)) {
            return 0;
        }
        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }
        ChunkloaderTarget entry = ChunkloaderForgeMod.getConfig() != null
                ? ChunkloaderForgeMod.getConfig().getEntryByName(name) : null;
        if (entry == null) {
            context.getSource().sendFailure(Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (entry.allowMobSpawning()) {
            context.getSource().sendFailure(Component.literal("Only Chunkplayers can pause or resume mobs.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!ChunkloaderForgeMod.getChunkloaderManager().setChunkloaderMobAi(name, enabled)) {
            context.getSource().sendFailure(Component.literal("Could not update mob activity.").withStyle(ChatFormatting.RED));
            return 0;
        }
        ChunkloaderNetworking.refreshOpenChunkMapMarkers(context.getSource().getServer(),
                ChunkloaderForgeMod.getChunkloaderManager());
        context.getSource().sendSuccess(() -> Component.literal((enabled ? "Resumed mobs for '" : "Paused mobs for '") + entry.name() + "'.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setNameVisible(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!canAccessNamed(context, name, true)) {
            return 0;
        }
        boolean visible = BoolArgumentType.getBool(context, "visible");

        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = findSavedLoader(name);

        if (entry == null) {
            var config = ChunkloaderForgeMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }

        boolean success = ChunkloaderForgeMod.getChunkloaderManager().setChunkloaderNameVisible(name, visible);

        if (success) {
            ChunkloaderNetworking.refreshOpenChunkMapMarkers(context.getSource().getServer(),
                    ChunkloaderForgeMod.getChunkloaderManager());
            context.getSource().sendSuccess(() -> Component.literal("Name visibility for '" + name + "' set to " + (visible ? "visible" : "invisible"))
                .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Error setting name visibility").withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setRadius(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!canAccessNamed(context, name, true)) {
            return 0;
        }
        int radius = IntegerArgumentType.getInteger(context, "radius");

        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        if (radius < ChunkloaderConstants.MIN_RADIUS || radius > ChunkloaderConstants.MAX_RADIUS) {
            context.getSource().sendFailure(Component.literal("Invalid radius: " + radius + " (must be between " + ChunkloaderConstants.MIN_RADIUS + " and " + ChunkloaderConstants.MAX_RADIUS + ")").withStyle(ChatFormatting.RED));
            return 0;
        }

        ChunkloaderTarget entry = findSavedLoader(name);

        if (entry == null) {
            var config = ChunkloaderForgeMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }

        String error = ChunkloaderForgeMod.getChunkloaderManager().setChunkloaderRadiusWithMessage(name, radius);
        if (error != null) {
            context.getSource().sendFailure(Component.literal(error).withStyle(ChatFormatting.RED));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Player '" + name + "': " + describeRadius(radius))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static String describeRadius(int radius) {
        int width = 2 * radius + 1;
        int selectedChunks = width * width;
        return "SD: " + radius + " (" + selectedChunks + " selected "
                + (selectedChunks == 1 ? "chunk" : "chunks") + ")";
    }

    private static int toggleMobSpawning(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!canAccessNamed(context, name, true)) {
            return 0;
        }

        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = findSavedLoader(name);

        if (entry == null) {
            var config = ChunkloaderForgeMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }

        boolean allow = !entry.allowMobSpawning();
        boolean success = ChunkloaderForgeMod.getChunkloaderManager().setChunkloaderAllowMobSpawning(name, allow);

        if (success) {
            String mode = allow ? "Fakeplayer" : "Chunkplayer";
            context.getSource().sendSuccess(() -> Component.literal("Mode for '" + name + "' set to " + mode)
                .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Toggle failed: rename the player first to avoid a name conflict.").withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int setTabListVisibleAll(CommandContext<CommandSourceStack> context) {
        boolean visible = BoolArgumentType.getBool(context, "visible");

        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        if (ChunkloaderForgeMod.getChunkloaderManager().isTabListVisibleAll() == visible) {
            context.getSource().sendSuccess(
                () -> Component.literal("Tab list visibility is already set to " + visible).withStyle(ChatFormatting.YELLOW),
                false
            );
            return 1;
        }

        int changed = ChunkloaderForgeMod.getChunkloaderManager().setTabListVisibleAll(visible);
        if (changed > 0) {
            context.getSource().sendSuccess(
                () -> Component.literal("Tab list visibility set to " + visible + " for " + changed + " " + playerNoun(changed))
                    .withStyle(ChatFormatting.GREEN),
                true
            );
            return 1;
        }

        context.getSource().sendFailure(Component.literal("No players found").withStyle(ChatFormatting.RED));
        return 0;
    }

    private static int renameChunkloader(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!canAccessNamed(context, name, true)) {
            return 0;
        }
        final String newName = StringArgumentType.getString(context, "newName");

        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = findSavedLoader(name);

        if (entry == null) {
            var config = ChunkloaderForgeMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }

        var server = context.getSource().getServer();
        boolean isRealPlayerName = false;
        for (var player : server.getPlayerList().getPlayers()) {
            if (!(player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer)
                && newName.equalsIgnoreCase(player.getName().getString())) {
                isRealPlayerName = true;
                break;
            }
        }

        if (isRealPlayerName) {
            context.getSource().sendFailure(
                Component.literal("Cannot rename to '" + newName + "'. This name is already used by a real player.")
                    .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        boolean success = ChunkloaderForgeMod.getChunkloaderManager().renameChunkloader(entry.chunkX(), entry.chunkZ(), entry.dimension(), newName);

        if (success) {
            context.getSource().sendSuccess(() -> Component.literal("Renamed '" + name + "' to '" + newName + "'")
                .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Failed to rename player. Name may already be in use or invalid.").withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int restoreFakePlayer(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!canAccessNamed(context, name, true)) {
            return 0;
        }

        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        var config = ChunkloaderForgeMod.getConfig();
        ChunkloaderTarget entry = config != null ? config.getEntryByName(name) : null;
        if (entry == null) {
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Player '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }

        if (entry.enabled()) {
            context.getSource().sendSuccess(() -> Component.literal("Player '" + name + "' is already enabled")
                .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        String limitMessage = ChunkloaderForgeMod.getChunkloaderManager().getActivationLimitMessage(entry);
        if (limitMessage != null) {
            context.getSource().sendFailure(Component.literal(limitMessage));
            return 0;
        }
        boolean restored = ChunkloaderForgeMod.getChunkloaderManager().toggleChunkloaderByName(name);
        if (restored) {
            context.getSource().sendSuccess(() -> Component.literal("Player '" + name + "' restored")
                .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }

        context.getSource().sendFailure(Component.literal("Failed to restore player '" + name + "'")
            .withStyle(ChatFormatting.RED));
        return 0;
    }

    private static int enableAllFakePlayers(CommandContext<CommandSourceStack> context) {
        var manager = ChunkloaderForgeMod.getChunkloaderManager();
        var config = ChunkloaderForgeMod.getConfig();
        if (manager == null || config == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }
        List<ChunkloaderTarget> disabled = config.getChunkEntries().stream()
                .filter(entry -> !entry.enabled()).toList();
        if (disabled.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No disabled player found")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        int count = manager.enableAllChunkloaders();
        if (count > 0) {
            String message = count + (count == 1 ? " Player restored" : " Players restored");
            context.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), true);
        }
        List<ChunkloaderTarget> remaining = disabled.stream()
                .map(entry -> config.getEntry(entry.chunkX(), entry.chunkZ(), entry.dimension()))
                .filter(entry -> entry != null && !entry.enabled()).toList();
        long limited = remaining.stream().filter(entry -> manager.getActivationLimitMessage(entry) != null).count();
        if (limited > 0) {
            String message = limited + (limited == 1 ? " Player remains" : " Players remain")
                    + " disabled because a player limit was reached.";
            context.getSource().sendFailure(Component.literal(message));
        }
        long failed = remaining.size() - limited;
        if (failed > 0) {
            context.getSource().sendFailure(Component.literal(failed
                    + (failed == 1 ? " Player could not be restored." : " Players could not be restored.")));
        }
        return count;
    }

    private static String playerNoun(int count) {
        return count == 1 ? "player" : "players";
    }

    private static int previewDisableAll(CommandContext<CommandSourceStack> context) {
        var manager = ChunkloaderForgeMod.getChunkloaderManager();
        int count = manager == null ? 0 : (int) manager.getActiveChunkloaderEntries().stream().filter(ChunkloaderTarget::enabled).count();
        context.getSource().sendFailure(Component.literal("This will disable " + count + " " + playerNoun(count) + ". Run /fp disableall confirm."));
        return 0;
    }

    private static int previewRemoveAll(CommandContext<CommandSourceStack> context) {
        var config = ChunkloaderForgeMod.getConfig();
        int count = config == null ? 0 : config.getChunkEntries().size();
        context.getSource().sendFailure(Component.literal("This will remove " + count + " " + playerNoun(count) + ". Run /fp removeall confirm."));
        return 0;
    }

    private static int previewRemoveEnabled(CommandContext<CommandSourceStack> context) {
        var config = ChunkloaderForgeMod.getConfig();
        int count = config == null ? 0 : (int) config.getChunkEntries().stream().filter(ChunkloaderTarget::enabled).count();
        context.getSource().sendFailure(Component.literal("This will remove " + count + " enabled " + playerNoun(count) + ". Run /fp removeall enabled confirm."));
        return 0;
    }

    private static int previewRemoveDisabled(CommandContext<CommandSourceStack> context) {
        var config = ChunkloaderForgeMod.getConfig();
        int count = config == null ? 0 : (int) config.getChunkEntries().stream().filter(entry -> !entry.enabled()).count();
        context.getSource().sendFailure(Component.literal("This will remove " + count + " disabled " + playerNoun(count) + ". Run /fp removeall disabled confirm."));
        return 0;
    }

    private static int disableAllFakePlayers(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        var entriesBefore = ChunkloaderForgeMod.getChunkloaderManager().getActiveChunkloaderEntries();
        long fakeplayerCountBefore = entriesBefore.stream().filter(e -> e.allowMobSpawning()).count();
        long chunkloaderCountBefore = entriesBefore.size() - fakeplayerCountBefore;

        int count = ChunkloaderForgeMod.getChunkloaderManager().disableAllChunkloaders();

        String message;
        String disabledLabel = (count == 1 ? " Player disabled" : " Players disabled");
        String keyName = de.chunkloader.util.KeybindHelper.getDisabledChunkloadersKeyName();
        if (fakeplayerCountBefore > 0 && chunkloaderCountBefore > 0) {
            message = count + disabledLabel + " (mob spawning: " + fakeplayerCountBefore + ", chunks only: " + chunkloaderCountBefore + ") (Press " + keyName + " to open disabled list)";
        } else {
            message = count + disabledLabel + " (Press " + keyName + " to open disabled list)";
        }

        context.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.YELLOW), true);
        return count;
    }

    private static int removeAllFakePlayers(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        var config = ChunkloaderForgeMod.getConfig();
        if (config == null) {
            context.getSource().sendFailure(Component.literal("Config is not initialized"));
            return 0;
        }

        var entriesBefore = config.getChunkEntries();
        long fakeplayerCount = entriesBefore.stream().filter(e -> e.allowMobSpawning()).count();
        long chunkplayerCount = entriesBefore.size() - fakeplayerCount;

        int count = ChunkloaderForgeMod.getChunkloaderManager().clearAllChunkloaders();
        if (count > 0) {
            String message;
            String removedLabel = (count == 1 ? " Player removed" : " Players removed");
            if (fakeplayerCount > 0 && chunkplayerCount > 0) {
                message = count + removedLabel + " (mob spawning: " + fakeplayerCount + ", chunks only: " + chunkplayerCount + ")";
            } else {
                message = count + removedLabel;
            }
            context.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), true);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("No player found").withStyle(ChatFormatting.YELLOW), false);
        }
        return count;
    }

    private static int removeAllEnabledFakePlayers(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        var config = ChunkloaderForgeMod.getConfig();
        if (config == null) {
            context.getSource().sendFailure(Component.literal("Config is not initialized"));
            return 0;
        }

        var entriesBefore = config.getChunkEntries().stream()
            .filter(e -> e.enabled())
            .toList();
        long fakeplayerCount = entriesBefore.stream().filter(e -> e.allowMobSpawning()).count();
        long chunkplayerCount = entriesBefore.size() - fakeplayerCount;

        int count = ChunkloaderForgeMod.getChunkloaderManager().removeAllEnabledChunkloaders();
        if (count > 0) {
            String message;
            String removedLabel = (count == 1 ? " enabled player removed" : " enabled players removed");
            if (fakeplayerCount > 0 && chunkplayerCount > 0) {
                message = count + removedLabel + " (mob spawning: " + fakeplayerCount + ", chunks only: " + chunkplayerCount + ")";
            } else {
                message = count + removedLabel;
            }
            context.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), true);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("No enabled player found").withStyle(ChatFormatting.YELLOW), false);
        }
        return count;
    }

    private static int removeAllDisabledFakePlayers(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }

        var config = ChunkloaderForgeMod.getConfig();
        if (config == null) {
            context.getSource().sendFailure(Component.literal("Config is not initialized"));
            return 0;
        }

        var entriesBefore = config.getChunkEntries().stream()
            .filter(e -> !e.enabled())
            .toList();
        long fakeplayerCount = entriesBefore.stream().filter(e -> e.allowMobSpawning()).count();
        long chunkplayerCount = entriesBefore.size() - fakeplayerCount;

        int count = ChunkloaderForgeMod.getChunkloaderManager().removeAllDisabledChunkloaders();
        if (count > 0) {
            String message;
            String removedLabel = (count == 1 ? " disabled player removed" : " disabled players removed");
            if (fakeplayerCount > 0 && chunkplayerCount > 0) {
                message = count + removedLabel + " (mob spawning: " + fakeplayerCount + ", chunks only: " + chunkplayerCount + ")";
            } else {
                message = count + removedLabel;
            }
            context.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), true);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("No disabled player found").withStyle(ChatFormatting.YELLOW), false);
        }
        return count;
    }

    private static int toggleVisualization(CommandContext<CommandSourceStack> context) {
        return toggleVisualization(context, false, 0, 0);
    }

    private static int toggleVisualization3D(CommandContext<CommandSourceStack> context) {
        return toggleVisualization(context, true, ChunkloaderConstants.MIN_BLOCK_Y, ChunkloaderConstants.MAX_BLOCK_Y);
    }

    private static int toggleVisualization3DWithHeight(CommandContext<CommandSourceStack> context) {
        int minY = IntegerArgumentType.getInteger(context, "minY");
        int maxY = IntegerArgumentType.getInteger(context, "maxY");
        if (minY >= maxY) {
            context.getSource().sendFailure(Component.literal("minY must be less than maxY").withStyle(ChatFormatting.RED));
            return 0;
        }
        return toggleVisualization(context, true, minY, maxY);
    }

    private static int toggleVisualization(CommandContext<CommandSourceStack> context, boolean threeDimensional,
            int minY, int maxY) {
        ServerPlayer viewer = context.getSource().getPlayer();
        if (viewer == null) {
            context.getSource().sendFailure(Component.literal("Run this command in-game to see the visualization."));
            return 0;
        }
        String name = StringArgumentType.getString(context, "name");
        if (!canAccessNamed(context, name, true)) {
            return 0;
        }
        var manager = ChunkloaderForgeMod.getChunkloaderManager();
        var config = ChunkloaderForgeMod.getConfig();
        if (manager == null || config == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }
        ChunkloaderTarget entry = config.getEntryByName(name);
        if (entry == null || !entry.enabled()) {
            context.getSource().sendFailure(Component.literal("No active player named '" + name + "'."));
            return 0;
        }
        boolean enabled = threeDimensional
                ? manager.toggleVisualization3D(viewer, entry, minY, maxY)
                : manager.toggleVisualization(viewer, entry);
        ChunkloaderNetworking.refreshOpenChunkMap(viewer, manager);
        String kind = threeDimensional ? "3D chunk visualization" : "Chunk border visualization";
        String height = threeDimensional && enabled ? " (height: " + minY + " to " + maxY + ")" : "";
        context.getSource().sendSuccess(() -> Component.literal(kind + " for '" + entry.name() + "' "
                + (enabled ? "enabled" : "disabled") + height)
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return 1;
    }

    private static int assignOwner(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        String playerName = StringArgumentType.getString(context, "player");
        ChunkloaderManager manager = ChunkloaderForgeMod.getChunkloaderManager();
        if (manager == null || ChunkloaderForgeMod.getConfig() == null) {
            context.getSource().sendFailure(Component.literal("Player Manager is not initialized"));
            return 0;
        }
        if (ChunkloaderForgeMod.getConfig().getEntryByName(name) == null) {
            context.getSource().sendFailure(Component.literal("Player '" + name + "' not found"));
            return 0;
        }
        ServerPlayer owner = context.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        if (owner == null || owner instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer) {
            context.getSource().sendFailure(Component.literal("Owner must be an online player."));
            return 0;
        }
        if (!manager.assignChunkloaderOwner(name, owner)) {
            String limitMessage = manager.getOwnerAssignmentLimitMessage(name, owner);
            context.getSource().sendFailure(Component.literal(limitMessage != null ? limitMessage : "Failed to assign owner."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Owner of '" + name + "' set to '" + owner.getGameProfile().name() + "'.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int grantAllPermissions(CommandContext<CommandSourceStack> context) {
        try {
            String playerName = StringArgumentType.getString(context, "player");

            var permissionConfig = PermissionManager.getPermissionConfig();
            if (permissionConfig == null) {
                context.getSource().sendFailure(Component.literal("Permission system is not initialized").withStyle(ChatFormatting.RED));
                return 0;
            }

            var server = context.getSource().getServer();
            var player = server.getPlayerList().getPlayerByName(playerName);
            if (player == null) {
                context.getSource().sendFailure(Component.literal("Player '" + playerName + "' not found").withStyle(ChatFormatting.RED));
                return 0;
            }

            permissionConfig.grantPermission(player.getUUID(), PermissionManager.PERMISSION_USE);
            server.getCommands().sendCommands(player);
            ChunkloaderNetworking.refreshOpenChunkMapMarkers(server, ChunkloaderForgeMod.getChunkloaderManager());
            context.getSource().sendSuccess(() -> Component.literal("Granted mod access to " + playerName + ". They can create and manage their own players.")
                .withStyle(ChatFormatting.GREEN), true);

            player.sendSystemMessage(Component.literal("You can now create and manage your own Fakeplayers and Chunkplayers.")
                .withStyle(ChatFormatting.GREEN));

            return 1;
        } catch (Exception e) {

            context.getSource().sendFailure(Component.literal("An error occurred: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    private static int revokeAllPermissions(CommandContext<CommandSourceStack> context) {
        try {
            String playerName = StringArgumentType.getString(context, "player");

            var permissionConfig = PermissionManager.getPermissionConfig();
            if (permissionConfig == null) {
                context.getSource().sendFailure(Component.literal("Permission system is not initialized").withStyle(ChatFormatting.RED));
                return 0;
            }

            var server = context.getSource().getServer();
            var player = server.getPlayerList().getPlayerByName(playerName);
            if (player == null) {
                context.getSource().sendFailure(Component.literal("Player '" + playerName + "' not found").withStyle(ChatFormatting.RED));
                return 0;
            }

            permissionConfig.clearPlayerPermissions(player.getUUID());
            server.getCommands().sendCommands(player);
            ChunkloaderNetworking.refreshOpenChunkMapMarkers(server, ChunkloaderForgeMod.getChunkloaderManager());
            context.getSource().sendSuccess(() -> Component.literal("Revoked mod access from " + playerName)
                .withStyle(ChatFormatting.GREEN), true);

            player.sendSystemMessage(Component.literal("Your mod access has been revoked.")
                .withStyle(ChatFormatting.RED));

            return 1;
        } catch (Exception e) {

            context.getSource().sendFailure(Component.literal("An error occurred: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }
}

