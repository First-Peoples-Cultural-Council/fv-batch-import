package task;

import java.io.IOException;
import java.util.Map;

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
import org.nuxeo.ecm.automation.client.model.RecordSet;

import common.CsvLogWriter;
import mappers.firstvoices.WordMapper;
import reader.CsvReader;
import reader.OracleReader;

public class TestWords {

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

	protected static Map<String, Document> cache = null;

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

	public TestWords(CsvReader reader, CommandLine cmd) {

		// Setup log writer
		String csvFile = cmd.getOptionValue("csv-file");
        csvFile = csvFile.substring(0, csvFile.length()-4) + "_missing.csv";
        logWriter = new CsvLogWriter(csvFile);

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

			if (session == null) {
				System.err.println("Can't open a session on Nuxeo Server");
				return;
			}
		} catch(Exception e) {
			e.printStackTrace();
		}

		// Setup mapper
		mapper = new WordMapper(session);

		try {
			mapper.buildCache();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("Cache loaded (" + cache.size() + " Records)");

		// process rows
        process();
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

	public int wordExists(String id) {
		try {
			RecordSet results = null;

			try {
				results = (RecordSet) session.newRequest("Repository.ResultSetQuery").setHeader(
				        Constants.HEADER_NX_SCHEMAS, "dublincore")
						.set("query", "SELECT COUNT(ecm:uuid) FROM FVWord WHERE fvl:import_id = "+ id +" AND ecm:isProxy = 0 AND ecm:isCheckedInVersion = 0 AND ecm:currentLifeCycleState != 'deleted'")
						.set("currentPageIndex", 0).set("pageSize", 1000).execute();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			if (results != null && results.size() > 0) {
				return (int) results.get(0).get("COUNT(ecm:uuid)");
			}
		}
		catch (RemoteException e) {
			e.printStackTrace();
		}

		return 0;
	}

	public void process() {

		reader.open();

		try {

			while (reader.next()) {
				lines++;
				if (limit > 0 && lines > limit) {
					lines--;
					break;
				}

				try {

					String originalImportID = reader.getString("WORD_ID");

					int occurences = wordExists(originalImportID);

					System.out.println(occurences);

					if (occurences > 1) {
						logWriter.writeLine(reader.getRow(), "WARN: " + occurences + "occurences found");
					} else if (occurences == 0) {
						logWriter.writeLine(reader.getRow(), "ERROR: Missing word!");
					}
				} catch (RemoteException e) {
					e.printStackTrace();
			        System.out.println(e.getClass().getName() + ": " + e.getMessage() + " for " + reader.printRow());
				}
			}
		}
		catch (Exception e) {
			//
		}
		finally {

			if (client != null) {
				client.shutdown();
			}

			if (logWriter != null) {
				logWriter.writeLine(new String[] {"Lines", String.valueOf(lines) }, "ERROR: Missing word!");
                logWriter.close();
			}

			if (reader != null) {
				reader.close();
			}
		}
	}

	public static void main(String[] argv) {

		setOptions();

		CommandLine cmd = getCommandLine(argv);

		reader = new CsvReader(cmd);
		new TestWords(reader, cmd);
	}
}
