package com.xiyu.bid.scoreparse.infrastructure.baidu;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 百度智能云表格识别（Table OCR）配置属性。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.score-parse.baidu-ocr")
public class BaiduOcrProperties {

    /** 是否启用百度 OCR 表格识别旁路通道。默认关闭。 */
    private boolean enabled = false;

    /** 百度智能云 API Key。 */
    private String apiKey = "";

    /** 百度智能云 Secret Key。 */
    private String secretKey = "";

    /** OAuth2 Token 获取 URL。 */
    private String tokenUrl = "https://aip.baidubce.com/oauth/2.0/token";

    /** 表格文字识别 API URL。 */
    private String tableUrl = "https://aip.baidubce.com/rest/2.0/ocr/v1/table";

    /** 连接超时时间（秒）。 */
    private int connectTimeoutSeconds = 10;

    /** 响应读取超时时间（秒）。 */
    private int readTimeoutSeconds = 30;
}
