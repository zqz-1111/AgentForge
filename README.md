# LangChain4j + Spring Boot AI Agent Template (Production Ready)

## 🚀 Overview
A reference architecture for building production-grade AI agents using **LangChain4j** and **Spring Boot 3.2**.
This template encapsulates the full agent stack: reasoning loop, tool calling, RAG retrieval, conversation memory, guardrails, observability, and model routing — all wired together and ready to extend.

## 🛠 Tech Stack
- **Framework:** Spring Boot 3.2, Java 21
- **AI SDK:** LangChain4j 0.31
- **LLM:** OpenAI (gpt-4o / gpt-3.5-turbo via ModelRouter)
- **Embeddings:** all-MiniLM-L6-v2 (local, no API cost)
- **Vector Store:** pgvector (PostgreSQL extension)
- **Memory:** Redis (TTL-based conversation history)
- **Observability:** Micrometer, Prometheus, Grafana, Zipkin
- **Build:** Maven + Docker

## ⚡ Key Features & Design Decisions

### 1. ReAct Loop (Reasoning + Acting)
The agent uses the **ReAct pattern**: it reasons about what tool to call, calls it, observes the result, and repeats until it has a final answer.

```
Thought: I need to check the payment status
Action: getPaymentStatus
Action Input: TXN1001
Observation: FAILED - Insufficient funds
Thought: I now have enough information
Final Answer: Payment TXN1001 failed due to insufficient funds.
```

**Max iterations:** 5 (configurable via `ai.agent.max-iterations`). Prevents infinite loops.

### 2. Model Routing
Queries are routed to different models based on complexity to **optimize cost vs. quality**:

| Query Type | Model | Trigger |
|---|---|---|
| Simple / short | gpt-3.5-turbo | length < 100 chars, no reasoning keywords |
| Complex / analytical | gpt-4o | length > 100 chars or keywords: why, analyze, explain, debug |

**Why:** gpt-3.5-turbo is ~10x cheaper. Routing saves cost without sacrificing quality on complex tasks.

### 3. RAG (Retrieval-Augmented Generation)
Documents are chunked, embedded, and stored in **pgvector**. On each query, the top-K most relevant chunks are retrieved and injected into the agent's context before the ReAct loop starts.

```
POST /ai/ingest  → ChunkingService → EmbeddingService → pgvector
POST /ai/query   → RetrievalService → top-5 chunks injected into context
```

**Chunk config:** 500 chars, 50 char overlap (configurable).

### 4. Conversation Memory
Stored in **Redis** with:
- TTL: 60 minutes (configurable)
- Sliding window: last 20 messages (prevents unbounded growth)
- Key format: `conversation:{sessionId}`

Pass `sessionId` in the request body to maintain context across turns. Omit it for a fresh session.

### 5. Guardrails
Three layers of protection:

| Layer | What it checks |
|---|---|
| `InputFilter` | Length limit (2000 chars), prompt injection patterns |
| `PromptInjectionDetector` | "ignore instructions", "jailbreak", "reveal prompt", etc. |
| `ResponseValidator` | Empty response, length limit (5000 chars) |

Violations return HTTP 400 with a safe error message. No internal details are leaked.

### 6. Observability
Full distributed tracing via Micrometer + Zipkin. Trace ID flows through all log lines.

**Metrics exposed at `/actuator/prometheus`:**
- `ai.agent.llm.calls` — total LLM invocations
- `ai.agent.tokens.estimated` — estimated token consumption
- Standard Spring Boot metrics (JVM, HTTP, etc.)

**Trace flow:**
```
[Client] → [AIController] → [AgentOrchestrator] → [AgentRuntime] → [LLMClient]
  trace-id: abc123 flows through all logs
```

## 📁 Project Structure
```
src/main/java/com/aiagent
├── controller
│   └── AIController.java              ← REST endpoints (/ai/query, /ai/ingest)
├── agent
│   ├── runtime
│   │   ├── AgentRuntime.java          ← Wires planner + guardrails + ReAct loop
│   │   ├── ReActLoopExecutor.java     ← Core reasoning loop
│   │   └── AgentPlanner.java          ← Builds context from memory + RAG
│   ├── orchestrator
│   │   └── AgentOrchestrator.java     ← Routes to correct agent type
│   └── config
│       └── AgentConfig.java           ← Agent-level config properties
├── tools
│   ├── ToolExecutor.java              ← Dispatches tool calls by name
│   ├── payment/PaymentTools.java      ← Payment status lookup
│   ├── logs/LogSearchTools.java       ← Log search
│   └── database/DatabaseTools.java    ← Read-only DB queries
├── rag
│   ├── ChunkingService.java           ← Document ingestion + chunking
│   ├── EmbeddingService.java          ← Text → vector
│   ├── RetrievalService.java          ← Query → relevant chunks
│   └── VectorSearchService.java       ← pgvector operations
├── memory
│   ├── ConversationMemory.java        ← Facade for session history
│   └── RedisMemoryStore.java          ← Redis list operations
├── llm
│   ├── LLMClient.java                 ← LLM call entry point
│   ├── ModelRouter.java               ← Cost-aware model selection
│   └── PromptTemplates.java           ← ReAct system prompt
├── observability
│   ├── AgentTraceLogger.java          ← Step-level trace logging
│   ├── PromptLogger.java              ← Prompt audit logging
│   └── TokenMetrics.java             ← Micrometer counters
├── guardrails
│   ├── InputFilter.java               ← Input validation
│   ├── ResponseValidator.java         ← Output validation
│   └── PromptInjectionDetector.java   ← Injection pattern detection
├── config
│   ├── AIConfiguration.java           ← LangChain4j beans
│   └── RedisConfig.java               ← Redis template
├── dto
│   ├── AgentRequest.java
│   └── AgentResponse.java
├── exception
│   ├── GuardrailException.java
│   └── GlobalExceptionHandler.java
└── AiAgentApplication.java
```

## 🏃 How to Run

### Prerequisites
- Java 21
- Docker + Docker Compose
- OpenAI API key

### Local Development
```bash
# Set your API key
export OPENAI_API_KEY=sk-...

# Start all infrastructure (PostgreSQL+pgvector, Redis, Prometheus, Grafana, Zipkin)
docker-compose up -d

# Build and run the app
mvn clean package -DskipTests
java -jar target/ai-agent-template-1.0.0.jar

# Access
# App:        http://localhost:8085
# Prometheus: http://localhost:9090
# Grafana:    http://localhost:3000  (admin/admin)
# Zipkin:     http://localhost:9411
```

### Kubernetes Deployment
```bash
# Fill in secrets in k8s/deployment.yaml, then:
kubectl apply -f k8s/deployment.yaml

# Check status
kubectl get pods -l app=ai-agent-template
```

## 🔌 API Reference

### Query the Agent
```bash
POST /ai/query
Content-Type: application/json

{
  "query": "Why did payment TXN1001 fail?",
  "sessionId": "user-session-abc"   # optional, omit for new session
}
```

Response:
```json
{
  "answer": "Payment TXN1001 failed due to insufficient funds.",
  "sessionId": "user-session-abc",
  "iterationsUsed": 2,
  "latencyMs": 1240
}
```

### Ingest a Document (RAG)
```bash
POST /ai/ingest
Content-Type: text/plain

Your document text here. The system will chunk, embed, and store it in pgvector.
```

## 📊 End-to-End Request Flow

```
POST /ai/query
     │
     ▼
AIController → validates request (Jakarta Validation)
     │
     ▼
AgentOrchestrator → logs entry, measures latency
     │
     ▼
AgentRuntime
  ├── InputFilter.validate()         ← guardrail: length + injection check
  ├── AgentPlanner.buildContext()    ← RAG retrieval + memory history
  ├── ReActLoopExecutor.execute()    ← ReAct loop (max 5 iterations)
  │     ├── LLMClient.generate()    ← ModelRouter selects gpt-3.5 or gpt-4o
  │     ├── ToolExecutor.execute()  ← calls PaymentTools / LogSearchTools / DatabaseTools
  │     └── TokenMetrics.record()   ← Micrometer counter
  ├── ResponseValidator.validate()   ← guardrail: output safety
  └── ConversationMemory.store()     ← persist to Redis
     │
     ▼
AgentResponse { answer, sessionId, iterationsUsed, latencyMs }
```

## 🔧 Adding a New Tool

1. Create `src/main/java/com/aiagent/tools/myfeature/MyTools.java`
2. Annotate methods with `@Tool("description")`
3. Register in `ToolExecutor.execute()` switch statement
4. Add the tool name to `PromptTemplates.REACT_SYSTEM_PROMPT`

## 🔧 Adding a New Agent Type

In `AgentOrchestrator.runAgent()`, inspect the query and route to a specialized runtime:
```java
if (query.contains("deploy") || query.contains("kubernetes")) {
    return devopsAgentRuntime.execute(request);
}
```

## 🛡️ Guardrail Behavior

| Violation | HTTP Status | Response |
|---|---|---|
| Empty input | 400 | "Input must not be empty" |
| Input too long | 400 | "Input exceeds maximum length of 2000" |
| Injection detected | 400 | "Input contains disallowed content" |
| Empty LLM response | 400 | "Agent returned an empty response" |
| Output too long | 400 | "Agent response exceeds maximum allowed length" |
| Unexpected error | 500 | "An unexpected error occurred" |

## 📊 Performance Characteristics

| Component | Latency | Notes |
|---|---|---|
| Embedding (local) | ~5ms | all-MiniLM-L6-v2 runs on CPU |
| pgvector search | ~10ms | top-5 from 100k vectors |
| Redis memory read | ~2ms | local network |
| LLM call (gpt-4o) | ~1-3s | network dependent |
| Full agent (1 tool) | ~2-4s | end-to-end |

## 🔗 Related Resume Claims

This repo backs up:
- ✅ "Built LangChain4j AI agent with ReAct reasoning loop and tool calling"
- ✅ "Implemented RAG pipeline with pgvector and local embedding model"
- ✅ "Designed cost-aware model routing (gpt-3.5 vs gpt-4o) reducing LLM costs"
- ✅ "Built multi-layer guardrails including prompt injection detection"
- ✅ "Established production-grade observability with Prometheus, Grafana, Zipkin"
- ✅ "Implemented Redis-backed conversation memory with TTL and sliding window"

## 📚 Further Reading
- [LangChain4j Docs](https://docs.langchain4j.dev/)
- [pgvector](https://github.com/pgvector/pgvector)
- [ReAct Paper](https://arxiv.org/abs/2210.03629)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
