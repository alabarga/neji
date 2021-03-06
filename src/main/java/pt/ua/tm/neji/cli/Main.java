/*
 * Copyright (c) 2012 David Campos, University of Aveiro.
 *
 * Neji is a framework for modular biomedical concept recognition made easy, fast and accessible.
 *
 * This project is licensed under the Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License.
 * To view a copy of this license, visit http://creativecommons.org/licenses/by-nc-sa/3.0/.
 *
 * This project is a free software, you are free to copy, distribute, change and transmit it. However, you may not use
 * it for commercial purposes.
 *
 * It is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package pt.ua.tm.neji.cli;

import org.apache.commons.cli.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ua.tm.gimli.config.Constants;
import pt.ua.tm.gimli.external.gdep.GDepParser;
import pt.ua.tm.neji.batch.FileBatchExecutor;
import pt.ua.tm.neji.context.Context;
import pt.ua.tm.neji.core.batch.Batch;
import pt.ua.tm.neji.core.corpus.InputCorpus.InputFormat;
import pt.ua.tm.neji.core.corpus.OutputCorpus.OutputFormat;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Main application for the CLI tool.
 * @author David Campos (<a href="mailto:david.campos@ua.pt">david.campos@ua.pt</a>)
 * @author Tiago Nunes
 * @version 1.0
 * @since 1.0
 */
public class Main {

    /** {@link org.slf4j.Logger} to be used in the class. */
    private static Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * Print help message of the program.
     *
     * @param options Command line arguments.
     * @param msg     Message to be displayed.
     */
    private static void printHelp(final Options options, final String msg) {
        if (msg.length() != 0) {
            logger.error(msg);
        }
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(150, "./neji.sh " + USAGE, HEADER, options, EXAMPLES + FOOTER);
    }

    /** Help message. */
    private static final String HEADER = "\nNeji: modular biomedical concept recognition made easy, fast and accessible.";
    private static final String USAGE = "-i <folder> -if [XML|RAW] [-x <tags>] [-f <wildcard filter>] "
            + "-o <folder> -of [XML|NEJI|A1|CONLL|JSON|B64] [-d <folder>] "
            + "[-m <folder>] "
            + "[-pl <PARSING_LEVEL>] [-p <processor.class>] "
            + "[-c] [-t <threads>] [-v]";
    //    private static final String MODEL_HELP = "Please follow the format: <file>,<group>,<config>,<parsing>,<dictionaries>\n"
//            + "-file: File with model;\n"
//            + "-group: Semantic group of the annotations generated by the model;\n"
//            + "-config: File with features configuration;\n"
//            + "-parsing: FW (forward) or BW (backward);\n"
//            + "-lexicons: Folder that contains the lexicons to perform normalization (optional).";
    private static final String EXAMPLES = "\nUsage examples:\n"
            + "1: "
            + "./neji.sh -i input -if XML -o output -of XML -x text -d folder -t 2\n"
            + "2: "
            + "./neji.sh -i input -if RAW -o output -of A1 -m folder -t 4\n"
            + "3: "
            + "./neji.sh -i input -if XML -o output -of XML -x text -d folder1 -m folder2 -c -t 6\n\n";
    private static final String FOOTER = "For more instructions, please visit http://bioinformatics.ua.pt/neji.";

    public static void main(String[] args) {
        installUncaughtExceptionHandler();

        int NUM_THREADS = Runtime.getRuntime().availableProcessors() - 1;
        NUM_THREADS = NUM_THREADS > 0 ? NUM_THREADS : 1;


        CommandLineParser parser = new GnuParser();
        Options options = new Options();
        options.addOption("h", "help", false, "Print this usage information.");

        options.addOption("i", "input", true, "Folder with corpus files.");
        options.addOption("o", "output", true, "Folder to save the annotated corpus files.");
        options.addOption("f", "input-filter", true, "Wildcard to filter files in input folder");

        Option o = new Option("m", "models", true, "Folder that contains the ML models.");
        o.setArgs(Integer.MAX_VALUE);
        options.addOption(o);

        options.addOption("d", "dictionaires", true, "Folder that contains the dictionaries.");

        options.addOption("if", "input-format", true, "XML or RAW");
        options.addOption("of", "output-format", true, "A1, NEJI, JSON, CONLL, XML or B64");

        options.addOption("l", "parsing-level", true, "TOKENIZATION | POS | LEMMATIZATION | CHUNKING | DEPENDENCY");
        options.addOption("p", "processor-class", true, "Full name of pipeline processor class.");

        options.addOption("x", "xml-tags", true, "XML tags to be considered.");

        options.addOption("v", "verbose", false, "Verbose mode.");
        options.addOption("c", "compressed", false, "If files are compressed using GZip.");
        options.addOption("t", "threads", true,
                "Number of threads. By default, if more than one core is available, it is the number of cores minus 1.");

        CommandLine commandLine = null;
        try {
            // Parse the program arguments
            commandLine = parser.parse(options, args);
        } catch (ParseException ex) {
            logger.error("There was a problem processing the input arguments.", ex);
            return;
        }

        // Show help text
        if (commandLine.hasOption('h')) {
            printHelp(options, "");
            return;
        }

        // No options
        if (commandLine.getOptions().length == 0) {
            printHelp(options, "");
            return;
        }

        File test = null;
        // Get corpus folder for input
        String folderCorpusIn = null;
        if (commandLine.hasOption('i')) {
            folderCorpusIn = commandLine.getOptionValue('i');
        } else {
            printHelp(options, "Please specify the input corpus folder.");
            return;
        }
        test = new File(folderCorpusIn);
        if (!test.isDirectory() || !test.canRead()) {
            logger.error("The specified path is not a folder or is not readable.");
            return;
        }
        folderCorpusIn = test.getAbsolutePath();
        folderCorpusIn += File.separator;

        String inputFolderWildcard = null;
        if (commandLine.hasOption("f")) {
            inputFolderWildcard = commandLine.getOptionValue("f");
        }

        // Get Input format
        InputFormat inputFormat;
        if (commandLine.hasOption("if")) {
            inputFormat = InputFormat.valueOf(commandLine.getOptionValue("if"));
        } else {
            printHelp(options, "Please specify the input format.");
            return;
        }

        // Get corpus folder for output
        String folderCorpusOut;
        if (commandLine.hasOption('o')) {
            folderCorpusOut = commandLine.getOptionValue('o');
        } else {
            printHelp(options, "Please specify the output corpus folder.");
            return;
        }
        test = new File(folderCorpusOut);
        if (!test.isDirectory() || !test.canWrite()) {
            logger.error("The specified path is not a folder or is not writable.");
            return;
        }
        folderCorpusOut = test.getAbsolutePath();
        folderCorpusOut += File.separator;


        // Get Output format
        OutputFormat outputFormat;
        if (commandLine.hasOption("of")) {
            outputFormat = OutputFormat.valueOf(commandLine.getOptionValue("of"));
        } else {
            printHelp(options, "Please specify the output format.");
            return;
        }

        if (inputFormat.equals(InputFormat.XML)) {
            if (outputFormat.equals(OutputFormat.A1) || outputFormat.equals(OutputFormat.JSON) || outputFormat.equals(
                    OutputFormat.NEJI)) {
                logger.error(
                        "XML input format only supports XML and CoNLL output formats, since other formats are based on character positions.");
                return;
            }
        }

        // Get XML tags
        String[] xmlTags = null;
        if (inputFormat.equals(InputFormat.XML)) {
            if (commandLine.hasOption("x")) {
                xmlTags = commandLine.getOptionValue("x").split(",");
            } else {
                printHelp(options, "Please specify XML tags to be used.");
                return;
            }
        }

        // Get models
        boolean doModels = false;
        String modelsFolder = null;
        if (commandLine.hasOption('m')) {
            modelsFolder = commandLine.getOptionValue('m');
            doModels = true;

            test = new File(modelsFolder);
            if (!test.isDirectory() || !test.canRead()) {
                logger.error("The specified path is not a folder or is not readable.");
                return;
            }
            modelsFolder = test.getAbsolutePath();
            modelsFolder += File.separator;
        }

        // Get dictionaries folder
        boolean doDictionaries = false;
        String dictionariesFolder = null;
        if (commandLine.hasOption('d')) {
            dictionariesFolder = commandLine.getOptionValue('d');
            doDictionaries = true;

            test = new File(dictionariesFolder);
            if (!test.isDirectory() || !test.canRead()) {
                logger.error("The specified path is not a folder or is not readable.");
                return;
            }
            dictionariesFolder = test.getAbsolutePath();
            dictionariesFolder += File.separator;
        }

        // Get verbose mode
        boolean verbose = false;
        if (commandLine.hasOption('v')) {
            verbose = true;
        }
        Constants.verbose = verbose;

        // Disable output from Mallet and GDep
        if (!Constants.verbose) {
            System.setOut(new PrintStream(new OutputStream() {
                @Override
                public void write(int b) {
                }
            }));
            System.setErr(new PrintStream(new OutputStream() {
                @Override
                public void write(int b) {
                }
            }));
        }

        // Get compressed mode
        boolean compressed = false;
        if (commandLine.hasOption('c')) {
            compressed = true;
        }

        // Get threads
        String threadsText = null;
        if (commandLine.hasOption('t')) {
            threadsText = commandLine.getOptionValue('t');
            NUM_THREADS = Integer.parseInt(threadsText);
            if (NUM_THREADS <= 0 || NUM_THREADS > 32) {
                logger.error("Illegal number of threads. Must be between 1 and 32.");
                return;
            }
        }

        // Load pipeline processor
        Class processor = DefaultProcessor.class;
        if (commandLine.hasOption("p")) {
            String processorName = commandLine.getOptionValue("p");
            try {
                processor = Class.forName(processorName);
            } catch (ClassNotFoundException ex) {
                logger.error("Could not load pipeline processor \"" + processorName + "\"");
                return;
            }
        }

        // Load parsing level
        GDepParser.ParserLevel parsingLevel = null;
        if (commandLine.hasOption("l")) {
            String parsingLevelName = commandLine.getOptionValue("l");
            try {
                parsingLevel = GDepParser.ParserLevel.valueOf(parsingLevelName);
            } catch (IllegalArgumentException ex) {
                logger.error("Invalid parsing level \"" + parsingLevelName + "\". "
                        + "Must be one of " + StringUtils.join(GDepParser.ParserLevel.values(), ", "));
                return;
            }
        }

        Context context = new Context(
                modelsFolder, // Models
                dictionariesFolder, // Dictionaries folder
                parsingLevel, // Parsing level
                false); // Use LINNAEUS

        try {
            Batch batch = new FileBatchExecutor(folderCorpusIn, inputFormat, folderCorpusOut, outputFormat,
                    compressed, NUM_THREADS, inputFolderWildcard);

            if (xmlTags == null) {
                batch.run(processor, context);
            } else {
                batch.run(processor, context, new Object[]{xmlTags});
            }


        } catch (Exception ex) {
            logger.error("There was a problem running the batch.", ex);
        }

//        logger.info("Dumping running threads:");
//        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
//        for (Thread t: threadSet) {
//            logger.info("{}", t);
//        }
//        System.exit(0);
    }

    private static void installUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable thrwbl) {
                if (thrwbl instanceof ThreadDeath) {
                    logger.warn("Ignoring uncaught ThreadDead exception.");
                    return;
                }
                logger.error("Uncaught exception on cli thread, aborting.", thrwbl);
                System.exit(0);
            }
        });
    }
}
