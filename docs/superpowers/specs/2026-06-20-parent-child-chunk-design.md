# Parent-Child Chunk Design

## Background

ragent currently supports structure-aware chunking for Markdown-like text and fixed-size chunking. The structure-aware chunker preserves headings, paragraphs, code fences, image/link rows, and block boundaries, but each output chunk still has to serve two conflicting purposes:

- Retrieval unit: small chunks are easier for embedding search to match precisely.
- Generation context: larger contiguous context is often needed to answer policies, workflows, and long-document questions completely.

This design adds a lightweight parent-child chunk mechanism for `STRUCTURE_AWARE` documents. It follows the common hierarchical retrieval pattern used by systems such as LlamaIndex `HierarchicalNodeParser` / `AutoMergingRetriever` and sentence-window retrieval: retrieve fine-grained nodes, then reconstruct nearby context before generation.

## Goals

- Use child chunks as the vector retrieval unit.
- Preserve parent-level document structure through metadata rather than a new table.
- Expand retrieved child chunks with nearby sibling chunks before rerank and prompt construction.
- Keep the first version small enough for safe secondary development on the current ragent codebase.
- Maintain backward compatibility for existing chunks and the `FIXED_SIZE` strategy.

## Non-Goals

- Do not add a parent chunk table in the first version.
- Do not change `FIXED_SIZE` chunking behavior.
- Do not retrieve or inject entire parent chunks by default.
- Do not generate LLM summaries for parent chunks.
- Do not redesign the evaluation framework as part of this feature.

## Confirmed Decisions

- Scope: only enhance `STRUCTURE_AWARE`.
- Storage model: no new parent chunk table; parent-child relation is stored in child metadata.
- Context expansion: retrieve neighboring child chunks with the same `parentId`.
- Default expansion window: previous 1 child + hit child + next 1 child.
- Expansion output: merge the hit child and its sibling window into one `expanded` chunk before rerank.
- Processor location: add a search post-processor after deduplication and before rerank.

## Configuration

Add parent-child options to the structure-aware chunk configuration. Defaults should work without user tuning:

```json
{
  "parentTargetChars": 1800,
  "parentMaxChars": 2600,
  "childChunkSize": 500,
  "childOverlapSize": 80,
  "siblingWindow": 1
}
```

`parentTargetChars` controls the preferred size of a parent chunk. `parentMaxChars` is the hard upper bound before further structural splitting. `childChunkSize` and `childOverlapSize` control the child chunks generated inside each parent. `siblingWindow` controls how many neighboring child chunks are added on each side during retrieval expansion.

## Chunking Design

### Parent Chunk Generation

Parent chunks represent coherent document sections. The splitter should prefer boundaries in this order:

1. Markdown heading hierarchy.
2. Lower-level subsection headings.
3. Paragraph and list boundaries.
4. Code/table/atomic blocks as indivisible units where possible.

Short adjacent sections may be merged until they approach `parentTargetChars`. Sections exceeding `parentMaxChars` should be split further using lower-level headings or paragraph/list boundaries. A parent chunk must not rewrite source text.

Each parent chunk gets:

- `parentId`: stable within one ingestion result.
- `parentIndex`: zero-based parent order in the document.
- `headingPath`: a readable section path such as `请假制度 > 病假`.

### Child Chunk Generation

Child chunks are generated inside one parent chunk only. A child chunk must not cross parent boundaries.

Child splitting uses `childChunkSize` and `childOverlapSize`, but should still prefer sentence, paragraph, list item, code block, and table boundaries over hard character cuts. This keeps retrieval units compact without cutting through obvious semantic boundaries.

Only child chunks are embedded and written to the vector store.

### Child Metadata

Each child chunk stores lightweight parent-child metadata:

```json
{
  "chunkType": "child",
  "parentId": "doc123-parent-0",
  "parentIndex": 0,
  "childIndex": 2,
  "parentChildCount": 5,
  "headingPath": "请假制度 > 病假",
  "siblingWindow": 1
}
```

The metadata should also preserve existing document and ingestion metadata such as `doc_id`, `collection_name`, `chunk_index`, source fields, and pipeline/task fields.

### Embedding Text

The stored display content remains the original child text. The embedding input may prepend `headingPath` to improve semantic recall:

```text
标题路径：请假制度 > 病假
正文：病假工资按……
```

This requires separating embedding text from display text. If that separation is not implemented in the first code pass, the first version can keep embedding over `content` and still store `headingPath` for retrieval expansion and later tuning.

## Retrieval Expansion Design

Add a new `SiblingExpansionPostProcessor` to the existing search post-processor chain:

```text
Search channels
-> DeduplicationPostProcessor
-> SiblingExpansionPostProcessor
-> RerankPostProcessor
```

### Expansion Algorithm

For each retrieved child chunk:

1. Read `parentId`, `childIndex`, and `siblingWindow` from metadata.
2. If metadata is missing or malformed, keep the original chunk unchanged.
3. Compute the range:

```text
start = max(0, childIndex - siblingWindow)
end = min(parentChildCount - 1, childIndex + siblingWindow)
```

4. Query chunks with the same `parentId` and `childIndex` in `[start, end]`.
5. Sort by `childIndex`.
6. Merge the sibling window into one expanded chunk.

### Expanded Chunk

The expanded chunk is the unit passed to rerank and prompt formatting. Its text is the ordered concatenation of the sibling window. Its score inherits the highest score among source hits inside the range.

Metadata shape:

```json
{
  "chunkType": "expanded",
  "expanded": true,
  "parentId": "doc123-parent-0",
  "headingPath": "请假制度 > 病假",
  "childIndexRange": [1, 3],
  "sourceHitChunkIds": ["child-2"],
  "sourceChildChunkIds": ["child-1", "child-2", "child-3"]
}
```

The expanded chunk id should be deterministic for a document ingestion version and range, for example:

```text
expanded:{parentId}:{start}-{end}
```

### Overlap Handling

If multiple hit children produce overlapping ranges under the same `parentId`, merge those ranges before creating expanded chunks.

Example:

```text
hit childIndex=2, window=1 -> [1, 3]
hit childIndex=3, window=1 -> [2, 4]
merged range -> [1, 4]
```

This avoids repeated context in rerank and prompt input.

## Data Model Requirements

`RetrievedChunk` should be extended to carry metadata:

```java
private Map<String, Object> metadata;
```

Both PostgreSQL and Milvus retrievers should return vector-store metadata into `RetrievedChunk`. Without metadata, `SiblingExpansionPostProcessor` cannot recover `parentId` and `childIndex`.

The vector store write path must preserve chunk metadata. The current ingestion/indexing path should be checked so metadata built during chunking and enrichment reaches `t_knowledge_vector.metadata`.

## Query Requirements

`SiblingExpansionPostProcessor` needs a way to fetch child chunks by:

- `collection_name`
- `parentId`
- `childIndex` range

For PostgreSQL, this can be implemented with JSONB metadata filters over `t_knowledge_vector`. For Milvus, use metadata filter expressions where supported.

The expansion query returns display content, id, score if available, and metadata. Expansion results do not need fresh vector similarity scores because they are context neighbors, not direct semantic hits.

## Compatibility

- Existing chunks without parent-child metadata are returned unchanged.
- Existing `FIXED_SIZE` chunking remains unchanged.
- If expansion query fails, log the failure and return the original retrieved chunks.
- If rerank is disabled, expanded chunks still provide richer context to prompt formatting.
- Existing prompt templates can continue using `RetrievedChunk.text`.

## Error Handling

- Missing `parentId`, `childIndex`, or `parentChildCount`: skip expansion for that chunk.
- Invalid numeric metadata: skip expansion for that chunk.
- Expansion query timeout/error: keep original chunks and continue the processor chain.
- Empty sibling query result: keep original chunk.
- Oversized expanded text: cap by configured maximum text length or shrink range toward the hit child.

## Testing Plan

Unit tests:

- Structure-aware parent generation from headings.
- Child chunks never cross parent boundaries.
- Child metadata contains `parentId`, `parentIndex`, `childIndex`, `parentChildCount`, and `headingPath`.
- Missing metadata falls back to original chunk behavior.
- Sibling expansion creates deterministic expanded ids.
- Overlapping sibling ranges are merged.
- Expanded score inherits the max source hit score.

Integration tests:

- Ingest a Markdown document and verify vector metadata is persisted.
- Retrieve a query matching one child and verify expanded text includes previous/hit/next children.
- Verify rerank receives expanded chunks rather than separate sibling chunks.
- Verify old chunks without metadata still retrieve normally.

## Future Work

- Add parent chunk as a first-class data model if source visualization, parent editing, or section-level citations become requirements.
- Add dynamic expansion policies for workflow/policy questions.
- Add optional `parentSummary` generation for long sections.
- Add query-aware choice between child-only, sibling-expanded, and full-parent context.
- Add citation display using `headingPath`, document name, page number, and child ranges.

## References

- LlamaIndex `HierarchicalNodeParser` and `AutoMergingRetriever`: https://developers.llamaindex.ai/python/framework/module_guides/loading/node_parsers/modules/
- LlamaIndex `SentenceWindowNodeParser`: https://developers.llamaindex.ai/python/framework/module_guides/loading/node_parsers/modules/
- H-RAG at SemEval-2026 Task 8: https://arxiv.org/abs/2605.00631
- RAPTOR: https://arxiv.org/abs/2401.18059
- Late Chunking: https://arxiv.org/abs/2409.04701
