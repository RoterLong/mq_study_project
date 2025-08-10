package com.mq.study.mqtt.manager;

import com.mq.study.mqtt.config.MqttTopicListener;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;


/**
 * MQTT注解处理器
 * 扫描并处理@MqttTopicListener注解
 */
@Slf4j
@Component
public class MqttAnnotationProcessor {
    
    @Resource
    private ApplicationContext applicationContext;
    
    @Resource
    private MqttClientManager mqttClientManager;
    
    /**
     * 初始化时扫描所有带有@MqttTopicListener注解的方法
     */
    @PostConstruct
    public void init() {
        log.info("开始扫描MQTT注解监听器...");

        // 获取所有Bean
        Map<String, Object> beans = applicationContext.getBeansOfType(Object.class);

        int listenerCount = 0;

        for (Object bean : beans.values()) {
            Class<?> clazz = bean.getClass();

            // 遍历Bean的所有方法
            for (Method method : clazz.getDeclaredMethods()) {
                // 检查是否有@MqttTopicListener注解
                if (method.isAnnotationPresent(MqttTopicListener.class)) {
                    processAnnotatedMethod(bean, method);
                    listenerCount++;
                }
            }
        }

        log.info("MQTT注解监听器扫描完成，共注册 {} 个监听器", listenerCount);
    }
    
    /**
     * 处理带有注解的方法
     */
    private void processAnnotatedMethod(Object bean, Method method) {
        MqttTopicListener annotation = method.getAnnotation(MqttTopicListener.class);

        String topic = annotation.topic();
        String clientId = annotation.clientId();
        int qos = annotation.qos();
        
        // 验证方法签名
        if (!isValidMethodSignature(method)) {
            log.error("MQTT监听器方法签名不正确: {}#{}, 期望参数: (String topic, String message)", 
                     bean.getClass().getSimpleName(), method.getName());
            return;
        }
        
        log.info("注册MQTT监听器: Topic={}, ClientId={}, QoS={}, Method={}#{}", 
                topic, clientId, qos, bean.getClass().getSimpleName(), method.getName());
        
        // 确保方法可访问
        method.setAccessible(true);
        
        // 注册到客户端管理器
        mqttClientManager.registerListener(topic, clientId, qos, bean, method);
    }
    
    /**
     * 验证方法签名是否正确
     * 期望签名: void methodName(String topic, String message)
     */
    private boolean isValidMethodSignature(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        
        // 检查参数个数和类型
        return parameterTypes.length == 3
               && parameterTypes[0] == String.class 
               && parameterTypes[1] == String.class
               && parameterTypes[2] == Integer.class;
    }
} 