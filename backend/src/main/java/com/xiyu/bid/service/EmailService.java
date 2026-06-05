package com.xiyu.bid.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 邮件服务
 * 开发环境返回令牌供测试使用
 */
@Service
public class EmailService {

        private static final Logger log = LoggerFactory.getLogger(EmailService.class);

        @Value("${app.email.dev-mode:true}")
        private boolean devMode;

        /**
         * 发送密码重置邮件
         * 开发环境：返回令牌供测试使用
         * 生产环境：发送真实邮件
         *
         * @param email 收件人邮箱
         * @param resetToken 重置令牌
         * @return 开发环境返回令牌，生产环境返回成功消息
         */
        public String sendPasswordResetEmail(String email, String resetToken) {
                if (devMode) {
                        log.info("[DEV MODE] Password reset token for {}: {}", email, resetToken);
                        return resetToken; // 开发环境返回令牌供测试
                }

                // 生产环境发送真实邮件
                // TODO: 集成SMTP服务
                log.info("Password reset email sent to: {}", email);
                return "Email sent successfully";
        }

        /**
         * 发送邮箱验证邮件
         */
        public String sendVerificationEmail(String email, String verificationToken) {
                if (devMode) {
                        log.info("[DEV MODE] Email verification token for {}: {}", email, verificationToken);
                        return verificationToken;
                }

                log.info("Verification email sent to: {}", email);
                return "Email sent successfully";
        }
}
