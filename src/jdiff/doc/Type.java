package jdiff.doc;

/**
 * Representation of a Java type used by the doclet model.
 */
public interface Type {
    /**
     * Returns the fully qualified name of the (erased) type.
     */
    String qualifiedTypeName();

    /**
     * Returns a display form of the type, potentially including generics information.
     */
    @Override
    String toString();
}
