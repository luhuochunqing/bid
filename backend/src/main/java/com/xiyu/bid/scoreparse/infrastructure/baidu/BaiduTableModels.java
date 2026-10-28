package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 百度表格识别接口（/rest/2.0/ocr/v1/table）响应模型。
 */
public final class BaiduTableModels {

    private BaiduTableModels() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BaiduTableResponse(
            @JsonProperty("table_num") Integer tableNum,
            @JsonProperty("tables_result") List<BaiduTableItem> tablesResult,
            @JsonProperty("error_code") Integer errorCode,
            @JsonProperty("error_msg") String errorMsg,
            @JsonProperty("log_id") Long logId
    ) {
        public BaiduTableResponse {
            tablesResult = tablesResult == null ? List.of() : List.copyOf(tablesResult);
        }

        public boolean isSuccess() {
            return errorCode == null || errorCode == 0;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BaiduTableItem(
            @JsonProperty("header") List<BaiduTableWord> header,
            @JsonProperty("footer") List<BaiduTableWord> footer,
            @JsonProperty("body") List<BaiduTableCell> body,
            @JsonProperty("table_location") List<BaiduPoint> tableLocation
    ) {
        public BaiduTableItem {
            header = header == null ? List.of() : List.copyOf(header);
            footer = footer == null ? List.of() : List.copyOf(footer);
            body = body == null ? List.of() : List.copyOf(body);
            tableLocation = tableLocation == null ? List.of() : List.copyOf(tableLocation);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BaiduTableWord(
            @JsonProperty("words") String words,
            @JsonProperty("location") List<BaiduPoint> location
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BaiduTableCell(
            @JsonProperty("row_start") int rowStart,
            @JsonProperty("row_end") int rowEnd,
            @JsonProperty("col_start") int colStart,
            @JsonProperty("col_end") int colEnd,
            @JsonProperty("words") String words,
            @JsonProperty("cell_location") List<BaiduPoint> cellLocation
    ) {
        public int rowspan() {
            return Math.max(1, rowEnd - rowStart + 1);
        }

        public int colspan() {
            return Math.max(1, colEnd - colStart + 1);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BaiduPoint(
            @JsonProperty("x") Integer x,
            @JsonProperty("y") Integer y
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BaiduTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("error") String error,
            @JsonProperty("error_description") String errorDescription
    ) {}
}
