package com.mq.study.mqtt.manager;

import com.mq.study.mqtt.config.MqttProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * MQTT客户端管理器
 * 管理多个MQTT客户端连接
 *
 * @author mh
 * @date 2025-01-18
 */
@Slf4j
@Component
public class MqttClientManager {

    @Resource
    private MqttProperties mqttProperties;


    // 存储客户端实例 <clientId, MqttAsyncClient>
    private final Map<String, MqttAsyncClient> clients = new ConcurrentHashMap<>();

    public MqttAsyncClient getClient(String clientId) {
        return clients.get(clientId);
    }

    // 存储topic与处理方法的映射 <topic, MethodInfo>
    private final Map<String, MethodInfo> topicHandlers = new ConcurrentHashMap<>();
    
    // 重连调度器
    private final ScheduledExecutorService reconnectScheduler = Executors.newScheduledThreadPool(2);

    /**
     * 注册MQTT监听器
     *
     * @param topic    主题
     * @param clientId 客户端ID
     * @param qos      QoS等级
     * @param bean     处理器Bean实例
     * @param method   处理方法
     */
    public void registerListener(String topic, String clientId, int qos, Object bean, Method method) {
        try {
            // 存储处理方法信息
            topicHandlers.put(topic, new MethodInfo(bean, method));

            // 如果客户端已存在，直接订阅新topic
            if (clients.containsKey(clientId)) {
                MqttAsyncClient client = clients.get(clientId);
                if (client.isConnected()) {
                    subscribeToTopic(client, topic, qos);
                }
                return;
            }

            // 创建新的MQTT客户端
            MqttAsyncClient client = new MqttAsyncClient(mqttProperties.getBrokerUrl(), clientId);

            // 设置回调
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.error("MQTT客户端[{}]连接丢失: {}", clientId, cause);
                    // 尝试重新连接
                    scheduleReconnect(clientId, topic, qos, bean, method);
                }

                @Override
                public void messageArrived(String receivedTopic, MqttMessage message) throws Exception {
                    handleMessage(receivedTopic, new String(message.getPayload()), message.getId());
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // 消息发送完成回调
                    log.info("消息投体成功 token={}", token);
                }
            });

            // 创建连接选项
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(mqttProperties.getUsername());
            options.setPassword(mqttProperties.getPassword().toCharArray());
            options.setConnectionTimeout(mqttProperties.getConnectionTimeout());
            options.setKeepAliveInterval(mqttProperties.getKeepAliveInterval());
            options.setCleanSession(mqttProperties.getCleanSession());
            options.setAutomaticReconnect(mqttProperties.getAutomaticReconnect());
            // 添加连接重试配置
            options.setMaxReconnectDelay(3000);
            options.setServerURIs(new String[]{mqttProperties.getBrokerUrl()});

            // 连接MQTT服务器
            client.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    log.info("连接成功");
                    subscribeToTopic(client, topic, qos);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    log.error("连接失败", exception);
                    scheduleReconnect(clientId, topic, qos, bean, method);
                }
            });

//            log.info("MQTT客户端[{}]连接成功", clientId);

            // 订阅主题
            // subscribeToTopic(client, topic, qos);

            // 存储客户端
            clients.put(clientId, client);

        } catch (Exception e) {
            log.error("注册MQTT监听器失败 - Topic: {}, ClientId: {}", topic, clientId, e);
        }
    }

    /**
     * 订阅主题
     */
    private void subscribeToTopic(MqttAsyncClient client, String topic, int qos) {
        try {
            IMqttToken subscribeToken = client.subscribe(topic, qos);
            subscribeToken.waitForCompletion();
            log.info("成功订阅主题: {} (QoS: {})", topic, qos);
        } catch (Exception e) {
            log.error("订阅主题失败: {}", topic, e);
        }
    }

    /**
     * 处理接收到的消息
     */
    private void handleMessage(String topic, String message, int messageId) {
       log.info("收到MQTT消息 - Topic: {}, Message: {}", topic, message);
        MethodInfo methodInfo = null;
        for (Map.Entry<String, MethodInfo> entry : topicHandlers.entrySet()) {
            if (matchTopic(entry.getKey(), topic)) {
                methodInfo = entry.getValue();
                break;
            }
        }
        if (methodInfo == null) {
            log.warn("未找到处理器 - Topic: {}", topic);
            return;
        }
        try {
            // 调用处理方法
            methodInfo.getMethod().invoke(methodInfo.getBean(), topic, message, messageId);
        } catch (Exception e) {
            log.error("调用消息处理方法失败 - Topic: {}", topic, e);
        }
    }

    /**
     * 判断主题是否匹配过滤器
     * 支持 MQTT 通配符：
     *   + 单层通配符
     *   # 多层通配符（只能出现在末尾）
     *
     * @param filter 订阅过滤器，如 "push-wx/+"、"push-wx/#"
     * @param topic  实际消息主题，如 "push-wx/device1"
     * @return 是否匹配
     */
    private boolean matchTopic(String filter, String topic) {
        String[] filterLevels = filter.split("/");
        String[] topicLevels = topic.split("/");

        int i = 0;
        for (; i < filterLevels.length; i++) {
            String f = filterLevels[i];

            // 多层通配符只能放末尾
            if (f.equals("#")) {
                return true;
            }

            if (i >= topicLevels.length) {
                // topic 层级不足，不能匹配
                return false;
            }

            if (!f.equals("+") && !f.equals(topicLevels[i])) {
                // 不是单层通配符且不相等，匹配失败
                return false;
            }
        }

        // 全部过滤器层级匹配，且 topic 不多余层级，匹配成功
        return i == topicLevels.length;
    }

    /**
     * 安排重新连接
     */
    private void scheduleReconnect(String clientId, String topic, int qos, Object bean, Method method) {
        log.info("安排5秒后重新连接MQTT客户端[{}]", clientId);
        reconnectScheduler.schedule(() -> {
            try {
                // 移除旧的客户端
                MqttAsyncClient oldClient = clients.remove(clientId);
                if (oldClient != null && oldClient.isConnected()) {
                    try {
                        oldClient.disconnect();
                        oldClient.close();
                    } catch (Exception e) {
                        log.warn("关闭旧MQTT连接时出错: {}", e.getMessage());
                    }
                }
                
                // 重新注册监听器
                log.info("尝试重新连接MQTT客户端[{}]", clientId);
                registerListener(topic, clientId, qos, bean, method);
                
            } catch (Exception e) {
                log.error("重新连接MQTT客户端[{}]失败: {}", clientId, e.getMessage());
                // 如果重连失败，再次安排重连（最多重试3次）
                scheduleReconnectWithLimit(clientId, topic, qos, bean, method, 1);
            }
        }, 5, TimeUnit.SECONDS);
    }
    
    /**
     * 带次数限制的重连安排
     */
    private void scheduleReconnectWithLimit(String clientId, String topic, int qos, Object bean, Method method, int retryCount) {
        if (retryCount >= 3) {
            log.error("MQTT客户端[{}]重连已达到最大重试次数，停止重连", clientId);
            return;
        }
        
        long delay = Math.min(30, 5 * (1L << retryCount)); // 指数退避，最大30秒
        log.info("安排{}秒后重新连接MQTT客户端[{}]，第{}次重试", delay, clientId, retryCount + 1);
        
        reconnectScheduler.schedule(() -> {
            try {
                registerListener(topic, clientId, qos, bean, method);
                log.info("MQTT客户端[{}]重连成功", clientId);
            } catch (Exception e) {
                log.error("MQTT客户端[{}]第{}次重连失败: {}", clientId, retryCount + 1, e.getMessage());
                scheduleReconnectWithLimit(clientId, topic, qos, bean, method, retryCount + 1);
            }
        }, delay, TimeUnit.SECONDS);
    }

    /**
     * 方法信息存储类
     */
    private static class MethodInfo {
        private final Object bean;
        private final Method method;

        public MethodInfo(Object bean, Method method) {
            this.bean = bean;
            this.method = method;
        }

        public Object getBean() {
            return bean;
        }

        public Method getMethod() {
            return method;
        }
    }

    /**
     * 销毁所有客户端连接
     */
    @PreDestroy
    public void destroy() {
        // 关闭重连调度器
        reconnectScheduler.shutdown();
        try {
            if (!reconnectScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                reconnectScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            reconnectScheduler.shutdownNow();
        }
        
        for (Map.Entry<String, MqttAsyncClient> entry : clients.entrySet()) {
            try {
                MqttAsyncClient client = entry.getValue();
                if (client != null && client.isConnected()) {
                    client.disconnect();
                    client.close();
                    log.info("MQTT客户端[{}]已断开连接", entry.getKey());
                }
            } catch (Exception e) {
                log.error("断开MQTT客户端[{}]连接失败", entry.getKey(), e);
            }
        }
        clients.clear();
        topicHandlers.clear();
    }
} 