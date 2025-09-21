package jdiff.doc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.PackageElement;

final class PackageDocImpl extends BaseDoc implements PackageDoc {
    private final PackageElement element;
    private final String name;
    private final Map<String, ClassDocImpl> classes = new LinkedHashMap<>();

    PackageDocImpl(DocEnvironment environment, PackageElement element, String name) {
        super(environment, element, name);
        this.element = element;
        this.name = name == null ? "anonymous" : name;
    }

    void addClass(ClassDocImpl classDoc) {
        classes.putIfAbsent(classDoc.qualifiedTypeName(), classDoc);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public ClassDoc[] allClasses() {
        if (classes.isEmpty()) {
            return new ClassDoc[0];
        }
        List<ClassDocImpl> list = new ArrayList<>(classes.values());
        Collections.sort(list);
        return list.toArray(new ClassDoc[0]);
    }
}
