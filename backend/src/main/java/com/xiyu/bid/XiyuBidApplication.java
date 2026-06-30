package com.xiyu.bid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class XiyuBidApplication {

    public static void main(String[] args) {
        // CO-438: 无显示器 Linux 服务器上 AWT 字体系统初始化需要 headless 模式，
        // 否则 POI autoSizeColumn 会报 "Fontconfig head is null"（systemd 默认不设此参数）
        System.setProperty("java.awt.headless", "true");
        SpringApplication.run(XiyuBidApplication.class, args);
    }
}
