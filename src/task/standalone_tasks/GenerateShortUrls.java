package task.standalone_tasks;

import common.CsvLogWriter;
import mappers.firstvoices.WordMapper;
import org.apache.commons.cli.*;
import org.apache.commons.lang.StringUtils;
import org.nuxeo.ecm.automation.client.Constants;
import org.nuxeo.ecm.automation.client.RemoteException;
import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.jaxrs.impl.HttpAutomationClient;
import org.nuxeo.ecm.automation.client.model.Document;
import org.nuxeo.ecm.automation.client.model.Documents;
import org.nuxeo.ecm.automation.client.model.PropertyMap;
import reader.CsvReader;
import reader.OracleReader;

import java.io.Console;
import java.util.*;
import java.io.IOException;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;

public class GenerateShortUrls {

	protected CsvLogWriter logWriter = null;

	static CsvReader reader = null;
	static Options options = new Options();

	Session session = null;
	HttpAutomationClient client = null;
	WordMapper mapper;

	String rootPath = "";

	protected int limit = 0;
	protected int lines = 0;
	protected int wordCount = 0;

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
                Option.builder().longOpt("domain").hasArg().required().desc("Nuxeo Domain to operate within").build());
	}

	public GenerateShortUrls(CsvReader reader, CommandLine cmd, HashMap<String, String> inputs) {

	    // Open CSV file
		reader.open();

        String nuxeoPassword = cmd.getOptionValue("nuxeo-password", inputs.get("password"));
        String nuxeoUser = cmd.getOptionValue("nuxeo-user", "Administrator");
        String nuxeoUrl = cmd.getOptionValue("nuxeo-url");
        String domain = cmd.getOptionValue("domain");

        rootPath = "/" + domain + "/Workspaces/";

		if (!nuxeoUrl.endsWith("/site/automation")) {
			nuxeoUrl += "/site/automation";
		}

        // Establish connection to Nuxeo
		client = new HttpAutomationClient(nuxeoUrl);

		try {
			session = client.getSession(nuxeoUser, nuxeoPassword);

			try {

				try {

					Documents docs = (Documents) session.newRequest("Repository.Query")
                    .setHeader(Constants.HEADER_NX_SCHEMAS, "*")
					.set("query", "SELECT * FROM FVDialect WHERE ecm:isCheckedInVersion = 0 AND ecm:currentLifeCycleState != 'deleted'")
					.set("currentPageIndex", 0).set("pageSize", 1000).execute();

			        Iterator<Document> docsIterator = docs.iterator();

			        System.out.println("Found " + docs.size() + " results");
			        System.out.println(" *************** ");

			        // Iterate over CSV with short URL mappings
                    while (reader.next()) {
                        String id = reader.getString("ID");
                        String friendly_url_segment = reader.getString("FRIENDLY_URL_SEGMENT");

                        // Skip if no ID specified in CSV file or no short URL provided
                        if (id == null || friendly_url_segment == null || friendly_url_segment.isEmpty()) {
                            continue;
                        }

                        // If you need the results as a typed array:
                        Collection<Document> docCollection = docs.list();

                        Predicate predicate = new Predicate() {
                            public boolean evaluate(Object object) {
                                String import_id = ((Document) object).getProperties().getString("fvl:import_id");

                                if (import_id != null) {
                                    return import_id.equals(id);
                                }

                                return false;
                            }
                        };

                        // Find matching document in docs collection
                        Document foundDoc = (Document) CollectionUtils.find(docCollection, predicate);

                        // If document found in collection
                        if (foundDoc != null) {
                            System.out.println("On " + foundDoc.getPath() + " (ID: " + id + ") - planning to set friendly URL to '" + friendly_url_segment + "'");
                            //System.out.println("http://localhost:3001/Workspaces/" + friendly_url_segment);

                            // No need to set friendly URL if dialect title matches friendly URL
                            if (friendly_url_segment.equals(foundDoc.getTitle())) {
                                continue;
                            }

                            // Set short URL
                            foundDoc.set("fvdialect:short_url", friendly_url_segment);

                            // Update dialect
                            Document updatedDoc = (Document) session.newRequest("Document.Update").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
                                    .setInput(foundDoc)
                                    .set("properties", foundDoc).execute();

                            // Publish dialect (directly)

                            try {
                                String[] splitPath = foundDoc.getPath().split("/");
                                String publishToSection = "/FV/sections/Data/" + splitPath[4] + "/" + splitPath[5] + "/";

                                Document publishedDoc = (Document) session.newRequest("Document.PublishToSection").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
                                        .setInput(foundDoc)
                                        .set("target", publishToSection)
                                        .execute();

                                System.out.println("Published Dialect to: " + publishedDoc.getPath());

                            } catch (RemoteException e) {

                            }

                            System.out.println("Set friendly URL to: " + updatedDoc.getProperties().getString("fvdialect:short_url"));


                        } else {
                            System.out.println("Could not find matching doc for ID:" + id + ", Path: " + friendly_url_segment);
                        }
                        System.out.println(" *************** ");
                    }

				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} finally {
				    session.close();
				}
			}
			catch (RemoteException e) {
				e.printStackTrace();
			}

			if (session == null) {
				System.err.println("Can't open a session on Nuxeo Server");
				return;
			}
		} catch(Exception e) {
			e.printStackTrace();
		}


	}

	private static CommandLine getCommandLine(String[] argv){
        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine = null;

        try {
            commandLine = parser.parse(options, argv);
        } catch (ParseException e) {
            e.printStackTrace();
            System.out.println("Error parsing...");
        }

        return commandLine;
	}


	public static void main(String[] argv) {

        HashMap<String, String> inputs = new HashMap<>();

        // Prompt for password
        Console console = System.console();

        if (console != null) {
            String password = new String(console.readPassword("Please enter your password: "));
            inputs.put("password", password);
        }

		setOptions();

		CommandLine cmd = getCommandLine(argv);

		if (CsvReader.enabledOptions(cmd)) {
			reader = new CsvReader(cmd);
		}

		new GenerateShortUrls(reader, cmd, inputs);
	}
}
