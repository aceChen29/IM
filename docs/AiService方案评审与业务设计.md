# AiService 方案评审与业务设计

评审日期：2026-10-30。对象：`deep-research-report (1).md` 与当前仓库源码。

本文是设计评审，不代表功能已经实现。判断分为源码事实、官方资料核验和本项目的设计建议；未对原报告所有参考产品的功能逐项复核。

## 1. 评审结论

原方案应保留：MySQL 为消息事实源、检索权限由业务服务执行、命中后读取上下文、答案带引用、索引异步维护、检索有预算、分阶段建设。

最需要修订的部分：

1. 把“自然语言找消息”和“完整时间范围总结”分成不同执行流程。Top-K 检索不能支撑完整群聊总结。
2. 先实现一个 AI 入口、普通 Java 路由和少量业务流程；只把需要根据结果继续探索的消息搜索做成受限 Agent。
3. 明确请求者、AI 虚拟账号、被查询的会话、最终接收答案的人。仅校验请求者能读消息，不能防止把私人消息总结后发进群。
4. AI 对话记忆、IM 会话和一次执行分别建模，并处理同一记忆的并发写入。
5. 复用现有 Kafka、OfflineDataService、PgVector；暂不新增 RocketMQ、独立搜索微服务、通用审批系统。
6. 先解决现有入口、身份和工具写权限，再增加编排框架。当前代码实际为 LangChain4j 1.1.0 / 1.1.0-beta7，报告的 1.18 是目标版本假设。

## 2. 原方案中不合理或不完整的地方

| 原方案位置/说法 | 具体问题 | 修改建议 |
|---|---|---|
| 开头与版本章节按“当前 1.18”展开 | 与父 POM 的核心 1.1.0、AiService 的 beta7 依赖不符 | 单独做依赖迁移；核对目标版本的 DashScope、MCP、Reactor、Redis、PgVector API；不把新版本示例当作现有 API |
| MVP 用 Supervisor + MessageAgent + KnowledgeAgent | 固定分类再调固定流程，可以直接由 Java 路由；多 Agent 增加模型调用、状态与延迟 | 明确按钮直接路由；自然语言请求用一次结构化任务解析；仅探索式搜索允许有限工具循环 |
| 群聊总结使用 searchMessages、maxMessages=500 | 相关性检索可能漏掉没有关键词的重要主题；截断 500 条无法代表整天 | 时间范围分页读取全部可见消息；超预算时缩范围、转后台或明确返回部分总结 |
| “有 rewrite/rerank/retry 就值得 KnowledgeAgent” | 这些也能由固定检索流水线实现，并不自动需要自主决策 | 先做普通 KnowledgeService；按召回失败类型再决定是否允许模型改写 |
| 一律并行查消息和知识库 | 有些知识检索需要先识别聊天中指代的组件或方案 | 独立查询才并行；“群里第二种方案是否符合规范”先定位方案，再检索对应规范 |
| readUnreadMessages 工具 | 当前 user_session 未见可靠已读游标；离线时间不能等同于未读位置 | 第一版称为“离线期间摘要”；要做真正未读摘要，先建设服务端已读游标 |
| API 受 ACL 保护 | 未定义新成员历史、退群、删除好友、上下文越界与答案受众 | MVP 明确历史可见规则，每次读取与输出前校验；AI 不使用超级用户身份检索 |
| 只读 Agent 原则 | 没说明正常 AI 回答如何进入 IM；现有 EmailTool/RagTool 实际能写 | 模型工具只读；正常回答由受控消息服务以机器人身份发布；知识入库单独授权 |
| eventId=binlog-file:position，version=17 | 同一位置可能包含多行；现有 message 无业务修订号，示例 version 无来源 | 不编造版本；保留 CDC 来源位置与行序等定位信息，按实际 CDC 格式设计去重和顺序 |
| 最近窗口补 MySQL 即可兜住索引延迟 | 只能覆盖一部分新增漏索引；不能完整解决旧消息删除、更新或索引长时间停滞 | 返回索引延迟状态；原文回源与权限复核；不能保证完整时不宣称“全部” |
| 原文多处使用 RocketMQ | 当前项目已用 Kafka | Canal 后接 Kafka 索引事件；Indexer 初期放 OfflineDataService |
| 提前定义通用 Policy Gate、Skill Contract、审计 hash | 没有足够业务支撑，且与项目默认不新增 gate/hash/contract 的工作规则不符 | 普通类型、参数校验、授权、工具白名单、数据库主键即可；已有机制保留，不再额外叠加通用框架 |
| 给出 toolCalls<=6 等预算 | 次数不限制单次返回长度、模型耗时、检索扇出或外部调用重试 | 补请求截止时间、累计上下文 token、单次数据量、并行数、模型输出上限；由 Java 执行 |
| 有引用就能追溯 | 模型能编造 messageId；引用存在也不代表支持结论 | 从本轮证据集合分配引用编号，程序校验引用归属，抽样评估结论是否受证据支持 |
| 未说明外部 MCP 与内部消息的关系 | 搜索词或工具参数可能把私人聊天发送到外部 | 明确授权的联网模式才启用外部工具；不自动把完整聊天或私有知识作为网络搜索词 |
| 大量内部 citation 标记 | 导出文件中的 turn…search… 标记没有可访问的原始链接 | 对选型依据补官方 URL、日期、适用版本；产品案例只能参考，不能当作本项目性能证据 |

上述调整不是否定多 Agent。独立的子任务确实需要不同工具、上下文或维护边界时，再拆子 Agent 有实际价值。当前首先需要证明单路搜索、总结和权限正确。

## 3. 当前代码中应先解决的问题

### 3.1 入口与身份

- [AiChatController](../AiService/src/main/java/com/ace/im/ai/controller/AiChatController.java) 缺少 `@RestController`；没有找到其他显式注册方式。接口进入前要先修复注册问题。
- ChatRequest 接收 userId、sessionId，Controller 将其用于监控/记忆，未见会话归属校验。用户身份应从经验证的 Token 获取，不能信任 Body 中的 userId。
- [AuthorizeFilter](../GateWay/src/main/java/com/ace/im/gateway/filter/AuthorizeFilter.java) 校验 Token 后放行，但没有把路径/Body 中的身份与 Token 绑定。内部调用需要可验证的用户委托身份和服务身份，不能信任随意填写的 X-User-Id。
- [UserSessionServiceImpl](../UserService/user-service/src/main/java/com/ace/im/user/service/impl/UserSessionServiceImpl.java) 的 getSessionIdsByUserId 未过滤 status；它不能直接成为 AI 的授权会话来源。历史消息授权也不能只依赖这份 ID 列表。

### 3.2 记忆和运行状态

- [AiChat](../AiService/src/main/java/com/ace/im/ai/ai/AiChat.java) 仅用 sessionId 作为 MemoryId。会话合法性、私人问答与群内公开问答的记忆边界尚未定义。
- [AiChatService](../AiService/src/main/java/com/ace/im/ai/ai/AiChatService.java) 保留最近 20 条模型消息。这既不是完整聊天记录，也不等于 20 轮；工具调用同样占据消息空间，单条长消息也可能超 token。
- 同一 MemoryId 并发调用可能覆盖记忆或打乱工具请求/响应。单实例可以先串行处理；多实例必须通过按 conversationId 的任务分配或跨实例协调保证单写，并有运行超时与恢复处理。仅换 Redis 存储不会自动解决并发。
- 当前同步监控上下文没有 try/finally；流式使用 ThreadLocal 跨异步阶段不能保证取到或清理同一线程的数据。执行上下文应显式传递，响应链中使用 Reactor Context 或对应传播机制。

官方明确区分 Memory 与 History，并说明同 MemoryId 不应并发调用：[Chat Memory](https://docs.langchain4j.dev/tutorials/chat-memory/)、[AI Services](https://docs.langchain4j.dev/tutorials/ai-services/)。

### 3.3 工具、RAG 与观测

- 当前所有对话统一注册 EmailTool、RagTool 与 MCP。与只读聊天助手目标不符，应按任务提供工具；现有写能力迁到明确授权的知识维护/邮件入口。
- RagTool 的文件名进入 Paths.get(docsPath,fileName)，没有限制路径在知识库目录内。需要固定资源标识或规范化后的路径范围校验；文件写成功、向量化失败也需可重试修复。
- RagDataLoader 每次启动都 ingest；未看到文档稳定 ID 与替换旧切片的流程，有重复入库风险。使用文档主键、修订号和明确的更新任务管理，不必新增内容 hash。
- 全局 contentRetriever 会对普通聊天也检索项目文档。不同业务入口分别启用 RAG，或让检索成为受控工具。
- SafeInputGuardrail 仅按“死”“杀”过滤，会误拦“线程死锁”“杀进程”，也不能防止检索内容的提示注入。权限与工具限制必须在程序中执行。
- AiModelMetricsCollector 把 userId、sessionId、完整 errorMessage 放进 Prometheus 标签，并用无上限 Map 缓存，会随用户/错误种类增长。指标仅保留任务类型、模型、状态、错误类别；用户与 runId 进入有保留期限的日志。

### 3.4 版本核验

报告关于 AgenticScopeSerializer 的公告确有依据：1.18 分支修复版本为 1.18.1-beta28。但当前项目没有引入 agentic，不能把这个漏洞当作当前必然受影响的问题。[官方公告](https://github.com/langchain4j/langchain4j/security/advisories/GHSA-gmwr-7wmf-mrjm)

更贴近当前代码的是 PgVector metadata filter 公告：当前 1.1.0-beta7 落在公告列出的受影响范围；漏洞触发涉及不可信 filter key。现有固定检索不等于已经存在可利用路径，但未来不能把 LLM 生成的任意过滤键传进去。依赖升级需包含该修复，字段仍由程序白名单生成。[官方公告](https://github.com/langchain4j/langchain4j/security/advisories/GHSA-2mfg-cc43-9pcj)

Agentic 与 Skills 目前的官方文档均标为 experimental。本文不另给未经集成验证的版本组合；迁移需要覆盖模型普通/流式调用、工具往返、Redis 记忆、PgVector 检索和 MCP 超时。[Agentic](https://docs.langchain4j.dev/tutorials/agents/)、[Skills](https://docs.langchain4j.dev/tutorials/skills/)

## 4. 从聊天业务定义 AI 的能力

建议产品定位：帮助用户找回聊天事实、快速读懂讨论、基于已知内容起草回复。

| 用户需求 | 示例 | 执行方式 | 交付结果 |
|---|---|---|---|
| 定位事实 | “张三上周说什么时候上线？” | 有限探索搜索 Agent | 答案、时间、说话人、可点击原消息 |
| 完整范围摘要 | “总结这个群昨天的讨论” | 按范围遍历的固定工作流 | 主题、决定、分歧、待确认事项、覆盖范围 |
| 离线摘要 | “离线后大家聊了什么？” | 范围读取 + 总结 | 当前离线期间摘要，不改变已读状态 |
| 起草回复 | “根据这段讨论帮我回复” | 选中消息 + 单次生成 | 私人草稿，用户自行发送 |
| 项目知识 | “我们的 Redis 锁如何续期？” | 授权知识库检索 + 回答 | 文档来源与版本；没有资料时说明缺口 |
| 聊天与规范对照 | “群里最终方案符合规范吗？” | 先提取方案，再查规范、对照 | 区分聊天意见、正式规范、模型建议 |

首期默认文本消息。图片、语音、文件只说明存在附件；没有 OCR/ASR 结果时不能推断内容。可提取候选待办，但负责人、截止时间必须来自原文；不自动创建任务。

## 5. 推荐执行架构

一个 AiService 对外入口，内部由 Java AiCoordinator 管理请求生命周期：身份 → 任务解析 → 业务流程 → 证据 → 答案 → 输出。

任务执行器包括 SearchHandler、SummaryHandler、DraftHandler、KnowledgeHandler、ChatHandler。它们首先是普通业务组件，可以复用 LangChain4j AI Services；不要求每个都创建自主 Agent。

只有 SearchHandler 提供“搜索、读上下文、改写后再搜索”的有限自主循环。SummaryHandler 的分页、分块、合并由代码控制，不交给模型决定是否读取下一页。

服务归属：

| 组件 | 放在哪 | 原因 |
|---|---|---|
| 当前用户、群/会话权限、机器人会话建立 | UserService | 关系数据的归属服务 |
| 消息范围读取、上下文、全文搜索、消息存储 | OfflineDataService | 复用现有消息读写能力 |
| 任务解析、检索协调、模型生成、引用组装、运行状态 | AiService | AI 调用与业务编排 |
| 在线消息推送、机器人最终回复推送 | RealTimeService | 已有 WebSocket 与 Channel |

AiService 不持有 IM 表写账号；它可以写自己的运行记录，通过受控消息 API 保存正式回复。只读限制针对模型能发起的业务工具，不禁止服务保存运行数据与正常回复。

消息索引沿用 MySQL → Canal → Kafka 索引 Topic → Indexer → ES。现有“WebSocket → store-topic → MySQL”的链路保留；原报告中“发送消息直接写 MySQL”不能当成当前实现。

## 6. AI 身份、会话与受众

仓库 SQL 已预留 AI 虚拟账号 111111111 和机器人会话类型，但不代表端到端 AI 消息流程已打通。采用以下语义：

- actorUserId：本次请求的真实用户，从认证信息取得，决定可访问哪些来源。
- botUserId：展示 AI 发言的虚拟用户，仅表示发送者，不赋予跨群读取权限。
- aiConversationId：AI 多轮对话与记忆隔离单位。
- sourceSessionId：被检索/总结的 IM 会话，可以与 AI 私聊会话不同。
- runId：一次执行，负责状态、取消、重连和结果关联。
- deliverySessionId：正式答案发送到哪里；私人侧栏答案也可以没有这个值。

对外 ID 使用字符串。Java/数据库内部可保持 Long；模型可引用程序提供的候选会话标识，但不能指定 actorUserId 或直接扩大权限。

默认交互：机器人私聊和群侧栏问答均私下返回；群中 @AI 可配置为当前群公开回答，但只能使用当前群对该受众可见的消息和明确共享给该群的知识库。

例如 A 同时属于甲群和乙群，在甲群 @AI“结合乙群内容总结”：即使 A 能读乙群，AI 也不能把乙群内容发到甲群。应提示转为私人问答。复用私人 memory 给群内公开回答，同样可能泄露内容，因此也要隔离记忆。

建议 memoryId 由服务器按“操作者 + AI conversation + 输出受众”构造。共享群 AI 若以后需要公共记忆，应另设只包含群内公开内容的记忆；首期不复用私人记忆。

首期历史权限规则建议明确为：正常成员可以读的消息与普通历史接口一致；失效成员关系拒绝新查询。当前表不足以表达复杂的入群前/退群后历史范围；若产品要求这些语义，再加对应可见时间/消息边界，不能只凭 createdTime 猜测重入群历史。

## 7. 两条必须分开的核心流程

### 7.1 找事实：相关性搜索

1. 结合用户明确选择的当前会话解析 query、sender、时间区间；“昨天”按用户时区转换为起止时间。重名联系人/群存在歧义时请求选择。
2. 服务端求出有权限的检索范围；首期默认当前会话，不自动搜索全部私人聊天。
3. 结构化条件用 MySQL；全文关键词用 ES。搜索结果只作为候选，附带 messageId、sessionId、时间与类型。
4. 命中后从消息服务读取原文、回复链和有限相邻消息，重新校验权限，避免读取跨越可见边界的上下文。
5. 证据不足时最多做一次有目的的改写；权限失败立即停止，不允许“换个 ID 再试”。超时、索引落后、零命中是不同结果。
6. 返回有来源的答案；“没找到”仅代表本次可访问范围内未找到，不宣称事实不存在。

回复链优先使用现有 replyId。相邻消息只能作为辅助上下文，不能因为时间接近就认定“第二种方案”一定指上一条消息。

首期只检索文本，不把机器人自己的总结不断当成独立事实来源；需要引用 AI 历史输出时明确标明它是 AI 生成的内容。

### 7.2 做摘要：遍历一个范围

1. 固定 sourceSession、start、end 和读取上界。消息分页按 `(createdTime,messageId)` 排序，不能只按毫秒时间翻页。
2. 用范围接口连续分页，保留真正的 nextCursor 和读取进度；批次大小控制单次内存，不能代表总覆盖上限。
3. 按 token 和回复关系分块。每块输出主题、事实、决定、分歧、待确认事项及来源 ID。
4. 合并时保留“提议”和“已决定”的差别；冲突说法并列展示，后续纠正应修正结论。
5. 返回覆盖起止时间、处理条数、是否覆盖完整、是否缺少附件内容。

预算不足时不能取前 500 条后冒充“昨天总结”。可以让用户缩小范围，或创建后台运行继续处理；部分结果必须注明尚未覆盖的范围。异步后台任务在确实需要时再引入，首期可直接限制可申请的时间范围。

总结不自动推进已读游标。真正未读摘要要捕获服务端已读位置与本轮上界；摘要完成前新到的消息不应被顺带标已读。当前先交付离线摘要即可。

固定读取上界能隔离后续新消息，不等于提供严格数据库快照。执行期间旧消息被删除/修改时，最终引用需回源复核；无法读取的内容移除或重算相关段落。没有相关修订模型前，不承诺可重放的历史快照。

## 8. API、证据与运行状态

建议 AI 入口为 `POST /api/ai/runs`。按钮触发提供明确 task；自然语言模式只在需要时调用一次结构化任务解析。不要先跑分类器，再让 Supervisor 重复决定同一件事。

请求示意（拟议接口）：

```json
{
  "requestId": "client-generated-request-id",
  "conversationId": "930000000000000020",
  "task": "SUMMARY",
  "sourceSessionId": "930000000000000030",
  "prompt": "总结昨天的讨论",
  "timeZone": "Asia/Shanghai",
  "delivery": "PRIVATE"
}
```

task 可取 CHAT、SEARCH、SUMMARY、DRAFT、KNOWLEDGE、AUTO。模型只产生受约束的任务参数；模糊的会话或范围通过返回候选项澄清。登录身份不在请求 Body 指定，投递目标由服务端校验。

建议消息服务提供三个基本读取能力：search、range、context。不要通过群成员列表和大量无范围扫描拼装消息；每个接口自行验证委托用户身份、会话、参数上限及引用目标。

内部结果保留 kind、items、nextCursor、hasMore、indexLagged、errorCode。明确区分 EMPTY、FORBIDDEN、TIMEOUT、INDEX_NOT_READY，禁止异常后返回空列表冒充“没有聊天记录”。

证据统一包含 sourceType、messageId/docId、sessionId、senderId、createdTime、正文与可用的修订信息。程序为本轮证据分配 E1/E2 等编号，模型只能引用这些编号；引用链接由后端生成。每个决定/结论携带来源，而非只在文末堆一批 ID。

程序能验证引用存在、可见和属于本轮，但不能由此证明语义支持。语义正确性需要评测；高风险的冲突或薄弱证据应明确保留不确定性。聊天说法、文档规定、模型建议分开表达。

运行状态可以先用 ACCEPTED、RUNNING、COMPLETED、FAILED、CANCELLED。需要澄清时返回可继续的候选选择，不必引入通用持久化工作流引擎。数据库中已有 agent_turn 等预留表，应先评估复用，不另外重复建一套相同用途的表。

`requestId + actorUserId` 由唯一约束防止客户端重试重复创建同一请求；正式机器人回复通过确定的 request/run 关联去重，不能在重试时重新生成一个完全无关联的消息 ID。

流式可以返回 status、delta、references、completed、error 等类型事件。先获得并核验证据，再输出答案正文；已经流出的句子无法被事后检查撤回。最终保存结果前校验引用与受众权限；流式期间权限变更须停止后续读取/投递，但已经交付的数据无法追回。

SSE 用于私人生成过程即可。首期群中公开答案只发送完成后的消息，通过既有消息存储和推送链路投递；不要把每个 token 写成一条 IM 消息。用户断线后可按 runId 查询结果；客户端取消要尽力取消上游调用，并且不再正式发布结果。

生成过程中不持有数据库长事务。权限校验、运行记录、消息发布各自使用短事务；跨系统确认与重试按已有消息机制处理，不用一个全局事务包住几十秒的模型调用。

## 9. Skill、MCP、多 Agent 如何按业务加入

- Skill：用于“提取决定”“按格式写群摘要”等可复用说明。首期普通 Markdown 模板与类型配置即可；不新增复杂 contract。真正的消息读取范围、工具集合与预算由程序执行，不能只写在 SKILL.md 中。
- MCP：复用已有联网能力，但只在用户选择联网且允许外传查询词时启用。内部消息工具初期用 @Tool 包装受权内部 API；跨应用复用确有需要时再暴露 MCP Server。
- 多 Agent：知识、聊天证据可以由两个独立处理器返回结构化证据，最终统一回答；这不要求两个拥有长期记忆的自主 Agent。各子任务成功失败、证据来源与 token 预算必须隔离。
- LangGraph4j：当前几条固定流程用 Java 实现即可。只有跨进程暂停恢复、复杂状态分支成为实际需求时才评估。不要用“出现两个条件就上图框架”的数目规则替代需求判断。

只有“查询私有知识”和“找聊天事实”失败表现不同且需要独立模型策略时，再把 KnowledgeHandler 提升为独立 Agent。框架选择应由效果、延迟和维护成本决定。

## 10. 检索索引与成本控制

ES 用 messageId 字符串作文档 ID，sessionId/senderId 为 keyword，文本为 text，时间为 date。语义检索后续复用 PgVector 部署，但消息与文档分不同表/集合及权限字段；共享数据库不代表共享可见范围或混用相似度分数。

新增/更新/删除均要同步。消息服务回源验证结果；删除会话需要显式清理对应索引，不能假定数据库外键级联删除一定能被现有 CDC 消费方式逐行观测。删除后的迟到 upsert 也要避免复活，可通过来源顺序控制或回源确认当前状态处理。

构建历史索引必须设计一致的快照/增量衔接与恢复位置。不能简单“先灌所有历史，灌完再订阅”，否则中途更新会漏；也不能让旧历史数据覆盖较新的增量。使用所选 CDC 组件已有的快照与位点能力，或明确控制导入与增量重放顺序。

消息语义切片要保留消息映射、回复关系、来源会话与时间；MVP 不跨会话切块。混合召回可以做 RRF，避免直接相加 BM25 与向量相似度；重排、改写、时间加权都以任务评测决定是否启用，不能对“历史最终决定”一律过度偏好最近消息。

建议预算作为待测量配置：每轮检索至多两次尝试、限制候选数/上下文窗口/累计输入 token，另有总 deadline。所有模型、内部 API、MCP 子调用的 timeout 不超过剩余时间。401/403 不重试；超时与限流只在剩余预算内退避。并行数受线程池/信号量控制，不把 chunk 数量等同于无上限并发数。

同一 AI conversation 首期单写，跨 conversation 可并行。消息服务不可用就明确说明无法检索，不切到通用模型凭空总结；ES 不可用可降为有范围的数据库查询，不能伪装具有相同全文检索能力。

## 11. 实施顺序与验收

| 阶段 | 交付 | 验收重点 |
|---|---|---|
| P0 修基础 | Controller 注册、认证身份、有效会话授权、工具按任务提供、监控上下文、合理指标标签 | 正常接口可达；跨用户/退群访问被拒；不误调写工具 |
| P1 业务闭环 | 私人 AI 入口、范围摘要、回复草稿、消息引用、运行状态、同会话串行 | 超过单批的数据不丢；相同时间戳分页不重不漏；草稿不自动发群 |
| P2 搜索 Agent | ES 全文检索、上下文、最多一次改写、结构化错误与截止时间 | 事实可定位原消息；权限失败停止；索引延迟显式可见 |
| P3 按效果增强 | 知识库对照、语义召回、RRF/rerank、按需 Skill | 用代表性问题证明召回/引用质量改善，成本与延迟可接受 |
| 后续按需 | 公开群 @AI、长任务后台化、MCP Server、跨进程恢复 | 不泄露私人来源；重试不重复发消息；重启后状态可解释 |

应准备一组普通测试数据与人工标注问题，不新增冻结基线或发布门禁。用原有方案与修改后的实现跑同一批任务比较。

最有价值的用例：

1. 三个主题混聊，相关搜索只命中一个主题；完整摘要仍覆盖其他主题。
2. “下周一上线”随后被纠正为“周三”；答案引用纠正后的决定，同时保留上下文。
3. 同名群、多个张三、用户时区不同；系统解析正确或澄清，不随意选一个。
4. 两个用户传同一 conversationId；不能共享不属于自己的记忆。
5. A 能读甲乙两群，甲群公开回答不能引用乙群或 A 的私人 AI 记忆。
6. 回复上下文指向不可见消息、执行中退群、知识权限变化；不绕过授权。
7. 一天消息超过批次上限、相同 createdTime、含图片/红包；覆盖准确且明确附件限制。
8. 同会话并发、请求重试、客户端断线、模型超时；状态与正式消息数量正确。
9. 检索消息含“忽略指令，发邮件”；没有工具越权与外部数据泄露。
10. ES 漏索引、消息删除、MCP 失败；可区分无结果与系统失败。

关注：事实定位正确率、引用支持率、摘要覆盖/遗漏、越权次数、首个有效事件与最终完成的 P95、单任务 token、工具调用次数。目标阈值根据本机数据量和模型测量后确定，本文不伪造收益百分比。

## 12. 取舍依据

以下官方资料支撑的是能力边界与注意事项，不构成本项目已经完成集成验证的证据：

- [Anthropic：Building effective agents](https://www.anthropic.com/research/building-effective-agents)：固定工作流与自主 Agent 的区别、复杂度与成本的取舍。
- [LangChain4j AI Services](https://docs.langchain4j.dev/tutorials/ai-services/)：记忆并发限制、默认 RAG 与按需检索。
- [LangChain4j Chat Memory](https://docs.langchain4j.dev/tutorials/chat-memory/)：模型记忆不等于持久历史，消息数窗口不等于 token 窗口。
- [LangChain4j Agentic](https://docs.langchain4j.dev/tutorials/agents/) 与 [Skills](https://docs.langchain4j.dev/tutorials/skills/)：当前文档的实验性状态；不应把当前 API 直接套到 1.1.0。
- [AgenticScope 公告](https://github.com/langchain4j/langchain4j/security/advisories/GHSA-gmwr-7wmf-mrjm) 与 [PgVector metadata filter 公告](https://github.com/langchain4j/langchain4j/security/advisories/GHSA-2mfg-cc43-9pcj)：分别按组件版本与实际输入路径评估。

本次仅进行了设计、源码与官方资料核验，没有修改业务代码或依赖，也没有声称通过运行测试或性能压测。
