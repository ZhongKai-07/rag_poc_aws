package com.huatai.rag.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "huatai.aws")
public class AwsProperties {

    private String region = "ap-northeast-1";
    private String bedrockRegion = "ap-northeast-1";

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getBedrockRegion() {
        return bedrockRegion;
    }

    public void setBedrockRegion(String bedrockRegion) {
        this.bedrockRegion = bedrockRegion;
    }
}
