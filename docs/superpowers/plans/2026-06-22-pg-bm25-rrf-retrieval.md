# PostgreSQL BM25 RRF Retrieval Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add PostgreSQL keyword retrieval and fuse it with vector retrieval using RRF before the existing rerank stage.

**Architecture:** Add a PostgreSQL keyword retriever that queries `t_knowledge_vector.content` with PostgreSQL full-text ranking, expose it through a new `KEYWORD_ES` search channel, and add an RRF post-processor that fuses original channel rank lists before rerank. Keep vector retrievers and rerank unchanged.

**Tech Stack:** Java 17, Spring Boot, JdbcTemplate, PostgreSQL full-text search, JUnit 5.

---

### Task 1: RRF Post-Processor

**Files:**
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/postprocessor/RrfFusionPostProcessor.java`
- Create: `bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/core/retrieve/postprocessor/RrfFusionPostProcessorTest.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/config/SearchChannelProperties.java`

- [ ] Write a failing unit test proving duplicated chunks from multiple channel result lists are fused by `1 / (k + rank)` and ordered by fused score.
- [ ] Implement `RrfFusionPostProcessor` with order after deduplication and before rerank.
- [ ] Add config fields `rag.search.rrf.enabled`, `rag.search.rrf.k`, and `rag.search.rrf.max-candidates`.
- [ ] Run the focused RRF test and confirm it passes.

### Task 2: PostgreSQL Keyword Retriever

**Files:**
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/KeywordRetrieverService.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/PgKeywordRetrieverService.java`
- Create: `bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/core/retrieve/PgKeywordRetrieverServiceTest.java`

- [ ] Write a failing unit test proving the retriever uses `websearch_to_tsquery`/`ts_rank_cd`, collection metadata filtering, and returns `RetrievedChunk`.
- [ ] Implement `PgKeywordRetrieverService` with `JdbcTemplate` and `@ConditionalOnProperty(name = "rag.vector.type", havingValue = "pg")`.
- [ ] Keep SQL parameterized for query text, collection name, and topK.
- [ ] Run the focused keyword retriever test and confirm it passes.

### Task 3: Keyword Search Channel

**Files:**
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/channel/KeywordSearchChannel.java`
- Create: `bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/core/retrieve/channel/KeywordSearchChannelTest.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/config/SearchChannelProperties.java`
- Modify: `bootstrap/src/main/resources/application.yaml`

- [ ] Write failing unit tests proving keyword retrieval is disabled by config and, when enabled, queries all active KB collections with the configured topK multiplier.
- [ ] Implement `KeywordSearchChannel` using `KnowledgeBaseMapper` and `KeywordRetrieverService`.
- [ ] Add `rag.search.channels.keyword` config fields.
- [ ] Run focused channel tests and confirm they pass.

### Task 4: PostgreSQL Schema Index

**Files:**
- Modify: `resources/database/schema_pg.sql`
- Create: `resources/database/upgrade_v1.2_to_v1.3CodeX.sql`

- [ ] Add a GIN full-text index for `t_knowledge_vector.content`.
- [ ] Add the same index to the upgrade script for existing databases.
- [ ] Verify the SQL is syntactically consistent with the existing PostgreSQL schema.

### Task 5: Verification

**Files:**
- Existing Maven module files only.

- [ ] Run focused Maven tests for the new classes.
- [ ] Run `./mvnw -pl bootstrap -DskipTests compile`.
- [ ] Review `git diff` to confirm no unrelated user changes were overwritten.
