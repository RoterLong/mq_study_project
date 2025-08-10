package com.mq.study.mqtt.listener;

import com.mq.study.mqtt.config.MqttTopicListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


/**
 * MQTT监听器示例
 * 使用@MqttTopicListener注解来处理不同的MQTT主题
 */
@Slf4j
@Component
public class MqttListener {

    @MqttTopicListener(topic = "push-test/#", clientId = "my-test-all")  // 通配符"+"
    public void handlePushWx(String topic, String message,Integer messageId) {
        String subTopic = topic.substring("push-test/".length());
        log.info("收到推送消息 - topic: {}, Message: {},messageId:{}，subTopic={}", topic, message,messageId,subTopic);

    }

}