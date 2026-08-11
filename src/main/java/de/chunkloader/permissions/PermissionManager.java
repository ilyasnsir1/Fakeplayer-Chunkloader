package de.chunkloader.permissions;

import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

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
    }

    public static void initConfig(MinecraftServer server) {
        if (permissionConfig == null && server != null) {
            permissionConfig = PermissionConfig.load(server);
        }
    }

    public static PermissionConfig getPermissionConfig() {
        return permissionConfig;
    }

    public static boolean hasPermission(ServerPlayer player, String permission) {
        if (player == null) {
            return false;
        }
        if (permissionConfig != null) {
            if (permissionConfig.hasPermission(player.getUUID(), permission)) {
                return true;
            }
        }

        return hasPermissionFallback(player, permission);
    }

    public static boolean hasPermission(CommandSourceStack source, String permission) {
        if (source.hasPermission(4)) {
            return true;
        }

        try {
            ServerPlayer player = source.getPlayerOrException();
            return hasPermission(player, permission);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            boolean isDedicated = source.getServer().isDedicatedServer();
            return source.hasPermission(isDedicated ? 2 : 0);
        }
    }

    private static boolean hasPermissionFallback(ServerPlayer player, String permission) {
        if (permission.equals(PERMISSION_ADMIN) ||
            permission.equals(PERMISSION_CLEAR) ||
            permission.equals(PERMISSION_RELOAD)) {
            return player.hasPermissions(2);
        }

        MinecraftServer server = player.level().getServer();
        boolean isDedicated = server != null && server.isDedicatedServer();
        int requiredLevel = isDedicated ? 2 : 0;

        return player.hasPermissions(requiredLevel);
    }

    public static boolean canUse(ServerPlayer player) {
        return hasPermission(player, PERMISSION_USE) || hasPermission(player, PERMISSION_ADMIN);
    }

    public static boolean isAdmin(ServerPlayer player) {
        return hasPermission(player, PERMISSION_ADMIN);
    }
}

