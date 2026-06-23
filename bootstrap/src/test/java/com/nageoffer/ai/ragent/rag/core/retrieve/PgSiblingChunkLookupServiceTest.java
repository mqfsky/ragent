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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PgSiblingChunkLookupServiceTest {

    @Test
    void shouldQuerySiblingChunksByParentAndChildRange() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PgSiblingChunkLookupService lookupService = new PgSiblingChunkLookupService(jdbcTemplate);
        ArgumentCaptor<RowMapper<RetrievedChunk>> rowMapperCaptor = ArgumentCaptor.forClass(RowMapper.class);

        when(jdbcTemplate.query(anyString(), rowMapperCaptor.capture(), any(), any(), any(), any()))
                .thenReturn(List.of());

        lookupService.findSiblings("kb_hr", "parent-1", 1, 3);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class),
                eq("kb_hr"), eq("parent-1"), eq(1), eq(3));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("metadata->>'collection_name' = ?"));
        assertTrue(sql.contains("metadata->>'parentId' = ?"));
        assertTrue(sql.contains("(metadata->>'childIndex')::int BETWEEN ? AND ?"));
        assertTrue(sql.contains("ORDER BY (metadata->>'childIndex')::int ASC"));

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("id")).thenReturn("child-2");
        when(resultSet.getString("content")).thenReturn("病假工资按制度执行");
        when(resultSet.getString("metadata")).thenReturn("""
                {"parentId":"parent-1","childIndex":2,"headingPath":"请假制度"}
                """);

        RetrievedChunk chunk = rowMapperCaptor.getValue().mapRow(resultSet, 0);

        assertEquals("child-2", chunk.getId());
        assertEquals("parent-1", chunk.getMetadata().get("parentId"));
        assertEquals(2, chunk.getMetadata().get("childIndex"));
    }
}
