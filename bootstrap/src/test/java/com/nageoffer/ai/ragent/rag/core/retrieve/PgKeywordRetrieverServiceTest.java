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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PgKeywordRetrieverServiceTest {

    @Test
    void shouldQueryPostgresFullTextIndexWithCollectionFilter() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        SearchChannelProperties properties = new SearchChannelProperties();
        PgKeywordRetrieverService retriever = new PgKeywordRetrieverService(jdbcTemplate, properties);
        List<RetrievedChunk> expected = List.of(RetrievedChunk.builder()
                .id("chunk-1")
                .text("员工入职流程")
                .score(0.42F)
                .build());

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(expected);

        List<RetrievedChunk> actual = retriever.retrieve(RetrieveRequest.builder()
                .query("员工 入职")
                .collectionName("kb_hr")
                .topK(6)
                .build());

        assertSame(expected, actual);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(), any(), any());
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("websearch_to_tsquery('simple', ?)"));
        assertTrue(sql.contains("ts_rank_cd"));
        assertTrue(sql.contains("metadata->>'collection_name' = ?"));
        assertTrue(sql.contains("LIMIT ?"));
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), org.mockito.ArgumentMatchers.eq("员工 入职"), org.mockito.ArgumentMatchers.eq("kb_hr"), org.mockito.ArgumentMatchers.eq(6));
    }

    @Test
    void shouldReturnEmptyWhenQueryOrCollectionIsBlank() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        SearchChannelProperties properties = new SearchChannelProperties();
        PgKeywordRetrieverService retriever = new PgKeywordRetrieverService(jdbcTemplate, properties);

        assertEquals(List.of(), retriever.retrieve(RetrieveRequest.builder()
                .query(" ")
                .collectionName("kb_hr")
                .topK(5)
                .build()));
        assertEquals(List.of(), retriever.retrieve(RetrieveRequest.builder()
                .query("员工")
                .collectionName(" ")
                .topK(5)
                .build()));
    }
}
