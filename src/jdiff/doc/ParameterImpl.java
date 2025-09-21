package jdiff.doc;

import javax.lang.model.element.VariableElement;

final class ParameterImpl implements Parameter {
    private final String name;
    private final Type type;

    ParameterImpl(DocEnvironment environment, VariableElement element) {
        this.name = element.getSimpleName().toString();
        this.type = environment.getType(element.asType());
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Type type() {
        return type;
    }
}
