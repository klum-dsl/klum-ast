package com.blackbuild.klum.ast.util;

import groovy.lang.GroovyObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Helper methods fro use in convenience factories. This will eventually take a lot of code from
 * the AST generated methods.
 */
public class FactoryHelper {

    public static final String MODEL_CLASS_KEY = "model-class";

    private FactoryHelper() {
        // static only
    }

    public static <T extends GroovyObject> T createFromClasspath(Class<T> type) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        String path = "META-INF/klum-model/" + type.getName() + ".properties";

        try (InputStream stream = loader.getResourceAsStream(path)) {
            assertResourceExists(path, stream);

            String configModelClassName = readModelClass(path, stream);

            return createModelFrom(type, loader, path, configModelClassName);

        } catch (IOException e) {
            throw new IllegalStateException("Error while reading marker properties.", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends GroovyObject> T createModelFrom(Class<T> type, ClassLoader loader, String path, String configModelClassName) {
        try {
            Class<T> modelClass = (Class<T>) loader.loadClass(configModelClassName);
            return (T) type.getMethod("createFrom", Class.class).invoke(null, modelClass);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class '" + configModelClassName + "' defined in " + path + " does not exist", e);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read model from " + configModelClassName);
        }
    }

    private static void assertResourceExists(String path, InputStream stream) {
        if (stream == null)
            throw new IllegalStateException("File " + path + " not found in classpath.");
    }

    private static String readModelClass(String path, InputStream stream) throws IOException {
        Properties marker = new Properties();
        marker.load(stream);
        String configModelClassName = marker.getProperty(MODEL_CLASS_KEY);
        if (configModelClassName == null)
            throw new IllegalStateException("No entry 'model-class' found in " + path);
        return configModelClassName;
    }

}
