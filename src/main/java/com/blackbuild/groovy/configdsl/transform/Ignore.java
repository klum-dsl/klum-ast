package com.blackbuild.groovy.configdsl.transform;

import java.lang.annotation.*;

/**
 * Designates a field to be completely ignored by KlumAST. Any field marked with this annotation will not have
 * any created setter / adder methods. It will also not be part of any implicit validation.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface Ignore {
}
