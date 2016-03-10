/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2012 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/
/**
 * Main entry point for the script compiler
 *
 * @author steele@osteele.com
 * @author ptw@openlaszlo.org
 */


package org.openlaszlo.compiler;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.PropertyConfigurator;
import org.openlaszlo.server.LPS;
import org.openlaszlo.utils.FileUtils;
import org.openlaszlo.utils.StringUtils;

public class Main {
    private static final String[] USAGE = {
        "Usage: lzc [OPTION]... FILE...",
        "",
        "Options:",
        "-D<name>=<value>",
        "  Set the name/var property to value (See Compiler.getProperties).",
        "-D<name>",
        "  Short for -Dname=true.",
        "-v",
        "  Write progress information to standard output.",
        "--onerror [throw|warn]",
        "  Action to take on compilation errors.  Defaults to warn.",
        "--help",
        "  Prints this message.",
        "--application-root",
        "  Location of root of application directory, if different from LPS_HOME",
        "--allow-lzo-switch",
        "  Allow external library in lzo compilation to contain a <switch> block",
        "",
        "Output options:",
        "--runtime=["+ StringUtils.join(Compiler.KNOWN_RUNTIMES, "|")+"]",
        "  Compile to a runtime, the supported runtimes are "+StringUtils.join(Compiler.SUPPORTED_RUNTIMES, ","),
        "--flex-options=version=10.x,air,debug",
        "  version: Flash 10 Player minor version [10.0 or 10.1] | air: use Adobe AIR config | debug: set flex compiler debug flag",
        "--dir outputdir",
        "  Output directory.",
        "-c | --compile",
        "  Compile and assemble, but do not link",
        " -a | --add-linkable-libraries-for-runtimes",
        "  Used with -c flag, builds platform-specific libraries for lzo files, for each supported runtime in the --runtime arg(s).",
        "-g1 | --debug",
        "  Add debugging support into the output object.",
        "-g | -g2 | --backtrace",
        "  Add debugging and backtrace support into the output object.",
        "-o <file> | --output <file>",
        "  Put output into given filename.",
        "-p | --profile",
        "  Add profiling information into the output object.",
        "--incremental",
        "  for as3 runtime, use incremental compiler mode",
        "--lzxonly",
        "  for as3 runtime, emit intermediate as files, but don't call backend as3 compiler",
        "--lzolibs",
        "  (for use with --compile option), comma separated list of external lzo libraries to link against",
        "",
        "Logging options:",
        "-l<loglevel>",
        "  Logging level (See org.apache.log4j.Level)",
        "-l<loggerName>=<loglevel>",
        "  Logging level (See org.apache.log4j.Level)",
        "-lp file",
        "  Log4j properties files",
        "--log logfile",
        "  Specify logfile (output still goes to console as well)",
        "--schema",
        "  Writes the schema to standard output.",
        "-S | --script",
        "  Writes JavaScript to .lzs file.",
        "-SS | --savestate",
        "  Writes JavaScript to .lzs file, and ASTs to -astin.txt, -astout.txt"
    };

    private final static String MORE_HELP =
        "Try `lzc --help' for more information.";


    /** Compiles each file base.ext to the output file base.swf (or .js),
     * writing progress information to standard output.  This method
     * is intended for testing the compiler.
     *
     * <p>See the usage string or execute <code>lzc --help</code>
     * to see a list of options.
     *
     * @param args the command line arguments
     */
    public static void main(String args[])
        throws IOException
    {
      System.exit(lzc(args, null, null, null));
    }


    // Save the name of the last outputfile, for use when called from the fcsh shell
    static String swfOutputFilename;

    /*
      Returns the filename of the output, which contains a mxmlc command-line arglist
     */
    public static String invoke(String args[])
        throws IOException
    {
        lzc(args, null, null, null);
        return Main.swfOutputFilename;
    }


    /** This method implements the behavior described in main
     * but also returns an integer error code.
     */
    public static int lzc(String args[], String logFile,
            String outFileName, String outDir)
        throws IOException
    {
        Logger logger = Logger.getRootLogger();
        List<String> files = new Vector<String>();
        // Configure logging
        logger.setLevel(Level.ERROR);
        PatternLayout layout = new PatternLayout("%m%n");
        logger.removeAllAppenders();
        if (logFile == null) {
            logger.addAppender(new ConsoleAppender(layout));
        } else {
            logger.addAppender(new FileAppender(layout, logFile, false));
        }

        Compiler compiler = new Compiler();

        String tmpdirstr = System.getProperty("java.io.tmpdir");
        String cachetmpdirstr = tmpdirstr + File.separator + "lzccache";
        (new File(cachetmpdirstr)).mkdirs();

        // Set default runtime to compiler.runtime.default
        compiler.setProperty(CompilationEnvironment.RUNTIME_PROPERTY,
                             LPS.getProperty("compiler.runtime.default",
                                             LPS.getRuntimeDefault()));
        compiler.setProperty(CompilationEnvironment.RUNTIMES_PROPERTY,
                             LPS.getProperty("compiler.runtime.default",
                                             LPS.getRuntimeDefault()));
        String outFileArg = null;
        boolean saveStateOption = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i].intern();
            if (arg.startsWith("-")) {
                if (arg == "-v") {
                    logger.setLevel(Level.ALL);
                } else if (arg == "-lp") {
                    String value = safeArg("-lp", args, ++i);
                    if (value == null) {
                        return 1;
                    }
                    PropertyConfigurator.configure(value);
                    String lhome = System.getProperty("LPS_HOME");
                    if (lhome == null || lhome.equals("")) {
                        lhome = System.getenv("LPS_HOME");
                    }
                    LPS.setHome(lhome);
                } else if (arg == "--schema") {
                    Compiler.SchemaLogger.setLevel(Level.ALL);
                } else if (arg == "--copy-resources") {
                    compiler.setProperty(CompilationEnvironment.COPY_RESOURCES_LOCAL, "true");
                } else if (arg == "-o" || arg == "--output") {
                    outFileArg = safeArg("-o or --output", args, ++i);
                    if (outFileArg == null) {
                        return 1;
                    }
                } else if (arg == "--onerror") {
                    String value = safeArg("--onerror", args, ++i);
                    if (value == null) {
                        return 1;
                    }
                    CompilationError.ThrowCompilationErrors =
                        "throw".equals(value);
                } else if (arg.startsWith("--runtime=")) {
                    String value = arg.substring("--runtime=".length());
                    String[] runtimes = value.split(",");
                    HashSet<String> known = new HashSet<String>(Compiler.KNOWN_RUNTIMES);
                    for (int k = 0; k < runtimes.length; k++) {
                        String rt = runtimes[k].trim();
                        if (!known.contains(rt)) {
                            System.err.println("Invalid value for --runtime, "+rt);
                            System.err.println(MORE_HELP);
                            return 1;
                        }
                    }
                    // First runtime is the 'primary' one, others are just used for secondary lzo compilations
                    compiler.setProperty(CompilationEnvironment.RUNTIME_PROPERTY, runtimes[0].trim());
                    compiler.setProperty(CompilationEnvironment.RUNTIMES_PROPERTY, value);
                } else if (arg.startsWith("--flex-options=")) {
                    String value = arg.substring("--flex-options=".length());
                    compiler.setProperty(CompilationEnvironment.FLEX_OPTIONS, value);
                    // Check if the flex version is one of the supported ones
                    Pattern pattern = Pattern.compile("version=([0-9.]*)");
                    Matcher matcher = pattern.matcher(value);
                    boolean badversion = false;
                    if (matcher.find() && matcher.groupCount() == 1) {
                        badversion = true;
                        String version = matcher.group(1);
                        HashSet<String> known = new HashSet<String>(Compiler.KNOWN_FLEX_VERSIONS);
                        if (known.contains(version)) {
                            badversion = false;
                        }
                    }
                    if (badversion) {
                        System.err.println("Invalid value for --flex-version, "+value+", must be one of "+Compiler.KNOWN_FLEX_VERSIONS);
                        System.err.println(MORE_HELP);
                        return 1;
                    }
                } else if (arg == "-S" || arg == "--script") {
                    compiler.setProperty(CompilationEnvironment.INTERMEDIATE_PROPERTY, "true");
                } else if (arg == "--allow-lzo-switch") {
                    compiler.setProperty(CompilationEnvironment.ALLOW_LZO_SWITCH_BLOCK, "true");
                } else if (arg == "-SS" || arg == "--scripts") {
                    saveStateOption = true;
                    LPS.setGenerateLfcStateFile();
                } else if (arg == "-log" || arg == "--log") {
                    String log = safeArg("-log or --log", args, ++i);
                    if (log == null) {
                        return 1;
                    }
                    logger.removeAllAppenders();
                    logger.addAppender(new FileAppender(layout, log, false));
                } else if (arg == "-dir" || arg == "--dir") {
                    outDir = safeArg("-dir or --dir", args, ++i);
                    if (outDir == null) {
                        return 1;
                    }
                } else if (arg.startsWith("-D")) {
                    String key = arg.substring(2);
                    String value = "true";
                    int offset = key.indexOf('=');
                    if (offset >= 0) {
                        value = key.substring(offset + 1).intern();
                        key = key.substring(0, offset);
                    }
                    compiler.setProperty(key, value);
                    LPS.setProperty(key, value);
                } else if (arg.startsWith("-l")) {
                    Logger thisLogger = logger;
                    String level = arg.substring(2);
                    if (level.indexOf('=') > -1) {
                        String key = level.substring(0, level.indexOf('='));
                        level = level.substring(level.indexOf('=')+1);
                        thisLogger = Logger.getLogger(key);
                    }
                    if (level != "" && level != null) {
                        thisLogger.setLevel(Level.toLevel(level));
                    }
                } else if (arg == "-g1" || arg == "--debug") {
                    compiler.setProperty(CompilationEnvironment.DEBUG_PROPERTY, "true");
                } else if (arg == "-g" || arg == "-g2" || arg == "--backtrace") {
                    compiler.setProperty(CompilationEnvironment.DEBUG_PROPERTY, "true");
                    compiler.setProperty(CompilationEnvironment.BACKTRACE_PROPERTY, "true");
                } else if (arg == "-p" || arg == "--profile") {
                    compiler.setProperty(CompilationEnvironment.PROFILE_PROPERTY, "true");
                } else if (arg == "-c" || arg == "--compile") {
                  compiler.setProperty(CompilationEnvironment.LINK_PROPERTY, "false");
                } else if (arg == "-a" || arg == "--add-linkable-libraries-for-runtimes") {
                  compiler.setProperty(CompilationEnvironment.BUILD_LZO_FOR_RUNTIMES_PROPERTY, "true");
                } else if (arg == "--lzolibs") {
                    String lzolibs = safeArg("--lzolibs", args, ++i);
                    compiler.setProperty(CompilationEnvironment.EXTERNAL_LZO_FILES_PROPERTY, lzolibs);
                    System.err.println("setting lzolibs to "+lzolibs);
                } else if (arg == "--incremental") {
                  compiler.setProperty(CompilationEnvironment.INCREMENTAL_MODE, "true");
                } else if (arg == "--lzxonly") {
                  compiler.setProperty(CompilationEnvironment.LZXONLY, "true");
                } else if (arg == "--help") {
                    for (int j = 0; j < USAGE.length; j++) {
                        System.err.println(USAGE[j]);
                    }
                    return 0;
                } else {
                    System.err.println("Usage: lzc [OPTION]... file...");
                    System.err.println(MORE_HELP);
                    return 1;
                }
                continue;
            }
            String sourceName = args[i];
            files.add(sourceName);
        }

        LPS.initialize();

        if (files.size() == 0) {
            System.err.println("lzc: no input files");
            System.err.println(MORE_HELP);
            return 1;
        }
        if (outFileArg != null) {
            outFileName = outFileArg;
            if (files.size() > 1) {
                System.err.println("lzc: -o or --output can only be used with one input file");
                System.err.println(MORE_HELP);
                return 1;
            }
        }
        if (outFileArg != null || outDir != null) {
            // Turn off deployment file
            compiler.setProperty(CompilationEnvironment.NO_DEPLOY, "true");
        }
        int status = 0;
        for (String sourceName : files) {
            String sourceNameNoExt = sourceName.endsWith(".lzx") ?
                sourceName.substring(0, sourceName.length()-4) : sourceName;

            if (saveStateOption) {
                // remove old
                File dir = new File(sourceName).getCanonicalFile().getParentFile();
                final String pat = new File(sourceNameNoExt).getName() +
                    "-ast(?:in|out)-[0-9]*.txt";
                String[] matches = dir.list(new FilenameFilter() {
                        public boolean accept(File dir, String name) {
                            return name.matches(pat);
                        }
                    });
                for (int i=0; i<matches.length; i++) {
                    System.out.println("Removing " + matches[i]);
                    new File(matches[i]).delete();
                }
                compiler.setProperty(org.openlaszlo.sc.Compiler.DUMP_AST_INPUT, sourceNameNoExt + "-astin-*.txt");
                compiler.setProperty(org.openlaszlo.sc.Compiler.DUMP_AST_OUTPUT, sourceNameNoExt + "-astout-*.txt");
                compiler.setProperty(org.openlaszlo.sc.Compiler.DUMP_SRC_INPUT, sourceNameNoExt + "-src-*.txt");
                compiler.setProperty(org.openlaszlo.sc.Compiler.DUMP_LINE_ANNOTATIONS, sourceNameNoExt + "-lineann-*.txt");
            }

            status += compile(compiler, logger, sourceName, outFileName, outDir);
        }
        return status;
    }

    /**
     * Utility function to produce a reasonable message for incorrect
     * invocations that end with a arg expecting more, like "lzc -mcache".
     * @return the next arg if available, and null not.
     *      When null is returned, an error message already produced
     * @param argname the name of the argument, e.g. "-mcache"
     * @param the args array
     * @param offset the offset into the args array.
     */
    public static String safeArg(String argname, String args[], int offset)
    {
        if (offset >= args.length) {
            System.err.println("lzc: expected argument for " + argname);
            System.err.println(MORE_HELP);
            return null;
        } else {
            return args[offset];
        }
    }

    static private int compile(Compiler compiler,
                                Logger logger,
                                String sourcePath,
                                String outName,
                                String outDir)
    {
        File sourceFile = new File(sourcePath);
        String objExtension = null;
        String finalExtension = null;
        String finalName = null;
        
        if ("true".equals(compiler.getProperty(CompilationEnvironment.INTERMEDIATE_PROPERTY))) {
          objExtension = ".lzi";
        } else if ("false".equals(compiler.getProperty(CompilationEnvironment.LINK_PROPERTY))) {
          objExtension = ".zip";
          finalExtension = ".lzo";
        } else {
          String runtime = compiler.getProperty(CompilationEnvironment.RUNTIME_PROPERTY);
          objExtension = Compiler.getObjectFileExtensionForRuntime(runtime);
        }
        if (outName == null) {
          String baseName = FileUtils.getBase(sourceFile.getName());
          outName = baseName + objExtension;
          if (finalExtension != null) {
            finalName = baseName + finalExtension;
          }
        }
        if (outDir == null) {
          outDir = sourceFile.getParent();
        }
        File objectFile = new File(outDir, outName);
        Main.swfOutputFilename = objectFile.getAbsolutePath();
        try {

            System.err.println("Compiling: " + sourceFile + " to " + ((finalExtension != null)  ? finalName : outName));
            compiler.compile(sourceFile, objectFile, new Properties());
            if (finalName != null) {
              File finalFile = new File(outDir, finalName);
              if (finalFile.exists()) {
                finalFile.delete();
              }
              if (! objectFile.renameTo(finalFile)) {
                throw new CompilationError("Could not rename " + objectFile + " to " + finalFile);
              }
            }
        } catch (CompilationError e) {
            logger.error(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Compilation errors occurred:"
 */
                org.openlaszlo.i18n.LaszloMessages.getMessage(
                    Main.class.getName(),"051018-249")
                );

            logger.error(e.toPlainText());
            return 2;
        } catch (IOException e) {
            logger.error(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="IO exception: " + p[0]
 */
                org.openlaszlo.i18n.LaszloMessages.getMessage(
                    Main.class.getName(),"051018-259", new Object[] {e.getMessage()})
                );
            return 3;
        }
        return 0;
    }
}

/**
 * @copyright Copyright 2001-2011 Laszlo Systems, Inc.  All Rights
 * Reserved.  Use is subject to license terms.
 */


