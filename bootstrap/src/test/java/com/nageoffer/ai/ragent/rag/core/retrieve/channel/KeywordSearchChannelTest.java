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

package com.nageoffer.ai.ragent.rag.core.retrieve.channel;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import com.nageoffer.ai.ragent.rag.core.retrieve.KeywordRetrieverService;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieveRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeywordSearchChannelTest {

    @Test
    void shouldBeDisabledByKeywordConfig() {
        SearchChannelProperties properties = new SearchChannelProperties();
        properties.getChannels().getKeyword().setEnabled(false);

        KeywordSearchChannel channel = new KeywordSearchChannel(
                Optional.of(mock(KeywordRetrieverService.class)),
                properties,
                mock(KnowledgeBaseMapper.class)
        );

        assertFalse(channel.isEnabled(SearchContext.builder().build()));
    }

    @Test
    void shouldBeDisabledWhenKeywordRetrieverIsUnavailable() {
        SearchChannelProperties properties = new SearchChannelProperties();
        KeywordSearchChannel channel = new KeywordSearchChannel(
                Optional.empty(),
                properties,
                mock(KnowledgeBaseMapper.class)
        );

        assertFalse(channel.isEnabled(SearchContext.builder().rewrittenQuestion("员工入职").build()));
    }

    @Test
    void shouldSearchAllCollectionsWithConfiguredTopKMultiplier() {
        KeywordRetrieverService keywordRetrieverService = mock(KeywordRetrieverService.class);
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        SearchChannelProperties properties = new SearchChannelProperties();
        properties.getChannels().getKeyword().setTopKMultiplier(3);
        KeywordSearchChannel channel = new KeywordSearchChannel(Optional.of(keywordRetrieverService), properties, knowledgeBaseMapper);

        when(knowledgeBaseMapper.selectList(any())).thenReturn(List.of(
                KnowledgeBaseDO.builder().collectionName("kb_hr").build(),
                KnowledgeBaseDO.builder().collectionName("kb_it").build()
        ));
        when(keywordRetrieverService.retrieve(any(RetrieveRequest.class)))
                .thenReturn(List.of(chunk("hr-1")))
                .thenReturn(List.of(chunk("it-1")));

        SearchChannelResult result = channel.search(SearchContext.builder()
                .rewrittenQuestion("员工账号开通")
                .topK(4)
                .build());

        assertEquals(SearchChannelType.KEYWORD_ES, result.getChannelType());
        assertEquals(List.of("hr-1", "it-1"), result.getChunks().stream().map(RetrievedChunk::getId).toList());

        ArgumentCaptor<RetrieveRequest> requestCaptor = ArgumentCaptor.forClass(RetrieveRequest.class);
        org.mockito.Mockito.verify(keywordRetrieverService, org.mockito.Mockito.times(2)).retrieve(requestCaptor.capture());
        assertEquals(List.of("kb_hr", "kb_it"), requestCaptor.getAllValues().stream().map(RetrieveRequest::getCollectionName).toList());
        assertTrue(requestCaptor.getAllValues().stream().allMatch(request -> request.getTopK() == 12));
        assertTrue(requestCaptor.getAllValues().stream().allMatch(request -> request.getQuery().equals("员工账号开通")));
    }

    private RetrievedChunk chunk(String id) {
        return RetrievedChunk.builder()
                .id(id)
                .text(id)
                .score(1.0F)
                .build();
    }
}
