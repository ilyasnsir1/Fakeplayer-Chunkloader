package de.chunkloader.fakeplayer;

import com.mojang.authlib.GameProfile;
import de.chunkloader.ChunkloaderMod;
import de.chunkloader.permissions.PermissionManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.player.PlayerModelPart;

public class ChunkloaderFakePlayer extends ServerPlayer {
    private final MinecraftServer server;
    private final net.minecraft.network.Connection netConnection;
    private final CommonListenerCookie clientData;
    private boolean registered;
    private Component tabListName;

    private static final int ALL_SKIN_LAYERS = java.util.Arrays.stream(PlayerModelPart.values())
            .mapToInt(PlayerModelPart::getMask)
            .reduce(0, (layers, layer) -> layers | layer);
    private static final ClientInformation FAKEPLAYER_OPTIONS = createFakeplayerOptions();

    private static ClientInformation createFakeplayerOptions() {
        ClientInformation defaults = ClientInformation.createDefault();
        return new ClientInformation(
                defaults.language(),
                0,
                defaults.chatVisibility(),
                defaults.chatColors(),
                ALL_SKIN_LAYERS,
                defaults.mainHand(),
                defaults.textFilteringEnabled(),
                defaults.allowsListing(),
                defaults.particleStatus());
    }

    private boolean visibleAsMarker = false;

    public ChunkloaderFakePlayer(MinecraftServer server, ServerLevel world, GameProfile profile) {
        super(server, world, profile, FAKEPLAYER_OPTIONS);
        this.server = server;
        this.netConnection = DummyClientConnection.create();
        this.clientData = CommonListenerCookie.createInitial(profile, false);

        try {
            new ServerGamePacketListenerImpl(server, this.netConnection, this, this.clientData);
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.warn(
                    "Error during networkHandler initialization for fake player {}: {}. " +
                            "This may be caused by incompatible mods (e.g. polymer-core, connectiblechains). " +
                            "The fakeplayer will continue to work, but some networking features may be limited.",
                    profile.name(), e.getMessage());
        }

        try {
            this.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            this.getAbilities().mayfly = true;
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
            server.getPlayerList().placeNewPlayer(this.netConnection, this, this.clientData);
            registered = true;
            return true;
        } catch (Exception e) {
            ChunkloaderMod.LOGGER.error("Failed to spawn fake player {}: {}", getGameProfile().name(), e.getMessage(), e);
            try {
                if (server.getPlayerList().getPlayers().contains(this)
                        || server.getPlayerList().getPlayer(this.getUUID()) == this) {
                    server.getPlayerList().remove(this);
                }
            } catch (Exception cleanup) {
                ChunkloaderMod.LOGGER.warn("Failed to clean up partially spawned fake player {}: {}",
                        getGameProfile().name(), cleanup.getMessage());
            }
            SyntheticPlayerContext.unmark(this.getUUID());
            registered = false;
            return false;
        } finally {
            SyntheticPlayerContext.exitSpawn();
        }
    }

    public void despawn() {
        if (!registered) {
            SyntheticPlayerContext.unmark(this.getUUID());
            return;
        }
        try {
            server.getPlayerList().remove(this);
            this.netConnection.disconnect(Component.literal("removed"));
        } finally {
            SyntheticPlayerContext.unmark(this.getUUID());
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
    public Component getName() {
        Component customName = this.getCustomName();
        if (customName != null) {
            return Component.literal(customName.getString());
        }

        Component name = super.getName();
        if (name != null) {
            return name;
        }

        String profileName = this.getGameProfile().name();
        return profileName != null ? Component.literal(profileName) : Component.literal("Fakeplayer");
    }

    @Override
    public Component getDisplayName() {
        Component customName = this.getCustomName();
        if (customName != null) {
            return customName;
        }
        return super.getDisplayName();
    }

    public void setPlayerListName(Component name) {
        this.tabListName = name;
        if (server != null && server.getPlayerList() != null && registered) {
            ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, this);
            server.getPlayerList().broadcastAll(packet);
        }
    }

    @Override
    public Component getTabListDisplayName() {
        if (tabListName != null) {
            return tabListName;
        }
        return super.getTabListDisplayName();
    }

    @Override
    public boolean shouldShowName() {
        return this.isCustomNameVisible();
    }

    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel world, net.minecraft.world.damagesource.DamageSource source,
            float amount) {
        if (visibleAsMarker && ChunkloaderMod.getChunkloaderManager() != null) {
            if (ChunkloaderMod.getChunkloaderManager().isChunkloaderMarker(this.getUUID())) {
                Entity attacker = source.getEntity();
                if (attacker instanceof net.minecraft.world.entity.player.Player player) {
                    if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                            && !PermissionManager.canUse(serverPlayer)) {
                        serverPlayer.sendSystemMessage(Component.translatable("message.chunkloader.no_permission_interact"));
                        return false;
                    }
                    if (player.isShiftKeyDown()) {
                        ChunkloaderMod.getChunkloaderManager().removeChunkloaderByMarkerUuid(this.getUUID());
                        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                            serverPlayer.sendSystemMessage(Component.literal("Player deleted"));
                        }
                    } else {
                        ChunkloaderMod.getChunkloaderManager().handleMarkerDestroyed(this.getUUID());
                        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                            String keyName = de.chunkloader.util.KeybindHelper.getDisabledChunkloadersKeyName();
                            serverPlayer.sendSystemMessage(
                                    Component.literal("Player disabled (Press " + keyName + " to open disabled list)"));
                        }
                    }
                    this.remove(Entity.RemovalReason.KILLED);
                    return true;
                } else {
                    return false;
                }
            }
            return super.hurtServer(world, source, amount);
        }
        return false;
    }

    @Override
    public void awardStat(net.minecraft.stats.Stat<?> stat, int amount) {
    }

    @Override
    public void resetStat(net.minecraft.stats.Stat<?> stat) {
    }

    @Override
    public int awardRecipes(java.util.Collection<net.minecraft.world.item.crafting.RecipeHolder<?>> recipes) {
        return 0;
    }

    @Override
    public void triggerRecipeCrafted(net.minecraft.world.item.crafting.RecipeHolder<?> recipe,
            java.util.List<net.minecraft.world.item.ItemStack> ingredients) {
    }

    @Override
    public void awardRecipesByKey(
            java.util.List<net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>>> recipes) {
    }

    @Override
    public int resetRecipes(java.util.Collection<net.minecraft.world.item.crafting.RecipeHolder<?>> recipes) {
        return 0;
    }

    @Override
    public void sendSystemMessage(Component message) {
    }

    @Override
    public void sendSystemMessage(Component message, boolean overlay) {
    }

    @Override
    public void sendOverlayMessage(Component message) {
    }

    @Override
    public void sendChatMessage(net.minecraft.network.chat.OutgoingChatMessage message, boolean filterMaskEnabled,
            net.minecraft.network.chat.ChatType.Bound bound) {
    }

    @Override
    public boolean allowsListing() {
        return false;
    }
}
