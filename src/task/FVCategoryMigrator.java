package task;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.FileHandler;

import mappers.CsvMapper;
import mappers.firstvoices.CategoryMigratorMapper;

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

import reader.AbstractReader;
import reader.CsvReader;
import reader.OracleReader;

import common.ConsoleLogger;
import common.CsvLogWriter;

/**
 * @author cstuart
 *
 */
public class FVCategoryMigrator extends AbstractMigrator {

    protected CsvMapper mapper = null;
    protected String sharedCategoriesFolderName = "Shared Categories";
	Map<String, Document> cache = new HashMap<String, Document>();

    protected FVCategoryMigrator(CommandLine cmd, AbstractReader reader) {
        super(reader);

        mapper = new CategoryMigratorMapper();
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

		String dictionaryId = reader.getString("DICTIONARY_ID");
    	
		// Only process shared categories (ones with empty dictionary id)
		if(dictionaryId.isEmpty()) {
			Map<String, Document> docs = null;

			String parentCategoryId = reader.getString("PARENT_CATEGORY");
	        if(parentCategoryId.isEmpty()) {
				// Create the category at the root of /Shared Data/Categories
		        docs = getOrCreateSharedCategoriesFolder(session, reader);
			} else {
				// Create the category as a child of the specified parent category
		        docs = getParentCategory(parentCategoryId, session, reader);
			}		
			
	        mapper.process(docs, session, reader);
	        if (lines % 1 == 0) {
	            ConsoleLogger.out("##### " + lines + " lines, " + errors + " errors, " 
	            		+ CategoryMigratorMapper.createdCategories + " Categories created, "
	            		+ CategoryMigratorMapper.updatedCategories + " Categories updated, "            		
	            		+ CategoryMigratorMapper.createdObjects + " objects created");;
	        }
		}
    }
    
	protected Map<String, Document> getOrCreateSharedCategoriesFolder(Session session, AbstractReader reader)
			throws IOException {
		
		Document sharedCategoriesFolder = null;
		Document sectionSharedCategoriesFolder = null;

		root = (Document) session.newRequest("Document.Fetch").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
				.set("value", sharedDataPath).execute();

		sectionRoot = (Document) session.newRequest("Document.Fetch").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
				.set("value", sectionSharedDataPath).execute();		
		
		try {			
			// FV/Workspaces/SharedData/Shared Categories
			sharedCategoriesFolder = (Document) session.newRequest("Document.Fetch").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
				.set("value", sharedDataPath + sharedCategoriesFolderName).execute();
			
			// FV/Sections/SharedData/Shared Categories
			sectionSharedCategoriesFolder = (Document) session.newRequest("Document.Fetch").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
					.set("value", sectionSharedDataPath + sharedCategoriesFolderName).execute();
			
		} catch (RemoteException e) {
			// Should use exists first but prefer to reduce number of network calls
			if (!e.getCause().getMessage().contains("DocumentNotFoundException")) {
				throw e;
			}
			sharedCategoriesFolder = new Document(sharedCategoriesFolderName, "FVCategories");
			sharedCategoriesFolder.set("dc:title", sharedCategoriesFolderName);
			sharedCategoriesFolder = (Document) session.newRequest("Document.Create").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
					.setInput(root).set("type", sharedCategoriesFolder.getType()).set("name", sharedCategoriesFolder.getId())
					.set("properties", sharedCategoriesFolder).execute();
			
			// Publish Shared Categories folder to the section
			sectionSharedCategoriesFolder = (Document) session.newRequest("Document.Publish").setInput(sharedCategoriesFolder).set("target", sectionSharedDataPath).execute();						
		}		

		cache.put("parent", sharedCategoriesFolder);
		cache.put(sectionCachePrefix + "parent", sectionSharedCategoriesFolder);
		
		return cache;
	}
	
	protected Map<String, Document> getParentCategory(String parentCategoryId, Session session, AbstractReader reader)
			throws IOException {
		
		Document parentCategory = null;
		Document sectionParentCategory = null;
		
		try {			
			parentCategory = (Document) session.newRequest("Document.Fetch").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
				.set("value", sharedDataPath + sharedCategoriesFolderName + "/" + parentCategoryId).execute();
		} catch (RemoteException e) {
			// Should use exists first but prefer to reduce number of network calls
			if (!e.getCause().getMessage().contains("DocumentNotFoundException")) {
				throw e;
			}
		}		

		try {			
			sectionParentCategory = (Document) session.newRequest("Document.Fetch").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
				.set("value", sectionSharedDataPath + sharedCategoriesFolderName + "/" + parentCategoryId).execute();
		} catch (RemoteException e) {
			// Should use exists first but prefer to reduce number of network calls
			if (!e.getCause().getMessage().contains("DocumentNotFoundException")) {
				throw e;
			}
		}			
		
		//Map<String, Document> cache = new HashMap<String, Document>();
		cache.put("parent", parentCategory);
		cache.put(sectionCachePrefix + "parent", sectionParentCategory);
		
		return cache;
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
        new FVCategoryMigrator(commandLine, reader).process(nuxeoUrl, nuxeoUser, nuxeoPassword, "/" + domain +"/Workspaces/");
    }

}
