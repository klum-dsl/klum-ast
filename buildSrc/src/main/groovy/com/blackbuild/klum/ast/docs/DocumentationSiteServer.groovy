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

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.gradle.api.GradleException

import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Matcher
import java.util.regex.Pattern

/** Serves and crawls a rendered site using only the JDK runtime. */
class DocumentationSiteServer implements Closeable {

    private static final Pattern REFERENCES = Pattern.compile(/(?i)(?:href|src)\s*=\s*["']([^"']+)["']/)
    private static final Pattern ANCHORS = Pattern.compile(/(?i)(?:id|name)\s*=\s*["']([^"']+)["']/)

    private final File root
    private final String basePath
    private final HttpServer server

    DocumentationSiteServer(File root, String basePath = '/klum-ast/') {
        if (!root.directory) throw new GradleException("Rendered documentation directory does not exist: $root")
        this.root = root.canonicalFile
        this.basePath = normalizeBasePath(basePath)
        server = HttpServer.create(new InetSocketAddress(InetAddress.loopbackAddress, 0), 0)
        server.createContext(this.basePath) { HttpExchange exchange -> handle(exchange) }
        server.executor = Executors.newCachedThreadPool()
    }

    URI start() {
        server.start()
        new URI("http", null, InetAddress.loopbackAddress.hostAddress, server.address.port, basePath, null, null)
    }

    void verify() {
        URI rootUri = start()
        Set<URI> pending = new LinkedHashSet<>([rootUri])
        Set<URI> visited = new LinkedHashSet<>()
        Map<URI, Set<String>> fragments = [:].withDefault { new LinkedHashSet<>() }
        while (!pending.empty) {
            URI requested = pending.iterator().next()
            pending.remove(requested)
            URI document = withoutFragment(requested)
            if (!visited.add(document)) {
                if (requested.fragment) fragments[document] << requested.fragment
                continue
            }
            HttpURLConnection connection = document.toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            int status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK)
                fail("Rendered documentation link returned HTTP $status: $document")
            byte[] bytes = connection.inputStream.bytes
            String contentType = connection.contentType ?: ''
            if (contentType.startsWith('text/html')) {
                String html = new String(bytes, StandardCharsets.UTF_8)
                Matcher matcher = REFERENCES.matcher(html)
                while (matcher.find()) {
                    String reference = matcher.group(1)
                    if (!reference || reference.startsWith('mailto:') || reference.startsWith('tel:')) continue
                    URI resolved = document.resolve(reference)
                    if (resolved.scheme in ['http', 'https'] && resolved.host == rootUri.host && resolved.port == rootUri.port) {
                        if (!resolved.path.startsWith(basePath)) fail("Internal link escapes the Pages base path: $reference from $document")
                        if (resolved.path.endsWith('.md')) fail("Rendered site retains a Markdown URL: $reference from $document")
                        pending << resolved
                        if (resolved.fragment) fragments[withoutFragment(resolved)] << resolved.fragment
                    }
                }
                fragments[document].each { String fragment ->
                    Set<String> anchors = []
                    Matcher anchorMatcher = ANCHORS.matcher(html)
                    while (anchorMatcher.find())
                        anchors << URLDecoder.decode(decodeHtmlAttribute(anchorMatcher.group(1)), StandardCharsets.UTF_8)
                    if (!anchors.contains(URLDecoder.decode(fragment, StandardCharsets.UTF_8)))
                        fail("Rendered documentation fragment is absent: $document#$fragment")
                }
            }
            connection.disconnect()
        }
    }

    @Override
    void close() {
        server.stop(0)
        if (server.executor instanceof ExecutorService) (server.executor as ExecutorService).shutdownNow()
    }

    private void handle(HttpExchange exchange) {
        try {
            if (!(exchange.requestMethod in ['GET', 'HEAD'])) {
                exchange.sendResponseHeaders(405, -1)
                return
            }
            String relative = URLDecoder.decode(exchange.requestURI.path.substring(basePath.length()), StandardCharsets.UTF_8)
            File target = new File(root, relative).canonicalFile
            if (!target.path.startsWith(root.path + File.separator) && target != root) {
                exchange.sendResponseHeaders(403, -1)
                return
            }
            if (target.directory) target = new File(target, 'index.html')
            if (!target.file) {
                exchange.sendResponseHeaders(404, -1)
                return
            }
            byte[] bytes = target.bytes
            exchange.responseHeaders.set('Content-Type', contentType(target.name))
            exchange.sendResponseHeaders(200, exchange.requestMethod == 'HEAD' ? -1 : bytes.length)
            if (exchange.requestMethod == 'GET') exchange.responseBody.write(bytes)
        } finally {
            exchange.close()
        }
    }

    private static URI withoutFragment(URI uri) {
        new URI(uri.scheme, uri.authority, uri.path, uri.query, null)
    }

    private static String normalizeBasePath(String path) {
        String result = path.startsWith('/') ? path : "/$path"
        result.endsWith('/') ? result : "$result/"
    }

    private static String contentType(String name) {
        if (name.endsWith('.html')) return 'text/html; charset=utf-8'
        if (name.endsWith('.css')) return 'text/css; charset=utf-8'
        if (name.endsWith('.json')) return 'application/json; charset=utf-8'
        if (name.endsWith('.svg')) return 'image/svg+xml'
        if (name.endsWith('.png')) return 'image/png'
        if (name.endsWith('.jpg') || name.endsWith('.jpeg')) return 'image/jpeg'
        'application/octet-stream'
    }

    private static String decodeHtmlAttribute(String value) {
        String decoded = value.replaceAll(/&#x([0-9a-fA-F]+);/) { String ignored, String hex ->
            new String(Character.toChars(Integer.parseInt(hex, 16)))
        }.replaceAll(/&#([0-9]+);/) { String ignored, String decimal ->
            new String(Character.toChars(Integer.parseInt(decimal, 10)))
        }
        decoded.replace('&lt;', '<').replace('&gt;', '>').replace('&quot;', '"')
                .replace('&#39;', "'").replace('&amp;', '&')
    }

    private static void fail(String message) {
        throw new GradleException(message)
    }
}
