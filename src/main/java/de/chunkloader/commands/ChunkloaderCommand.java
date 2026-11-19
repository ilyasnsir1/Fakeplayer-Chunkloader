package de.chunkloader.commands;

import de.chunkloader.network.ChunkloaderNetworking;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import de.chunkloader.ChunkloaderMod;
import de.chunkloader.ChunkloaderConstants;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.permissions.PermissionManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.List;

public class ChunkloaderCommand {

    private static boolean canModifyNamed(CommandContext<ServerCommandSource> context, String name) {
        return canAccessNamed(context, name, false);
    }

    private static boolean canAccessNamed(CommandContext<ServerCommandSource> context, String name, boolean allowShared) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null || PermissionManager.isAdmin(player)) {
            return true;
        }
        var config = ChunkloaderMod.getConfig();
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
        context.getSource().sendError(Text.literal("You don't own this player.").formatted(Formatting.RED));
        return false;
    }

    private static ChunkloaderTarget findSavedLoader(String name) {
        var config = ChunkloaderMod.getConfig();
        return config == null ? null : config.getEntryByName(name);
    }

    private static boolean canListEntry(ServerPlayerEntity player, ChunkloaderTarget entry) {
        return player == null || PermissionManager.canOperateEntry(player, entry);
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("fakeplayer")
            .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_USE))
            .then(CommandManager.literal("help").executes(ChunkloaderCommand::openHelp))
            .then(addCommand())
            .then(moveCommand())
            .then(CommandManager.literal("remove")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OWNED_LOADERS)
                    .executes(ChunkloaderCommand::removeFakePlayer)))
            .then(CommandManager.literal("list")
                .executes(ChunkloaderCommand::listFakePlayers))
            .then(CommandManager.literal("info")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .executes(ChunkloaderCommand::infoFakePlayer)))
            .then(CommandManager.literal("reload")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::reloadConfig))
            .then(CommandManager.literal("disable")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .executes(ChunkloaderCommand::disableFakePlayer)))
            .then(CommandManager.literal("stats")
                .executes(ChunkloaderCommand::statsFakePlayers))
            .then(CommandManager.literal("namevisible")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .then(CommandManager.argument("visible", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setNameVisible))))
            .then(CommandManager.literal("setradius")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .then(CommandManager.argument("radius", IntegerArgumentType.integer(ChunkloaderConstants.MIN_RADIUS, ChunkloaderConstants.MAX_RADIUS))
                        .executes(ChunkloaderCommand::setRadius))))
            .then(CommandManager.literal("setmobtarget")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setMobTarget))))
            .then(mobActivityCommand("pausemobs", false))
            .then(mobActivityCommand("resumemobs", true))
            .then(CommandManager.literal("toggle")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .executes(ChunkloaderCommand::toggleMobSpawning)))
            .then(CommandManager.literal("tablist")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(CommandManager.argument("visible", BoolArgumentType.bool())
                    .executes(ChunkloaderCommand::setTabListVisibleAll)))
            .then(CommandManager.literal("rename")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .then(CommandManager.argument("newName", StringArgumentType.string())
                        .executes(ChunkloaderCommand::renameChunkloader))))
            .then(CommandManager.literal("restore")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.DISABLED_LOADERS)
                    .executes(ChunkloaderCommand::restoreFakePlayer)))
            .then(CommandManager.literal("restoreall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::enableAllFakePlayers))
            .then(CommandManager.literal("disableall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::previewDisableAll)
                .then(CommandManager.literal("confirm")
                    .executes(ChunkloaderCommand::disableAllFakePlayers)))
            .then(CommandManager.literal("removeall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::previewRemoveAll)
                .then(CommandManager.literal("confirm")
                    .executes(ChunkloaderCommand::removeAllFakePlayers))
                .then(CommandManager.literal("enabled")
                    .executes(ChunkloaderCommand::previewRemoveEnabled)
                    .then(CommandManager.literal("confirm")
                        .executes(ChunkloaderCommand::removeAllEnabledFakePlayers)))
                .then(CommandManager.literal("disabled")
                    .executes(ChunkloaderCommand::previewRemoveDisabled)
                    .then(CommandManager.literal("confirm")
                        .executes(ChunkloaderCommand::removeAllDisabledFakePlayers))))
            .then(CommandManager.literal("visualize")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.ACTIVE_LOADERS)
                    .executes(ChunkloaderCommand::toggleVisualization)))
            .then(CommandManager.literal("visualize3d")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.ACTIVE_LOADERS)
                    .executes(ChunkloaderCommand::toggleVisualization3D)
                    .then(CommandManager.argument("minY", IntegerArgumentType.integer(-64, 320))
                        .then(CommandManager.argument("maxY", IntegerArgumentType.integer(-64, 320))
                            .executes(ChunkloaderCommand::toggleVisualization3DWithHeight)))))
            .then(ChunkloaderLimitCommands.createNode())
            .then(ChunkloaderSharingCommands.share())
            .then(ChunkloaderSharingCommands.unshare())
            .then(ChunkloaderSharingCommands.shares())
            .then(CommandManager.literal("owner")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OWNED_LOADERS)
                    .then(CommandManager.argument("player", StringArgumentType.string())
                        .suggests(ChunkloaderSuggestions.ONLINE_PLAYERS)
                        .executes(ChunkloaderCommand::assignOwner))))
            .then(CommandManager.literal("permission")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(CommandManager.literal("grant")
                    .then(CommandManager.argument("player", StringArgumentType.string())
                        .suggests(ChunkloaderSuggestions.ONLINE_PLAYERS)
                        .executes(ChunkloaderCommand::grantAllPermissions)))
                .then(CommandManager.literal("revoke")
                    .then(CommandManager.argument("player", StringArgumentType.string())
                        .suggests(ChunkloaderSuggestions.ONLINE_PLAYERS)
                        .executes(ChunkloaderCommand::revokeAllPermissions)))));

        var fpNode = CommandManager.literal("fp")
            .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_USE))
            .then(CommandManager.literal("help").executes(ChunkloaderCommand::openHelp))
            .then(addCommand())
            .then(moveCommand())
            .then(CommandManager.literal("remove")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OWNED_LOADERS)
                    .executes(ChunkloaderCommand::removeFakePlayer)))
            .then(CommandManager.literal("list")
                .executes(ChunkloaderCommand::listFakePlayers))
            .then(CommandManager.literal("info")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .executes(ChunkloaderCommand::infoFakePlayer)))
            .then(CommandManager.literal("reload")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::reloadConfig))
            .then(CommandManager.literal("disable")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .executes(ChunkloaderCommand::disableFakePlayer)))
            .then(CommandManager.literal("stats")
                .executes(ChunkloaderCommand::statsFakePlayers))
            .then(CommandManager.literal("namevisible")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .then(CommandManager.argument("visible", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setNameVisible))))
            .then(CommandManager.literal("setradius")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .then(CommandManager.argument("radius", IntegerArgumentType.integer(ChunkloaderConstants.MIN_RADIUS, ChunkloaderConstants.MAX_RADIUS))
                        .executes(ChunkloaderCommand::setRadius))))
            .then(CommandManager.literal("setmobtarget")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setMobTarget))))
            .then(mobActivityCommand("pausemobs", false))
            .then(mobActivityCommand("resumemobs", true))
            .then(CommandManager.literal("toggle")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .executes(ChunkloaderCommand::toggleMobSpawning)))
            .then(CommandManager.literal("tablist")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(CommandManager.argument("visible", BoolArgumentType.bool())
                    .executes(ChunkloaderCommand::setTabListVisibleAll)))
            .then(CommandManager.literal("rename")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                    .then(CommandManager.argument("newName", StringArgumentType.string())
                        .executes(ChunkloaderCommand::renameChunkloader))))
            .then(CommandManager.literal("restore")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.DISABLED_LOADERS)
                    .executes(ChunkloaderCommand::restoreFakePlayer)))
            .then(CommandManager.literal("restoreall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::enableAllFakePlayers))
            .then(CommandManager.literal("disableall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::previewDisableAll)
                .then(CommandManager.literal("confirm")
                    .executes(ChunkloaderCommand::disableAllFakePlayers)))
            .then(CommandManager.literal("removeall")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .executes(ChunkloaderCommand::previewRemoveAll)
                .then(CommandManager.literal("confirm")
                    .executes(ChunkloaderCommand::removeAllFakePlayers))
                .then(CommandManager.literal("enabled")
                    .executes(ChunkloaderCommand::previewRemoveEnabled)
                    .then(CommandManager.literal("confirm")
                        .executes(ChunkloaderCommand::removeAllEnabledFakePlayers)))
                .then(CommandManager.literal("disabled")
                    .executes(ChunkloaderCommand::previewRemoveDisabled)
                    .then(CommandManager.literal("confirm")
                        .executes(ChunkloaderCommand::removeAllDisabledFakePlayers))))
            .then(CommandManager.literal("visualize")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.ACTIVE_LOADERS)
                    .executes(ChunkloaderCommand::toggleVisualization)))
            .then(CommandManager.literal("visualize3d")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.ACTIVE_LOADERS)
                    .executes(ChunkloaderCommand::toggleVisualization3D)
                    .then(CommandManager.argument("minY", IntegerArgumentType.integer(-64, 320))
                        .then(CommandManager.argument("maxY", IntegerArgumentType.integer(-64, 320))
                            .executes(ChunkloaderCommand::toggleVisualization3DWithHeight)))))
            .then(ChunkloaderLimitCommands.createNode())
            .then(ChunkloaderSharingCommands.share())
            .then(ChunkloaderSharingCommands.unshare())
            .then(ChunkloaderSharingCommands.shares())
            .then(CommandManager.literal("owner")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .suggests(ChunkloaderSuggestions.OWNED_LOADERS)
                    .then(CommandManager.argument("player", StringArgumentType.string())
                        .suggests(ChunkloaderSuggestions.ONLINE_PLAYERS)
                        .executes(ChunkloaderCommand::assignOwner))))
            .then(CommandManager.literal("permission")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(CommandManager.literal("grant")
                    .then(CommandManager.argument("player", StringArgumentType.string())
                        .suggests(ChunkloaderSuggestions.ONLINE_PLAYERS)
                        .executes(ChunkloaderCommand::grantAllPermissions)))
                .then(CommandManager.literal("revoke")
                    .then(CommandManager.argument("player", StringArgumentType.string())
                        .suggests(ChunkloaderSuggestions.ONLINE_PLAYERS)
                        .executes(ChunkloaderCommand::revokeAllPermissions))));

        dispatcher.register(fpNode);
    }

    private static int openHelp(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFeedback(() -> Text.literal(
                    "Run /fp help in-game to open the guide. Use /fp add <x> <y> <z> to place a Fakeplayer from the console."), false);
        } else if (!ChunkloaderNetworking.tryOpenHelp(player)) {
            context.getSource().sendFeedback(() -> Text.literal(
                    "Use /fp add to create a Fakeplayer where you stand. With the client mod, right-click it to open its map."), false);
        }
        return 1;
    }

    private static int addFakePlayer(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        return createFakePlayer(context, player, player.getBlockPos(), (net.minecraft.server.world.ServerWorld) player.getEntityWorld(), MathHelper.wrapDegrees(player.getYaw()));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> addCommand() {
        return CommandManager.literal("add")
                .executes(ChunkloaderCommand::addFakePlayer)
                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                        .executes(context -> addFakePlayerAt(context, context.getSource().getWorld()))
                        .then(CommandManager.argument("dimension", DimensionArgumentType.dimension())
                                .executes(context -> addFakePlayerAt(context, DimensionArgumentType.getDimensionArgument(context, "dimension")))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> moveCommand() {
        return CommandManager.literal("move")
                .then(CommandManager.argument("name", StringArgumentType.string())
                        .suggests(ChunkloaderSuggestions.OPERABLE_LOADERS)
                        .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                .executes(context -> movePlayer(context, null))
                                .then(CommandManager.argument("dimension", DimensionArgumentType.dimension())
                                        .executes(context -> movePlayer(context, DimensionArgumentType.getDimensionArgument(context, "dimension"))))));
    }

    private static int addFakePlayerAt(CommandContext<ServerCommandSource> context, ServerWorld world) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        BlockPos position = BlockPosArgumentType.getBlockPos(context, "pos");
        return createFakePlayer(context, player, position, world, player != null ? player.getYaw() : context.getSource().getRotation().y);
    }

    private static int createFakePlayer(CommandContext<ServerCommandSource> context, ServerPlayerEntity player,
            BlockPos pos, ServerWorld world, float yaw) {
        try {
            ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
            if (manager == null) {
                context.getSource().sendError(Text.literal("Player Manager is not initialized"));
                return 0;
            }
            if (world == null) {
                context.getSource().sendError(Text.literal("Level is not available"));
                return 0;
            }
            if (!world.isInBuildLimit(pos) || pos.getY() < ChunkloaderConstants.MIN_BLOCK_Y
                    || pos.getY() > ChunkloaderConstants.MAX_BLOCK_Y) {
                context.getSource().sendError(Text.literal("Position is outside the world.").formatted(Formatting.RED));
                return 0;
            }
            var config = ChunkloaderMod.getConfig();
            String owner = player != null ? player.getGameProfile().name() : null;
            var ownerUuid = player != null ? player.getUuid() : null;
            String limitMessage = config != null ? config.getActivationLimitMessage(ownerUuid, owner) : null;
            if (limitMessage != null) {
                context.getSource().sendError(Text.literal(limitMessage));
                return 0;
            }
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            String dimension = ChunkloaderManager.getDimensionString(world);
            String overlappingName = manager.getOverlappingChunkloaderName(chunkX, chunkZ, 0, dimension, null);
            if (overlappingName != null) {
                context.getSource().sendError(Text.literal(
                        "Cannot add player: Position is already covered by a player '" + overlappingName + "'").formatted(Formatting.RED));
                return 0;
            }
            if (!manager.addChunkloader(chunkX, chunkZ, pos, null, world, owner, yaw, ownerUuid)) {
                String failureLimit = config != null ? config.getActivationLimitMessage(ownerUuid, owner) : null;
                context.getSource().sendError(Text.literal(failureLimit != null ? failureLimit : "Failed to create player")
                        .formatted(Formatting.RED));
                return 0;
            }

            ChunkloaderTarget entry = config != null ? config.getEntry(chunkX, chunkZ, dimension) : null;
            if (entry != null && entry.easterEggSkinIndex() == null) {
                String name = entry.name() != null ? entry.name() : "Unnamed";
                context.getSource().sendFeedback(() -> Text.literal("Player '" + name + "' created at position ("
                        + entry.blockX() + ", " + entry.blockY() + ", " + entry.blockZ() + ") in " + entry.dimension())
                        .formatted(Formatting.GREEN), true);
                if (player != null && (net.minecraft.server.world.ServerWorld) player.getEntityWorld() == world) {
                    context.getSource().sendFeedback(() -> Text.literal("Right-click the player to open the map."), false);
                }
            }
            return 1;
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error in addFakePlayer command", e);
            context.getSource().sendError(Text.literal("An error occurred: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
    }

    private static LiteralArgumentBuilder<ServerCommandSource> mobActivityCommand(String command, boolean enabled) {
        return CommandManager.literal(command)
                .then(CommandManager.argument("name", StringArgumentType.string())
                        .suggests(ChunkloaderSuggestions.OPERABLE_CHUNKPLAYERS)
                        .executes(context -> setMobAi(context, enabled)));
    }

    private static int setMobAi(CommandContext<ServerCommandSource> context, boolean enabled) {
        String name = StringArgumentType.getString(context, "name");
        if (!canAccessNamed(context, name, true)) {
            return 0;
        }
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }
        ChunkloaderTarget entry = ChunkloaderMod.getConfig() != null ? ChunkloaderMod.getConfig().getEntryByName(name) : null;
        if (entry == null) {
            context.getSource().sendError(Text.literal("Player '" + name + "' not found").formatted(Formatting.RED));
            return 0;
        }
        if (entry.allowMobSpawning()) {
            context.getSource().sendError(Text.literal("Only Chunkplayers can pause or resume mobs.").formatted(Formatting.RED));
            return 0;
        }
        if (!ChunkloaderMod.getChunkloaderManager().setChunkloaderMobAi(name, enabled)) {
            context.getSource().sendError(Text.literal("Could not update mob activity.").formatted(Formatting.RED));
            return 0;
        }
        ChunkloaderNetworking.refreshOpenChunkMapMarkers(context.getSource().getServer(),
                ChunkloaderMod.getChunkloaderManager());
        context.getSource().sendFeedback(() -> Text.literal((enabled ? "Resumed mobs for '" : "Paused mobs for '") + entry.name() + "'.").formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int movePlayer(CommandContext<ServerCommandSource> context, ServerWorld targetWorld) {
        String name = StringArgumentType.getString(context, "name");
        if (!canAccessNamed(context, name, true)) {
            return 0;
        }

        ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = findSavedLoader(name);
        if (entry == null) {
            context.getSource().sendError(Text.literal("Player '" + name + "' not found."));
            return 0;
        }

        BlockPos pos = BlockPosArgumentType.getBlockPos(context, "pos");
        String dimension = targetWorld == null ? entry.dimension() : ChunkloaderManager.getDimensionString(targetWorld);
        String failure = manager.updateDisabledChunkloaderCoordsWithMessage(
                entry.chunkX(), entry.chunkZ(), entry.dimension(), pos.getX() >> 4, pos.getZ() >> 4,
                pos.getX(), pos.getY(), pos.getZ(), dimension);
        if (failure != null) {
            context.getSource().sendError(Text.literal(failure));
            return 0;
        }

        ChunkloaderNetworking.refreshOpenChunkMapMarkers(context.getSource().getServer(), manager);
        context.getSource().sendFeedback(() -> Text.literal("Moved '" + entry.name() + "' to ("
                + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ") in " + dimension).formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int removeFakePlayer(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!canModifyNamed(context, name)) {
            return 0;
        }

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        boolean success = ChunkloaderMod.getChunkloaderManager().removeChunkloaderByName(name);

        if (success) {
            context.getSource().sendFeedback(() -> Text.literal("Player '" + name + "' removed").formatted(Formatting.GREEN), true);
            return 1;
        } else {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Player '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }
    }

    private static int listFakePlayers(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        var fakePlayers = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(entry -> canListEntry(context.getSource().getPlayer(), entry))
            .toList();

        if (fakePlayers.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("No saved players"), false);
        } else {
            int count = fakePlayers.size();
            context.getSource().sendFeedback(() -> Text.literal("Saved " + playerNoun(count) + " (" + count + "):"), false);
            for (var entry : fakePlayers) {
                String name = entry.name() != null ? entry.name() : "Unnamed";
                context.getSource().sendFeedback(() -> Text.literal("  - " + name + (entry.enabled() ? " (active)" : " (disabled)") + " @ Block (" +
                    entry.blockX() + ", " + entry.blockY() + ", " + entry.blockZ() + ") in " + entry.dimension()), false);
            }
        }

        return fakePlayers.size();
    }

    private static int infoFakePlayer(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!canAccessNamed(context, name, true)) {
            return 0;
        }

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = findSavedLoader(name);

        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Player '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }

        context.getSource().sendFeedback(() -> Text.literal("=== Player Info ===").formatted(Formatting.GOLD), false);
        context.getSource().sendFeedback(() -> Text.literal("Name: " + entry.name()).formatted(Formatting.YELLOW), false);
        context.getSource().sendFeedback(() -> Text.literal("Status: " + (entry.enabled() ? "Active" : "Inactive"))
            .formatted(entry.enabled() ? Formatting.GREEN : Formatting.RED), false);
        String mode = entry.allowMobSpawning() ? "Fakeplayer" : "Chunkplayer";
        Formatting modeColor = entry.allowMobSpawning() ? Formatting.GREEN : Formatting.BLUE;
        context.getSource().sendFeedback(() -> Text.literal("Mode: " + mode).formatted(modeColor), false);
        String mobSetting = entry.allowMobSpawning()
                ? "Mob target: " + (entry.mobTarget() ? "Enabled" : "Disabled")
                : "Mob activity: " + (entry.mobAi() ? "Enabled" : "Paused");
        context.getSource().sendFeedback(() -> Text.literal(mobSetting), false);
        context.getSource().sendFeedback(() -> Text.literal("Dimension: " + entry.dimension()), false);
        context.getSource().sendFeedback(() -> Text.literal("Chunk: (" + entry.chunkX() + ", " + entry.chunkZ() + ")"), false);
        context.getSource().sendFeedback(() -> Text.literal("Block: (" + entry.blockX() + ", " + entry.blockY() + ", " + entry.blockZ() + ")"), false);
        context.getSource().sendFeedback(() -> Text.literal("Name visible: " + (entry.nameVisible() ? "Yes" : "No")), false);
        context.getSource().sendFeedback(() -> Text.literal(describeRadius(entry.chunkRadius())), false);

        return 1;
    }

    private static int reloadConfig(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderMod.getChunkloaderManager().reloadConfig();
        context.getSource().sendFeedback(() -> Text.literal("Config reloaded").formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int disableFakePlayer(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!canAccessNamed(context, name, true)) {
            return 0;
        }

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget existingEntry = findSavedLoader(name);

        if (existingEntry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Player '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }

        if (!existingEntry.enabled()) {
            context.getSource().sendFeedback(() -> Text.literal("Player '" + name + "' is already disabled")
                    .formatted(Formatting.YELLOW), false);
            return 0;
        }

        ChunkloaderMod.getChunkloaderManager().toggleChunkloaderByName(name);
        ChunkloaderTarget updatedEntry = findSavedLoader(name);
        if (updatedEntry == null || updatedEntry.enabled()) {
            context.getSource().sendError(Text.literal("Failed to disable player '" + name + "'")
                    .formatted(Formatting.RED));
            return 0;
        }

        String keyName = de.chunkloader.util.KeybindHelper.getDisabledChunkloadersKeyName();
        context.getSource().sendFeedback(() -> Text.literal(
                "Player '" + name + "' disabled (Press " + keyName + " to open disabled list)").formatted(Formatting.RED), true);
        return 1;
    }

    private static int statsFakePlayers(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderManager.ChunkloaderStats stats = ChunkloaderMod.getChunkloaderManager().getStats();
        ChunkloaderManager.ChunkloaderPerformanceStats perfStats = ChunkloaderMod.getChunkloaderManager().getPerformanceStats();

        context.getSource().sendFeedback(() -> Text.literal("=== Player Statistics ===").formatted(Formatting.GOLD), false);
        context.getSource().sendFeedback(() -> Text.literal("Total: " + stats.total()).formatted(Formatting.AQUA), false);
        context.getSource().sendFeedback(() -> Text.literal("Active players: " + stats.enabled()).formatted(Formatting.GREEN), false);
        context.getSource().sendFeedback(() -> Text.literal("Inactive: " + stats.disabled()).formatted(Formatting.RED), false);
        context.getSource().sendFeedback(() -> Text.literal("Selected chunks: " + stats.coreChunks()), false);

        long usedMB = perfStats.usedMemory() / (1024 * 1024);
        long maxMB = perfStats.maxMemory() / (1024 * 1024);
        Formatting memoryColor = perfStats.memoryUsagePercent() > 80 ? Formatting.RED : perfStats.memoryUsagePercent() > 60 ? Formatting.YELLOW : Formatting.GREEN;

        context.getSource().sendFeedback(() -> Text.literal("Memory used: " + usedMB + " MB / " + maxMB + " MB (" + String.format("%.1f", perfStats.memoryUsagePercent()) + "%)")
            .formatted(memoryColor), false);

        if (perfStats.memoryUsagePercent() > 85) {
            context.getSource().sendFeedback(() -> Text.literal("⚠ Warning: High memory usage!").formatted(Formatting.RED), false);
        }

        if (stats.chunksWithRings() > 1000) {
            context.getSource().sendFeedback(() -> Text.literal("⚠ Note: Many loaded chunks may impact performance").formatted(Formatting.YELLOW), false);
        }

        return 1;
    }

    private static int setNameVisible(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!canAccessNamed(context, name, true)) {
            return 0;
        }
        boolean visible = BoolArgumentType.getBool(context, "visible");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = findSavedLoader(name);

        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Player '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }

        boolean success = ChunkloaderMod.getChunkloaderManager().setChunkloaderNameVisible(name, visible);

        if (success) {
            ChunkloaderNetworking.refreshOpenChunkMapMarkers(context.getSource().getServer(),
                    ChunkloaderMod.getChunkloaderManager());
            context.getSource().sendFeedback(() -> Text.literal("Name visibility for '" + name + "' set to " + (visible ? "visible" : "invisible"))
                .formatted(Formatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendError(Text.literal("Error setting name visibility").formatted(Formatting.RED));
            return 0;
        }
    }

    private static int setRadius(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!canAccessNamed(context, name, true)) {
            return 0;
        }
        int radius = IntegerArgumentType.getInteger(context, "radius");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        if (radius < ChunkloaderConstants.MIN_RADIUS || radius > ChunkloaderConstants.MAX_RADIUS) {
            context.getSource().sendError(Text.literal("Invalid radius: " + radius + " (must be between " + ChunkloaderConstants.MIN_RADIUS + " and " + ChunkloaderConstants.MAX_RADIUS + ")").formatted(Formatting.RED));
            return 0;
        }

        ChunkloaderTarget entry = findSavedLoader(name);

        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Player '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }

        String error = ChunkloaderMod.getChunkloaderManager().setChunkloaderRadiusWithMessage(name, radius);
        if (error != null) {
            context.getSource().sendError(Text.literal(error).formatted(Formatting.RED));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Player '" + name + "': " + describeRadius(radius))
                .formatted(Formatting.GREEN), true);
        return 1;
    }

    private static String describeRadius(int radius) {
        int width = 2 * radius + 1;
        int selectedChunks = width * width;
        return "SD: " + radius + " (" + selectedChunks + " selected "
                + (selectedChunks == 1 ? "chunk" : "chunks") + ")";
    }

    private static int setMobTarget(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!canAccessNamed(context, name, true)) {
            return 0;
        }
        boolean enabled = BoolArgumentType.getBool(context, "enabled");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = findSavedLoader(name);

        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Player '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }

        if (!entry.allowMobSpawning()) {
            context.getSource().sendError(Text.literal("Mob target is only available for Fakeplayers").formatted(Formatting.RED));
            return 0;
        }

        boolean success = ChunkloaderMod.getChunkloaderManager().setChunkloaderMobTarget(name, enabled);

        if (success) {
            ChunkloaderNetworking.refreshOpenChunkMapMarkers(context.getSource().getServer(),
                    ChunkloaderMod.getChunkloaderManager());
            context.getSource().sendFeedback(() -> Text.literal("Mob target for '" + name + "' set to " + (enabled ? "enabled" : "disabled"))
                .formatted(Formatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendError(Text.literal("Error setting mob target").formatted(Formatting.RED));
            return 0;
        }
    }

    private static int toggleMobSpawning(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!canAccessNamed(context, name, true)) {
            return 0;
        }

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = findSavedLoader(name);

        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Player '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }

        boolean allow = !entry.allowMobSpawning();
        boolean success = ChunkloaderMod.getChunkloaderManager().setChunkloaderAllowMobSpawning(name, allow);

        if (success) {
            String mode = allow ? "Fakeplayer" : "Chunkplayer";
            context.getSource().sendFeedback(() -> Text.literal("Mode for '" + name + "' set to " + mode)
                .formatted(Formatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendError(Text.literal("Toggle failed: rename the player first to avoid a name conflict.").formatted(Formatting.RED));
            return 0;
        }
    }

    private static int setTabListVisibleAll(CommandContext<ServerCommandSource> context) {
        boolean visible = BoolArgumentType.getBool(context, "visible");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        if (ChunkloaderMod.getChunkloaderManager().isTabListVisibleAll() == visible) {
            context.getSource().sendFeedback(
                () -> Text.literal("Tab list visibility is already set to " + visible).formatted(Formatting.YELLOW),
                false
            );
            return 1;
        }

        int changed = ChunkloaderMod.getChunkloaderManager().setTabListVisibleAll(visible);
        if (changed > 0) {
            context.getSource().sendFeedback(
                () -> Text.literal("Tab list visibility set to " + visible + " for " + changed + " " + playerNoun(changed))
                    .formatted(Formatting.GREEN),
                true
            );
            return 1;
        }
        context.getSource().sendError(Text.literal("No players found").formatted(Formatting.RED));
        return 0;
    }

    private static int renameChunkloader(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!canAccessNamed(context, name, true)) {
            return 0;
        }
        final String newName = StringArgumentType.getString(context, "newName");

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = findSavedLoader(name);

        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Player '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }

        var server = context.getSource().getServer();
        boolean isRealPlayerName = false;
        for (var world : server.getWorlds()) {
            for (var player : world.getPlayers()) {
                if (!(player instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer) &&
                    newName.equalsIgnoreCase(player.getName().getString())) {
                    isRealPlayerName = true;
                    break;
                }
            }
            if (isRealPlayerName) break;
        }

        if (isRealPlayerName) {
            context.getSource().sendError(Text.literal("Cannot rename to '" + newName + "'. This name is already used by a real player.").formatted(Formatting.RED));
            return 0;
        }

        boolean success = ChunkloaderMod.getChunkloaderManager().renameChunkloader(entry.chunkX(), entry.chunkZ(), entry.dimension(), newName);

        if (success) {
            context.getSource().sendFeedback(() -> Text.literal("Renamed '" + name + "' to '" + newName + "'")
                .formatted(Formatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendError(Text.literal("Failed to rename player. Name may already be in use or invalid.").formatted(Formatting.RED));
            return 0;
        }
    }

    private static int restoreFakePlayer(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        if (!canAccessNamed(context, name, true)) {
            return 0;
        }

        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        var config = ChunkloaderMod.getConfig();
        ChunkloaderTarget entry = config != null ? config.getEntryByName(name) : null;
        if (entry == null) {
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Player '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Player '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }

        if (entry.enabled()) {
            context.getSource().sendFeedback(() -> Text.literal("Player '" + name + "' is already enabled")
                .formatted(Formatting.YELLOW), false);
            return 0;
        }

        String limitMessage = ChunkloaderMod.getChunkloaderManager().getActivationLimitMessage(entry);
        if (limitMessage != null) {
            context.getSource().sendError(Text.literal(limitMessage));
            return 0;
        }
        boolean restored = ChunkloaderMod.getChunkloaderManager().toggleChunkloaderByName(name);
        if (restored) {
            context.getSource().sendFeedback(() -> Text.literal("Player '" + name + "' restored")
                .formatted(Formatting.GREEN), true);
            return 1;
        }

        context.getSource().sendError(Text.literal("Failed to restore player '" + name + "'")
            .formatted(Formatting.RED));
        return 0;
    }

    private static int enableAllFakePlayers(CommandContext<ServerCommandSource> context) {
        var manager = ChunkloaderMod.getChunkloaderManager();
        var config = ChunkloaderMod.getConfig();
        if (manager == null || config == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }
        List<ChunkloaderTarget> disabled = config.getChunkEntries().stream()
                .filter(entry -> !entry.enabled()).toList();
        if (disabled.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("No disabled player found")
                    .formatted(Formatting.YELLOW), false);
            return 0;
        }

        int count = manager.enableAllChunkloaders();
        if (count > 0) {
            String message = count + (count == 1 ? " Player restored" : " Players restored");
            context.getSource().sendFeedback(() -> Text.literal(message).formatted(Formatting.GREEN), true);
        }
        List<ChunkloaderTarget> remaining = disabled.stream()
                .map(entry -> config.getEntry(entry.chunkX(), entry.chunkZ(), entry.dimension()))
                .filter(entry -> entry != null && !entry.enabled()).toList();
        long limited = remaining.stream().filter(entry -> manager.getActivationLimitMessage(entry) != null).count();
        if (limited > 0) {
            String message = limited + (limited == 1 ? " Player remains" : " Players remain")
                    + " disabled because a player limit was reached.";
            context.getSource().sendError(Text.literal(message));
        }
        long failed = remaining.size() - limited;
        if (failed > 0) {
            context.getSource().sendError(Text.literal(failed
                    + (failed == 1 ? " Player could not be restored." : " Players could not be restored.")));
        }
        return count;
    }

    private static String playerNoun(int count) {
        return count == 1 ? "player" : "players";
    }

    private static int previewDisableAll(CommandContext<ServerCommandSource> context) {
        var manager = ChunkloaderMod.getChunkloaderManager();
        int count = manager == null ? 0 : (int) manager.getActiveChunkloaderEntries().stream().filter(ChunkloaderTarget::enabled).count();
        context.getSource().sendError(Text.literal(
            "This will disable " + count + " " + playerNoun(count) + ". Run /fp disableall confirm."));
        return 0;
    }

    private static int previewRemoveAll(CommandContext<ServerCommandSource> context) {
        var config = ChunkloaderMod.getConfig();
        int count = config == null ? 0 : config.getChunkEntries().size();
        context.getSource().sendError(Text.literal(
            "This will remove " + count + " " + playerNoun(count) + ". Run /fp removeall confirm."));
        return 0;
    }

    private static int previewRemoveEnabled(CommandContext<ServerCommandSource> context) {
        var config = ChunkloaderMod.getConfig();
        int count = config == null ? 0 : (int) config.getChunkEntries().stream().filter(ChunkloaderTarget::enabled).count();
        context.getSource().sendError(Text.literal(
            "This will remove " + count + " enabled " + playerNoun(count) + ". Run /fp removeall enabled confirm."));
        return 0;
    }

    private static int previewRemoveDisabled(CommandContext<ServerCommandSource> context) {
        var config = ChunkloaderMod.getConfig();
        int count = config == null ? 0 : (int) config.getChunkEntries().stream().filter(entry -> !entry.enabled()).count();
        context.getSource().sendError(Text.literal(
            "This will remove " + count + " disabled " + playerNoun(count) + ". Run /fp removeall disabled confirm."));
        return 0;
    }

    private static int disableAllFakePlayers(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        var entriesBefore = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries();
        long fakeplayerCountBefore = entriesBefore.stream().filter(e -> e.allowMobSpawning()).count();
        long chunkloaderCountBefore = entriesBefore.size() - fakeplayerCountBefore;

        int count = ChunkloaderMod.getChunkloaderManager().disableAllChunkloaders();

        String message;
        String disabledLabel = (count == 1 ? " Player disabled" : " Players disabled");
        String keyName = de.chunkloader.util.KeybindHelper.getDisabledChunkloadersKeyName();
        if (fakeplayerCountBefore > 0 && chunkloaderCountBefore > 0) {
            message = count + disabledLabel + " (mob spawning: " + fakeplayerCountBefore + ", chunks only: " + chunkloaderCountBefore + ") (Press " + keyName + " to open disabled list)";
        } else {
            message = count + disabledLabel + " (Press " + keyName + " to open disabled list)";
        }

        context.getSource().sendFeedback(() -> Text.literal(message).formatted(Formatting.YELLOW), true);
        return count;
    }

    private static int removeAllFakePlayers(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        var config = ChunkloaderMod.getConfig();
        if (config == null) {
            context.getSource().sendError(Text.literal("Config is not initialized"));
            return 0;
        }

        var entriesBefore = config.getChunkEntries();
        long fakeplayerCount = entriesBefore.stream().filter(e -> e.allowMobSpawning()).count();
        long chunkplayerCount = entriesBefore.size() - fakeplayerCount;

        int count = ChunkloaderMod.getChunkloaderManager().clearAllChunkloaders();
        if (count > 0) {
            String message;
            String removedLabel = (count == 1 ? " Player removed" : " Players removed");
            if (fakeplayerCount > 0 && chunkplayerCount > 0) {
                message = count + removedLabel + " (mob spawning: " + fakeplayerCount + ", chunks only: " + chunkplayerCount + ")";
            } else {
                message = count + removedLabel;
            }
            context.getSource().sendFeedback(() -> Text.literal(message).formatted(Formatting.GREEN), true);
        } else {
            context.getSource().sendFeedback(() -> Text.literal("No player found").formatted(Formatting.YELLOW), false);
        }
        return count;
    }

    private static int removeAllEnabledFakePlayers(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        var config = ChunkloaderMod.getConfig();
        if (config == null) {
            context.getSource().sendError(Text.literal("Config is not initialized"));
            return 0;
        }

        var entriesBefore = config.getChunkEntries().stream()
            .filter(e -> e.enabled())
            .toList();
        long fakeplayerCount = entriesBefore.stream().filter(e -> e.allowMobSpawning()).count();
        long chunkplayerCount = entriesBefore.size() - fakeplayerCount;

        int count = ChunkloaderMod.getChunkloaderManager().removeAllEnabledChunkloaders();
        if (count > 0) {
            String message;
            String removedLabel = (count == 1 ? " enabled player removed" : " enabled players removed");
            if (fakeplayerCount > 0 && chunkplayerCount > 0) {
                message = count + removedLabel + " (mob spawning: " + fakeplayerCount + ", chunks only: " + chunkplayerCount + ")";
            } else {
                message = count + removedLabel;
            }
            context.getSource().sendFeedback(() -> Text.literal(message).formatted(Formatting.GREEN), true);
        } else {
            context.getSource().sendFeedback(() -> Text.literal("No enabled player found").formatted(Formatting.YELLOW), false);
        }
        return count;
    }

    private static int removeAllDisabledFakePlayers(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }

        var config = ChunkloaderMod.getConfig();
        if (config == null) {
            context.getSource().sendError(Text.literal("Config is not initialized"));
            return 0;
        }

        var entriesBefore = config.getChunkEntries().stream()
            .filter(e -> !e.enabled())
            .toList();
        long fakeplayerCount = entriesBefore.stream().filter(e -> e.allowMobSpawning()).count();
        long chunkplayerCount = entriesBefore.size() - fakeplayerCount;

        int count = ChunkloaderMod.getChunkloaderManager().removeAllDisabledChunkloaders();
        if (count > 0) {
            String message;
            String removedLabel = (count == 1 ? " disabled player removed" : " disabled players removed");
            if (fakeplayerCount > 0 && chunkplayerCount > 0) {
                message = count + removedLabel + " (mob spawning: " + fakeplayerCount + ", chunks only: " + chunkplayerCount + ")";
            } else {
                message = count + removedLabel;
            }
            context.getSource().sendFeedback(() -> Text.literal(message).formatted(Formatting.GREEN), true);
        } else {
            context.getSource().sendFeedback(() -> Text.literal("No disabled player found").formatted(Formatting.YELLOW), false);
        }
        return count;
    }

    private static int toggleVisualization(CommandContext<ServerCommandSource> context) {
        return toggleVisualization(context, false, 0, 0);
    }

    private static int toggleVisualization3D(CommandContext<ServerCommandSource> context) {
        return toggleVisualization(context, true, ChunkloaderConstants.MIN_BLOCK_Y, ChunkloaderConstants.MAX_BLOCK_Y);
    }

    private static int toggleVisualization3DWithHeight(CommandContext<ServerCommandSource> context) {
        int minY = IntegerArgumentType.getInteger(context, "minY");
        int maxY = IntegerArgumentType.getInteger(context, "maxY");
        if (minY >= maxY) {
            context.getSource().sendError(Text.literal("minY must be less than maxY").formatted(Formatting.RED));
            return 0;
        }
        return toggleVisualization(context, true, minY, maxY);
    }

    private static int toggleVisualization(CommandContext<ServerCommandSource> context, boolean threeDimensional,
            int minY, int maxY) {
        ServerPlayerEntity viewer = context.getSource().getPlayer();
        if (viewer == null) {
            context.getSource().sendError(Text.literal("Run this command in-game to see the visualization."));
            return 0;
        }
        String name = StringArgumentType.getString(context, "name");
        if (!canAccessNamed(context, name, true)) {
            return 0;
        }
        var manager = ChunkloaderMod.getChunkloaderManager();
        var config = ChunkloaderMod.getConfig();
        if (manager == null || config == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }
        ChunkloaderTarget entry = config.getEntryByName(name);
        if (entry == null || !entry.enabled()) {
            context.getSource().sendError(Text.literal("No active player named '" + name + "'."));
            return 0;
        }
        boolean enabled = threeDimensional
                ? manager.toggleVisualization3D(viewer, entry, minY, maxY)
                : manager.toggleVisualization(viewer, entry);
        ChunkloaderNetworking.refreshOpenChunkMap(viewer, manager);
        String kind = threeDimensional ? "3D chunk visualization" : "Chunk border visualization";
        String height = threeDimensional && enabled ? " (height: " + minY + " to " + maxY + ")" : "";
        context.getSource().sendFeedback(() -> Text.literal(kind + " for '" + entry.name() + "' "
                + (enabled ? "enabled" : "disabled") + height)
                .formatted(enabled ? Formatting.GREEN : Formatting.RED), false);
        return 1;
    }

    private static int assignOwner(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        String playerName = StringArgumentType.getString(context, "player");
        ChunkloaderManager manager = ChunkloaderMod.getChunkloaderManager();
        if (manager == null || ChunkloaderMod.getConfig() == null) {
            context.getSource().sendError(Text.literal("Player Manager is not initialized"));
            return 0;
        }
        if (ChunkloaderMod.getConfig().getEntryByName(name) == null) {
            context.getSource().sendError(Text.literal("Player '" + name + "' not found"));
            return 0;
        }
        ServerPlayerEntity owner = context.getSource().getServer().getPlayerManager().getPlayer(playerName);
        if (owner == null || owner instanceof de.chunkloader.fakeplayer.ChunkloaderFakePlayer) {
            context.getSource().sendError(Text.literal("Owner must be an online player."));
            return 0;
        }
        if (!manager.assignChunkloaderOwner(name, owner)) {
            String limitMessage = manager.getOwnerAssignmentLimitMessage(name, owner);
            context.getSource().sendError(Text.literal(limitMessage != null ? limitMessage : "Failed to assign owner."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Owner of '" + name + "' set to '" + owner.getGameProfile().name() + "'.")
                .formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int grantAllPermissions(CommandContext<ServerCommandSource> context) {
        try {
            String playerName = StringArgumentType.getString(context, "player");

            var permissionConfig = PermissionManager.getPermissionConfig();
            if (permissionConfig == null) {
                context.getSource().sendError(Text.literal("Permission system is not initialized").formatted(Formatting.RED));
                return 0;
            }

            var server = context.getSource().getServer();
            var player = server.getPlayerManager().getPlayer(playerName);
            if (player == null) {
                context.getSource().sendError(Text.literal("Player '" + playerName + "' not found").formatted(Formatting.RED));
                return 0;
            }

            permissionConfig.grantPermission(player.getUuid(), PermissionManager.PERMISSION_USE);
            server.getCommandManager().sendCommandTree(player);
            ChunkloaderNetworking.refreshOpenChunkMapMarkers(server, ChunkloaderMod.getChunkloaderManager());
            context.getSource().sendFeedback(() -> Text.literal("Granted mod access to " + playerName + ". They can create and manage their own players.")
                .formatted(Formatting.GREEN), true);

            player.sendMessage(Text.literal("You can now create and manage your own Fakeplayers and Chunkplayers.")
                .formatted(Formatting.GREEN), false);

            return 1;
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error in grantAllPermissions command", e);
            context.getSource().sendError(Text.literal("An error occurred: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
    }

    private static int revokeAllPermissions(CommandContext<ServerCommandSource> context) {
        try {
            String playerName = StringArgumentType.getString(context, "player");

            var permissionConfig = PermissionManager.getPermissionConfig();
            if (permissionConfig == null) {
                context.getSource().sendError(Text.literal("Permission system is not initialized").formatted(Formatting.RED));
                return 0;
            }

            var server = context.getSource().getServer();
            var player = server.getPlayerManager().getPlayer(playerName);
            if (player == null) {
                context.getSource().sendError(Text.literal("Player '" + playerName + "' not found").formatted(Formatting.RED));
                return 0;
            }

            permissionConfig.clearPlayerPermissions(player.getUuid());
            server.getCommandManager().sendCommandTree(player);
            ChunkloaderNetworking.refreshOpenChunkMapMarkers(server, ChunkloaderMod.getChunkloaderManager());
            context.getSource().sendFeedback(() -> Text.literal("Revoked mod access from " + playerName)
                .formatted(Formatting.GREEN), true);

            player.sendMessage(Text.literal("Your mod access has been revoked.")
                .formatted(Formatting.RED), false);

            return 1;
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error in revokeAllPermissions command", e);
            context.getSource().sendError(Text.literal("An error occurred: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
    }
}

