package de.chunkloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.ChunkloaderConstants;
import de.chunkloader.config.ChunkloaderTarget;
import de.chunkloader.manager.ChunkloaderManager;
import de.chunkloader.permissions.PermissionManager;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;

import java.util.List;

public class ChunkloaderCommand {
    
    private static String getEntityTypeName(boolean allowMobSpawning) {
        return allowMobSpawning ? "Fakeplayer" : "Chunkplayer";
    }
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, net.minecraft.core.HolderLookup.Provider registryAccess) {
        registerCommands(dispatcher, registryAccess);
    }
    
    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, net.minecraft.core.HolderLookup.Provider registryAccess) {
        dispatcher.register(Commands.literal("fakeplayer")
            .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_USE))
            .then(Commands.literal("add")
                .executes(ChunkloaderCommand::addFakePlayer))
            .then(Commands.literal("remove")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(ChunkloaderCommand::removeFakePlayer)))
            .then(Commands.literal("list")
                .executes(ChunkloaderCommand::listFakePlayers))
            .then(Commands.literal("info")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(ChunkloaderCommand::infoFakePlayer)))
            .then(Commands.literal("reload")
                .executes(ChunkloaderCommand::reloadConfig))
            .then(Commands.literal("toggle")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(ChunkloaderCommand::toggleFakePlayer)))
            .then(Commands.literal("stats")
                .executes(ChunkloaderCommand::statsFakePlayers))
            .then(Commands.literal("namevisible")
                .then(Commands.argument("name", StringArgumentType.string())
                    .then(Commands.argument("visible", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setNameVisible))))
            .then(Commands.literal("setradius")
                .then(Commands.argument("name", StringArgumentType.string())
                    .then(Commands.argument("radius", IntegerArgumentType.integer(ChunkloaderConstants.MIN_RADIUS, ChunkloaderConstants.MAX_RADIUS))
                        .executes(ChunkloaderCommand::setRadius))))
            .then(Commands.literal("setmobspawning")
                .then(Commands.argument("name", StringArgumentType.string())
                    .then(Commands.argument("allow", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setMobSpawning))))
            .then(Commands.literal("rename")
                .then(Commands.argument("name", StringArgumentType.string())
                    .then(Commands.argument("newName", StringArgumentType.string())
                        .executes(ChunkloaderCommand::renameChunkloader))))
            .then(Commands.literal("enableall")
                .executes(ChunkloaderCommand::enableAllFakePlayers))
            .then(Commands.literal("disableall")
                .executes(ChunkloaderCommand::disableAllFakePlayers))
            .then(Commands.literal("removeall")
                .executes(ChunkloaderCommand::removeAllFakePlayers)
                .then(Commands.literal("enabled")
                    .executes(ChunkloaderCommand::removeAllEnabledFakePlayers))
                .then(Commands.literal("disabled")
                    .executes(ChunkloaderCommand::removeAllDisabledFakePlayers)))
            .then(Commands.literal("visualize")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(ChunkloaderCommand::toggleVisualization)))
            .then(Commands.literal("visualize3d")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(ChunkloaderCommand::toggleVisualization3D)
                    .then(Commands.argument("minY", IntegerArgumentType.integer(-64, 320))
                        .then(Commands.argument("maxY", IntegerArgumentType.integer(-64, 320))
                            .executes(ChunkloaderCommand::toggleVisualization3DWithHeight)))))
            .then(Commands.literal("permission")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(Commands.literal("grant")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .executes(ChunkloaderCommand::grantAllPermissions)))
                .then(Commands.literal("revoke")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .executes(ChunkloaderCommand::revokeAllPermissions)))));
        
        var fpNode = Commands.literal("fp")
            .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_USE))
            .then(Commands.literal("add")
                .executes(ChunkloaderCommand::addFakePlayer))
            .then(Commands.literal("remove")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(ChunkloaderCommand::removeFakePlayer)))
            .then(Commands.literal("list")
                .executes(ChunkloaderCommand::listFakePlayers))
            .then(Commands.literal("info")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(ChunkloaderCommand::infoFakePlayer)))
            .then(Commands.literal("reload")
                .executes(ChunkloaderCommand::reloadConfig))
            .then(Commands.literal("toggle")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(ChunkloaderCommand::toggleFakePlayer)))
            .then(Commands.literal("stats")
                .executes(ChunkloaderCommand::statsFakePlayers))
            .then(Commands.literal("namevisible")
                .then(Commands.argument("name", StringArgumentType.string())
                    .then(Commands.argument("visible", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setNameVisible))))
            .then(Commands.literal("setradius")
                .then(Commands.argument("name", StringArgumentType.string())
                    .then(Commands.argument("radius", IntegerArgumentType.integer(ChunkloaderConstants.MIN_RADIUS, ChunkloaderConstants.MAX_RADIUS))
                        .executes(ChunkloaderCommand::setRadius))))
            .then(Commands.literal("setmobspawning")
                .then(Commands.argument("name", StringArgumentType.string())
                    .then(Commands.argument("allow", BoolArgumentType.bool())
                        .executes(ChunkloaderCommand::setMobSpawning))))
            .then(Commands.literal("rename")
                .then(Commands.argument("name", StringArgumentType.string())
                    .then(Commands.argument("newName", StringArgumentType.string())
                        .executes(ChunkloaderCommand::renameChunkloader))))
            .then(Commands.literal("enableall")
                .executes(ChunkloaderCommand::enableAllFakePlayers))
            .then(Commands.literal("disableall")
                .executes(ChunkloaderCommand::disableAllFakePlayers))
            .then(Commands.literal("removeall")
                .executes(ChunkloaderCommand::removeAllFakePlayers)
                .then(Commands.literal("enabled")
                    .executes(ChunkloaderCommand::removeAllEnabledFakePlayers))
                .then(Commands.literal("disabled")
                    .executes(ChunkloaderCommand::removeAllDisabledFakePlayers)))
            .then(Commands.literal("visualize")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(ChunkloaderCommand::toggleVisualization)))
            .then(Commands.literal("visualize3d")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(ChunkloaderCommand::toggleVisualization3D)
                    .then(Commands.argument("minY", IntegerArgumentType.integer(-64, 320))
                        .then(Commands.argument("maxY", IntegerArgumentType.integer(-64, 320))
                            .executes(ChunkloaderCommand::toggleVisualization3DWithHeight)))))
            .then(Commands.literal("permission")
                .requires(source -> PermissionManager.hasPermission(source, PermissionManager.PERMISSION_ADMIN))
                .then(Commands.literal("grant")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .executes(ChunkloaderCommand::grantAllPermissions)))
                .then(Commands.literal("revoke")
                    .then(Commands.argument("player", StringArgumentType.string())
                        .executes(ChunkloaderCommand::revokeAllPermissions))));
        
        dispatcher.register(fpNode);
    }
    
    private static int addFakePlayer(CommandContext<CommandSourceStack> context) {
        try {
            if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
                context.getSource().sendFailure(Component.literal("Fakeplayer Manager is not initialized"));
                return 0;
            }
            
            if (context.getSource().getPlayer() == null) {
                context.getSource().sendFailure(Component.literal("This command can only be executed by a player"));
                return 0;
            }
            
            var player = context.getSource().getPlayer();
            BlockPos playerPos = player.blockPosition();
            var world = (net.minecraft.server.level.ServerLevel) player.level();
            
            var config = ChunkloaderForgeMod.getConfig();
            if (config != null && config.getChunkEntries().size() >= config.getMaxChunkloaders()) {
                context.getSource().sendFailure(Component.literal("Maximum Chunkloader limit (" + config.getMaxChunkloaders() + ") reached!"));
                return 0;
            }
            
            if (world == null) {
                context.getSource().sendFailure(Component.literal("World is not available"));
                return 0;
            }
            
            int chunkX = playerPos.getX() >> 4;
            int chunkZ = playerPos.getZ() >> 4;
            String dimension = world.dimension().location().toString();
            String overlappingName = ChunkloaderForgeMod.getChunkloaderManager().getOverlappingChunkloaderName(
                chunkX, chunkZ, 0, dimension, null);
            
            if (overlappingName != null) {
                ChunkloaderTarget overlappingEntry = config != null ? config.getEntryByName(overlappingName) : null;
                String errorMsg;
                if (overlappingEntry != null && overlappingEntry.allowMobSpawning()) {
                    errorMsg = "Cannot add fakeplayer: Position is already covered by a fakeplayer '" + overlappingName + "'";
                } else {
                    errorMsg = "Cannot add fakeplayer: Position is already covered by chunkloader '" + overlappingName + "'";
                }
                context.getSource().sendFailure(Component.literal(errorMsg).withStyle(ChatFormatting.RED));
                return 0;
            }
            
            String playerName = player.getName().getString();
            boolean success = ChunkloaderForgeMod.getChunkloaderManager().addChunkloader(
                chunkX, chunkZ, playerPos, null, world, playerName);
            
            if (success) {
                var entries = ChunkloaderForgeMod.getChunkloaderManager().getActiveChunkloaderEntries();
                
                String name = entries.stream()
                    .filter(e -> e != null && e.blockX() == playerPos.getX() && 
                        e.blockY() == playerPos.getY() && e.blockZ() == playerPos.getZ())
                    .map(e -> e.name() != null ? e.name() : "Unnamed")
                    .findFirst()
                    .orElse("Unknown");
                
                context.getSource().sendSuccess(() -> Component.literal("Fakeplayer '" + name + "' created at position (" +
                    playerPos.getX() + ", " + playerPos.getY() + ", " + playerPos.getZ() + ")").withStyle(ChatFormatting.GREEN), true);
                return 1;
            } else {
                String errorMsg = "Failed to create fakeplayer";
                if (config != null && config.getChunkEntries().size() >= config.getMaxChunkloaders()) {
                    errorMsg = "Maximum Chunkloader limit (" + config.getMaxChunkloaders() + ") reached!";
                } else if (config != null && config.hasEntry(chunkX, chunkZ)) {
                    errorMsg = "A chunkloader already exists at this position!";
                }
                context.getSource().sendFailure(Component.literal(errorMsg).withStyle(ChatFormatting.RED));
                return 0;
            }
        } catch (Exception e) {

            context.getSource().sendFailure(Component.literal("An error occurred: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }
    
    private static int removeFakePlayer(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        
        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }
        
        boolean success = ChunkloaderForgeMod.getChunkloaderManager().removeChunkloaderByName(name);
        
        if (success) {
                context.getSource().sendSuccess(() -> Component.literal("Fakeplayer '" + name + "' removed").withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            var config = ChunkloaderForgeMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();
            
            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Fakeplayer '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Fakeplayer '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }
    }
    
    private static int listFakePlayers(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }
        
        var fakePlayers = ChunkloaderForgeMod.getChunkloaderManager().getActiveChunkloaderEntries();
        
        if (fakePlayers.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No active fakeplayers"), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("Active fakeplayers (" + fakePlayers.size() + "):"), false);
            for (var entry : fakePlayers) {
                String name = entry.name() != null ? entry.name() : "Unnamed";
                context.getSource().sendSuccess(() -> Component.literal("  - " + name + " @ Block (" + 
                    entry.blockX() + ", " + entry.blockY() + ", " + entry.blockZ() + ")"), false);
            }
        }
        
        return fakePlayers.size();
    }
    
    private static int infoFakePlayer(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        
        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }
        
        ChunkloaderTarget entry = ChunkloaderForgeMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);
        
        if (entry == null) {
            var config = ChunkloaderForgeMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();
            
            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Fakeplayer '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Fakeplayer '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }
        
        context.getSource().sendSuccess(() -> Component.literal("=== Fakeplayer Info ===").withStyle(ChatFormatting.GOLD), false);
        context.getSource().sendSuccess(() -> Component.literal("Name: " + entry.name()).withStyle(ChatFormatting.YELLOW), false);
        context.getSource().sendSuccess(() -> Component.literal("Status: " + (entry.enabled() ? "Active" : "Inactive"))
            .withStyle(entry.enabled() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        String mode = entry.allowMobSpawning() ? "Fakeplayer Mode (Mob Spawning)" : "Chunkplayer Mode (Chunks Only)";
        ChatFormatting modeColor = entry.allowMobSpawning() ? ChatFormatting.GREEN : ChatFormatting.BLUE;
        context.getSource().sendSuccess(() -> Component.literal("Mode: " + mode).withStyle(modeColor), false);
        context.getSource().sendSuccess(() -> Component.literal("Chunk: (" + entry.chunkX() + ", " + entry.chunkZ() + ")"), false);
        context.getSource().sendSuccess(() -> Component.literal("Block: (" + entry.blockX() + ", " + entry.blockY() + ", " + entry.blockZ() + ")"), false);
        context.getSource().sendSuccess(() -> Component.literal("Name visible: " + (entry.nameVisible() ? "Yes" : "No")), false);
        int chunksLoaded = (entry.chunkRadius() * 2 + 1) * (entry.chunkRadius() * 2 + 1);
        context.getSource().sendSuccess(() -> Component.literal("Chunk radius: " + entry.chunkRadius() + " (loads " + chunksLoaded + " chunks)"), false);
        
        return 1;
    }
    
    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }
        
        ChunkloaderForgeMod.getChunkloaderManager().reloadConfig();
        context.getSource().sendSuccess(() -> Component.literal("Config reloaded").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
    
    private static int toggleFakePlayer(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        
        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }
        
        ChunkloaderTarget existingEntry = ChunkloaderForgeMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);
        
        if (existingEntry == null) {
            var config = ChunkloaderForgeMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();
            
            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Fakeplayer '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Fakeplayer '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }
        
        boolean newEnabled = ChunkloaderForgeMod.getChunkloaderManager().toggleChunkloaderByName(name);
        
        ChunkloaderTarget updatedEntry = ChunkloaderForgeMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(existingEntry);
        
        String entityType = getEntityTypeName(updatedEntry != null ? updatedEntry.allowMobSpawning() : true);
        
        if (newEnabled) {
            context.getSource().sendSuccess(() -> Component.literal(entityType + " '" + name + "' enabled").withStyle(ChatFormatting.GREEN), true);
        } else {
            context.getSource().sendSuccess(() -> Component.literal(entityType + " '" + name + "' disabled").withStyle(ChatFormatting.RED), true);
        }
        
        return 1;
    }
    
    private static int statsFakePlayers(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }
        
        ChunkloaderManager.ChunkloaderStats stats = ChunkloaderForgeMod.getChunkloaderManager().getStats();
        ChunkloaderManager.ChunkloaderPerformanceStats perfStats = ChunkloaderForgeMod.getChunkloaderManager().getPerformanceStats();
        
        context.getSource().sendSuccess(() -> Component.literal("=== Fakeplayer Statistics ===").withStyle(ChatFormatting.GOLD), false);
        context.getSource().sendSuccess(() -> Component.literal("Total: " + stats.total()).withStyle(ChatFormatting.AQUA), false);
        context.getSource().sendSuccess(() -> Component.literal("Active: " + stats.enabled()).withStyle(ChatFormatting.GREEN), false);
        context.getSource().sendSuccess(() -> Component.literal("Inactive: " + stats.disabled()).withStyle(ChatFormatting.RED), false);
        context.getSource().sendSuccess(() -> Component.literal("Loaded chunks: " + stats.loadedChunks()), false);
        context.getSource().sendSuccess(() -> Component.literal("Active fakeplayers: " + stats.activeFakePlayers()), false);
        
        long usedMB = perfStats.usedMemory() / (1024 * 1024);
        long maxMB = perfStats.maxMemory() / (1024 * 1024);
        ChatFormatting memoryColor = perfStats.memoryUsagePercent() > 80 ? ChatFormatting.RED : perfStats.memoryUsagePercent() > 60 ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
        
        context.getSource().sendSuccess(() -> Component.literal("Memory used: " + usedMB + " MB / " + maxMB + " MB (" + String.format("%.1f", perfStats.memoryUsagePercent()) + "%)")
            .withStyle(memoryColor), false);
        
        if (perfStats.memoryUsagePercent() > 85) {
            context.getSource().sendSuccess(() -> Component.literal("⚠ Warning: High memory usage!").withStyle(ChatFormatting.RED), false);
        }
        
        if (stats.loadedChunks() > 1000) {
            context.getSource().sendSuccess(() -> Component.literal("⚠ Note: Many loaded chunks may impact performance").withStyle(ChatFormatting.YELLOW), false);
        }
        
        return 1;
    }
    
    private static int setNameVisible(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        boolean visible = BoolArgumentType.getBool(context, "visible");
        
        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }
        
        ChunkloaderTarget entry = ChunkloaderForgeMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);
        
        if (entry == null) {
            var config = ChunkloaderForgeMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();
            
            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Fakeplayer '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Fakeplayer '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }
        
        boolean success = ChunkloaderForgeMod.getChunkloaderManager().setChunkloaderNameVisible(name, visible);
        
        if (success) {
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
        int radius = IntegerArgumentType.getInteger(context, "radius");
        
        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }
        
        if (radius < ChunkloaderConstants.MIN_RADIUS || radius > ChunkloaderConstants.MAX_RADIUS) {
            context.getSource().sendFailure(Component.literal("Invalid radius: " + radius + " (must be between " + ChunkloaderConstants.MIN_RADIUS + " and " + ChunkloaderConstants.MAX_RADIUS + ")").withStyle(ChatFormatting.RED));
            return 0;
        }
        
        ChunkloaderTarget entry = ChunkloaderForgeMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);
        
        if (entry == null) {
            var config = ChunkloaderForgeMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();
            
            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Fakeplayer '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Fakeplayer '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }
        
        boolean success = ChunkloaderForgeMod.getChunkloaderManager().setChunkloaderRadius(name, radius);
        
        if (success) {
            int chunksLoaded = (radius * 2 + 1) * (radius * 2 + 1);
            context.getSource().sendSuccess(() -> Component.literal("Chunk radius for '" + name + "' set to " + radius + " (loads " + chunksLoaded + " chunks)")
                .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Error setting chunk radius").withStyle(ChatFormatting.RED));
            return 0;
        }
    }
    
    private static int setMobSpawning(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        boolean allow = BoolArgumentType.getBool(context, "allow");
        
        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }
        
        ChunkloaderTarget entry = ChunkloaderForgeMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);
        
        if (entry == null) {
            var config = ChunkloaderForgeMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();
            
            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Fakeplayer '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Fakeplayer '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }
        
        boolean success = ChunkloaderForgeMod.getChunkloaderManager().setChunkloaderAllowMobSpawning(name, allow);
        
        if (success) {
            String mode = allow ? "Fakeplayer Mode (Mob spawning enabled)" : "Chunkplayer Mode (chunks only, no mob spawning)";
            context.getSource().sendSuccess(() -> Component.literal("Mob spawning for '" + name + "' set to " + allow + " (" + mode + ")")
                .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Error setting mob spawning status").withStyle(ChatFormatting.RED));
            return 0;
        }
    }
    
    private static int renameChunkloader(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        final String newName = StringArgumentType.getString(context, "newName");

        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }

        ChunkloaderTarget entry = ChunkloaderForgeMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
            .filter(e -> name.equals(e.name()))
            .findFirst()
            .orElse(null);

        if (entry == null) {
            var config = ChunkloaderForgeMod.getConfig();
            List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();

            Component errorText;
            if (!similar.isEmpty()) {
                errorText = Component.literal("Fakeplayer '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                    .withStyle(ChatFormatting.RED);
            } else {
                errorText = Component.literal("Fakeplayer '" + name + "' not found").withStyle(ChatFormatting.RED);
            }
            context.getSource().sendFailure(errorText);
            return 0;
        }

        boolean success = ChunkloaderForgeMod.getChunkloaderManager().renameChunkloader(entry.chunkX(), entry.chunkZ(), newName);

        if (success) {
            context.getSource().sendSuccess(() -> Component.literal("Renamed '" + name + "' to '" + newName + "'")
                .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Failed to rename chunkloader. Name may already be in use or invalid.").withStyle(ChatFormatting.RED));
            return 0;
        }
    }
    
    private static int enableAllFakePlayers(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }
        
        int count = ChunkloaderForgeMod.getChunkloaderManager().enableAllChunkloaders();
        
        var entries = ChunkloaderForgeMod.getChunkloaderManager().getActiveChunkloaderEntries();
        
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
        
        context.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), true);
        return count;
    }
    
    private static int disableAllFakePlayers(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Fakeplayer Manager is not initialized"));
            return 0;
        }
        
        var entriesBefore = ChunkloaderForgeMod.getChunkloaderManager().getActiveChunkloaderEntries();
        long fakeplayerCountBefore = entriesBefore.stream().filter(e -> e.allowMobSpawning()).count();
        long chunkloaderCountBefore = entriesBefore.size() - fakeplayerCountBefore;
        
        int count = ChunkloaderForgeMod.getChunkloaderManager().disableAllChunkloaders();
        
        String message;
        if (fakeplayerCountBefore > 0 && chunkloaderCountBefore > 0) {
            message = count + " entities disabled (" + fakeplayerCountBefore + " fakeplayers, " + chunkloaderCountBefore + " chunkplayers)";
        } else if (fakeplayerCountBefore > 0) {
            message = count + " fakeplayers disabled";
        } else {
            message = count + " chunkplayers disabled";
        }
        
        context.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.YELLOW), true);
        return count;
    }
    
    private static int removeAllFakePlayers(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Fakeplayer Manager is not initialized"));
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
            if (fakeplayerCount > 0 && chunkplayerCount > 0) {
                message = count + " entities removed (" + fakeplayerCount + " fakeplayers, " + chunkplayerCount + " chunkplayers)";
            } else if (fakeplayerCount > 0) {
                message = count + " fakeplayers removed";
            } else {
                message = count + " chunkplayers removed";
            }
            context.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), true);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("No fakeplayers found").withStyle(ChatFormatting.YELLOW), false);
        }
        return count;
    }
    
    private static int removeAllEnabledFakePlayers(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Fakeplayer Manager is not initialized"));
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
            if (fakeplayerCount > 0 && chunkplayerCount > 0) {
                message = count + " enabled entities removed (" + fakeplayerCount + " fakeplayers, " + chunkplayerCount + " chunkplayers)";
            } else if (fakeplayerCount > 0) {
                message = count + " enabled fakeplayers removed";
            } else {
                message = count + " enabled chunkplayers removed";
            }
            context.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), true);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("No enabled fakeplayers found").withStyle(ChatFormatting.YELLOW), false);
        }
        return count;
    }
    
    private static int removeAllDisabledFakePlayers(CommandContext<CommandSourceStack> context) {
        if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
            context.getSource().sendFailure(Component.literal("Fakeplayer Manager is not initialized"));
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
            if (fakeplayerCount > 0 && chunkplayerCount > 0) {
                message = count + " disabled entities removed (" + fakeplayerCount + " fakeplayers, " + chunkplayerCount + " chunkplayers)";
            } else if (fakeplayerCount > 0) {
                message = count + " disabled fakeplayers removed";
            } else {
                message = count + " disabled chunkplayers removed";
            }
            context.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), true);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("No disabled fakeplayers found").withStyle(ChatFormatting.YELLOW), false);
        }
        return count;
    }
    
    private static int toggleVisualization(CommandContext<CommandSourceStack> context) {
        try {
            String name = StringArgumentType.getString(context, "name");
            
            if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
                context.getSource().sendFailure(Component.literal("Fakeplayer Manager is not initialized"));
                return 0;
            }
            
            ChunkloaderTarget entry = ChunkloaderForgeMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
                .filter(e -> e != null && name.equals(e.name()))
                .findFirst()
                .orElse(null);
            
            if (entry == null) {
                var config = ChunkloaderForgeMod.getConfig();
                List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();
                
                Component errorText;
                if (!similar.isEmpty()) {
                    errorText = Component.literal("Fakeplayer '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                        .withStyle(ChatFormatting.RED);
                } else {
                    errorText = Component.literal("Fakeplayer '" + name + "' not found").withStyle(ChatFormatting.RED);
                }
                context.getSource().sendFailure(errorText);
                return 0;
            }
            
            de.chunkloader.manager.ChunkloaderManager.ChunkKey key = new de.chunkloader.manager.ChunkloaderManager.ChunkKey(entry.chunkX(), entry.chunkZ());
            
            if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
                context.getSource().sendFailure(Component.literal("Fakeplayer Manager is not initialized"));
                return 0;
            }
            
            ChunkloaderForgeMod.getChunkloaderManager().toggleVisualization(key);
            boolean isActive = ChunkloaderForgeMod.getChunkloaderManager().isVisualizationActive(key);
            
            context.getSource().sendSuccess(() -> Component.literal("Chunk border visualization for '" + name + "' is now " + (isActive ? "enabled" : "disabled"))
                .withStyle(isActive ? ChatFormatting.GREEN : ChatFormatting.RED), true);
            
            return 1;
        } catch (Exception e) {

            context.getSource().sendFailure(Component.literal("An error occurred: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }
    
    private static int toggleVisualization3D(CommandContext<CommandSourceStack> context) {
        try {
            String name = StringArgumentType.getString(context, "name");
            
            if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
                context.getSource().sendFailure(Component.literal("Fakeplayer Manager is not initialized"));
                return 0;
            }
            
            ChunkloaderTarget entry = ChunkloaderForgeMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
                .filter(e -> e != null && name.equalsIgnoreCase(e.name()))
                .findFirst()
                .orElse(null);
            
            if (entry == null) {
                var config = ChunkloaderForgeMod.getConfig();
                List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();
                
                Component errorText;
                if (!similar.isEmpty()) {
                    errorText = Component.literal("Fakeplayer '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                        .withStyle(ChatFormatting.RED);
                } else {
                    errorText = Component.literal("Fakeplayer '" + name + "' not found").withStyle(ChatFormatting.RED);
                }
                context.getSource().sendFailure(errorText);
                return 0;
            }
            
            de.chunkloader.manager.ChunkloaderManager.ChunkKey key = 
                new de.chunkloader.manager.ChunkloaderManager.ChunkKey(entry.chunkX(), entry.chunkZ());
            
            if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
                context.getSource().sendFailure(Component.literal("Fakeplayer Manager is not initialized"));
                return 0;
            }
            
            ChunkloaderForgeMod.getChunkloaderManager().toggleVisualization3D(key);
            boolean isActive = ChunkloaderForgeMod.getChunkloaderManager().isVisualization3DActive(key);
            
            context.getSource().sendSuccess(() -> Component.literal("3D chunk visualization for '" + name + "' is now " + (isActive ? "enabled" : "disabled") + " (full height: -64 to 320)")
                .withStyle(isActive ? ChatFormatting.GREEN : ChatFormatting.RED), true);
            
            return 1;
        } catch (Exception e) {

            context.getSource().sendFailure(Component.literal("An error occurred: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }
    
    private static int toggleVisualization3DWithHeight(CommandContext<CommandSourceStack> context) {
        try {
            String name = StringArgumentType.getString(context, "name");
            int minY = IntegerArgumentType.getInteger(context, "minY");
            int maxY = IntegerArgumentType.getInteger(context, "maxY");
            
            if (minY >= maxY) {
                context.getSource().sendFailure(Component.literal("minY must be less than maxY").withStyle(ChatFormatting.RED));
                return 0;
            }
            
            if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
                context.getSource().sendFailure(Component.literal("Fakeplayer Manager is not initialized"));
                return 0;
            }
            
            ChunkloaderTarget entry = ChunkloaderForgeMod.getChunkloaderManager().getActiveChunkloaderEntries().stream()
                .filter(e -> e != null && name.equalsIgnoreCase(e.name()))
                .findFirst()
                .orElse(null);
            
            if (entry == null) {
                var config = ChunkloaderForgeMod.getConfig();
                List<String> similar = config != null ? config.findSimilarNames(name, 3) : List.of();
                
                Component errorText;
                if (!similar.isEmpty()) {
                    errorText = Component.literal("Fakeplayer '" + name + "' not found. Did you mean: " + String.join(", ", similar))
                        .withStyle(ChatFormatting.RED);
                } else {
                    errorText = Component.literal("Fakeplayer '" + name + "' not found").withStyle(ChatFormatting.RED);
                }
                context.getSource().sendFailure(errorText);
                return 0;
            }
            
            de.chunkloader.manager.ChunkloaderManager.ChunkKey key = 
                new de.chunkloader.manager.ChunkloaderManager.ChunkKey(entry.chunkX(), entry.chunkZ());
            
            if (ChunkloaderForgeMod.getChunkloaderManager() == null) {
                context.getSource().sendFailure(Component.literal("Fakeplayer Manager is not initialized"));
                return 0;
            }
            
            ChunkloaderForgeMod.getChunkloaderManager().toggleVisualization3D(key, minY, maxY);
            boolean isActive = ChunkloaderForgeMod.getChunkloaderManager().isVisualization3DActive(key);
            
            context.getSource().sendSuccess(() -> Component.literal("3D chunk visualization for '" + name + "' is now " + (isActive ? "enabled" : "disabled") + " (height: " + minY + " to " + maxY + ")")
                .withStyle(isActive ? ChatFormatting.GREEN : ChatFormatting.RED), true);
            
            return 1;
        } catch (Exception e) {

            context.getSource().sendFailure(Component.literal("An error occurred: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
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
            
            permissionConfig.grantPermission(player.getUUID(), "chunkloader.*");
            context.getSource().sendSuccess(() -> Component.literal("Granted all fakeplayer permissions to " + playerName)
                .withStyle(ChatFormatting.GREEN), true);
            
            player.sendSystemMessage(Component.literal("You have been granted all fakeplayer permissions!")
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
            context.getSource().sendSuccess(() -> Component.literal("Revoked all fakeplayer permissions from " + playerName)
                .withStyle(ChatFormatting.GREEN), true);
            
            player.sendSystemMessage(Component.literal("Your fakeplayer permissions have been revoked.")
                .withStyle(ChatFormatting.RED));
            
            return 1;
        } catch (Exception e) {

            context.getSource().sendFailure(Component.literal("An error occurred: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }
}

