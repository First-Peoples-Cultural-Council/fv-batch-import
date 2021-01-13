package task;

import common.ConsoleLogger;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import mappers.CsvMapper;
import mappers.CsvValidator;
import mappers.firstvoices.BinaryMapper;
import mappers.firstvoices.WordMapper;
import org.nuxeo.client.NuxeoClient;
import org.nuxeo.client.objects.Document;
import reader.AbstractReader;
import reader.CsvReader;

/**
 * Class to migrate words from a CSV file (or potentially an Oracle database) into the FirstVoices
 * system.
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
    mapper.setDialectID(dialectID);

    // Setup output of errors and log to path of data/csv file
    if (csvFile != null) {
      setupErrorOutputFiles(csvFile);
    }
  }

  public static void main(String[] argv)
      throws Exception {

    FVWordMigrator wordMigrator = new FVWordMigrator(null, argv);

    if (csvFile != null && !csvFile.isEmpty()) {
      AbstractReader reader = new CsvReader(csvFile);
      wordMigrator.setReader(reader);
    }

    CsvValidator csvVal = new CsvValidator(url, username, password, csvFile, dialectID,
        languagePath);
    HashMap<String, ArrayList<String>> valid = csvVal.validate(blobDataPath, limit);

    if (valid.isEmpty() || Boolean.TRUE.equals(skipValidation)) {
      wordMigrator.process(url, username, password, "/" + domain + "/Workspaces/");
    } else {
      csvVal.printInvalidEntries();
    }

    csvVal.close();
  }

  @Override
  protected void processRow(NuxeoClient client) throws IOException {
    Map<String, Document> docs = getOrCreateLanguageDocument(client, reader);
    docs.put("parent", docs.get("Dictionary"));

    try {
      mapper.process(docs, client, reader);
    } catch (IOException e) {
      // File not found
      logWriter.writeLine(reader.getRow(), e.getMessage());
    }

    if (lines % 1000 == 0) {
      ConsoleLogger.out(
          "##### " + lines + " lines " + errors + " errors " + CsvMapper.createdWords + " words "
              + CsvMapper.createdObjects + " objects ");
    }
  }
}
