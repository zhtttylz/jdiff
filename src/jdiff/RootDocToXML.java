package jdiff;

import java.util.*;
import java.io.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LineMap;
import com.sun.source.util.DocTrees;
import com.sun.source.util.SourcePositions;
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
    private final Elements elements;
    private final Types types;

    private static PrintWriter outputFile = null;

    /** Default constructor. */
    public RootDocToXML(DocletEnvironment environment) {
        this.environment = environment;
        this.docTrees = environment.getDocTrees();
        this.elements = environment.getElementUtils();
        this.types = environment.getTypeUtils();
    }

    /**
     * Write the XML representation of the API to a file.
     *
     * @param environment the DocletEnvironment supplied by Javadoc
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
            if (environment != null) {
                RootDocToXML apiWriter = new RootDocToXML(environment);
                if (apiWriter.hasIncludedTypes()) {
                    apiWriter.emitXMLHeader();
                    apiWriter.logOptions();
                    apiWriter.processPackages();
                    apiWriter.emitXMLFooter();
                }
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

    private boolean hasIncludedTypes() {
        if (environment == null) {
            return false;
        }
        for (Element element : environment.getIncludedElements()) {
            if (element instanceof TypeElement) {
                return true;
            }
        }
        return false;
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
     */
    public void processPackages() {
        Map<String, PackageElement> packageMap = new TreeMap<String, PackageElement>();
        Map<String, List<TypeElement>> classesByPackage = new HashMap<String, List<TypeElement>>();
        Map<String, List<TypeElement>> specifiedClasses = new HashMap<String, List<TypeElement>>();

        for (Element element : environment.getSpecifiedElements()) {
            if (element instanceof PackageElement) {
                PackageElement pkg = (PackageElement) element;
                String pkgName = pkg.getQualifiedName().toString();
                packageMap.put(pkgName, pkg);
            } else if (element instanceof TypeElement) {
                TypeElement type = (TypeElement) element;
                PackageElement pkg = safeGetPackageOf(type);
                String pkgName = packageNameOf(type, pkg);
                if (pkg != null || !packageMap.containsKey(pkgName)) {
                    packageMap.put(pkgName, pkg);
                }
                specifiedClasses.computeIfAbsent(pkgName, k -> new ArrayList<TypeElement>()).add(type);
            }
        }

        for (Element element : environment.getIncludedElements()) {
            if (element instanceof PackageElement) {
                PackageElement pkg = (PackageElement) element;
                String pkgName = pkg.getQualifiedName().toString();
                packageMap.put(pkgName, pkg);
            } else if (element instanceof TypeElement) {
                TypeElement type = (TypeElement) element;
                PackageElement pkg = safeGetPackageOf(type);
                String pkgName = packageNameOf(type, pkg);
                if (pkgName.length() == 0 && packagesOnly) {
                    continue;
                }
                if (pkg != null || !packageMap.containsKey(pkgName)) {
                    packageMap.put(pkgName, pkg);
                }
                classesByPackage.computeIfAbsent(pkgName, k -> new ArrayList<TypeElement>()).add(type);
            }
        }

        List<String> packageNames = new ArrayList<String>(packageMap.keySet());
        Collections.sort(packageNames);
        for (String pkgName : packageNames) {
            PackageElement pkg = packageMap.get(pkgName);
            if (pkgName.length() == 0 && packagesOnly) {
                continue;
            }
            String pkgComment = safeGetDocComment(pkg);
            if (!shownElement(pkg, pkgComment, null))
                continue;

            if (trace) System.out.println("PROCESSING PACKAGE: " + pkgName);
            outputFile.println("<package name=\"" + pkgName + "\">");

            List<TypeElement> classList;
            if (specifiedClasses.containsKey(pkgName)) {
                if (trace) System.out.println("Using the specified classes");
                classList = new ArrayList<TypeElement>(specifiedClasses.get(pkgName));
            } else {
                List<TypeElement> included = classesByPackage.get(pkgName);
                if (included != null) {
                    classList = new ArrayList<TypeElement>(included);
                } else {
                    classList = new ArrayList<TypeElement>();
                }
            }
            processClasses(classList, pkgName);

            addPkgDocumentation(pkg, pkgComment, 2);

            outputFile.println("</package>");
        }
    } // processPackages


    private PackageElement safeGetPackageOf(TypeElement type) {
        try {
            return elements.getPackageOf(type);
        } catch (IllegalArgumentException e) {
            if (trace) {
                System.out.println("Unable to resolve package for type '" + type + "': " + e);
            }
            return null;
        }
    }

    private String safeGetDocComment(Element element) {
        if (element == null) {
            return null;
        }

        if (element.getKind() == ElementKind.MODULE) {
            if (trace) {
                System.out.println("Skipping doc comment lookup for module '" + element + "'");
            }
            return null;
        }

        try {
            return elements.getDocComment(element);
        } catch (IllegalArgumentException e) {
            if (trace) {
                System.out.println("Unable to resolve doc comment for element '" + element + "': " + e);
            }
            return null;
        }
    }

    private String packageNameOf(TypeElement type, PackageElement pkg) {
        if (pkg != null) {
            return pkg.getQualifiedName().toString();
        }
        Element enclosing = type.getEnclosingElement();
        while (enclosing != null && !(enclosing instanceof PackageElement)) {
            enclosing = enclosing.getEnclosingElement();
        }
        if (enclosing instanceof PackageElement) {
            return ((PackageElement) enclosing).getQualifiedName().toString();
        }
        String qualifiedName = type.getQualifiedName().toString();
        int lastDot = qualifiedName.lastIndexOf('.');
        return (lastDot >= 0) ? qualifiedName.substring(0, lastDot) : "";
    }


    /**
     * Process classes and interfaces.
     */
    public void processClasses(List<TypeElement> classes, String pkgName) {
        if (classes == null || classes.isEmpty())
            return;
        classes.sort(Comparator.comparing(type -> type.getSimpleName().toString()));
        for (TypeElement type : classes) {
            String docComment = safeGetDocComment(type);
            if (!shownElement(type, docComment, classVisibilityLevel))
                continue;
            boolean isInterface = type.getKind().isInterface();
            if (trace) System.out.println("PROCESSING CLASS/IFC: " + type.getSimpleName());
            if (isInterface) {
                outputFile.println("  <!-- start interface " + pkgName + "." + type.getSimpleName() + " -->");
                outputFile.print("  <interface name=\"" + type.getSimpleName() + "\"");
            } else {
                outputFile.println("  <!-- start class " + pkgName + "." + type.getSimpleName() + " -->");
                outputFile.print("  <class name=\"" + type.getSimpleName() + "\"");
            }
            TypeMirror parent = type.getSuperclass();
            if (parent != null && parent.getKind() != TypeKind.NONE) {
                String parentString = buildEmittableTypeString(parent);
                if (parentString != null && parentString.length() != 0 &&
                    !"java.lang.Object".equals(types.erasure(parent).toString())) {
                    outputFile.println(" extends=\"" + parentString + "\"");
                }
            }
            outputFile.println("    abstract=\"" + type.getModifiers().contains(Modifier.ABSTRACT) + "\"");
            addCommonModifiers(type, docComment, 4);
            outputFile.println(">");

            processInterfaces(type.getInterfaces());
            processConstructors(type);
            processMethods(type);
            processFields(type);

            addDocumentation(type, docComment, 4);

            if (isInterface) {
                outputFile.println("  </interface>");
                outputFile.println("  <!-- end interface " + pkgName + "." + type.getSimpleName() + " -->");
            } else {
                outputFile.println("  </class>");
                outputFile.println("  <!-- end class " + pkgName + "." + type.getSimpleName() + " -->");
            }
        }
    }//processClasses()

    /**
     * Process the interfaces implemented by the class.
     */
    public void processInterfaces(List<? extends TypeMirror> ifaces) {
        if (ifaces == null)
            return;
        if (trace) System.out.println("PROCESSING INTERFACES, number=" + ifaces.size());
        for (TypeMirror iface : ifaces) {
            String ifaceName = buildEmittableTypeString(iface);
            if (trace) System.out.println("PROCESSING INTERFACE: " + ifaceName);
            outputFile.println("    <implements name=\"" + ifaceName + "\"/>");
        }//for
    }//processInterfaces()

    /**
     * Process the constructors in the class.
     */
    public void processConstructors(TypeElement type) {
        List<ExecutableElement> ctors = new ArrayList<ExecutableElement>();
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.CONSTRUCTOR) {
                ctors.add((ExecutableElement) enclosed);
            }
        }
        if (trace) System.out.println("PROCESSING CONSTRUCTORS, number=" + ctors.size());
        for (ExecutableElement ctor : ctors) {
            String ctorName = ctor.getSimpleName().toString();
            if (ctorName.isEmpty() || ctorName.indexOf('<') != -1 || ctorName.indexOf('>') != -1) {
                ctorName = type.getSimpleName().toString();
            }
            if (ctorName.isEmpty()) {
                ctorName = type.toString();
            }
            ctorName = ctorName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            if (trace) System.out.println("PROCESSING CONSTRUCTOR: " + ctorName);
            String docComment = safeGetDocComment(ctor);
            if (!shownElement(ctor, docComment, memberVisibilityLevel))
                continue;
            outputFile.print("    <constructor name=\"" + ctorName + "\"");

            List<? extends VariableElement> params = ctor.getParameters();
            if (!params.isEmpty()) {
                outputFile.print(" type=\"");
                for (int j = 0; j < params.size(); j++) {
                    if (j != 0)
                        outputFile.print(", ");
                    outputFile.print(parameterTypeToString(ctor, params.get(j), j == params.size() - 1));
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
        }//for
    }//processConstructors()

    private String parameterTypeToString(ExecutableElement method, VariableElement param, boolean lastParam) {
        TypeMirror type = param.asType();
        if (method.isVarArgs() && lastParam && type.getKind() == TypeKind.ARRAY) {
            TypeMirror component = ((ArrayType) type).getComponentType();
            return buildEmittableTypeString(component) + "...";
        }
        return buildEmittableTypeString(type);
    }

    /**
     * Process all exceptions thrown by a constructor or method.
     */
    public void processExceptions(List<? extends TypeMirror> thrown) {
        if (thrown == null)
            return;
        if (trace) System.out.println("PROCESSING EXCEPTIONS, number=" + thrown.size());
        for (TypeMirror type : thrown) {
            String exceptionName = simpleTypeName(type);
            if (trace) System.out.println("PROCESSING EXCEPTION: " + exceptionName);
            outputFile.print("      <exception name=\"" + exceptionName + "\" type=\"");
            emitType(type);
            outputFile.println("\"/>");
        }//for
    }//processExceptions()
    /**
     * Process the methods in the class.
     */
    public void processMethods(TypeElement type) {
        List<ExecutableElement> methods = new ArrayList<ExecutableElement>();
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD) {
                methods.add((ExecutableElement) enclosed);
            }
        }
        if (trace) System.out.println("PROCESSING " + type.getSimpleName() + " METHODS, number = " + methods.size());
        for (ExecutableElement method : methods) {
            String methodName = method.getSimpleName().toString();
            if (trace) System.out.println("PROCESSING METHOD: " + methodName);
            String docComment = safeGetDocComment(method);
            if (!shownElement(method, docComment, memberVisibilityLevel))
                continue;
            outputFile.print("    <method name=\"" + methodName + "\"");
            TypeMirror retType = method.getReturnType();
            if (retType.getKind() == TypeKind.VOID) {
                outputFile.println();
            } else {
                outputFile.print(" return=\"");
                emitType(retType);
                outputFile.println("\"");
            }
            outputFile.print("      abstract=\"" + method.getModifiers().contains(Modifier.ABSTRACT) + "\"");
            outputFile.print(" native=\"" + method.getModifiers().contains(Modifier.NATIVE) + "\"");
            outputFile.println(" synchronized=\"" + method.getModifiers().contains(Modifier.SYNCHRONIZED) + "\"");
            addCommonModifiers(method, docComment, 6);
            outputFile.println(">");

            List<? extends VariableElement> params = method.getParameters();
            for (int j = 0; j < params.size(); j++) {
                VariableElement param = params.get(j);
                outputFile.print("      <param name=\"" + param.getSimpleName() + "\"");
                outputFile.print(" type=\"");
                outputFile.print(parameterTypeToString(method, param, j == params.size() - 1));
                outputFile.println("\"/>");
            }

            processExceptions(method.getThrownTypes());

            addDocumentation(method, docComment, 6);

            outputFile.println("    </method>");
        }//for
    }//processMethods()

    /**
     * Process the fields in the class.
     */
    public void processFields(TypeElement type) {
        List<VariableElement> fields = new ArrayList<VariableElement>();
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                fields.add((VariableElement) enclosed);
            }
        }
        if (trace) System.out.println("PROCESSING FIELDS, number=" + fields.size());
        for (VariableElement field : fields) {
            String fieldName = field.getSimpleName().toString();
            if (trace) System.out.println("PROCESSING FIELD: " + fieldName);
            String docComment = safeGetDocComment(field);
            if (!shownElement(field, docComment, memberVisibilityLevel))
                continue;
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

        }//for
    }//processFields()

    /**
     * Emit the type name. Removed any prefixed warnings about ambiguity.
     * The type maybe an array.
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
     */
    private String buildEmittableTypeString(TypeMirror type) {
        if (type == null) {
            return null;
        }
        String name = type.toString()
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
        if (name.startsWith("<<ambiguous>>")) {
            name = name.substring(13);
        }
        return name;
    }

    private String simpleTypeName(TypeMirror type) {
        Element element = types.asElement(type);
        if (element != null) {
            return element.getSimpleName().toString();
        }
        String name = type.toString();
        int idx = name.lastIndexOf('.') + 1;
        if (idx > 0 && idx < name.length()) {
            return name.substring(idx);
        }
        return name;
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
     */
    public boolean shownElement(Element element, String docComment, String visLevel) {
        if (doExclude && excludeTag != null && docComment != null) {
            if (docComment.indexOf(excludeTag) != -1) {
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
    } //shownElement()

    /**
     * Insert the source code details, if available.
     */
    public void addSourcePosition(Element element, int indent) {
        if (!addSrcInfo)
            return;
        if (docTrees == null)
            return;
        TreePath path = docTrees.getPath(element);
        if (path == null)
            return;
        CompilationUnitTree cu = path.getCompilationUnit();
        if (cu == null)
            return;
        JavaFileObject fileObject = cu.getSourceFile();
        if (fileObject == null)
            return;
        SourcePositions positions = docTrees.getSourcePositions();
        long pos = positions.getStartPosition(cu, path.getLeaf());
        if (pos == Diagnostic.NOPOS)
            return;
        LineMap lineMap = cu.getLineMap();
        if (lineMap == null)
            return;
        long line = lineMap.getLineNumber(pos);
        for (int i = 0; i < indent; i++) outputFile.print(" ");
        outputFile.println("src=\"" + fileObject.getName() + ":" + line + "\"");
    }

    /**
     * Add qualifiers for the program element as attributes.
     */
    public void addCommonModifiers(Element element, String docComment, int indent) {
        addSourcePosition(element, indent);
        for (int i = 0; i < indent; i++) outputFile.print(" ");
        Set<Modifier> modifiers = element.getModifiers();
        outputFile.print("static=\"" + modifiers.contains(Modifier.STATIC) + "\"");
        outputFile.print(" final=\"" + modifiers.contains(Modifier.FINAL) + "\"");
        String visibility = null;
        if (modifiers.contains(Modifier.PUBLIC))
            visibility = "public";
        else if (modifiers.contains(Modifier.PROTECTED))
            visibility = "protected";
        else if (modifiers.contains(Modifier.PRIVATE))
            visibility = "private";
        else
            visibility = "package";
        outputFile.println(" visibility=\"" + visibility + "\"");

        for (int i = 0; i < indent; i++) outputFile.print(" ");
        String deprecatedText = extractDeprecated(docComment);
        if (deprecatedText != null) {
            if (deprecatedText.length() == 0) {
                outputFile.print("deprecated=\"deprecated, no comment\"");
            } else {
                int idx = endOfFirstSentence(deprecatedText);
                String fs = (idx == -1) ? deprecatedText : deprecatedText.substring(0, idx + 1);
                String st = API.hideHTMLTags(fs);
                outputFile.print("deprecated=\"" + st + "\"");
            }
        } else {
            outputFile.print("deprecated=\"not deprecated\"");
        }
    } //addQualifiers()

    private String extractDeprecated(String docComment) {
        if (docComment == null)
            return null;
        int idx = docComment.indexOf("@deprecated");
        if (idx == -1)
            return null;
        int start = idx + "@deprecated".length();
        int len = docComment.length();
        while (start < len) {
            char ch = docComment.charAt(start);
            if (!Character.isWhitespace(ch))
                break;
            start++;
        }
        int end = start;
        while (end < len) {
            char ch = docComment.charAt(end);
            if (ch == '@' && end > start) {
                char prev = docComment.charAt(end - 1);
                if (prev == '\n' || prev == '\r') {
                    break;
                }
            }
            end++;
        }
        return docComment.substring(start, end).trim();
    }

    /**
     * Add at least the first sentence from a doc block to the API.
     */
    public void addDocumentation(Element element, String docComment, int indent) {
        String rct = docComment;
        if (rct != null) {
            rct = stripNonPrintingChars(rct, element);
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
    }

    /**
     * Add at least the first sentence from a doc block for a package to the API.
     */
    public void addPkgDocumentation(PackageElement pkg, String pkgComment, int indent) {
        String rct = pkgComment;
        if (rct == null) {
            rct = readPackageFile(pkg);
        }
        if (rct != null) {
            rct = stripNonPrintingChars(rct, pkg);
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

    private String readPackageFile(PackageElement pkg) {
        if (pkg == null || Options.sourcePath == null)
            return null;
        String pkgName = pkg.getQualifiedName().toString();
        String pkgPath = pkgName.replace('.', JDiff.DIR_SEP.charAt(0));
        String[] roots = Options.sourcePath.split(File.pathSeparator);
        for (String root : roots) {
            if (root == null || root.length() == 0)
                continue;
            File base = new File(root);
            if (!base.isAbsolute()) {
                base = new File(System.getProperty("user.dir"), root);
            }
            try {
                base = base.getCanonicalFile();
            } catch (IOException e) {
                base = base.getAbsoluteFile();
            }
            File pkgDir = pkgPath.length() == 0 ? base : new File(base, pkgPath);
            String content = readPackageContent(new File(pkgDir, "package.html"));
            if (content == null) {
                content = readPackageContent(new File(pkgDir, "package.htm"));
            }
            if (content != null) {
                return content;
            }
        }
        if (trace)
            System.out.println("No package level documentation file for '" + pkgName + "'");
        return null;
    }

    private String readPackageContent(File file) {
        if (!file.exists()) {
            return null;
        }
        StringBuilder rct = new StringBuilder();
        try {
            FileInputStream f = new FileInputStream(file);
            BufferedReader d = new BufferedReader(new InputStreamReader(f));
            String str = d.readLine();
            boolean inBody = false;
            while(str != null) {
                if (!inBody) {
                    if (str.toLowerCase().trim().startsWith("<body")) {
                        inBody = true;
                    }
                    str = d.readLine();
                    continue;
                } else {
                    if (str.toLowerCase().trim().startsWith("</body")) {
                        inBody = false;
                        str = d.readLine();
                        continue;
                    }
                }
                rct.append(str).append('\n');
                str = d.readLine();
            }
            d.close();
        } catch(IOException e) {
            System.out.println("Error reading file \"" + file + "\": " + e.getMessage());
            System.exit(5);
        }
        return rct.length() == 0 ? null : rct.toString();
    }
    /**
     * Strip out non-printing characters, replacing them with a character
     * which will not change where the end of the first sentence is found.
     * This character is the hash mark, '&#035;'.
     */
    public String stripNonPrintingChars(String s, Element element) {
        if (!stripNonPrintables)
            return s;
        char[] sa = s.toCharArray();
        for (int i = 0; i < sa.length; i++) {
            char c = sa[i];
            if (Character.isLetterOrDigit(c))
                continue;
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
                c == '`')
                continue;
            sa[i] = '#';
        }
        return new String(sa);
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
     * Find the index of the end of the first sentence in the given text,
     * when writing out to an XML file.
     */
    public static int endOfFirstSentence(String text) {
        return endOfFirstSentence(text, true);
    }

    /**
     * Find the index of the end of the first sentence in the given text.
     */
    public static int endOfFirstSentence(String text, boolean writingToXML) {
        if (saveAllDocs && writingToXML)
            return -1;
        int textLen = text.length();
        if (textLen == 0)
            return 0;
        int index = -1;
        int fromindex = 0;
        int ellipsis = text.indexOf(". . .");
        if (ellipsis != -1)
            fromindex = ellipsis + 5;
        int i = 0;
        while (i < textLen && text.charAt(i) == ' ') {
            i++;
        }
        if (i < textLen && text.charAt(i) == '@' && fromindex < textLen-1)
            fromindex = i + 1;
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
        index = minIndex(index, text.indexOf("<p>", 2));
        index = minIndex(index, text.indexOf("<P>", 2));
        index = minIndex(index, text.indexOf("<blockquote", 2));
        index = minIndex(index, text.indexOf("<pre", fromindex));
        if (index != -1 &&
            (text.charAt(index) == '@' || text.charAt(index) == '<')) {
            if (index != 0)
                index--;
        }
        return index;
    }

    /**
     * Return the minimum of two indexes if > -1, and return -1
     * only if both indexes = -1.
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
     * is "http://www.w3.org".
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

    /** Set to enable increased logging verbosity for debugging. */
    private static boolean trace = false;

} //RootDocToXML
