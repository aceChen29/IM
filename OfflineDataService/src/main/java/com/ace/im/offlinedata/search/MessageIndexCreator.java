package com.ace.im.offlinedata.search;

import lombok.RequiredArgsConstructor;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.IOException;

@RequiredArgsConstructor
public class MessageIndexCreator {
    private final RestHighLevelClient client;
    private static final String INDEX_NAME = "message";
    private static final String MAPPING_TEMPLATE = "{"
            + "\"mappings\":{\"properties\":{"
            + "\"messageId\":{\"type\":\"keyword\"},"
            + "\"senderId\":{\"type\":\"keyword\"},"
            + "\"sessionId\":{\"type\":\"keyword\"},"
            + "\"type\":{\"type\":\"integer\"},"
            + "\"content\":{\"type\":\"text\",\"analyzer\":\"ik_max_word\",\"search_analyzer\":\"ik_smart\"},"
            + "\"replyId\":{\"type\":\"keyword\"},"
            + "\"sessionType\":{\"type\":\"integer\"},"
            + "\"createdTime\":{\"type\":\"date\",\"format\":\"strict_date_optional_time||epoch_millis\"}"
            + "}}}";
    void createIndex() throws IOException {

        // 2.发送请求，判断索引库是否存在
        boolean exists = client.indices().exists(new GetIndexRequest(INDEX_NAME), RequestOptions.DEFAULT);
        // 3.输出查询结果
        System.out.println(exists ? "索引库已经存在！" : "索引库不存在！");
        if(exists) return;
        CreateIndexRequest request = new CreateIndexRequest(INDEX_NAME);
        request.source(MAPPING_TEMPLATE, XContentType.JSON);
        client.indices().create(request, RequestOptions.DEFAULT);
    }
}
