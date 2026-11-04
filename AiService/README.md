# AiService模块

```text
AiService/
├── pom.xml                 # 聚合父模块，packaging=pom
├── ai-facade/              # 普通JAR：Feign接口、请求DTO、响应VO
└── ai-service/             # 可运行服务：Controller、模型、RAG、工具、记忆、监控
```

服务名称仍为`AiService`，默认端口仍为`8105`。

## 构建和运行

从IM项目根目录执行：

```bash
mvn -pl AiService/ai-service -am package
java -jar AiService/ai-service/target/ai-service-0.0.1-SNAPSHOT.jar
```

IDEA重新加载根`pom.xml`后，启动类仍为`com.ace.im.ai.AiServiceApplication`，运行配置的模块classpath选择`ai-service`。
资源和本地开发配置位于`AiService/ai-service/src/main/resources`，原有`application-dev.yml`已经迁移；该文件继续被Git忽略。
原`AiService/target/AiService-0.0.1-SNAPSHOT.jar`是拆分前的构建产物，不再作为启动入口。

Docker构建：

```bash
docker build -t infinitechat-ai-service:local AiService/ai-service
```

## 服务注册

默认启用Nacos发现和注册：

```yaml
spring:
  application:
    name: AiService
  cloud:
    nacos:
      discovery:
        enabled: ${AI_DISCOVERY_ENABLED:true}
        register-enabled: true
        server-addr: ${NACOS_SERVER_ADDR:${SERVER_ADDR:127.0.0.1:18375}}
```

本机使用已有`infinitechat-nacos`容器。HTTP端口18375，客户端gRPC端口19375。
跨主机/容器部署时，配置实际可访问的Nacos地址和AiService注册IP。

## 其他服务调用

调用方依赖`com.ace:ai-facade:0.0.1-SNAPSHOT`，不要依赖`ai-service`或聚合模块。
OfflineDataService和RealTimeService已经添加依赖并启用`AiServiceClient`扫描。

```java
import com.ace.im.ai.api.client.AiServiceClient;

String summary = aiServiceClient.chatSummary(historyLog);
```

普通聊天返回`ChatResponse`，会话总结返回`String`，与当前Controller保持一致。
`ChatRequest`、`KnowledgeInput`、`KnowledgeQuestion`、`ChatResponse`位于`com.ace.im.ai.api.model`下。

流式`/api/ai/streamChat`仍通过HTTP SSE访问，不声明为同步Feign的`Flux`返回值。
`/api/ai/summary`使用POST，聊天记录作为UTF-8纯文本请求体，不是URL参数，也不是JSON对象。
调用方仍使用`aiServiceClient.chatSummary(historyLog)`，Feign自动写入请求体。
可直接运行`ai-service/src/test/http/chat-summary.http`中的示例。
