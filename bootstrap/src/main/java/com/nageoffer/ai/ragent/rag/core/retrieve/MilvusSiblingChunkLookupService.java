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

package com.nageoffer.ai.ragent.rag.core.retrieve;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.response.QueryResp;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "milvus", matchIfMissing = true)
public class MilvusSiblingChunkLookupService implements SiblingChunkLookupService {

    private final MilvusClientV2 milvusClient;

    @Override
    public List<RetrievedChunk> findSiblings(String collectionName, String parentId, int startChildIndex, int endChildIndex) {
        if (!StringUtils.hasText(collectionName)
                || !StringUtils.hasText(parentId)
                || startChildIndex > endChildIndex) {
            return List.of();
        }

        String filter = "metadata[\"collection_name\"] == \"" + escape(collectionName) + "\""
                + " && metadata[\"parentId\"] == \"" + escape(parentId) + "\""
                + " && metadata[\"childIndex\"] >= " + startChildIndex
                + " && metadata[\"childIndex\"] <= " + endChildIndex;

        QueryReq req = QueryReq.builder()
                .collectionName(collectionName)
                .filter(filter)
                .outputFields(List.of("id", "content", "metadata"))
                .limit(Math.max(1, endChildIndex - startChildIndex + 1))
                .build();
        QueryResp resp = milvusClient.query(req);
        if (resp == null || resp.getQueryResults() == null) {
            return List.of();
        }
        return resp.getQueryResults().stream()
                .map(QueryResp.QueryResult::getEntity)
                .map(this::toChunk)
                .sorted(Comparator.comparingInt(chunk -> asInt(chunk.getMetadata().get("childIndex"), 0)))
                .toList();
    }

    private RetrievedChunk toChunk(Map<String, Object> entity) {
        return RetrievedChunk.builder()
                .id(Objects.toString(entity.get("id"), ""))
                .text(Objects.toString(entity.get("content"), ""))
                .metadata(toMetadataMap(entity.get("metadata")))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMetadataMap(Object metadata) {
        if (metadata instanceof Map<?, ?> source) {
            Map<String, Object> result = new HashMap<>();
            source.forEach((key, value) -> {
                if (key != null) {
                    result.put(key.toString(), value);
                }
            });
            return result;
        }
        return new HashMap<>();
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

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
