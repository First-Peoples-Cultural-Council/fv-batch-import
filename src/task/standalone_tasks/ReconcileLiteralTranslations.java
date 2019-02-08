package task.standalone_tasks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.codehaus.jackson.map.ObjectMapper;
import org.nuxeo.ecm.automation.client.Constants;
import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.jaxrs.impl.HttpAutomationClient;
import org.nuxeo.ecm.automation.client.model.Document;
import org.nuxeo.ecm.automation.client.model.Documents;
import org.nuxeo.ecm.automation.client.model.PropertyList;
import org.nuxeo.ecm.automation.client.model.PropertyMap;

import mappers.CsvMapper;
import reader.CsvReader;
import reader.OracleReader;



public class ReconcileLiteralTranslations {

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

	public ReconcileLiteralTranslations(CommandLine cmd) throws Exception {

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

            for (int j = 0; j < 50; j++) {
                Documents docs = (Documents) session.newRequest("Repository.Query").setHeader(
                        Constants.HEADER_NX_SCHEMAS, "*")
                        .set("query", "SELECT * FROM FVWord WHERE ecm:path STARTSWITH '/FV/Workspaces' AND fv:literal_translation/*/translation IS NOT NULL AND dc:modified NOT BETWEEN DATE '2017-06-25' AND DATE '2017-07-02'")
                        .set("currentPageIndex", 0).set("pageSize", 1000).set("sortBy","dc:modified").set("sortOrder", "desc").execute();

                        System.out.println("#### =>" + j);

                        if (docs.size() > 0) {
                            for (i = 0; i < docs.size(); i++) {
                                Document currentDoc = docs.get(i);

                                System.out.println(currentDoc.getId());

                                //SELECT COUNT(ecm:uuid) FROM FVPhrase WHERE ecm:path STARTSWITH '/FV/Workspaces' AND fv:literal_translation/*/translation IS NOT NULL
                                //AND dc:modified NOT BETWEEN DATE '2017-06-25' AND DATE '2017-06-27'

                                //Literal Translation

                                String existingValueLT = null;
                                List<Map> existingValueComplexListLT = new ArrayList<Map>();
                                ArrayList<String> JSONValuesLT = new ArrayList<String>();

                                PropertyList list = currentDoc.getProperties().getList("fv:literal_translation");
                                for (int k = 0; k < list.size(); ++k) {
                                    PropertyMap mapLT = list.getMap(k);

                                    JSONValuesLT.add("{\"language\":\"" + mapLT.getString("language") + "\",\"translation\":\"" + mapLT.getString("translation").replaceAll("\"", "'").replaceAll("\\\\", "\\\\\\\\") + "\"}");
                                }

                                existingValueLT = JSONValuesLT.toString();
                                existingValueComplexListLT = Arrays.asList(JSONmapper.readValue(existingValueLT, Map[].class));

                                //Definition

                                String existingValueD = null;
                                List<Map> existingValueComplexListD = new ArrayList<Map>();
                                ArrayList<String> JSONValuesD = new ArrayList<String>();

                                PropertyList listD = currentDoc.getProperties().getList("fv:definitions");
                                for (int k = 0; k < listD.size(); ++k) {
                                    PropertyMap mapD = listD.getMap(k);

                                    JSONValuesD.add("{\"language\":\"" + mapD.getString("language") + "\",\"translation\":\"" + mapD.getString("translation").replaceAll("\"", "'").replaceAll("\\\\", "\\\\\\\\") + "\"}");
                                }

                                existingValueD = JSONValuesD.toString();
                                existingValueComplexListD = Arrays.asList(JSONmapper.readValue(existingValueD, Map[].class));

                                if (existingValueLT.equals(existingValueD)) {
                                    currentDoc.set("fv:literal_translation", new ArrayList<String>().toString());
                                } else {
                                    currentDoc.set("fv:definitions", existingValueLT);
                                    currentDoc.set("fv:literal_translation", existingValueD);
                                }

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

                                //System.out.println("####");

                            }
                        }

                        total = total + docs.size();
            }



            //System.out.println("Skipped (All Good): " + skipped);
            //System.out.println("Todo (Manually): " + manualTodo);
            //System.out.println("No Known Action: " + noKnown);
            //System.out.println("Total: " + total);

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

		new ReconcileLiteralTranslations(cmd);
	}
}
