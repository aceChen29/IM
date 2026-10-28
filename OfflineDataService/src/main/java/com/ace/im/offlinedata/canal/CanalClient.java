package com.ace.im.offlinedata.canal;

import cn.hutool.json.JSONUtil;
import com.ace.im.common.constant.CommonConstant;
import com.ace.im.common.model.dto.MessageBody;
import com.ace.im.common.model.vo.MessageResponse;
import com.ace.im.common.utils.FormatDateUtil;
import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
/*
设置重连 心跳等相关参数，实现心跳机制。根据binlog处理数据变更，在这里只监听了message表，如果是其他表变更continue
如果是插入操作 则需要进行处理，得到插入事件涉及的行，对于每行的数据转成一个map key 列名，value是对应的值，然后可以根据map得到消息的响应体
* */
@Component
@Slf4j
@SuppressWarnings("BusyWait")
public class CanalClient implements CommandLineRunner {

    @Resource
    private CanalConnector canalConnector;


    @Resource
    private StringRedisTemplate stringRedisTemplate;
    //指数退避重连就是：连接失败后先等一会儿再试；如果连续失败，每次等待时间翻倍，直到上限
    //第 1 次失败 → 等 2 秒
    //第 2 次失败 → 等 4 秒
    //第 3 次失败 → 等 8 秒
    //第 4 次失败 → 等 16 秒
    //第 5 次失败 → 等 32 秒
    //再失败     → 等 60 秒
    private static final int MAX_RETRY_TIMES = 5; // 发生错误后最大重试次数

    private static final long INITIAL_RETRY_DELAY = 1000; // 初始重试延迟1秒

    private static final long MAX_RETRY_DELAY = 60000; // 最大重试延迟60秒

    private static final long HEARTBEAT_INTERVAL = 30000; // 30秒发送一次心跳

    private static final long IDLE_CHECK_INTERVAL = 5000; // 5秒检查一次空闲状态
    // 需要监听的表名集合
    private static final Set<String> MONITOR_TABLES = Set.of("infinitechat.message");

    @Override
    public void run(String... args) {
        new Thread(this::process).start();
    }


    private void process() {
        log.info("====== Canal 消费线程已启动 ======");

        int batchSize = 1000;
        int retryTimes = 0;
        long retryDelay = INITIAL_RETRY_DELAY;
        long lastActiveTime = System.currentTimeMillis();

        while (true) {
            try {
                // 检查连接状态
                if (!canalConnector.checkValid()) {
                    reconnectCanal();
                    retryTimes = 0;
                    retryDelay = INITIAL_RETRY_DELAY;
                    lastActiveTime = System.currentTimeMillis();
                }

                // 检查是否需要发送心跳
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastActiveTime > HEARTBEAT_INTERVAL) {
                    sendHeartbeat();
                    lastActiveTime = currentTime;
                    continue;
                }

                Message message = canalConnector.getWithoutAck(batchSize);
                long batchId = message.getId();
                int size = message.getEntries().size();

                if (batchId == -1 || size == 0) {
                    // 没有数据时短暂休眠，避免CPU空转
                    Thread.sleep(IDLE_CHECK_INTERVAL);
                    continue;
                }

                lastActiveTime = System.currentTimeMillis(); // 更新最后活跃时间

                try {
                    handleMessage(message.getEntries());
                    canalConnector.ack(batchId);
                    retryTimes = 0;
                    retryDelay = INITIAL_RETRY_DELAY;
                } catch (Exception e) {
                    log.error("处理消息内容出错，尝试回滚", e);
                    safeRollback(batchId);
                    throw e;
                }

            } catch (Exception e) {
                log.error("处理canal消息出错", e);

                if (retryTimes++ >= MAX_RETRY_TIMES) {
                    log.error("达到最大重试次数{}，等待后重新尝试", MAX_RETRY_TIMES);
                    retryTimes = 0;
                    try {
                        Thread.sleep(MAX_RETRY_DELAY);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }

                long sleepTime = Math.min(retryDelay * 2, MAX_RETRY_DELAY);
                log.warn("{}秒后尝试第{}次重连...", sleepTime / 1000, retryTimes);
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                retryDelay = sleepTime;
            }
        }
    }


    /**
     * 发送心跳保持连接
     */
    private void sendHeartbeat() {
        try {
            // 发送空ack作为心跳
            canalConnector.ack(-1);
            log.debug("发送心跳保持连接");
        } catch (Exception e) {
            log.error("发送心跳失败", e);
            try {
                if (canalConnector.checkValid()) {
                    canalConnector.disconnect();
                }
            } catch (Exception ex) {
                log.error("断开连接出错", ex);
            }
        }
    }

    private void reconnectCanal() {
        try {
            canalConnector.disconnect();
            canalConnector.connect();
            canalConnector.subscribe();
            log.info("成功重新连接到Canal服务器");
        } catch (Exception e) {
            log.error("连接Canal服务器失败", e);
            throw e;
        }
    }

    private void safeRollback(long batchId) {
        try {
            canalConnector.rollback(batchId);
        } catch (Exception ex) {
            log.error("回滚canal消息出错", ex);
            try {
                if (canalConnector.checkValid()) {
                    canalConnector.disconnect();
                }
            } catch (Exception e) {
                log.error("断开连接出错", e);
            }
        }
    }


    private void handleMessage(List<CanalEntry.Entry> entries) {
        for (CanalEntry.Entry entry : entries) {
            if (entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONBEGIN || entry.getEntryType() == CanalEntry.EntryType.TRANSACTIONEND) {
                continue;
            }

            CanalEntry.RowChange rowChange;
            try {
                rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            } catch (Exception e) {
                throw new RuntimeException("解析binlog事件错误", e);
            }

            String schemaName = entry.getHeader().getSchemaName();
            String tableName = entry.getHeader().getTableName();
            String fullTableName = schemaName + "." + tableName;

            log.info("====== 收到变更: fullTableName={} ======", fullTableName);

            if (!MONITOR_TABLES.contains(fullTableName)) {
                continue;
            }

            System.out.println("表名：" + tableName);
            CanalEntry.EventType eventType = rowChange.getEventType();

            log.info("======> binlog[{}:{}], name[{},{}], eventType: {}", entry.getHeader().getLogfileName(), entry.getHeader().getLogfileOffset(), schemaName, tableName, eventType);


            for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                if (rowChange.getEventType() == CanalEntry.EventType.INSERT) {
                    handleInsert(rowData.getAfterColumnsList(), tableName);

                }
            }
        }
    }


    // 在你的 Canal 处理方法中
    private void handleInsert(List<CanalEntry.Column> columns, String tableName) {
        Map<String, String> map = new HashMap<>();
        for (CanalEntry.Column column : columns) {
            map.put(column.getName(), column.getValue());
        }
        log.info("表名：{}，数据：{}", tableName, map);

        // 1. 构建完整的消息对象
        MessageResponse messageResponse = buildMessageFromMap(map);
        log.info("消息体：{}", messageResponse);
        //消息写入redis zset 并且根据时间戳删除7天前的消息
        String sessionKey = CommonConstant.SESSION_KEY_REDIS + messageResponse.getSessionId();
        Long createdMillis= FormatDateUtil.parseDateTimeToMillis(messageResponse.getCreatedTime());
        String messageResponseJson = JSONUtil.toJsonStr(messageResponse);

        try {
            //普通调用：发送 ZADD → 等 ZADD 响应 → 发送删除命令 → 等删除响应
            //Pipeline：发送 ZADD → 发送删除命令 → 一起读取两个响应
            stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                public @Nullable <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                    stringRedisTemplate.opsForZSet()
                            .add(sessionKey, messageResponseJson, createdMillis);
                    stringRedisTemplate.opsForZSet().removeRangeByScore(sessionKey, -1,  System.currentTimeMillis() - CommonConstant.SEVEN_DAYS_MILLIS);
                    return null;
                }
            });

            log.debug("消息已存入Redis, sessionId={}, messageId={}",
                    messageResponse.getSessionId(), messageResponse.getMessageId());
        } catch (Exception e) {
            log.error("存储消息到Redis失败, sessionId={}, messageId={}",
                    messageResponse.getSessionId(), messageResponse.getMessageId(), e);
        }
    }

    private MessageResponse buildMessageFromMap(Map<String, String> map) {
        MessageResponse messageResponse = new MessageResponse();
        messageResponse.setSessionId(Long.valueOf(map.get("session_id")));
        messageResponse.setSenderId(Long.valueOf(map.get("sender_id")));
        messageResponse.setMessageId(Long.valueOf(map.get("message_id")));
        Integer type = Integer.valueOf(map.get("type"));
        messageResponse.setType(type);
        messageResponse.setSessionType(Integer.valueOf(map.get("session_type")));
        messageResponse.setCreatedTime(map.get("created_time"));

        // 根据消息类型解析 content
        MessageBody body = new MessageBody();
        body.setContent(map.get("content"));
        if (StringUtils.isEmpty(map.get("reply_id"))) {
            body.setReplyId(null);
        } else {
            body.setReplyId(Long.valueOf(map.get("reply_id")));
        }
        messageResponse.setBody(body);
        return messageResponse;
    }
}