/**
 *
 */
package task;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import mappers.CsvMapper;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.nuxeo.ecm.automation.client.model.Document;
import org.nuxeo.ecm.automation.client.Constants;
import org.nuxeo.ecm.automation.client.RemoteException;
import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.jaxrs.impl.HttpAutomationClient;
import org.nuxeo.ecm.automation.client.model.Documents;
import common.CsvLogWriter;
import common.SkipRowException;
import org.nuxeo.ecm.automation.client.model.FileBlob;
import org.omg.CORBA.portable.ApplicationException;
import reader.AbstractReader;
import reader.CsvReader;
import reader.OracleReader;

/**
 * @author loopingz
 * TODO: Remove portions of code that are related to setting up the dialect/etc - some commented.
 */
public abstract class AbstractMigrator {
	protected int lines = 0;
	protected int wordCount = 0;

	protected Document root = null;
	protected String rootPath = null;
    protected String dataPath = null;
	protected String sharedDataPath = null;
	protected Document sectionRoot = null;
	protected String sectionRootPath = null;
	protected String sectionDataPath = null;
	protected String sectionSharedDataPath = null;
    // Prefix used to distinguish Section documents in the cache
    protected String sectionCachePrefix = "SECTION_";

	protected int errors = 0;
	protected Logger log = Logger.getLogger("Migrator");
	protected AbstractReader reader = null;
	protected Map<String, Document> folderDocumentsCache = new HashMap<String, Document>();
	protected Map<String, Map<String, Document>> baseDocumentsCache = new HashMap<String, Map<String, Document>>();
	protected String familyDocumentType = "FVLanguageFamily";
	protected String languageDocumentType = "FVLanguage";
	protected String dialectDocumentType = "FVDialect";
	protected CsvLogWriter logWriter = null;
	static Options options = new Options();
	protected boolean isDialectNewlyCreated = false;

	protected static JCommander jc = null;

	@Parameter(names = { "-url" }, description = "Nuxeo URL to connect to", required = true)
	protected static String url;

	@Parameter(names = { "-username" }, description = "Username to connect with", required = true)
	protected static String username;

	@Parameter(names = { "-password" }, description = "Password to connect with", required = true, password = true)
	protected static String password;

    @Parameter(names = { "-domain" }, description = "Nuxeo Domain to operate within", required = true)
    protected static String domain;

    @Parameter(names = { "-limit" }, description = "Limit the number of lines to process")
    protected static int limit = 0;

    @Parameter(names = { "-dialect-id" }, description = "The GUID of the dialect to input the entries into", required = true)
    protected static String dialectID;

    @Parameter(names = { "-csv-file" }, description = "Path to CSV file", required = true)
    protected static String csvFile;

    @Parameter(names = { "-data-path" }, description = "Path to media files")
    protected static String blobDataPath;

    protected AbstractMigrator(AbstractReader reader) {
		this.reader = reader;
	}

	protected abstract void processRow(Session session) throws IOException;

	protected static void setOptions() {

        CsvReader.addOptions(options);
        OracleReader.addOptions(options);
        options.addOption(
                Option.builder().longOpt("nuxeo-url").hasArg().required().desc("Nuxeo URL to connect to").build());
        options.addOption(Option.builder().longOpt("nuxeo-user").hasArg()
                .desc("Nuxeo User to connect with (default: Administrator)").build());
        options.addOption(Option.builder().longOpt("nuxeo-password").hasArg()
                .desc("Nuxeo Password to connect with (default: Administrator)").build());
		options.addOption(
				Option.builder().longOpt("dialect-id").hasArg().required().desc("The GUID of the dialect to input the entries into").build());
		options.addOption(
				Option.builder().longOpt("username").hasArg().desc("The username to apply to the created documents").build());
        options.addOption(
                Option.builder().longOpt("domain").hasArg().required().desc("Nuxeo Domain to operate within").build());
        options.addOption(
                Option.builder().longOpt("limit").hasArg().desc("Limit the number of lines to process").build());
	}

	public void process(String nuxeoUrl, String nuxeoUser, String nuxeoPassword, String rootPath) {
		this.rootPath = rootPath;
		dataPath = rootPath + "Data/";
		sharedDataPath = rootPath + "SharedData/";

		// TODO: handle this better
		String domain = rootPath.split("/Workspaces/")[0];
		sectionRootPath = domain + "/sections";
		sectionDataPath = sectionRootPath + "/Data/";
		sectionSharedDataPath = sectionRootPath + "/SharedData/";


        reader.open();

        if (!nuxeoUrl.endsWith("/site/automation")) {
            nuxeoUrl += "/site/automation";
        }
        String connection = nuxeoUrl + " with " + nuxeoUser + "/";
        for (int i = 0; i < nuxeoPassword.length(); i++) {
            connection += "*";
        }
        System.out.println("Starting connection to Nuxeo: " + connection);
        HttpAutomationClient client = new HttpAutomationClient(nuxeoUrl);
        Session session = null;

        try {
            session = client.getSession(nuxeoUser, nuxeoPassword);
            if (session == null) {
                System.err.println("Can't open a session on Nuxeo Server");
                return;
            }
            try {
                root = (Document) session.newRequest("Document.Fetch").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
                        .set("value", dataPath).execute();

                sectionRoot = (Document) session.newRequest("Document.Fetch").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
                        .set("value", sectionDataPath).execute();

                FileBlob parts_of_speech =  (FileBlob) session.newRequest("Directory.Entries")
                        .set("directoryName", "parts_of_speech").execute();



                // Print the name out
                while (reader.next()) {
                    lines++;
                    if (limit > 0 && lines > limit) {
                        lines--;
                        break;
                    }
                    try {
                        processRow(session);
                    } catch (SkipRowException | RemoteException e) {
                        e.printStackTrace();
                        //    if (!(e instanceof SkipRowException)) {
                        errors++;
                        //    }
                        if (logWriter != null) {
                            // Save row to another CSV
                            logWriter.writeLine(reader.getRow(), e.getMessage());
                        }
                        log.warning(e.getClass().getName() + ": " + e.getMessage() + " for " + reader.printRow());
                    }
                }
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }
            /**
             * Prepare Photo
             */

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            if (client != null) {
                client.shutdown();
            }
            if (logWriter != null) {
                // Save row to another CSV
                logWriter.close();
            }
        }
    }
	protected String getId(String id) {
		return id.replace("/", "_");
	}

	protected Map<String, Document> getOrCreateLanguageDocument(Session session, AbstractReader reader)
			throws IOException {
		return getOrCreateLanguageDocument(session, null, null, null);
	}

    protected void setProperty(Document doc, String property, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        doc.set(property, value);
    }

	protected Map<String, Document> getOrCreateLanguageDocument(Session session, String family, String lang, String dialect)
			throws IOException {

		String cacheKey = dialectID;
		String key = null;

		if (baseDocumentsCache.containsKey(cacheKey)) {
			return baseDocumentsCache.get(cacheKey);
		}

		Document dialectDoc = null;
		Document sectionDialectDoc = null;

		key += dialectID;

		try {
			if (!folderDocumentsCache.containsKey(key)) {
				dialectDoc = (Document) session.newRequest("Document.Fetch").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
						.set("value", dialectID).execute();

				/*if(reader.getString("DIALECT_PUBLISHED").equals("1") && reader.getString("DIALECT_STATUS").equals("1")) {
					sectionDialectDoc = (Document) session.newRequest("Document.Fetch").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
						.set("value", sectionDataPath + key).execute();
					folderDocumentsCache.put(sectionCachePrefix + key, sectionDialectDoc);
				}*/
				folderDocumentsCache.put(key, dialectDoc);

			} else {
				dialectDoc = folderDocumentsCache.get(key);
				sectionDialectDoc = folderDocumentsCache.get(sectionCachePrefix + key);
			}

		} catch (RemoteException e) {
		    e.printStackTrace();
		}

        Documents dialectChildren = (Documents) session.newRequest("Document.GetChildren")
                                                .setHeader(Constants.HEADER_NX_SCHEMAS, "*")
                                                .setInput(dialectDoc)
                                                .execute();

        Map<String, Document> cache = new HashMap<String, Document>();

        // Add dialect document to cache
        cache.put("Dialect", dialectDoc);

        // Add SharedData directory to cache
        cache.put("Shared Data", (Document) session.newRequest("Document.Fetch").setHeader(Constants.HEADER_NX_SCHEMAS, "*").set("value", sharedDataPath).execute());

        // Add dialect children to cache
        Iterator<Document> dialectChildrenIterator = dialectChildren.iterator();

        while (dialectChildrenIterator.hasNext()) {
            Document dialectChild = dialectChildrenIterator.next();
            cache.put(dialectChild.getTitle(), dialectChild);
        }

		/*Document sectionDictionary = null;
		Document sectionContributors = null;
		Document sectionResources = null;
		Document sectionCategories = null;
		Document sectionPhraseBooks = null;
		Document sectionStoriesAndSongs = null;
		Document sectionAlphabet = null;
		Document sectionSharedData = (Document) session.newRequest("Document.Fetch").setHeader(Constants.HEADER_NX_SCHEMAS, "*").set("value", sectionSharedDataPath).execute();

		// If the dialect and its children should exist in the section, either publish them, or retrieve them if they already exist
		if(reader.getString("DIALECT_PUBLISHED").equals("1")) {
			// If the dialect is new, check if it should be published
			if(isDialectNewlyCreated) {

				try {
					session.newRequest("Document.FollowLifecycleTransition")
					.setInput(dialectDoc)
					.set("value", "Publish")
					.execute();

				} catch (Exception e) {
					System.out.println(e.getCause());
				}
			}
			else {
				// Fetch section dialect and its children
				sectionDialectDoc = (Document) session.newRequest("Document.Fetch").setHeader(Constants.HEADER_NX_SCHEMAS, "*").set("value", sectionDataPath + key).execute();
				sectionDictionary = (Document) session.newRequest("Document.Fetch").setHeader(Constants.HEADER_NX_SCHEMAS, "*").set("value", sectionDataPath + key + "/Dictionary").execute();
				sectionContributors = (Document) session.newRequest("Document.Fetch").setHeader(Constants.HEADER_NX_SCHEMAS, "*").set("value", sectionDataPath + key + "/Contributors").execute();
				sectionResources = (Document) session.newRequest("Document.Fetch").setHeader(Constants.HEADER_NX_SCHEMAS, "*").set("value", sectionDataPath + key + "/Resources").execute();
				sectionCategories = (Document) session.newRequest("Document.Fetch").setHeader(Constants.HEADER_NX_SCHEMAS, "*").set("value", sectionDataPath + key + "/Categories").execute();
				sectionPhraseBooks = (Document) session.newRequest("Document.Fetch").setHeader(Constants.HEADER_NX_SCHEMAS, "*").set("value", sectionDataPath + key + "/Phrase Books").execute();
				sectionStoriesAndSongs = (Document) session.newRequest("Document.Fetch").setHeader(Constants.HEADER_NX_SCHEMAS, "*").set("value", sectionDataPath + key + "/Stories & Songs").execute();
				sectionAlphabet = (Document) session.newRequest("Document.Fetch").setHeader(Constants.HEADER_NX_SCHEMAS, "*").set("value", sectionDataPath + key + "/Alphabet").execute();
			}

			// Cache section documents
			cache.put(sectionCachePrefix + "Dialect", sectionDialectDoc);
			cache.put(sectionCachePrefix + "Dictionary", sectionDictionary);
			cache.put(sectionCachePrefix + "Contributors", sectionContributors);
			cache.put(sectionCachePrefix + "Resources", sectionResources);
			cache.put(sectionCachePrefix + "Categories", sectionCategories);
			cache.put(sectionCachePrefix + "Phrase Books", sectionPhraseBooks);
			cache.put(sectionCachePrefix + "Stories & Songs", sectionStoriesAndSongs);
			cache.put(sectionCachePrefix + "Alphabet", sectionAlphabet);
			cache.put(sectionCachePrefix + "Shared Data", sectionSharedData);
		}
		// Make private dialects so by blocking inheritance
		else if (reader.getString("DIALECT_STATUS").equals("0") || reader.getString("DIALECT_STATUS").equals("2")) {
			sectionDialectDoc = (Document) session.newRequest("Document.BlockPermissionInheritance").setInput(dialectDoc).set("acl","local").execute();
		}*/

		baseDocumentsCache.put(cacheKey, cache);
		return cache;
	}

	protected void setupErrorOutputFiles(String csvFileLocation) {
        // Set tag
        File csvFileWrapper = new File(csvFile);

        try {
            FileHandler fh = new FileHandler(new File(new File(csvFileWrapper.getParent()), "import.log").getAbsolutePath());
            log.addHandler(fh);
        } catch (SecurityException | IOException e) {
            e.printStackTrace();
        }

        // Create tag based on date and CSV file
        CsvMapper.setTag(LocalDate.now().toString() + "_" + csvFileWrapper.getName());

        // Remove the .csv from the file to create a file_errors.csv
        logWriter = new CsvLogWriter(csvFile.substring(0, csvFile.length()-4) + "_errors.csv");
    }

    protected void buildCommandLine(String migratorName, Object migrator, String[] argv) {
        // Create new command line builder
        jc = JCommander.newBuilder()
                .addObject(migrator)
                .build();

        jc.setProgramName(migratorName);

        // Parse arguments
        jc.parse(argv);

        // Show usage
        jc.usage();
    }
}
