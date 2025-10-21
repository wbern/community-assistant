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
import community.domain.MockEmailInboxService;
import community.domain.MockSheetSyncService;
import community.domain.SheetSyncService;
import realestate.application.EmailClient;

import java.time.Duration;

@Setup
public class Bootstrap implements ServiceSetup {

  private static final String BOOTSTRAP_TIMER_NAME = "bootstrap-sheet-sync-timer";

  // Singleton mock for testing - will be replaced with real service later
  private static final MockSheetSyncService MOCK_SHEET_SERVICE = new MockSheetSyncService();

  private final ComponentClient componentClient;
  private final TimerScheduler timerScheduler;

  public Bootstrap(Config config, ComponentClient componentClient, TimerScheduler timerScheduler) {
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
          // TODO: Replace with real GmailInboxService when Gmail integration is ready
          return (T) new MockEmailInboxService();
        }
        return null;
      }
    };
  }

}
