# Parent-Child Sibling Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two-level parent-child chunking and sibling-window expansion so ragent retrieves precise child chunks while reranking richer local context.

**Architecture:** Enhance `STRUCTURE_AWARE` chunking to emit child chunks with parent metadata. Preserve metadata through vector retrieval into `RetrievedChunk`, then add a post-processor between RRF and rerank that expands child hits by fetching neighboring children from the same parent.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Mockito, MyBatis/JdbcTemplate, Milvus SDK, PostgreSQL JSONB metadata.

---

### Task 1: Carry Metadata Through RetrievedChunk

**Files:**
- Modify: `framework/src/main/java/com/nageoffer/ai/ragent/framework/convention/RetrievedChunk.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/PgRetrieverService.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/MilvusRetrieverService.java`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/core/retrieve/PgRetrieverServiceTest.java`

- [ ] Add `Map<String, Object> metadata` to `RetrievedChunk` with `@Builder.Default`.
- [ ] Update PG retrieval SQL to select `metadata` and parse JSONB into the chunk.
- [ ] Update Milvus retrieval mapping to copy the returned `metadata` object into the chunk.
- [ ] Add a focused PG retriever test proving `metadata.parentId` survives retrieval.

### Task 2: Add Parent-Child Structure-Aware Options

**Files:**
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/core/chunk/ChunkingOptions.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/core/chunk/ParentChildOptions.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/core/chunk/ChunkingMode.java`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/core/chunk/ChunkingModeTest.java`

- [ ] Add a `ParentChildOptions` record with `parentTargetChars`, `parentMaxChars`, `childChunkSize`, `childOverlapSize`, and `siblingWindow`.
- [ ] Allow `ChunkingMode.STRUCTURE_AWARE` to parse parent-child config keys while keeping existing `targetChars/maxChars/minChars/overlapChars` backward compatible.
- [ ] Add tests proving old config still works and new config is surfaced in `getDefaultConfig()`.

### Task 3: Emit Child Chunks With Parent Metadata

**Files:**
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/core/chunk/strategy/StructureAwareTextChunker.java`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/core/chunk/strategy/StructureAwareTextChunkerTest.java`

- [ ] Write a failing test for a Markdown document with headings that produces multiple child chunks under one parent.
- [ ] Implement parent generation using existing block packing with parent-size options.
- [ ] Split each parent into child chunks, never crossing parent boundaries.
- [ ] Populate `chunkType=child`, `parentId`, `parentIndex`, `childIndex`, `parentChildCount`, `headingPath`, and `siblingWindow`.
- [ ] Keep existing single-level behavior for `TextBoundaryOptions`.

### Task 4: Add Sibling Fetch Interface

**Files:**
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/SiblingChunkLookupService.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/PgSiblingChunkLookupService.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/MilvusSiblingChunkLookupService.java`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/core/retrieve/PgSiblingChunkLookupServiceTest.java`

- [ ] Define lookup by `collectionName`, `parentId`, `startChildIndex`, and `endChildIndex`.
- [ ] Implement PG lookup over `t_knowledge_vector.metadata` JSONB.
- [ ] Implement Milvus lookup using metadata filter expressions where supported by the current SDK.
- [ ] Return chunks sorted by numeric `childIndex`.

### Task 5: Add SiblingExpansionPostProcessor

**Files:**
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/core/retrieve/postprocessor/SiblingExpansionPostProcessor.java`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/core/retrieve/postprocessor/SiblingExpansionPostProcessorTest.java`

- [ ] Write a failing test where hit child `2` with window `1` expands to children `1..3`.
- [ ] Merge overlapping sibling ranges for the same parent.
- [ ] Build deterministic ids like `expanded:{parentId}:{start}-{end}`.
- [ ] Preserve highest source score and add metadata `chunkType=expanded`, `expanded=true`, `sourceHitChunkIds`, and `sourceChildChunkIds`.
- [ ] Order the processor after RRF and before rerank.

### Task 6: Verify Integration

**Files:**
- Modify tests only unless integration reveals missing wiring.

- [ ] Run focused chunk tests.
- [ ] Run focused retrieval/post-processor tests.
- [ ] Run `./mvnw -pl bootstrap -DskipITs test` if feasible.
- [ ] Confirm old chunks without metadata pass through unchanged.
