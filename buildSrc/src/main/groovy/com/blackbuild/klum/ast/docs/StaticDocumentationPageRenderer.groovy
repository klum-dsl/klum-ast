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

import org.commonmark.Extension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.AttributeProvider
import org.commonmark.renderer.html.AttributeProviderContext
import org.commonmark.renderer.html.AttributeProviderFactory
import org.commonmark.renderer.html.HtmlRenderer

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import java.text.Normalizer
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Converts authored Markdown to a complete, dependency-free static HTML page.
 *
 * The public interface is intentionally expressed in source and output paths:
 * callers own version/release policy while this class owns Markdown semantics,
 * link rewriting, heading identifiers, and renderer chrome.
 */
class StaticDocumentationPageRenderer {

    static final String CONTRACT_ID = 'commonmark-java-static-html-v1'
    static final String COMMONMARK_VERSION = '0.28.0'
    static final String SITE_CSS = '''
:root { color-scheme: light dark; --bg: #fff; --fg: #202124; --muted: #5f6368; --line: #d7dce1; --accent: #5b2ca0; --code: #f3f4f6; }
@media (prefers-color-scheme: dark) { :root { --bg: #17181a; --fg: #eceff1; --muted: #b0b6bc; --line: #42464b; --accent: #c7a6ff; --code: #25282c; } }
* { box-sizing: border-box; }
body { margin: 0; color: var(--fg); background: var(--bg); font: 16px/1.6 system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
a { color: var(--accent); }
.site-header { border-bottom: 1px solid var(--line); }
.site-header__inner, .layout, .site-footer { width: min(1120px, calc(100% - 2rem)); margin: 0 auto; }
.site-header__inner { display: flex; gap: 1rem; align-items: center; justify-content: space-between; padding: .8rem 0; }
.brand { display: inline-flex; gap: .65rem; align-items: center; color: var(--fg); font-weight: 700; text-decoration: none; }
.brand img { width: 2rem; height: 2rem; object-fit: contain; }
.version-badge { color: var(--muted); font-size: .9rem; }
.layout { display: grid; grid-template-columns: minmax(13rem, 18rem) minmax(0, 1fr); gap: 2rem; padding: 1.5rem 0 3rem; }
.sidebar { border-right: 1px solid var(--line); padding-right: 1rem; }
.sidebar ul { padding-left: 1.2rem; }
.content { min-width: 0; }
.status-banner { border: 1px solid var(--line); border-left: .35rem solid var(--accent); padding: .75rem 1rem; margin-bottom: 1.5rem; }
pre, code { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; }
code { background: var(--code); padding: .1em .25em; border-radius: .2rem; }
pre { overflow: auto; padding: 1rem; background: var(--code); border-radius: .3rem; }
pre code { padding: 0; }
table { border-collapse: collapse; display: block; overflow-x: auto; }
th, td { border: 1px solid var(--line); padding: .4rem .65rem; text-align: left; }
img { max-width: 100%; height: auto; }
.site-footer { border-top: 1px solid var(--line); padding: 1rem 0 2rem; color: var(--muted); }
@media (max-width: 760px) { .layout { grid-template-columns: 1fr; } .sidebar { border-right: 0; border-bottom: 1px solid var(--line); padding: 0 0 1rem; } }
'''.stripIndent().trim() + '\n'

    private static final List<Extension> EXTENSIONS = [TablesExtension.create()].asImmutable()
    private static final Pattern WIKI_LINK = Pattern.compile(/\[\[([^\]]+)]]/)
    private static final String WIKI_ROOT = '.klum-wiki-root/'
    private static final Set<String> IMAGE_EXTENSIONS = ['png', 'jpg', 'jpeg', 'gif', 'svg', 'webp'] as Set

    static String render(Map<String, ?> inputs) {
        String markdown = inputs.markdown?.toString() ?: ''
        String sourcePath = inputs.sourcePath.toString()
        String outputPath = inputs.outputPath.toString()
        Map<String, String> pageOutputs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
        pageOutputs.putAll(inputs.pageOutputs as Map<String, String>)
        Map<String, String> wikiPages = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
        wikiPages.putAll(inputs.wikiPages as Map<String, String>)
        String navigationMarkdown = inputs.navigationMarkdown?.toString() ?: ''
        String footerMarkdown = inputs.footerMarkdown?.toString() ?: ''
        String repositoryRevision = inputs.repositoryRevision?.toString()
        String repositorySourcePath = inputs.repositorySourcePath?.toString()
        String authoringRoot = inputs.authoringRoot?.toString()

        String prepared = expandWikiLinks(markdown, wikiPages)
        Node document = parser().parse(prepared)
        Map<Node, String> headingIds = assignHeadingIds(document)
        rewriteLinks(document, sourcePath, outputPath, pageOutputs, repositoryRevision, repositorySourcePath, authoringRoot)
        String content = htmlRenderer(headingIds).render(document)
        String navigation = renderFragment(navigationMarkdown, '_Sidebar.md', outputPath, pageOutputs, wikiPages)
        String footer = renderFragment(footerMarkdown, '_Footer.md', outputPath, pageOutputs, wikiPages)

        String title = inputs.title?.toString() ?: firstHeading(document) ?: 'KlumAST documentation'
        String version = inputs.version.toString()
        String status = inputs.status.toString()
        String statusLabel = inputs.statusLabel.toString()
        String notice = inputs.notice.toString()
        String homeLink = relativeUrl(outputPath, 'index.html')
        String apiLink = relativeUrl(outputPath, 'api/index.html')
        String statusLink = relativeUrl(outputPath, 'status/index.html')
        String cssLink = relativeUrl(outputPath, 'assets/site.css')
        String logoPath = inputs.logoPath?.toString()
        String logo = logoPath ? "<img src=\"${escapeAttribute(relativeUrl(outputPath, logoPath))}\" alt=\"${escapeAttribute(inputs.logoAltText?.toString() ?: 'KlumAST')}\">" : ''

        """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${escapeHtml(title)} — KlumAST ${escapeHtml(version)}</title>
  <link rel="stylesheet" href="${escapeAttribute(cssLink)}">
</head>
<body>
  <header class="site-header"><div class="site-header__inner">
    <a class="brand" href="${escapeAttribute(homeLink)}">${logo}<span>KlumAST</span></a>
    <span class="version-badge">${escapeHtml(version)} · ${escapeHtml(statusLabel)}</span>
  </div></header>
  <div class="layout">
    <nav class="sidebar" aria-label="Documentation">${navigation ?: "<p><a href=\"${escapeAttribute(homeLink)}\">Documentation</a></p>"}<p><a href="${escapeAttribute(apiLink)}">API reference</a></p></nav>
    <main class="content">
      <aside class="status-banner" data-status="${escapeAttribute(status)}">${escapeHtml(notice)} <a href="${escapeAttribute(statusLink)}">Version status</a>.</aside>
      ${content}
    </main>
  </div>
  <footer class="site-footer">${footer ?: '<p>KlumAST documentation</p>'}</footer>
</body>
</html>
"""
    }

    static String pageOutputPath(String sourcePath, String landingSourcePath) {
        if (sourcePath == landingSourcePath) return 'index.html'
        String withoutExtension = sourcePath.substring(0, sourcePath.length() - '.md'.length())
        "$withoutExtension/index.html"
    }

    static String wikiKey(String sourcePath) {
        String name = sourcePath.tokenize('/').last().replaceFirst(/(?i)\.md$/, '')
        normalizeWikiName(name)
    }

    private static Parser parser() {
        Parser.builder().extensions(EXTENSIONS).build()
    }

    private static HtmlRenderer htmlRenderer(Map<Node, String> headingIds) {
        HtmlRenderer.builder()
                .extensions(EXTENSIONS)
                .escapeHtml(true)
                .sanitizeUrls(true)
                .attributeProviderFactory(new HeadingIdAttributeProviderFactory(headingIds))
                .build()
    }

    private static String renderFragment(String markdown, String sourcePath, String outputPath,
                                         Map<String, String> pageOutputs, Map<String, String> wikiPages) {
        if (!markdown) return ''
        Node fragment = parser().parse(expandWikiLinks(markdown, wikiPages))
        Map<Node, String> headingIds = assignHeadingIds(fragment)
        rewriteLinks(fragment, sourcePath, outputPath, pageOutputs, null, null, null)
        htmlRenderer(headingIds).render(fragment)
    }

    private static String expandWikiLinks(String markdown, Map<String, String> wikiPages) {
        Matcher matcher = WIKI_LINK.matcher(markdown)
        StringBuffer expanded = new StringBuffer()
        while (matcher.find()) {
            String expression = matcher.group(1).trim()
            String label = expression
            String destination = expression
            int separator = expression.indexOf('|')
            if (separator >= 0) {
                destination = expression.substring(0, separator).trim()
                label = expression.substring(separator + 1).trim()
            }
            String fragment = ''
            int hash = destination.indexOf('#')
            if (hash >= 0) {
                fragment = '#' + slug(destination.substring(hash + 1))
                destination = destination.substring(0, hash)
            }
            String replacement
            String extension = destination.contains('.') ? destination.substring(destination.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : ''
            if (IMAGE_EXTENSIONS.contains(extension)) {
                replacement = "![${label}](${WIKI_ROOT}${destination}${fragment})"
            } else {
                String resolved = wikiPages[normalizeWikiName(destination)] ?: destination.replace(' ', '-') + '.md'
                replacement = "[${label}](${WIKI_ROOT}${resolved}${fragment})"
            }
            matcher.appendReplacement(expanded, Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(expanded)
        expanded.toString()
    }

    private static Map<Node, String> assignHeadingIds(Node document) {
        Map<Node, String> ids = new IdentityHashMap<>()
        Map<String, Integer> occurrences = [:].withDefault { 0 }
        document.accept(new AbstractVisitor() {
            @Override
            void visit(Heading heading) {
                String base = slug(plainText(heading)) ?: 'section'
                int count = occurrences[base]++
                ids[heading] = (count ? "$base-$count" : base).toString()
                visitChildren(heading)
            }
        })
        ids
    }

    private static void rewriteLinks(Node document, String sourcePath, String outputPath, Map<String, String> pageOutputs,
                                     String repositoryRevision, String repositorySourcePath, String authoringRoot) {
        document.accept(new AbstractVisitor() {
            @Override
            void visit(Link link) {
                link.destination = rewriteDestination(link.destination, sourcePath, outputPath, pageOutputs,
                        repositoryRevision, repositorySourcePath, authoringRoot)
                visitChildren(link)
            }

            @Override
            void visit(Image image) {
                image.destination = rewriteDestination(image.destination, sourcePath, outputPath, pageOutputs,
                        repositoryRevision, repositorySourcePath, authoringRoot)
                visitChildren(image)
            }
        })
    }

    private static String rewriteDestination(String destination, String sourcePath, String outputPath,
                                             Map<String, String> pageOutputs, String repositoryRevision,
                                             String repositorySourcePath, String authoringRoot) {
        if (!destination) return destination
        if (destination.startsWith('#')) return normalizeLocalFragment(destination)
        if (destination.startsWith('//') || destination ==~ /(?i)[a-z][a-z0-9+.-]*:.*/)
            return destination
        int suffixAt = [destination.indexOf('?'), destination.indexOf('#')].findAll { it >= 0 }.min() ?: -1
        String path = suffixAt >= 0 ? destination.substring(0, suffixAt) : destination
        String suffix = suffixAt >= 0 ? destination.substring(suffixAt) : ''
        if (!path) return destination
        boolean wikiRooted = path.startsWith(WIKI_ROOT)
        if (wikiRooted) path = path.substring(WIKI_ROOT.length())
        Path sourceParent = wikiRooted ? Paths.get('') : (Paths.get(sourcePath).parent ?: Paths.get(''))
        Path resolved = sourceParent.resolve(path).normalize()
        String sourceTarget = resolved.toString().replace('\\', '/')
        String outputTarget = pageOutputs[sourceTarget]
        if (!outputTarget && !sourceTarget.toLowerCase(Locale.ROOT).endsWith('.md'))
            outputTarget = pageOutputs[sourceTarget + '.md']

        if ((!outputTarget || resolved.startsWith('..')) && repositoryRevision && repositorySourcePath && authoringRoot) {
            Path repositoryParent = wikiRooted ? Paths.get(authoringRoot) : (Paths.get(repositorySourcePath).parent ?: Paths.get(''))
            Path repositoryResolved = repositoryParent.resolve(path).normalize()
            Path authoringRootPath = Paths.get(authoringRoot)
            if (repositoryResolved.startsWith(authoringRootPath)) {
                sourceTarget = authoringRootPath.relativize(repositoryResolved).toString().replace('\\', '/')
                outputTarget = pageOutputs[sourceTarget]
                if (!outputTarget && !sourceTarget.toLowerCase(Locale.ROOT).endsWith('.md'))
                    outputTarget = pageOutputs[sourceTarget + '.md']
            } else {
                String repositoryPath = repositoryResolved.toString().replace('\\', '/')
                if (!repositoryPath || repositoryPath.startsWith('../')) return destination
                String kind = repositoryPath.toLowerCase(Locale.ROOT).endsWith('.md') ? 'blob' : 'tree'
                return "https://github.com/klum-dsl/klum-ast/$kind/$repositoryRevision/$repositoryPath${normalizeLocalFragment(suffix)}"
            }
        }
        if (resolved.startsWith('..') && !outputTarget) return destination
        String rewritten = relativeUrl(outputPath, outputTarget ?: sourceTarget)
        if (path.endsWith('/') && !rewritten.endsWith('/')) rewritten += '/'
        rewritten + normalizeLocalFragment(suffix)
    }

    private static String normalizeLocalFragment(String suffix) {
        int hash = suffix.indexOf('#')
        if (hash < 0 || hash == suffix.length() - 1) return suffix
        String encoded = suffix.substring(hash + 1).replace('+', '%2B')
        String fragment = URLDecoder.decode(encoded, StandardCharsets.UTF_8)
        suffix.substring(0, hash + 1) + slug(fragment)
    }

    static String relativeUrl(String fromOutputPath, String targetOutputPath) {
        Path fromDirectory = Paths.get(fromOutputPath).parent ?: Paths.get('')
        Path target = Paths.get(targetOutputPath)
        Path relative = fromDirectory.relativize(target)
        String result = relative.toString().replace('\\', '/')
        if (targetOutputPath.endsWith('/index.html')) {
            result = result.substring(0, result.length() - 'index.html'.length())
        } else if (targetOutputPath == 'index.html') {
            result = result == 'index.html' ? './' : result.substring(0, result.length() - 'index.html'.length())
        }
        result ?: './'
    }

    private static String firstHeading(Node document) {
        Node current = document.firstChild
        while (current) {
            if (current instanceof Heading) return plainText(current)
            current = current.next
        }
        null
    }

    private static String plainText(Node node) {
        StringBuilder value = new StringBuilder()
        Node child = node.firstChild
        while (child) {
            if (child instanceof Text) value.append(child.literal)
            else if (child instanceof Code) value.append(child.literal)
            else value.append(plainText(child))
            child = child.next
        }
        value.toString()
    }

    static String slug(String value) {
        String normalized = Normalizer.normalize(value ?: '', Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
        normalized.replaceAll(/[^\p{L}\p{N}\p{M}_\- ]/, '').replaceAll(/\s+/, '-').replaceAll(/-+/, '-').replaceAll(/^-|-$/, '')
    }

    static boolean containsAuthoredHtml(String markdown) {
        !authoredHtmlLiterals(markdown).empty
    }

    static List<String> authoredHtmlLiterals(String markdown) {
        List<String> found = []
        parser().parse(markdown ?: '').accept(new AbstractVisitor() {
            @Override
            void visit(HtmlBlock htmlBlock) {
                found << htmlBlock.literal
            }

            @Override
            void visit(HtmlInline htmlInline) {
                found << htmlInline.literal
            }
        })
        found
    }

    private static String normalizeWikiName(String value) {
        (value ?: '').replaceFirst(/(?i)\.md$/, '').replaceAll(/[^\p{L}\p{N}]/, '').toLowerCase(Locale.ROOT)
    }

    private static String escapeHtml(String value) {
        escapeAttribute(value).replace("'", '&#39;')
    }

    private static String escapeAttribute(String value) {
        (value ?: '').replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;')
    }

    private static class HeadingIdAttributeProviderFactory implements AttributeProviderFactory {
        private final Map<Node, String> ids

        HeadingIdAttributeProviderFactory(Map<Node, String> ids) {
            this.ids = ids
        }

        @Override
        AttributeProvider create(AttributeProviderContext context) {
            { Node node, String tagName, Map<String, String> attributes ->
                if (ids.containsKey(node)) attributes.id = ids[node]
            } as AttributeProvider
        }
    }
}
