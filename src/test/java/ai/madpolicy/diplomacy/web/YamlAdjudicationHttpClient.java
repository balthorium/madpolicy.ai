package ai.madpolicy.diplomacy.web;

import ai.madpolicy.diplomacy.DiplomacyApplication;
import ai.madpolicy.diplomacy.model.GameStateDocument;
import ai.madpolicy.diplomacy.model.TestYaml;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

public final class YamlAdjudicationHttpClient {
    public static final long FIXED_EPOCH_SECONDS = 1_700_000_000L;

    private static final Object LOCK = new Object();
    private static ConfigurableApplicationContext sharedContext;
    private static URI sharedAdjudicateUri;
    private static URI sharedOfficiateUri;
    private static Path sharedGamesRoot;
    private static boolean shutdownHookRegistered;

    private final HttpClient httpClient;

    public YamlAdjudicationHttpClient() {
        ensureServerStarted();
        this.httpClient = HttpClient.newHttpClient();
    }

    public static boolean canBindLocalPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void ensureServerStarted() {
        synchronized (LOCK) {
            if (sharedContext != null) {
                return;
            }
            sharedGamesRoot = createGamesRoot();
            sharedContext = SpringApplication.run(
                DiplomacyApplication.class,
                "--server.port=0",
                "--diplomacy.clock.fixed-epoch-seconds=" + FIXED_EPOCH_SECONDS,
                "--diplomacy.games.root=" + sharedGamesRoot
            );
            int port = ((ServletWebServerApplicationContext) sharedContext).getWebServer().getPort();
            sharedAdjudicateUri = URI.create("http://127.0.0.1:" + port + "/adjudicate");
            sharedOfficiateUri = URI.create("http://127.0.0.1:" + port + "/officiate");
            if (!shutdownHookRegistered) {
                Runtime.getRuntime().addShutdownHook(new Thread(YamlAdjudicationHttpClient::shutdownServer));
                shutdownHookRegistered = true;
            }
        }
    }

    public static Path gamesRoot() {
        ensureServerStarted();
        return sharedGamesRoot;
    }

    private static Path createGamesRoot() {
        try {
            return Files.createTempDirectory("diplomacy-games-");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create temporary games root", e);
        }
    }

    private static void shutdownServer() {
        synchronized (LOCK) {
            if (sharedContext != null) {
                sharedContext.close();
                sharedContext = null;
                sharedAdjudicateUri = null;
                sharedOfficiateUri = null;
            }
        }
    }

    public HttpResponse<String> adjudicateYaml(String requestBody) {
        HttpRequest request = HttpRequest.newBuilder(sharedAdjudicateUri)
            .header("Content-Type", "application/yaml")
            .header("Accept", "application/yaml")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to call adjudication endpoint", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling adjudication endpoint", e);
        }
    }

    public GameStateDocument adjudicate(GameStateDocument requestBody) {
        HttpResponse<String> response = adjudicateYaml(TestYaml.write(requestBody));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Adjudication endpoint returned " + response.statusCode() + ": " + response.body());
        }
        return TestYaml.load(response.body(), GameStateDocument.class);
    }

    public HttpResponse<String> initialStateYaml() {
        HttpRequest request = HttpRequest.newBuilder(sharedOfficiateUri)
            .header("Accept", "application/yaml")
            .GET()
            .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to call officiate endpoint", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling officiate endpoint", e);
        }
    }

    public HttpResponse<String> officiateYaml(String requestBody) {
        HttpRequest request = HttpRequest.newBuilder(sharedOfficiateUri)
            .header("Content-Type", "application/yaml")
            .header("Accept", "application/yaml")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to call officiate endpoint", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling officiate endpoint", e);
        }
    }

    public GameStateDocument officiate(GameStateDocument requestBody) {
        HttpResponse<String> response = officiateYaml(TestYaml.write(requestBody));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Officiate endpoint returned " + response.statusCode() + ": " + response.body());
        }
        return TestYaml.load(response.body(), GameStateDocument.class);
    }
}
