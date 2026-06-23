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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "pg")
public class PgSiblingChunkLookupService implements SiblingChunkLookupService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<RetrievedChunk> findSiblings(String collectionName, String parentId, int startChildIndex, int endChildIndex) {
        if (!StringUtils.hasText(collectionName)
                || !StringUtils.hasText(parentId)
                || startChildIndex > endChildIndex) {
            return List.of();
        }

        String sql = """
                SELECT id, content, metadata
                FROM t_knowledge_vector
                WHERE metadata->>'collection_name' = ?
                  AND metadata->>'parentId' = ?
                  AND (metadata->>'childIndex')::int BETWEEN ? AND ?
                ORDER BY (metadata->>'childIndex')::int ASC
                """;

        // noinspection SqlDialectInspection,SqlNoDataSourceInspection
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> RetrievedChunk.builder()
                        .id(rs.getString("id"))
                        .text(rs.getString("content"))
                        .metadata(parseMetadata(rs.getString("metadata")))
                        .build(),
                collectionName,
                parentId,
                startChildIndex,
                endChildIndex);
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(metadataJson, MAP_TYPE);
            return parsed == null ? new HashMap<>() : new HashMap<>(parsed);
        } catch (Exception e) {
            log.warn("Sibling chunk metadata 解析失败: {}", metadataJson, e);
            return new HashMap<>();
        }
    }
}
