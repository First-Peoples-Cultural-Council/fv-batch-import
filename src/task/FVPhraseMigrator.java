package task;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.logging.FileHandler;

import mappers.CsvMapper;
import mappers.CsvValidator;
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
 * @author cstuart dyona
 * Class to migrate phrases from a CSV file (or potentially an Oracle database) into the FirstVoices system.
 */
public class FVPhraseMigrator extends AbstractMigrator {

    protected CsvMapper mapper = null;

	public FVPhraseMigrator(AbstractReader reader, String[] argv) {
		super(reader);

        // Build command line
        buildCommandLine("FV Batch Import - Phrases", this, argv);

        // Set binary mapper data path
        BinaryMapper.setDataPath(blobDataPath);

		mapper = new PhraseMigratorMapper();
        mapper.setFakeCreation(false);

        // Setup output of errors and log to path of data/csv file
        if (csvFile != null) {
            setupErrorOutputFiles(csvFile);
        }
	}

	@Override
	protected void processRow(Session session) throws IOException {
        Map<String, Document> docs = getOrCreateLanguageDocument(session, reader);
        docs.put("parent", docs.get("Dictionary"));

        try {
            Document phraseDoc = mapper.process(docs, session, reader);
        } catch (IOException e) {
            // File not found
            logWriter.writeLine(reader.getRow(), e.getMessage());
        }


        if (lines % 1000 == 0) {
            ConsoleLogger.out("##### " + lines + " lines, " + errors + " errors, "
            		+ PhraseMigratorMapper.createdPhrases + " phrases created, "
            		+ PhraseMigratorMapper.updatedPhrases + " null import_id phrases updated, "
            		+ PhraseMigratorMapper.cachedPhrases + " phrases in cache and skipped, "
            		+ CsvMapper.createdObjects + " objects created");
        }
	}

    public static void main(String[] argv) throws Exception, SQLException, ClassNotFoundException, ParseException {
        AbstractReader reader = null;

        FVPhraseMigrator phraseMigrator = new FVPhraseMigrator(reader, argv);

        if (csvFile != null && !csvFile.isEmpty()) {
            reader = new CsvReader(csvFile);
            phraseMigrator.setReader(reader);
        }

        CsvValidator csvVal = new CsvValidator(url, username, password, csvFile, dialectID);
        List<String> valid = csvVal.validate(blobDataPath);

        if(valid.isEmpty())
            phraseMigrator.process(url, username, password, "/" + domain + "/Workspaces/");
        else
            System.out.println(valid);

        csvVal.close();
	}

}
