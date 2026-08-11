package de.chunkloader.fakeplayer;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.jetbrains.annotations.Nullable;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;

public class DummyClientConnection extends Connection {
    private static Field CHANNEL_FIELD;
    private static Field ADDRESS_FIELD;
    private PacketListener listener;

    private static void initFields() {
        if (CHANNEL_FIELD != null)
            return;
        try {
            Field[] fields = Connection.class.getDeclaredFields();
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
                        java.util.Arrays.toString(Connection.class.getDeclaredFields()));
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to access Connection internals: " + e.getMessage(), e);
        }
    }

    private static EmbeddedChannel createResilientChannel() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addFirst("fakeplayer_exception_handler", new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            }
        });
        channel.pipeline().addFirst("fakeplayer_outbound_handler", new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                promise.setSuccess();
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            }
        });
        return channel;
    }

    public DummyClientConnection() {
        super(PacketFlow.SERVERBOUND);
        initFields();
        try {
            CHANNEL_FIELD.set(this, createResilientChannel());
            ADDRESS_FIELD.set(this, new InetSocketAddress("127.0.0.1", 0));
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to initialize DummyClientConnection", e);
        }
    }

    @Override
    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T listener) {
        this.listener = listener;
    }

    @Override
    public void setListenerForServerboundHandshake(PacketListener listener) {
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
    public void disconnect(Component reason) {
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    public static Connection create() {
        return new DummyClientConnection();
    }
}
