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
import mappers.firstvoices.WordMapper;
import mappers.firstvoices.alphabet.CharacterMigratorMapper;
import mappers.firstvoices.book.BookMigratorMapper;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.model.Document;

import reader.AbstractReader;
import reader.CsvReader;
import reader.OracleReader;
import common.ConsoleLogger;
import common.CsvLogWriter;

public class FVCharacterMigrator extends AbstractMigrator {

    protected CsvMapper mapper = null;
	
    protected FVCharacterMigrator(AbstractReader reader, String[] argv) {
        super(reader);

        // Build command line
        buildCommandLine("FV Batch Import - Characters", this, argv);

        // Set binary mapper data path
        BinaryMapper.setDataPath(blobDataPath);

        mapper = new CharacterMigratorMapper();
        mapper.setFakeCreation(false);

        // Setup output of errors and log to path of data/csv file
        if (csvFile != null) {
            setupErrorOutputFiles(csvFile);
        }
    }

	@Override
	protected void processRow(Session session) throws IOException {				
        Map<String, Document> docs = getOrCreateLanguageDocument(session, reader);
        docs.put("parent", docs.get("Alphabet"));
        mapper.process(docs, session, reader);
        ConsoleLogger.out("##### " + lines + " lines, " + errors + " errors, " 
            		+ CharacterMigratorMapper.createdCharacters + " Characters created, "
            		+ CharacterMigratorMapper.existingCharacters + " Characters skipped, "            		
            		+ CharacterMigratorMapper.createdObjects + " objects created");        
	}

	public static void main(String[] argv) throws Exception, SQLException, ClassNotFoundException, ParseException {
        AbstractReader reader = null;

        FVCharacterMigrator characterMigrator = new FVCharacterMigrator(reader, argv);

        if (csvFile != null && !csvFile.isEmpty()) {
            reader = new CsvReader(csvFile);
            characterMigrator.setReader(reader);
        }

        CsvValidator csvVal = new CsvValidator(url, username, password, csvFile, dialectID);
        List<String> valid = csvVal.validate(blobDataPath);

        if(valid.isEmpty())
            characterMigrator.process(url, username, password, "/" + domain + "/Workspaces/");
        else
            System.out.println(valid);

        csvVal.close();
	}

}
