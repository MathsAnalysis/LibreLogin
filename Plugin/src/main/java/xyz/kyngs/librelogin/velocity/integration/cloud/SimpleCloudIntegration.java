package xyz.kyngs.librelogin.velocity.integration.cloud;

import eu.thesimplecloud.api.CloudAPI;
import eu.thesimplecloud.api.event.service.CloudServiceStartedEvent;
import eu.thesimplecloud.api.event.service.CloudServiceUpdatedEvent;
import eu.thesimplecloud.api.eventapi.CloudEventHandler;
import eu.thesimplecloud.api.eventapi.IListener;
import eu.thesimplecloud.api.service.ICloudService;
import eu.thesimplecloud.api.service.ServiceState;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.velocity.VelocityLibreLogin;

import java.util.Map;

public class SimpleCloudIntegration implements IListener {

    protected final VelocityLibreLogin plugin;

    public SimpleCloudIntegration(VelocityLibreLogin plugin) {
        this.plugin = plugin;
        CloudAPI.getInstance().getCloudServiceManager().getAllCachedObjects().forEach(this::addServer);
    }

    private void addServer(ICloudService cloudService){
        if (cloudService.isProxy()) return;

        var server = plugin.providePlatformHandle().getServer(cloudService.getName(), true);
        for (Map.Entry<String, String> entry : plugin.getConfiguration().get(ConfigurationKeys.LOBBY).entries()) {
            String forced = entry.getKey();
            String serverName = entry.getValue();
            if (!cloudService.getName().contains(serverName)) continue;
            if (server == null) {
                plugin.getLogger().warn("Lobby server/world " + cloudService.getName() + " not found!");
                return;
            }
            plugin.getServerHandler().registerLobbyServer(server, forced);
            plugin.getLogger().info("Registered server " + cloudService.getName() + " with forced " + forced);
            return;
        }

        if (cloudService.getGroupName().equals("lobby")) {
            if (server == null) {
                plugin.getLogger().warn("Lobby server/world " + cloudService.getName() + " not found!");
                return;
            }
            plugin.getServerHandler().registerLobbyServer(server);
            plugin.getLogger().info("Registered server " + cloudService.getName() + " with forced root");
            return;
        }

        if (cloudService.getGroupName().equals("auth")) {
            if (server == null) {
                plugin.getLogger().warn("Limbo server/world " + cloudService.getName() + " not found!");
                return;
            }
            plugin.getServerHandler().registerLimboServer(server);
            plugin.getLogger().info("Registered server " + cloudService.getName() + " as limbo");
        }
    }

    @CloudEventHandler
    public void onStart(CloudServiceStartedEvent event) {
        ICloudService cloudService = event.getCloudService();
        addServer(cloudService);
    }

    @CloudEventHandler
    public void onUnregister(CloudServiceUpdatedEvent event) {
        if(event.getCloudService().getState() != ServiceState.CLOSED)  return;
        ICloudService cloudService = event.getCloudService();
        if (cloudService.isProxy()) return;
        var server = plugin.providePlatformHandle().getServer(cloudService.getName(), true);
        if (server == null) return;
        for (Map.Entry<String, String> entry : plugin.getConfiguration().get(ConfigurationKeys.LOBBY).entries()) {
            String forced = entry.getKey();
            String serverName = entry.getValue();
            if (cloudService.getName().contains(serverName)) {
                plugin.getServerHandler().getLobbyServers().get(forced).remove(server);
                return;
            }
        }
        if (cloudService.getGroupName().equals("lobby")) {
            plugin.getServerHandler().getLobbyServers().get("root").remove(server);
            return;
        }

        if (cloudService.getGroupName().equals("auth")) {
            plugin.getServerHandler().getLimboServers().remove(server);
        }
    }

}
