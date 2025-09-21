package jdiff.doc;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;

final class MethodDocImpl extends ExecutableMemberDocImpl implements MethodDoc {
    private final Type returnType;

    MethodDocImpl(DocEnvironment environment, ExecutableElement element) {
        super(environment, element);
        this.returnType = environment.getType(element.getReturnType());
    }

    @Override
    public Type returnType() {
        return returnType;
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
    public boolean isAbstract() {
        return executableElement().getModifiers().contains(Modifier.ABSTRACT);
    }

    @Override
    public boolean isNative() {
        return executableElement().getModifiers().contains(Modifier.NATIVE);
    }

    @Override
    public boolean isSynchronized() {
        return executableElement().getModifiers().contains(Modifier.SYNCHRONIZED);
    }
}
