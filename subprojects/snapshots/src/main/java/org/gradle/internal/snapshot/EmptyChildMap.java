/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.snapshot;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

public class EmptyChildMap<T> extends AbstractChildMap<T> {
    private static final EmptyChildMap<Object> INSTANCE = new EmptyChildMap<>();

    @SuppressWarnings("unchecked")
    public static <T> EmptyChildMap<T> getInstance() {
        return (EmptyChildMap<T>) INSTANCE;
    }

    private EmptyChildMap() {
    }

    @Override
    public <R> R findChild(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, FindChildHandler<T, R> handler) {
        return handler.handleNotFound();
    }

    @Override
    protected <R> R handlePath(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, PathRelationshipHandler<R> handler) {
        return handler.handleDifferent(0);
    }

    @Override
    protected T get(int index) {
        throw indexOutOfBoundsException(index);
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public List<T> values() {
        return Collections.emptyList();
    }

    @Override
    protected AbstractChildMap<T> withNewChild(int insertBefore, String path, T newChild) {
        if (insertBefore != 0) {
            throw indexOutOfBoundsException(insertBefore);
        }
        return new SingletonChildMap<>(path, newChild);
    }

    @Override
    protected AbstractChildMap<T> withReplacedChild(int childIndex, String newPath, T newChild) {
        throw indexOutOfBoundsException(childIndex);
    }

    @Override
    protected AbstractChildMap<T> withRemovedChild(int childIndex) {
        throw indexOutOfBoundsException(childIndex);
    }

    @Override
    public void visitChildren(BiConsumer<String, T> visitor) {
    }

    private static IndexOutOfBoundsException indexOutOfBoundsException(int childIndex) {
        return new IndexOutOfBoundsException("Index out of range: " + childIndex);
    }
}
