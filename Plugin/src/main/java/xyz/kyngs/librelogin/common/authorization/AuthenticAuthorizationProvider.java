/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.authorization;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import xyz.kyngs.librelogin.api.authorization.AuthorizationProvider;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent;
import xyz.kyngs.librelogin.api.totp.TOTPData;
import xyz.kyngs.librelogin.common.AuthenticHandler;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.event.events.AuthenticAuthenticatedEvent;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class AuthenticAuthorizationProvider<P, S> extends AuthenticHandler<P, S> implements AuthorizationProvider<P> {

    private final Map<P, Boolean> unAuthorized;
    private final Map<P, String> awaiting2FA;
    private final Map<P,Long> timeAuthorized;
    private final Cache<UUID, EmailVerifyData> emailConfirmCache;
    private final Cache<UUID, String> passwordResetCache;

    private final Cache<UUID, Boolean> unConfirmedEmailCache;


    public AuthenticAuthorizationProvider(AuthenticLibreLogin<P, S> plugin) {
        super(plugin);
        unAuthorized = new ConcurrentHashMap<>();
        awaiting2FA = new ConcurrentHashMap<>();


        timeAuthorized = new ConcurrentHashMap<>();
        var millis = plugin.getConfiguration().get(ConfigurationKeys.MILLISECONDS_TO_REFRESH_NOTIFICATION);

        if (millis > 0) {
            plugin.repeat(this::notifyUnauthorized, 0, millis);
        }

        plugin.repeat(this::broadcastActionbars, 0, 1000);

        emailConfirmCache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();

        passwordResetCache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();

        unConfirmedEmailCache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
    }

    public Cache<UUID, EmailVerifyData> getEmailConfirmCache() {
        return emailConfirmCache;
    }

    public Cache<UUID, String> getPasswordResetCache() {
        return passwordResetCache;
    }

    public void onExit(P player) {
        stopTracking(player);
        awaiting2FA.remove(player);
        timeAuthorized.remove(player);
        emailConfirmCache.invalidate(platformHandle.getUUIDForPlayer(player));
        passwordResetCache.invalidate(platformHandle.getUUIDForPlayer(player));
    }


    @Override
    public boolean isAwaitingEmailConfirmation(P player) {
        return unConfirmedEmailCache.getIfPresent(platformHandle.getUUIDForPlayer(player)) != null;
    }

    @Override
    public boolean isAuthorized(P player) {
        return !unAuthorized.containsKey(player);
    }

    @Override
    public boolean isAwaiting2FA(P player) {
        return awaiting2FA.containsKey(player);
    }

    @Override
    public void authorize(User user, P player, AuthenticatedEvent.AuthenticationReason reason) {
        if (isAuthorized(player)) {
            throw new IllegalStateException("Player is already authorized");
        }
        stopTracking(player);

        user.setLastAuthentication(Timestamp.valueOf(LocalDateTime.now()));
        user.setIp(platformHandle.getIP(player));
        plugin.getDatabaseProvider().updateUser(user);

        var audience = platformHandle.getAudienceForPlayer(player);

        /*if (user.getEmail() == null && reason != AuthenticatedEvent.AuthenticationReason.PREMIUM) {
            //require email : You must have an email to play on this server.
            audience.sendMessage(plugin.getMessages().getMessage("require-email"));
            unConfirmedEmailCache.put(platformHandle.getUUIDForPlayer(player), true);
            return;
        }*/

        audience.clearTitle();
        audience.sendActionBar(Component.empty());
        plugin.getEventProvider().fire(plugin.getEventTypes().authenticated, new AuthenticAuthenticatedEvent<>(user, player, plugin, reason));
        plugin.authorize(player, user, audience);
    }

    @Override
    public boolean confirmTwoFactorAuth(P player, Integer code, User user) {
        var secret = awaiting2FA.get(player);
        if (plugin.getTOTPProvider().verify(code, secret)) {
            user.setSecret(secret);
            plugin.getDatabaseProvider().updateUser(user);
            return true;
        }
        return false;
    }

    public void startTracking(User user, P player) {
        var audience = platformHandle.getAudienceForPlayer(player);

        unAuthorized.put(player, user.isRegistered());
        timeAuthorized.put(player,System.currentTimeMillis());
        audience.sendMessage(plugin.getMessages().getMessage(user.isRegistered() ? "prompt-login" : "prompt-register"));

        plugin.cancelOnExit(plugin.delay(() -> {
            if (!unAuthorized.containsKey(player)) return;
            sendInfoMessage(user.isRegistered(), audience);
        }, 250), player);

        var limit = plugin.getConfiguration().get(ConfigurationKeys.SECONDS_TO_AUTHORIZE);

        if (limit > 0) {
            plugin.cancelOnExit(plugin.delay(() -> {
                if (!unAuthorized.containsKey(player)) return;
                platformHandle.kick(player, plugin.getMessages().getMessage("kick-time-limit"));
            }, limit * 1000L), player);
        }



        sendInfoMessage(user.isRegistered(), audience);
    }

    private void broadcastActionbars() {
        var wrong = new HashSet<P>();
        unAuthorized.forEach((player, registered) -> {
            var audience = platformHandle.getAudienceForPlayer(player);

            if (audience == null) {
                wrong.add(player);
                return;
            }

            sendActionBar(registered, audience, player);

        });

        wrong.forEach(unAuthorized::remove);
    }

    private void sendActionBar(boolean registered, Audience audience, P player) {
        var time = timeAuthorized.get(player);
        if(time == null){
            return;
        }

        var limit = plugin.getConfiguration().get(ConfigurationKeys.SECONDS_TO_AUTHORIZE);
        long remain = (limit * 1000L)- (System.currentTimeMillis() - time);
        int secondi = (int) remain/1000;

        if (plugin.getConfiguration().get(ConfigurationKeys.USE_ACTION_BAR)) {
            audience.sendActionBar(plugin.getMessages().getMessage(registered ? "action-bar-login" : "action-bar-register","%time%",String.valueOf(secondi)));
        }
    }

    private void sendInfoMessage(boolean registered, Audience audience) {
        //audience.sendMessage(plugin.getMessages().getMessage(registered ? "prompt-login" : "prompt-register"));
        if (!plugin.getConfiguration().get(ConfigurationKeys.USE_TITLES)) return;
        var toRefresh = plugin.getConfiguration().get(ConfigurationKeys.MILLISECONDS_TO_REFRESH_NOTIFICATION);
        //noinspection UnstableApiUsage
        audience.showTitle(Title.title(
                plugin.getMessages().getMessage(registered ? "title-login" : "title-register"),
                plugin.getMessages().getMessage(registered ? "sub-title-login" : "sub-title-register"),
                Title.Times.of(
                        Duration.ofMillis(0),
                        Duration.ofMillis(toRefresh > 0 ?
                                (long) (toRefresh * 1.1) :
                                10000
                        ),
                        Duration.ofMillis(0)
                )
        ));
    }

    public void stopTracking(P player) {
        unAuthorized.remove(player);
    }

    public void notifyUnauthorized() {
        var wrong = new HashSet<P>();
        unAuthorized.forEach((player, registered) -> {
            var audience = platformHandle.getAudienceForPlayer(player);

            if (audience == null) {
                wrong.add(player);
                return;
            }

            sendInfoMessage(registered, audience);

        });

        wrong.forEach(unAuthorized::remove);
    }

    public record EmailVerifyData(String email, String token, UUID uuid) {
    }

    public void beginTwoFactorAuth(User user, P player, TOTPData data) {
        awaiting2FA.put(player, data.secret());

        var limbo = plugin.getServerHandler().chooseLimboServer(user, player);

        if (limbo == null) {
            platformHandle.kick(player, plugin.getMessages().getMessage("kick-no-limbo"));
            return;
        }

        platformHandle.movePlayer(player, limbo).whenComplete((t, e) -> {
            if (t != null || e != null) awaiting2FA.remove(player);
        });
    }
}
