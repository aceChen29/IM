package com.ace.im.offlinedata.search;

import com.ace.im.offlinedata.config.ElasticsearchConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

/** 手动创建业务索引；只加载 ES 配置，索引和客户端生命周期分开管理。 */
@SpringBootTest(classes = {ElasticsearchConfig.class, MessageIndexCreator.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MessageIndexTest {

    @Autowired
    private MessageIndexCreator creator;

    @Test
    void createMessageIndex() throws IOException {
        creator.createIndex();
    }
}
