package jdiff.doc;

public interface MethodDoc extends ProgramElementDoc {
    String name();

    Type returnType();

    Parameter[] parameters();

    Type[] thrownExceptions();

    boolean isAbstract();

    boolean isNative();

    boolean isSynchronized();
}
