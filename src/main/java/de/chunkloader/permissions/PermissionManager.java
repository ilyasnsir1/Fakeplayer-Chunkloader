package de.chunkloader.permissions;

import de.chunkloader.ChunkloaderMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;

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
        if (source == null) {
            return false;
        }

        PermissionSet permissions = source.permissions();

        if (hasPermissionLevel(permissions, 4)) {
            return true;
        }

        try {
            ServerPlayer player = source.getPlayerOrException();
            return hasPermission(player, permission);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            MinecraftServer server = source.getServer();
            boolean isDedicated = server != null && server.isDedicatedServer();
            return hasPermissionLevel(permissions, isDedicated ? 2 : 0);
        }
    }

    private static boolean hasPermissionFallback(ServerPlayer player, String permission) {
        if (permission.equals(PERMISSION_ADMIN) ||
            permission.equals(PERMISSION_CLEAR) ||
            permission.equals(PERMISSION_RELOAD)) {
            return hasPermissionLevel(player.permissions(), 2);
        }

        MinecraftServer server = null;
        if (player.level() != null) {
            server = player.level().getServer();
        }
        boolean isDedicated = server != null && server.isDedicatedServer();
        int requiredLevel = isDedicated ? 2 : 0;

        return hasPermissionLevel(player.permissions(), requiredLevel);
    }

    private static boolean hasPermissionLevel(PermissionSet permissions, int level) {
        if (permissions == null) {
            return false;
        }

        return permissions.hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)));
    }

    public static boolean canUse(ServerPlayer player) {
        return hasPermission(player, PERMISSION_USE) || hasPermission(player, PERMISSION_ADMIN);
    }

    public static boolean isAdmin(ServerPlayer player) {
        return hasPermission(player, PERMISSION_ADMIN);
    }
}

