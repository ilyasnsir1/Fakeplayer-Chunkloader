package de.chunkloader.fakeplayer;

import com.mojang.authlib.GameProfile;
import de.chunkloader.ChunkloaderForgeMod;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import java.util.EnumSet;

public class ChunkloaderFakePlayer extends ServerPlayer {
    private final MinecraftServer server;
    private final net.minecraft.network.Connection networkConnection;
    private final CommonListenerCookie clientData;
    private boolean registered;
    private Component tabListName;
    
    
    private boolean visibleAsMarker = false;
    
    public ChunkloaderFakePlayer(MinecraftServer server, ServerLevel world, GameProfile profile) {
        this(server, world, CommonListenerCookie.createInitial(profile, false));
    }

    private ChunkloaderFakePlayer(MinecraftServer server, ServerLevel world, CommonListenerCookie cookie) {
        super(server, world, cookie.gameProfile(), cookie.clientInformation());
        this.server = server;
        this.clientData = cookie;
        this.networkConnection = DummyClientConnection.create();

        new ServerGamePacketListenerImpl(server, networkConnection, this, clientData);

        this.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        this.getAbilities().mayfly = true;
        this.getAbilities().invulnerable = true;
        this.getAbilities().flying = true;
        this.setInvisible(true);
        this.setSilent(true);
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setHealth(this.getMaxHealth());
        this.setVisibleAsMarker(true);
    }
    
    public void setVisibleAsMarker(boolean visible) {
        this.visibleAsMarker = visible;
        this.setInvisible(!visible);
        this.setInvulnerable(true);
        this.getAbilities().invulnerable = true;
    }
    
    public boolean isVisibleAsMarker() {
        return visibleAsMarker;
    }
    
    public void spawn() {
        if (registered) return;
        try {
            server.getPlayerList().placeNewPlayer(networkConnection, this, clientData);
        } catch (Exception e) {
        } finally {
            try {
                registered = server != null
                    && server.getPlayerList() != null
                    && server.getPlayerList().getPlayers().stream().anyMatch(p -> p != null && p.getUUID().equals(this.getUUID()));
            } catch (Exception ignored) {
                registered = true;
            }
        }
    }
    
    public void despawn() {
        try {
            if (server != null && server.getPlayerList() != null) {
            server.getPlayerList().remove(this);
            }
        } catch (Exception ignored) {
        }
        try {
            if (this.networkConnection != null) {
                this.networkConnection.disconnect(Component.literal("Fakeplayer despawned"));
            }
        } catch (Exception ignored) {
        } finally {
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
                    java.util.List.of(this)
                );
                
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
    
    public boolean handleHurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (visibleAsMarker && ChunkloaderForgeMod.getChunkloaderManager() != null) {
            if (ChunkloaderForgeMod.getChunkloaderManager().isChunkloaderMarker(this.getUUID())) {
                Entity attacker = source.getEntity();
                if (attacker instanceof Player player) {
                    if (player.isShiftKeyDown()) {
                        ChunkloaderForgeMod.getChunkloaderManager().removeChunkloaderByMarkerUuid(this.getUUID());
                    } else {
                        ChunkloaderForgeMod.getChunkloaderManager().handleMarkerDestroyed(this.getUUID());
                    }
                    this.remove(Entity.RemovalReason.KILLED);
                    return true;
                } else {
                    return false;
                }
            }
        }
        return false;
    }
    
    @Override
    public boolean canHarmPlayer(Player player) {
        return false;
    }
    
    @Override
    public void sendSystemMessage(Component message, boolean overlay) {
    }
    
    @Override
    public boolean isSpectator() {
        return false;
    }
}

