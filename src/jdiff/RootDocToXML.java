package jdiff;

import java.io.*;
import java.util.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.TreePath;

import jdk.javadoc.doclet.DocletEnvironment;

/**
 * Converts a Javadoc RootDoc object into a representation in an 
 * XML file.
 *
 * See the file LICENSE.txt for copyright details.
 * @author Matthew Doar, mdoar@pobox.com
 */
public class RootDocToXML {

    private final DocletEnvironment environment;
    private final DocTrees docTrees;
    private final Elements elementUtils;
    private final Types typeUtils;
    private final Map<String, List<TypeElement>> packageTypes = new TreeMap();
    private final Map<String, PackageElement> packageElements = new HashMap();
    private final Map<String, List<TypeElement>> explicitlySpecifiedClasses = new HashMap();

    public RootDocToXML(DocletEnvironment environment) {
        this.environment = environment;
        this.docTrees = environment.getDocTrees();
        this.elementUtils = environment.getElementUtils();
        this.typeUtils = environment.getTypeUtils();
        gatherSpecifiedElements();
        gatherIncludedElements();
    }

    private void gatherSpecifiedElements() {
        for (Element element : environment.getSpecifiedElements()) {
            if (element instanceof TypeElement) {
                TypeElement type = (TypeElement) element;
                PackageElement pkg = elementUtils.getPackageOf(type);
                if (pkg != null) {
                    String pkgName = pkg.getQualifiedName().toString();
                    explicitlySpecifiedClasses
                        .computeIfAbsent(pkgName, k -> new ArrayList())
                        .add(type);
                    packageElements.putIfAbsent(pkgName, pkg);
                }
            } else if (element instanceof PackageElement) {
                PackageElement pkg = (PackageElement) element;
                packageElements.putIfAbsent(pkg.getQualifiedName().toString(), pkg);
            }
        }
    }

    private void gatherIncludedElements() {
        for (Element element : environment.getIncludedElements()) {
            if (element instanceof PackageElement) {
                PackageElement pkg = (PackageElement) element;
                packageElements.putIfAbsent(pkg.getQualifiedName().toString(), pkg);
            } else if (element instanceof TypeElement) {
                TypeElement type = (TypeElement) element;
                PackageElement pkg = elementUtils.getPackageOf(type);
                if (pkg == null) {
                    continue;
                }
                String pkgName = pkg.getQualifiedName().toString();
                packageElements.putIfAbsent(pkgName, pkg);
                packageTypes.computeIfAbsent(pkgName, k -> new ArrayList()).add(type);
            }
        }
        for (Map.Entry<String, List<TypeElement>> entry : explicitlySpecifiedClasses.entrySet()) {
            List<TypeElement> list = packageTypes.computeIfAbsent(entry.getKey(), k -> new ArrayList());
            for (TypeElement type : entry.getValue()) {
                if (!list.contains(type)) {
                    list.add(type);
                }
            }
        }
    }

    /**
     * Write the XML representation of the API to a file.
     *
     * @param environment  the DocletEnvironment passed by Javadoc
     * @return true if no problems encountered
     */
    public static boolean writeXML(DocletEnvironment environment) {
        String tempFileName = outputFileName;
        if (outputDirectory != null) {
            tempFileName = outputDirectory;
            if (!tempFileName.endsWith(JDiff.DIR_SEP))
                tempFileName += JDiff.DIR_SEP;
	    tempFileName += outputFileName;
    	}

        try {
            FileOutputStream fos = new FileOutputStream(tempFileName);
            outputFile = new PrintWriter(fos);
            System.out.println("JDiff: writing the API to file '" + tempFileName + "'...");
            RootDocToXML apiWriter = new RootDocToXML(environment);
            if (!apiWriter.packageTypes.isEmpty() || !apiWriter.explicitlySpecifiedClasses.isEmpty()) {
                apiWriter.emitXMLHeader();
                apiWriter.logOptions();
                apiWriter.processPackages();
                apiWriter.emitXMLFooter();
            }
            outputFile.close();
        } catch(IOException e) {
            System.out.println("IO Error while attempting to create " + tempFileName);
            System.out.println("Error: " +  e.getMessage());
            System.exit(1);
        }
        // If validation is desired, write out the appropriate api.xsd file
        // in the same directory as the XML file.
        if (XMLToAPI.validateXML) {
            writeXSD();
        }
        return true;
    }

    /**
     * Write the XML Schema file used for validation.
     */
    public static void writeXSD() {
        String xsdFileName = outputFileName;
        if (outputDirectory == null) {
	    int idx = xsdFileName.lastIndexOf('\\');
	    int idx2 = xsdFileName.lastIndexOf('/');
	    if (idx == -1 && idx2 == -1) {
		xsdFileName = "";
	    } else if (idx == -1 && idx2 != -1) {
		xsdFileName = xsdFileName.substring(0, idx2);
	    } else if (idx != -1  && idx2 == -1) {
		xsdFileName = xsdFileName.substring(0, idx);
	    } else if (idx != -1  && idx2 != -1) {
		int max = idx2 > idx ? idx2 : idx;
		xsdFileName = xsdFileName.substring(0, max);
	    }
	} else {
	    xsdFileName = outputDirectory;
	    if (!xsdFileName.endsWith(JDiff.DIR_SEP)) 
		 xsdFileName += JDiff.DIR_SEP;
	}
        xsdFileName += "api.xsd";
        try {
            FileOutputStream fos = new FileOutputStream(xsdFileName);
            PrintWriter xsdFile = new PrintWriter(fos);
            // The contents of the api.xsd file
            xsdFile.println("<?xml version=\"1.0\" encoding=\"iso-8859-1\" standalone=\"no\"?>");
            xsdFile.println("<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">");
            xsdFile.println("");
            xsdFile.println("<xsd:annotation>");
            xsdFile.println("  <xsd:documentation>");
            xsdFile.println("  Schema for JDiff API representation.");
            xsdFile.println("  </xsd:documentation>");
            xsdFile.println("</xsd:annotation>");
            xsdFile.println();
            xsdFile.println("<xsd:element name=\"api\" type=\"apiType\"/>");
            xsdFile.println("");
            xsdFile.println("<xsd:complexType name=\"apiType\">");
            xsdFile.println("  <xsd:sequence>");
            xsdFile.println("    <xsd:element name=\"package\" type=\"packageType\" minOccurs='1' maxOccurs='unbounded'/>");
            xsdFile.println("  </xsd:sequence>");
            xsdFile.println("  <xsd:attribute name=\"name\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"jdversion\" type=\"xsd:string\"/>");
            xsdFile.println("</xsd:complexType>");
            xsdFile.println();
            xsdFile.println("<xsd:complexType name=\"packageType\">");
            xsdFile.println("  <xsd:sequence>");
            xsdFile.println("    <xsd:choice maxOccurs='unbounded'>");
            xsdFile.println("      <xsd:element name=\"class\" type=\"classType\"/>");
            xsdFile.println("      <xsd:element name=\"interface\" type=\"classType\"/>");
            xsdFile.println("    </xsd:choice>");
            xsdFile.println("    <xsd:element name=\"doc\" type=\"xsd:string\" minOccurs='0' maxOccurs='1'/>");
            xsdFile.println("  </xsd:sequence>");
            xsdFile.println("  <xsd:attribute name=\"name\" type=\"xsd:string\"/>");
            xsdFile.println("</xsd:complexType>");
            xsdFile.println();
            xsdFile.println("<xsd:complexType name=\"classType\">");
            xsdFile.println("  <xsd:sequence>");
            xsdFile.println("    <xsd:element name=\"implements\" type=\"interfaceTypeName\" minOccurs='0' maxOccurs='unbounded'/>");
            xsdFile.println("    <xsd:element name=\"constructor\" type=\"constructorType\" minOccurs='0' maxOccurs='unbounded'/>");
            xsdFile.println("    <xsd:element name=\"method\" type=\"methodType\" minOccurs='0' maxOccurs='unbounded'/>");
            xsdFile.println("    <xsd:element name=\"field\" type=\"fieldType\" minOccurs='0' maxOccurs='unbounded'/>");
            xsdFile.println("    <xsd:element name=\"doc\" type=\"xsd:string\" minOccurs='0' maxOccurs='1'/>");
            xsdFile.println("  </xsd:sequence>");
            xsdFile.println("  <xsd:attribute name=\"name\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"extends\" type=\"xsd:string\" use='optional'/>");
            xsdFile.println("  <xsd:attribute name=\"abstract\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"src\" type=\"xsd:string\" use='optional'/>");
            xsdFile.println("  <xsd:attribute name=\"static\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"final\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"deprecated\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"visibility\" type=\"xsd:string\"/>");
            xsdFile.println("</xsd:complexType>");
            xsdFile.println();
            xsdFile.println("<xsd:complexType name=\"interfaceTypeName\">");
            xsdFile.println("  <xsd:attribute name=\"name\" type=\"xsd:string\"/>");
            xsdFile.println("</xsd:complexType>");
            xsdFile.println();
            xsdFile.println("<xsd:complexType name=\"constructorType\">");
            xsdFile.println("  <xsd:sequence>");
            xsdFile.println("    <xsd:element name=\"exception\" type=\"exceptionType\" minOccurs='0' maxOccurs='unbounded'/>");
            xsdFile.println("    <xsd:element name=\"doc\" type=\"xsd:string\" minOccurs='0' maxOccurs='1'/>");
            xsdFile.println("  </xsd:sequence>");
            xsdFile.println("  <xsd:attribute name=\"name\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"type\" type=\"xsd:string\" use='optional'/>");
            xsdFile.println("  <xsd:attribute name=\"src\" type=\"xsd:string\" use='optional'/>");
            xsdFile.println("  <xsd:attribute name=\"static\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"final\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"deprecated\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"visibility\" type=\"xsd:string\"/>");
            xsdFile.println("</xsd:complexType>");
            xsdFile.println();
            xsdFile.println("<xsd:complexType name=\"paramsType\">");
            xsdFile.println("  <xsd:attribute name=\"name\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"type\" type=\"xsd:string\"/>");
            xsdFile.println("</xsd:complexType>");
            xsdFile.println();
            xsdFile.println("<xsd:complexType name=\"exceptionType\">");
            xsdFile.println("  <xsd:attribute name=\"name\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"type\" type=\"xsd:string\"/>");
            xsdFile.println("</xsd:complexType>");
            xsdFile.println();
            xsdFile.println("<xsd:complexType name=\"methodType\">");
            xsdFile.println("  <xsd:sequence>");
            xsdFile.println("    <xsd:element name=\"param\" type=\"paramsType\" minOccurs='0' maxOccurs='unbounded'/>");
            xsdFile.println("    <xsd:element name=\"exception\" type=\"exceptionType\" minOccurs='0' maxOccurs='unbounded'/>");
            xsdFile.println("    <xsd:element name=\"doc\" type=\"xsd:string\" minOccurs='0' maxOccurs='1'/>");
            xsdFile.println("  </xsd:sequence>");
            xsdFile.println("  <xsd:attribute name=\"name\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"return\" type=\"xsd:string\" use='optional'/>");
            xsdFile.println("  <xsd:attribute name=\"abstract\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"native\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"synchronized\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"src\" type=\"xsd:string\" use='optional'/>");
            xsdFile.println("  <xsd:attribute name=\"static\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"final\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"deprecated\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"visibility\" type=\"xsd:string\"/>");
            xsdFile.println("</xsd:complexType>");
            xsdFile.println();
            xsdFile.println("<xsd:complexType name=\"fieldType\">");
            xsdFile.println("  <xsd:sequence>");
            xsdFile.println("    <xsd:element name=\"doc\" type=\"xsd:string\" minOccurs='0' maxOccurs='1'/>");
            xsdFile.println("  </xsd:sequence>");
            xsdFile.println("  <xsd:attribute name=\"name\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"type\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"transient\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"volatile\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"value\" type=\"xsd:string\" use='optional'/>");
            xsdFile.println("  <xsd:attribute name=\"src\" type=\"xsd:string\" use='optional'/>");
            xsdFile.println("  <xsd:attribute name=\"static\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"final\" type=\"xsd:boolean\"/>");
            xsdFile.println("  <xsd:attribute name=\"deprecated\" type=\"xsd:string\"/>");
            xsdFile.println("  <xsd:attribute name=\"visibility\" type=\"xsd:string\"/>");
            xsdFile.println("</xsd:complexType>");
            xsdFile.println();
            xsdFile.println("</xsd:schema>");
            xsdFile.close();
        } catch(IOException e) {
            System.out.println("IO Error while attempting to create " + xsdFileName);
            System.out.println("Error: " +  e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Write the options which were used to generate this XML file
     * out as XML comments.
     */
    public void logOptions() {
        outputFile.print("<!-- ");
        outputFile.print(" Command line arguments = " + Options.cmdOptions);
        outputFile.println(" -->");
    }

    /**
     * Process each package and the classes/interfaces within it.
     *
     * @param pd  an array of PackageDoc objects
     */
    public void processPackages() {
        List<String> packageNames = new ArrayList(packageTypes.keySet());
        for (String pkgName : packageElements.keySet()) {
            if (!packageNames.contains(pkgName)) {
                packageNames.add(pkgName);
            }
        }
        Collections.sort(packageNames);
        for (String pkgName : packageNames) {
            PackageElement pkg = packageElements.get(pkgName);
            if (pkg == null) {
                continue;
            }
            String pkgComment = elementUtils.getDocComment(pkg);
            if (!shouldIncludeElement(pkg, pkgComment, null)) {
                continue;
            }
            if (trace) System.out.println("PROCESSING PACKAGE: " + pkgName);
            outputFile.println("<package name=\"" + pkgName + "\">");

            List<TypeElement> classes = collectClassesForPackage(pkgName);
            Collections.sort(classes, new Comparator<TypeElement>() {
                public int compare(TypeElement a, TypeElement b) {
                    return a.getQualifiedName().toString().compareTo(b.getQualifiedName().toString());
                }
            });
            processClasses(classes, pkgName);

            addPkgDocumentation(pkg, pkgComment, 2);

            outputFile.println("</package>");
        }
    } // processPackages

    private List<TypeElement> collectClassesForPackage(String pkgName) {
        List<TypeElement> classes;
        if (explicitlySpecifiedClasses.containsKey(pkgName) && !packagesOnly) {
            classes = new ArrayList(explicitlySpecifiedClasses.get(pkgName));
        } else {
            classes = new ArrayList(packageTypes.getOrDefault(pkgName, Collections.emptyList()));
        }
        return classes;
    }

    private boolean isInterface(TypeElement type) {
        switch (type.getKind()) {
            case INTERFACE:
            case ANNOTATION_TYPE:
                return true;
            default:
                return false;
        }
    }

    private List<ExecutableElement> getConstructors(TypeElement type) {
        List<ExecutableElement> constructors = new ArrayList();
        for (Element e : type.getEnclosedElements()) {
            if (e instanceof ExecutableElement && e.getKind() == javax.lang.model.element.ElementKind.CONSTRUCTOR) {
                constructors.add((ExecutableElement) e);
            }
        }
        return constructors;
    }

    private List<ExecutableElement> getMethods(TypeElement type) {
        List<ExecutableElement> methods = new ArrayList();
        for (Element e : type.getEnclosedElements()) {
            if (e instanceof ExecutableElement && e.getKind() == javax.lang.model.element.ElementKind.METHOD) {
                methods.add((ExecutableElement) e);
            }
        }
        return methods;
    }

    private List<VariableElement> getFields(TypeElement type) {
        List<VariableElement> fields = new ArrayList();
        for (Element e : type.getEnclosedElements()) {
            if (e instanceof VariableElement &&
                (e.getKind() == javax.lang.model.element.ElementKind.FIELD ||
                 e.getKind() == javax.lang.model.element.ElementKind.ENUM_CONSTANT)) {
                fields.add((VariableElement) e);
            }
        }
        return fields;
    }
    
    /**
     * Process classes and interfaces.
     *
     * @param cd An array of ClassDoc objects.
     */
    public void processClasses(List<TypeElement> types, String pkgName) {
        if (types.isEmpty()) {
            return;
        }
        if (trace) System.out.println("PROCESSING CLASSES, number=" + types.size());
        for (TypeElement type : types) {
            String className = type.getSimpleName().toString();
            String docComment = elementUtils.getDocComment(type);
            if (trace) System.out.println("PROCESSING CLASS/IFC: " + className);
            if (!shouldIncludeElement(type, docComment, classVisibilityLevel)) {
                continue;
            }
            boolean isInterface = isInterface(type);
            if (isInterface) {
                outputFile.println("  <!-- start interface " + pkgName + "." + className + " -->");
                outputFile.print("  <interface name=\"" + className + "\"");
            } else {
                outputFile.println("  <!-- start class " + pkgName + "." + className + " -->");
                outputFile.print("  <class name=\"" + className + "\"");
            }
            TypeMirror superclass = type.getSuperclass();
            if (superclass != null && superclass.getKind() != TypeKind.NONE) {
                outputFile.println(" extends=\"" + buildEmittableTypeString(superclass) + "\"");
            }
            outputFile.println("    abstract=\"" + type.getModifiers().contains(Modifier.ABSTRACT) + "\"");
            addCommonModifiers(type, docComment, 4);
            outputFile.println(">");

            processInterfaces(type.getInterfaces());
            processConstructors(type, getConstructors(type));
            processMethods(type, getMethods(type));
            processFields(getFields(type));

            addDocumentation(type, docComment, 4);

            if (isInterface) {
                outputFile.println("  </interface>");
                outputFile.println("  <!-- end interface " + pkgName + "." + className + " -->");
            } else {
                outputFile.println("  </class>");
                outputFile.println("  <!-- end class " + pkgName + "." + className + " -->");
            }
        }
    }
    
    /**
     * Add qualifiers for the program element as attributes.
     *
     * @param ped The given program element.
     */
    public void addCommonModifiers(Element element, String docComment, int indent) {
        addSourcePosition(element, indent);
        Set<Modifier> modifiers = element.getModifiers();
        for (int i = 0; i < indent; i++) outputFile.print(" ");
        outputFile.print("static=\"" + modifiers.contains(Modifier.STATIC) + "\"");
        outputFile.print(" final=\"" + modifiers.contains(Modifier.FINAL) + "\"");
        String visibility = determineVisibility(modifiers);
        outputFile.println(" visibility=\"" + visibility + "\"");

        for (int i = 0; i < indent; i++) outputFile.print(" ");
        String deprecatedText = extractDeprecatedText(element, docComment);
        if (deprecatedText != null) {
            outputFile.print("deprecated=\"" + deprecatedText + "\"");
        } else {
            outputFile.print("deprecated=\"not deprecated\"");
        }

    }

    private String determineVisibility(Set<Modifier> modifiers) {
        if (modifiers.contains(Modifier.PUBLIC)) {
            return "public";
        }
        if (modifiers.contains(Modifier.PROTECTED)) {
            return "protected";
        }
        if (modifiers.contains(Modifier.PRIVATE)) {
            return "private";
        }
        return "package";
    }

    private String extractDeprecatedText(Element element, String docComment) {
        boolean isDeprecated = element != null && element.getAnnotation(Deprecated.class) != null;
        String text = null;
        if (docComment != null) {
            int index = docComment.indexOf("@deprecated");
            if (index != -1) {
                isDeprecated = true;
                int start = index + "@deprecated".length();
                int end = docComment.indexOf("@", start);
                if (end == -1) {
                    end = docComment.length();
                }
                text = docComment.substring(start, end).trim();
            }
        }
        if (!isDeprecated) {
            return null;
        }
        if (text == null || text.length() == 0) {
            return "deprecated, no comment";
        }
        int idx = endOfFirstSentence(text);
        if (idx == 0) {
            return "deprecated, no comment";
        }
        String firstSentence;
        if (idx == -1) {
            firstSentence = text;
        } else {
            firstSentence = text.substring(0, idx + 1);
        }
        return API.hideHTMLTags(firstSentence);
    }

    /**
     * Insert the source code details, if available.
     *
     * @param ped The given program element.
     */
    public void addSourcePosition(Element element, int indent) {
        if (!addSrcInfo)
            return;
        if (element == null)
            return;
        TreePath path = docTrees.getPath(element);
        if (path == null)
            return;
        CompilationUnitTree unit = path.getCompilationUnit();
        if (unit == null || unit.getSourceFile() == null)
            return;
        long pos = docTrees.getSourcePositions().getStartPosition(unit, path.getLeaf());
        if (pos < 0)
            return;
        long line = unit.getLineMap().getLineNumber(pos);
        if (line < 0)
            return;
        String fileName = unit.getSourceFile().toUri().getPath();
        if (fileName == null) {
            fileName = unit.getSourceFile().getName();
        }
        if (fileName == null)
            return;
        for (int i = 0; i < indent; i++) outputFile.print(" ");
        outputFile.println("src=\"" + fileName + ":" + line + "\"");
    }

    /**
     * Process the interfaces implemented by the class.
     *
     * @param ifaces An array of ClassDoc objects
     */
    public void processInterfaces(List<? extends TypeMirror> ifaces) {
        if (trace) System.out.println("PROCESSING INTERFACES, number=" + ifaces.size());
        for (TypeMirror iface : ifaces) {
            String ifaceName = buildEmittableTypeString(iface);
            if (trace) System.out.println("PROCESSING INTERFACE: " + ifaceName);
            outputFile.println("    <implements name=\"" + ifaceName + "\"/>");
        }
    }
    
    /**
     * Process the constructors in the class.
     *
     * @param ct An array of ConstructorDoc objects
     */
    public void processConstructors(TypeElement owner, List<ExecutableElement> constructors) {
        if (trace) System.out.println("PROCESSING CONSTRUCTORS, number=" + constructors.size());
        for (ExecutableElement ctor : constructors) {
            String ctorName = owner.getSimpleName().toString();
            String docComment = elementUtils.getDocComment(ctor);
            if (trace) System.out.println("PROCESSING CONSTRUCTOR: " + ctorName);
            if (!shouldIncludeElement(ctor, docComment, memberVisibilityLevel)) {
                continue;
            }
            outputFile.print("    <constructor name=\"" + ctorName + "\"");

            List<? extends VariableElement> params = ctor.getParameters();
            if (!params.isEmpty()) {
                outputFile.print(" type=\"");
                boolean first = true;
                for (VariableElement param : params) {
                    if (!first) {
                        outputFile.print(", ");
                    }
                    emitType(param.asType());
                    first = false;
                }
                outputFile.println("\"");
            } else {
                outputFile.println();
            }
            addCommonModifiers(ctor, docComment, 6);
            outputFile.println(">");

            processExceptions(ctor.getThrownTypes());

            addDocumentation(ctor, docComment, 6);

            outputFile.println("    </constructor>");
        }
    }
    
    /**
     * Process all exceptions thrown by a constructor or method.
     *
     * @param cd An array of ClassDoc objects
     */
    public void processExceptions(List<? extends TypeMirror> thrownTypes) {
        if (trace) System.out.println("PROCESSING EXCEPTIONS, number=" + thrownTypes.size());
        for (TypeMirror thrown : thrownTypes) {
            String typeName = buildEmittableTypeString(thrown);
            String simpleName = extractSimpleName(typeName);
            if (trace) System.out.println("PROCESSING EXCEPTION: " + typeName);
            outputFile.print("      <exception name=\"" + simpleName + "\" type=\"");
            outputFile.print(typeName);
            outputFile.println("\"/>");
        }
    }
    
    /**
     * Process the methods in the class.
     *
     * @param md An array of MethodDoc objects
     */
    public void processMethods(TypeElement owner, List<ExecutableElement> methods) {
        if (trace) System.out.println("PROCESSING " + owner.getSimpleName() + " METHODS, number = " + methods.size());
        for (ExecutableElement method : methods) {
            String methodName = method.getSimpleName().toString();
            if (trace) System.out.println("PROCESSING METHOD: " + methodName);
            String docComment = elementUtils.getDocComment(method);
            if (!shouldIncludeElement(method, docComment, memberVisibilityLevel)) {
                continue;
            }
            outputFile.print("    <method name=\"" + methodName + "\"");
            TypeMirror retType = method.getReturnType();
            if (retType.getKind() != TypeKind.VOID) {
                outputFile.print(" return=\"");
                emitType(retType);
                outputFile.println("\"");
            } else {
                outputFile.println();
            }
            outputFile.print("      abstract=\"" + method.getModifiers().contains(Modifier.ABSTRACT) + "\"");
            outputFile.print(" native=\"" + method.getModifiers().contains(Modifier.NATIVE) + "\"");
            outputFile.println(" synchronized=\"" + method.getModifiers().contains(Modifier.SYNCHRONIZED) + "\"");
            addCommonModifiers(method, docComment, 6);
            outputFile.println(">");

            List<? extends VariableElement> params = method.getParameters();
            for (VariableElement param : params) {
                outputFile.print("      <param name=\"" + param.getSimpleName().toString() + "\"");
                outputFile.print(" type=\"");
                emitType(param.asType());
                outputFile.println("\"/>");
            }

            processExceptions(method.getThrownTypes());

            addDocumentation(method, docComment, 6);

            outputFile.println("    </method>");
        }
    }

    /**
     * Process the fields in the class.
     *
     * @param fd An array of FieldDoc objects
     */
    public void processFields(List<VariableElement> fields) {
        if (trace) System.out.println("PROCESSING FIELDS, number=" + fields.size());
        for (VariableElement field : fields) {
            String fieldName = field.getSimpleName().toString();
            String docComment = elementUtils.getDocComment(field);
            if (trace) System.out.println("PROCESSING FIELD: " + fieldName);
            if (!shouldIncludeElement(field, docComment, memberVisibilityLevel)) {
                continue;
            }
            outputFile.print("    <field name=\"" + fieldName + "\"");
            outputFile.print(" type=\"");
            emitType(field.asType());
            outputFile.println("\"");
            outputFile.print("      transient=\"" + field.getModifiers().contains(Modifier.TRANSIENT) + "\"");
            outputFile.println(" volatile=\"" + field.getModifiers().contains(Modifier.VOLATILE) + "\"");
            addCommonModifiers(field, docComment, 6);
            outputFile.println(">");

            addDocumentation(field, docComment, 6);

            outputFile.println("    </field>");

        }
    }
    
    /**
     * Emit the type name. Removed any prefixed warnings about ambiguity.
     * The type maybe an array.
     *
     * @param type A Type object.
     */
    public void emitType(TypeMirror type) {
        String name = buildEmittableTypeString(type);
        if (name == null)
            return;
        outputFile.print(name);
    }

    /**
     * Build the emittable type name. The type may be an array and/or
     * a generic type.
     *
     * @param type A Type object
     * @return The emittable type name
     */
    private String buildEmittableTypeString(TypeMirror type) {
        if (type == null) {
            return null;
        }
        String name = type.toString()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        return name;
    }

    private String extractSimpleName(String typeName) {
        if (typeName == null) {
            return null;
        }
        int genericIndex = typeName.indexOf("&lt;");
        if (genericIndex != -1) {
            typeName = typeName.substring(0, genericIndex);
        }
        int lastDot = typeName.lastIndexOf('.');
        if (lastDot != -1) {
            typeName = typeName.substring(lastDot + 1);
        }
        return typeName;
    }

    /**
     * Emit the XML header.
     */
    public void emitXMLHeader() {
        outputFile.println("<?xml version=\"1.0\" encoding=\"iso-8859-1\" standalone=\"no\"?>");
        outputFile.println("<!-- Generated by the JDiff Javadoc doclet -->");
        outputFile.println("<!-- (" + JDiff.jDiffLocation + ") -->");
        outputFile.println("<!-- on " + new Date() + " -->");
        outputFile.println();
/* No need for this any longer, since doc block text is in an CDATA element
        outputFile.println("<!-- XML Schema is used, but XHTML transitional DTD is needed for nbsp -->");
        outputFile.println("<!-- entity definitions etc.-->");
        outputFile.println("<!DOCTYPE api");
        outputFile.println("     PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"");
        outputFile.println("     \"" + baseURI + "/TR/xhtml1/DTD/xhtml1-transitional.dtd\">");
*/
        outputFile.println("<api");
        outputFile.println("  xmlns:xsi='" + baseURI + "/2001/XMLSchema-instance'");
        outputFile.println("  xsi:noNamespaceSchemaLocation='api.xsd'");
        outputFile.println("  name=\"" + apiIdentifier + "\"");
        outputFile.println("  jdversion=\"" + JDiff.version + "\">");
        outputFile.println();
    }

    /**
     * Emit the XML footer.
     */
    public void emitXMLFooter() {
        outputFile.println();
        outputFile.println("</api>");
    }

    /** 
     * Determine if the program element is shown, according to the given 
     * level of visibility. 
     *
     * @param ped The given program element.
     * @param visLevel The desired visibility level; "public", "protected",
     *   "package" or "private". If null, only check for an exclude tag.
     * @return boolean Set if this element is shown.
     */
    public boolean shouldIncludeElement(Element element, String docComment, String visLevel) {
        if (doExclude && excludeTag != null) {
            if (docComment != null && docComment.indexOf(excludeTag) != -1) {
                return false;
            }
        }
        if (visLevel == null) {
            return true;
        }
        if (element == null) {
            return true;
        }
        Set<Modifier> modifiers = element.getModifiers();
        if (visLevel.compareTo("private") == 0)
            return true;
        if (visLevel.compareTo("package") == 0)
            return !modifiers.contains(Modifier.PRIVATE);
        if (visLevel.compareTo("protected") == 0)
            return modifiers.contains(Modifier.PUBLIC) || modifiers.contains(Modifier.PROTECTED);
        if (visLevel.compareTo("public") == 0)
            return modifiers.contains(Modifier.PUBLIC);
        return false;
    }
    
    /** 
     * Strip out non-printing characters, replacing them with a character 
     * which will not change where the end of the first sentence is found.
     * This character is the hash mark, '&#035;'.
     */
    public String stripNonPrintingChars(String s, String elementName) {
        if (!stripNonPrintables)
            return s;
        char[] sa = s.toCharArray();
        for (int i = 0; i < sa.length; i++) {
            char c = sa[i];
            // TODO still have an issue with Unicode: 0xfc in java.lang.String.toUpperCase comments
//            if (Character.isDefined(c))
            if (Character.isLetterOrDigit(c))
                continue;
            // There must be a better way that is still platform independent!
            if (c == ' ' ||
                c == '.' ||
                c == ',' ||
                c == '\r' ||
                c == '\t' ||
                c == '\n' ||
                c == '!' ||
                c == '?' ||
                c == ';' ||
                c == ':' ||
                c == '[' ||
                c == ']' ||
                c == '(' ||
                c == ')' ||
                c == '~' ||
                c == '@' ||
                c == '#' ||
                c == '$' ||
                c == '%' ||
                c == '^' ||
                c == '&' ||
                c == '*' ||
                c == '-' ||
                c == '=' ||
                c == '+' ||
                c == '_' ||
                c == '|' ||
                c == '\\' ||
                c == '/' ||
                c == '\'' ||
                c == '}' ||
                c == '{' ||
                c == '"' ||
                c == '<' ||
                c == '>' ||
                c == '`'
                )
                continue;
/* Doesn't seem to return the expected values?
            int val = Character.getNumericValue(c);
//            if (s.indexOf("which is also a test for non-printable") != -1)
//                System.out.println("** Char " + i + "[" + c + "], val =" + val); //DEBUG
            // Ranges from http://www.unicode.org/unicode/reports/tr20/
            // Should really replace 0x2028 and  0x2029 with <br/>
            if (val == 0x0 ||
                inRange(val, 0x2028, 0x2029) || 
                inRange(val, 0x202A, 0x202E) || 
                inRange(val, 0x206A, 0x206F) || 
                inRange(val, 0xFFF9, 0xFFFC) || 
                inRange(val, 0xE0000, 0xE007F)) {
                if (trace && elementName != null) {
                    System.out.println("Warning: changed non-printing character  " + sa[i] + " in " + elementName);
                }
                sa[i] = '#';
            }
*/
            // Replace the non-printable character with a printable character
            // which does not change the end of the first sentence
            sa[i] = '#';
        }
        return new String(sa);
    }

    private String elementName(Element element) {
        if (element == null) {
            return null;
        }
        if (element instanceof PackageElement) {
            return ((PackageElement) element).getQualifiedName().toString();
        }
        String name = element.getSimpleName().toString();
        if (name == null || name.length() == 0) {
            return element.toString();
        }
        return name;
    }

    /** Return true if val is in the range [min|max], inclusive. */
    public boolean inRange(int val, int min, int max) {
        if (val < min)
            return false;
        if (val > max)
            return false;
        return true;
    }

    /** 
     * Add at least the first sentence from a doc block to the API. This is
     * used by the report generator if no comment is provided.
     * Need to make sure that HTML tags are not confused with XML tags.
     * This could be done by stuffing the &lt; character to another string
     * or by handling HTML in the parser. This second option seems neater. Note that
     * XML expects all element tags to have either a closing "/>" or a matching
     * end element tag. Due to the difficulties of converting incorrect HTML
     * to XHTML, the first option is used.
     */
    public void addDocumentation(Element element, String docComment, int indent) {
        if (docComment == null) {
            return;
        }
        String elementName = elementName(element);
        String rct = stripNonPrintingChars(docComment, elementName);
        rct = rct.trim();
        if (rct.compareTo("") != 0 &&
            rct.indexOf(Comments.placeHolderText) == -1 &&
            rct.indexOf("InsertOtherCommentsHere") == -1) {
            int idx = endOfFirstSentence(rct);
            if (idx == 0)
                return;
            for (int i = 0; i < indent; i++) outputFile.print(" ");
            outputFile.println("<doc>");
            for (int i = 0; i < indent; i++) outputFile.print(" ");
            String firstSentence = null;
            if (idx == -1)
                firstSentence = rct;
            else
                firstSentence = rct.substring(0, idx+1);
            boolean checkForAts = false;
            if (checkForAts && firstSentence.indexOf("@") != -1 &&
                firstSentence.indexOf("@link") == -1) {
                System.out.println("Warning: @ tag seen in comment: " +
                                   firstSentence);
            }
            String firstSentenceNoTags = API.stuffHTMLTags(firstSentence);
            outputFile.println(firstSentenceNoTags);
            for (int i = 0; i < indent; i++) outputFile.print(" ");
            outputFile.println("</doc>");
        }
    }

    /** 
     * Add at least the first sentence from a doc block for a package to the API. This is
     * used by the report generator if no comment is provided.
     * The default source tree may not include the package.html files, so
     * this may be unavailable in many cases.
     * Need to make sure that HTML tags are not confused with XML tags.
     * This could be done by stuffing the &lt; character to another string
     * or by handling HTML in the parser. This second option is neater. Note that
     * XML expects all element tags to have either a closing "/>" or a matching
     * end element tag.  Due to the difficulties of converting incorrect HTML
     * to XHTML, the first option is used.
     */
    public void addPkgDocumentation(PackageElement pkg, String docComment, int indent) {
        String rct = docComment;
        if (rct == null) {
            rct = readPackageDocumentationFromHtml(pkg);
        }
        if (rct != null) {
            String pkgName = pkg.getQualifiedName().toString();
            rct = stripNonPrintingChars(rct, pkgName);
            rct = rct.trim();
            if (rct.compareTo("") != 0 &&
                rct.indexOf(Comments.placeHolderText) == -1 &&
                rct.indexOf("InsertOtherCommentsHere") == -1) {
                int idx = endOfFirstSentence(rct);
                if (idx == 0)
                    return;
                for (int i = 0; i < indent; i++) outputFile.print(" ");
                outputFile.println("<doc>");
                for (int i = 0; i < indent; i++) outputFile.print(" ");
                String firstSentence = null;
                if (idx == -1)
                    firstSentence = rct;
                else
                    firstSentence = rct.substring(0, idx+1);
                String firstSentenceNoTags = API.stuffHTMLTags(firstSentence);
                outputFile.println(firstSentenceNoTags);
                for (int i = 0; i < indent; i++) outputFile.print(" ");
                outputFile.println("</doc>");
            }
        }
    }

    private String readPackageDocumentationFromHtml(PackageElement pkg) {
        if (pkg == null) {
            return null;
        }
        String pkgName = pkg.getQualifiedName().toString();
        String filename = pkgName.replace('.', JDiff.DIR_SEP.charAt(0));
        String srcLocation = sourcePath;
        if (srcLocation != null) {
            srcLocation = resolveSourcePath(srcLocation);
            filename = srcLocation + JDiff.DIR_SEP + filename;
        }
        String attempted = filename + JDiff.DIR_SEP + "package.htm";
        BufferedReader reader = null;
        try {
            File file = new File(attempted);
            if (!file.exists()) {
                file = new File(attempted + "l");
                attempted = file.getPath();
            } else {
                attempted = file.getPath();
            }
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            StringBuilder rct = new StringBuilder();
            String str = reader.readLine();
            boolean inBody = false;
            while (str != null) {
                if (!inBody) {
                    if (str.toLowerCase().trim().startsWith("<body")) {
                        inBody = true;
                    }
                    str = reader.readLine();
                    continue;
                } else {
                    if (str.toLowerCase().trim().startsWith("</body")) {
                        inBody = false;
                        str = reader.readLine();
                        continue;
                    }
                }
                rct.append(str).append("\n");
                str = reader.readLine();
            }
            if (rct.length() == 0) {
                return null;
            }
            return rct.toString();
        } catch (FileNotFoundException e) {
            if (trace)
                System.out.println("No package level documentation file at '" + attempted + "'");
            return null;
        } catch (IOException e) {
            System.out.println("Error reading file \"" + attempted + "\": " + e.getMessage());
            System.exit(5);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    private String resolveSourcePath(String srcLocation) {
        if (srcLocation.startsWith("..")) {
            String curDir = System.getProperty("user.dir");
            String location = srcLocation;
            while (location.startsWith("..")) {
                location = location.substring(3);
                int idx = curDir.lastIndexOf(JDiff.DIR_SEP);
                if (idx == -1) {
                    break;
                }
                curDir = curDir.substring(0, idx + 1);
            }
            return curDir + location;
        }
        return srcLocation;
    }

    /** 
     * Find the index of the end of the first sentence in the given text,
     * when writing out to an XML file.
     * This is an extended version of the algorithm used by the DocCheck 
     * Javadoc doclet. It checks for @tags too.
     *
     * @param text The text to be searched.
     * @return The index of the end of the first sentence. If there is no
     *         end, return -1. If there is no useful text, return 0.
     *         If the whole doc block comment is wanted (default), return -1.
     */
    public static int endOfFirstSentence(String text) {
        return endOfFirstSentence(text, true);
    }

    /** 
     * Find the index of the end of the first sentence in the given text.
     * This is an extended version of the algorithm used by the DocCheck 
     * Javadoc doclet. It checks for &#064;tags too.
     *
     * @param text The text to be searched.
     * @param writingToXML Set to true when writing out XML.
     * @return The index of the end of the first sentence. If there is no
     *         end, return -1. If there is no useful text, return 0.
     *         If the whole doc block comment is wanted (default), return -1.
     */
    public static int endOfFirstSentence(String text, boolean writingToXML) {
        if (saveAllDocs && writingToXML)
            return -1;
	int textLen = text.length();
	if (textLen == 0)
	    return 0;
        int index = -1;
        // Handle some special cases
        int fromindex = 0;
        int ellipsis = text.indexOf(". . ."); // Handles one instance of this
        if (ellipsis != -1)
            fromindex = ellipsis + 5;
        // If the first non-whitespace character is an @, go beyond it
        int i = 0;
        while (i < textLen && text.charAt(i) == ' ') {
            i++;
        }
        if (text.charAt(i) == '@' && fromindex < textLen-1)
            fromindex = i + 1;
        // Use the brute force approach.
        index = minIndex(index, text.indexOf("? ", fromindex));
        index = minIndex(index, text.indexOf("?\t", fromindex));
        index = minIndex(index, text.indexOf("?\n", fromindex));
        index = minIndex(index, text.indexOf("?\r", fromindex));
        index = minIndex(index, text.indexOf("?\f", fromindex));
        index = minIndex(index, text.indexOf("! ", fromindex));
        index = minIndex(index, text.indexOf("!\t", fromindex));
        index = minIndex(index, text.indexOf("!\n", fromindex));
        index = minIndex(index, text.indexOf("!\r", fromindex));
        index = minIndex(index, text.indexOf("!\f", fromindex));
        index = minIndex(index, text.indexOf(". ", fromindex));
        index = minIndex(index, text.indexOf(".\t", fromindex));
        index = minIndex(index, text.indexOf(".\n", fromindex));
        index = minIndex(index, text.indexOf(".\r", fromindex));
        index = minIndex(index, text.indexOf(".\f", fromindex));
        index = minIndex(index, text.indexOf("@param", fromindex));
        index = minIndex(index, text.indexOf("@return", fromindex));
        index = minIndex(index, text.indexOf("@throw", fromindex));
        index = minIndex(index, text.indexOf("@serial", fromindex));
        index = minIndex(index, text.indexOf("@exception", fromindex));
        index = minIndex(index, text.indexOf("@deprecate", fromindex));
        index = minIndex(index, text.indexOf("@author", fromindex));
        index = minIndex(index, text.indexOf("@since", fromindex));
        index = minIndex(index, text.indexOf("@see", fromindex));
        index = minIndex(index, text.indexOf("@version", fromindex));
        if (doExclude && excludeTag != null)
            index = minIndex(index, text.indexOf(excludeTag));
        index = minIndex(index, text.indexOf("@vtexclude", fromindex));
        index = minIndex(index, text.indexOf("@vtinclude", fromindex));
        index = minIndex(index, text.indexOf("<p>", 2)); // Not at start
        index = minIndex(index, text.indexOf("<P>", 2)); // Not at start
        index = minIndex(index, text.indexOf("<blockquote", 2));  // Not at start
        index = minIndex(index, text.indexOf("<pre", fromindex)); // May contain anything!
        // Avoid the char at the start of a tag in some cases
        if (index != -1 &&  
            (text.charAt(index) == '@' || text.charAt(index) == '<')) {
            if (index != 0)
                index--;
        }
        
/* Not used for jdiff, since tags are explicitly checked for above.
        // Look for a sentence terminated by an HTML tag.
        index = minIndex(index, text.indexOf(".<", fromindex));
        if (index == -1) {
            // If period-whitespace etc was not found, check to see if
            // last character is a period,
            int endIndex = text.length()-1;
            if (text.charAt(endIndex) == '.' ||
                text.charAt(endIndex) == '?' ||
                text.charAt(endIndex) == '!') 
                index = endIndex;
        }
*/
        return index;
    }
    
    /**
     * Return the minimum of two indexes if > -1, and return -1
     * only if both indexes = -1.
     * @param i an int index
     * @param j an int index
     * @return an int equal to the minimum index > -1, or -1
     */
    public static int minIndex(int i, int j) {
        if (i == -1) return j;
        if (j == -1) return i;
        return Math.min(i,j);
    }
    
    /** 
     * The name of the file where the XML representing the API will be 
     * stored. 
     */
    public static String outputFileName = null;

    /** 
     * The identifier of the API being written out in XML, e.g. 
     * &quotSuperProduct 1.3&quot;. 
     */
    public static String apiIdentifier = null;

    /** 
     * The file where the XML representing the API will be stored. 
     */
    private static PrintWriter outputFile = null;
    
    /** 
     * The name of the directory where the XML representing the API will be 
     * stored. 
     */
    public static String outputDirectory = null;

    /** 
     * Do not display a class  with a lower level of visibility than this. 
     * Default is to display all public and protected classes.
     */
    public static String classVisibilityLevel = "protected";

    /** 
     * Do not display a member with a lower level of visibility than this. 
     * Default is to display all public and protected members 
     * (constructors, methods, fields).
     */
    public static String memberVisibilityLevel = "protected";

    /** 
     * If set, then save the entire contents of a doc block comment in the 
     * API file. If not set, then just save the first sentence. Default is 
     * that this is set.
     */
    public static boolean saveAllDocs = true;

    /** 
     * If set, exclude program elements marked with whatever the exclude tag
     * is specified as, e.g. "@exclude".
     */
    public static boolean doExclude = false;

    /** 
     * Exclude program elements marked with this String, e.g. "@exclude".
     */
    public static String excludeTag = null;

    /** 
     * The base URI for locating necessary DTDs and Schemas. By default, this 
     * is "http://www.w3.org". A typical value to use local copies of DTD files
     * might be "file:///C:/jdiff/lib"
     */
    public static String baseURI = "http://www.w3.org";

    /** 
     * If set, then strip out non-printing characters from documentation.
     * Default is that this is set.
     */
    static boolean stripNonPrintables = true;

    /** 
     * If set, then add the information about the source file and line number
     * which is available in J2SE1.4. Default is that this is not set.
     */
    static boolean addSrcInfo = false;

    /**
     * If set, scan classes with no packages.
     * If the source is  a jar file this may duplicates classes, so
     * disable it using the -packagesonly option. Default is that this is
     * not set.
     */
    static boolean packagesOnly = false;

    /** Location of source files when provided via -sourcepath. */
    static String sourcePath = null;

    /** Set to enable increased logging verbosity for debugging. */
    private static boolean trace = false;

} //RootDocToXML
