package jdiff.doc;

import javax.lang.model.element.ExecutableElement;

final class ConstructorDocImpl extends ExecutableMemberDocImpl implements ConstructorDoc {
    private final ClassDocImpl owner;

    ConstructorDocImpl(DocEnvironment environment, ExecutableElement element, ClassDocImpl owner) {
        super(environment, element);
        this.owner = owner;
    }

    @Override
    public Parameter[] parameters() {
        return super.parameters();
    }

    @Override
    public Type[] thrownExceptions() {
        return super.thrownExceptions();
    }

    @Override
    public String name() {
        return owner.name();
    }
}
