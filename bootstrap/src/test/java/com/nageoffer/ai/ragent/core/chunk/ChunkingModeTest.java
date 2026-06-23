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

package com.nageoffer.ai.ragent.core.chunk;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkingModeTest {

    @Test
    void structureAwareShouldParseParentChildConfig() {
        ChunkingOptions options = ChunkingMode.STRUCTURE_AWARE.createOptions(Map.of(
                "parentTargetChars", 1800,
                "parentMaxChars", 2600,
                "childChunkSize", 500,
                "childOverlapSize", 80,
                "siblingWindow", 1
        ));

        ParentChildOptions parentChild = assertInstanceOf(ParentChildOptions.class, options);
        assertEquals(1800, parentChild.parentTargetChars());
        assertEquals(2600, parentChild.parentMaxChars());
        assertEquals(500, parentChild.childChunkSize());
        assertEquals(80, parentChild.childOverlapSize());
        assertEquals(1, parentChild.siblingWindow());
    }

    @Test
    void structureAwareShouldKeepLegacyTextBoundaryConfig() {
        ChunkingOptions options = ChunkingMode.STRUCTURE_AWARE.createOptions(Map.of(
                "targetChars", 1400,
                "overlapChars", 0,
                "maxChars", 1800,
                "minChars", 600
        ));

        TextBoundaryOptions textBoundary = assertInstanceOf(TextBoundaryOptions.class, options);
        assertEquals(1400, textBoundary.targetChars());
        assertEquals(0, textBoundary.overlapChars());
        assertEquals(1800, textBoundary.maxChars());
        assertEquals(600, textBoundary.minChars());
    }

    @Test
    void structureAwareDefaultsShouldExposeParentChildConfig() {
        Map<String, Integer> defaults = ChunkingMode.STRUCTURE_AWARE.getDefaultConfig();

        assertTrue(defaults.containsKey("parentTargetChars"));
        assertTrue(defaults.containsKey("parentMaxChars"));
        assertTrue(defaults.containsKey("childChunkSize"));
        assertTrue(defaults.containsKey("childOverlapSize"));
        assertTrue(defaults.containsKey("siblingWindow"));
    }
}
