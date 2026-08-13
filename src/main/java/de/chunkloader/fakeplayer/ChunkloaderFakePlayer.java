package de.chunkloader.fakeplayer;

import com.mojang.authlib.GameProfile;
import de.chunkloader.ChunkloaderForgeMod;
import de.chunkloader.permissions.PermissionManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.GameType;
import java.util.Arrays;
import java.util.EnumSet;

public class ChunkloaderFakePlayer extends ServerPlayer {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final MinecraftServer server;
    private final net.minecraft.network.Connection networkConnection;
    private final CommonListenerCookie clientData;
    private boolean registered;
    private Component tabListName;
    private boolean visibleAsMarker = false;
    private boolean mobTarget = false;

    private static final int ALL_SKIN_LAYERS = Arrays.stream(PlayerModelPart.values())
            .mapToInt(PlayerModelPart::getMask)
            .reduce(0, (layers, layer) -> layers | layer);

    private static ClientInformation createFakeplayerInformation() {
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
                defaults.particleStatus()
        );
    }

    public ChunkloaderFakePlayer(MinecraftServer server, ServerLevel world, GameProfile profile) {
        this(server, world, CommonListenerCookie.createInitial(profile, false));
    }

    private ChunkloaderFakePlayer(MinecraftServer server, ServerLevel world, CommonListenerCookie cookie) {
        super(server, world, cookie.gameProfile(), createFakeplayerInformation());
        this.server = server;
        this.clientData = cookie;
        this.networkConnection = DummyClientConnection.create();

        new ServerGamePacketListenerImpl(server, networkConnection, this, clientData);

        this.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        var abilities = this.getAbilities();
        abilities.invulnerable = true;
        abilities.mayfly = true;
        abilities.flying = true;
        this.setInvisible(true);
        this.setSilent(true);
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setHealth(this.getMaxHealth());
        this.setVisibleAsMarker(true);
    }

    public void setVisibleAsMarker(boolean visible) {
        this.visibleAsMarker = visible;
        applyMobTargetState();
        if (visible) {
            this.setInvulnerable(false);
        }
    }

    public boolean isVisibleAsMarker() {
        return visibleAsMarker;
    }

    public void setMobTarget(boolean mobTarget) {
        this.mobTarget = mobTarget;
        applyMobTargetState();
    }

    public boolean isMobTarget() {
        return mobTarget;
    }

    private void applyVisibilityState() {
        boolean show = visibleAsMarker || mobTarget;
        this.setInvisible(!show);
    }

    private void applyMobTargetState() {
        applyVisibilityState();
        this.getAbilities().invulnerable = !mobTarget;
        this.setInvulnerable(!mobTarget && !visibleAsMarker);
        this.onUpdateAbilities();
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        if (!mobTarget) {
            return false;
        }
        return super.canBeSeenAsEnemy();
    }

    @Override
    public boolean isInvulnerable() {
        if (mobTarget) {
            return false;
        }
        return super.isInvulnerable();
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
            server.getPlayerList().placeNewPlayer(networkConnection, this, clientData);
            registered = true;
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to spawn fake player {}: {}", getGameProfile().name(), e.getMessage(), e);
            try {
                if (server.getPlayerList().getPlayers().contains(this)
                        || server.getPlayerList().getPlayer(this.getUUID()) == this) {
                    server.getPlayerList().remove(this);
                }
            } catch (Exception cleanup) {
                LOGGER.warn("Failed to clean up partially spawned fake player {}: {}",
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
            if (server != null && server.getPlayerList() != null) {
                server.getPlayerList().remove(this);
            }
            if (this.networkConnection != null) {
                this.networkConnection.disconnect(Component.literal("Fakeplayer despawned"));
            }
        } catch (Exception ignored) {
        } finally {
            SyntheticPlayerContext.unmark(this.getUUID());
            registered = false;
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.setHealth(this.getMaxHealth());
        this.fallDistance = 0.0F;
    }

    @Override
    public Component getName() {
        Component customName = this.getCustomName();
        if (customName != null) {
            return Component.literal(customName.getString());
        }

        Component baseName = super.getName();
        if (baseName != null) {
            return baseName;
        }

        return Component.literal("Fakeplayer");
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
        Component oldName = this.tabListName;
        this.tabListName = name;

        if (registered && server != null && server.getPlayerList() != null &&
                (oldName == null || !oldName.equals(name))) {
            try {
                ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
                        EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
                        java.util.List.of(this));

                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player != null && player.connection != null) {
                        player.connection.send(packet);
                    }
                }
            } catch (Exception e) {
            }
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
        
        if (visibleAsMarker && ChunkloaderForgeMod.getChunkloaderManager() != null) {
            if (ChunkloaderForgeMod.getChunkloaderManager().isChunkloaderMarker(this.getUUID())) {
                Entity attacker = source.getEntity();
                if (attacker instanceof Player player) {
                    if (player instanceof ServerPlayer serverPlayer && !PermissionManager.canUse(serverPlayer)) {
                        serverPlayer.sendSystemMessage(
                                Component.translatable("message.chunkloader.no_permission_interact"));
                        return false;
                    }
                    if (player.isShiftKeyDown()) {
                        ChunkloaderForgeMod.getChunkloaderManager().removeChunkloaderByMarkerUuid(this.getUUID());
                        if (player instanceof ServerPlayer serverPlayer) {
                            serverPlayer.sendSystemMessage(Component.literal("Player deleted"));
                        }
                    } else {
                        ChunkloaderForgeMod.getChunkloaderManager().handleMarkerDestroyed(this.getUUID());
                        if (player instanceof ServerPlayer serverPlayer) {
                            String keyName = de.chunkloader.util.KeybindHelper.getDisabledChunkloadersKeyName();
                            serverPlayer.sendSystemMessage(
                                    Component.literal("Player disabled (Press " + keyName + " to open disabled list)"));
                        }
                    }
                    this.remove(Entity.RemovalReason.KILLED);
                    return true;
                }
                return false;
            }
            return super.hurtServer(world, source, amount);
        }
        return false;
    }

    @Override
    public boolean canHarmPlayer(Player player) {
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
    public void sendSystemMessage(Component message, boolean overlay) {
    }

    @Override
    public boolean isSpectator() {
        return false;
    }
}
