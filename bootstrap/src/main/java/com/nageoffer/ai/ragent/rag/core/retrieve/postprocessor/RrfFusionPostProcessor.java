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

import cn.hutool.core.collection.CollUtil;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchChannelResult;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RRF 融合后置处理器
 * <p>
 * 使用各检索通道内的排序位置融合候选，避免直接比较向量分数和 BM25 分数。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RrfFusionPostProcessor implements SearchResultPostProcessor {

    private static final int LOG_TOP_N = 5;

    private final SearchChannelProperties properties;

    @Override
    public String getName() {
        return "RRF";
    }

    @Override
    public int getOrder() {
        return 5;
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        return properties.getRrf().isEnabled();
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                        List<SearchChannelResult> results,
                                        SearchContext context) {
        if (CollUtil.isEmpty(results)) {
            return chunks;
        }

        Map<String, Candidate> candidates = new LinkedHashMap<>();
        int sequence = 0;
        int k = Math.max(1, properties.getRrf().getK());

        for (SearchChannelResult result : results) {
            if (result == null || CollUtil.isEmpty(result.getChunks())) {
                continue;
            }

            List<RetrievedChunk> channelChunks = result.getChunks();
            for (int i = 0; i < channelChunks.size(); i++) {
                RetrievedChunk chunk = channelChunks.get(i);
                if (chunk == null) {
                    continue;
                }

                String key = generateChunkKey(chunk);
                Candidate candidate = candidates.get(key);
                if (candidate == null) {
                    candidate = new Candidate(chunk, sequence);
                    candidates.put(key, candidate);
                }
                candidate.addContribution(resolveChannelName(result), i + 1, 1.0F / (k + i + 1));
                candidate.keepBetterRepresentative(chunk);
                sequence++;
            }
        }

        int maxCandidates = properties.getRrf().getMaxCandidates();
        List<Candidate> sortedCandidates = candidates.values().stream()
                .sorted(Comparator
                        .comparing(Candidate::getFusedScore, Comparator.reverseOrder())
                        .thenComparingInt(Candidate::getFirstSeen))
                .limit(maxCandidates > 0 ? maxCandidates : Long.MAX_VALUE)
                .toList();
        logTopCandidates(sortedCandidates);

        return sortedCandidates.stream()
                .map(Candidate::toChunk)
                .toList();
    }

    private String resolveChannelName(SearchChannelResult result) {
        if (result.getChannelName() != null && !result.getChannelName().isBlank()) {
            return result.getChannelName();
        }
        return result.getChannelType() == null ? "UNKNOWN" : result.getChannelType().name();
    }

    private void logTopCandidates(List<Candidate> candidates) {
        if (CollUtil.isEmpty(candidates)) {
            return;
        }

        int limit = Math.min(LOG_TOP_N, candidates.size());
        List<String> summaries = candidates.stream()
                .limit(limit)
                .map(Candidate::toLogSummary)
                .toList();
        log.info("RRF 融合 Top{} 候选：{}", limit, summaries);
    }

    private String generateChunkKey(RetrievedChunk chunk) {
        return chunk.getId() != null
                ? chunk.getId()
                : String.valueOf(chunk.getText() == null ? 0 : chunk.getText().hashCode());
    }

    private static class Candidate {

        private RetrievedChunk representative;
        private float fusedScore;
        private final int firstSeen;
        private final Map<String, Integer> channelRanks = new LinkedHashMap<>();

        private Candidate(RetrievedChunk representative, int firstSeen) {
            this.representative = representative;
            this.firstSeen = firstSeen;
        }

        private void addContribution(String channelName, int rank, float score) {
            fusedScore += score;
            channelRanks.putIfAbsent(channelName, rank);
        }

        private void keepBetterRepresentative(RetrievedChunk chunk) {
            Float currentScore = representative.getScore();
            Float nextScore = chunk.getScore();
            if (nextScore != null && (currentScore == null || nextScore > currentScore)) {
                representative = chunk;
            }
        }

        private Float getFusedScore() {
            return fusedScore;
        }

        private int getFirstSeen() {
            return firstSeen;
        }

        private RetrievedChunk toChunk() {
            return RetrievedChunk.builder()
                    .id(representative.getId())
                    .text(representative.getText())
                    .score(fusedScore)
                    .metadata(new HashMap<>(representative.getMetadata()))
                    .build();
        }

        private String toLogSummary() {
            return String.format(
                    Locale.ROOT,
                    "{id=%s, score=%.6f, sources=%s}",
                    representative.getId(),
                    fusedScore,
                    channelRanks
            );
        }
    }
}
