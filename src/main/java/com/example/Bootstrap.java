package com.example;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
//import dev.langchain4j.model.ollama.OllamaChatModel;
import com.typesafe.config.Config;
import community.domain.MockSheetSyncService;
import community.domain.SheetSyncService;
import realestate.application.EmailClient;

@Setup
public class Bootstrap implements ServiceSetup {

  // Singleton mock for testing - will be replaced with real service later
  private static final MockSheetSyncService MOCK_SHEET_SERVICE = new MockSheetSyncService();

  public Bootstrap(Config config) {
    if (config.getString("akka.javasdk.agent.model-provider").equals("openai")
      && config.getString("akka.javasdk.agent.openai.api-key").isBlank()) {
      throw new IllegalStateException(
        "No API keys found. Make sure you have OPENAI_API_KEY defined as environment variable, or change the model provider configuration in application.conf to use a different LLM.");
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
        if (aClass.equals(EmailClient.class)) {
          return (T) new EmailClient();
        }
        if (aClass.equals(SheetSyncService.class)) {
          // TODO: Replace with real GoogleSheetSyncService in Cycle 9
          return (T) MOCK_SHEET_SERVICE;
        }
        return null;
      }
    };
  }

}
