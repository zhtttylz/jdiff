package jdiff.doc;

/**
 * Represents documentation for a Java package.
 */
public interface PackageDoc extends Doc {
    /**
     * Returns the package name.
     */
    String name();

    /**
     * Returns all classes contained in the package that are part of the documentation set.
     */
    ClassDoc[] allClasses();
}
