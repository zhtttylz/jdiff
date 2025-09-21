package jdiff.doc;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

final class TypeImpl implements Type {
    private final DocEnvironment environment;
    private final TypeMirror mirror;

    TypeImpl(DocEnvironment environment, TypeMirror mirror) {
        this.environment = environment;
        this.mirror = mirror;
    }

    TypeMirror mirror() {
        return mirror;
    }

    @Override
    public String qualifiedTypeName() {
        if (mirror.getKind().isPrimitive() || mirror.getKind() == TypeKind.VOID) {
            return mirror.toString();
        }
        if (mirror instanceof ArrayType) {
            ArrayType arrayType = (ArrayType) mirror;
            return environment.getType(arrayType.getComponentType()).qualifiedTypeName() + "[]";
        }
        TypeMirror erasure = environment.types().erasure(mirror);
        return erasure.toString();
    }

    @Override
    public String toString() {
        return mirror.toString();
    }
}
