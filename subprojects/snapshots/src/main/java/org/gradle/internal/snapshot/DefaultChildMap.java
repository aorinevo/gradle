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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class DefaultChildMap<T> implements ChildMap<T> {
    private final List<Entry<T>> children;

    public DefaultChildMap(List<Entry<T>> children) {
        this.children = children;
    }

    @Override
    public Optional<T> get(VfsRelativePath relativePath) {
        return Optional.empty();
    }

    @Override
    public <R> R findChild(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, FindChildHandler<T, R> handler) {
        int childIndex = findChildIndexWithCommonPrefix(relativePath, caseSensitivity);
        if (childIndex < 0) {
            return handler.handleNotFound();
        }
        Entry<T> entry = children.get(childIndex);
        return entry.findPath(relativePath, caseSensitivity, handler);
    }

    @Override
    public <R> R handlePath(VfsRelativePath relativePath, CaseSensitivity caseSensitivity, PathRelationshipHandler<R> handler) {
        int childIndex = findChildIndexWithCommonPrefix(relativePath, caseSensitivity);
        if (childIndex >= 0) {
            return children.get(childIndex).handlePath(relativePath, childIndex, caseSensitivity, handler);
        }
        return handler.handleDifferent(-childIndex - 1);
    }

    private int findChildIndexWithCommonPrefix(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        int childIndex = SearchUtil.binarySearch(
            children,
            candidate -> relativePath.compareToFirstSegment(candidate.getPath(), caseSensitivity)
        );
        return childIndex;
    }

    @Override
    public T get(int index) {
        return children.get(index).getValue();
    }

    @Override
    public int indexOf(String path, CaseSensitivity caseSensitivity) {
        return handlePath(VfsRelativePath.of(path), caseSensitivity, new PathRelationshipHandler<Integer>() {
            @Override
            public Integer handleDescendant(String childPath, int childIndex) {
                return -1;
            }

            @Override
            public Integer handleAncestor(String childPath, int childIndex) {
                return -1;
            }

            @Override
            public Integer handleSame(int childIndex) {
                return childIndex;
            }

            @Override
            public Integer handleCommonPrefix(int commonPrefixLength, String childPath, int childIndex) {
                return -1;
            }

            @Override
            public Integer handleDifferent(int indexOfNextBiggerChild) {
                return -1;
            }
        });
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public List<T> values() {
        return children.stream()
            .map(Entry::getValue)
            .collect(Collectors.toList());
    }

    @Override
    public ChildMap<T> withNewChild(int insertBefore, String path, T newChild) {
        List<Entry<T>> newChildren = new ArrayList<>(children);
        newChildren.add(insertBefore, new Entry<>(path, newChild));
        return new DefaultChildMap<>(newChildren);
    }

    @Override
    public ChildMap<T> withReplacedChild(int childIndex, String newPath, T newChild) {
        Entry<T> oldEntry = children.get(childIndex);
        if (oldEntry.getPath().equals(newPath) && oldEntry.getValue().equals(newChild)) {
            return this;
        }
        List<Entry<T>> newChildren = new ArrayList<>(children);
        newChildren.set(childIndex, new Entry<>(newPath, newChild));
        return new DefaultChildMap<>(newChildren);
    }

    @Override
    public ChildMap<T> withRemovedChild(int childIndex) {
        List<Entry<T>> newChildren = new ArrayList<>(children);
        newChildren.remove(childIndex);
        if (newChildren.size() == 1) {
            Entry<T> onlyChild = newChildren.get(0);
            return new SingletonChildMap<>(onlyChild);
        }
        return new DefaultChildMap<>(newChildren);
    }

    @Override
    public void visitChildren(BiConsumer<String, T> visitor) {
        for (Entry<T> child : children) {
            visitor.accept(child.getPath(), child.getValue());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultChildMap<?> that = (DefaultChildMap<?>) o;

        return children.equals(that.children);
    }

    @Override
    public int hashCode() {
        return children.hashCode();
    }
}
