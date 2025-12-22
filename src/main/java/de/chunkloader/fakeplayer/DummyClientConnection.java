package de.chunkloader.fakeplayer;

import io.netty.channel.embedded.EmbeddedChannel;
import org.jetbrains.annotations.Nullable;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.state.NetworkState;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;

public class DummyClientConnection extends ClientConnection {
    private static Field CHANNEL_FIELD;
    private static Field ADDRESS_FIELD;
    private PacketListener listener;
    
    private static void initFields() {
        if (CHANNEL_FIELD != null) return;
        try {
            Field[] fields = ClientConnection.class.getDeclaredFields();
            for (Field field : fields) {
                Class<?> fieldType = field.getType();
                
                if (fieldType.getName().contains("Channel") && CHANNEL_FIELD == null) {
                    CHANNEL_FIELD = field;
                    CHANNEL_FIELD.setAccessible(true);
                }
                if (fieldType.getName().contains("SocketAddress") && ADDRESS_FIELD == null) {
                    ADDRESS_FIELD = field;
                    ADDRESS_FIELD.setAccessible(true);
                }
            }
            
            if (CHANNEL_FIELD == null || ADDRESS_FIELD == null) {
                throw new RuntimeException("Could not find required fields in ClientConnection. Found fields: " + 
                    java.util.Arrays.toString(ClientConnection.class.getDeclaredFields()));
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to access ClientConnection internals: " + e.getMessage(), e);
        }
    }
    
    public DummyClientConnection() {
        super(NetworkSide.SERVERBOUND);
        initFields();
        try {
            CHANNEL_FIELD.set(this, new EmbeddedChannel());
            ADDRESS_FIELD.set(this, new InetSocketAddress("127.0.0.1", 0));
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to initialize DummyClientConnection", e);
        }
    }
    
    @Override
    public <T extends PacketListener> void transitionInbound(NetworkState<T> state, T listener) {
        this.listener = listener;
    }
    
    @Override
    public void setInitialPacketListener(PacketListener listener) {
        this.listener = listener;
    }
    
    @Override
    public PacketListener getPacketListener() {
        return listener;
    }
    
    @Override
    public void send(Packet<?> packet) {
    }
    
    @Override
    @SuppressWarnings("all")
    public void send(Packet<?> packet, io.netty.channel.@Nullable ChannelFutureListener listener) {
    }
    
    @Override
    public void disconnect(Text reason) {
    }
    
    @Override
    public boolean isOpen() {
        return true;
    }
    
    @Override
    public boolean isChannelAbsent() {
        return false;
    }
    
    public static ClientConnection create() {
        return new DummyClientConnection();
    }
}

