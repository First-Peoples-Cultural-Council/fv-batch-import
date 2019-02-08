package task.standalone_tasks;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.nuxeo.ecm.automation.client.Constants;
import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.jaxrs.impl.HttpAutomationClient;
import org.nuxeo.ecm.automation.client.model.Document;
import org.nuxeo.ecm.automation.client.model.Documents;
import mappers.CsvMapper;
import reader.CsvReader;
import reader.OracleReader;



public class ReconcileDeltaStatuses {

	static Options options = new Options();

	Session session = null;
	HttpAutomationClient client = null;
	protected CsvMapper mapper = null;

	String rootPath = "";

	protected int limit = 0;
	protected int lines = 0;
	protected int wordCount = 0;

	protected boolean fakeWrite = false;

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

	public ReconcileDeltaStatuses(CommandLine cmd) throws Exception {

	    try {
            System.setOut(new PrintStream(new FileOutputStream("/Users/dyona/Dev/Workspaces/ImportDemo/csv/deltas/reconcile_status_log.txt")));
        } catch (FileNotFoundException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

		// Setup log writer
		/*String csvFile = cmd.getOptionValue("csv-file");
        logWriter = new CsvLogWriter(csvFile.substring(0, csvFile.length()-4) + "_errors.csv");

        // Setup ToDo writer
        logWriterToDo = new CsvLogWriter(csvFile.substring(0, csvFile.length()-4) + "_todo.csv");*/

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

        try {

            // Establish connection to Nuxeo
            client = new HttpAutomationClient(nuxeoUrl);

            try {
                session = client.getSession(nuxeoUser, nuxeoPassword);
            } catch(Exception e) {
                e.printStackTrace();
            }

            int i = 0;
            int skipped = 0;
            int manualTodo = 0;
            int total = 0;
            int noKnown = 0;

            for (int j = 0; j < 5; j++) {
                Documents docs = (Documents) session.newRequest("Repository.Query").setHeader(
                        Constants.HEADER_NX_SCHEMAS, "*")
                        .set("query", "SELECT ecm:uuid, dc:title, ecm:primaryType FROM FVWord, FVPhrase WHERE ecm:path STARTSWITH '/FV/Workspaces' AND dc:modified BETWEEN DATE '2017-05-27' AND DATE '2017-06-10'")
                        .set("currentPageIndex", 0).set("pageSize", 1000).set("sortBy","dc:modified").set("sortOrder", "desc").execute();

                        if (docs.size() > 0) {
                            for (i = 0; i < docs.size(); i++) {
                                Document currentDoc = docs.get(i);

                                System.out.println("####");

                                System.out.println(currentDoc.getId());

                                if (currentDoc.getString("fvl:status_id") != null) {
                                    String legacyStatus = convertStatus(currentDoc.getString("fvl:status_id"), currentDoc.getString("fva:dialect"));
                                    String currentStatus = currentDoc.getState();

                                    if (!currentStatus.equals(legacyStatus)) {
                                        System.out.println(currentStatus + " != " + legacyStatus);

                                        if ("Disabled".equals(currentStatus)) {
                                            System.out.println("Disabled. Double-check manually -- " + currentDoc.getString("fvl:import_id") + " | " + currentDoc.getString("dc:title"));
                                        }
                                        else if (!"Published".equals(currentStatus) && "Published".equals(legacyStatus)) {
                                            System.out.println("Need to publish");

                                            if ("Enabled".equals(currentStatus) || "New".equals(currentStatus)) {
                                                session.newRequest("Document.FollowLifecycleTransition")
                                                .setInput(currentDoc)
                                                .set("value", "Publish")
                                                .execute();
                                            }
                                        }
                                        else if (!"New".equals(currentStatus) && "New".equals(legacyStatus)) {
                                            if ("Enabled".equals(currentStatus) || "Published".equals(currentStatus)) {
                                                session.newRequest("Document.FollowLifecycleTransition")
                                                .setInput(currentDoc)
                                                .set("value", "RevertToNew")
                                                .execute();
                                            }
                                        }
                                        else if (!"Disabled".equals(currentStatus) && "Disabled".equals(legacyStatus)) {
                                            session.newRequest("Document.FollowLifecycleTransition")
                                            .setInput(currentDoc)
                                            .set("value", "Disable")
                                            .execute();
                                        } else {
                                            ++noKnown;
                                            System.out.println("No known action");
                                        }

                                    } else {
                                        ++skipped;
                                        //System.out.println("All Good.");
                                    }
                                } else {
                                    if (currentDoc.getString("fvl:import_id") != null) {
                                        ++manualTodo;
                                        System.out.println("Need to reconcile manually");
                                    } else {
                                        if ("FVWord".equals(currentDoc.getType())) {
                                            ++manualTodo;
                                            System.out.println("Need to reconcile manually - missing import_id");
                                        }
                                    }
                                }

                                System.out.println("####");

                            }
                        }

                        total = total + docs.size();
            }



            System.out.println("Skipped (All Good): " + skipped);
            System.out.println("Todo (Manually): " + manualTodo);
            System.out.println("No Known Action: " + noKnown);
            System.out.println("Total: " + total);

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
	}

    private String convertStatus(String status_id, String dialect_id) throws IOException {

        Document dialect = (Document) session.newRequest("Repository.GetDocument").set("value", dialect_id).execute();

        if (dialect != null) {
            if ("Published".equals(dialect.getState()) && "1".equals(status_id)){
                return "Published";
            }
        }

        switch (status_id) {
            case "0":
                return "New";
            case "1":
                return "Enabled";
            case "2":
                return "Disabled";
        }

        return "New";
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


	public static void main(String[] argv) throws Exception {

		setOptions();

		CommandLine cmd = getCommandLine(argv);

		new ReconcileDeltaStatuses(cmd);
	}
}
