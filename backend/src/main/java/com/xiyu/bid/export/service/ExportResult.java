package com.xiyu.bid.export.service;

/**
 * Result of a paginated export operation.
 */
public record ExportResult(byte[] data, int recordCount) {}
