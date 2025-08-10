package com.mq.study.mqtt.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MQTT配置类
 * 
 * @author mh
 * @date 2025-01-18
 */
@Configuration
@EnableConfigurationProperties(MqttProperties.class)
public class MqttConfig {
    
} 