/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.database.connector;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import xyz.kyngs.librelogin.api.database.connector.MongoDatabaseConnector;
import xyz.kyngs.librelogin.api.util.ThrowableFunction;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.config.ConfigurateHelper;
import xyz.kyngs.librelogin.common.config.key.ConfigurationKey;

import java.util.logging.Level;
import java.util.logging.Logger;

public class AuthenticMongoDatabaseConnector extends AuthenticDatabaseConnector<MongoException, MongoCollection<Document>> implements MongoDatabaseConnector {

    private final String connectionString;
    private MongoClient client;

    public AuthenticMongoDatabaseConnector(AuthenticLibreLogin<?, ?> plugin, String prefix) {
        super(plugin, prefix);


        connectionString = "mongodb://"
                + get(Configuration.USER) + ":"
                + get(Configuration.PASSWORD) + "@"
                + get(Configuration.HOST) + ":"
                + get(Configuration.PORT);
    }

    @Override
    public void connect() throws MongoException {
        client = MongoClients.create(connectionString);
        Logger mongoLogger = Logger.getLogger( "org.mongodb.driver" );
        mongoLogger.setLevel(Level.WARNING); // e.g. or Log.WARNING, etc.
        connected = true;
    }

    @Override
    public void disconnect() throws MongoException {
        connected = false;
        client.close();
    }

    @Override
    public MongoCollection<Document> obtainInterface() throws MongoException, IllegalStateException {
        return client.getDatabase(get(Configuration.NAME)).getCollection("libreLogin");
    }

    @Override
    public <V> V runQuery(ThrowableFunction<MongoCollection<Document>, V, MongoException> function) {
        try {
            return function.apply(obtainInterface());
        } catch (MongoException e) {
            throw new RuntimeException(e);
        }
    }

    public static final class Configuration {

        public static final ConfigurationKey<String> HOST = new ConfigurationKey<>(
                "host",
                "localhost",
                "The host of the database.",
                ConfigurateHelper::getString
        );

        public static final ConfigurationKey<String> NAME = new ConfigurationKey<>(
                "database",
                "librelogin",
                "The name of the database.",
                ConfigurateHelper::getString
        );

        public static final ConfigurationKey<String> PASSWORD = new ConfigurationKey<>(
                "password",
                "",
                "The password of the database.",
                ConfigurateHelper::getString
        );

        public static final ConfigurationKey<Integer> PORT = new ConfigurationKey<>(
                "port",
                3306,
                "The port of the database.",
                ConfigurateHelper::getInt
        );

        public static final ConfigurationKey<String> USER = new ConfigurationKey<>(
                "user",
                "root",
                "The user of the database.",
                ConfigurateHelper::getString
        );
    }
}