package com.ace.im.offlinedata.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.ace.im.ai.api.client.AiServiceClient;
import com.ace.im.common.common.ErrorCode;
import com.ace.im.common.constant.CommonConstant;
import com.ace.im.common.exception.BusinessException;
import com.ace.im.common.model.dto.MessageBody;
import com.ace.im.common.model.dto.MessageRequest;
import com.ace.im.common.model.vo.MessageResponse;
import com.ace.im.common.utils.FormatDateUtil;
import com.ace.im.offlinedata.model.dto.*;
import com.ace.im.offlinedata.model.vo.MessageSearchResponse;
import com.ace.im.offlinedata.model.vo.MessageSearchVO;
import com.ace.im.offlinedata.model.po.MessageDoc;
import com.ace.im.user.api.client.UserServiceClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ace.im.common.model.entity.Message;
import com.ace.im.offlinedata.service.MessageService;
import com.ace.im.offlinedata.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
* @author cy
* @description 针对表【message】的数据库操作Service实现
* @createDate 2026-10-27 22:36:43
*/
@Service
@RequiredArgsConstructor
@Slf4j
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
    private final RestHighLevelClient client;
    @Override
    public MessageSearchResponse search(MessageSearchRequest messageSearchRequest) {
        // 1. 检查基本查询参数，避免空关键词或非法分页数量。
        if (messageSearchRequest == null
                || !StringUtils.hasText(messageSearchRequest.getSessionId())
                || !StringUtils.hasText(messageSearchRequest.getKeyword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话ID和搜索关键词不能为空");
        }
        String keyword = messageSearchRequest.getKeyword().trim();
        int pageSize = messageSearchRequest.getPageSize();
        if (keyword.length() > 100 || pageSize <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "关键词最多100字，每页数量必须大于0");
        }
        if (messageSearchRequest.getStartTime() != null
                && messageSearchRequest.getEndTime() != null
                && !messageSearchRequest.getStartTime().isBefore(messageSearchRequest.getEndTime())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "起始时间必须早于结束时间");
        }

        // 2. 指定 message 索引，并先初始化查询体，才能调用 source().query(...)。
        SearchRequest searchRequest = new SearchRequest(CommonConstant.INDEX_NAME);
        searchRequest.source(new SearchSourceBuilder());

        // filter 做精确过滤；must 对 content 分词匹配，不是 SQL 的字符串包含查询。
        // AND 表示关键词分词后，各词之间是“且”的关系；不要求相邻或按输入顺序出现。
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .filter(QueryBuilders.termQuery("sessionId", messageSearchRequest.getSessionId()))
                .filter(QueryBuilders.termQuery("type", 0))
                .must(QueryBuilders.matchQuery("content", keyword).operator(Operator.AND));

        // 3. 时间范围可只传一端。先统一转成毫秒，正确处理 Z、+08:00 等时区偏移。
        // 区间约定为 [startTime, endTime)：包含起点，不包含终点。
        if (messageSearchRequest.getStartTime() != null) {
            boolQuery.filter(QueryBuilders.rangeQuery("createdTime")
                    .gte(messageSearchRequest.getStartTime().toInstant().toEpochMilli()));
        }
        if (messageSearchRequest.getEndTime() != null) {
            boolQuery.filter(QueryBuilders.rangeQuery("createdTime")
                    .lt(messageSearchRequest.getEndTime().toInstant().toEpochMilli()));
        }
        // 只设置一次顶层 query。再次 query(rangeQuery) 会覆盖上面的 bool 条件。
        searchRequest.source().query(boolQuery);

        // 4. 同毫秒的消息再用唯一 messageId 排序，保证翻页位置明确。
        // 多取一条仅用于判断 hasMore，不返回给前端，也不计算精确总数。
        searchRequest.source()
                .sort("createdTime", SortOrder.DESC)
                .sort("messageId", SortOrder.DESC)
                .size(pageSize + 1)
                // 不统计匹配消息的总条数；是否有下一页由多取的那一条判断。
                .trackTotalHits(false)
                // 设置 ES 查询超时为 3 秒，不是 HTTP 请求全过程的硬性 3 秒截止。
                // ES 超时可能返回部分结果，下面检查 isTimedOut() 后会提示重试。
                .timeout(TimeValue.timeValueSeconds(3));

        // 第一页不传 cursor；下一页使用上次返回的两个 ES sort 值。
        MessageSearchCursor cursor = messageSearchRequest.getCursor();
        if (cursor != null) {
            if (cursor.getCreatedTime() == null || cursor.getCreatedTime() <= 0
                    || !StringUtils.hasText(cursor.getMessageId())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "分页游标必须包含时间和消息ID");
            }
            searchRequest.source().searchAfter(new Object[]{
                    cursor.getCreatedTime(), cursor.getMessageId()
            });
        }

        // 5. 高亮完整正文。先转义原文中的 HTML，再用受控的 em 标签标记匹配词。
        searchRequest.source().highlighter(new HighlightBuilder()
                .encoder("html")
                .preTags("<em>").postTags("</em>")
                .field(new HighlightBuilder.Field("content").numOfFragments(0)));//不切分为片段

        try {
            // 6. 同步执行搜索；超时或分片失败时不能当成“没有匹配消息”。
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            if (searchResponse.isTimedOut() || searchResponse.getFailedShards() > 0) {
                throw new BusinessException(ErrorCode.SYSTEM_BUSY, "搜索暂不可用，请稍后重试");
            }
            SearchHit[] hits = searchResponse.getHits().getHits();
            boolean hasMore = hits.length > pageSize;
            int count = Math.min(hits.length, pageSize);
            List<MessageSearchVO> records = new ArrayList<>(count);

            // 7. _source 保留原文，高亮从独立的 highlight 字段中获取。
            for (int i = 0; i < count; i++) {
                SearchHit hit = hits[i];
                MessageDoc doc = JSONUtil.toBean(hit.getSourceAsString(), MessageDoc.class);
                MessageSearchVO vo = BeanUtil.copyProperties(doc, MessageSearchVO.class, "createdTime");
                vo.setCreatedTime(FormatDateUtil.formatDate(doc.getCreatedTime()));

                HighlightField highlight = hit.getHighlightFields().get("content");
                if (highlight != null && highlight.fragments() != null
                        && highlight.fragments().length > 0) {
                    vo.setHighlightContent(highlight.fragments()[0].string());
                } else {
                    // 无高亮片段时返回转义后的原文，不把正文中的 HTML 当标签执行。
                    vo.setHighlightContent(HtmlUtils.htmlEscape(doc.getContent()));
                }
                records.add(vo);
            }

            // 8. nextCursor 必须取最后一条实际返回的消息，不能取多查出来的那一条。
            MessageSearchResponse result = new MessageSearchResponse();
            result.setRecords(records);
            result.setHasMore(hasMore);
            if (hasMore && count > 0) {
                Object[] sortValues = hits[count - 1].getSortValues();
                MessageSearchCursor nextCursor = new MessageSearchCursor();
                nextCursor.setCreatedTime(((Number) sortValues[0]).longValue());
                nextCursor.setMessageId(String.valueOf(sortValues[1]));
                result.setNextCursor(nextCursor);
            }
            return result;
        } catch (IOException | ElasticsearchException e) {
            log.error("搜索消息失败，sessionId={}", messageSearchRequest.getSessionId(), e);
            throw new BusinessException(ErrorCode.SYSTEM_BUSY, "搜索暂不可用，请稍后重试");
        }
    }
    private AiServiceClient aiServiceClient;
    @Override
    public String getSummary(SessionSummaryRequest sessionSummaryRequest) {
        return aiServiceClient.chatSummary(historyChatLog(sessionSummaryRequest.getSessionId(), sessionSummaryRequest.getHours()));
    }
    public String historyChatLog(Long sessionId, Integer hours) {
        Map<Long, String> userNickName = userServiceClient.getUserNickName(sessionId);

        // 获取当前上海时间
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        LocalDateTime nowShanghai = LocalDateTime.now(shanghai);
        LocalDateTime threshold = nowShanghai.minusHours(hours);
        // 格式化为 yyyy-MM-dd HH:mm:ss 字符串
        String timeThresholdStr = threshold.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        QueryWrapper<Message> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("session_id", sessionId).ge("created_time", timeThresholdStr);

        List<Message> messages = this.list(queryWrapper);
        StringBuilder chatLog = new StringBuilder();

        for (Message message : messages) {
            Long senderId = message.getSenderId();
            String senderName = userNickName.get(senderId);
            String timeStr = FormatDateUtil.formatDate(message.getCreatedTime());
            chatLog.append("[").append(senderName).append("] ").append(timeStr).append("：").append(message.getContent()).append("\n");
        }
        String result = "没有消息";

        try {
            result = aiServiceClient.chatSummary(chatLog.toString().trim());
        } catch (Exception e) {
            throw new RuntimeException("调用会话总结失败");
        }

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
