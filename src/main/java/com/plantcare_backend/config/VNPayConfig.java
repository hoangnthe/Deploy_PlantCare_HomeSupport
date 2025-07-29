package com.plantcare_backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "vnpay")
public class VNPayConfig {
    private String tmnCode;
    private String hashSecret;
    private String paymentUrl;
    private String returnUrl;
    private String ipnUrl;
    private String merchantAdminUrl;
}
