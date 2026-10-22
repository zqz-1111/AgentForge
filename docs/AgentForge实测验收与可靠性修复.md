---
date: 2026-10-22
tags: [AgentForge, SpringBoot, SSE, Redis, pgvector, 集成测试]
---

# AgentForge实测验收与可靠性修复

## 一、这次解决什么问题

AgentForge是纯后端API项目。之前的6项测试验证了组件，并不能证明接口鉴权、持久化检索和流式响应全部跑通。本次使用实际HTTP、PostgreSQL/pgvector、Redis和本地模型协议桩，检查请求完整经过应用的结果。

本地模型协议桩是按OpenAI兼容格式返回固定答案和向量的小程序，不是真实模型。它能验证SDK和应用如何调用、转发和处理失败，不能用于证明回答正确率、检索语义质量、模型速度或成本下降。

## 二、前置环境与数据保护

- JDK21：`E:/jdk-21.0.7+6`；Python：`E:/pp/python.exe`。
- Maven：`F:/IDEA/IntelliJ IDEA 2026.1.2/plugins/maven/lib/maven3/bin/mvn.cmd`。
- 复用原`ai-agent-template-postgres-1`及数据卷`ai-agent-template_pgdata`，端口5432。
- 原`agentdb`先整库备份到`F:/work/.workspace-audit/private-backups/agentforge-before-acceptance.sql`。SHA-256：`64c2a91ee5d4d8fcea918f9ff26f49275dd3649b60a9b02f8358bfa6b4d0311e`。
- 验收使用新建`agentforge_validation`数据库，已启用`vector`扩展；Redis复用现有服务但使用14号库和`agentforge:`键前缀，没有执行FLUSHDB。
- 原库验收前后行数相同：API Key 4、文档元数据0、向量1。没有把旧密钥或向量迁入验收库。
- 应用仅监听`127.0.0.1:8085`，模型桩监听`127.0.0.1:18085`。注册令牌及测试密钥只放在Git忽略的`.local-runtime/`。

## 三、修复与原理

### 3.1 API Key管理：先鉴权，再按所有者访问

API Key是调用接口的凭证。旧过滤器跳过整个`/auth/`路径，导致未登录也能列出和停用密钥，实体还会把key序列化出去。

现在只有注册入口可以跳过业务密钥鉴权，但它另需管理员配置的注册令牌。默认未配置则返回503关闭注册。已配置时，注册请求需携带`X-Registration-Token`；注册成功仅在该响应返回新key。列表与撤销需Bearer鉴权，并按认证所得userId查询，跨用户撤销返回404。列表实体用`@JsonIgnore`隐藏key，`toString()`也排除它。

注册令牌是管理员的开户能力，不能发给普通用户。数据库目前仍保存可用于鉴权的原始API Key；哈希存储和轮换机制尚未实现，不能把本次修复称为完整凭证安全方案。

### 3.2 限流：承认固定窗口，并修复原子性

当前实现为“首次请求开始60秒”的固定窗口，不是滑动窗口。Redis Lua脚本把INCR和首次EXPIRE放在一次原子执行中，避免计数成功但进程在设置TTL之前崩溃，留下永不过期计数。真实Redis验证前两次放行、第三次429，且TTL在1到60秒内。

### 3.3 SSE：从模拟打字改为SDK增量转发

SSE是服务端向客户端持续发送事件的HTTP响应。旧实现先同步生成完整答案，再每两个字符sleep15ms，视觉上像流式，模型生成期间仍然拿不到答案。

现在Cloud路径使用SDK的`StreamingChatLanguageModel.generate(..., handler)`。ReAct控制文本先缓冲；当`Final Answer:`标记出现，后续答案片段立即发出。标记可能跨多个回调，因此累积解析器要保留之前的片段。工具决策仍需完整解析后执行，不把Action文本直接当答案发送。没有最终标记的普通回复在完成后整体返回；不支持流式的provider走明确的阻塞回退，去掉人为打字延迟。

流式读取有超时和失败传播，超时后忽略晚到回调；输出长度受限，失败不发成功DONE。SSE使用独立有界线程池，避免占用公共ForkJoin池；会话记忆写入成功后再发DONE。`totalTokens`是字符估计，不能当提供商账单。

SDK当前接口没有暴露取消句柄，忽略晚到回调不等于远端推理必然停止。断开连接和超时也不构成强制终止所有底层I/O的保证。

### 3.4 会话隔离：同名sessionId也不能串用户

旧记忆键只依赖客户端传来的sessionId。现在同步和SSE入口使用鉴权userId与sessionId生成带长度边界的SHA-256存储键；客户端JSON不能覆盖userId。两个用户使用相同sessionId，各自仅有自己的两条消息。旧未分用户的Redis记忆不自动迁移。

### 3.5 知识库：持久化、事务、删除要一致

旧BM25（关键词排序算法）仅在进程内追加文本，应用重启就丢失；删除只删文档目录，向量依然能检索。

现在先生成全部嵌入，再通过Spring事务连接写入文档元数据和向量行。向量metadata带documentId，删除通过同一事务删除目录及关联向量。BM25每次从持久化向量表读取文本快照，按请求构建关键词排序，因此重启后仍可降级检索，删除后不会继续检索到旧文本。中文大小按UTF-8实际字节计算；搜索参数topK在1到50之间，且真正传入两路召回。

测试在第二个向量写入时注入异常，验证第一个向量和元数据都回滚。原库旧向量没有documentId，不会擅自猜测归属或删除；本次删除保证针对新摄入并关联documentId的数据。

这是小型简历项目的实现，每次BM25检索读取并计算全部文本，规模增大时需专门持久化索引、增量更新及压测。知识库当前为已鉴权用户共享，不是多租户私有文档库。

### 3.6 Worker期限与工具边界

Supervisor按规则将同一问题路由给匹配Worker，没有LLM自主拆题。线程池饱和时明确拒绝，不再用CallerRuns把工作转到请求线程；提交排队和等待共用Worker截止时间。改用ExecutorService.submit返回的Future发出中断请求，补充超时及饱和测试。对于响应中断的Worker可停止；不响应中断的I/O不能保证强制取消。最终LLM汇总仍受模型请求超时管理，不属于Worker的全局期限。

DataAnalyst只处理用户已有SELECT和算式，没有实现自然语言转SQL。SQL工具默认关闭，开启前必须另行配置只读、受限数据源；本轮没有执行真实业务SQL。文件工具默认沙箱缩小为项目下`tool-files`。DatabaseTools和LogSearchTools仍是示例桩，不能宣称真实数据库/日志平台已接通。

同步ReAct还补齐了迭代次数：返回实际使用轮数，并把工具决策连同Observation保留到下一轮上下文。

## 四、复现方式

先确认既有PostgreSQL与Redis运行，验收库存在且启用vector，8085和18085空闲。不需要重建原数据卷。

```powershell
cd F:/work/ai-agent-template
$env:JAVA_HOME = 'E:/jdk-21.0.7+6'
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
# 第一次启动前只打包；集成测试依赖随后启动的本地协议桩。
& 'F:/IDEA/IntelliJ IDEA 2026.1.2/plugins/maven/lib/maven3/bin/mvn.cmd' -o -s F:/work/.workspace-audit/maven-settings.xml '-Dmaven.repo.local=F:/IDEA/Maven/repository' -DskipTests package
./scripts/start-validation.ps1
# 应用health为UP后验证。
& E:/pp/python.exe scripts/verify_validation.py
./scripts/restart-validation-app.ps1
# 重启health为UP后验证降级检索和删除。
& E:/pp/python.exe scripts/verify_validation.py --after-restart
# 协议桩运行时执行完整测试和打包。
& 'F:/IDEA/IntelliJ IDEA 2026.1.2/plugins/maven/lib/maven3/bin/mvn.cmd' -o -s F:/work/.workspace-audit/maven-settings.xml '-Dmaven.repo.local=F:/IDEA/Maven/repository' package
```

启动脚本不会覆盖已占用端口。重启脚本会核对保存PID对应的JAR路径和验收配置，只重启本次应用。运行的是`.local-runtime/app.jar`副本，避免Windows锁住target下的JAR而阻止重新打包。脚本路径针对本机环境，换机器需调整JDK/Python/Maven与数据库配置。

## 五、效果验证与证据

- Maven：30项测试，0失败、0错误；包括原6项组件测试、4项Spring上下文/护栏、8项实际数据库/Redis接口集成、6项流式解析/异常/超时、2项Worker期限/饱和、4项执行器回归。
- 实际HTTP：27项检查，覆盖鉴权/所有者/密钥隐藏、非法参数、限流、知识摄入/检索/降级/重启/删除、同步ReAct工具执行、SSE及Supervisor算式Worker。
- 模型桩刻意延迟时，首个答案片段早于DONE，证明增量在完成前送达。具体本轮时序以JSON为准，不能作为真实模型TTFT指标。
- 验收删除后，验收库文档元数据和向量都为0；测试API Key仍留在独立验收库，不影响原库4个Key。
- 原始日志：`F:/work/.workspace-audit/agentforge-final-tests.log`；实际HTTP结果归档：`F:/work/.workspace-audit/agentforge-http-verification-result.json`。
- 运行日志与仅本地测试凭证：`F:/work/ai-agent-template/.local-runtime/`，Git忽略，不上传。

## 六、尚未覆盖

真实模型和云嵌入效果、生产网络、浏览器前端（本项目没有前端）、并发负载、检索准确率、成本节省、完整安全审计、监控栈和Kubernetes部署均未验收。性能话术中的TTFT降低80%和Top-1 96.5%没有可追溯原始数据，暂不使用。

## 七、面试怎么讲

“我用Java实现ReAct工具循环与pgvector、BM25、RRF混合检索，支持SSE多事件及模型答案增量转发。调试中修复了API Key管理越权、同名会话串用户、文档删除后仍可检索和内存关键词索引重启丢失的问题；补了真实数据库/Redis的集成验证，以及流式失败、事务回滚和Worker期限测试。模型协议由本地桩复现，真实模型质量与性能还需另外评测。”

## 八、关联笔记

- [[AgentForge面试话术]]
- [[AgentForge补充知识点]]
- [[简历求职工作空间接管与改进计划]]

## 九、代码变更

- 修改：auth包、GlobalExceptionHandler、application.yml、docker-compose.yml — 注册令牌、所有者隔离、密钥隐藏、原子固定窗口与Spring Boot 3 Redis配置。
- 新增：SessionScope；修改AgentRequest、AgentRuntime、AIController、AIStreamController、RedisMemoryStore — 同步与流式会话隔离。
- 新增：FinalAnswerAccumulator、StreamingResponseReader、StreamingExecutorConfig；修改StreamingReActExecutor、ModelRouter — SDK流式回调、期限、异常、长度与有界执行。
- 修改：ChunkingService、VectorSearchService、HybridRetrievalService、RetrievalService、KnowledgeController、AIConfiguration — 事务/删除/重启/字节/topK。
- 修改：SupervisorAgent、AgentExecutorConfig、ReActLoopExecutor、SqlTool、FileReadTool与DataAnalyst说明 — 截止时间、拒绝策略、迭代数和能力边界。
- 新增：4份回归/集成测试类与测试配置、4份PowerShell/Python协议桩/启动/重启/验收脚本；更新README和.gitignore。
