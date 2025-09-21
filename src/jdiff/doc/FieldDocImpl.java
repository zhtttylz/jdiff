package jdiff.doc;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;

final class FieldDocImpl extends BaseProgramElementDoc implements FieldDoc {
    private final VariableElement element;
    private final Type type;

    FieldDocImpl(DocEnvironment environment, Element element) {
        super(environment, element);
        this.element = (VariableElement) element;
        this.type = environment.getType(this.element.asType());
    }

    @Override
    public String name() {
        return element.getSimpleName().toString();
    }

    @Override
    public Type type() {
        return type;
    }

    @Override
    public boolean isTransient() {
        return element.getModifiers().contains(Modifier.TRANSIENT);
    }

    @Override
    public boolean isVolatile() {
        return element.getModifiers().contains(Modifier.VOLATILE);
    }
}
