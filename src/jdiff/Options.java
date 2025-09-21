package jdiff;

import java.io.*;
import java.util.*;
import javax.tools.Diagnostic;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.Reporter;

/**
 * Class to handle options for JDiff.
 *
 * See the file LICENSE.txt for copyright details.
 * @author Matthew Doar, mdoar@pobox.com
 */
public class Options {

    private static final boolean trace = false;
    private static final Map<String, SimpleOption> OPTIONS = new LinkedHashMap<>();

    private static Reporter reporter;
    private static boolean exitAfterOptions;

    static {
        registerOption("-apiname", 1, "<name>", "Generate an XML file describing the named API.");
        registerOption("-apidir", 1, "<directory>", "Directory where the generated XML file will be written.");
        registerOption("-oldapi", 1, "<name>", "Name of the XML file describing the old API.");
        registerOption("-oldapidir", 1, "<directory>", "Directory containing the XML for the old API.");
        registerOption("-newapi", 1, "<name>", "Name of the XML file describing the new API.");
        registerOption("-newapidir", 1, "<directory>", "Directory containing the XML for the new API.");
        registerOption("-d", 1, "<directory>", "Destination directory for generated HTML reports.");
        registerOption("-javadocnew", 1, "<location>", "Location of existing Javadoc for the new API.");
        registerOption("-javadocold", 1, "<location>", "Location of existing Javadoc for the old API.");
        registerOption("-baseuri", 1, "<base>", "Base URI for locating supporting DTDs and schemas.");
        registerOption("-excludeclass", 1, "<level>", "Exclude classes below the given visibility level.");
        registerOption("-excludemember", 1, "<level>", "Exclude members below the given visibility level.");
        registerOption("-firstsentence", 0, "", "Store only the first sentence of documentation comments.");
        registerOption("-docchanges", 0, "", "Report changes in documentation comments.");
        registerOption("-incompatible", 0, "", "Report only incompatible changes.");
        registerOption("-packagesonly", 0, "", "Limit scanning to explicitly named packages.");
        registerOption("-showallchanges", 0, "", "Display all recorded changes, even compatible ones.");
        registerOption("-nosuggest", 1, "<level>", "Disable suggested comments (all|remove|add|change).");
        registerOption("-checkcomments", 0, "", "Check that documentation comments end with a period.");
        registerOption("-retainnonprinting", 0, "", "Retain non-printing characters in comments.");
        registerOption("-excludetag", 1, "<tag>", "Exclude elements containing the specified Javadoc tag.");
        registerOption("-stats", 0, "", "Generate statistical summary output.");
        registerOption("-doctitle", 1, "<html>", "Custom HTML for the report title page.");
        registerOption("-windowtitle", 1, "<text>", "Browser window title for the report.");
        registerOption("-version", 0, "", "Display the JDiff version.");
        registerOption("-help", 0, "", "Display usage information.");
    }

    /** Default constructor. */
    public Options() { }

    public static void reset(Reporter newReporter) {
        reporter = newReporter;
        cmdOptions = "";
        exitAfterOptions = false;
    }

    public static Set<Doclet.Option> getSupportedOptions() {
        return new LinkedHashSet<>(OPTIONS.values());
    }

    static boolean shouldExit() {
        return exitAfterOptions;
    }

    static boolean processOption(String option, List<String> arguments) {
        appendOption(option, arguments);
        String opt = option.toLowerCase(Locale.ROOT);
        if (trace) {
            System.out.println("Processing option: " + opt + " " + arguments);
        }
        switch (opt) {
            case "-apiname":
                return handleApiName(arguments);
            case "-apidir":
                return expectSingleValue(arguments, value -> RootDocToXML.outputDirectory = value,
                        "No directory specified after -apidir option.");
            case "-oldapi":
                return handleApiFile(arguments, true);
            case "-oldapidir":
                return expectSingleValue(arguments, value -> JDiff.oldDirectory = value,
                        "No directory specified after -oldapidir option.");
            case "-newapi":
                return handleApiFile(arguments, false);
            case "-newapidir":
                return expectSingleValue(arguments, value -> JDiff.newDirectory = value,
                        "No directory specified after -newapidir option.");
            case "-d":
                return expectSingleValue(arguments, value -> HTMLReportGenerator.outputDir = value,
                        "No directory specified after -d option.");
            case "-javadocnew":
                return expectSingleValue(arguments, value -> HTMLReportGenerator.newDocPrefix = value,
                        "No location specified after -javadocnew option.");
            case "-javadocold":
                return expectSingleValue(arguments, value -> HTMLReportGenerator.oldDocPrefix = value,
                        "No location specified after -javadocold option.");
            case "-baseuri":
                return expectSingleValue(arguments, value -> RootDocToXML.baseURI = value,
                        "No base location specified after -baseURI option.");
            case "-excludeclass":
                return handleVisibility(arguments, true);
            case "-excludemember":
                return handleVisibility(arguments, false);
            case "-firstsentence":
                RootDocToXML.saveAllDocs = false;
                return true;
            case "-docchanges":
                HTMLReportGenerator.reportDocChanges = true;
                Diff.noDocDiffs = false;
                return true;
            case "-incompatible":
                HTMLReportGenerator.incompatibleChangesOnly = true;
                return true;
            case "-packagesonly":
                RootDocToXML.packagesOnly = true;
                return true;
            case "-showallchanges":
                Diff.showAllChanges = true;
                return true;
            case "-nosuggest":
                return handleNoSuggest(arguments);
            case "-checkcomments":
                APIHandler.checkIsSentence = true;
                return true;
            case "-retainnonprinting":
                RootDocToXML.stripNonPrintables = false;
                return true;
            case "-excludetag":
                return expectSingleValue(arguments, value -> {
                    RootDocToXML.excludeTag = value.trim();
                    RootDocToXML.doExclude = true;
                }, "No exclude tag specified after -excludetag option.");
            case "-stats":
                HTMLReportGenerator.doStats = true;
                return true;
            case "-doctitle":
                return expectSingleValue(arguments, value -> HTMLReportGenerator.docTitle = value,
                        "No HTML text specified after -doctitle option.");
            case "-windowtitle":
                return expectSingleValue(arguments, value -> HTMLReportGenerator.windowTitle = value,
                        "No text specified after -windowtitle option.");
            case "-version":
                System.out.println("JDiff version: " + JDiff.version);
                exitAfterOptions = true;
                return true;
            case "-help":
                usage();
                exitAfterOptions = true;
                return true;
            default:
                // Should not happen since options are pre-registered.
                return true;
        }
    }

    private static boolean handleApiName(List<String> arguments) {
        if (arguments.isEmpty()) {
            reportError("No version identifier specified after -apiname option.");
            return false;
        }
        if (JDiff.compareAPIs) {
            reportError("Use the -apiname option, or the -oldapi and -newapi options, but not both.");
            return false;
        }
        String filename = arguments.get(0);
        RootDocToXML.apiIdentifier = filename;
        filename = filename.replace(' ', '_');
        RootDocToXML.outputFileName = filename + ".xml";
        JDiff.writeXML = true;
        JDiff.compareAPIs = false;
        return true;
    }

    private static boolean handleApiFile(List<String> arguments, boolean oldApi) {
        if (arguments.isEmpty()) {
            reportError("No version identifier specified after " + (oldApi ? "-oldapi" : "-newapi") + " option.");
            return false;
        }
        if (JDiff.writeXML) {
            reportError("Use the -apiname or " + (oldApi ? "-oldapi" : "-newapi") + " option, but not both.");
            return false;
        }
        String filename = arguments.get(0).replace(' ', '_');
        if (oldApi) {
            JDiff.oldFileName = filename + ".xml";
        } else {
            JDiff.newFileName = filename + ".xml";
        }
        JDiff.writeXML = false;
        JDiff.compareAPIs = true;
        return true;
    }

    private static boolean handleVisibility(List<String> arguments, boolean forClass) {
        if (arguments.isEmpty()) {
            reportError("No level (public|protected|package|private) specified after " +
                    (forClass ? "-excludeclass" : "-excludemember") + " option.");
            return false;
        }
        String level = arguments.get(0);
        if (!level.equals("public") && !level.equals("protected") &&
            !level.equals("package") && !level.equals("private")) {
            reportError("Level specified after " + (forClass ? "-excludeclass" : "-excludemember") +
                    " option must be one of (public|protected|package|private).");
            return false;
        }
        if (forClass) {
            RootDocToXML.classVisibilityLevel = level;
        } else {
            RootDocToXML.memberVisibilityLevel = level;
        }
        return true;
    }

    private static boolean handleNoSuggest(List<String> arguments) {
        if (arguments.isEmpty()) {
            reportError("No level (all|remove|add|change) specified after -nosuggest option.");
            return false;
        }
        String level = arguments.get(0).toLowerCase(Locale.ROOT);
        switch (level) {
            case "all":
                HTMLReportGenerator.noCommentsOnRemovals = true;
                HTMLReportGenerator.noCommentsOnAdditions = true;
                HTMLReportGenerator.noCommentsOnChanges = true;
                break;
            case "remove":
                HTMLReportGenerator.noCommentsOnRemovals = true;
                break;
            case "add":
                HTMLReportGenerator.noCommentsOnAdditions = true;
                break;
            case "change":
                HTMLReportGenerator.noCommentsOnChanges = true;
                break;
            default:
                reportError("Level specified after -nosuggest option must be one of (all|remove|add|change).");
                return false;
        }
        return true;
    }

    private static boolean expectSingleValue(List<String> arguments, ValueConsumer consumer, String errorMessage) {
        if (arguments.isEmpty()) {
            reportError(errorMessage);
            return false;
        }
        consumer.accept(arguments.get(0));
        return true;
    }

    private static void appendOption(String option, List<String> arguments) {
        cmdOptions += " " + option;
        for (String arg : arguments) {
            cmdOptions += " " + arg;
        }
    }

    private static void reportError(String message) {
        if (reporter != null) {
            reporter.print(Diagnostic.Kind.ERROR, message);
        } else {
            System.err.println(message);
        }
    }

    /** Display the arguments for JDiff. */
    public static void usage() {
        System.err.println("JDiff version: " + JDiff.version);
        System.err.println("");
        System.err.println("Valid JDiff arguments:");
        System.err.println("");
        System.err.println("  -apiname <Name of a version>");
        System.err.println("  -oldapi <Name of a version>");
        System.err.println("  -newapi <Name of a version>");

        System.err.println("  Optional Arguments");
        System.err.println();
        System.err.println("  -d <directory> Destination directory for output HTML files");
        System.err.println("  -apidir <directory> Destination directory for the XML file generated with the '-apiname' argument.");
        System.err.println("  -oldapidir <directory> Location of the XML file for the old API");
        System.err.println("  -newapidir <directory> Location of the XML file for the new API");
        System.err.println("  -sourcepath <location of Java source files>");
        System.err.println("  -javadocnew <location of existing Javadoc files for the new API>");
        System.err.println("  -javadocold <location of existing Javadoc files for the old API>");

        System.err.println("  -baseURI <base> Use \"base\" as the base location of the various DTDs and Schemas used by JDiff");
        System.err.println("  -excludeclass [public|protected|package|private] Exclude classes which are not public, protected etc");
        System.err.println("  -excludemember [public|protected|package|private] Exclude members which are not public, protected etc");

        System.err.println("  -firstsentence Save only the first sentence of each comment block with the API.");
        System.err.println("  -docchanges Report changes in Javadoc comments between the APIs");
        System.err.println("  -incompatible Only report incompatible changes");
        System.err.println("  -nosuggest [all|remove|add|change] Do not add suggested comments to all, or the removed, added or chabged sections");
        System.err.println("  -checkcomments Check that comments are sentences");
        System.err.println("  -stripnonprinting Remove non-printable characters from comments.");
        System.err.println("  -excludetag <tag> Define the Javadoc tag which implies exclusion");
        System.err.println("  -stats Generate statistical output");
        System.err.println("  -help       (generates this output)");
        System.err.println("");
        System.err.println("For more help, see jdiff.html");
    }

    /** All the options passed on the command line. Logged to XML. */
    public static String cmdOptions = "";

    private interface ValueConsumer {
        void accept(String value);
    }

    private static void registerOption(String name, int argumentCount, String parameters, String description) {
        OPTIONS.put(name, new SimpleOption(name, argumentCount, parameters, description));
    }

    private static final class SimpleOption implements Doclet.Option {
        private final String name;
        private final int argumentCount;
        private final String parameters;
        private final String description;

        SimpleOption(String name, int argumentCount, String parameters, String description) {
            this.name = name;
            this.argumentCount = argumentCount;
            this.parameters = parameters;
            this.description = description;
        }

        @Override
        public int getArgumentCount() {
            return argumentCount;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public Kind getKind() {
            return Kind.OTHER;
        }

        @Override
        public List<String> getNames() {
            return Collections.singletonList(name);
        }

        @Override
        public String getParameters() {
            return parameters;
        }

        @Override
        public boolean process(String option, List<String> arguments) {
            return Options.processOption(option, arguments);
        }
    }
}
