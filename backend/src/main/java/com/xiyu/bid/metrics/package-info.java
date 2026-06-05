/**
 * Monitoring metrics package.
 * <p>
 * Provides custom business metrics via Micrometer for Prometheus scraping.
 * <p>
 * Key components:
 * <ul>
 *   <li>{@link com.xiyu.bid.metrics.BusinessMetrics} - Central registry for all business metrics</li>
 * </ul>
 * <p>
 * Metrics are automatically exposed at {@code /actuator/prometheus} when
 * {@code micrometer-registry-prometheus} is on the classpath.
 */
package com.xiyu.bid.metrics;
