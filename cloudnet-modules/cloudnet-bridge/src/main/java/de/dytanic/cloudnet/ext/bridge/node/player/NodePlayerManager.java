package de.dytanic.cloudnet.ext.bridge.node.player;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import de.dytanic.cloudnet.CloudNet;
import de.dytanic.cloudnet.common.concurrent.ITask;
import de.dytanic.cloudnet.common.document.gson.JsonDocument;
import de.dytanic.cloudnet.database.IDatabase;
import de.dytanic.cloudnet.driver.CloudNetDriver;
import de.dytanic.cloudnet.driver.service.ServiceEnvironmentType;
import de.dytanic.cloudnet.ext.bridge.BridgeConstants;
import de.dytanic.cloudnet.ext.bridge.player.*;
import de.dytanic.cloudnet.ext.bridge.player.executor.DefaultPlayerExecutor;
import de.dytanic.cloudnet.ext.bridge.player.executor.PlayerExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class NodePlayerManager implements IPlayerManager {

    private final Map<UUID, CloudPlayer> onlineCloudPlayers = new ConcurrentHashMap<>();

    private final String databaseName;

    public NodePlayerManager(String databaseName) {
        this.databaseName = databaseName;
    }

    /**
     * @deprecated IPlayerManager should be accessed through the {@link de.dytanic.cloudnet.common.registry.IServicesRegistry}
     */
    @Deprecated
    public static NodePlayerManager getInstance() {
        return (NodePlayerManager) CloudNetDriver.getInstance().getServicesRegistry().getFirstService(IPlayerManager.class);
    }


    public IDatabase getDatabase() {
        return CloudNet.getInstance().getDatabaseProvider().getDatabase(databaseName);
    }


    @Override
    public int getOnlineCount() {
        return this.onlineCloudPlayers.size();
    }

    @Override
    public long getRegisteredCount() {
        return this.getDatabase().getDocumentsCount();
    }

    @Nullable
    @Override
    public CloudPlayer getOnlinePlayer(@NotNull UUID uniqueId) {
        return onlineCloudPlayers.get(uniqueId);
    }

    @Override
    public @NotNull List<? extends ICloudPlayer> getOnlinePlayers(@NotNull String name) {
        Preconditions.checkNotNull(name);

        return this.onlineCloudPlayers.values().stream().filter(cloudPlayer -> cloudPlayer.getName().equalsIgnoreCase(name)).collect(Collectors.toList());
    }

    @Override
    public @NotNull List<? extends ICloudPlayer> getOnlinePlayers(@NotNull ServiceEnvironmentType environment) {
        Preconditions.checkNotNull(environment);

        return this.onlineCloudPlayers.values().stream().filter(cloudPlayer -> (cloudPlayer.getLoginService() != null && cloudPlayer.getLoginService().getEnvironment() == environment) ||
                (cloudPlayer.getConnectedService() != null && cloudPlayer.getConnectedService().getEnvironment() == environment)).collect(Collectors.toList());
    }

    @Override
    public @NotNull List<CloudPlayer> getOnlinePlayers() {
        return new ArrayList<>(this.onlineCloudPlayers.values());
    }

    @Override
    public ICloudOfflinePlayer getOfflinePlayer(@NotNull UUID uniqueId) {
        Preconditions.checkNotNull(uniqueId);

        JsonDocument jsonDocument = this.getDatabase().get(uniqueId.toString());

        return jsonDocument != null ? jsonDocument.toInstanceOf(CloudOfflinePlayer.TYPE) : null;
    }

    @Override
    public @NotNull List<? extends ICloudOfflinePlayer> getOfflinePlayers(@NotNull String name) {
        Preconditions.checkNotNull(name);

        return this.getDatabase().get(new JsonDocument("name", name)).stream().map(jsonDocument -> (CloudOfflinePlayer) jsonDocument.toInstanceOf(CloudOfflinePlayer.TYPE)).collect(Collectors.toList());
    }

    @Override
    @Deprecated
    public List<? extends ICloudOfflinePlayer> getRegisteredPlayers() {
        List<? extends ICloudOfflinePlayer> cloudOfflinePlayers = new ArrayList<>();

        this.getDatabase().iterate((s, jsonDocument) -> cloudOfflinePlayers.add(jsonDocument.toInstanceOf(CloudOfflinePlayer.TYPE)));

        return cloudOfflinePlayers;
    }

    @Override
    @NotNull
    public ITask<Integer> getOnlineCountAsync() {
        return this.schedule(this::getOnlineCount);
    }

    @Override
    @NotNull
    public ITask<Long> getRegisteredCountAsync() {
        return this.getDatabase().getDocumentsCountAsync();
    }

    @Override
    @NotNull
    public ITask<ICloudPlayer> getOnlinePlayerAsync(@NotNull UUID uniqueId) {
        return this.schedule(() -> this.getOnlinePlayer(uniqueId));
    }

    @Override
    @NotNull
    public ITask<List<? extends ICloudPlayer>> getOnlinePlayersAsync(@NotNull String name) {
        return this.schedule(() -> this.getOnlinePlayers(name));
    }

    @Override
    @NotNull
    public ITask<List<? extends ICloudPlayer>> getOnlinePlayersAsync(@NotNull ServiceEnvironmentType environment) {
        return this.schedule(() -> this.getOnlinePlayers(environment));
    }

    @Override
    @NotNull
    public ITask<List<? extends ICloudPlayer>> getOnlinePlayersAsync() {
        return this.schedule(this::getOnlinePlayers);
    }

    @Override
    @NotNull
    public ITask<ICloudOfflinePlayer> getOfflinePlayerAsync(@NotNull UUID uniqueId) {
        return this.schedule(() -> this.getOfflinePlayer(uniqueId));
    }

    @Override
    @NotNull
    public ITask<List<? extends ICloudOfflinePlayer>> getOfflinePlayersAsync(@NotNull String name) {
        return this.schedule(() -> this.getOfflinePlayers(name));
    }

    @Override
    @NotNull
    public ITask<List<? extends ICloudOfflinePlayer>> getRegisteredPlayersAsync() {
        return this.schedule(this::getRegisteredPlayers);
    }

    @Override
    public void updateOfflinePlayer(@NotNull ICloudOfflinePlayer cloudOfflinePlayer) {
        Preconditions.checkNotNull(cloudOfflinePlayer);

        this.updateOfflinePlayer0(cloudOfflinePlayer);
        CloudNetDriver.getInstance().getMessenger().sendChannelMessage(
                BridgeConstants.BRIDGE_CUSTOM_MESSAGING_CHANNEL_PLAYER_API_CHANNEL_NAME,
                "update_offline_cloud_player",
                new JsonDocument(
                        "offlineCloudPlayer", cloudOfflinePlayer
                )
        );
    }

    public void updateOfflinePlayer0(ICloudOfflinePlayer cloudOfflinePlayer) {
        this.getDatabase().update(cloudOfflinePlayer.getUniqueId().toString(), JsonDocument.newDocument(cloudOfflinePlayer));
    }

    @Override
    public void updateOnlinePlayer(@NotNull ICloudPlayer cloudPlayer) {
        Preconditions.checkNotNull(cloudPlayer);

        this.updateOnlinePlayer0(cloudPlayer);
        CloudNetDriver.getInstance().getMessenger().sendChannelMessage(
                BridgeConstants.BRIDGE_CUSTOM_MESSAGING_CHANNEL_PLAYER_API_CHANNEL_NAME,
                "update_online_cloud_player",
                new JsonDocument(
                        "cloudPlayer", cloudPlayer
                )
        );
    }

    public void updateOnlinePlayer0(ICloudPlayer cloudPlayer) {
        if (this.onlineCloudPlayers.containsKey(cloudPlayer.getUniqueId())) {
            this.onlineCloudPlayers.put(cloudPlayer.getUniqueId(), (CloudPlayer) cloudPlayer);
        }
        this.updateOfflinePlayer0(CloudOfflinePlayer.of(cloudPlayer));
    }

    @Override
    public @NotNull PlayerExecutor getPlayerExecutor(@NotNull UUID uniqueId) {
        return new DefaultPlayerExecutor(uniqueId);
    }

    @Override
    public void broadcastMessage(@NotNull String message) {
        Preconditions.checkNotNull(message);

        CloudNetDriver.getInstance().getMessenger().sendChannelMessage(
                BridgeConstants.BRIDGE_CUSTOM_MESSAGING_CHANNEL_PLAYER_API_CHANNEL_NAME,
                "broadcast_message",
                new JsonDocument()
                        .append("message", message)
        );
    }

    @Override
    public void broadcastMessage(@NotNull String message, @NotNull String permission) {
        Preconditions.checkNotNull(message);
        Preconditions.checkNotNull(permission);

        CloudNetDriver.getInstance().getMessenger().sendChannelMessage(
                BridgeConstants.BRIDGE_CUSTOM_MESSAGING_CHANNEL_PLAYER_API_CHANNEL_NAME,
                "broadcast_message",
                new JsonDocument()
                        .append("message", message)
                        .append("permission", permission)
        );
    }


    @NotNull
    private <T> ITask<T> schedule(Callable<T> callable) {
        return CloudNet.getInstance().getTaskScheduler().schedule(callable);
    }

    public Map<UUID, CloudPlayer> getOnlineCloudPlayers() {
        return this.onlineCloudPlayers;
    }

    public String getDatabaseName() {
        return this.databaseName;
    }

    public void loginPlayer(NetworkConnectionInfo networkConnectionInfo, NetworkPlayerServerInfo networkPlayerServerInfo) {
        CloudPlayer cloudPlayer = this.getOnlinePlayer(networkConnectionInfo.getUniqueId());

        if (cloudPlayer == null) {
            cloudPlayer = this.getOnlineCloudPlayers().values().stream()
                    .filter(cloudPlayer1 -> cloudPlayer1.getName().equalsIgnoreCase(networkConnectionInfo.getName()) &&
                            cloudPlayer1.getLoginService().getUniqueId().equals(networkConnectionInfo.getNetworkService().getUniqueId()))
                    .findFirst()
                    .orElse(null);

            if (cloudPlayer == null) {
                ICloudOfflinePlayer cloudOfflinePlayer = this.getOrRegisterOfflinePlayer(networkConnectionInfo);

                cloudPlayer = new CloudPlayer(
                        cloudOfflinePlayer,
                        networkConnectionInfo.getNetworkService(),
                        networkConnectionInfo.getNetworkService(),
                        networkConnectionInfo,
                        networkPlayerServerInfo
                );

                cloudPlayer.setLastLoginTimeMillis(System.currentTimeMillis());
                this.getOnlineCloudPlayers().put(cloudPlayer.getUniqueId(), cloudPlayer);
            }
        }

        if (networkPlayerServerInfo != null) {
            cloudPlayer.setConnectedService(networkPlayerServerInfo.getNetworkService());
            cloudPlayer.setNetworkPlayerServerInfo(networkPlayerServerInfo);

            if (networkPlayerServerInfo.getXBoxId() != null) {
                cloudPlayer.setXBoxId(networkPlayerServerInfo.getXBoxId());
            }
        }

        cloudPlayer.setName(networkConnectionInfo.getName());

        this.updateOnlinePlayer0(cloudPlayer);
    }

    public ICloudOfflinePlayer getOrRegisterOfflinePlayer(NetworkConnectionInfo networkConnectionInfo) {
        ICloudOfflinePlayer cloudOfflinePlayer = this.getOfflinePlayer(networkConnectionInfo.getUniqueId());

        if (cloudOfflinePlayer == null) {
            cloudOfflinePlayer = new CloudOfflinePlayer(
                    networkConnectionInfo.getUniqueId(),
                    networkConnectionInfo.getName(),
                    null,
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    networkConnectionInfo
            );

            this.getDatabase().insert(
                    cloudOfflinePlayer.getUniqueId().toString(),
                    JsonDocument.newDocument(cloudOfflinePlayer)
            );
        }

        return cloudOfflinePlayer;
    }

    public void logoutPlayer(CloudPlayer cloudPlayer) {
        this.getOnlineCloudPlayers().remove(cloudPlayer.getUniqueId());
        cloudPlayer.setLastNetworkConnectionInfo(cloudPlayer.getNetworkConnectionInfo());
        this.updateOnlinePlayer0(cloudPlayer);
    }

    private void logoutPlayer(UUID uniqueId, String name, Predicate<CloudPlayer> predicate) {
        CloudPlayer cloudPlayer = uniqueId != null ?
                this.getOnlinePlayer(uniqueId) :
                this.getOnlineCloudPlayers().values().stream()
                        .filter(cloudPlayer1 -> cloudPlayer1.getName().equals(name))
                        .findFirst()
                        .orElse(null);

        if (cloudPlayer != null && (predicate == null || predicate.test(cloudPlayer))) {
            this.logoutPlayer(cloudPlayer);
        }
    }

    public void logoutPlayer(NetworkConnectionInfo networkConnectionInfo) {
        this.logoutPlayer(networkConnectionInfo.getUniqueId(), networkConnectionInfo.getName(),
                cloudPlayer -> cloudPlayer != null &&
                        cloudPlayer.getLoginService().getUniqueId().equals(networkConnectionInfo.getNetworkService().getUniqueId())
        );
    }

    public void logoutPlayer(UUID uniqueId, String name) {
        this.logoutPlayer(uniqueId, name, null);
    }

}
