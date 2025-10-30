/**
 * Centralized logging utilities for Akka SDK components.
 * 
 * This package provides structured, consistent logging across all component types:
 * 
 * <ul>
 * <li>{@link community.application.action.TimedActionLogger} - Logs TimedAction executions</li>
 * <li>{@link community.application.action.KeyValueEntityLogger} - Logs KeyValueEntity state changes</li>
 * <li>{@link community.application.action.HttpEndpointLogger} - Logs HTTP endpoint access</li>
 * </ul>
 * 
 * All loggers follow the same structured format pattern with optional additional context.
 * This ensures consistent observability across the entire community application.
 */
package community.application.action;