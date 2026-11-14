package io.github.hjuergens.compiled;

/**
 * Loads the one class of a compiled rule. Each rule has a loader of its own, so all rules can use the same class name,
 * and a rule's class is unloaded once neither the rule nor its loader is referenced.
 */
final class RuleClassLoader extends ClassLoader {

    RuleClassLoader() {
        super(RuleClassLoader.class.getClassLoader());
    }

    Class<?> define(String name, byte[] bytecode) {
        return defineClass(name, bytecode, 0, bytecode.length);
    }
}
