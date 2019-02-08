package task.standalone_tasks;

import java.io.IOException;
import java.util.Iterator;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.nuxeo.ecm.automation.client.RemoteException;
import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.jaxrs.impl.HttpAutomationClient;
import org.nuxeo.ecm.automation.client.model.Document;
import org.nuxeo.ecm.automation.client.model.Documents;

import common.CsvLogWriter;
import mappers.firstvoices.WordMapper;
import reader.CsvReader;
import reader.OracleReader;

public class PublishSubset {

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
        options.addOption(
                Option.builder().longOpt("limit").hasArg().desc("Limit the number of lines to process").build());
	}

	public PublishSubset(CsvReader reader, CommandLine cmd) {

		// Setup log writer
		//String csvFile = cmd.getOptionValue("csv-file");
        //csvFile = csvFile.substring(0, csvFile.length()-4) + "_missing.csv";
        //logWriter = new CsvLogWriter(csvFile);

        if (cmd.hasOption("limit")) {
            limit = Integer.valueOf(cmd.getOptionValue("limit"));
        }

        String nuxeoPassword = cmd.getOptionValue("nuxeo-password", "Administrator");
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
					.set("query", "SELECT * FROM FVBook WHERE ecm:path STARTSWITH '/FV/sections/Data/' AND ecm:currentLifeCycleState = 'Disabled'")
					.set("currentPageIndex", 0).set("pageSize", 1000).execute();

			        Iterator<Document> docsIterator = docs.iterator();

			        System.out.println("Found " + docs.size() + " results");

			        while (docsIterator.hasNext()) {
			            Document currentProxyDoc = docsIterator.next();

                        Document source = (Document) session.newRequest("Proxy.GetSourceDocument")
                        .setInput(currentProxyDoc)
                        .execute();

                        Document proxyParent = (Document) session.newRequest("Document.GetParent")
                                .setInput(currentProxyDoc)
                                .execute();

                        System.out.println("Proxy = " + currentProxyDoc.getTitle() + currentProxyDoc.getPath());
//                        System.out.println("SourceDoc = " + source.getPath());
//                        System.out.println("Target = " + proxyParent.getId());

                        if (source.getState().equals("Enabled")){
							session.newRequest("Document.FollowLifecycleTransition")
									.setInput(source)
									.set("value", "Publish")
									.execute();
						} else if (source.getState().equals("Disabled")){
							session.newRequest("Document.Delete")
									.setInput(currentProxyDoc)
									.execute();

						}
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

        options.addOption(
                Option.builder().longOpt("limit").hasArg().desc("Limit the number of lines to process").build());

        try {
            commandLine = parser.parse(options, argv);
        } catch (ParseException e) {
            e.printStackTrace();
            System.out.println("Error parsing...");
        }

        return commandLine;
	}


	public static void main(String[] argv) {

		setOptions();

		CommandLine cmd = getCommandLine(argv);

		reader = new CsvReader(cmd);
		new PublishSubset(reader, cmd);
	}
}
