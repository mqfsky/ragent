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
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 基于 PostgreSQL 全文检索的关键词检索服务
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "pg")
public class PgKeywordRetrieverService implements KeywordRetrieverService {

    private final JdbcTemplate jdbcTemplate;
    private final SearchChannelProperties properties;

    @Override
    public List<RetrievedChunk> retrieve(RetrieveRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getQuery())
                || !StringUtils.hasText(request.getCollectionName())
                || request.getTopK() <= 0) {
            return List.of();
        }

        String textSearchConfig = resolveTextSearchConfig();
        String vectorExpr = "to_tsvector('" + textSearchConfig + "', coalesce(content, ''))";
        String queryExpr = "websearch_to_tsquery('" + textSearchConfig + "', ?)";
        String sql = """
                WITH q AS (
                    SELECT %s AS query
                )
                SELECT id, content, ts_rank_cd(%s, q.query) AS score
                FROM t_knowledge_vector, q
                WHERE metadata->>'collection_name' = ?
                  AND %s @@ q.query
                ORDER BY score DESC
                LIMIT ?
                """.formatted(queryExpr, vectorExpr, vectorExpr);

        // noinspection SqlDialectInspection,SqlNoDataSourceInspection
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> RetrievedChunk.builder()
                        .id(rs.getString("id"))
                        .text(rs.getString("content"))
                        .score(rs.getFloat("score"))
                        .build(),
                request.getQuery(),
                request.getCollectionName(),
                request.getTopK());
    }

    private String resolveTextSearchConfig() {
        String config = properties.getChannels().getKeyword().getTextSearchConfig();
        if (!StringUtils.hasText(config) || !config.matches("[A-Za-z0-9_]+")) {
            return "simple";
        }
        return config;
    }
}
