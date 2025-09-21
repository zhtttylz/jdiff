package jdiff.doc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import jdk.javadoc.doclet.DocletEnvironment;

public final class RootDocImpl implements RootDoc {
    private final DocEnvironment environment;
    private final PackageDoc[] packages;
    private final ClassDoc[] specifiedClasses;
    private final Map<String, PackageDocImpl> packagesByName;

    private RootDocImpl(DocEnvironment environment,
                        PackageDoc[] packages,
                        ClassDoc[] specifiedClasses,
                        Map<String, PackageDocImpl> packagesByName) {
        this.environment = environment;
        this.packages = packages;
        this.specifiedClasses = specifiedClasses;
        this.packagesByName = packagesByName;
    }

    public static RootDocImpl create(DocletEnvironment environment) {
        DocEnvironment docEnv = new DocEnvironment(environment);
        Map<String, PackageDocImpl> packagesByName = new LinkedHashMap<>();
        List<ClassDoc> specifiedClasses = new ArrayList<>();

        // Register all included classes so that package data is available.
        for (Element element : environment.getIncludedElements()) {
            if (element instanceof TypeElement) {
                ClassDocImpl classDoc = docEnv.getClassDoc((TypeElement) element);
                packagesByName.put(classDoc.containingPackage().name(), (PackageDocImpl) classDoc.containingPackage());
            } else if (element instanceof PackageElement) {
                PackageDocImpl pkg = docEnv.getPackage((PackageElement) element);
                packagesByName.put(pkg.name(), pkg);
            }
        }

        for (Element element : environment.getSpecifiedElements()) {
            if (element instanceof PackageElement) {
                PackageDocImpl pkg = docEnv.getPackage((PackageElement) element);
                packagesByName.put(pkg.name(), pkg);
            } else if (element instanceof TypeElement) {
                ClassDocImpl classDoc = docEnv.getClassDoc((TypeElement) element);
                packagesByName.put(classDoc.containingPackage().name(), (PackageDocImpl) classDoc.containingPackage());
                specifiedClasses.add(classDoc);
            }
        }

        // Ensure at least the discovered packages are present.
        for (PackageDocImpl pkg : docEnv.packages()) {
            packagesByName.putIfAbsent(pkg.name(), pkg);
        }

        PackageDoc[] packages = packagesByName.values().toArray(new PackageDoc[0]);
        ClassDoc[] classes = specifiedClasses.toArray(new ClassDoc[0]);
        return new RootDocImpl(docEnv, packages, classes, packagesByName);
    }

    @Override
    public PackageDoc[] specifiedPackages() {
        return packages;
    }

    @Override
    public ClassDoc[] specifiedClasses() {
        return specifiedClasses;
    }

    @Override
    public PackageDoc packageNamed(String name) {
        PackageDocImpl pkg = packagesByName.get(name);
        if (pkg == null) {
            pkg = environment.getOrCreatePackageByName(name);
            packagesByName.put(name, pkg);
        }
        return pkg;
    }
}
