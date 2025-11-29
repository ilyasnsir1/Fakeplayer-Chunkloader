package de.chunkloader.permissions;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public class PermissionManager {
    
    private static PermissionConfig permissionConfig = null;
    
    public static final String PERMISSION_BASE = "chunkloader";
    public static final String PERMISSION_USE = "chunkloader.use";
    public static final String PERMISSION_ADMIN = "chunkloader.admin";
    public static final String PERMISSION_ADD = "chunkloader.add";
    public static final String PERMISSION_REMOVE = "chunkloader.remove";
    public static final String PERMISSION_LIST = "chunkloader.list";
    public static final String PERMISSION_INFO = "chunkloader.info";
    public static final String PERMISSION_TOGGLE = "chunkloader.toggle";
    public static final String PERMISSION_CLEAR = "chunkloader.clear";
    public static final String PERMISSION_RELOAD = "chunkloader.reload";
    public static final String PERMISSION_SET_RADIUS = "chunkloader.setradius";
    public static final String PERMISSION_SET_MOB_SPAWNING = "chunkloader.setmobspawning";
    
    public static void init() {
        permissionConfig = null;
        ChunkloaderMod.LOGGER.info("Permission manager reset.");
    }

    public static void initConfig(MinecraftServer server) {
        if (permissionConfig == null && server != null) {
            permissionConfig = PermissionConfig.load(server);
            ChunkloaderMod.LOGGER.info("Built-in permission system initialized.");
        }
    }
    
    public static PermissionConfig getPermissionConfig() {
        return permissionConfig;
    }
    
    public static boolean hasPermission(ServerPlayerEntity player, String permission) {
        if (player == null) {
            return false;
        }
        if (permissionConfig != null) {
            if (permissionConfig.hasPermission(player.getUuid(), permission)) {
                return true;
            }
        }
        
        return hasPermissionFallback(player, permission);
    }
    
    public static boolean hasPermission(ServerCommandSource source, String permission) {
        if (source.hasPermissionLevel(4)) {
            return true;
        }
        
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            return hasPermission(player, permission);
        }
        
        boolean isDedicated = source.getServer().isDedicated();
        return source.hasPermissionLevel(isDedicated ? 2 : 0);
    }
    
    private static boolean hasPermissionFallback(ServerPlayerEntity player, String permission) {
        if (permission.equals(PERMISSION_ADMIN) || 
            permission.equals(PERMISSION_CLEAR) || 
            permission.equals(PERMISSION_RELOAD)) {
            return player.hasPermissionLevel(2);
        }
        
        boolean isDedicated = player.getEntityWorld().getServer().isDedicated();
        int requiredLevel = isDedicated ? 2 : 0;
        
        return player.hasPermissionLevel(requiredLevel);
    }
    
    public static boolean canUse(ServerPlayerEntity player) {
        return hasPermission(player, PERMISSION_USE) || hasPermission(player, PERMISSION_ADMIN);
    }
    
    public static boolean isAdmin(ServerPlayerEntity player) {
        return hasPermission(player, PERMISSION_ADMIN);
    }
}

