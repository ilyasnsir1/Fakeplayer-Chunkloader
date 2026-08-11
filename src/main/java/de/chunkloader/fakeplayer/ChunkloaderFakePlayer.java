package de.chunkloader.fakeplayer;

import com.mojang.authlib.GameProfile;
import de.chunkloader.ChunkloaderMod;
import de.chunkloader.permissions.PermissionManager;
import net.minecraft.entity.Entity;
import net.minecraft.network.ClientConnection;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

public class ChunkloaderFakePlayer extends ServerPlayerEntity {
    private final MinecraftServer server;
    private final ClientConnection connection;
    private final ConnectedClientData clientData;
    private boolean registered;
    private Text tabListName;

    private static final int ALL_SKIN_LAYERS = java.util.Arrays.stream(PlayerModelPart.values())
            .mapToInt(PlayerModelPart::getBitFlag)
            .reduce(0, (layers, layer) -> layers | layer);

    private static final SyncedClientOptions FAKEPLAYER_OPTIONS = createFakeplayerOptions();

    private static SyncedClientOptions createFakeplayerOptions() {
        SyncedClientOptions defaults = SyncedClientOptions.createDefault();
        return new SyncedClientOptions(
                defaults.language(),
                0,
                defaults.chatVisibility(),
                defaults.chatColorsEnabled(),
                ALL_SKIN_LAYERS,
                defaults.mainArm(),
                defaults.filtersText(),
                defaults.allowsServerListing(),
                defaults.particleStatus());
    }

    private boolean visibleAsMarker = false;

    public ChunkloaderFakePlayer(MinecraftServer server, ServerWorld world, GameProfile profile) {
        super(server, world, profile, FAKEPLAYER_OPTIONS);
        this.server = server;
        this.connection = DummyClientConnection.create();
        this.clientData = ConnectedClientData.createDefault(profile, false);

        try {
            this.networkHandler = new ServerPlayNetworkHandler(server, connection, this, clientData);
            this.connection.setInitialPacketListener(this.networkHandler);
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.warn(
                    "Error during networkHandler initialization for fake player {}: {}. " +
                            "This may be caused by incompatible mods (e.g. polymer-core, connectiblechains). " +
                            "The fakeplayer will continue to work, but some networking features may be limited.",
                    profile.name(), e.getMessage());
            if (this.networkHandler == null) {
                try {
                    this.networkHandler = new ServerPlayNetworkHandler(server, connection, this, clientData);
                    this.connection.setInitialPacketListener(this.networkHandler);
                } catch (Exception e2) {
                    ChunkloaderMod.LOGGER.error(
                            "Failed to create networkHandler for fake player {} after retry: {}",
                            profile.name(), e2.getMessage(), e2);
                }
            }
        }

        try {
            this.interactionManager.changeGameMode(GameMode.SURVIVAL);
            this.getAbilities().allowFlying = true;
            this.getAbilities().invulnerable = true;
            this.getAbilities().flying = true;
            this.setInvisible(true);
            this.setSilent(true);
            this.setNoGravity(true);
            this.setInvulnerable(true);
            this.setHealth(getMaxHealth());
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.warn(
                    "Error during fake player setup for {}: {}. Continuing with limited functionality.",
                    profile.name(), e.getMessage());
        }
    }

    public void setVisibleAsMarker(boolean visible) {
        this.visibleAsMarker = visible;
        this.setInvisible(!visible);
        if (visible) {
            this.setInvulnerable(false);
        }
    }

    public boolean isVisibleAsMarker() {
        return visibleAsMarker;
    }

    public boolean isRegistered() {
        return registered;
    }

    public boolean spawn() {
        if (registered) {
            return true;
        }

        SyntheticPlayerContext.enterSpawn();
        SyntheticPlayerContext.mark(this);
        try {
            server.getPlayerManager().onPlayerConnect(connection, this, clientData);
            registered = true;
            return true;
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Failed to spawn fake player {}: {}", getGameProfile().name(), e.getMessage(), e);
            try {
                if (server.getPlayerManager().getPlayerList().contains(this)
                        || server.getPlayerManager().getPlayer(this.getUuid()) == this) {
                    server.getPlayerManager().remove(this);
                }
            } catch (Exception cleanup) {
                ChunkloaderMod.LOGGER.warn("Failed to clean up partially spawned fake player {}: {}",
                        getGameProfile().name(), cleanup.getMessage());
            }
            SyntheticPlayerContext.unmark(this.getUuid());
            registered = false;
            return false;
        } finally {
            SyntheticPlayerContext.exitSpawn();
        }
    }

    public void despawn() {
        if (!registered) {
            SyntheticPlayerContext.unmark(this.getUuid());
            return;
        }
        try {
            server.getPlayerManager().remove(this);
            this.connection.disconnect(Text.literal("removed"));
        } finally {
            SyntheticPlayerContext.unmark(this.getUuid());
            registered = false;
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.setHealth(getMaxHealth());
        this.fallDistance = 0.0F;
    }

    @Override
    public Text getName() {
        Text customName = this.getCustomName();
        if (customName != null) {
            return Text.literal(customName.getString());
        }

        Text name = super.getName();
        if (name != null) {
            return name;
        }

        String profileName = this.getGameProfile().name();
        return profileName != null ? Text.literal(profileName) : Text.literal("Fakeplayer");
    }

    @Override
    public Text getDisplayName() {
        Text customName = this.getCustomName();
        if (customName != null) {
            return customName;
        }
        return super.getDisplayName();
    }

    public void setPlayerListName(Text name) {
        this.tabListName = name;
        if (server != null && server.getPlayerManager() != null && registered) {
            PlayerListS2CPacket packet = new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, this);
            server.getPlayerManager().sendToAll(packet);
        }
    }

    @Override
    public Text getPlayerListName() {
        if (tabListName != null) {
            return tabListName;
        }
        return super.getPlayerListName();
    }

    @Override
    public boolean shouldRenderName() {
        return this.isCustomNameVisible();
    }

    @Override
    public boolean damage(net.minecraft.server.world.ServerWorld world, net.minecraft.entity.damage.DamageSource source,
            float amount) {
        if (visibleAsMarker && ChunkloaderMod.getChunkloaderManager() != null) {
            if (ChunkloaderMod.getChunkloaderManager().isChunkloaderMarker(this.getUuid())) {
                Entity attacker = source.getAttacker();
                if (attacker instanceof net.minecraft.entity.player.PlayerEntity player) {
                    if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer
                            && !PermissionManager.canUse(serverPlayer)) {
                        serverPlayer.sendMessage(Text.translatable("message.chunkloader.no_permission_interact"),
                                false);
                        return false;
                    }
                    if (player.isSneaking()) {
                        ChunkloaderMod.getChunkloaderManager().removeChunkloaderByMarkerUuid(this.getUuid());
                        if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                            serverPlayer.sendMessage(Text.literal("Player deleted"), false);
                        }
                    } else {
                        ChunkloaderMod.getChunkloaderManager().handleMarkerDestroyed(this.getUuid());
                        if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                            String keyName = de.chunkloader.util.KeybindHelper.getDisabledChunkloadersKeyName();
                            serverPlayer.sendMessage(
                                    Text.literal("Player disabled (Press " + keyName + " to open disabled list)"),
                                    false);
                        }
                    }
                    this.remove(Entity.RemovalReason.KILLED);
                    return true;
                } else {
                    return false;
                }
            }
            return super.damage(world, source, amount);
        }
        return false;
    }

    @Override
    public boolean shouldDamagePlayer(net.minecraft.entity.player.PlayerEntity player) {
        return false;
    }

    @Override
    public void increaseStat(net.minecraft.stat.Stat<?> stat, int amount) {
    }

    @Override
    public void resetStat(net.minecraft.stat.Stat<?> stat) {
    }

    @Override
    public int unlockRecipes(java.util.Collection<net.minecraft.recipe.RecipeEntry<?>> recipes) {
        return 0;
    }

    @Override
    public void onRecipeCrafted(net.minecraft.recipe.RecipeEntry<?> recipe,
            java.util.List<net.minecraft.item.ItemStack> ingredients) {
    }

    @Override
    public void unlockRecipes(
            java.util.List<net.minecraft.registry.RegistryKey<net.minecraft.recipe.Recipe<?>>> recipes) {
    }

    @Override
    public int lockRecipes(java.util.Collection<net.minecraft.recipe.RecipeEntry<?>> recipes) {
        return 0;
    }

    @Override
    public void sendMessage(Text message, boolean overlay) {
    }

    @Override
    public boolean allowsServerListing() {
        return false;
    }
}
