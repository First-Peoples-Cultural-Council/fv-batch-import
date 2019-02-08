package task;

import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.nuxeo.ecm.automation.client.Constants;
import org.nuxeo.ecm.automation.client.RemoteException;
import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.jaxrs.impl.HttpAutomationClient;
import org.nuxeo.ecm.automation.client.model.Document;
import org.nuxeo.ecm.automation.client.model.Documents;

import common.CsvLogWriter;
import mappers.firstvoices.WordMapper;
import reader.CsvReader;
import reader.OracleReader;

public class TestPublish {

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

	public TestPublish(CsvReader reader, CommandLine cmd) {

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

					Documents docs = (Documents) session.newRequest("Repository.Query").setHeader(
			        Constants.HEADER_NX_SCHEMAS, "*")
					.set("query", "SELECT * FROM FVDialect WHERE  ecm:isProxy = 0 AND ecm:isCheckedInVersion = 0 AND ecm:currentLifeCycleState = 'Published' ORDER BY dc:title ASC")
					.set("currentPageIndex", 0).set("pageSize", 100).execute();
			
					int i;
					int j;
					
					for (i = 0; i < docs.size(); i++) {

						for (int n = 0; n < 10; n++) {
							Documents words = (Documents) session.newRequest("Repository.Query").setHeader(
							        Constants.HEADER_NX_SCHEMAS, "*")
									.set("query", "SELECT * FROM FVWord WHERE ecm:isProxy = 0 AND ecm:currentLifeCycleState = 'Enabled' AND fva:dialect = '" + docs.get(i).getId() + "' ORDER BY dc:title ASC")
									.set("currentPageIndex", 0).execute();
							
							System.out.println(docs.get(i).getPath());
							System.out.println(words.size());
							
							for (j = 0; j < words.size(); j++) {
								
								//System.out.println(words.get(j).getPath());
								
								if (!"Published".equals(words.get(j).getState())){
									session.newRequest("Document.FollowLifecycleTransition")
									.setInput(words.get(j))
									.set("value", "Publish")
									.execute();									
								}
	
								if (j % 1000 == 0)
									System.out.println(j);
							}
						}

						System.out.println(i);
					}

				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				//if (results != null && results.size() > 0) {
				//	System.out.println((int) results.get(0).get("COUNT(ecm:uuid)"));
				//}
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
		new TestPublish(reader, cmd);
	}
}
