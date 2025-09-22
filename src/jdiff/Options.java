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

    private static final Map<String, Integer> OPTION_LENGTHS = createOptionLengths();
    private static final List<Doclet.Option> SUPPORTED_OPTIONS = createSupportedOptions();
    private static final List<String[]> pendingOptions = new ArrayList<String[]>();

    private static Reporter reporter;

    /** All the options passed on the command line. Logged to XML. */
    public static String cmdOptions = "";

    /** The value of the -sourcepath option, if provided. */
    public static String sourcePath = null;

    /** Set to enable increased logging verbosity for debugging. */
    private static boolean trace = false;

    /** Default constructor. */
    public Options() {
    }

    public static void setReporter(Reporter reporter) {
        Options.reporter = reporter;
    }

    public static void reset() {
        cmdOptions = "";
        sourcePath = null;
        pendingOptions.clear();
        JDiff.writeXML = false;
        JDiff.compareAPIs = false;
        RootDocToXML.outputFileName = null;
        RootDocToXML.apiIdentifier = null;
        RootDocToXML.outputDirectory = null;
        RootDocToXML.classVisibilityLevel = "protected";
        RootDocToXML.memberVisibilityLevel = "protected";
        RootDocToXML.saveAllDocs = true;
        RootDocToXML.doExclude = false;
        RootDocToXML.excludeTag = null;
        RootDocToXML.baseURI = "http://www.w3.org";
        RootDocToXML.stripNonPrintables = true;
        RootDocToXML.addSrcInfo = false;
        RootDocToXML.packagesOnly = false;
        HTMLReportGenerator.outputDir = null;
        HTMLReportGenerator.newDocPrefix = "../";
        HTMLReportGenerator.oldDocPrefix = null;
        HTMLReportGenerator.reportDocChanges = false;
        HTMLReportGenerator.incompatibleChangesOnly = false;
        HTMLReportGenerator.noCommentsOnRemovals = false;
        HTMLReportGenerator.noCommentsOnAdditions = false;
        HTMLReportGenerator.noCommentsOnChanges = false;
        HTMLReportGenerator.doStats = false;
        HTMLReportGenerator.docTitle = null;
        HTMLReportGenerator.windowTitle = null;
        Diff.noDocDiffs = true;
        Diff.showAllChanges = false;
    }

    public static Collection<Doclet.Option> getSupportedOptions() {
        return SUPPORTED_OPTIONS;
    }

    /**
     * Returns the "length" of a given option. If an option takes no
     * arguments, its length is one. If it takes one argument, its
     * length is two, and so on. This method is called by Javadoc to
     * parse the options it does not recognize. It then calls
     * {@link #validOptions} to validate them.
     * <blockquote>
     * <b>Note:</b><br>
     * The options arrive as case-sensitive strings. For options that
     * are not case-sensitive, use toLowerCase() on the option string
     * before comparing it.
     * </blockquote>
     *
     * @param option  a String containing an option
     * @return an int telling how many components that option has
     */
    public static int optionLength(String option) {
        if (option == null) {
            return 0;
        }
        Integer len = OPTION_LENGTHS.get(option.toLowerCase());
        return len == null ? 0 : len.intValue();
    }

   /**
    * After parsing the available options using {@link #optionLength},
    * Javadoc invokes this method with an array of options-arrays, where
    * the first item in any array is the option, and subsequent items in
    * that array are its arguments. So, if -print is an option that takes
    * no arguments, and -copies is an option that takes 1 argument, then
    * <pre>
    *     -print -copies 3
    * </pre>
    * produces an array of arrays that looks like:
    * <pre>
    *      option[0][0] = -print
    *      option[1][0] = -copies
    *      option[1][1] = 3
    * </pre>
    * (By convention, command line switches start with a "-", but
    * they don't have to.)
    * <p>
    * <b>Note:</b><br>
    * Javadoc passes <i>all</i>parameters to this method, not just
    * those that Javadoc doesn't recognize. The only way to
    * identify unexpected arguments is therefore to check for every
    * Javadoc parameter as well as doclet parameters.
    *
    * @param options   an array of String arrays, one per option
    * @param reporter  a Reporter for generating error messages
    * @return true if no errors were found, and all options are
    *         valid
    */
    public static boolean validOptions(String[][] options,
                                       Reporter reporter) {
        reset();
        setReporter(reporter);
        if (options != null) {
            for (int i = 0; i < options.length; i++) {
                pendingOptions.add(options[i]);
            }
        }
        return handleOptions();
    }

    public static boolean processPendingOptions(Reporter reporter) {
        if (reporter != null) {
            setReporter(reporter);
        }
        return handleOptions();
    }

    private static boolean handleOptions() {
        ErrorHandler err = new ErrorHandler();
        if (trace)
            System.out.println("Command line arguments: ");
        for (int i = 0; i < pendingOptions.size(); i++) {
            String[] opt = pendingOptions.get(i);
            for (int j = 0; j < opt.length; j++) {
                cmdOptions += " " + opt[j];
                if (trace)
                    System.out.print(" " + opt[j]);
            }
        }
        if (trace)
            System.out.println();

        for (int i = 0; i < pendingOptions.size(); i++) {
            String[] option = pendingOptions.get(i);
            if (option.length == 0) {
                continue;
            }
            String name = option[0].toLowerCase();
            if (name.equals("-apiname")) {
                if (option.length < 2) {
                    err.msg("No version identifier specified after -apiname option.");
                } else if (JDiff.compareAPIs) {
                    err.msg("Use the -apiname option, or the -oldapi and -newapi options, but not both.");
                } else {
                    String filename = option[1];
                    RootDocToXML.apiIdentifier = filename;
                    filename = filename.replace(' ', '_');
                    RootDocToXML.outputFileName =  filename + ".xml";
                    JDiff.writeXML = true;
                    JDiff.compareAPIs = false;
                }
                continue;
            }
            if (name.equals("-apidir")) {
                if (option.length < 2) {
                    err.msg("No directory specified after -apidir option.");
                } else {
                    RootDocToXML.outputDirectory = option[1];
                }
                continue;
            }
            if (name.equals("-oldapi")) {
                if (option.length < 2) {
                    err.msg("No version identifier specified after -oldapi option.");
                } else if (JDiff.writeXML) {
                    err.msg("Use the -apiname or -oldapi option, but not both.");
                } else {
                    String filename = option[1];
                    filename = filename.replace(' ', '_');
                    JDiff.oldFileName =  filename + ".xml";
                    JDiff.writeXML = false;
                    JDiff.compareAPIs = true;
                }
                continue;
            }
            if (name.equals("-oldapidir")) {
                if (option.length < 2) {
                    err.msg("No directory specified after -oldapidir option.");
                } else {
                        JDiff.oldDirectory = option[1];
                }
                continue;
            }
            if (name.equals("-newapi")) {
                if (option.length < 2) {
                    err.msg("No version identifier specified after -newapi option.");
                } else if (JDiff.writeXML) {
                    err.msg("Use the -apiname or -newapi option, but not both.");
                } else {
                    String filename = option[1];
                    filename = filename.replace(' ', '_');
                    JDiff.newFileName =  filename + ".xml";
                    JDiff.writeXML = false;
                    JDiff.compareAPIs = true;
                }
                continue;
            }
            if (name.equals("-newapidir")) {
                if (option.length < 2) {
                    err.msg("No directory specified after -newapidir option.");
                } else {
                        JDiff.newDirectory = option[1];
                }
                continue;
            }
            if (name.equals("-d")) {
                if (option.length < 2) {
                    err.msg("No directory specified after -d option.");
                } else {
                    HTMLReportGenerator.outputDir = option[1];
                }
                continue;
            }
            if (name.equals("-sourcepath")) {
                if (option.length < 2) {
                    err.msg("No location specified after -sourcepath option.");
                } else {
                    sourcePath = option[1];
                }
                continue;
            }
            if (name.equals("-javadocnew")) {
                if (option.length < 2) {
                    err.msg("No location specified after -javadocnew option.");
                } else {
                    HTMLReportGenerator.newDocPrefix = option[1];
                }
                continue;
            }
            if (name.equals("-javadocold")) {
                if (option.length < 2) {
                    err.msg("No location specified after -javadocold option.");
                } else {
                    HTMLReportGenerator.oldDocPrefix = option[1];
                }
                continue;
            }
            if (name.equals("-baseuri")) {
                if (option.length < 2) {
                    err.msg("No base location specified after -baseURI option.");
                } else {
                    RootDocToXML.baseURI = option[1];
                }
                continue;
            }
            if (name.equals("-excludeclass")) {
                if (option.length < 2) {
                    err.msg("No level (public|protected|package|private) specified after -excludeclass option.");
                } else {
                    String level = option[1];
                    if (level.compareTo("public") != 0 &&
                        level.compareTo("protected") != 0 &&
                        level.compareTo("package") != 0 &&
                        level.compareTo("private") != 0) {
                        err.msg("Level specified after -excludeclass option must be one of (public|protected|package|private).");
                    } else {
                        RootDocToXML.classVisibilityLevel = level;
                    }
                }
                continue;
            }
            if (name.equals("-excludemember")) {
                if (option.length < 2) {
                    err.msg("No level (public|protected|package|private) specified after -excludemember option.");
                } else {
                    String level = option[1];
                    if (level.compareTo("public") != 0 &&
                        level.compareTo("protected") != 0 &&
                        level.compareTo("package") != 0 &&
                        level.compareTo("private") != 0) {
                        err.msg("Level specified after -excludemember option must be one of (public|protected|package|private).");
                    } else {
                        RootDocToXML.memberVisibilityLevel = level;
                    }
                }
                continue;
            }
            if (name.equals("-firstsentence")) {
                RootDocToXML.saveAllDocs = false;
                continue;
            }
            if (name.equals("-docchanges")) {
                HTMLReportGenerator.reportDocChanges = true;
                Diff.noDocDiffs = false;
                continue;
            }
            if (name.equals("-incompatible")) {
              HTMLReportGenerator.incompatibleChangesOnly = true;
              continue;
            }
            if (name.equals("-packagesonly")) {
                RootDocToXML.packagesOnly = true;
                continue;
            }
            if (name.equals("-showallchanges")) {
                Diff.showAllChanges = true;
                continue;
            }
            if (name.equals("-nosuggest")) {
                if (option.length < 2) {
                    err.msg("No level (all|remove|add|change) specified after -nosuggest option.");
                } else {
                    String level = option[1];
                    if (level.compareTo("all") != 0 &&
                        level.compareTo("remove") != 0 &&
                        level.compareTo("add") != 0 &&
                        level.compareTo("change") != 0) {
                        err.msg("Level specified after -nosuggest option must be one of (all|remove|add|change).");
                    } else {
                        if (level.compareTo("removal") == 0)
                            HTMLReportGenerator.noCommentsOnRemovals = true;
                        else if (level.compareTo("add") == 0)
                            HTMLReportGenerator.noCommentsOnAdditions = true;
                        else if (level.compareTo("change") == 0)
                            HTMLReportGenerator.noCommentsOnChanges = true;
                        else if (level.compareTo("all") == 0) {
                            HTMLReportGenerator.noCommentsOnRemovals = true;
                            HTMLReportGenerator.noCommentsOnAdditions = true;
                            HTMLReportGenerator.noCommentsOnChanges = true;
                        }
                    }
                }
                continue;
            }
            if (name.equals("-checkcomments")) {
                APIHandler.checkIsSentence = true;
                continue;
            }
            if (name.equals("-retainnonprinting")) {
                RootDocToXML.stripNonPrintables = false;
                continue;
            }
            if (name.equals("-excludetag")) {
                if (option.length < 2) {
                    err.msg("No exclude tag specified after -excludetag option.");
                } else {
                    RootDocToXML.excludeTag = option[1];
                    RootDocToXML.excludeTag = RootDocToXML.excludeTag.trim();
                    RootDocToXML.doExclude = true;
                }
                continue;
            }
            if (name.equals("-stats")) {
                HTMLReportGenerator.doStats = true;
                continue;
            }
            if (name.equals("-doctitle")) {
                if (option.length < 2) {
                    err.msg("No HTML text specified after -doctitle option.");
                } else {
                    HTMLReportGenerator.docTitle = option[1];
                }
                continue;
            }
            if (name.equals("-windowtitle")) {
                if (option.length < 2) {
                    err.msg("No text specified after -windowtitle option.");
                } else {
                    HTMLReportGenerator.windowTitle = option[1];
                }
                continue;
            }
            if (name.equals("-version")) {
                System.out.println("JDiff version: " + JDiff.version);
                System.exit(0);
            }
            if (name.equals("-help")) {
                usage();
                System.exit(0);
            }
        }
        if (!JDiff.writeXML && !JDiff.compareAPIs) {
            err.msg("First use the -apiname option to generate an XML file for one API.");
            err.msg("Then use the -apiname option again to generate another XML file for a different version of the API.");
            err.msg("Finally use the -oldapi option and -newapi option to generate a report about how the APIs differ.");
        }
        return err.noErrorsFound;
    }

    private static Map<String, Integer> createOptionLengths() {
        Map<String, Integer> lengths = new LinkedHashMap<String, Integer>();
        lengths.put("-authorid", Integer.valueOf(2));
        lengths.put("-versionid", Integer.valueOf(2));
        lengths.put("-d", Integer.valueOf(2));
        lengths.put("-classlist", Integer.valueOf(1));
        lengths.put("-title", Integer.valueOf(2));
        lengths.put("-docletid", Integer.valueOf(1));
        lengths.put("-evident", Integer.valueOf(2));
        lengths.put("-skippkg", Integer.valueOf(2));
        lengths.put("-skipclass", Integer.valueOf(2));
        lengths.put("-execdepth", Integer.valueOf(2));
        lengths.put("-help", Integer.valueOf(1));
        lengths.put("-version", Integer.valueOf(1));
        lengths.put("-package", Integer.valueOf(1));
        lengths.put("-protected", Integer.valueOf(1));
        lengths.put("-public", Integer.valueOf(1));
        lengths.put("-private", Integer.valueOf(1));
        lengths.put("-sourcepath", Integer.valueOf(2));
        lengths.put("-apiname", Integer.valueOf(2));
        lengths.put("-oldapi", Integer.valueOf(2));
        lengths.put("-newapi", Integer.valueOf(2));
        lengths.put("-apidir", Integer.valueOf(2));
        lengths.put("-oldapidir", Integer.valueOf(2));
        lengths.put("-newapidir", Integer.valueOf(2));
        lengths.put("-excludeclass", Integer.valueOf(2));
        lengths.put("-excludemember", Integer.valueOf(2));
        lengths.put("-firstsentence", Integer.valueOf(1));
        lengths.put("-docchanges", Integer.valueOf(1));
        lengths.put("-incompatible", Integer.valueOf(1));
        lengths.put("-packagesonly", Integer.valueOf(1));
        lengths.put("-showallchanges", Integer.valueOf(1));
        lengths.put("-javadocnew", Integer.valueOf(2));
        lengths.put("-javadocold", Integer.valueOf(2));
        lengths.put("-baseuri", Integer.valueOf(2));
        lengths.put("-nosuggest", Integer.valueOf(2));
        lengths.put("-checkcomments", Integer.valueOf(1));
        lengths.put("-retainnonprinting", Integer.valueOf(1));
        lengths.put("-excludetag", Integer.valueOf(2));
        lengths.put("-stats", Integer.valueOf(1));
        lengths.put("-windowtitle", Integer.valueOf(2));
        lengths.put("-doctitle", Integer.valueOf(2));
        return Collections.unmodifiableMap(lengths);
    }

    private static List<Doclet.Option> createSupportedOptions() {
        List<Doclet.Option> options = new ArrayList<Doclet.Option>();
        for (Map.Entry<String, Integer> entry : OPTION_LENGTHS.entrySet()) {
            final String name = entry.getKey();
            final int argCount = Math.max(0, entry.getValue().intValue() - 1);
            options.add(new LegacyOption(name, argCount));
        }
        return Collections.unmodifiableList(options);
    }

    private static final class LegacyOption implements Doclet.Option {
        private final String name;
        private final int argumentCount;

        LegacyOption(String name, int argumentCount) {
            this.name = name;
            this.argumentCount = argumentCount;
        }

        public int getArgumentCount() {
            return argumentCount;
        }

        public String getDescription() {
            return "JDiff legacy option";
        }

        public Kind getKind() {
            return Kind.OTHER;
        }

        public List<String> getNames() {
            return Collections.singletonList(name);
        }

        public String getParameters() {
            if (argumentCount == 0) {
                return "";
            }
            if (argumentCount == 1) {
                return "<arg>";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < argumentCount; i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                sb.append("<arg").append(i + 1).append(">");
            }
            return sb.toString();
        }

        public boolean process(String option, List<String> arguments) {
            String[] values = new String[arguments.size() + 1];
            values[0] = option;
            for (int i = 0; i < arguments.size(); i++) {
                values[i + 1] = arguments.get(i);
            }
            pendingOptions.add(values);
            return true;
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

    private static class ErrorHandler {
        boolean noErrorsFound = true;
        void msg(String msg) {
            noErrorsFound = false;
            print(msg);
        }
        void print(String msg) {
            if (reporter != null) {
                reporter.print(Diagnostic.Kind.ERROR, msg);
            } else {
                System.err.println(msg);
            }
        }
    }
}
