/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.command.commands.mail;

import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Syntax;
import net.kyori.adventure.audience.Audience;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.authorization.AuthenticAuthorizationProvider;
import xyz.kyngs.librelogin.common.command.InvalidCommandArgument;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.util.GeneralUtil;
import xyz.kyngs.librelogin.common.util.RateLimiter;

import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

@CommandAlias("setemail|addmail|mail|registermail")

public class SetEMailCommand<P> extends EMailCommand<P> {

    private final RateLimiter<UUID> limiter = new RateLimiter<>(1, TimeUnit.MINUTES);

    public SetEMailCommand(AuthenticLibreLogin<P, ?> premium) {
        super(premium);
    }

    @Default
    @Syntax("{@@syntax.set-email}")
    @CommandCompletion("%autocomplete.set-email")
    public CompletionStage<Void> onSetMail(Audience sender, P player, UUID uuid, String mail) {
        return runAsync(() -> {
            var user = getUser(player);
            if (user.getPremiumUUID() == null) {
                sender.sendMessage(getMessage("error-premium"));
                return;
            }

            if(getDatabaseProvider().getByEmail(mail) != null) {
                sender.sendMessage(getMessage("error-mail-already-used"));
                return;
            }

            if (limiter.tryAndLimit(uuid)) {
                throw new InvalidCommandArgument(getMessage("error-mail-throttle"));
            }
            if (mailHandler.iaAllowedMail(mail)) {
                throw new InvalidCommandArgument(getMessage("error-mail-not-allowed"));
            }

            var token = GeneralUtil.generateAlphanumericText(16);

            sender.sendMessage(getMessage("info-mail-sending"));


            try {
                mailHandler.sendVerificationMail(mail, token, user.getLastNickname());
                getAuthorizationProvider().getEmailConfirmCache().put(uuid, new AuthenticAuthorizationProvider.EmailVerifyData(mail, token, uuid));
            } catch (Exception e) {
                if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                    getLogger().debug("Cannot send verification mail to " + mail + " for " + player);
                    e.printStackTrace();
                }
                throw new InvalidCommandArgument(getMessage("error-mail-not-sent"));
            }

            sender.sendMessage(getMessage("info-verification-mail-sent"));
        });
    }
}