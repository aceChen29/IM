package com.ace.im.offlinedata.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.ace.im.common.common.ErrorCode;
import com.ace.im.common.constant.CommonConstant;
import com.ace.im.common.exception.BusinessException;
import com.ace.im.common.model.dto.MessageBody;
import com.ace.im.common.model.dto.MessageRequest;
import com.ace.im.common.model.vo.MessageResponse;
import com.ace.im.common.utils.FormatDateUtil;
import com.ace.im.offlinedata.model.dto.HistoryMessageRequest;
import com.ace.im.offlinedata.model.dto.OfflineMessageRequest;
import com.ace.im.user.api.client.UserServiceClient;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ace.im.common.model.entity.Message;
import com.ace.im.offlinedata.service.MessageService;
import com.ace.im.offlinedata.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
* @author cy
* @description 针对表【message】的数据库操作Service实现
* @createDate 2026-10-27 22:36:43
*/
@Service
@RequiredArgsConstructor
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService{

    @Override
    public void saveMessageToMySQL(MessageRequest messageRequest) {
        Message message = new Message();
        BeanUtil.copyProperties(messageRequest, message);
        message.setContent(messageRequest.getBody().getContent());
        message.setReplyId(messageRequest.getBody().getReplyId());
        if(!save(message))
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
    }
    private final StringRedisTemplate stringRedisTemplate;
    private final UserServiceClient userServiceClient;
    /*
     通过用户的userId得到用户的所有会话Id， 计算离线时长，如果离线时长< 7天，则从redis中获取消息
     如果离线时长 > 7 天，去数据库查从离线时间到7天之前的冷消息，7内的数据从redis中获取，加入到key为sessionId， value为消息的集合
     这里还做了数据库查询的优化 没有对于每个会话去连接一次数据库查消息
     而是返回所有消息后再通过会话Id groupby 减少数据库连接
    * */
    @Override
    public Map<Long, List<MessageResponse>> getOfflineMessages(OfflineMessageRequest request) {
        Map<Long, List<MessageResponse>> result = new HashMap<>();
        if (request.getUserId() == null || request.getOfflineTime() == null) {
            return result;
        }

        List<Long> sessionIds = userServiceClient.getSessionIdsByUserId(request.getUserId());
        if (sessionIds == null || sessionIds.isEmpty()) {
            return result;
        }

        long nowTime = System.currentTimeMillis();
        if (request.getOfflineTime() >= nowTime) {
            return result;
        }
        long hotBoundary = nowTime - CommonConstant.SEVEN_DAYS_MILLIS;
        long offlineTime = request.getOfflineTime();
        if (offlineTime < hotBoundary) {
            // 一次 SQL 查出所有会话在冷区间内的消息。
            getMessagesFromMySQL(sessionIds, offlineTime, hotBoundary, result);
        }
        // 冷区间查询不含边界，Redis 从边界开始；纯热区间从离线时间的下一毫秒开始。
        long redisStart = offlineTime < hotBoundary ? hotBoundary : offlineTime + 1;
        getMessagesFromRedis(sessionIds, redisStart, nowTime, result);
        result.values().forEach(messages -> messages.sort(
                Comparator.comparing(MessageResponse::getCreatedTime)
                        .thenComparing(MessageResponse::getMessageId)));
        return result;
    }
    /** 向上翻页：先取 beforeTime 之前的热消息，不足一页时从冷数据补齐。 */
    @Override
    public List<MessageResponse> getHistoryMessages(HistoryMessageRequest request) {
        if (request.getSessionId() == null || request.getBeforeTime() == null
                || request.getBeforeTime() <= 0) {
            return List.of();
        }
        int limit = request.getLimit() == null ? CommonConstant.DEFAULT_LIMIT : request.getLimit();
        if (limit <= 0) {
            return List.of();
        }

        long beforeTime = request.getBeforeTime();
        long nowTime = System.currentTimeMillis();
        long hotBoundary = nowTime - CommonConstant.SEVEN_DAYS_MILLIS;
        List<MessageResponse> result = new ArrayList<>();

        if (beforeTime > hotBoundary) {
            result.addAll(getMessagesFromRedisBefore(request.getSessionId(), hotBoundary,
                    Math.min(beforeTime - 1, nowTime), limit));
        }
        if (result.size() < limit) {
            // Redis 查的是热区间，MySQL 只补查边界之前的冷消息。
            long mysqlBeforeTime = Math.min(beforeTime, hotBoundary);
            result.addAll(getMessagesFromMySQLBefore(request.getSessionId(), mysqlBeforeTime,
                    limit - result.size()));
        }

        result.sort(Comparator.comparing(MessageResponse::getCreatedTime)
                .thenComparing(MessageResponse::getMessageId).reversed());
        return result;
    }

    /** Redis ZSet 按分数倒序查询，最多返回 limit 条。 */
    private List<MessageResponse> getMessagesFromRedisBefore(Long sessionId, long startTime,
                                                             long endTime, int limit) {
        String key = CommonConstant.SESSION_KEY_REDIS + sessionId;
        Set<String> jsonMessages = stringRedisTemplate.opsForZSet()
                .reverseRangeByScore(key, startTime, endTime, 0, limit);
        if (jsonMessages == null || jsonMessages.isEmpty()) {
            return List.of();
        }
        return jsonMessages.stream()
                .map(json -> JSONUtil.toBean(json, MessageResponse.class))
                .toList();
    }

    /** MySQL 从指定时间之前的冷消息里倒序取剩余条数。 */
    private List<MessageResponse> getMessagesFromMySQLBefore(Long sessionId, long beforeTime, int limit) {
        List<Message> messages = lambdaQuery()
                .eq(Message::getSessionId, sessionId)
                .lt(Message::getCreatedTime, new Date(beforeTime))
                .orderByDesc(Message::getCreatedTime, Message::getMessageId)
                .last("LIMIT " + limit)
                .list();
        return convertToResponses(messages);
    }

    /** 按 ZSet 的毫秒时间戳范围查询热消息，两个端点均包含。 */
    private void getMessagesFromRedis(List<Long> sessionIds, long startTime, long endTime,
                                      Map<Long, List<MessageResponse>> result) {
        for (Long sessionId : sessionIds) {
            String key = CommonConstant.SESSION_KEY_REDIS + sessionId;
            Set<String> jsonMessages = stringRedisTemplate.opsForZSet()
                    .rangeByScore(key, startTime, endTime);
            if (jsonMessages == null || jsonMessages.isEmpty()) {
                continue;
            }
            List<MessageResponse> redisMessages = jsonMessages.stream()
                    .map(json -> JSONUtil.toBean(json, MessageResponse.class))
                    .toList();
            List<MessageResponse> messages = new ArrayList<>(result.getOrDefault(sessionId, List.of())); // 复制已有消息
            messages.addAll(redisMessages);// 把 Redis 消息加到新 List
            result.put(sessionId, messages);// 用合并后的 List 更新 Map
        }
    }

    /** 一次查询所有会话在 (startTime, endTime) 内的冷消息，再按会话 ID 分组。 */
    private void getMessagesFromMySQL(List<Long> sessionIds, long startTime, long endTime,
                                      Map<Long, List<MessageResponse>> result) {
        List<Message> messages = lambdaQuery()
                .in(Message::getSessionId, sessionIds)
                .gt(Message::getCreatedTime, new Date(startTime))
                .lt(Message::getCreatedTime, new Date(endTime))
                .orderByAsc(Message::getCreatedTime, Message::getMessageId)
                .list();
        Map<Long, List<MessageResponse>> grouped = convertToResponses(messages).stream()
                .collect(Collectors.groupingBy(MessageResponse::getSessionId));
        grouped.forEach((sessionId, responses) -> result.put(sessionId, new ArrayList<>(responses)));
    }

    /** MySQL 实体转响应对象，保持与 Redis 中的 createdTime 格式一致。 */
    private List<MessageResponse> convertToResponses(List<Message> messages) {
        return messages.stream().map(message -> {
            MessageResponse response = BeanUtil.copyProperties(message, MessageResponse.class);
            response.setCreatedTime(FormatDateUtil.formatDate(message.getCreatedTime()));

            MessageBody body = new MessageBody();
            body.setContent(message.getContent());
            body.setReplyId(message.getReplyId());
            response.setBody(body);
            return response;
        }).toList();
    }
}
