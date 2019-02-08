package task;

import com.beust.jcommander.JCommander;
import common.ConsoleLogger;
import mappers.CsvMapper;
import mappers.firstvoices.BinaryMapper;
import mappers.firstvoices.WordMapper;
import org.apache.commons.cli.ParseException;
import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.model.Document;
import reader.AbstractReader;
import reader.CsvReader;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

/**
 * Class to migrate words from a CSV file (or potentially an Oracle database) into the FirstVoices system.
 */
public class FVWordMigrator extends AbstractMigrator {

    protected CsvMapper mapper = null;

    protected FVWordMigrator(AbstractReader reader, String[] argv) {
        super(reader);

        // Build command line
        buildCommandLine("FV Batch Import - Words", this, argv);

        // Set binary mapper data path
        BinaryMapper.setDataPath(blobDataPath);

        mapper = new WordMapper();
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
            Document wordDoc = mapper.process(docs, session, reader);
        } catch (IOException e) {
            // File not found
            logWriter.writeLine(reader.getRow(), e.getMessage());
        }

        if (lines % 1000 == 0) {
            ConsoleLogger.out("##### " + lines + " lines " + errors + " errors " + CsvMapper.createdWords + " words " + CsvMapper.createdObjects + " objects ");
        }
    }

    private void setReader(AbstractReader reader) {
        this.reader = reader;
    }

    public static void main(String[] argv) throws Exception, SQLException, ClassNotFoundException, ParseException {
        AbstractReader reader = null;

        FVWordMigrator wordMigrator = new FVWordMigrator(reader, argv);

        if (csvFile != null && !csvFile.isEmpty()) {
            reader = new CsvReader(csvFile);
            wordMigrator.setReader(reader);
        }

        // TODO: Add some more validation of the CSV file prior to processing
        // e.g. are images present? Are there duplicates internally?

        wordMigrator.process(url, username, password, "/" + domain + "/Workspaces/");
    }
}
