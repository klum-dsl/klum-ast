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
package com.blackbuild.klum.ast.doc;

import com.blackbuild.annodocimal.ast.AstDocumentation;
import com.blackbuild.annodocimal.ast.Documentation;
import com.blackbuild.klum.ast.ast.DslAstHelper;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;

import java.util.Map;

/**
 * Utility class for extracting and generation doc comments for generated methods.
 */
public class DocUtil {

    private DocUtil() {
        // Utility class
    }

    /**
     * Returns the getter text for the given field.
     *
     * @param field the field to generate the getter text for
     * @return the getter text
     */
    public static String getGetterText(FieldNode field) {
        return "Returns the " + getDisplayNameOf(field) + ".";
    }

    /**
     * Returns the display name (or 'nice' name) of the given field.
     * The display name is the first sentence of the AnnoDoc value of the field, or the field name if no AnnoDoc is present.
     *
     * @param field the field to get the display name of
     * @return the display name
     */
    public static String getDisplayNameOf(AnnotatedNode field) {
        String sentence = AstDocumentation.extractExact(field)
                .flatMap(Documentation::getSummary)
                .orElse(getName(field));
        // TODO other punctuation?
        if (sentence.charAt(sentence.length() - 1) == '.')
            return sentence.substring(0, sentence.length() - 1);
        return sentence;
    }

    public static Map<String, String> getTemplatesFor(AnnotatedNode field) {
        Map<String, String> result = new java.util.LinkedHashMap<>(AstDocumentation.extractExact(field)
                .map(Documentation::getTemplateValues)
                .orElse(Map.of()));
        AstDocumentation.extractExact(field).ifPresent(documentation -> documentation.getTags().stream()
                .filter(tag -> tag.getName().equals("template"))
                .forEach(tag -> addTemplate(result, tag.getValue())));
        AstDocumentation.extractExact(field).ifPresent(documentation -> documentation.render().lines()
                .filter(line -> line.startsWith("@template "))
                .forEach(line -> addTemplate(result, line.substring("@template ".length()))));
        if (field instanceof FieldNode)
            result.putIfAbsent("singleElementName", getSingleElementDisplayNameOf((FieldNode) field));
        result.putIfAbsent("fieldDisplayName", getDisplayNameOf(field));
        result.putIfAbsent("fieldName", getName(field));
        return result;
    }

    private static void addTemplate(Map<String, String> templates, String value) {
        int separator = value.indexOf(' ');
        if (separator <= 0 || separator == value.length() - 1) return;
        templates.putIfAbsent(value.substring(0, separator), value.substring(separator + 1));
    }

    private static String getName(AnnotatedNode annotatedNode) {
        if (annotatedNode instanceof FieldNode) {
            return ((FieldNode) annotatedNode).getName();
        } else if (annotatedNode instanceof MethodNode) {
            return ((MethodNode) annotatedNode).getName();
        } else if (annotatedNode instanceof ClassNode) {
            return ((ClassNode) annotatedNode).getName();
        } else {
            return "unknown";
        }
    }

    /**
     * Returns the setter text for the given field.
     *
     * @param field the field to generate the setter text for
     * @return the setter text
     */
    public static String getSetterText(FieldNode field) {
        return "Sets the " + getDisplayNameOf(field) + ".";
    }

    /**
     * Return the javaodc text for a flag setter (i.e. a method that sets a boolean value to true).
     * @param field the field to generate the setter text for
     * @return the setter text
     */
    public static String getFlagSetterText(FieldNode field) {
        return "Sets the flag " + getDisplayNameOf(field) + " to 'true'.";
    }

    /**
     * Returns the adder text for the given field.
     *
     * @param field the field to generate the adder text for
     * @return the adder text
     */
    public static String getCollectionAdderText(FieldNode field) {
        return "Adds a(n) " + getSingleElementDisplayNameOf(field) + ".";
    }

    /**
     * Returns the name of a single element of the collection field.
     *
     * @param field the field to get the element name of
     * @return the element name
     * @see DslAstHelper#getElementNameForCollectionField(FieldNode)
     */
    public static String getSingleElementDisplayNameOf(FieldNode field) {
        return DslAstHelper.getElementNameForCollectionField(field);
    }

    public static String getCollectionMultiAdderText(FieldNode field) {
        return "Adds one or more " + getDisplayNameOf(field) + ".";
    }
}
