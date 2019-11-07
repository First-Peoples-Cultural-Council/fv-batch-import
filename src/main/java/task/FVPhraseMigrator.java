package task;

import common.ConsoleLogger;
import mappers.CsvMapper;
import mappers.CsvValidator;
import mappers.firstvoices.BinaryMapper;
import mappers.firstvoices.PhraseMigratorMapper;
import reader.AbstractReader;
import reader.CsvReader;
import org.apache.commons.cli.ParseException;
import org.nuxeo.client.NuxeoClient;
import org.nuxeo.client.objects.Document;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

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
	protected void processRow(NuxeoClient client) throws IOException {
        Map<String, Document> docs = getOrCreateLanguageDocument(client, reader);
        docs.put("parent", docs.get("Dictionary"));

        try {
            Document phraseDoc = mapper.process(docs, client, reader);
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
        List<String> valid = csvVal.validate(blobDataPath, limit);

        if(valid.isEmpty() || skipValidation)
            phraseMigrator.process(url, username, password, "/" + domain + "/Workspaces/");
        else
            System.out.println(valid);

        csvVal.close();
	}

}
