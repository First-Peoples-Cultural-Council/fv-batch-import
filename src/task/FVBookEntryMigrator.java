package task;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.FileHandler;

import mappers.CsvMapper;
import mappers.firstvoices.BinaryMapper;
import mappers.firstvoices.book.BookEntryMigratorMapper;
import mappers.firstvoices.book.BookMigratorMapper;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.nuxeo.ecm.automation.client.Constants;
import org.nuxeo.ecm.automation.client.RemoteException;
import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.model.Document;
import org.nuxeo.ecm.automation.client.model.Documents;

import reader.AbstractReader;
import reader.CsvReader;
import reader.OracleReader;
import common.ConsoleLogger;
import common.CsvLogWriter;
import common.SkipRowException;

/**
 * @author cstuart
 *
 */
public class FVBookEntryMigrator extends AbstractMigrator {

    protected CsvMapper mapper = null;
	Map<String, Document> bookDocumentCache = null;
	Map<String, Document> sectionBookDocumentCache = null;
	Map<String, Document> cache = new HashMap<String, Document>();
	
    protected FVBookEntryMigrator(CommandLine cmd, AbstractReader reader) {
        super(reader);

        BinaryMapper.setDataPath(cmd.getOptionValue("data-path"));
        
        mapper = new BookEntryMigratorMapper();
                        
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

		docs.put("parent", getParentBook(session, reader));
		docs.put(sectionCachePrefix + "parent", getSectionParentBook(session, reader));
		
        mapper.process(docs, session, reader);
        ConsoleLogger.out("##### " + lines + " lines, " + errors + " errors, " 
            		+ BookEntryMigratorMapper.createdBookEntries + " Book Entries created, "
            		+ BookEntryMigratorMapper.updatedBookEntries + " Book Entries updated, "            		
            		+ BookEntryMigratorMapper.createdObjects + " objects created");
	}
	
	protected Document getParentBook(Session session, AbstractReader reader) throws IOException {		
		if(bookDocumentCache == null) {
			bookDocumentCache =  new HashMap<String, Document>(); 
			cacheBookDocuments(session);
		}		
		Document parentBookDoc = bookDocumentCache.get(reader.getString("SENTRY_BOOK_ID"));		
		if(parentBookDoc == null) {
			throw new SkipRowException("Skipping...missing parent Book document.");
		}
		
		return parentBookDoc;
	}

	protected Document getSectionParentBook(Session session, AbstractReader reader) throws IOException {	
		if(sectionBookDocumentCache == null) {
			sectionBookDocumentCache =  new HashMap<String, Document>(); 
			cacheSectionBookDocuments(session);
		}		
		Document sectionParentBookDoc = sectionBookDocumentCache.get(reader.getString("SENTRY_BOOK_ID"));		
//		if(sectionParentBookDoc == null) {
//			throw new SkipRowException("Skipping...missing parent Book document in section.");
//		}
		
		return sectionParentBookDoc;
	}	
	
	protected Map<String, Document> cacheBookDocuments(Session session) throws IOException {
		Integer page = 0;
		Integer pageSize = 1000;
		System.out.println("Loading Book document cache...");
		String query = "SELECT * FROM FVBook WHERE ecm:currentLifeCycleState != 'deleted' AND ecm:path STARTSWITH '/FV/Workspaces'";
		
		while (true) {
			Documents docs = (Documents) session.newRequest("Repository.Query").setHeader(
			        Constants.HEADER_NX_SCHEMAS, "*")
					.set("query", query)
					.set("currentPageIndex", page).set("pageSize", pageSize).execute();
			for (int i = 0; i < docs.size(); i++) {
				String importId = docs.get(i).getProperties().getString("fvl:import_id");
				bookDocumentCache.put(importId, docs.get(i));
			}
			if (docs.size() < pageSize) {
				break;
			}
			page++;
		}
        ConsoleLogger.out("Caching " + bookDocumentCache.size() + " Book documents");
		
		return bookDocumentCache;		
	} 	

	protected Map<String, Document> cacheSectionBookDocuments(Session session) throws IOException {
		Integer page = 0;
		Integer pageSize = 1000;
		System.out.println("Loading section Book document cache...");
		String query = "SELECT * FROM FVBook WHERE ecm:currentLifeCycleState != 'deleted' AND ecm:path STARTSWITH '/FV/sections'";
		
		while (true) {
			Documents docs = (Documents) session.newRequest("Repository.Query").setHeader(
			        Constants.HEADER_NX_SCHEMAS, "*")
					.set("query", query)
					.set("currentPageIndex", page).set("pageSize", pageSize).execute();
			for (int i = 0; i < docs.size(); i++) {
				String importId = docs.get(i).getProperties().getString("fvl:import_id");
				sectionBookDocumentCache.put(importId, docs.get(i));
			}
			if (docs.size() < pageSize) {
				break;
			}
			page++;
		}
        ConsoleLogger.out("Caching " + sectionBookDocumentCache.size() + " section Book documents");
		
		return sectionBookDocumentCache;		
	} 	
	
    public static void main(String[] argv) throws SQLException, ClassNotFoundException, ParseException {
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
        new FVBookEntryMigrator(commandLine, reader).process(nuxeoUrl, nuxeoUser, nuxeoPassword, "/" + domain +"/Workspaces/");
    }

}
