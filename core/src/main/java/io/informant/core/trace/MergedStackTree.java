/**
 * Copyright 2011-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.informant.core.trace;

import io.informant.core.util.NotThreadSafe;
import io.informant.core.util.ThreadSafe;

import java.lang.Thread.State;
import java.lang.management.ThreadInfo;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import checkers.igj.quals.ReadOnly;
import checkers.nullness.quals.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;

/**
 * Merged stack tree built from sampled stack traces captured by periodic calls to
 * {@link Thread#getStackTrace()}.
 * 
 * This can be either thread-specific stack sampling tied to a trace, or it can be a global sampled
 * stack tree across all threads.
 * 
 * @author Trask Stalnaker
 * @since 0.5
 */
// TODO it would be more efficient to store stack traces unmerged up until some point
// (e.g. to optimize for trace captures which are never stored)
// in this case, it should be configurable how many stack traces to store unmerged
// after which the existing stack traces are merged as well as future stack traces
@ThreadSafe
public class MergedStackTree {

    private static final Pattern metricMarkerMethodPattern = Pattern
            .compile("^.*\\$informant\\$metric\\$(.*)\\$[0-9]+$");

    private final Collection<MergedStackTreeNode> rootNodes = Queues.newConcurrentLinkedQueue();

    private final Object lock = new Object();

    @Nullable
    public MergedStackTreeNode getRootNode() {
        return MergedStackTreeNode.createSyntheticRoot(ImmutableList.copyOf(rootNodes));
    }

    void addStackTrace(ThreadInfo threadInfo) {
        synchronized (lock) {
            // TODO put into list, then merge every 100, or whenever merge is requested
            List<StackTraceElement> stackTrace = Arrays.asList(threadInfo.getStackTrace());
            addToStackTree(stripSyntheticMetricMethods(stackTrace), threadInfo.getThreadState());
        }
    }

    @VisibleForTesting
    public void addToStackTree(@ReadOnly List<StackTraceElementPlus> stackTrace,
            State threadState) {
        MergedStackTreeNode lastMatchedNode = null;
        Iterable<MergedStackTreeNode> nextChildNodes = rootNodes;
        int nextIndex;
        // navigate the stack tree nodes
        // matching the new stack trace as far as possible
        for (nextIndex = stackTrace.size() - 1; nextIndex >= 0; nextIndex--) {
            StackTraceElementPlus element = stackTrace.get(nextIndex);
            // check all child nodes
            boolean matchFound = false;
            for (MergedStackTreeNode childNode : nextChildNodes) {
                if (matches(element.getStackTraceElement(), childNode, nextIndex == 0,
                        threadState)) {
                    // match found, update lastMatchedNode and continue
                    childNode.incrementSampleCount();
                    List<String> metricNames = element.getMetricNames();
                    if (metricNames != null) {
                        childNode.addAllAbsentMetricNames(metricNames);
                    }
                    lastMatchedNode = childNode;
                    nextChildNodes = lastMatchedNode.getChildNodes();
                    matchFound = true;
                    break;
                }
            }
            if (!matchFound) {
                break;
            }
        }
        // add remaining stack trace elements
        for (int i = nextIndex; i >= 0; i--) {
            StackTraceElementPlus element = stackTrace.get(i);
            MergedStackTreeNode nextNode = MergedStackTreeNode.create(
                    element.getStackTraceElement(), element.getMetricNames());
            if (i == 0) {
                // leaf node
                nextNode.setLeafThreadState(threadState);
            }
            if (lastMatchedNode == null) {
                // new root node
                rootNodes.add(nextNode);
            } else {
                lastMatchedNode.addChildNode(nextNode);
            }
            lastMatchedNode = nextNode;
        }
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("rootNodes", rootNodes)
                .toString();
    }

    // recreate the stack trace as it would have been without the synthetic $metric$ methods
    public static List<StackTraceElementPlus> stripSyntheticMetricMethods(
            @ReadOnly List<StackTraceElement> stackTrace) {

        List<StackTraceElementPlus> stackTracePlus = Lists.newArrayListWithCapacity(
                stackTrace.size());
        for (Iterator<StackTraceElement> i = stackTrace.iterator(); i.hasNext();) {
            StackTraceElement element = i.next();
            String metricName = getMetricName(element);
            if (metricName != null) {
                String originalMethodName = element.getMethodName();
                List<String> metricNames = Lists.newArrayList();
                metricNames.add(metricName);
                // skip over successive $metric$ methods up to and including the "original" method
                while (i.hasNext()) {
                    StackTraceElement skipElement = i.next();
                    metricName = getMetricName(skipElement);
                    if (metricName == null) {
                        // loop should always terminate here since synthetic $metric$ methods should
                        // never be the last element (the last element is the first element in the
                        // call stack)
                        originalMethodName = skipElement.getMethodName();
                        break;
                    }
                    metricNames.add(metricName);
                }
                // "original" in the sense that this is what it would have been without the
                // synthetic $metric$ methods
                StackTraceElement originalElement = new StackTraceElement(element.getClassName(),
                        originalMethodName, element.getFileName(), element.getLineNumber());
                stackTracePlus.add(new StackTraceElementPlus(originalElement, metricNames));
            } else {
                stackTracePlus.add(new StackTraceElementPlus(element, null));
            }
        }
        return stackTracePlus;
    }

    @Nullable
    private static String getMetricName(StackTraceElement stackTraceElement) {
        Matcher matcher = metricMarkerMethodPattern.matcher(stackTraceElement.getMethodName());
        if (matcher.matches()) {
            return matcher.group(1).replace("$", " ");
        } else {
            return null;
        }
    }

    private static boolean matches(StackTraceElement stackTraceElement,
            MergedStackTreeNode childNode, boolean leaf, State threadState) {

        State leafThreadState = childNode.getLeafThreadState();
        if (leafThreadState != null && leaf) {
            // only consider thread state when matching the leaf node
            return stackTraceElement.equals(childNode.getStackTraceElement())
                    && threadState == leafThreadState;
        } else if (leafThreadState == null && !leaf) {
            return stackTraceElement.equals(childNode.getStackTraceElement());
        } else {
            return false;
        }
    }

    @NotThreadSafe
    public static class StackTraceElementPlus {
        private final StackTraceElement stackTraceElement;
        @ReadOnly
        @Nullable
        private final List<String> metricNames;
        private StackTraceElementPlus(StackTraceElement stackTraceElement,
                @ReadOnly @Nullable List<String> metricNames) {
            this.stackTraceElement = stackTraceElement;
            this.metricNames = metricNames;
        }
        public StackTraceElement getStackTraceElement() {
            return stackTraceElement;
        }
        @ReadOnly
        @Nullable
        public List<String> getMetricNames() {
            return metricNames;
        }
    }
}
