package com.huatai.rag.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "huatai.aws")
public class AwsProperties {

    private String region = "ap-northeast-1";
    private String bedrockRegion = "ap-northeast-1";
    private String bdaRegion = "us-east-1";
    private String bdaProjectArn = "";
    private String bdaProfileArn = "";
    private String bdaStage = "LIVE";
    private int bdaMaxPollAttempts = 60;
    private Duration bdaPollInterval = Duration.ofSeconds(2);

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

    public String getBdaRegion() {
        return bdaRegion;
    }

    public void setBdaRegion(String bdaRegion) {
        this.bdaRegion = bdaRegion;
    }

    public String getBdaProjectArn() {
        return bdaProjectArn;
    }

    public void setBdaProjectArn(String bdaProjectArn) {
        this.bdaProjectArn = bdaProjectArn;
    }

    public String getBdaProfileArn() {
        return bdaProfileArn;
    }

    public void setBdaProfileArn(String bdaProfileArn) {
        this.bdaProfileArn = bdaProfileArn;
    }

    public String getBdaStage() {
        return bdaStage;
    }

    public void setBdaStage(String bdaStage) {
        this.bdaStage = bdaStage;
    }

    public int getBdaMaxPollAttempts() {
        return bdaMaxPollAttempts;
    }

    public void setBdaMaxPollAttempts(int bdaMaxPollAttempts) {
        this.bdaMaxPollAttempts = bdaMaxPollAttempts;
    }

    public Duration getBdaPollInterval() {
        return bdaPollInterval;
    }

    public void setBdaPollInterval(Duration bdaPollInterval) {
        this.bdaPollInterval = bdaPollInterval;
    }
}
