package com.pura365.camera.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE)
public class GetInfoResponse {
    @JsonProperty("DeviceID")
    private String DeviceID;

    @JsonProperty("DeviceEnable")
    private Boolean DeviceEnable;

    @JsonProperty("CloudStorage")
    private Integer CloudStorage;

    @JsonProperty("NormalAI")
    private Boolean NormalAI;

    @JsonProperty("S3Hostname")
    private String S3Hostname;

    @JsonProperty("S3Region")
    private String S3Region;

    @JsonProperty("S3Bucket")
    private String S3Bucket;

    @JsonProperty("S3SecretKey")
    private String S3SecretKey;

    @JsonProperty("S3AccessKey")
    private String S3AccessKey;

    @JsonProperty("MqttHostname")
    private String MqttHostname;

    @JsonProperty("MqttPass")
    private String MqttPass;

    @JsonProperty("MqttUser")
    private String MqttUser;

    @JsonProperty("GPTHostname")
    private String GPTHostname;

    @JsonProperty("GPTKey")
    private String GPTKey;
    
    // getters and setters
    public String getDeviceID() { return DeviceID; }
    public void setDeviceID(String deviceID) { DeviceID = deviceID; }
    
    public Boolean getDeviceEnable() { return DeviceEnable; }
    public void setDeviceEnable(Boolean deviceEnable) { DeviceEnable = deviceEnable; }
    
    public Integer getCloudStorage() { return CloudStorage; }
    public void setCloudStorage(Integer cloudStorage) { CloudStorage = cloudStorage; }
    
    public Boolean getNormalAI() { return NormalAI; }
    public void setNormalAI(Boolean normalAI) { NormalAI = normalAI; }
    
    public String getS3Hostname() { return S3Hostname; }
    public void setS3Hostname(String s3Hostname) { S3Hostname = s3Hostname; }
    
    public String getS3Region() { return S3Region; }
    public void setS3Region(String s3Region) { S3Region = s3Region; }

    public String getS3Bucket() { return S3Bucket; }
    public void setS3Bucket(String s3Bucket) { S3Bucket = s3Bucket; }
    
    public String getS3SecretKey() { return S3SecretKey; }
    public void setS3SecretKey(String s3SecretKey) { S3SecretKey = s3SecretKey; }
    
    public String getS3AccessKey() { return S3AccessKey; }
    public void setS3AccessKey(String s3AccessKey) { S3AccessKey = s3AccessKey; }
    
    public String getMqttHostname() { return MqttHostname; }
    public void setMqttHostname(String mqttHostname) { MqttHostname = mqttHostname; }
    
    public String getMqttPass() { return MqttPass; }
    public void setMqttPass(String mqttPass) { MqttPass = mqttPass; }
    
    public String getMqttUser() { return MqttUser; }
    public void setMqttUser(String mqttUser) { MqttUser = mqttUser; }
    
    public String getGPTHostname() { return GPTHostname; }
    public void setGPTHostname(String gPTHostname) { GPTHostname = gPTHostname; }
    
    public String getGPTKey() { return GPTKey; }
    public void setGPTKey(String gPTKey) { GPTKey = gPTKey; }

    @Override
    public String toString() {
        return "GetInfoResponse{" +
                "DeviceID='" + DeviceID + '\'' +
                ", DeviceEnable=" + DeviceEnable +
                ", CloudStorage=" + CloudStorage +
                ", NormalAI=" + NormalAI +
                ", S3Hostname='" + S3Hostname + '\'' +
                ", S3Region='" + S3Region + '\'' +
                ", S3Bucket='" + S3Bucket + '\'' +
                ", S3SecretKey='" + maskSensitive(S3SecretKey) + '\'' +
                ", S3AccessKey='" + maskSensitive(S3AccessKey) + '\'' +
                ", MqttHostname='" + MqttHostname + '\'' +
                ", MqttPass='" + maskSensitive(MqttPass) + '\'' +
                ", MqttUser='" + MqttUser + '\'' +
                ", GPTHostname='" + GPTHostname + '\'' +
                ", GPTKey='" + maskSensitive(GPTKey) + '\'' +
                '}';
    }

    private String maskSensitive(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.length() <= 6) {
            return "***";
        }
        return value.substring(0, 3) + "***" + value.substring(value.length() - 3);
    }
}
