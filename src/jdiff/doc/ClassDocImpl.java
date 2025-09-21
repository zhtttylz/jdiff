package jdiff.doc;

import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

final class ClassDocImpl extends BaseProgramElementDoc implements ClassDoc {
    private final DocEnvironment environment;
    private final TypeElement element;
    private final PackageDocImpl packageDoc;

    private ConstructorDoc[] constructors;
    private MethodDoc[] methods;
    private FieldDoc[] fields;
    private Type[] interfaces;
    private ClassDoc[] innerClasses;

    ClassDocImpl(DocEnvironment environment, TypeElement element, PackageDocImpl packageDoc) {
        super(environment, element);
        this.environment = environment;
        this.element = element;
        this.packageDoc = packageDoc;
    }

    @Override
    public String name() {
        return element.getSimpleName().toString();
    }

    @Override
    public String qualifiedTypeName() {
        return element.getQualifiedName().toString();
    }

    @Override
    public String toString() {
        return qualifiedTypeName();
    }

    @Override
    public boolean isInterface() {
        ElementKind kind = element.getKind();
        return kind == ElementKind.INTERFACE || kind == ElementKind.ANNOTATION_TYPE;
    }

    @Override
    public boolean isAbstract() {
        return element.getModifiers().contains(Modifier.ABSTRACT) || isInterface();
    }

    @Override
    public PackageDoc containingPackage() {
        return packageDoc;
    }

    @Override
    public Type superclassType() {
        TypeMirror mirror = element.getSuperclass();
        if (mirror == null) {
            return null;
        }
        return environment.getType(mirror);
    }

    @Override
    public Type[] interfaceTypes() {
        if (interfaces == null) {
            interfaces = environment.getTypes(new ArrayList<>(element.getInterfaces()));
        }
        return interfaces;
    }

    @Override
    public ConstructorDoc[] constructors() {
        if (constructors == null) {
            List<ConstructorDoc> list = new ArrayList<>();
            for (Element e : element.getEnclosedElements()) {
                if (e.getKind() == ElementKind.CONSTRUCTOR) {
                    list.add(new ConstructorDocImpl(environment, (ExecutableElement) e, this));
                }
            }
            constructors = list.toArray(new ConstructorDoc[0]);
        }
        return constructors;
    }

    @Override
    public MethodDoc[] methods() {
        if (methods == null) {
            List<MethodDoc> list = new ArrayList<>();
            for (Element e : element.getEnclosedElements()) {
                if (e.getKind() == ElementKind.METHOD) {
                    list.add(new MethodDocImpl(environment, (ExecutableElement) e));
                }
            }
            methods = list.toArray(new MethodDoc[0]);
        }
        return methods;
    }

    @Override
    public FieldDoc[] fields() {
        if (fields == null) {
            List<FieldDoc> list = new ArrayList<>();
            for (Element e : element.getEnclosedElements()) {
                if (e.getKind() == ElementKind.FIELD) {
                    list.add(new FieldDocImpl(environment, e));
                }
            }
            fields = list.toArray(new FieldDoc[0]);
        }
        return fields;
    }

    @Override
    public ClassDoc[] innerClasses() {
        if (innerClasses == null) {
            List<ClassDoc> list = new ArrayList<>();
            for (Element e : element.getEnclosedElements()) {
                if (e.getKind().isClass() || e.getKind().isInterface()) {
                    if (e instanceof TypeElement) {
                        list.add(environment.getClassDoc((TypeElement) e));
                    }
                }
            }
            innerClasses = list.toArray(new ClassDoc[0]);
        }
        return innerClasses;
    }

    @Override
    public int compareTo(ClassDoc other) {
        return this.qualifiedTypeName().compareTo(other.qualifiedTypeName());
    }
}
