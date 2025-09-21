package jdiff.doc;

/**
 * Common operations for documented program elements (types, fields, methods, etc.).
 */
public interface ProgramElementDoc extends Doc {
    boolean isStatic();

    boolean isFinal();

    boolean isPublic();

    boolean isProtected();

    boolean isPackagePrivate();

    boolean isPrivate();
}
