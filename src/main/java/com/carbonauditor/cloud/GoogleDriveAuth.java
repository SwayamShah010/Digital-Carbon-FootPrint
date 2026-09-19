package com.carbonauditor.cloud;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.client.auth.oauth2.Credential;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

/**
 * Module 15: Cloud Storage Scanner — Google Drive authentication.
 *
 * Handles the one-time OAuth2 "installed app" consent flow: opens the
 * user's browser to sign in and grant read-only access, then caches the
 * resulting token locally so future scans don't require re-consenting.
 *
 * Setup required before this will work (see README.md "Google Drive Setup"):
 *   1. Create a Google Cloud project and enable the Google Drive API.
 *   2. Create an OAuth 2.0 Client ID of type "Desktop app".
 *   3. Download its JSON and save it as credentials.json in the project's
 *      working directory (next to carbon_auditor.db).
 *
 * This app only ever requests read-only Drive scopes — it cannot modify
 * or delete anything in the user's Drive.
 */
public class GoogleDriveAuth {

    private static final String APPLICATION_NAME = "Local & Cloud Carbon Auditor";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final String CREDENTIALS_FILE_PATH = "credentials.json";

    // Read-only: this app never writes to or deletes from Drive.
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE_METADATA_READONLY);

    /**
     * Builds an authenticated Drive client, prompting the user to sign in
     * via their browser if no cached token exists yet.
     *
     * @throws FileNotFoundException if credentials.json hasn't been set up yet
     */
    public Drive getDriveService() throws Exception {
        if (!Files.exists(Path.of(CREDENTIALS_FILE_PATH))) {
            throw new FileNotFoundException(
                    "credentials.json not found. Follow the 'Google Drive Setup' steps in README.md " +
                            "to create one, then place it at: " + Path.of(CREDENTIALS_FILE_PATH).toAbsolutePath());
        }

        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = authorize(httpTransport);

        return new Drive.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private Credential authorize(final NetHttpTransport httpTransport) throws Exception {
        try (InputStream in = Files.newInputStream(Path.of(CREDENTIALS_FILE_PATH))) {
            GoogleClientSecrets clientSecrets =
                    GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                    .setAccessType("offline")
                    .build();

            LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
            return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        }
    }

    /** True if a cached token already exists (i.e. the user has previously granted access). */
    public boolean hasCachedToken() {
        return Files.exists(Path.of(TOKENS_DIRECTORY_PATH));
    }

    /** True if credentials.json has been placed in the working directory. */
    public boolean hasCredentialsFile() {
        return Files.exists(Path.of(CREDENTIALS_FILE_PATH));
    }
}
