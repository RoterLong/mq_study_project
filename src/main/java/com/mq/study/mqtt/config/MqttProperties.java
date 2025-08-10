package com.mq.study.mqtt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MQTT配置属性
 * 
 * @author mh
 * @date 2025-01-18
 */
@Data
@Component
@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {
    
    /**
     * MQTT服务器地址
     */
    private String brokerUrl;
    
    /**
     * MQTT客户端ID前缀
     */
    private String clientIdPrefix;
    
    /**
     * MQTT账号
     */
    private String username;
    
    /**
     * MQTT密码
     */
    private String password;
    
    /**
     * 连接超时时间（秒）
     */
    private Integer connectionTimeout = 30;
    
    /**
     * 心跳间隔（秒）
     */
    private Integer keepAliveInterval = 60;
    
    /**
     * 是否清除会话
     */
    private Boolean cleanSession = true;
    
    /**
     * 是否自动重连
     */
    private Boolean automaticReconnect = true;
} 