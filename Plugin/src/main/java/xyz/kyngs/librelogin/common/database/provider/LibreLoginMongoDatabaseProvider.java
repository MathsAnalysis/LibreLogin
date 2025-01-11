/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.database.provider;

import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.crypto.HashedPassword;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.database.connector.MongoDatabaseConnector;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.database.AuthenticDatabaseProvider;
import xyz.kyngs.librelogin.common.database.AuthenticUser;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

public class LibreLoginMongoDatabaseProvider extends AuthenticDatabaseProvider<MongoDatabaseConnector> {

    public LibreLoginMongoDatabaseProvider(MongoDatabaseConnector connector, AuthenticLibreLogin<?, ?> plugin) {
        super(connector, plugin);
    }

    @Override
    public Collection<User> getByIP(String ip) {
        plugin.reportMainThread();
        return connector.runQuery(collection -> {
            return collection.find(Filters.eq("ip", ip)).into(new ArrayList<>()).stream().map(this::getUserFromResult).toList();
        });
    }

    @Override
    public User getByName(String name) {
        plugin.reportMainThread();
        return connector.runQuery(collection -> {
            return getUserFromResult(collection.find(Filters.eq("last_nickname", name)).first());
        });
    }

    @Override
    public Collection<User> getAllUsers() {
        plugin.reportMainThread();
        return connector.runQuery(collection -> {
            return collection.find().into(new ArrayList<>()).stream().map(this::getUserFromResult).toList();
        });
    }

    @Override
    public User getByUUID(UUID uuid) {
        plugin.reportMainThread();
        return connector.runQuery(collection -> {
            return getUserFromResult(collection.find(Filters.eq("uuid", uuid.toString())).first());
        });
    }

    @Override
    public User getByPremiumUUID(UUID uuid) {
        plugin.reportMainThread();
        return connector.runQuery(collection -> {
            return getUserFromResult(collection.find(Filters.eq("premium_uuid", uuid.toString())).first());
        });
    }

    @Override
    public User getByEmail(String email) {
        plugin.reportMainThread();
        return connector.runQuery(collection -> {
            return getUserFromResult(collection.find(Filters.eq("email", email)).first());
        });
    }

    @Nullable
    private User getUserFromResult(Document document) {
        if (document == null) return null;

        var id = UUID.fromString(document.getString("uuid"));
        var premiumUUID = document.getString("premium_uuid");
        var hashedPassword = document.getString("hashed_password");
        var salt = document.getString("salt");
        var algo = document.getString("algo");
        var lastNickname = document.getString("last_nickname");
        var joinDate = document.getLong("joined") == null ? null : new Timestamp(document.getLong("joined"));
        var lastSeen = document.getLong("last_seen") == null ? null : new Timestamp(document.getLong("last_seen"));

        var lastAuthentication = document.getLong("last_authentication") == null ? null : new Timestamp(document.getLong("last_authentication"));
        return new AuthenticUser(
                id,
                premiumUUID == null ? null : UUID.fromString(premiumUUID),
                hashedPassword == null ? null : new HashedPassword(
                        hashedPassword,
                        salt,
                        algo
                ),
                lastNickname,
                joinDate,
                lastSeen,
                document.getString("secret"),
                document.getString("ip"),
                lastAuthentication,
                document.getString("last_server"),
                document.getString("email")
        );


    }

    @Override
    public void insertUser(User user) {
        plugin.reportMainThread();
        connector.runQuery(collection -> {
            Document document = new Document();
            document.put("uuid", user.getUuid().toString());
            document.put("premium_uuid", user.getPremiumUUID() == null ? null : user.getPremiumUUID().toString());
            document.put("hashed_password", user.getHashedPassword() == null ? null : user.getHashedPassword().hash());
            document.put("salt", user.getHashedPassword() == null ? null : user.getHashedPassword().salt());
            document.put("algo", user.getHashedPassword() == null ? null : user.getHashedPassword().algo());
            document.put("last_nickname", user.getLastNickname());
            document.put("joined", user.getJoinDate() == null ? null : user.getJoinDate().getTime());
            document.put("last_seen", user.getLastSeen() == null ? null : user.getLastSeen().getTime());
            document.put("last_server", user.getLastServer());
            document.put("secret", user.getSecret());
            document.put("ip", user.getIp());
            document.put("last_authentication", user.getLastAuthentication() == null ? null : user.getLastAuthentication().getTime());
            document.put("email", user.getEmail());
            collection.insertOne(document);
        });
    }

    @Override
    public void insertUsers(Collection<User> users) {
        plugin.reportMainThread();
        connector.runQuery(collection -> {
            for (var user : users) {
                insertUser(user);
            }
        });
    }

    @Override
    public void updateUser(User user) {
        plugin.reportMainThread();
        connector.runQuery(collection -> {
            var document = new Document();
            document.put("uuid", user.getUuid().toString());
            document.put("premium_uuid", user.getPremiumUUID() == null ? null : user.getPremiumUUID().toString());
            document.put("hashed_password", user.getHashedPassword() == null ? null : user.getHashedPassword().hash());
            document.put("salt", user.getHashedPassword() == null ? null : user.getHashedPassword().salt());
            document.put("algo", user.getHashedPassword() == null ? null : user.getHashedPassword().algo());
            document.put("last_nickname", user.getLastNickname());
            document.put("joined", user.getJoinDate() == null ? null : user.getJoinDate().getTime());
            document.put("last_seen", user.getLastSeen() == null ? null : user.getLastSeen().getTime());
            document.put("last_server", user.getLastServer());
            document.put("secret", user.getSecret());
            document.put("ip", user.getIp());
            document.put("last_authentication", user.getLastAuthentication() == null ? null : user.getLastAuthentication().getTime());
            document.put("email", user.getEmail());
            collection.replaceOne(Filters.eq("uuid", user.getUuid().toString()), document);
        });
    }

    @Override
    public void deleteUser(User user) {
        plugin.reportMainThread();
        connector.runQuery(collection -> {
            collection.deleteOne(Filters.eq("uuid", user.getUuid().toString()));
        });
    }
}