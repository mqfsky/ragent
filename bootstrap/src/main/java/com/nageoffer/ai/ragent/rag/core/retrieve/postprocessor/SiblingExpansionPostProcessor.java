/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.rag.core.retrieve.postprocessor;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.core.retrieve.SiblingChunkLookupService;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchChannelResult;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class SiblingExpansionPostProcessor implements SearchResultPostProcessor {

    private final SiblingChunkLookupService lookupService;

    @Override
    public String getName() {
        return "SiblingExpansion";
    }

    @Override
    public int getOrder() {
        return 8;
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        return true;
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                        List<SearchChannelResult> results,
                                        SearchContext context) {
        if (chunks == null || chunks.isEmpty()) {
            return chunks == null ? List.of() : chunks;
        }

        List<RetrievedChunk> passthrough = new ArrayList<>();
        Map<GroupKey, List<HitRange>> groupedRanges = new LinkedHashMap<>();
        for (RetrievedChunk chunk : chunks) {
            HitRange range = toHitRange(chunk);
            if (range == null) {
                passthrough.add(chunk);
                continue;
            }
            groupedRanges.computeIfAbsent(new GroupKey(range.collectionName, range.parentId), ignored -> new ArrayList<>())
                    .add(range);
        }

        if (groupedRanges.isEmpty()) {
            return chunks;
        }

        List<RetrievedChunk> expanded = new ArrayList<>();
        for (Map.Entry<GroupKey, List<HitRange>> entry : groupedRanges.entrySet()) {
            List<HitRange> mergedRanges = mergeRanges(entry.getValue());
            for (HitRange range : mergedRanges) {
                List<RetrievedChunk> siblings = lookupService.findSiblings(
                        entry.getKey().collectionName,
                        entry.getKey().parentId,
                        range.start,
                        range.end);
                if (siblings == null || siblings.isEmpty()) {
                    expanded.addAll(range.hits);
                    continue;
                }
                expanded.add(toExpandedChunk(entry.getKey(), range, siblings));
            }
        }
        expanded.addAll(passthrough);
        return expanded;
    }

    private HitRange toHitRange(RetrievedChunk chunk) {
        if (chunk == null || chunk.getMetadata() == null || chunk.getMetadata().isEmpty()) {
            return null;
        }
        Map<String, Object> metadata = chunk.getMetadata();
        String parentId = Objects.toString(metadata.get("parentId"), "");
        String collectionName = Objects.toString(metadata.get("collection_name"), "");
        int childIndex = asInt(metadata.get("childIndex"), -1);
        int parentChildCount = asInt(metadata.get("parentChildCount"), -1);
        int siblingWindow = Math.max(0, asInt(metadata.get("siblingWindow"), 1));
        if (!StringUtils.hasText(parentId)
                || !StringUtils.hasText(collectionName)
                || childIndex < 0
                || parentChildCount <= 0) {
            return null;
        }
        int start = Math.max(0, childIndex - siblingWindow);
        int end = Math.min(parentChildCount - 1, childIndex + siblingWindow);
        return new HitRange(collectionName, parentId, start, end, new ArrayList<>(List.of(chunk)));
    }

    private List<HitRange> mergeRanges(List<HitRange> ranges) {
        if (ranges == null || ranges.isEmpty()) {
            return List.of();
        }
        List<HitRange> sorted = ranges.stream()
                .sorted(Comparator.comparingInt(HitRange::start).thenComparingInt(HitRange::end))
                .toList();
        List<HitRange> merged = new ArrayList<>();
        for (HitRange next : sorted) {
            if (merged.isEmpty()) {
                merged.add(next.copy());
                continue;
            }
            HitRange current = merged.get(merged.size() - 1);
            if (next.start <= current.end) {
                current.end = Math.max(current.end, next.end);
                current.hits.addAll(next.hits);
            } else {
                merged.add(next.copy());
            }
        }
        return merged;
    }

    private RetrievedChunk toExpandedChunk(GroupKey key, HitRange range, List<RetrievedChunk> siblings) {
        List<RetrievedChunk> orderedSiblings = siblings.stream()
                .sorted(Comparator.comparingInt(chunk -> asInt(chunk.getMetadata().get("childIndex"), 0)))
                .toList();
        String text = orderedSiblings.stream()
                .map(RetrievedChunk::getText)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.joining("\n\n"));
        Float score = range.hits.stream()
                .map(RetrievedChunk::getScore)
                .filter(Objects::nonNull)
                .max(Float::compareTo)
                .orElse(null);
        List<String> sourceHitChunkIds = range.hits.stream()
                .map(RetrievedChunk::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<String> sourceChildChunkIds = orderedSiblings.stream()
                .map(RetrievedChunk::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("chunkType", "expanded");
        metadata.put("expanded", true);
        metadata.put("collection_name", key.collectionName);
        metadata.put("parentId", key.parentId);
        metadata.put("headingPath", resolveHeadingPath(range, orderedSiblings));
        metadata.put("childIndexRange", List.of(range.start, range.end));
        metadata.put("sourceHitChunkIds", sourceHitChunkIds);
        metadata.put("sourceChildChunkIds", sourceChildChunkIds);

        return RetrievedChunk.builder()
                .id("expanded:" + key.parentId + ":" + range.start + "-" + range.end)
                .text(text)
                .score(score)
                .metadata(metadata)
                .build();
    }

    private String resolveHeadingPath(HitRange range, List<RetrievedChunk> siblings) {
        for (RetrievedChunk hit : range.hits) {
            Object headingPath = hit.getMetadata().get("headingPath");
            if (headingPath != null && StringUtils.hasText(headingPath.toString())) {
                return headingPath.toString();
            }
        }
        for (RetrievedChunk sibling : siblings) {
            Object headingPath = sibling.getMetadata().get("headingPath");
            if (headingPath != null && StringUtils.hasText(headingPath.toString())) {
                return headingPath.toString();
            }
        }
        return "";
    }

    private int asInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private record GroupKey(String collectionName, String parentId) {
    }

    private static class HitRange {

        private final String collectionName;
        private final String parentId;
        private int start;
        private int end;
        private final List<RetrievedChunk> hits;

        private HitRange(String collectionName, String parentId, int start, int end, List<RetrievedChunk> hits) {
            this.collectionName = collectionName;
            this.parentId = parentId;
            this.start = start;
            this.end = end;
            this.hits = hits;
        }

        private int start() {
            return start;
        }

        private int end() {
            return end;
        }

        private HitRange copy() {
            return new HitRange(collectionName, parentId, start, end, new ArrayList<>(hits));
        }
    }
}
