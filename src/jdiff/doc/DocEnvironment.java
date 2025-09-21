package jdiff.doc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import jdk.javadoc.doclet.DocletEnvironment;

/**
 * Shared state used when adapting {@link DocletEnvironment} to the doclet model used by JDiff.
 */
class DocEnvironment {
    private final DocletEnvironment environment;
    private final Elements elements;
    private final Types types;

    private final Map<PackageElement, PackageDocImpl> packageByElement = new LinkedHashMap<>();
    private final Map<String, PackageDocImpl> packageByName = new LinkedHashMap<>();
    private final Map<TypeElement, ClassDocImpl> classByElement = new LinkedHashMap<>();
    private final Map<TypeMirror, TypeImpl> typeCache = new IdentityHashMap<>();

    private final PackageDocImpl anonymousPackage;

    DocEnvironment(DocletEnvironment environment) {
        this.environment = environment;
        this.elements = environment.getElementUtils();
        this.types = environment.getTypeUtils();
        this.anonymousPackage = new PackageDocImpl(this, null, "anonymous");
        packageByName.put(this.anonymousPackage.name(), this.anonymousPackage);
    }

    DocletEnvironment environment() {
        return environment;
    }

    Elements elements() {
        return elements;
    }

    Types types() {
        return types;
    }

    PackageDocImpl getPackage(PackageElement element) {
        if (element == null || element.isUnnamed()) {
            return anonymousPackage;
        }
        return packageByElement.computeIfAbsent(element, pkg -> {
            String name = pkg.getQualifiedName().toString();
            PackageDocImpl doc = new PackageDocImpl(this, pkg, name);
            packageByName.put(name, doc);
            return doc;
        });
    }

    PackageDocImpl getOrCreatePackageByName(String name) {
        if (name == null) {
            return anonymousPackage;
        }
        return packageByName.computeIfAbsent(name, key -> new PackageDocImpl(this, null, key));
    }

    ClassDocImpl getClassDoc(TypeElement element) {
        return classByElement.computeIfAbsent(element, type -> {
            PackageDocImpl pkg = getPackage(elements.getPackageOf(type));
            ClassDocImpl doc = new ClassDocImpl(this, type, pkg);
            pkg.addClass(doc);
            return doc;
        });
    }

    Collection<PackageDocImpl> packages() {
        return Collections.unmodifiableCollection(packageByName.values());
    }

    Type getType(TypeMirror mirror) {
        if (mirror == null) {
            return null;
        }
        if (mirror.getKind() == TypeKind.NONE) {
            return null;
        }
        return typeCache.computeIfAbsent(mirror, m -> new TypeImpl(this, m));
    }

    Type[] getTypes(List<? extends TypeMirror> mirrors) {
        if (mirrors.isEmpty()) {
            return new Type[0];
        }
        List<Type> result = new ArrayList<>(mirrors.size());
        for (TypeMirror mirror : mirrors) {
            Type type = getType(mirror);
            if (type != null) {
                result.add(type);
            }
        }
        return result.toArray(new Type[0]);
    }

    String docComment(Element element) {
        if (element == null) {
            return null;
        }
        return elements.getDocComment(element);
    }

    PackageDocImpl anonymousPackage() {
        return anonymousPackage;
    }

    void registerSyntheticPackage(PackageDocImpl pkg) {
        packageByName.put(pkg.name(), pkg);
    }
}
