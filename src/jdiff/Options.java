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

    /** Default constructor. */
    public Options() {
    }

    private static final class OptionSpec {
        final String name;
        final int argumentCount;
        final Doclet.Option.Kind kind;

        OptionSpec(String name, int argumentCount, Doclet.Option.Kind kind) {
            this.name = name;
            this.argumentCount = argumentCount;
            this.kind = kind;
        }
    }

    private static final List<OptionSpec> OPTION_SPECS = Arrays.asList(
        new OptionSpec("-authorid", 1, Doclet.Option.Kind.OTHER),
        new OptionSpec("-versionid", 1, Doclet.Option.Kind.OTHER),
        new OptionSpec("-d", 1, Doclet.Option.Kind.STANDARD),
        new OptionSpec("-classlist", 0, Doclet.Option.Kind.OTHER),
        new OptionSpec("-title", 1, Doclet.Option.Kind.OTHER),
        new OptionSpec("-docletid", 0, Doclet.Option.Kind.OTHER),
        new OptionSpec("-evident", 1, Doclet.Option.Kind.OTHER),
        new OptionSpec("-skippkg", 1, Doclet.Option.Kind.OTHER),
        new OptionSpec("-skipclass", 1, Doclet.Option.Kind.OTHER),
        new OptionSpec("-execdepth", 1, Doclet.Option.Kind.OTHER),
        new OptionSpec("-help", 0, Doclet.Option.Kind.STANDARD),
        new OptionSpec("-version", 0, Doclet.Option.Kind.STANDARD),
        new OptionSpec("-package", 0, Doclet.Option.Kind.STANDARD),
        new OptionSpec("-protected", 0, Doclet.Option.Kind.STANDARD),
        new OptionSpec("-public", 0, Doclet.Option.Kind.STANDARD),
        new OptionSpec("-private", 0, Doclet.Option.Kind.STANDARD),
        new OptionSpec("-sourcepath", 1, Doclet.Option.Kind.STANDARD),
        new OptionSpec("-apiname", 1, Doclet.Option.Kind.OTHER),
        new OptionSpec("-oldapi", 1, Doclet.Option.Kind.OTHER),
        new OptionSpec("-newapi", 1, Doclet.Option.Kind.OTHER),
        new OptionSpec("-apidir", 1, Doclet.Option.Kind.OTHER),
        new OptionSpec("-oldapidir", 1, Doclet.Option.Kind.OTHER),
        new OptionSpec("-newapidir", 1, Doclet.Option.Kind.OTHER),
        new OptionSpec("-excludeclass", 1, Doclet.Option.Kind.OTHER),
        new OptionSpec("-excludemember", 1, Doclet.Option.Kind.OTHER),
        new OptionSpec("-firstsentence", 0, Doclet.Option.Kind.OTHER),
        new OptionSpec("-docchanges", 0, Doclet.Option.Kind.OTHER),
        new OptionSpec("-incompatible", 0, Doclet.Option.Kind.OTHER),
        new OptionSpec("-packagesonly", 0, Doclet.Option.Kind.OTHER),
        new OptionSpec("-showallchanges", 0, Doclet.Option.Kind.OTHER),
        new OptionSpec("-javadocnew", 1, Doclet.Option.Kind.OTHER),
        new OptionSpec("-javadocold", 1, Doclet.Option.Kind.OTHER),
        new OptionSpec("-baseuri", 1, Doclet.Option.Kind.OTHER),
        new OptionSpec("-nosuggest", 1, Doclet.Option.Kind.OTHER),
        new OptionSpec("-checkcomments", 0, Doclet.Option.Kind.OTHER),
        new OptionSpec("-retainnonprinting", 0, Doclet.Option.Kind.OTHER),
        new OptionSpec("-excludetag", 1, Doclet.Option.Kind.OTHER),
        new OptionSpec("-stats", 0, Doclet.Option.Kind.OTHER),
        new OptionSpec("-windowtitle", 1, Doclet.Option.Kind.STANDARD),
        new OptionSpec("-doctitle", 1, Doclet.Option.Kind.STANDARD)
    );

    private static class SimpleOption implements Doclet.Option {
        private final OptionSpec spec;
        private final List<String[]> recordedOptions;

        SimpleOption(OptionSpec spec, List<String[]> recordedOptions) {
            this.spec = spec;
            this.recordedOptions = recordedOptions;
        }

        @Override
        public int getArgumentCount() {
            return spec.argumentCount;
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public Doclet.Option.Kind getKind() {
            return spec.kind;
        }

        @Override
        public List<String> getNames() {
            return Collections.singletonList(spec.name);
        }

        @Override
        public String getParameters() {
            return "";
        }

        @Override
        public boolean process(String option, List<String> arguments) {
            List<String> values = new ArrayList<>(1 + arguments.size());
            values.add(option);
            values.addAll(arguments);
            recordedOptions.add(values.toArray(new String[0]));
            Options.cmdOptions += " " + option;
            for (String argument : arguments) {
                Options.cmdOptions += " " + argument;
            }
            return true;
        }
    }

    public static Set<Doclet.Option> getSupportedOptions(List<String[]> recordedOptions) {
        Set<Doclet.Option> options = new LinkedHashSet<>();
        for (OptionSpec spec : OPTION_SPECS) {
            options.add(new SimpleOption(spec, recordedOptions));
        }
        return options;
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
    * @param reporter  a DocErrorReporter for generating error messages
    * @return true if no errors were found, and all options are
    *         valid
    */
    public static boolean processOptions(List<String[]> options,
                                         Reporter reporter) {
        final Reporter errOut = reporter;

        // A nice object-oriented way of handling errors. An instance of this
        // class puts out an error message and keeps track of whether or not
        // an error was found.
        class ErrorHandler {
            boolean noErrorsFound = true;
            void msg(String msg) {
                noErrorsFound = false;
                if (errOut != null) {
                    errOut.print(Diagnostic.Kind.ERROR, msg);
                } else {
                    System.err.println(msg);
                }
            }
        }

        ErrorHandler err = new ErrorHandler();
        if (trace)
            System.out.println("Command line arguments: ");
        if (trace) {
            for (String[] option : options) {
                for (String value : option) {
                    System.out.print(" " + value);
                }
            }
        }
        if (trace)
            System.out.println();

        for (String[] option : options) {
            if (option.length == 0)
                continue;
            String optName = option[0].toLowerCase();
            if (optName.equals("-apiname")) {
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
            if (optName.equals("-apidir")) {
                if (option.length < 2) {
                    err.msg("No directory specified after -apidir option.");
                } else {
                    RootDocToXML.outputDirectory = option[1];
                }
                continue;
            }
            if (optName.equals("-oldapi")) {
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
            if (optName.equals("-oldapidir")) {
                if (option.length < 2) {
                    err.msg("No directory specified after -oldapidir option.");
                } else {
                    JDiff.oldDirectory = option[1];
                }
                continue;
            }
            if (optName.equals("-newapi")) {
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
            if (optName.equals("-newapidir")) {
                if (option.length < 2) {
                    err.msg("No directory specified after -newapidir option.");
                } else {
                    JDiff.newDirectory = option[1];
                }
                continue;
            }
            if (optName.equals("-d")) {
                if (option.length < 2) {
                    err.msg("No directory specified after -d option.");
                } else {
                    HTMLReportGenerator.outputDir = option[1];
                }
                continue;
            }
            if (optName.equals("-sourcepath")) {
                if (option.length < 2) {
                    err.msg("No location specified after -sourcepath option.");
                } else {
                    RootDocToXML.sourcePath = option[1];
                }
                continue;
            }
            if (optName.equals("-javadocnew")) {
                if (option.length < 2) {
                    err.msg("No location specified after -javadocnew option.");
                } else {
                    HTMLReportGenerator.newDocPrefix = option[1];
                }
                continue;
            }
            if (optName.equals("-javadocold")) {
                if (option.length < 2) {
                    err.msg("No location specified after -javadocold option.");
                } else {
                    HTMLReportGenerator.oldDocPrefix = option[1];
                }
                continue;
            }
            if (optName.equals("-baseuri")) {
                if (option.length < 2) {
                    err.msg("No base location specified after -baseURI option.");
                } else {
                    RootDocToXML.baseURI = option[1];
                }
                continue;
            }
            if (optName.equals("-excludeclass")) {
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
            if (optName.equals("-excludemember")) {
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
            if (optName.equals("-firstsentence")) {
                RootDocToXML.saveAllDocs = false;
                continue;
            }
            if (optName.equals("-docchanges")) {
                HTMLReportGenerator.reportDocChanges = true;
                Diff.noDocDiffs = false;
                continue;
            }
            if (optName.equals("-incompatible")) {
              HTMLReportGenerator.incompatibleChangesOnly = true;
              continue;
            }
            if (optName.equals("-packagesonly")) {
                RootDocToXML.packagesOnly = true;
                continue;
            }
            if (optName.equals("-showallchanges")) {
                Diff.showAllChanges = true;
                continue;
            }
            if (optName.equals("-nosuggest")) {
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
            if (optName.equals("-checkcomments")) {
                APIHandler.checkIsSentence = true;
                continue;
            }
            if (optName.equals("-retainnonprinting")) {
                RootDocToXML.stripNonPrintables = false;
                continue;
            }
            if (optName.equals("-excludetag")) {
                if (option.length < 2) {
                    err.msg("No exclude tag specified after -excludetag option.");
                } else { 
                    RootDocToXML.excludeTag = option[1];
                    RootDocToXML.excludeTag = RootDocToXML.excludeTag.trim();
                    RootDocToXML.doExclude = true;
                }
                continue;
            }
            if (optName.equals("-stats")) {
                HTMLReportGenerator.doStats = true;
                continue;
            }
            if (optName.equals("-doctitle")) {
                if (option.length < 2) {
                    err.msg("No HTML text specified after -doctitle option.");
                } else { 
                    HTMLReportGenerator.docTitle = option[1];
                }
                continue;
            }
            if (optName.equals("-windowtitle")) {
                if (option.length < 2) {
                    err.msg("No text specified after -windowtitle option.");
                } else { 
                    HTMLReportGenerator.windowTitle = option[1];
                }
                continue;
            }
            if (optName.equals("-version")) {
                System.out.println("JDiff version: " + JDiff.version);
                System.exit(0);
            }
            if (optName.equals("-help")) {
                usage();
                System.exit(0);
            }
        }//for
        if (!JDiff.writeXML && !JDiff.compareAPIs) {
            err.msg("First use the -apiname option to generate an XML file for one API.");
            err.msg("Then use the -apiname option again to generate another XML file for a different version of the API.");
            err.msg("Finally use the -oldapi option and -newapi option to generate a report about how the APIs differ.");
        }
        return err.noErrorsFound;
    }// processOptions()

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

    /** Set to enable increased logging verbosity for debugging. */
    private static boolean trace = false;
}
