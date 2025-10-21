package com.example;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timer.TimerScheduler;
//import dev.langchain4j.model.ollama.OllamaChatModel;
import com.typesafe.config.Config;
import community.application.SheetSyncFlushAction;
import community.domain.EmailInboxService;
import community.domain.GmailInboxService;
import community.domain.MockEmailInboxService;
import community.domain.MockSheetSyncService;
import community.domain.SheetSyncService;
import realestate.application.EmailClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.cdimascio.dotenv.Dotenv;

import java.time.Duration;

@Setup
public class Bootstrap implements ServiceSetup {

  private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);
  private static final String BOOTSTRAP_TIMER_NAME = "bootstrap-sheet-sync-timer";

  // Singleton mock for testing - will be replaced with real service later
  private static final MockSheetSyncService MOCK_SHEET_SERVICE = new MockSheetSyncService();

  private final ComponentClient componentClient;
  private final TimerScheduler timerScheduler;

  private static void loadEnvVar(Dotenv dotenv, String key) {
    String value = dotenv.get(key);
    if (value != null && !value.isEmpty() && System.getenv(key) == null) {
      System.setProperty(key, value);
    }
  }

  public Bootstrap(Config config, ComponentClient componentClient, TimerScheduler timerScheduler) {
    // Load .env file if it exists (for local development)
    // Only load specific variables we need to avoid config conflicts
    try {
      Dotenv dotenv = Dotenv.configure()
          .ignoreIfMissing()
          .load();

      // Only load specific variables
      loadEnvVar(dotenv, "GMAIL_USER_EMAIL");
      loadEnvVar(dotenv, "GOOGLE_APPLICATION_CREDENTIALS");
      loadEnvVar(dotenv, "OPENAI_API_KEY");

      log.info("Loaded .env file");
    } catch (Exception e) {
      log.debug("No .env file found or error loading it (this is fine): {}", e.getMessage());
    }

    this.componentClient = componentClient;
    this.timerScheduler = timerScheduler;
    if (config.getString("akka.javasdk.agent.model-provider").equals("openai")
      && config.getString("akka.javasdk.agent.openai.api-key").isBlank()) {
      throw new IllegalStateException(
        "No API keys found. Make sure you have OPENAI_API_KEY defined as environment variable, or change the model provider configuration in application.conf to use a different LLM.");
    }
  }

  @Override
  public void onStartup() {
    // Bootstrap the periodic timer for SheetSyncFlushAction
    // Schedule the first flush immediately (Duration.ZERO) to start the periodic cycle
    timerScheduler.createSingleTimer(
      BOOTSTRAP_TIMER_NAME,
      Duration.ZERO,
      componentClient
        .forTimedAction()
        .method(SheetSyncFlushAction::scheduleNextFlush)
        .deferred()
    );
  }

  /**
   * Get the mock sheet service for testing purposes.
   * TODO: Remove this once we implement the real GoogleSheetSyncService.
   */
  public static MockSheetSyncService getMockSheetService() {
    return MOCK_SHEET_SERVICE;
  }

  @Override
  public DependencyProvider createDependencyProvider() {
    return new DependencyProvider() {

      @Override
      public <T> T getDependency(Class<T> aClass) {
        if (aClass.equals(EmailClient.class)) {
          return (T) new EmailClient();
        }
        if (aClass.equals(SheetSyncService.class)) {
          // TODO: Replace with real GoogleSheetSyncService in Cycle 9
          return (T) MOCK_SHEET_SERVICE;
        }
        if (aClass.equals(EmailInboxService.class)) {
          // Use real Gmail if credentials are available, otherwise mock
          // Check both environment and system properties (for .env support)
          String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
          if (credentialsPath == null) {
            credentialsPath = System.getProperty("GOOGLE_APPLICATION_CREDENTIALS");
          }

          String gmailUserEmail = System.getenv("GMAIL_USER_EMAIL");
          if (gmailUserEmail == null) {
            gmailUserEmail = System.getProperty("GMAIL_USER_EMAIL");
          }

          if (credentialsPath != null && !credentialsPath.isEmpty() &&
              gmailUserEmail != null && !gmailUserEmail.isEmpty()) {
            try {
              log.info("Initializing GmailInboxService for {}", gmailUserEmail);
              return (T) new GmailInboxService(gmailUserEmail);
            } catch (Exception e) {
              log.warn("Failed to initialize GmailInboxService, falling back to MockEmailInboxService", e);
              return (T) new MockEmailInboxService();
            }
          } else {
            log.info("Using MockEmailInboxService (GOOGLE_APPLICATION_CREDENTIALS or GMAIL_USER_EMAIL not set)");
            return (T) new MockEmailInboxService();
          }
        }
        return null;
      }
    };
  }

}
