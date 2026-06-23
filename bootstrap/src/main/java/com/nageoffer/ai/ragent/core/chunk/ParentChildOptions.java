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

import java.util.Map;

/**
 * 父子分块配置。
 *
 * @param parentTargetChars 父块目标字符数
 * @param parentMaxChars    父块硬上限字符数
 * @param childChunkSize    子块目标字符数
 * @param childOverlapSize  子块重叠字符数
 * @param siblingWindow     检索命中后向前/向后扩展的子块窗口
 */
public record ParentChildOptions(
        int parentTargetChars,
        int parentMaxChars,
        int childChunkSize,
        int childOverlapSize,
        int siblingWindow
) implements ChunkingOptions {

    @Override
    public Map<String, Integer> toConfigMap() {
        return Map.of(
                "parentTargetChars", parentTargetChars,
                "parentMaxChars", parentMaxChars,
                "childChunkSize", childChunkSize,
                "childOverlapSize", childOverlapSize,
                "siblingWindow", siblingWindow);
    }
}
