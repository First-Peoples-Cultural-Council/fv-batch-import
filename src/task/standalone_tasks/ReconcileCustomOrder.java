package task.standalone_tasks;

import java.io.IOException;
import java.util.ArrayList;

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
import mappers.firstvoices.WordMapper;

public class ReconcileCustomOrder {
	static Options options = new Options();

	Session session = null;
	HttpAutomationClient client = null;
	WordMapper mapper;

	String rootPath = "";
	String domain = "FV";
	String dialect;

	protected int limit = 10;
	protected int lines = 0;
	protected int wordCount = 0;
	protected String section = "Workspaces";
	protected ArrayList<String> dialects = new ArrayList<String>();

	/**
	 * Setup the options
	 */
	protected static void setOptions() {
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
        options.addOption(
        		Option.builder().longOpt("section").hasArg().desc("Which section to work in (Workspaces or sections)").build());
        options.addOption(
        		Option.builder().longOpt("dialect").hasArg().desc("Dialict UUID" ).build());
	}

	/**
	 * Fetch documents of type
	 * type must be "FVWord" or "FVPhrase"
	 *
	 * @param type
	 * @return Documents|null
	 */
	private Documents fetchDocuments( String type )
	{
		Documents docs = null;
		try {
			String query = "SELECT * FROM " + type
			+ " WHERE (fv:custom_order is null OR fv:custom_order = '')"
			+ " AND ecm:path STARTSWITH '" + rootPath + "'"
            + " AND ecm:isProxy = 0"
            + " AND ecm:isCheckedInVersion = 0";

			if( dialect != null ) {
				query += " AND fva:dialect = '" + dialect + "'";
			}

			query += " ORDER BY dc:title";

			docs = (Documents) session.newRequest("Repository.Query")
					.setHeader(Constants.HEADER_NX_SCHEMAS, "*")
					.set("currentPageIndex",  0)
					.set("pageSize", limit)
					.set("query", query)
					.execute();
		} catch(IOException e) {
			e.printStackTrace();
		}

		return docs;
	}

	/**
	 * Process
	 *
	 * Note Boolean cont = true continually will create an infinite loop. There must be a call at some point that sets cont as false
	 *
	 * @param cont Continue to process and look for more words|phrase in other sections
	 */
	protected void processOrders( Boolean cont )
	{
		// if we do not have the session, no point in moving forward
		if (session == null) {
			System.err.println("Can't open a session on Nuxeo Server");
			return;
		}

		Documents words = fetchDocuments( "FVWord" );
		Documents phrases = null;

		// lets make use of our limit and if we have room, add on some phrases
		/*if( words.size() < limit )
		{
			// we have room to process some phrases
			// set a new limit
			limit = limit - words.size();
			if( limit > 0 ) {
				// good to go
				phrases = fetchDocuments( "FVPhrase" );
			}
		}*/

		Boolean canProcess = !(( words == null && phrases == null ) || ( words.size() == 0 && phrases.size() == 0 ));

		// 1 other situation we may encounter is we have completed all of a section/workspace
		//**** HOWEVER, THERE IS NOT MECHANISM IN PLACE TO QUERY THE SEPARATE SECTIONS|WORKSPACES YET ******/
		if( !canProcess && cont ) {
			if( section == "Workspaces" ) {
				section = "sections";
			} else {
				section = "Workspaces";
			}

			rootPath = "/" + domain + "/" + section;

			// recall process with the above changes
			processOrders( false );
			return;
		}

		/*
		 * At this point, we should have one of the following scenarios
		 *  1) A full limit list of words
		 *  2) A partial limit list of words and a partial limit list of phrases
		 *  3) NULL list of words, a full limit list of phrases
		 *  4) NULL list of words, a partial limit list of phrases
		 *  5) NULL list of words and a NULL list of phrases
		 */
		if(!canProcess) {
			// If we have hit here, we have processed all words and phrases in both sections and Workspaces
			// we have nothing left to process
			System.out.println( "CONGRATULATIONS! Your done." );
			return;
		} else {
			if( words != null && words.size() > 0 ) {
				// process the words
				processWords( words );
			}
			if( phrases != null && phrases.size() > 0 ) {
				// process the phrases
				processPhrases( phrases );
			}
		}
	}

	/**
	 * Process Words
	 *
	 * @param words
	 */
	private void processWords( Documents words )
	{
		int idx = 0;
		for( idx = 0; idx < words.size(); idx++ ) {
			try {
			    System.out.println( "Word: " + words.get( idx ) );

                session.newRequest( "Document.ComputeNativeOrderForAsset" )
                .setInput( words.get( idx ) )
                .execute();

				//if (!dialects.contains(words.get( idx ).getProperties().getString("fva:dialect"))){
				//    dialects.add(words.get( idx ).getProperties().getString("fva:dialect"));
				//}

				//System.out.println( "Word: " + words.get( idx ) );
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	/**
	 * Process phrases
	 *
	 * @param phrases
	 */
	private void processPhrases( Documents phrases )
	{
		int idx = 0;
		for( idx = 0; idx < phrases.size(); idx++ ) {
			try {
				session.newRequest( "Document.ComputeNativeOrderForAsset" )
				.setInput( phrases.get( idx ) )
				.execute();
				System.out.println( "Phrase: " + phrases.get( idx ) );

                if (!dialects.contains(phrases.get( idx ).getProperties().getString("fva:dialect"))){
                    dialects.add(phrases.get( idx ).getProperties().getString("fva:dialect"));
                }

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public ReconcileCustomOrder(CommandLine cmd) {
		// attempt to setup the session
		setupSession( cmd );
		processOrders( true );

		//System.out.println("Completed Dialects:");
		//System.out.println(dialects.toString());

        /*try {
            RecordSet records = (RecordSet) session.newRequest("Repository.ResultSetPageProvider")
                    .setHeader(Constants.HEADER_NX_SCHEMAS, "*")
                    .set("pageSize", 1)
                    .set("query", "SELECT COUNT(ecm:uuid) FROM FVWord WHERE ecm:isProxy = 0 AND fv:custom_order is null AND ecm:isCheckedInVersion = 0")
                    .execute();
            System.out.println(records.get(0));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            session.close();
        }*/

        if (session != null) {
            session.close();
        }

	}

	/**
	 * Setup the session
	 *
	 * @param cmd
	 */
	private void setupSession(CommandLine cmd) {
		// if a limit has been specified, lets use it
        if (cmd.hasOption("limit")) {
            limit = Integer.valueOf(cmd.getOptionValue("limit"));
        }

        // unless its too low or too high
        limit = Math.abs( limit ) <= 10000 ? Math.abs( limit ) : 10000;

        // check for a new section
        if(cmd.hasOption("section")) {
        	section = cmd.getOptionValue("section");
        }

        if( "Section".equals( section ) || "Sections".equals( section ) || "section".equals( section ) ) {
        	section = "section";
        } else if( "workspace".equals( section) || "workspaces".equals( section ) ) {
        	section = "Workspace";
        }

        if( cmd.hasOption( "dialect" ) ) {
        	dialect = cmd.getOptionValue("dialect");
        }

        String nuxeoPassword = cmd.getOptionValue("nuxeo-password", "Administrator");
        String nuxeoUser = cmd.getOptionValue("nuxeo-user", "Administrator");
        String nuxeoUrl = cmd.getOptionValue("nuxeo-url");
        domain = cmd.getOptionValue("domain");

        rootPath = "/" + domain + "/" + section;

		if (!nuxeoUrl.endsWith("/site/automation")) {
			nuxeoUrl += "/site/automation";
		}

        // Establish connection to Nuxeo
		client = new HttpAutomationClient(nuxeoUrl);

		try {
			session = client.getSession(nuxeoUser, nuxeoPassword);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 *
	 * @param argv
	 * @return
	 */
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

	/**
	 *
	 * @param argv
	 */
	public static void main(String[] argv) {
		setOptions();
		CommandLine cmd = getCommandLine(argv);
		new ReconcileCustomOrder(cmd);
	}
}
