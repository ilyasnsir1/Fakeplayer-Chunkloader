package de.chunkloader.fakeplayer;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;

public class DummyClientConnection extends net.minecraft.network.Connection {
    private static Field CHANNEL_FIELD;
    private static Field ADDRESS_FIELD;
    
    private static void initFields() {
        if (CHANNEL_FIELD != null) return;
        try {
            Field[] fields = net.minecraft.network.Connection.class.getDeclaredFields();
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
                throw new RuntimeException("Could not find required fields in Connection. Found fields: " + 
                    java.util.Arrays.toString(net.minecraft.network.Connection.class.getDeclaredFields()));
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to access Connection internals: " + e.getMessage(), e);
        }
    }
    
    public DummyClientConnection() {
        super(PacketFlow.SERVERBOUND);
        initFields();
        try {
            CHANNEL_FIELD.set(this, new EmbeddedChannel());
            ADDRESS_FIELD.set(this, new InetSocketAddress("127.0.0.1", 0));
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to initialize DummyClientConnection", e);
        }
    }
    
    @Override
    public void send(Packet<?> packet) {
    }
    
    @Override
    public boolean isConnected() {
        return true;
    }
    
    public static net.minecraft.network.Connection create() {
        return new DummyClientConnection();
    }
}

