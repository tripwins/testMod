package dev.prisonsutils.client.api;

import java.nio.charset.StandardCharsets;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Raw JSON payload carried on the {@code cosmicapi:main} plugin channel.
 *
 * <p>The entire packet body is the UTF-8 JSON string with no length prefix — the common plugin-message
 * convention. The exact wire framing isn't documented yet; if the live API turns out to length-prefix
 * the body, this codec is the only thing that needs to change.
 */
public record CosmicPayload(String json) implements CustomPayload {

    public static final CustomPayload.Id<CosmicPayload> ID =
            new CustomPayload.Id<>(Identifier.of("cosmicapi", "main"));

    public static final PacketCodec<PacketByteBuf, CosmicPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeBytes(value.json().getBytes(StandardCharsets.UTF_8)),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new CosmicPayload(new String(bytes, StandardCharsets.UTF_8));
            });

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
