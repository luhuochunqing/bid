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

    /** APIHub 登录 URL。 */
    private String tokenUrl = "http://apihub-test.ehsy.com/user/login";

    /** APIHub 统一查询 URL。 */
    private String tableUrl = "http://apihub-test.ehsy.com/search";

    /** 部署级中转开关；仅测试环境显式开启，数据库旧 enabled 不可绕过。 */
    private boolean gatewayEnabled = false;

    /** APIHub 账号，由数据库或环境配置提供，不复用百度 API Key。 */
    private String username = "";
    private String password = "";
    /** 文档允许 username 或 userName，按测试环境实际契约配置。 */
    private String loginUsernameField = "username";

    /** 连接超时时间（秒）。 */
    private int connectTimeoutSeconds = 10;

    /** 响应读取超时时间（秒）。 */
    private int readTimeoutSeconds = 30;
}
