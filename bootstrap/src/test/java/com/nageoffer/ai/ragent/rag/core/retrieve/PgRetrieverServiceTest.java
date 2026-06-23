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
import com.nageoffer.ai.ragent.infra.embedding.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PgRetrieverServiceTest {

    @Test
    void shouldMapMetadataFromVectorRows() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        PgRetrieverService retriever = new PgRetrieverService(jdbcTemplate, embeddingService);
        ArgumentCaptor<RowMapper<RetrievedChunk>> rowMapperCaptor = ArgumentCaptor.forClass(RowMapper.class);

        when(embeddingService.embed("病假工资")).thenReturn(List.of(0.25F, 0.5F));
        when(jdbcTemplate.query(anyString(), rowMapperCaptor.capture(), any(), any(), any(), any()))
                .thenReturn(List.of());

        retriever.retrieve(RetrieveRequest.builder()
                .query("病假工资")
                .collectionName("kb_hr")
                .topK(3)
                .build());

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("id")).thenReturn("chunk-2");
        when(resultSet.getString("content")).thenReturn("病假工资按制度执行");
        when(resultSet.getFloat("score")).thenReturn(0.82F);
        when(resultSet.getString("metadata")).thenReturn("""
                {"collection_name":"kb_hr","parentId":"parent-1","childIndex":2}
                """);

        RetrievedChunk chunk = rowMapperCaptor.getValue().mapRow(resultSet, 0);

        assertEquals("chunk-2", chunk.getId());
        assertEquals("parent-1", chunk.getMetadata().get("parentId"));
        assertEquals(2, chunk.getMetadata().get("childIndex"));
    }
}
