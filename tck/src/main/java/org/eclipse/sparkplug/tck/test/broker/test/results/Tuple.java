/*
 * Copyright 2019-present HiveMQ and the HiveMQ Community
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.sparkplug.tck.test.broker.test.results;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Tuple<A, B> {
    private @NotNull
    final A key;
    private @Nullable
    final B value;

    public Tuple(final @NotNull A key, final @Nullable B value) {
        this.key = key;
        this.value = value;
    }

    public @NotNull A getKey() {
        return key;
    }

    public @Nullable B getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "Tuple{" +
                "key=" + key +
                ", value=" + value +
                '}';
    }
}
