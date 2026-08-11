package de.chunkloader.permissions;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.command.permission.PermissionPredicate;
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
        if (source == null) {
            return false;
        }

        PermissionPredicate permissions = source.getPermissions();

        if (hasPermissionLevel(permissions, 4)) {
            return true;
        }

        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            return hasPermission(player, permission);
        }

        MinecraftServer server = source.getServer();
        boolean isDedicated = server != null && server.isDedicated();
        return hasPermissionLevel(permissions, isDedicated ? 2 : 0);
    }

    private static boolean hasPermissionFallback(ServerPlayerEntity player, String permission) {
        if (permission.equals(PERMISSION_ADMIN) ||
            permission.equals(PERMISSION_CLEAR) ||
            permission.equals(PERMISSION_RELOAD)) {
            return hasPermissionLevel(player.getPermissions(), 2);
        }

        MinecraftServer server = null;
        if (player.getEntityWorld() != null) {
            server = player.getEntityWorld().getServer();
        }
        boolean isDedicated = server != null && server.isDedicated();
        int requiredLevel = isDedicated ? 2 : 0;

        return hasPermissionLevel(player.getPermissions(), requiredLevel);
    }

    private static boolean hasPermissionLevel(PermissionPredicate predicate, int level) {
        if (predicate == null) {
            return false;
        }

        PermissionLevel permissionLevel = PermissionLevel.fromLevel(level);
        return predicate.hasPermission(new Permission.Level(permissionLevel));
    }

    public static boolean canUse(ServerPlayerEntity player) {
        return hasPermission(player, PERMISSION_USE) || hasPermission(player, PERMISSION_ADMIN);
    }

    public static boolean isAdmin(ServerPlayerEntity player) {
        return hasPermission(player, PERMISSION_ADMIN);
    }
}

