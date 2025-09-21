package jdiff.doc;

/**
 * Represents documentation for a class, interface, enum or record.
 */
public interface ClassDoc extends ProgramElementDoc, Type, Comparable<ClassDoc> {
    /**
     * Returns the simple name of the class.
     */
    String name();

    boolean isInterface();

    boolean isAbstract();

    PackageDoc containingPackage();

    Type superclassType();

    Type[] interfaceTypes();

    ConstructorDoc[] constructors();

    MethodDoc[] methods();

    FieldDoc[] fields();

    ClassDoc[] innerClasses();
}
