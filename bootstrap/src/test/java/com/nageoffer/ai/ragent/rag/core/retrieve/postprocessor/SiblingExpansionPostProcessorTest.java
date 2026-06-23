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
import com.nageoffer.ai.ragent.rag.core.retrieve.SiblingChunkLookupService;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SiblingExpansionPostProcessorTest {

    @Test
    void shouldExpandHitChildWithSiblingWindowBeforeRerank() {
        SiblingChunkLookupService lookupService = mock(SiblingChunkLookupService.class);
        SiblingExpansionPostProcessor processor = new SiblingExpansionPostProcessor(lookupService);
        RetrievedChunk hit = RetrievedChunk.builder()
                .id("child-2")
                .text("命中子块")
                .score(0.91F)
                .metadata(Map.of(
                        "collection_name", "kb_hr",
                        "chunkType", "child",
                        "parentId", "parent-1",
                        "childIndex", 2,
                        "parentChildCount", 5,
                        "siblingWindow", 1,
                        "headingPath", "请假制度"
                ))
                .build();
        when(lookupService.findSiblings("kb_hr", "parent-1", 1, 3)).thenReturn(List.of(
                child("child-1", "前文", 1),
                child("child-2", "命中子块", 2),
                child("child-3", "后文", 3)
        ));

        List<RetrievedChunk> expanded = processor.process(
                List.of(hit),
                List.of(),
                SearchContext.builder().topK(5).build());

        assertEquals(1, expanded.size());
        RetrievedChunk chunk = expanded.get(0);
        assertEquals("expanded:parent-1:1-3", chunk.getId());
        assertEquals("前文\n\n命中子块\n\n后文", chunk.getText());
        assertEquals(0.91F, chunk.getScore());
        assertEquals("expanded", chunk.getMetadata().get("chunkType"));
        assertEquals(true, chunk.getMetadata().get("expanded"));
        assertEquals(List.of("child-2"), chunk.getMetadata().get("sourceHitChunkIds"));
        assertEquals(List.of("child-1", "child-2", "child-3"), chunk.getMetadata().get("sourceChildChunkIds"));
        assertEquals(List.of(1, 3), chunk.getMetadata().get("childIndexRange"));
        verify(lookupService).findSiblings("kb_hr", "parent-1", 1, 3);
    }

    @Test
    void shouldKeepChunksWithoutParentMetadataUnchanged() {
        SiblingChunkLookupService lookupService = mock(SiblingChunkLookupService.class);
        SiblingExpansionPostProcessor processor = new SiblingExpansionPostProcessor(lookupService);
        RetrievedChunk chunk = RetrievedChunk.builder()
                .id("legacy")
                .text("旧分块")
                .score(0.5F)
                .build();

        List<RetrievedChunk> processed = processor.process(List.of(chunk), List.of(), SearchContext.builder().build());

        assertEquals(List.of(chunk), processed);
    }

    private RetrievedChunk child(String id, String text, int childIndex) {
        return RetrievedChunk.builder()
                .id(id)
                .text(text)
                .metadata(Map.of(
                        "collection_name", "kb_hr",
                        "parentId", "parent-1",
                        "childIndex", childIndex,
                        "headingPath", "请假制度"
                ))
                .build();
    }
}
