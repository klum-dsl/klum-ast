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
package com.blackbuild.klum.ast.gradle

import org.gradle.api.GradleException
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir

import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Issue('805')
class KlumGdslArchiveTest extends Specification {
    @TempDir File directory
    static final String ORIGIN = 'org.example:environment-schema:1.0'
    static final String ENVELOPE = KlumGdslMetadataFormat.ENVELOPE

    def "validates the canonical producer envelope and preserves empty metadata"() {
        given:
        File full = archive(entries())
        File empty = archive([(ENVELOPE + 'mappings.json'): '{"mappings":[]}\n'], 'empty.jar')

        when:
        def validated = KlumGdslArchive.read(full, ORIGIN, [ORIGIN])
        def retired = KlumGdslArchive.read(empty, ORIGIN, [ORIGIN])

        then:
        validated.mappings()*.modelType() == ['example.Environment']
        validated.destination() == 'schema-owned/6f72672e6578616d706c65/656e7669726f6e6d656e742d736368656d61/'
        retired.mappings().empty
    }

    def "rejects #defect metadata before extraction"() {
        given:
        def content = entries()
        switch (defect) {
            case 'template': content[ENVELOPE + 'environment.gdsl'] += 'println "injected"'; break
            case 'hash': content[ENVELOPE + 'mappings.json'] = content[ENVELOPE + 'mappings.json'].replace('payloadSha256', 'wrongHash'); break
            case 'json': content[ENVELOPE + 'mappings.json'] = '{'; break
            case 'catalog': content[ENVELOPE + 'mappings.json'] = '{"mappings": [{}]}'; break
            case 'missing': content.remove(ENVELOPE + 'environment.gdsl'); break
            case 'extra': content['other.gdsl'] = 'untrusted'; break
            case 'traversal': content['../escaped.gdsl'] = 'untrusted'; break
            case 'absolute': content['/escaped.gdsl'] = 'untrusted'; break
            case 'format': break
            case 'origin': break
            case 'normal': break
        }
        File file = archive(content, 'invalid.jar', defect == 'format' ? '2' : '1', defect == 'origin' ? 'other:schema:1.0' : ORIGIN)

        when:
        KlumGdslArchive.read(file, ORIGIN, defect == 'normal' ? ['org.example:environment-schema:2.0'] : [ORIGIN])

        then:
        GradleException error = thrown()
        error.message.contains(reason)
        error.message.contains('previous IDE output is stale')

        where:
        defect      | reason
        'template'  | 'payload hash/template mismatch'
        'hash'      | 'noncanonical catalog'
        'catalog'   | 'missing string field'
        'json'      | 'Invalid GDSL metadata'
        'missing'   | 'Missing GDSL entry'
        'extra'     | 'unexpected ZIP entry'
        'traversal' | 'unsafe ZIP entry'
        'absolute'  | 'unsafe ZIP entry'
        'format'    | 'unsupported GDSL format'
        'origin'    | 'Schema origin mismatch'
        'normal'    | 'does not match the selected normal Schema'
    }

    def "missing manifest cannot disguise an ordinary library as metadata"() {
        given:
        File file = new File(directory, 'ordinary.jar')
        new ZipOutputStream(file.newOutputStream()).withCloseable { output ->
            output.putNextEntry(new ZipEntry('environment.gdsl'))
            output.write('untrusted'.bytes)
            output.closeEntry()
        }

        when:
        KlumGdslArchive.read(file, ORIGIN, [ORIGIN])

        then:
        GradleException error = thrown()
        error.message.contains('missing metadata manifest')
    }

    def "rejects duplicate ZIP names even when their payload bytes are equal"() {
        given:
        File file = archive(entries() + ['fake.gdsl': 'same', 'dupe.gdsl': 'same'])
        // ZIP filenames do not affect payload CRCs; equal-length replacement makes a valid duplicate-entry archive.
        file.bytes = new String(file.bytes, 'ISO-8859-1').replace('fake.gdsl', 'dupe.gdsl').getBytes('ISO-8859-1')

        when:
        KlumGdslArchive.read(file, ORIGIN, [ORIGIN])

        then:
        GradleException error = thrown()
        error.message.contains('duplicate or unsafe ZIP entry dupe.gdsl')
    }

    private static Map<String, String> entries() {
        def mapping = new KlumGdslMetadataFormat.Mapping('environment', '.environment.groovy', 'example.Environment')
        [(ENVELOPE + 'mappings.json'): KlumGdslMetadataFormat.catalog([mapping]),
         (ENVELOPE + 'environment.gdsl'): KlumGdslMetadataFormat.payload(mapping)]
    }

    private File archive(Map<String, String> entries, String name = 'metadata.jar', String format = '1', String origin = ORIGIN) {
        File file = new File(directory, name)
        new ZipOutputStream(file.newOutputStream()).withCloseable { output ->
            Manifest manifest = new Manifest()
            manifest.mainAttributes.put(Attributes.Name.MANIFEST_VERSION, '1.0')
            manifest.mainAttributes.putValue('Klum-Gdsl-Format', format)
            manifest.mainAttributes.putValue('Klum-Schema-Coordinates', origin)
            output.putNextEntry(new ZipEntry('META-INF/MANIFEST.MF'))
            manifest.write(output)
            output.closeEntry()
            entries.each { path, content ->
                output.putNextEntry(new ZipEntry(path))
                output.write(content.getBytes('UTF-8'))
                output.closeEntry()
            }
        }
        file
    }
}
