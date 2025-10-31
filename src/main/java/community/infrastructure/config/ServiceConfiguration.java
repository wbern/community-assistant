package community.infrastructure.config;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timer.TimerScheduler;
//import dev.langchain4j.model.ollama.OllamaChatModel;
import com.typesafe.config.Config;
import community.application.action.SheetSyncFlushAction;
import community.application.action.EmailPollingAction;
import community.domain.port.EmailInboxService;
import community.infrastructure.gmail.GmailInboxService;
import community.infrastructure.mock.MockEmailInboxService;
import community.infrastructure.mock.MockSheetSyncService;
import community.domain.port.SheetSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.cdimascio.dotenv.Dotenv;

import java.time.Duration;

@Setup
public class ServiceConfiguration implements ServiceSetup {

  private static final Logger log = LoggerFactory.getLogger(ServiceConfiguration.class);
  private static final String BOOTSTRAP_TIMER_NAME = "bootstrap-sheet-sync-timer";
  private static final String EMAIL_POLLING_BOOTSTRAP_TIMER_NAME = "bootstrap-email-polling-timer";

  // Singleton mock for testing - will be replaced with real service later
  private static final MockSheetSyncService MOCK_SHEET_SERVICE = new MockSheetSyncService();

  private final ComponentClient componentClient;
  private final TimerScheduler timerScheduler;
  private final Config config;

  // Instance-based cache for expensive services (not static, allows test isolation)
  private SheetSyncService sheetSyncServiceInstance;
  private EmailInboxService emailInboxServiceInstance;

  private static void loadEnvVar(Dotenv dotenv, String key) {
    String value = dotenv.get(key);
    if (value != null && !value.isEmpty() && System.getenv(key) == null) {
      System.setProperty(key, value);
    }
  }

  public ServiceConfiguration(Config config, ComponentClient componentClient, TimerScheduler timerScheduler) {
    // Load .env file if it exists (for local development)
    // Only load specific variables we need to avoid config conflicts
    try {
      Dotenv dotenv = Dotenv.configure()
          .ignoreIfMissing()
          .load();

      // Only load specific variables
      loadEnvVar(dotenv, "GMAIL_USER_EMAIL");
      loadEnvVar(dotenv, "GOOGLE_APPLICATION_CREDENTIALS");
      loadEnvVar(dotenv, "SPREADSHEET_ID");
      loadEnvVar(dotenv, "OPENAI_API_KEY");

      log.info("Loaded .env file");
    } catch (Exception e) {
      log.debug("No .env file found or error loading it (this is fine): {}", e.getMessage());
    }

    this.componentClient = componentClient;
    this.timerScheduler = timerScheduler;
    this.config = config;
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
    
    // Bootstrap the periodic timer for EmailPollingAction only if enabled
    boolean emailPollingEnabled = config.getBoolean("community.email-polling.enabled");
    if (emailPollingEnabled) {
      log.info("Email polling is enabled, starting automatic polling");
      timerScheduler.createSingleTimer(
        EMAIL_POLLING_BOOTSTRAP_TIMER_NAME,
        Duration.ZERO,
        componentClient
          .forTimedAction()
          .method(EmailPollingAction::scheduleNextPoll)
          .deferred()
      );
    } else {
      log.info("Email polling is disabled in this environment");
    }
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
        if (aClass.equals(SheetSyncService.class)) {
          // Return cached instance if available (prevents expensive re-initialization)
          if (sheetSyncServiceInstance != null) {
            return (T) sheetSyncServiceInstance;
          }

          // Use real GoogleSheetSyncService if spreadsheet ID is available, otherwise mock
          String spreadsheetId = System.getenv("SPREADSHEET_ID");
          if (spreadsheetId == null) {
            spreadsheetId = System.getProperty("SPREADSHEET_ID");
          }

          if (spreadsheetId != null && !spreadsheetId.isEmpty()) {
            try {
              log.info("Initializing GoogleSheetSyncService with spreadsheet ID: {}", spreadsheetId);
              sheetSyncServiceInstance = new community.infrastructure.sheets.GoogleSheetSyncService(spreadsheetId);
              return (T) sheetSyncServiceInstance;
            } catch (Exception e) {
              throw new RuntimeException("Failed to initialize GoogleSheetSyncService. Check GOOGLE_APPLICATION_CREDENTIALS and Sheets API access: " + e.getMessage(), e);
            }
          } else {
            log.info("Using MockSheetSyncService (SPREADSHEET_ID not set)");
            sheetSyncServiceInstance = MOCK_SHEET_SERVICE;
            return (T) MOCK_SHEET_SERVICE;
          }
        }
        if (aClass.equals(EmailInboxService.class)) {
          // Return cached instance if available (prevents expensive re-initialization)
          if (emailInboxServiceInstance != null) {
            return (T) emailInboxServiceInstance;
          }

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
              emailInboxServiceInstance = new GmailInboxService(gmailUserEmail);
              return (T) emailInboxServiceInstance;
            } catch (Exception e) {
              throw new RuntimeException("Failed to initialize GmailInboxService. Check GOOGLE_APPLICATION_CREDENTIALS and Gmail API access: " + e.getMessage(), e);
            }
          } else {
            log.info("Using MockEmailInboxService (GOOGLE_APPLICATION_CREDENTIALS or GMAIL_USER_EMAIL not set)");
            emailInboxServiceInstance = new MockEmailInboxService();
            return (T) emailInboxServiceInstance;
          }
        }
        return null;
      }
    };
  }

}
