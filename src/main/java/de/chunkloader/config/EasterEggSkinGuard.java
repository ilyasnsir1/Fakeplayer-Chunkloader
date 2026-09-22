package de.chunkloader.config;

import com.google.gson.JsonObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class EasterEggSkinGuard {
    private static final String PROOF_FIELD = "easterEggProof";
    private static final String INDEX_FIELD = "easterEggSkinIndex";
    private static final byte[] KEY = createKey();

    private EasterEggSkinGuard() {
    }

    private static byte[] createKey() {
        try {

            return MessageDigest.getInstance("SHA-256")
                    .digest("chunkloader:easter-egg-skin-v1|do-not-edit-json".getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static String sign(String dimension, int chunkX, int chunkZ, int skinIndex) {
        String dim = dimension != null ? dimension : "minecraft:overworld";
        int normalized = Math.floorMod(skinIndex, 2);
        String payload = dim + "|" + chunkX + "|" + chunkZ + "|" + normalized;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(KEY, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign easter-egg skin index", e);
        }
    }

    public static boolean verify(String dimension, int chunkX, int chunkZ, int skinIndex, String proof) {
        if (proof == null || proof.isBlank()) {
            return false;
        }
        String expected = sign(dimension, chunkX, chunkZ, skinIndex);
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = proof.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        return a.length == b.length && MessageDigest.isEqual(a, b);
    }

    public static Integer readVerifiedIndex(JsonObject chunkObj, String dimension, int chunkX, int chunkZ) {
        if (chunkObj == null
                || !chunkObj.has(INDEX_FIELD)
                || chunkObj.get(INDEX_FIELD).isJsonNull()) {
            return null;
        }
        int index;
        try {
            index = chunkObj.get(INDEX_FIELD).getAsInt();
        } catch (Exception e) {
            return null;
        }
        String proof = chunkObj.has(PROOF_FIELD) && !chunkObj.get(PROOF_FIELD).isJsonNull()
                ? chunkObj.get(PROOF_FIELD).getAsString()
                : null;
        if (!verify(dimension, chunkX, chunkZ, index, proof)) {
            return null;
        }
        return Math.floorMod(index, 2);
    }

    public static void writeSignedIndex(JsonObject chunkObj, String dimension, int chunkX, int chunkZ, int skinIndex) {
        int normalized = Math.floorMod(skinIndex, 2);
        chunkObj.addProperty(INDEX_FIELD, normalized);
        chunkObj.addProperty(PROOF_FIELD, sign(dimension, chunkX, chunkZ, normalized));
    }

    public static void migrateUnsignedProofs(JsonObject root) {
        if (root == null || !root.has("chunkloaders") || !root.get("chunkloaders").isJsonArray()) {
            return;
        }
        for (var element : root.getAsJsonArray("chunkloaders")) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject chunkObj = element.getAsJsonObject();
            if (!chunkObj.has(INDEX_FIELD) || chunkObj.get(INDEX_FIELD).isJsonNull()) {
                continue;
            }
            int index;
            int chunkX;
            int chunkZ;
            try {
                index = chunkObj.get(INDEX_FIELD).getAsInt();
                chunkX = chunkObj.get("x").getAsInt();
                chunkZ = chunkObj.get("z").getAsInt();
            } catch (Exception e) {
                continue;
            }
            String dimension = chunkObj.has("dimension")
                    ? chunkObj.get("dimension").getAsString()
                    : "minecraft:overworld";
            String proof = chunkObj.has(PROOF_FIELD) && !chunkObj.get(PROOF_FIELD).isJsonNull()
                    ? chunkObj.get(PROOF_FIELD).getAsString()
                    : null;
            if (verify(dimension, chunkX, chunkZ, index, proof)) {
                continue;
            }
            writeSignedIndex(chunkObj, dimension, chunkX, chunkZ, index);
        }
    }
}
