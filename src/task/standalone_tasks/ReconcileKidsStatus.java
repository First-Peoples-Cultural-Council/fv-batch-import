package task.standalone_tasks;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.codehaus.jackson.map.ObjectMapper;
import org.nuxeo.ecm.automation.client.Constants;
import org.nuxeo.ecm.automation.client.RemoteException;
import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.jaxrs.impl.HttpAutomationClient;
import org.nuxeo.ecm.automation.client.model.Documents;

import common.SkipRowException;
import mappers.CsvMapper;
import reader.CsvReader;
import reader.OracleReader;



public class ReconcileKidsStatus {

	static Options options = new Options();

	Session session = null;
	HttpAutomationClient client = null;
	protected CsvMapper mapper = null;

	static CsvReader reader = null;

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

	public ReconcileKidsStatus(CsvReader reader, CommandLine cmd) throws Exception {

	    ObjectMapper JSONmapper = new ObjectMapper();

	    /*try {
            System.setOut(new PrintStream(new FileOutputStream("/Users/dyona/Dev/Workspaces/ImportDemo/csv/deltas/reconcile_literal_translations_log.txt")));
        } catch (FileNotFoundException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }*/

		// Setup log writer
		/*String csvFile = cmd.getOptionValue("csv-file");
        logWriter = new CsvLogWriter(csvFile.substring(0, csvFile.length()-4) + "_errors.csv");

        // Setup ToDo writer
        logWriterToDo = new CsvLogWriter(csvFile.substring(0, csvFile.length()-4) + "_todo.csv");*/

        if (cmd.hasOption("limit")) {
            limit = Integer.valueOf(cmd.getOptionValue("limit"));
        }

        //reader.open();

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

            String numbers = "11,16,56278,56279";
            List<Integer> list = Stream.of(numbers.split(","))
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());

            int count = 0;

            String ids = null;

            for (int i = 0; i < 33; ++i) {

                int start = (1000 * i);
                int end = (1000 * i) + 1000;

                if (i == 32) {
                    ids = list.subList(start, 32170).toString().replace("[", "(").replace("]", ")");
                } else {
                    ids = list.subList(start, end).toString().replace("[", "(").replace("]", ")");;
                }

                try {
                Documents docs = (Documents) session.newRequest("Repository.Query").setHeader(
                        Constants.HEADER_NX_SCHEMAS, "*")
                        .set("query", "SELECT * FROM FVPhrase WHERE ecm:path STARTSWITH '/FV/Workspaces' AND fv:available_in_childrens_archive <> 1 AND fvl:import_id IN" + ids)
                        .set("currentPageIndex", 0).set("pageSize", 1000).execute();

                if (docs.size() > 0) {

                    System.out.println("Docs found for iteration " + i + ", size: " + docs.size());

                    /*for (Document currentDoc : docs) {
                        currentDoc.set("fv:available_in_childrens_archive", true);

                        // Save
                        Document saveDoc = (Document) session.newRequest("Document.Update").setHeader(
                                Constants.HEADER_NX_SCHEMAS, "*")
                                .setInput(currentDoc)
                                .set("properties", currentDoc).execute();

                        if ("Published".equals(currentDoc.getState()) || "Republish".equals(currentDoc.getState())) {
                            session.newRequest("FVPublish")
                            .setInput(currentDoc)
                            .execute();
                        }
                    }*/
                    }
                }
                 catch (Exception e) {
                    e.printStackTrace();
                }
            }

            /*while (reader.next()) {
                lines++;
                if (limit > 0 && lines > limit) {
                    lines--;
                    break;
                }

                try {
                    System.out.println(reader.getString(0));

                    Documents docs = (Documents) session.newRequest("Repository.Query").setHeader(
                            Constants.HEADER_NX_SCHEMAS, "*")
                            .set("query", "SELECT * FROM FVWord WHERE ecm:path STARTSWITH '/FV/Workspaces' AND fv:available_in_childrens_archive <> 1")
                            .set("currentPageIndex", 0).set("pageSize", 1000).execute();

                    if (docs.size() > 0) {
                        if (docs.size() > 1) {
                            throw new Exception("Two records found!");
                        }

                        Document currentDoc = docs.get(0);
                        System.out.println(currentDoc.getId());

                        currentDoc.set("fv:available_in_childrens_archive", true);

                        // Save
                        Document saveDoc = (Document) session.newRequest("Document.Update").setHeader(
                                Constants.HEADER_NX_SCHEMAS, "*")
                                .setInput(currentDoc)
                                .set("properties", currentDoc).execute();

                        if ("Published".equals(currentDoc.getState()) || "Republish".equals(currentDoc.getState())) {
                            session.newRequest("FVPublish")
                            .setInput(currentDoc)
                            .execute();
                        }
                    }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
            }*/
        } catch (SkipRowException | RemoteException e) {
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


	public static void main(String[] argv) throws Exception {

		setOptions();

		CommandLine cmd = getCommandLine(argv);

	    reader = new CsvReader(cmd);
	    new ReconcileKidsStatus(reader, cmd);
	}
}
