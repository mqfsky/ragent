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

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import com.nageoffer.ai.ragent.rag.core.retrieve.KeywordRetrieverService;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieveRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * PostgreSQL 关键词检索通道
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeywordSearchChannel implements SearchChannel {

    private final Optional<KeywordRetrieverService> keywordRetrieverService;
    private final SearchChannelProperties properties;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    @Override
    public String getName() {
        return "KeywordSearch";
    }

    @Override
    public int getPriority() {
        return 5;
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        return keywordRetrieverService.isPresent()
                && properties.getChannels().getKeyword().isEnabled()
                && context != null
                && StringUtils.hasText(context.getMainQuestion());
    }

    @Override
    public SearchChannelResult search(SearchContext context) {
        long startTime = System.currentTimeMillis();
        try {
            List<String> collections = getAllKBCollections();
            if (collections.isEmpty()) {
                return result(List.of(), startTime);
            }

            int topK = context.getTopK() * Math.max(1, properties.getChannels().getKeyword().getTopKMultiplier());
            List<RetrievedChunk> chunks = new ArrayList<>();
            for (String collection : collections) {
                chunks.addAll(keywordRetrieverService.get().retrieve(RetrieveRequest.builder()
                        .query(context.getMainQuestion())
                        .collectionName(collection)
                        .topK(topK)
                        .build()));
            }

            log.info("关键词检索完成，collections={}, chunks={}, latency={}ms",
                    collections.size(), chunks.size(), System.currentTimeMillis() - startTime);
            return result(chunks, startTime);
        } catch (Exception e) {
            log.error("关键词检索失败", e);
            return result(List.of(), startTime);
        }
    }

    @Override
    public SearchChannelType getType() {
        return SearchChannelType.KEYWORD_ES;
    }

    private SearchChannelResult result(List<RetrievedChunk> chunks, long startTime) {
        return SearchChannelResult.builder()
                .channelType(SearchChannelType.KEYWORD_ES)
                .channelName(getName())
                .chunks(chunks)
                .latencyMs(System.currentTimeMillis() - startTime)
                .build();
    }

    private List<String> getAllKBCollections() {
        Set<String> collections = new LinkedHashSet<>();
        List<KnowledgeBaseDO> kbList = knowledgeBaseMapper.selectList(
                Wrappers.query(KnowledgeBaseDO.class)
                        .select("collection_name")
                        .eq("deleted", 0)
        );
        for (KnowledgeBaseDO kb : kbList) {
            if (kb != null && StringUtils.hasText(kb.getCollectionName())) {
                collections.add(kb.getCollectionName());
            }
        }
        return new ArrayList<>(collections);
    }
}
