package task.standalone_tasks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.nuxeo.ecm.automation.client.Constants;
import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.jaxrs.impl.HttpAutomationClient;
import org.nuxeo.ecm.automation.client.model.Documents;
import org.nuxeo.ecm.automation.client.model.RecordSet;

import common.CsvLogWriter;
import mappers.firstvoices.WordMapper;
import reader.CsvReader;
import reader.OracleReader;

public class LanguageStatusReport {

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

	public LanguageStatusReport(CsvReader reader, CommandLine cmd) {

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

		ArrayList<String> missingDialects = new ArrayList<String>();

		try {
			session = client.getSession(nuxeoUser, nuxeoPassword);



				    List<String> dialects = Arrays.asList("SENĆOŦEN","Secwepemc","nłeʔkepmxcin","HUL'Q'UMI'NUM'","Gitsenimx̱","Nisga'a","Nuu-chah-nulth (Barkley)","diiɁdiitidq","Ktunaxa","Kwak̓wala","hən̓q̓əmin̓əm̓","Halq'eméylem","Hlg̱aagilda X̱aayda Kil","nsyilxcən","c̕išaaʔatḥ","Tse'Khene (McLeod Lake)","Haisla","Dene","Northern St̓át̓imcets","she shashishalhem","Líl̓wat","Ehattesaht Nuchatlaht","Kwadacha Tsek'ene","Klahoose","Sliammon","Wet’suwet’en","Secwepemctsin (Eastern Dialect)","’Uik̓ala","Dakelh / Southern Carrier","Tsilhqot'in (Xeni Gwet'in)","Nuxalk","Nadleh Whut'en","Yekooche","Nak’azdli Dakelh","Splatsin (Eastern dialect)","Cree (Saulteau First Nation)","Tsaaʔ Dane - Beaver People","Syilx","Kyuquot-Checleseht","Stz’uminus");

				    Iterator<String> dialectsIterator = dialects.iterator();

						while (dialectsIterator.hasNext()) {
				        String dialectTitle = dialectsIterator.next();

				        try {

		                    Documents docs = (Documents) session.newRequest("Repository.Query")
		                            .set("query", "SELECT * FROM FVDialect WHERE ecm:path STARTSWITH '/FV/Workspaces/Data/' AND dc:title LIKE '" + dialectTitle.replace("'", "\\'") + "'")
		                            .set("currentPageIndex", 0).set("pageSize", 10).execute();

		                    if (docs.size() == 1) {
	                            RecordSet recordsInWorkspaces = (RecordSet) session.newRequest("Repository.ResultSetPageProvider")
                                .setHeader(Constants.HEADER_NX_SCHEMAS, "*")
                                .set("pageSize", 1)
                                .set("query", "SELECT COUNT(ecm:uuid) FROM FVWord WHERE ecm:isProxy = 0 AND ecm:currentLifeCycleState <> 'deleted' AND ecm:isCheckedInVersion = 0 AND fva:dialect = '"+ docs.get(0).getId() +"' AND fv:related_audio/* IS NOT NULL")
                                .execute();

		                        System.out.println(dialectTitle + "," + recordsInWorkspaces.get(0));
		                    }
		                    if (docs.size() > 1) {
		                        throw new Exception("Expecting only one result for this dialect");
		                    } else if (docs.size() == 0) {
		                        missingDialects.add(dialectTitle);
		                        //throw new Exception("No results for " + dialectTitle);
		                    }
    			        } catch (IOException e) {
    			            // TODO Auto-generated catch block
    			            e.printStackTrace();
    			        } finally {
    			            session.close();
    			        }
				    }

			if (session == null) {
				System.err.println("Can't open a session on Nuxeo Server");
				return;
			}
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
            System.out.println("Missing dialects:");
            System.out.println(missingDialects.toString());
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
		new LanguageStatusReport(reader, cmd);
	}
}
