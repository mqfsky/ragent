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

package com.nageoffer.ai.ragent.core.chunk.strategy;

import com.nageoffer.ai.ragent.core.chunk.ParentChildOptions;
import com.nageoffer.ai.ragent.core.chunk.TextBoundaryOptions;
import com.nageoffer.ai.ragent.core.chunk.VectorChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureAwareTextChunkerTest {

    private final StructureAwareTextChunker chunker = new StructureAwareTextChunker();

    @Test
    void parentChildModeShouldEmitChildChunksWithParentMetadata() {
        String text = """
                # 请假制度

                病假工资按照公司制度执行，员工需要提交医院证明并在系统中完成申请。
                审批通过后，HR 会根据请假天数和岗位规则核算薪资。
                如果申请材料缺失，直属主管会退回申请并要求员工补充材料。
                员工可以在 OA 系统中查看审批进度和薪资核算说明。
                """;

        List<VectorChunk> chunks = chunker.chunk(text, new ParentChildOptions(800, 1200, 55, 0, 1));

        assertTrue(chunks.size() > 1);
        String parentId = String.valueOf(chunks.get(0).getMetadata().get("parentId"));
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = chunks.get(i).getMetadata();
            assertEquals("child", metadata.get("chunkType"));
            assertEquals(parentId, metadata.get("parentId"));
            assertEquals(0, metadata.get("parentIndex"));
            assertEquals(i, metadata.get("childIndex"));
            assertEquals(chunks.size(), metadata.get("parentChildCount"));
            assertEquals(1, metadata.get("siblingWindow"));
            assertEquals("请假制度", metadata.get("headingPath"));
        }
    }

    @Test
    void legacyTextBoundaryModeShouldKeepSingleLevelMetadataFreeChunks() {
        String text = """
                # 请假制度

                病假工资按照公司制度执行。
                """;

        List<VectorChunk> chunks = chunker.chunk(text, new TextBoundaryOptions(1400, 0, 1800, 600));

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getMetadata() == null || chunk.getMetadata().isEmpty()));
    }
}
