/*
 * Copyright © 2015 Stefan Niederhauser (nidin@gmx.ch)
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
package guru.nidi.graphviz.model;

import guru.nidi.graphviz.attribute.*;

import java.util.*;

public final class CreationContext {
    private static final ThreadLocal<Stack<CreationContext>> CONTEXT = ThreadLocal.withInitial(Stack::new);
    private final MutableGraph graph;
    private final Map<Label, ImmutableNode> immutableNodes = new HashMap<>();
    private final Map<Label, MutableNode> mutableNodes = new HashMap<>();
    private final MutableAttributed<CreationContext> nodeAttributes = new SimpleMutableAttributed<>(this);
    private final MutableAttributed<CreationContext> linkAttributes = new SimpleMutableAttributed<>(this);
    private final MutableAttributed<CreationContext> graphAttributes = new SimpleMutableAttributed<>(this);

    private CreationContext() {
        this(null);
    }

    private CreationContext(MutableGraph graph) {
        this.graph = graph;
    }

    public static <T> T use(ThrowingFunction<CreationContext, T> actions) {
        return use(null, actions);
    }

    public static <T> T use(MutableGraph graph, ThrowingFunction<CreationContext, T> actions) {
        final CreationContext ctx = begin(graph);
        try {
            return actions.apply(ctx);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            end();
        }
    }

    public static Optional<CreationContext> current() {
        final Stack<CreationContext> cs = CONTEXT.get();
        return cs.empty() ? Optional.empty() : Optional.of(cs.peek());
    }

    public static CreationContext get() {
        final Stack<CreationContext> cs = CONTEXT.get();
        if (cs.empty()) {
            throw new IllegalStateException("Not in a CreationContext");
        }
        return cs.peek();
    }

    public static CreationContext begin() {
        return begin(null);
    }

    public static CreationContext begin(MutableGraph graph) {
        final CreationContext ctx = new CreationContext(graph);
        CONTEXT.get().push(ctx);
        return ctx;
    }

    public static void end() {
        final Stack<CreationContext> cs = CONTEXT.get();
        if (!cs.empty()) {
            final CreationContext ctx = cs.pop();
            if (ctx.graph != null) {
                ctx.graph.graphAttrs().add(ctx.graphAttributes);
            }
        }
    }

    public MutableAttributed<CreationContext> nodeAttrs() {
        return nodeAttributes;
    }

    public MutableAttributed<CreationContext> linkAttrs() {
        return linkAttributes;
    }

    public MutableAttributed<CreationContext> graphAttrs() {
        return graphAttributes;
    }

    static ImmutableNode createNode(Label name) {
        return current()
                .map(ctx -> ctx.newNode(name))
                .orElseGet(() -> new ImmutableNode(name));
    }

    private ImmutableNode newNode(Label name) {
        return immutableNodes.computeIfAbsent(name, ImmutableNode::new).with(nodeAttributes);
    }

    static MutableGraph createMutGraph() {
        return current()
                .map(CreationContext::newMutGraph)
                .orElseGet(MutableGraph::new);
    }

    private MutableGraph newMutGraph() {
        final MutableGraph mg = new MutableGraph();
        if (graph != null) {
            graph.add(mg);
        }
        return mg;
    }

    static MutableNode createMutNode(Label name) {
        return current()
                .map(ctx -> ctx.newMutNode(name))
                .orElseGet(() -> new MutableNode().setName(name));
    }

    private MutableNode newMutNode(Label name) {
        return mutableNodes.computeIfAbsent(name, l -> addMutNode(new MutableNode()).setName(l).add(nodeAttributes));
    }

    private MutableNode addMutNode(MutableNode node) {
        if (graph != null) {
            graph.add(node);
        }
        return node;
    }

    static Link createLink(LinkSource from, LinkTarget to) {
        final Link link = new Link(from, to, Attributes.attrs());
        return current()
                .map(ctx -> link.with(ctx.linkAttributes))
                .orElse(link);
    }
}
