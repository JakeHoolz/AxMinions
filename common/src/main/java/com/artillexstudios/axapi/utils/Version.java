package com.artillexstudios.axapi.utils;

import com.artillexstudios.axapi.nms.NMSHandlers;
import com.artillexstudios.axapi.reflection.FastMethodInvoker;
import com.artillexstudios.axapi.utils.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public enum Version {
    v1_21_11(774, "v1_21_R7", Collections.singletonList("1.21.11")),
    FUTURE_RELEASE(Integer.MAX_VALUE, "FUTURE_RELEASE", Collections.singletonList("FUTURE_RELEASE")),
    UNKNOWN(-1, "UNKNOWN", Collections.singletonList("UNKNOWN"));

    private static final Int2ObjectArrayMap<Version> versionMap;
    private static Version serverVersion;
    private static int protocolVersion;
    private final List<String> versions;
    private final int protocolId;
    private final String nmsVersion;
    private final BooleanSupplier supplier;

    Version(int protocolId, String nmsVersion, List<String> versions, BooleanSupplier supplier) {
        this.protocolId = protocolId;
        this.versions = versions;
        this.nmsVersion = nmsVersion;
        this.supplier = supplier;
    }

    Version(int protocolId, String nmsVersion, List<String> versions) {
        this(protocolId, nmsVersion, versions, () -> true);
    }

    public static Version getPlayerVersion(Player player) {
        return versionMap.get(NMSHandlers.getNmsHandler().getProtocolVersionId(player));
    }

    public static Version getServerVersion() {
        return serverVersion;
    }

    public static int getProtocolVersion() {
        return protocolVersion;
    }

    public boolean isNewerThan(Version version) {
        return this.protocolId > version.protocolId;
    }

    public boolean isNewerThanOrEqualTo(Version version) {
        return this.protocolId >= version.protocolId;
    }

    public boolean isOlderThan(Version version) {
        return this.protocolId < version.protocolId;
    }

    public boolean isOlderThanOrEqualTo(Version version) {
        return this.protocolId <= version.protocolId;
    }

    public List<String> getVersions() {
        return this.versions;
    }

    public int getProtocolId() {
        return this.protocolId;
    }

    public String getNMSVersion() {
        return this.nmsVersion;
    }

    private static Integer resolveProtocolVersion() {
        String[] methodNames = {"c", "getProtocolVersion", "d"};
        for (String methodName : methodNames) {
            try {
                FastMethodInvoker invoker = FastMethodInvoker.createSilently("net.minecraft.SharedConstants", methodName);
                Object result = invoker.invoke(null);
                if (result instanceof Integer) {
                    return (Integer) result;
                }
            } catch (Exception ignored) {
                // Try next option.
            }
        }

        try {
            Object unsafe = Bukkit.class.getMethod("getUnsafe").invoke(null);
            Object result = unsafe.getClass().getMethod("getProtocolVersion").invoke(unsafe);
            if (result instanceof Integer) {
                return (Integer) result;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Version resolveServerVersionFromBukkit() {
        String minecraftVersion;
        try {
            minecraftVersion = Bukkit.getMinecraftVersion();
        } catch (Exception exception) {
            return null;
        }

        for (Version value : Version.values()) {
            if (!value.versions.contains(minecraftVersion)) {
                continue;
            }
            return value;
        }
        return null;
    }

    static {
        versionMap = new Int2ObjectArrayMap<>();
        Integer resolvedProtocol = resolveProtocolVersion();
        for (Version value : Version.values()) {
            if (!value.supplier.getAsBoolean()) {
                continue;
            }
            versionMap.put(value.protocolId, value);
            if (resolvedProtocol != null && value.protocolId == resolvedProtocol) {
                serverVersion = value;
                break;
            }
        }

        if (serverVersion == null) {
            serverVersion = resolveServerVersionFromBukkit();
            if (serverVersion != null) {
                resolvedProtocol = serverVersion.protocolId;
            }
        }

        if (serverVersion == null) {
            LogUtils.error("Failed to fetch the version information!", new Object[0]);
            serverVersion = UNKNOWN;
        }

        protocolVersion = resolvedProtocol != null ? resolvedProtocol : serverVersion.protocolId;
    }
}
