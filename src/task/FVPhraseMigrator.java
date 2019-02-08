package task;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.logging.FileHandler;

import mappers.CsvMapper;
import mappers.firstvoices.BinaryMapper;
import mappers.firstvoices.PhraseMigratorMapper;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.model.Document;

import common.ConsoleLogger;
import common.CsvLogWriter;
import reader.AbstractReader;
import reader.CsvReader;
import reader.OracleReader;

/**
 * @author cstuart
 *
 */
public class FVPhraseMigrator extends AbstractMigrator {

    protected CsvMapper mapper = null;

	public FVPhraseMigrator(CommandLine cmd, AbstractReader reader) {
		super(reader);

		BinaryMapper.setDataPath(cmd.getOptionValue("data-path"));

		mapper = new PhraseMigratorMapper();

		if (cmd.hasOption("limit")) {
            limit = Integer.valueOf(cmd.getOptionValue("limit"));
        }
        try {
            FileHandler fh = new FileHandler(new File(new File(cmd.getOptionValue("data-path")), "import.log").getAbsolutePath());
            log.addHandler(fh);
        } catch (SecurityException | IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        String csvFile = cmd.getOptionValue("csv-file");
        // Remove the .csv from the file to create a file_errors.csv
        csvFile = csvFile.substring(0, csvFile.length()-4) + "_errors.csv";
        logWriter = new CsvLogWriter(csvFile);
	}

	@Override
	protected void processRow(Session session) throws IOException {
        Map<String, Document> docs = getOrCreateLanguageDocument(session, reader);
        docs.put("parent", docs.get("Dictionary"));
        mapper.process(docs, session, reader);
        if (lines % 1000 == 0) {
            ConsoleLogger.out("##### " + lines + " lines, " + errors + " errors, "
            		+ PhraseMigratorMapper.createdPhrases + " phrases created, "
            		+ PhraseMigratorMapper.updatedPhrases + " null import_id phrases updated, "
            		+ PhraseMigratorMapper.cachedPhrases + " phrases in cache and skipped, "
            		+ CsvMapper.createdObjects + " objects created");
        }
	}

	public static void main(String[] argv) {
        AbstractReader reader = null;
        // Handle command line option

        setOptions();

        options.addOption(
                Option.builder().longOpt("data-path").required().hasArg().desc("Where to get data from").build());

        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine = null;
        try {
            commandLine = parser.parse(options, argv);
        } catch (ParseException e) {
            e.printStackTrace();
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("Migrator", options);
            return;
        }
        if (CsvReader.enabledOptions(commandLine)) {
            reader = new CsvReader(commandLine);
        } else if (OracleReader.enabledOptions(commandLine)) {
            reader = new OracleReader(commandLine);
        }
        // Connect to Nuxeo server
        String nuxeoPassword = commandLine.getOptionValue("nuxeo-password", "Administrator");
        String nuxeoUser = commandLine.getOptionValue("nuxeo-user", "Administrator");
        String nuxeoUrl = commandLine.getOptionValue("nuxeo-url");
        String domain = commandLine.getOptionValue("domain");
        new FVPhraseMigrator(commandLine, reader).process(nuxeoUrl, nuxeoUser, nuxeoPassword, "/" + domain + "/Workspaces/");
	}

}
