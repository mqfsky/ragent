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
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchChannelResult;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchChannelType;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RrfFusionPostProcessorTest {

    @Test
    void shouldFuseRanksAcrossChannelsBeforeRerank() {
        SearchChannelProperties properties = new SearchChannelProperties();
        properties.getRrf().setK(60);
        properties.getRrf().setMaxCandidates(10);
        RrfFusionPostProcessor processor = new RrfFusionPostProcessor(properties);

        RetrievedChunk vectorTop = chunk("vector-top", "向量第一", 0.99F);
        RetrievedChunk sharedVector = chunk("shared", "共同命中", 0.80F);
        RetrievedChunk keywordTop = chunk("keyword-top", "关键词第一", 8.0F);
        RetrievedChunk sharedKeyword = chunk("shared", "共同命中", 3.0F);

        List<SearchChannelResult> results = List.of(
                result(SearchChannelType.VECTOR_GLOBAL, vectorTop, sharedVector),
                result(SearchChannelType.KEYWORD_ES, keywordTop, sharedKeyword)
        );

        List<RetrievedChunk> fused = processor.process(List.of(), results, SearchContext.builder().topK(3).build());

        assertEquals(List.of("shared", "vector-top", "keyword-top"), fused.stream().map(RetrievedChunk::getId).toList());
        assertTrue(fused.get(0).getScore() > fused.get(1).getScore());
        assertEquals((float) (1.0 / 62.0 + 1.0 / 62.0), fused.get(0).getScore(), 0.000001F);
    }

    @Test
    void shouldLimitFusedCandidatesBeforeRerank() {
        SearchChannelProperties properties = new SearchChannelProperties();
        properties.getRrf().setMaxCandidates(2);
        RrfFusionPostProcessor processor = new RrfFusionPostProcessor(properties);

        List<SearchChannelResult> results = List.of(result(
                SearchChannelType.KEYWORD_ES,
                chunk("a", "A", 3.0F),
                chunk("b", "B", 2.0F),
                chunk("c", "C", 1.0F)
        ));

        List<RetrievedChunk> fused = processor.process(List.of(), results, SearchContext.builder().topK(3).build());

        assertEquals(List.of("a", "b"), fused.stream().map(RetrievedChunk::getId).toList());
    }

    private SearchChannelResult result(SearchChannelType type, RetrievedChunk... chunks) {
        return SearchChannelResult.builder()
                .channelType(type)
                .channelName(type.name())
                .chunks(List.of(chunks))
                .build();
    }

    private RetrievedChunk chunk(String id, String text, Float score) {
        return RetrievedChunk.builder()
                .id(id)
                .text(text)
                .score(score)
                .build();
    }
}
