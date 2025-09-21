package jdiff.doc;

public interface ConstructorDoc extends ProgramElementDoc {
    String name();

    Parameter[] parameters();

    Type[] thrownExceptions();
}
