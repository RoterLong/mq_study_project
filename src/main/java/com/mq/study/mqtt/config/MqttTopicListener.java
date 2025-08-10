package com.mq.study.mqtt.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * MQTT主题监听注解
 * 用于标记处理特定MQTT主题的方法
 * 
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MqttTopicListener {
    
    /**
     * 监听的MQTT主题
     */
    String topic();
    
    /**
     * MQTT客户端ID（固定值）
     */
    String clientId();
    
    /**
     * QoS等级，默认为1
     */
    int qos() default 1;
} 