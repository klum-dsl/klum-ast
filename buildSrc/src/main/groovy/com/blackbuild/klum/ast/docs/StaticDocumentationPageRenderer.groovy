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
import java.util.Locale
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
:root { --font-ui: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; --font-code: ui-monospace, "SFMono-Regular", Consolas, monospace; --klum-ink: #041321; --klum-ink-raised: #071b2c; --portal-blue: #8ec8eb; --season-gold: #a78411; --focus: #c96c4a; --page: #f6f0e8; --surface: #fffdf9; --text: #1c2938; --muted: #485360; --line: #d8d5cd; --link: #155d8b; --link-hover: #0f476c; --code: #edf3f6; color-scheme: light; }
* { box-sizing: border-box; }
html { background: var(--page); }
body { margin: 0; color: var(--text); background: var(--page); font: 400 17px/1.65 var(--font-ui); }
a { color: var(--link); text-decoration-thickness: .08em; text-underline-offset: .15em; }
a:hover { color: var(--link-hover); }
a:focus-visible { outline: 3px solid var(--focus); outline-offset: 3px; }
.site-header { color: #f9f6f1; background: linear-gradient(112deg, var(--klum-ink), var(--klum-ink-raised)); border-bottom: 2px solid rgb(142 200 235 / .42); }
.site-header__inner, .layout, .site-footer { width: min(1120px, calc(100% - 2rem)); margin: 0 auto; }
.site-header__inner { display: flex; gap: 1.25rem; align-items: center; justify-content: space-between; min-height: 6.25rem; }
.brand { display: inline-flex; align-items: center; color: inherit; text-decoration: none; }
.brand img { width: 12.75rem; height: 4.25rem; object-fit: contain; }
.version-badge { display: grid; justify-items: end; gap: .05rem; color: #dcebf2; font-size: .84rem; line-height: 1.35; }
.layout { display: grid; grid-template-columns: minmax(13rem, 17rem) minmax(0, 1fr); gap: clamp(1.75rem, 4vw, 3.5rem); align-items: start; padding: clamp(1.75rem, 4vw, 3.5rem) 0 5rem; }
.skip-link { position: absolute; z-index: 2; top: .5rem; left: .5rem; padding: .35rem .65rem; color: var(--klum-ink); background: var(--surface); transform: translateY(-180%); }
.skip-link:focus { transform: translateY(0); }
.sidebar { padding: .25rem 1.25rem .5rem 0; border-right: 1px solid var(--line); font-size: .95rem; }
.sidebar__compact { display: none; }
.sidebar__wide > ul, .sidebar__compact nav > ul { display: grid; gap: .8rem; margin: 0; padding: 0; list-style: none; }
.sidebar__wide > ul > li, .sidebar__compact nav > ul > li { color: var(--muted); font-size: .74rem; font-weight: 750; letter-spacing: .09em; line-height: 1.35; text-transform: uppercase; }
.sidebar__wide > ul > li > a, .sidebar__compact nav > ul > li > a { display: block; padding: .05rem 0; color: var(--muted); text-decoration: none; }
.sidebar__wide > ul > li > ul, .sidebar__compact nav > ul > li > ul { display: grid; gap: .22rem; margin: .4rem 0 0; padding: 0 0 0 .75rem; border-left: 1px solid #d9e2e5; list-style: none; }
.sidebar__wide > ul > li > ul a, .sidebar__compact nav > ul > li > ul a { display: block; padding: .1rem 0; color: var(--muted); font-size: .91rem; font-weight: 400; letter-spacing: normal; line-height: 1.45; text-decoration: none; text-transform: none; }
.sidebar a:hover { color: var(--link); }
.sidebar__wide > p, .sidebar__compact nav > p { margin: 1.2rem 0 0; padding-top: .95rem; border-top: 1px solid var(--line); font-size: .85rem; }
.content { min-width: 0; max-width: 48rem; }
.content h1, .content h2, .content h3 { color: var(--klum-ink); line-height: 1.18; letter-spacing: -.022em; }
.content h1 { margin: 0 0 1.1rem; font-size: clamp(2rem, 4vw, 2.75rem); font-weight: 600; }
.content h2 { margin: 2.5rem 0 .75rem; font-size: 1.52rem; font-weight: 600; }
.content h3 { margin: 1.75rem 0 .5rem; font-size: 1.12rem; font-weight: 700; }
.content h1 code, .content h2 code, .content h3 code { padding: 0; color: #12364c; background: transparent; border-radius: 0; font: 650 1em/1 var(--font-code); }
.content p, .content ul, .content ol { margin: 0 0 1.1rem; }
.content li + li { margin-top: .25rem; }
.content code { padding: .12em .3em; color: #12364c; background: var(--code); border-radius: .22rem; font: .88em/1.3 var(--font-code); }
.content pre { overflow: auto; margin: 1.3rem 0; padding: 1.15rem 1.25rem; color: #173446; background: var(--code); border: 1px solid #d8e1e5; border-radius: .45rem; }
.content pre code { padding: 0; background: transparent; }
.content blockquote { margin: 1.5rem 0; padding: .15rem 1rem; color: var(--muted); border-left: .25rem solid var(--portal-blue); }
.content table { display: block; width: 100%; overflow-x: auto; border-collapse: collapse; }
.content th, .content td { padding: .55rem .65rem; border: 1px solid var(--line); text-align: left; vertical-align: top; }
.content th { color: var(--klum-ink); background: #f5f3ed; }
img { max-width: 100%; height: auto; }
.season-lockup { display: block; margin: 0 0 2rem; border: 1px solid #183447; box-shadow: 0 .5rem 1.5rem rgb(4 19 33 / .14); }
.season-lockup img { display: block; width: 100%; }
.status-banner { margin-bottom: 2rem; padding: .85rem 1rem; color: #4f4217; background: #fff9e9; border: 1px solid #e4d5a7; border-left: .35rem solid var(--season-gold); }
.site-footer { padding: 1.25rem 0 2rem; color: var(--muted); border-top: 1px solid var(--line); font-size: .9rem; }
@media (max-width: 760px) { .site-header__inner { min-height: 5.65rem; } .version-badge { font-size: .76rem; } .layout { grid-template-columns: 1fr; gap: 1.5rem; padding-top: 1.5rem; } .sidebar { padding: 0 0 1rem; border-right: 0; border-bottom: 1px solid var(--line); } .sidebar__wide { display: none; } .sidebar__compact { display: block; } .sidebar__compact summary { cursor: pointer; color: var(--klum-ink); font-size: .78rem; font-weight: 750; letter-spacing: .09em; text-transform: uppercase; } .sidebar__compact[open] summary { margin-bottom: .9rem; } }
@media (max-width: 420px) { body { font-size: 16px; } .site-header__inner, .layout, .site-footer { width: min(calc(100% - 1.25rem), 1120px); } .site-header__inner { min-height: 4.9rem; } .brand img { width: 12.75rem; height: 4.25rem; } .version-badge { display: none; } }
'''.stripIndent().trim() + '\n'

    private static final List<Extension> EXTENSIONS = [TablesExtension.create()].asImmutable()
    private static final Pattern WIKI_LINK = Pattern.compile(/\[\[([^\]]+)]]/)
    private static final Pattern SEASON_LOCKUP_IMAGE = Pattern.compile(/<p><img src="([^"]*klumast-season-4-documentation\.svg)" alt="([^"]*)" title="season-lockup" \/><\/p>/)
    private static final Pattern SEASON_LOCKUP_MARKDOWN = Pattern.compile(/!\[[^\]]*]\(([^\s)]+klumast-season-4-documentation\.svg)\s+"season-lockup"\)/)
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
        String content = renderResponsiveSeasonLockup(htmlRenderer(headingIds).render(document))
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
        String faviconPath = inputs.faviconPath?.toString()
        String favicon = faviconPath
                ? "\n  <link rel=\"icon\" type=\"${escapeAttribute(faviconMediaType(faviconPath))}\" href=\"${escapeAttribute(relativeUrl(outputPath, faviconPath))}\">"
                : ''
        String sidebarContent = (navigation ?: "<p><a href=\"${escapeAttribute(homeLink)}\">Documentation</a></p>") +
                "<p><a href=\"${escapeAttribute(apiLink)}\">API reference</a></p>"

        """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${escapeHtml(title)} — KlumAST ${escapeHtml(version)}</title>
  <link rel="stylesheet" href="${escapeAttribute(cssLink)}">${favicon}
</head>
<body>
  <a class="skip-link" href="#main-content">Skip to main content</a>
  <header class="site-header"><div class="site-header__inner">
    <a class="brand" href="${escapeAttribute(homeLink)}">${logo}</a>
    <span class="version-badge">${escapeHtml(version)} · ${escapeHtml(statusLabel)}</span>
  </div></header>
  <div class="layout">
    <aside class="sidebar">
      <nav class="sidebar__wide" aria-label="Documentation">${sidebarContent}</nav>
      <details class="sidebar__compact">
        <summary>Documentation navigation</summary>
        <nav aria-label="Documentation">${sidebarContent}</nav>
      </details>
    </aside>
    <main id="main-content" class="content" tabindex="-1">
      <aside class="status-banner" data-status="${escapeAttribute(status)}">${escapeHtml(notice)} <a href="${escapeAttribute(statusLink)}">Version status</a>.</aside>
      ${content}
    </main>
  </div>
  <footer class="site-footer">${footer ?: '<p>KlumAST documentation</p>'}</footer>
</body>
</html>
"""
    }

    private static String faviconMediaType(String path) {
        if (path.toLowerCase(Locale.ROOT).endsWith('.svg')) return 'image/svg+xml'
        if (path.toLowerCase(Locale.ROOT).endsWith('.png')) return 'image/png'
        'image/x-icon'
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

    private static String renderResponsiveSeasonLockup(String html) {
        Matcher matcher = SEASON_LOCKUP_IMAGE.matcher(html)
        StringBuffer replaced = new StringBuffer()
        while (matcher.find()) {
            String full = matcher.group(1)
            String compact = compactSeasonLockupPath(full)
            String replacement = """<picture class="season-lockup">
  <source media="(max-width: 1000px)" srcset="${compact}">
  <img src="${full}" alt="${matcher.group(2)}">
</picture>"""
            matcher.appendReplacement(replaced, Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(replaced)
        replaced.toString()
    }

    static Set<String> responsiveSeasonLockupSources(String markdown) {
        Matcher matcher = SEASON_LOCKUP_MARKDOWN.matcher(markdown)
        Set<String> sources = new LinkedHashSet<>()
        while (matcher.find()) sources.add(matcher.group(1))
        sources
    }

    static String compactSeasonLockupPath(String fullPath) {
        fullPath.replaceFirst(/documentation\.svg$/, 'documentation-compact.svg')
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
