package jdiff.doc;

public interface RootDoc {
    PackageDoc[] specifiedPackages();

    ClassDoc[] specifiedClasses();

    PackageDoc packageNamed(String name);
}
