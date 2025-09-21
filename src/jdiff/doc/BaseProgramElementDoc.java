package jdiff.doc;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;

abstract class BaseProgramElementDoc extends BaseDoc implements ProgramElementDoc {
    private final Element element;

    BaseProgramElementDoc(DocEnvironment environment, Element element) {
        super(environment, element, null);
        this.element = element;
    }

    Element asElement() {
        return element;
    }

    @Override
    public boolean isStatic() {
        return element.getModifiers().contains(Modifier.STATIC);
    }

    @Override
    public boolean isFinal() {
        return element.getModifiers().contains(Modifier.FINAL);
    }

    @Override
    public boolean isPublic() {
        return element.getModifiers().contains(Modifier.PUBLIC);
    }

    @Override
    public boolean isProtected() {
        return element.getModifiers().contains(Modifier.PROTECTED);
    }

    @Override
    public boolean isPrivate() {
        return element.getModifiers().contains(Modifier.PRIVATE);
    }

    @Override
    public boolean isPackagePrivate() {
        return !(isPublic() || isProtected() || isPrivate());
    }
}
