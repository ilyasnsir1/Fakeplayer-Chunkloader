package de.chunkloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import de.chunkloader.ChunkloaderMod;
import de.chunkloader.ChunkloaderConstants;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.permissions.PermissionManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class ChunkloaderCommand {
    
    private static String getEntityTypeName(boolean allowMobSpawning) {
        return allowMobSpawning ? "Fakeplayer" : "Chunkplayer";
    }
    
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ChunkloaderMod.LOGGER.info("Registering fakeplayer commands...");
            registerCommands(dispatcher);
        });
    }
    
    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("fakeplayer")
            .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_USE))
            .then(CommandManager.literal("add")
                .executes(ChunkloaderCommand::addFakePlayer))
            .then(CommandManager.literal("remove")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .executes(ChunkloaderCommand::removeFakePlayer)))
            .then(CommandManager.literal("list")
                .executes(ChunkloaderCommand::listFakePlayers))
            .then(CommandManager.literal("info")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .executes(ChunkloaderCommand::infoFakePlayer)))
            .then(CommandManager.literal("reload")
                .executes(ChunkloaderCommand::reloadConfig))
            .then(CommandManager.literal("toggle")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .executes(ChunkloaderCommand::toggleFakePlayer)))
            .then(CommandManager.literal("stats")
                .executes(ChunkloaderCommand::statsFakePlayers))
            .then(CommandManager.literal("namevisible")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .then(CommandManager.argument("visible", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setNameVisible))))
            .then(CommandManager.literal("setradius")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .then(CommandManager.argument("radius", IntegerArgumentType.integer(ChunkloaderConstants.MIN_RADIUS, ChunkloaderConstants.MAX_RADIUS))
                        .executes(ChunkloaderCommand::setRadius))))
            .then(CommandManager.literal("setmobspawning")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .then(CommandManager.argument("allow", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setMobSpawning))))
            .then(CommandManager.literal("enableall")
                .executes(ChunkloaderCommand::enableAllFakePlayers))
            .then(CommandManager.literal("disableall")
                .executes(ChunkloaderCommand::disableAllFakePlayers))
            .then(CommandManager.literal("removeall")
                .executes(ChunkloaderCommand::removeAllFakePlayers)
                .then(CommandManager.literal("enabled")
                    .executes(ChunkloaderCommand::removeAllEnabledFakePlayers))
                .then(CommandManager.literal("disabled")
                    .executes(ChunkloaderCommand::removeAllDisabledFakePlayers)))
            .then(CommandManager.literal("visualize")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .executes(ChunkloaderCommand::toggleVisualization)))
            .then(CommandManager.literal("visualize3d")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .executes(ChunkloaderCommand::toggleVisualization3D)
                    .then(CommandManager.argument("minY", IntegerArgumentType.integer(-64, 320))
                        .then(CommandManager.argument("maxY", IntegerArgumentType.integer(-64, 320))
                            .executes(ChunkloaderCommand::toggleVisualization3DWithHeight)))))
            .then(CommandManager.literal("permission")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(CommandManager.literal("grant")
                    .then(CommandManager.argument("player", StringArgumentType.string())
                        .executes(ChunkloaderCommand::grantAllPermissions)))
                .then(CommandManager.literal("revoke")
                    .then(CommandManager.argument("player", StringArgumentType.string())
                        .executes(ChunkloaderCommand::revokeAllPermissions)))));
        
        var fpNode = CommandManager.literal("fp")
            .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_USE))
            .then(CommandManager.literal("add")
                .executes(ChunkloaderCommand::addFakePlayer))
            .then(CommandManager.literal("remove")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .executes(ChunkloaderCommand::removeFakePlayer)))
            .then(CommandManager.literal("list")
                .executes(ChunkloaderCommand::listFakePlayers))
            .then(CommandManager.literal("info")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .executes(ChunkloaderCommand::infoFakePlayer)))
            .then(CommandManager.literal("reload")
                .executes(ChunkloaderCommand::reloadConfig))
            .then(CommandManager.literal("toggle")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .executes(ChunkloaderCommand::toggleFakePlayer)))
            .then(CommandManager.literal("stats")
                .executes(ChunkloaderCommand::statsFakePlayers))
            .then(CommandManager.literal("namevisible")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .then(CommandManager.argument("visible", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setNameVisible))))
            .then(CommandManager.literal("setradius")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .then(CommandManager.argument("radius", IntegerArgumentType.integer(ChunkloaderConstants.MIN_RADIUS, ChunkloaderConstants.MAX_RADIUS))
                        .executes(ChunkloaderCommand::setRadius))))
            .then(CommandManager.literal("setmobspawning")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .then(CommandManager.argument("allow", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setMobSpawning))))
            .then(CommandManager.literal("enableall")
                .executes(ChunkloaderCommand::enableAllFakePlayers))
            .then(CommandManager.literal("disableall")
                .executes(ChunkloaderCommand::disableAllFakePlayers))
            .then(CommandManager.literal("removeall")
                .executes(ChunkloaderCommand::removeAllFakePlayers)
                .then(CommandManager.literal("enabled")
                    .executes(ChunkloaderCommand::removeAllEnabledFakePlayers))
                .then(CommandManager.literal("disabled")
                    .executes(ChunkloaderCommand::removeAllDisabledFakePlayers)))
            .then(CommandManager.literal("visualize")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .executes(ChunkloaderCommand::toggleVisualization)))
            .then(CommandManager.literal("visualize3d")
                .then(CommandManager.argument("name", StringArgumentType.string())
                    .executes(ChunkloaderCommand::toggleVisualization3D)
                    .then(CommandManager.argument("minY", IntegerArgumentType.integer(-64, 320))
                        .then(CommandManager.argument("maxY", IntegerArgumentType.integer(-64, 320))
                            .executes(ChunkloaderCommand::toggleVisualization3DWithHeight)))))
            .then(CommandManager.literal("permission")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(CommandManager.literal("grant")
                    .then(CommandManager.argument("player", StringArgumentType.string())
                        .executes(ChunkloaderCommand::grantAllPermissions)))
                .then(CommandManager.literal("revoke")
                    .then(CommandManager.argument("player", StringArgumentType.string())
                        .executes(ChunkloaderCommand::revokeAllPermissions))));
        
        dispatcher.register(fpNode);
    }
    
    private static int addFakePlayer(CommandContext<ServerCommandSource> context) {
        try {
            if (ChunkloaderMod.getChunkloaderManager() == null) {
                context.getSource().sendError(Text.literal("Fakeplayer Manager is not initialized"));
                return 0;
            }
            
            if (context.getSource().getPlayer() == null) {
                context.getSource().sendError(Text.literal("This command can only be executed by a player"));
                return 0;
            }
            
            var player = context.getSource().getPlayer();
            BlockPos playerPos = player.getBlockPos();
            var world = (net.minecraft.server.world.ServerWorld) player.getEntityWorld();
            
            var config = ChunkloaderMod.getConfig();
            if (config != null && config.getChunkEntries().size() >= config.getMaxChunkloaders()) {
                context.getSource().sendError(Text.literal("Maximum Chunkloader limit (" + config.getMaxChunkloaders() + ") reached!"));
                return 0;
            }
            
            if (world == null) {
                context.getSource().sendError(Text.literal("World is not available"));
                return 0;
            }
            
            int chunkX = playerPos.getX() >> 4;
            int chunkZ = playerPos.getZ() >> 4;
            String dimension = world.getRegistryKey().getValue().toString();
            String overlappingName = ChunkloaderMod.getChunkloaderManager().getOverlappingChunkloaderName(
                chunkX, chunkZ, 0, dimension, null);
            
            if (overlappingName != null) {
                ChunkloaderTarget overlappingEntry = config != null ? config.getEntryByName(overlappingName) : null;
                String errorMsg;
                if (overlappingEntry != null && overlappingEntry.allowMobSpawning()) {
                    errorMsg = "Cannot add fakeplayer: Position is already covered by a fakeplayer '" + overlappingName + "'";
                } else {
                    errorMsg = "Cannot add fakeplayer: Position is already covered by chunkloader '" + overlappingName + "'";
                }
                context.getSource().sendError(Text.literal(errorMsg).formatted(Formatting.RED));
                return 0;
            }
            
            String playerName = player.getName().getString();
            boolean success = ChunkloaderMod.getChunkloaderManager().addChunkloader(
                chunkX, chunkZ, playerPos, null, world, playerName);
            
            if (success) {
                var entries = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries();
                
                String name = entries.stream()
                    .filter(e -> e != null && e.blockX() == playerPos.getX() && 
                        e.blockY() == playerPos.getY() && e.blockZ() == playerPos.getZ())
                    .map(e -> e.name() != null ? e.name() : "Unnamed")
                    .findFirst()
                    .orElse("Unknown");
                
                context.getSource().sendFeedback(() -> Text.literal("Fakeplayer '" + name + "' created at position (" +
                    playerPos.getX() + ", " + playerPos.getY() + ", " + playerPos.getZ() + ")").formatted(Formatting.GREEN), true);
                return 1;
            } else {
                String errorMsg = "Failed to create fakeplayer";
                if (config != null && config.getChunkEntries().size() >= config.getMaxChunkloaders()) {
                    errorMsg = "Maximum Chunkloader limit (" + config.getMaxChunkloaders() + ") reached!";
                } else if (config != null && config.hasEntry(chunkX, chunkZ)) {
                    errorMsg = "A chunkloader already exists at this position!";
                }
                context.getSource().sendError(Text.literal(errorMsg).formatted(Formatting.RED));
                return 0;
            }
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error in addFakePlayer command", e);
            context.getSource().sendError(Text.literal("An error occurred: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
    }
    
    private static int removeFakePlayer(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }
        
        boolean success = ChunkloaderMod.getChunkloaderManager().removeChunkloaderByName(name);
        
        if (success) {
            context.getSource().sendFeedback(() -> Text.literal("Fakeplayer '" + name + "' removed").formatted(Formatting.GREEN), true);
            return 1;
        } else {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();
            
            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Fakeplayer '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Fakeplayer '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }
    }
    
    private static int listFakePlayers(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }
        
        var fakePlayers = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries();
        
        if (fakePlayers.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("No active fakeplayers"), false);
        } else {
            context.getSource().sendFeedback(() -> Text.literal("Active fakeplayers (" + fakePlayers.size() + "):"), false);
            for (var entry : fakePlayers) {
                String name = entry.name() != null ? entry.name() : "Unnamed";
                context.getSource().sendFeedback(() -> Text.literal("  - " + name + " @ Block (" + 
                    entry.blockX() + ", " + entry.blockY() + ", " + entry.blockZ() + ")"), false);
            }
        }
        
        return fakePlayers.size();
    }
    
    private static int infoFakePlayer(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }
        
        ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);
        
        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();
            
            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Fakeplayer '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Fakeplayer '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }
        
        context.getSource().sendFeedback(() -> Text.literal("=== Fakeplayer Info ===").formatted(Formatting.GOLD), false);
        context.getSource().sendFeedback(() -> Text.literal("Name: " + entry.name()).formatted(Formatting.YELLOW), false);
        context.getSource().sendFeedback(() -> Text.literal("Status: " + (entry.enabled() ? "Active" : "Inactive"))
            .formatted(entry.enabled() ? Formatting.GREEN : Formatting.RED), false);
        String mode = entry.allowMobSpawning() ? "Fakeplayer Mode (Mob Spawning)" : "Chunkplayer Mode (Chunks Only)";
        Formatting modeColor = entry.allowMobSpawning() ? Formatting.GREEN : Formatting.BLUE;
        context.getSource().sendFeedback(() -> Text.literal("Mode: " + mode).formatted(modeColor), false);
        context.getSource().sendFeedback(() -> Text.literal("Chunk: (" + entry.chunkX() + ", " + entry.chunkZ() + ")"), false);
        context.getSource().sendFeedback(() -> Text.literal("Block: (" + entry.blockX() + ", " + entry.blockY() + ", " + entry.blockZ() + ")"), false);
        context.getSource().sendFeedback(() -> Text.literal("Name visible: " + (entry.nameVisible() ? "Yes" : "No")), false);
        int chunksLoaded = (entry.chunkRadius() * 2 + 1) * (entry.chunkRadius() * 2 + 1);
        context.getSource().sendFeedback(() -> Text.literal("Chunk radius: " + entry.chunkRadius() + " (loads " + chunksLoaded + " chunks)"), false);
        
        return 1;
    }
    
    private static int reloadConfig(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }
        
        ChunkloaderMod.getChunkloaderManager().reloadConfig();
        context.getSource().sendFeedback(() -> Text.literal("Config reloaded").formatted(Formatting.GREEN), true);
        return 1;
    }
    
    private static int toggleFakePlayer(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }
        
        ChunkloaderTarget existingEntry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);
        
        if (existingEntry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();
            
            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Fakeplayer '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Fakeplayer '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }
        
        boolean newEnabled = ChunkloaderMod.getChunkloaderManager().toggleChunkloaderByName(name);
        
        ChunkloaderTarget updatedEntry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(existingEntry);
        
        String entityType = getEntityTypeName(updatedEntry != null ? updatedEntry.allowMobSpawning() : true);
        
        if (newEnabled) {
            context.getSource().sendFeedback(() -> Text.literal(entityType + " '" + name + "' enabled").formatted(Formatting.GREEN), true);
        } else {
            context.getSource().sendFeedback(() -> Text.literal(entityType + " '" + name + "' disabled").formatted(Formatting.RED), true);
        }
        
        return 1;
    }
    
    private static int statsFakePlayers(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }
        
        ChunkloaderManager.ChunkloaderStats stats = ChunkloaderMod.getChunkloaderManager().getStats();
        ChunkloaderManager.ChunkloaderPerformanceStats perfStats = ChunkloaderMod.getChunkloaderManager().getPerformanceStats();
        
        context.getSource().sendFeedback(() -> Text.literal("=== Fakeplayer Statistics ===").formatted(Formatting.GOLD), false);
        context.getSource().sendFeedback(() -> Text.literal("Total: " + stats.total()).formatted(Formatting.AQUA), false);
        context.getSource().sendFeedback(() -> Text.literal("Active: " + stats.enabled()).formatted(Formatting.GREEN), false);
        context.getSource().sendFeedback(() -> Text.literal("Inactive: " + stats.disabled()).formatted(Formatting.RED), false);
        context.getSource().sendFeedback(() -> Text.literal("Loaded chunks: " + stats.loadedChunks()), false);
        context.getSource().sendFeedback(() -> Text.literal("Active fakeplayers: " + stats.activeFakePlayers()), false);
        
        long usedMB = perfStats.usedMemory() / (1024 * 1024);
        long maxMB = perfStats.maxMemory() / (1024 * 1024);
        Formatting memoryColor = perfStats.memoryUsagePercent() > 80 ? Formatting.RED : perfStats.memoryUsagePercent() > 60 ? Formatting.YELLOW : Formatting.GREEN;
        
        context.getSource().sendFeedback(() -> Text.literal("Memory used: " + usedMB + " MB / " + maxMB + " MB (" + String.format("%.1f", perfStats.memoryUsagePercent()) + "%)")
            .formatted(memoryColor), false);
        
        if (perfStats.memoryUsagePercent() > 85) {
            context.getSource().sendFeedback(() -> Text.literal("⚠ Warning: High memory usage!").formatted(Formatting.RED), false);
        }
        
        if (stats.loadedChunks() > 1000) {
            context.getSource().sendFeedback(() -> Text.literal("⚠ Note: Many loaded chunks may impact performance").formatted(Formatting.YELLOW), false);
        }
        
        return 1;
    }
    
    private static int setNameVisible(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        boolean visible = BoolArgumentType.getBool(context, "visible");
        
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }
        
        ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);
        
        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();
            
            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Fakeplayer '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Fakeplayer '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }
        
        boolean success = ChunkloaderMod.getChunkloaderManager().setChunkloaderNameVisible(name, visible);
        
        if (success) {
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
        int radius = IntegerArgumentType.getInteger(context, "radius");
        
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }
        
        if (radius < ChunkloaderConstants.MIN_RADIUS || radius > ChunkloaderConstants.MAX_RADIUS) {
            context.getSource().sendError(Text.literal("Invalid radius: " + radius + " (must be between " + ChunkloaderConstants.MIN_RADIUS + " and " + ChunkloaderConstants.MAX_RADIUS + ")").formatted(Formatting.RED));
            return 0;
        }
        
        ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);
        
        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();
            
            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Fakeplayer '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Fakeplayer '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }
        
        boolean success = ChunkloaderMod.getChunkloaderManager().setChunkloaderRadius(name, radius);
        
        if (success) {
            int chunksLoaded = (radius * 2 + 1) * (radius * 2 + 1);
            context.getSource().sendFeedback(() -> Text.literal("Chunk radius for '" + name + "' set to " + radius + " (loads " + chunksLoaded + " chunks)")
                .formatted(Formatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendError(Text.literal("Error setting chunk radius").formatted(Formatting.RED));
            return 0;
        }
    }
    
    private static int setMobSpawning(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        boolean allow = BoolArgumentType.getBool(context, "allow");
        
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }
        
        ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);
        
        if (entry == null) {
            var config = ChunkloaderMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();
            
            Text errorText;
            if (!similar.isEmpty()) {
                errorText = Text.literal("Fakeplayer '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .formatted(Formatting.RED);
            } else {
                errorText = Text.literal("Fakeplayer '" + name + "' not found").formatted(Formatting.RED);
            }
            context.getSource().sendError(errorText);
            return 0;
        }
        
        boolean success = ChunkloaderMod.getChunkloaderManager().setChunkloaderAllowMobSpawning(name, allow);
        
        if (success) {
            String mode = allow ? "Fakeplayer Mode (Mob spawning enabled)" : "Chunkplayer Mode (chunks only, no mob spawning)";
            context.getSource().sendFeedback(() -> Text.literal("Mob spawning for '" + name + "' set to " + allow + " (" + mode + ")")
                .formatted(Formatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendError(Text.literal("Error setting mob spawning status").formatted(Formatting.RED));
            return 0;
        }
    }
    
    private static int enableAllFakePlayers(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }
        
        int count = ChunkloaderMod.getChunkloaderManager().enableAllChunkloaders();
        
        var entries = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries();
        
        long fakeplayerCount = entries.stream().filter(e -> e.allowMobSpawning()).count();
        long chunkloaderCount = entries.size() - fakeplayerCount;
        
        String message;
        if (fakeplayerCount > 0 && chunkloaderCount > 0) {
            message = count + " entities enabled (" + fakeplayerCount + " fakeplayers, " + chunkloaderCount + " chunkplayers)";
        } else if (fakeplayerCount > 0) {
            message = count + " fakeplayers enabled";
        } else {
            message = count + " chunkplayers enabled";
        }
        
        context.getSource().sendFeedback(() -> Text.literal(message).formatted(Formatting.GREEN), true);
        return count;
    }
    
    private static int disableAllFakePlayers(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }
        
        var entriesBefore = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries();
        long fakeplayerCountBefore = entriesBefore.stream().filter(e -> e.allowMobSpawning()).count();
        long chunkloaderCountBefore = entriesBefore.size() - fakeplayerCountBefore;
        
        int count = ChunkloaderMod.getChunkloaderManager().disableAllChunkloaders();
        
        String message;
        if (fakeplayerCountBefore > 0 && chunkloaderCountBefore > 0) {
            message = count + " entities disabled (" + fakeplayerCountBefore + " fakeplayers, " + chunkloaderCountBefore + " chunkplayers)";
        } else if (fakeplayerCountBefore > 0) {
            message = count + " fakeplayers disabled";
        } else {
            message = count + " chunkplayers disabled";
        }
        
        context.getSource().sendFeedback(() -> Text.literal(message).formatted(Formatting.YELLOW), true);
        return count;
    }
    
    private static int removeAllFakePlayers(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Fakeplayer Manager is not initialized"));
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
            if (fakeplayerCount > 0 && chunkplayerCount > 0) {
                message = count + " entities removed (" + fakeplayerCount + " fakeplayers, " + chunkplayerCount + " chunkplayers)";
            } else if (fakeplayerCount > 0) {
                message = count + " fakeplayers removed";
            } else {
                message = count + " chunkplayers removed";
            }
            context.getSource().sendFeedback(() -> Text.literal(message).formatted(Formatting.GREEN), true);
        } else {
            context.getSource().sendFeedback(() -> Text.literal("No fakeplayers found").formatted(Formatting.YELLOW), false);
        }
        return count;
    }
    
    private static int removeAllEnabledFakePlayers(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Fakeplayer Manager is not initialized"));
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
            if (fakeplayerCount > 0 && chunkplayerCount > 0) {
                message = count + " enabled entities removed (" + fakeplayerCount + " fakeplayers, " + chunkplayerCount + " chunkplayers)";
            } else if (fakeplayerCount > 0) {
                message = count + " enabled fakeplayers removed";
            } else {
                message = count + " enabled chunkplayers removed";
            }
            context.getSource().sendFeedback(() -> Text.literal(message).formatted(Formatting.GREEN), true);
        } else {
            context.getSource().sendFeedback(() -> Text.literal("No enabled fakeplayers found").formatted(Formatting.YELLOW), false);
        }
        return count;
    }
    
    private static int removeAllDisabledFakePlayers(CommandContext<ServerCommandSource> context) {
        if (ChunkloaderMod.getChunkloaderManager() == null) {
            context.getSource().sendError(Text.literal("Fakeplayer Manager is not initialized"));
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
            if (fakeplayerCount > 0 && chunkplayerCount > 0) {
                message = count + " disabled entities removed (" + fakeplayerCount + " fakeplayers, " + chunkplayerCount + " chunkplayers)";
            } else if (fakeplayerCount > 0) {
                message = count + " disabled fakeplayers removed";
            } else {
                message = count + " disabled chunkplayers removed";
            }
            context.getSource().sendFeedback(() -> Text.literal(message).formatted(Formatting.GREEN), true);
        } else {
            context.getSource().sendFeedback(() -> Text.literal("No disabled fakeplayers found").formatted(Formatting.YELLOW), false);
        }
        return count;
    }
    
    private static int toggleVisualization(CommandContext<ServerCommandSource> context) {
        try {
            String name = StringArgumentType.getString(context, "name");
            
            if (ChunkloaderMod.getChunkloaderManager() == null) {
                context.getSource().sendError(Text.literal("Fakeplayer Manager is not initialized"));
                return 0;
            }
            
            ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
                .filter(e -> e != null && name.equals(e.name()))
                .findFirst()
                .orElse(null);
            
            if (entry == null) {
                var config = ChunkloaderMod.getConfig();
                List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();
                
                Text errorText;
                if (!similar.isEmpty()) {
                    errorText = Text.literal("Fakeplayer '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                        .formatted(Formatting.RED);
                } else {
                    errorText = Text.literal("Fakeplayer '" + name + "' not found").formatted(Formatting.RED);
                }
                context.getSource().sendError(errorText);
                return 0;
            }
            
            de.chunkloader.manager.ChunkloaderManager.ChunkKey key = new de.chunkloader.manager.ChunkloaderManager.ChunkKey(entry.chunkX(), entry.chunkZ());
            
            if (ChunkloaderMod.getChunkloaderManager() == null) {
                context.getSource().sendError(Text.literal("Fakeplayer Manager is not initialized"));
                return 0;
            }
            
            ChunkloaderMod.getChunkloaderManager().toggleVisualization(key);
            boolean isActive = ChunkloaderMod.getChunkloaderManager().isVisualizationActive(key);
            
            context.getSource().sendFeedback(() -> Text.literal("Chunk border visualization for '" + name + "' is now " + (isActive ? "enabled" : "disabled"))
                .formatted(isActive ? Formatting.GREEN : Formatting.RED), true);
            
            return 1;
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error in toggleVisualization command", e);
            context.getSource().sendError(Text.literal("An error occurred: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
    }
    
    private static int toggleVisualization3D(CommandContext<ServerCommandSource> context) {
        try {
            String name = StringArgumentType.getString(context, "name");
            
            if (ChunkloaderMod.getChunkloaderManager() == null) {
                context.getSource().sendError(Text.literal("Fakeplayer Manager is not initialized"));
                return 0;
            }
            
            ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
                .filter(e -> e != null && name.equalsIgnoreCase(e.name()))
                .findFirst()
                .orElse(null);
            
            if (entry == null) {
                var config = ChunkloaderMod.getConfig();
                List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();
                
                Text errorText;
                if (!similar.isEmpty()) {
                    errorText = Text.literal("Fakeplayer '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                        .formatted(Formatting.RED);
                } else {
                    errorText = Text.literal("Fakeplayer '" + name + "' not found").formatted(Formatting.RED);
                }
                context.getSource().sendError(errorText);
                return 0;
            }
            
            de.chunkloader.manager.ChunkloaderManager.ChunkKey key = 
                new de.chunkloader.manager.ChunkloaderManager.ChunkKey(entry.chunkX(), entry.chunkZ());
            
            if (ChunkloaderMod.getChunkloaderManager() == null) {
                context.getSource().sendError(Text.literal("Fakeplayer Manager is not initialized"));
                return 0;
            }
            
            ChunkloaderMod.getChunkloaderManager().toggleVisualization3D(key);
            boolean isActive = ChunkloaderMod.getChunkloaderManager().isVisualization3DActive(key);
            
            context.getSource().sendFeedback(() -> Text.literal("3D chunk visualization for '" + name + "' is now " + (isActive ? "enabled" : "disabled") + " (full height: -64 to 320)")
                .formatted(isActive ? Formatting.GREEN : Formatting.RED), true);
            
            return 1;
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error in toggleVisualization3D command", e);
            context.getSource().sendError(Text.literal("An error occurred: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
    }
    
    private static int toggleVisualization3DWithHeight(CommandContext<ServerCommandSource> context) {
        try {
            String name = StringArgumentType.getString(context, "name");
            int minY = IntegerArgumentType.getInteger(context, "minY");
            int maxY = IntegerArgumentType.getInteger(context, "maxY");
            
            if (minY >= maxY) {
                context.getSource().sendError(Text.literal("minY must be less than maxY").formatted(Formatting.RED));
                return 0;
            }
            
            if (ChunkloaderMod.getChunkloaderManager() == null) {
                context.getSource().sendError(Text.literal("Fakeplayer Manager is not initialized"));
                return 0;
            }
            
            ChunkloaderTarget entry = ChunkloaderMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
                .filter(e -> e != null && name.equalsIgnoreCase(e.name()))
                .findFirst()
                .orElse(null);
            
            if (entry == null) {
                var config = ChunkloaderMod.getConfig();
                List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();
                
                Text errorText;
                if (!similar.isEmpty()) {
                    errorText = Text.literal("Fakeplayer '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                        .formatted(Formatting.RED);
                } else {
                    errorText = Text.literal("Fakeplayer '" + name + "' not found").formatted(Formatting.RED);
                }
                context.getSource().sendError(errorText);
                return 0;
            }
            
            de.chunkloader.manager.ChunkloaderManager.ChunkKey key = 
                new de.chunkloader.manager.ChunkloaderManager.ChunkKey(entry.chunkX(), entry.chunkZ());
            
            if (ChunkloaderMod.getChunkloaderManager() == null) {
                context.getSource().sendError(Text.literal("Fakeplayer Manager is not initialized"));
                return 0;
            }
            
            ChunkloaderMod.getChunkloaderManager().toggleVisualization3D(key, minY, maxY);
            boolean isActive = ChunkloaderMod.getChunkloaderManager().isVisualization3DActive(key);
            
            context.getSource().sendFeedback(() -> Text.literal("3D chunk visualization for '" + name + "' is now " + (isActive ? "enabled" : "disabled") + " (height: " + minY + " to " + maxY + ")")
                .formatted(isActive ? Formatting.GREEN : Formatting.RED), true);
            
            return 1;
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error in toggleVisualization3DWithHeight command", e);
            context.getSource().sendError(Text.literal("An error occurred: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
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
            
            permissionConfig.grantPermission(player.getUuid(), "chunkloader.*");
            context.getSource().sendFeedback(() -> Text.literal("Granted all fakeplayer permissions to " + playerName)
                .formatted(Formatting.GREEN), true);
            
            player.sendMessage(Text.literal("You have been granted all fakeplayer permissions!")
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
            context.getSource().sendFeedback(() -> Text.literal("Revoked all fakeplayer permissions from " + playerName)
                .formatted(Formatting.GREEN), true);
            
            player.sendMessage(Text.literal("Your fakeplayer permissions have been revoked.")
                .formatted(Formatting.RED), false);
            
            return 1;
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Error in revokeAllPermissions command", e);
            context.getSource().sendError(Text.literal("An error occurred: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
    }
}

