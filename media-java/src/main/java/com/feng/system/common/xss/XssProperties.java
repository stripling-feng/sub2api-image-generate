package com.feng.system.common.xss;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "xss")
public class XssProperties {

    /**
     * URL white list that bypasses the XSS filter, supports Ant-style patterns.
     */
    private List<String> excludeUrls = new ArrayList<>();
}
