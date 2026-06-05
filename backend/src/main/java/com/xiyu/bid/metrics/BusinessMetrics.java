package com.xiyu.bid.metrics;

import org.springframework.stereotype.Component;

/**
 * Central registry for business-level metrics (Facade).
 * <p>
 * This class delegates to domain-specific metrics holders for better modularity.
 * Metrics are exposed via {@code /actuator/prometheus} and consumed by Grafana dashboards
 * and Prometheus alerting rules.
 * <p>
 * Metric naming follows Micrometer conventions: {@code <domain>_<name>_<unit>}.
 * Labels follow Prometheus conventions for dimensionality.
 */
@Component
public class BusinessMetrics {

    private final AuthMetrics authMetrics;
    private final ProjectMetrics projectMetrics;
    private final BidMetrics bidMetrics;
    private final TenderMetrics tenderMetrics;
    private final ExportMetrics exportMetrics;
    private final ApiMetrics apiMetrics;
    private final OrgSyncMetrics orgSyncMetrics;
    private final CacheMetrics cacheMetrics;

    public BusinessMetrics(
            AuthMetrics authMetrics,
            ProjectMetrics projectMetrics,
            BidMetrics bidMetrics,
            TenderMetrics tenderMetrics,
            ExportMetrics exportMetrics,
            ApiMetrics apiMetrics,
            OrgSyncMetrics orgSyncMetrics,
            CacheMetrics cacheMetrics) {
        this.authMetrics = authMetrics;
        this.projectMetrics = projectMetrics;
        this.bidMetrics = bidMetrics;
        this.tenderMetrics = tenderMetrics;
        this.exportMetrics = exportMetrics;
        this.apiMetrics = apiMetrics;
        this.orgSyncMetrics = orgSyncMetrics;
        this.cacheMetrics = cacheMetrics;
    }

    // ==================== Auth Metrics ====================

    public void recordLoginSuccess() {
        authMetrics.recordLoginSuccess();
    }

    public void recordLoginSuccess(String userType) {
        authMetrics.recordLoginSuccess(userType);
    }

    public void recordLoginFailure() {
        authMetrics.recordLoginFailure();
    }

    public void recordLogout() {
        authMetrics.recordLogout();
    }

    public void recordTokenRefreshSuccess() {
        authMetrics.recordTokenRefreshSuccess();
    }

    public void recordTokenRefreshFailure() {
        authMetrics.recordTokenRefreshFailure();
    }

    public void recordLoginLatency(Runnable operation) {
        authMetrics.recordLoginLatency(operation);
    }

    public <T> T recordLoginLatency(java.util.function.Supplier<T> operation) {
        return authMetrics.recordLoginLatency(operation);
    }

    public long recordLoginLatencyNanos(long startNanos) {
        return authMetrics.recordLoginLatencyNanos(startNanos);
    }

    // ==================== Project Metrics ====================

    public void recordProjectCreated() {
        projectMetrics.recordProjectCreated();
    }

    public void recordProjectCreationFailed() {
        projectMetrics.recordProjectCreationFailed();
    }

    public void recordProjectUpdated() {
        projectMetrics.recordProjectUpdated();
    }

    public void recordProjectDeleted() {
        projectMetrics.recordProjectDeleted();
    }

    public void recordProjectExported() {
        projectMetrics.recordProjectExported();
    }

    public void recordProjectCreateLatency(Runnable operation) {
        projectMetrics.recordProjectCreateLatency(operation);
    }

    public <T> T recordProjectCreateLatency(java.util.function.Supplier<T> operation) {
        return projectMetrics.recordProjectCreateLatency(operation);
    }

    // ==================== Bid Metrics ====================

    public void recordBidSubmitted() {
        bidMetrics.recordBidSubmitted();
    }

    public void recordBidSubmissionFailed() {
        bidMetrics.recordBidSubmissionFailed();
    }

    public void recordBidUpdated() {
        bidMetrics.recordBidUpdated();
    }

    public void recordBidWithdrawn() {
        bidMetrics.recordBidWithdrawn();
    }

    public void recordBidSubmitLatency(Runnable operation) {
        bidMetrics.recordBidSubmitLatency(operation);
    }

    public <T> T recordBidSubmitLatency(java.util.function.Supplier<T> operation) {
        return bidMetrics.recordBidSubmitLatency(operation);
    }

    // ==================== Tender Processing Metrics ====================

    public void recordTenderProcessed() {
        tenderMetrics.recordTenderProcessed();
    }

    public void recordTenderProcessingFailed() {
        tenderMetrics.recordTenderProcessingFailed();
    }

    public void recordTenderProcessingTime(long durationMillis) {
        tenderMetrics.recordTenderProcessingTime(durationMillis);
    }

    public <T> T recordTenderProcessing(java.util.function.Supplier<T> operation) {
        return tenderMetrics.recordTenderProcessing(operation);
    }

    // ==================== Export Metrics ====================

    public void recordExportSuccess() {
        exportMetrics.recordExportSuccess();
    }

    public void recordExportFailure() {
        exportMetrics.recordExportFailure();
    }

    public void recordExportSizeExceeded() {
        exportMetrics.recordExportSizeExceeded();
    }

    // ==================== API Metrics ====================

    public void recordRateLimited() {
        apiMetrics.recordRateLimited();
    }

    public void recordNotFound() {
        apiMetrics.recordNotFound();
    }

    public void recordUnauthorized() {
        apiMetrics.recordUnauthorized();
    }

    // ==================== Organization Sync Metrics ====================

    public void recordOrgSyncSuccess() {
        orgSyncMetrics.recordOrgSyncSuccess();
    }

    public void recordOrgSyncFailure() {
        orgSyncMetrics.recordOrgSyncFailure();
    }

    public void recordOrgSyncTime(long durationMillis) {
        orgSyncMetrics.recordOrgSyncTime(durationMillis);
    }

    public <T> T recordOrgSync(java.util.function.Supplier<T> operation) {
        return orgSyncMetrics.recordOrgSync(operation);
    }

    // ==================== Cache Metrics ====================

    public void recordCacheHit() {
        cacheMetrics.recordCacheHit();
    }

    public void recordCacheMiss() {
        cacheMetrics.recordCacheMiss();
    }
}
