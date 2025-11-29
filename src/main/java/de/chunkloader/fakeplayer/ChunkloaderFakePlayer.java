package de.chunkloader.fakeplayer;

import com.mojang.authlib.GameProfile;
import de.chunkloader.ChunkloaderMod;
import net.minecraft.entity.Entity;
import net.minecraft.network.ClientConnection;
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
    
    private static final SyncedClientOptions DEFAULT_OPTIONS = SyncedClientOptions.createDefault();
    
    private boolean visibleAsMarker = false;
    
    public ChunkloaderFakePlayer(MinecraftServer server, ServerWorld world, GameProfile profile) {
        super(server, world, profile, DEFAULT_OPTIONS);
        this.server = server;
        this.connection = DummyClientConnection.create();
        this.clientData = ConnectedClientData.createDefault(profile, false);
        this.networkHandler = new ServerPlayNetworkHandler(server, connection, this, clientData);
        this.connection.setInitialPacketListener(this.networkHandler);
        this.interactionManager.changeGameMode(GameMode.SURVIVAL);
        this.getAbilities().allowFlying = true;
        this.getAbilities().invulnerable = true;
        this.getAbilities().flying = true;
        this.setInvisible(true);
        this.setSilent(true);
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setHealth(getMaxHealth());
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
    
    public void spawn() {
        if (registered) return;
        server.getPlayerManager().onPlayerConnect(connection, this, clientData);
        registered = true;
    }
    
    public void despawn() {
        if (!registered) return;
        server.getPlayerManager().remove(this);
        this.connection.disconnect(Text.literal("chunkloader removed"));
        registered = false;
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
    public boolean damage(net.minecraft.server.world.ServerWorld world, net.minecraft.entity.damage.DamageSource source, float amount) {
        if (visibleAsMarker && ChunkloaderMod.getChunkloaderManager() != null) {
            if (ChunkloaderMod.getChunkloaderManager().isChunkloaderMarker(this.getUuid())) {
                Entity attacker = source.getAttacker();
                if (attacker instanceof net.minecraft.entity.player.PlayerEntity player) {
                    if (player.isSneaking()) {
                    ChunkloaderMod.getChunkloaderManager().removeChunkloaderByMarkerUuid(this.getUuid());
                } else {
                    ChunkloaderMod.getChunkloaderManager().handleMarkerDestroyed(this.getUuid());
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
    public void sendMessage(Text message, boolean overlay) {
    }
    
    @Override
    public boolean allowsServerListing() {
        return false;
    }
}

