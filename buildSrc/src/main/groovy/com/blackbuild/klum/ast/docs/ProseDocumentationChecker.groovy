/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.blackbuild.klum.ast.docs

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.CompletableFuture
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.net.URISyntaxException
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.List
import java.util.Map
import java.util.Set
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

class ProseDocumentationChecker {
    private final String repository
    private final String revision
    private final Parser parser = Parser.builder().extensions(List.of(TablesExtension.create())).build()
    private final Map<String, Node> documents = new HashMap<>()
    private int localLinks
    private int fragments
    private int externalLinks
    private int inboundLinks

    ProseDocumentationChecker(String repository, String revision) {
        this.repository = repository
        this.revision = revision
    }

    private long totalBytes

    byte[] git(String... arguments) {
        List<String> command = new ArrayList<>(List.of("git", "-C", repository))
        command.addAll(List.of(arguments))
        ProcessBuilder builder = new ProcessBuilder(command)
        builder.environment().put("GIT_NO_REPLACE_OBJECTS", "1")
        builder.environment().put("GIT_NO_LAZY_FETCH", "1")
        Process process = builder.start()
        def output = CompletableFuture.supplyAsync { process.inputStream.readNBytes(4194305) }
        def errors = CompletableFuture.supplyAsync { process.errorStream.readNBytes(65537) }
        try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) throw new IllegalArgumentException("Git process timed out")
            byte[] bytes = output.get(5, TimeUnit.SECONDS)
            if (process.exitValue() != 0 || errors.get(5, TimeUnit.SECONDS).length != 0 || bytes.length > 4194304)
                throw new IllegalArgumentException("Git failed, warned or exceeded output budget")
            totalBytes += bytes.length
            if (totalBytes > 67108864) throw new IllegalArgumentException("Prose checks exceed 64 MiB read budget")
            return bytes
        } finally { process.destroyForcibly(); }
    }

    Node document(String path) throws Exception {
        if (!documents.containsKey(path)) {
            byte[] bytes = git("show", revision + ":" + path)
            if (bytes.length > 1048576) throw new IllegalArgumentException("Markdown target exceeds 1 MiB budget")
            String markdown = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
            if (markdown.indexOf('\0') >= 0) throw new IllegalArgumentException("Binary Markdown")
            Node node = parser.parse(markdown)
            documents.put(path, node)
        }
        return documents.get(path)
    }

    static Set<String> headingIds(Node node) {
        new HashSet<>(StaticDocumentationPageRenderer.assignHeadingIds(node).values())
    }

    void verifyDestination(String source, String destination) throws Exception {
        // CommonMark already parses inline/reference/autolinks and ignores code blocks/spans.
        URI uri = new URI(destination)
        if (uri.isAbsolute()) {
            if (!Set.of("https", "http", "mailto").contains(uri.getScheme()) ||
                    (Set.of("https", "http").contains(uri.getScheme()) && uri.getHost() == null) ||
                    (uri.getScheme().equals("mailto") && uri.getSchemeSpecificPart().isBlank())) {
                throw new IllegalArgumentException("Unsupported link scheme: " + destination)
            }
            externalLinks++
            return; // Syntax only; deliberately no remote availability claim.
        }
        if (uri.getAuthority() != null || uri.getQuery() != null || uri.getPath() == null || uri.getPath().contains("\\"))
            throw new IllegalArgumentException("Unsupported relative URL: " + destination)
        String target = localTarget(source, uri)
        if (Path.of(target).isAbsolute() || target.equals("..") || target.startsWith("../"))
            throw new IllegalArgumentException("Link escapes repository: " + destination)
        String type = new String(git("cat-file", "-t", revision + ":" + target), StandardCharsets.UTF_8).trim()
        if (!Set.of("blob", "tree").contains(type)) throw new IllegalArgumentException("Unsupported link target")
        // A repository symlink is not proof that its external target exists.
        String entry = new String(git("ls-tree", "--full-tree", revision, "--", target), StandardCharsets.UTF_8)
        if (entry.startsWith("120000")) throw new IllegalArgumentException("Symlink link target")
        localLinks++
        String fragment = uri.getFragment()
        if (fragment != null && !fragment.isEmpty()) {
            boolean sourceLines = false
            Matcher range = Pattern.compile("L([1-9][0-9]*)(?:-L([1-9][0-9]*))?").matcher(fragment)
            if (range.matches() && type.equals("blob")) {
                long start = Long.parseLong(range.group(1))
                long end = range.group(2) == null ? start : Long.parseLong(range.group(2))
                String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(git("show", revision + ":" + target))).toString()
                sourceLines = start <= end && end <= text.lines().count()
            }
            if (!sourceLines && (!target.endsWith(".md") || !headingIds(document(target)).contains(fragment)))
                throw new IllegalArgumentException("Missing or unsupported fragment: " + source + " -> " + destination)
            fragments++
        }
    }

    void check(String source) throws Exception {
        Node node = document(source)
        int[] h1 = [0]
        List<String> errors = new ArrayList<>()
        node.accept(new AbstractVisitor() {
            public void visit(Heading heading) {
                if (heading.getLevel() == 1) h1[0]++
                visitChildren(heading)
            }
            public void visit(Link link) { verify(link.getDestination()); visitChildren(link); }
            public void visit(Image image) { verify(image.getDestination()); visitChildren(image); }
            void verify(String destination) {
                try { verifyDestination(source, destination); }
                catch (Exception error) { errors.add(error.getMessage()); }
            }
        })
        if (h1[0] != 1) errors.add("Expected one H1 in " + source + "; found " + h1[0])
        String html = HtmlRenderer.builder().escapeHtml(true).extensions(List.of(TablesExtension.create())).build().render(node)
        if (html.isBlank()) errors.add("Empty render: " + source)
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors))
    }

    private static String localTarget(String source, URI uri) {
        Path parent = Path.of(source).getParent() ?: Path.of('')
        uri.path.isEmpty() ? source : parent.resolve(uri.path).normalize().toString()
    }

    private void verifyIncoming(String source, String destination, Set<String> changed) {
        URI uri = new URI(destination)
        if (uri.isAbsolute() || uri.authority != null) return
        if (!changed.contains(localTarget(source, uri))) return
        inboundLinks++
        verifyDestination(source, destination)
    }

    void inbound(Set<String> changed) throws Exception {
        String names = new String(git("ls-tree", "-r", "-z", "--name-only", revision), StandardCharsets.UTF_8)
        List<String> errors = new ArrayList<>()
        String[] sources = names.split("\0")
        if (sources.length > 20000) throw new IllegalArgumentException("Tracked-file budget exceeded")
        for (String source : sources) {
            if (!source.endsWith(".md") || changed.contains(source)) continue
            document(source).accept(new AbstractVisitor() {
                public void visit(Link link) { verify(link.getDestination()); visitChildren(link); }
                public void visit(Image image) { verify(image.getDestination()); visitChildren(image); }
                void verify(String destination) {
                    try {
                        verifyIncoming(source, destination, changed)
                    } catch (URISyntaxException ignored) {
                        // Unrelated unsupported authored URI: no global-link-health claim.
                    } catch (Exception error) { errors.add(error.getMessage()); }
                }
            })
        }
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors))
    }

    static void verify(String repository, String revision, List<String> files) {
        if (!revision.matches("[0-9a-f]{40}") || files.isEmpty())
            throw new IllegalArgumentException("Exact revision and nonempty changed files required")
        ProseDocumentationChecker checker = new ProseDocumentationChecker(repository, revision)
        for (String file : files) checker.check(file)
        checker.inbound(new HashSet<>(files))
        println("Prose checks at " + revision + ": " + files.size() + " files, " + checker.localLinks +
                " local links, " + checker.inboundLinks + " incoming, " + checker.fragments +
                " fragments; " + checker.externalLinks + " external links syntax-only; escaped CommonMark render.")
    }
}
