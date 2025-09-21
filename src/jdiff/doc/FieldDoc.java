package jdiff.doc;

public interface FieldDoc extends ProgramElementDoc {
    String name();

    Type type();

    boolean isTransient();

    boolean isVolatile();
}
