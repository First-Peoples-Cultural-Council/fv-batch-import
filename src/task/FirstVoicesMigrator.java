package task;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.FileHandler;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.nuxeo.ecm.automation.client.Constants;
import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.model.Document;
import org.nuxeo.ecm.automation.client.model.Documents;
import org.nuxeo.ecm.automation.client.model.FileBlob;

import common.CsvLogWriter;
import reader.CsvReader;
import reader.AbstractReader;
import reader.OracleReader;

public class FirstVoicesMigrator extends AbstractMigrator {
	protected CommandLine cmd = null;
	protected String dataPath = null;
	protected List<String> wordIds = new LinkedList<String>();
	protected Map<String, Set<String>> existingWordsCache = new HashMap<String, Set<String>>();
	protected Map<String, String> contributorsCache = new HashMap<String, String>();
	protected Map<String, String> imagesCache = new HashMap<String, String>();
	protected Map<String, Map<String, String>> existingPhrasesCache = new HashMap<String, Map<String, String>>();

	public FirstVoicesMigrator(CommandLine commandLine, AbstractReader reader) {
		super(reader);
		cmd = commandLine;
		dataPath = commandLine.getOptionValue("data-path");
		if (cmd.hasOption("limit")) {
			limit = Integer.valueOf(commandLine.getOptionValue("limit"));
		}
		try {
		    FileHandler fh = new FileHandler(new File(new File(dataPath), "import.log").getAbsolutePath());
			log.addHandler(fh);
		} catch (SecurityException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		logWriter = new CsvLogWriter(new File(new File(dataPath), "errors.csv").getAbsolutePath());
	}

	/**
	 * Specify username and password in argument list
	 *
	 * @param argv
	 * @throws SQLException
	 * @throws ClassNotFoundException
	 * @throws ParseException
	 */
	public static void main(String[] argv) throws SQLException, ClassNotFoundException, ParseException {

		AbstractReader reader = null;
		// Handle command line option
		Options options = new Options();
		CsvReader.addOptions(options);
		OracleReader.addOptions(options);
		options.addOption(
				Option.builder().longOpt("nuxeo-url").hasArg().required().desc("Nuxeo URL to connect to").build());
		options.addOption(Option.builder().longOpt("nuxeo-user").hasArg()
				.desc("Nuxeo User to connect with (default: Administrator)").build());
		options.addOption(Option.builder().longOpt("nuxeo-password").hasArg()
				.desc("Nuxeo Password to connect with (default: Administrator)").build());
		options.addOption(
				Option.builder().longOpt("data-path").required().hasArg().desc("Where to get data from").build());
		options.addOption(
				Option.builder().longOpt("limit").hasArg().desc("Limit the number of lines to process").build());
		CommandLineParser parser = new DefaultParser();
		CommandLine commandLine = null;
		try {
			commandLine = parser.parse(options, argv);
		} catch (ParseException e) {
			e.printStackTrace();
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("Migrator", options);
			return;
		}
		if (CsvReader.enabledOptions(commandLine)) {
			reader = new CsvReader(commandLine);
		} else if (OracleReader.enabledOptions(commandLine)) {
			reader = new OracleReader(commandLine);
		}
		// Connect to Nuxeo server
		String nuxeoPassword = commandLine.getOptionValue("nuxeo-password", "Administrator");
		String nuxeoUser = commandLine.getOptionValue("nuxeo-user", "Administrator");
		String nuxeoUrl = commandLine.getOptionValue("nuxeo-url");
		new FirstVoicesMigrator(commandLine, reader).process(nuxeoUrl, nuxeoUser, nuxeoPassword, "/default-domain/workspaces/FVData/");
	}

	@Override
    protected void processRow(Session session) throws IOException {

	    //System.out.println("|--------- ******************** ---------|");

		long start = System.currentTimeMillis();
		String wordValue = reader.getString("WORD_VALUE");
		String wordId = wordValue.replace("/", "");
		// Computing the wordId
		int i = 1;
		while (wordIds.contains(wordId)) {
			wordId = wordValue.replace("/", "") + "." + String.valueOf(++i);
		}
		wordIds.add(wordId);

		String importId = reader.getString("WORD_ID");
		String dominantLanguageDefinition = reader.getString("DOMINANT_LANGUAGE_DEFINITION");
		String dominantLanguageWordValue = reader.getString("DOMINANT_LANGUAGE_WORD_VALUE");

		// Dominant language
		String dominantLanguage = reader.getString("DOMINANT_LANGUAGE");
		// TODO Dominant language can be empty
		if (dominantLanguage.isEmpty() || dominantLanguage.length() < 2 || dominantLanguage == "Nanaimo") {
			dominantLanguage = "English";
		} else {
			dominantLanguage = dominantLanguage.substring(0, 1).toUpperCase() + dominantLanguage.substring(1);
		}

		// Phrase related strings
		String phraseTitle = reader.getString("ABORIGINAL_LANGUAGE_SENTENCE");
		String phraseTranslation = reader.getString("DOMINANT_LANGUAGE_SENTENCE");

		// String PART_OF_SPEECH = reader.getString
		// ("DESCR").toLowerCase();
		int partOfSpeechId = reader.getInt("PART_OF_SPEECH_ID");
		int categoryId = reader.getInt("CATEGORY_ID");
		long method_start = System.currentTimeMillis();
		// TODO Change it to the new returned columns
		Map<String, Document> docs = getOrCreateLanguageDocument(session, reader);
		System.out.println("Get/Create Language: " + Long.valueOf(System.currentTimeMillis() - method_start));
		method_start = System.currentTimeMillis();
		if (checkWordCreated(importId, session, docs.get("Dictionary"))) {
			return;
		}
		System.out.println("CheckWordCreated: " + Long.valueOf(System.currentTimeMillis() - method_start));
		method_start = System.currentTimeMillis();
		wordCount++;
		System.out.println("Processing Word #" + wordCount + "/" + lines + " | Value: " + wordValue + " | New Id: " + wordId + " | Legacy Id: " + importId);
		/**
		 * Prepare Word
		 */
		Document word = new Document(wordId, "FVWord");

		setProperty(word, "dc:title", wordValue);

		setProperty(word, "fv-word:part_of_speech", transformPartOfSpeech(partOfSpeechId));
		setProperty(word, "fv-word:categories", transformWordCategory(categoryId));

		setProperty(word, "fv:cultural_note", reader.getString("CULTURAL_NOTE"));
		setProperty(word, "fv-word:pronunciation", reader.getString("PHONETIC_INFO"));
		setProperty(word, "fvl:assigned_usr_id", reader.getString("ASSIGNED_USR_ID"));
		setProperty(word, "fvl:change_date", reader.getString("CHANGE_DTTM"));

		setProperty(word, "fvl:import_id", importId);
		method_start = System.currentTimeMillis();

		setProperty(word, "fv:source", processWordSources(reader.getString("CONTRIBUTER"), session, docs.get("Contributors")));
		System.out.println("Process source: " + Long.valueOf(System.currentTimeMillis() - method_start));

		setProperty(word, "fv:reference", reader.getString("REFERENCE"));
		setProperty(word, "fv:available_in_childrens_archive", reader.getString("AVAILABLE_IN_CHILDRENS_ARCHIVE"));

		method_start = System.currentTimeMillis();
		setProperty(word, "fv:related_pictures", processWordImages(reader, session, docs.get("Resources"), docs.get("Contributors")));
		System.out.println("ProcessImage: " + Long.valueOf(System.currentTimeMillis() - method_start));
		method_start = System.currentTimeMillis();
		setProperty(word, "fv:related_audio", processWordAudio(reader, session, docs.get("Resources"), docs.get("Contributors")));
		System.out.println("ProcessAudio: " + Long.valueOf(System.currentTimeMillis() - method_start));
		method_start = System.currentTimeMillis();
		setProperty(word, "fv:related_videos", processWordVideo(reader, session, docs.get("Resources"), docs.get("Contributors")));
		System.out.println("ProcessVideo: " + Long.valueOf(System.currentTimeMillis() - method_start));

		setProperty(word, "fv-word:related_phrases", processWordSamplePhrase(phraseTitle, phraseTranslation, dominantLanguage, session, docs.get("Dictionary")));
		System.out.println("ProcessWordSamplePhrase: " + Long.valueOf(System.currentTimeMillis() - method_start));

		// Create literal translation(s)
        if (!dominantLanguageWordValue.isEmpty()) {
            setProperty(word,
                        "fv:literal_translation", "[{\"language\": \"" + dominantLanguage + "\", \"translation\": \"" + dominantLanguageWordValue + "\"}]");
        }

		// Create definition(s)
		if (!dominantLanguageDefinition.isEmpty()) {
            setProperty(word,
                    "fv:definitions", "[{\"language\": \"" + dominantLanguage + "\", \"translation\": \"" + dominantLanguageDefinition + "\"}]");
		}

		/**
		 * Create Word
		 */
		// Need to handle existing doc
		word = (Document) session.newRequest("Document.Create").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
				.setInput(docs.get("Dictionary")).set("type", word.getType()).set("name", word.getId())
				.set("properties", word).execute();
		System.out.println("Work #" + String.valueOf(wordCount) + ": " + word.getId());
		System.out.println("Full word creation time: " + Long.valueOf(System.currentTimeMillis() - start));
		System.out.println("|--------- ******************** ---------|");
	}

	/*
	 * Mapping between WORD_VALUE.part_of_speech_id values and Nuxeo
	 * "parts_of_speech" vocabulary entries
	 */
	public String transformPartOfSpeech(int partOfSpeechId) {

		switch (partOfSpeechId) {
		case 0:
		    return "basic";
		case 1:
			return "verb";
		case 2:
			return "noun";
		case 3:
			return "pronoun";
		case 4:
			return "adjective";
		case 5:
			return "adverb";
		case 6:
			return "preposition";
		case 7:
			return "conjunction";
		case 8:
			return "interjection";
		case 9:
			return "particle";
        case 10:
            return "advanced";
		case 11:
			return "pronoun_personal";
		case 12:
			return "pronoun_reflexive";
		case 13:
			return "pronoun_reciprocal";
		case 14:
			return "pronoun_demonstrative";
		case 15:
			return "pronoun_relative";
		case 16:
			return "particle_postposition";
		case 17:
			return "particle_quantifier";
		case 18:
			return "particle_article_determiner";
		case 19:
			return "particle_tense_aspect";
		case 20:
			return "particle_modal";
		case 21:
			return "particle_conjunction";
		case 22:
			return "particle_auxiliary_verb";
		case 23:
			return "particle_adjective";
		case 24:
			return "particle_adverb";
		case 25:
			return "entity_noun_like_word";
		case 26:
			return "event_activity_verb_like_word";
		case 27:
			return "event_activity_verb_like_word_transitive";
		case 28:
			return "event_activity_verb_like_word_intransitive";
		case 29:
			return "event_activity_verb_like_word_reflexive";
		case 30:
			return "event_activity_verb_like_word_reciprocal";
		case 31:
			return "question_word";
		case 32:
			return "suffix_prefix";
		default:
			System.out.println("Could not retrieve Nuxeo part of speech value");
			return "";
		}
	}

	/*
	 * Mapping between WORD_VALUE.category_id values and Nuxeo "categories"
	 * vocabulary entries
	 */
	public String transformWordCategory(int wordCategoryId) {

		switch (wordCategoryId) {
		case 0:
			System.out.println("No category value assigned");
			return "";
		case 1:
			return "nature_environment";
		case 2:
			return "nature_environment_place_location";
		case 3:
			return "nature_environment_place_names";
		case 4:
			return "nature_environment_weather";
		case 5:
			return "nature_environment_natural_resources";
		case 6:
			return "nature_environment_landscape";
		case 8:
			return "animals";
		case 9:
			return "animals_mammals";
		case 10:
			return "animals_fish";
		case 11:
			return "animals_shellfish";
		case 12:
			return "animals_reptiles";
		case 13:
			return "animals_insects";
		case 14:
			return "animals_birds";
		case 15:
			return "animals_spiders";
		case 18:
			return "plants";
		case 19:
			return "plants_food_plants";
		case 20:
			return "plants_medicine_plants";
		case 21:
			return "plants_trees";
		case 22:
			return "plants_shrubs";
		case 23:
			return "plants_flowers";
		case 25:
			return "body";
		case 26:
			return "body_body_parts";
		case 27:
			return "body_bodily_afflictions_health";
		case 28:
			return "body_senses";
		case 29:
			return "body_speech_language";
		case 31:
			return "spirit";
		case 32:
			return "spirit_spiritual_beliefs";
		case 34:
			return "human_relations";
		case 35:
			return "human_relations_kinship_terms";
		case 37:
			return "human_things_activities";
		case 38:
			return "human_things_activities_dwelling";
		case 39:
			return "human_things_activities_clothing";
		case 40:
			return "food_gathering_making";
		case 41:
			return "human_things_activities_making_cultural_objects";
		case 42:
			return "human_things_activities_employment_work";
		case 43:
			return "human_things_activities_trade";
		case 44:
			return "human_things_activities_government";
		case 46:
			return "events";
		case 47:
			return "events_states";
		case 48:
			return "events_motion";
		case 49:
			return "events_thinking_feeling";
		case 50:
			return "events_activities";
		case 52:
			return "events_time";
		case 55:
			return "plants_grasses";
		case 56:
			return "plants_fungi";
		case 57:
			return "plants_lichens";
		case 58:
			return "plants_ferns";
		case 59:
			return "numbers";
		case 60:
			return "colours";
		case 61:
			return "plants_vegetables";
		case 62:
			return "food";
		case 63:
			return "human_things_activities_transportation";
		case 64:
			return "human_things_activities_fishing_hunting";
		case 65:
			return "human_things_activities_buildings";
		case 66:
			return "human_things_activities_sports";
		case 67:
			return "animals_amphibians";
		case 68:
			return "conjunctions";
		case 69:
			return "human_things_activities_tools_implements";
		case 70:
			return "nature_environment_seasons";
		case 71:
			return "plants";
		case 72:
			return "question_words";
		case 199:
			return "animals_marsupials";
		default:
			System.out.println("Could not retrieve Nuxeo category value");
			return "";
		}
	}

	protected void cacheDictionary(Session session, Document dictionary) throws IOException {
		Set<String> list = new HashSet<String>();
		Integer pageSize = 1000;
		Integer page = 0;
		while (true) {
			// Cache dictionary
			Documents existingWords = (Documents) session.newRequest("Repository.Query").setHeader(
			        Constants.HEADER_NX_SCHEMAS, "fvlegacy")
					.set("query", "SELECT * FROM FVWord WHERE ecm:parentId=? AND ecm:currentLifeCycleState != 'deleted'")
					.set("queryParams", dictionary.getId()).set("currentPageIndex", page).set("pageSize", pageSize).set("sortBy", "fvl:import_id").execute();
			for (int i = 0; i < existingWords.size(); i++) {
				list.add(existingWords.get(i).getString("fvl:import_id"));
			}
			if (existingWords.size() < pageSize) {
				break;
			}
			page++;
		}
		page = 0;
		existingWordsCache.put(dictionary.getId(), list);
		Map<String, String> listPhrases = new HashMap<String, String>();
		while (true) {
			Documents existingPhrases = (Documents) session.newRequest("Repository.Query").setHeader(
			        Constants.HEADER_NX_SCHEMAS, "dublincore")
					.set("query", "SELECT * FROM FVPhrase WHERE ecm:parentId=? AND ecm:currentLifeCycleState != 'deleted'")
					.set("queryParams", dictionary.getId()).set("currentPageIndex", page).set("pageSize", pageSize).set("sortBy", "fvl:import_id").execute();
			for (int i = 0; i < existingPhrases.size(); i++) {
				listPhrases.put(existingPhrases.get(i).getString("dc:title"), existingPhrases.get(i).getId());
			}
			if (existingPhrases.size() < pageSize) {
				break;
			}
			page++;
		}
		existingPhrasesCache.put(dictionary.getId(), listPhrases);

	}
	public boolean checkWordCreated(String wordId, Session session, Document dictionary) throws IOException {
		if (!existingWordsCache.containsKey(dictionary.getId())) {
			cacheDictionary(session, dictionary);
		}
		return existingWordsCache.get(dictionary.getId()).contains(wordId);
	}

    /*
     * Process the "CONTRIBUTERS" string from the migrated word data. Split on
     * commas, then check each segment to see if a matching FVContributor
     * already exists within Nuxeo, or if it needs to be created. Return a
     * comma-separated list of ID references to FVContributor documents.
     */
    public String processWordSources(String migratedSources, Session session, Document contributorsFolder) {
            String sourcesList = "";

            if (migratedSources != null && !migratedSources.isEmpty()) {
                    // System.out.println("CONTRIBUTER: [" + migratedSources + "]");
                    String[] sourcesArray = migratedSources.split("/\\s*");

                    // Check if Contributor already exists within Nuxeo. If it does,
                    // reference it. If not, create a new one.
                    for (int i = 0; i < sourcesArray.length; i++) {
                            String source = sourcesArray[i].trim();
                            if (contributorsCache.containsKey(source)) {
                                    sourcesList += contributorsCache.get(source) + ",";
                                    continue;
                            }
                            try {

                                    List<String> params = new LinkedList<String>();
                                    params.add(source);

                                    Documents existingContributorDocs = (Documents) session.newRequest("Repository.Query")
                                                    .set("query",
                                                                    "SELECT * FROM FVContributor WHERE dc:title=? AND ecm:currentLifeCycleState != 'deleted'")
                                                    .set("queryParams", params).execute();
                                    Document contributor = null;
                                    // Contributor does not exist. Create a new one
                                    if (existingContributorDocs.isEmpty()) {
                                            System.out.println("No existing FVContributor found. Creating: " + source);
                                            Document newContributorDocument = new Document(source.replace("/", "_"), "FVContributor");
                                            newContributorDocument.set("dc:title", source);
                                            contributor = (Document) session.newRequest("Document.Create")
                                                            .setHeader(Constants.HEADER_NX_SCHEMAS, "*").setInput(contributorsFolder)
                                                            .set("type", newContributorDocument.getType())
                                                            .set("name", newContributorDocument.getId()).set("properties", newContributorDocument)
                                                            .execute();
                                    }
                                    // Contributor already exists. Retrieve its ID and add it to
                                    // the list of Sources for the current Word
                                    else {
                                            contributor = existingContributorDocs.get(0);
                                    }
                                    sourcesList += contributor.getId() + ",";
                                    contributorsCache.put(source, contributor.getId());
                            } catch (IOException e) {
                                    e.printStackTrace();
                            }
                    }
                    if (sourcesList.endsWith(",")) {
                            sourcesList = sourcesList.substring(0, sourcesList.length() - 1);
                    }
            }
            // System.out.println("sourcesList: [" + sourcesList + "]");
            return sourcesList;
    }

	protected String processWordAudio(AbstractReader reader, Session session, Document resourcesFolder, Document contributorsFolder)
			throws IOException {
		return processWordBinary(reader, "AUDIO", session, resourcesFolder, contributorsFolder, "FVAudio");
	}

	protected String processWordVideo(AbstractReader reader, Session session, Document resourcesFolder, Document contributorsFolder)
			throws IOException {
		return processWordBinary(reader, "VIDEO", session, resourcesFolder, contributorsFolder, "FVVideo");
	}

	protected String processWordImages(AbstractReader reader, Session session, Document resourcesFolder, Document contributorsFolder)
			throws IOException {
		return processWordBinary(reader, "IMG", session, resourcesFolder, contributorsFolder, "FVPicture");
	}

	protected String processWordBinary(AbstractReader reader, String prefix, Session session, Document resourcesFolder,
	    Document contributorsFolder, String profile) throws IOException {
		String binaryFilename = reader.getString(prefix + "_FILENAME");
		String binaryId = reader.getString(prefix + "_ID");
		String binaryStatus = reader.getString(prefix + "_STATUS");

		String binaryDesc = reader.getString(prefix + "_DESCR");
		String binaryIsShared = reader.getString(prefix + "_SHARED");
		String binaryRecorder = reader.getString(prefix + "_RECORDER");
		String binaryUserId = reader.getString(prefix + "_USER_ID");
		String binaryContributor = reader.getString(prefix + "_CONTRIBUTOR");

		if (binaryFilename == null || binaryId == null || binaryStatus == null || binaryFilename.isEmpty()
				|| binaryId.isEmpty()) {
			// Skip if no file
			return null;
		}
		String id = binaryFilename.replace("/", "_");
		String cacheId = resourcesFolder.getId() + id;
		if (imagesCache.containsKey(cacheId)) {
			return imagesCache.get(cacheId);
		}
		binaryFilename = dataPath + binaryFilename;
		File file = new File(binaryFilename);
		if (!file.exists()) {
			log.warning("Cant import non existing binary " + binaryFilename + " for record " + reader.printRow());
			return null;
		}
		String binaryName = file.getName();
		String uid = null;
		List<String> params = new LinkedList<String>();
		params.add(binaryName);
		params.add(resourcesFolder.getId());
		Documents images = (Documents) session.newRequest("Repository.Query")
				.set("query",
						"SELECT * FROM " + profile
								+ " WHERE dc:title=? AND ecm:currentLifeCycleState != 'deleted' AND ecm:parentId=?")
				.set("queryParams", params).execute();
		if (!images.isEmpty()) {
			for (int i = 0; i < images.size(); i++) {
				// Cannot use ends with on path
				if (images.get(i).getPath().endsWith(id)) {
					uid = images.get(i).getId();
					imagesCache.put(cacheId, uid);
					return uid;
				}
			}
		}

		Document binaryDoc = new Document(getId(id), profile);
		binaryDoc.set("dc:title", binaryName);
		binaryDoc.set("dc:description", binaryDesc);
		binaryDoc.set("fvl:import_id", binaryId);
		binaryDoc.set("fvl:status_id", binaryStatus);
		binaryDoc.set("fvl:assigned_usr_id", binaryUserId);
		binaryDoc.set("fvm:shared", binaryIsShared);
		binaryDoc.set("fvm:source", processWordSources(binaryContributor, session, contributorsFolder));
		binaryDoc.set("fvm:recorder", processWordSources(binaryRecorder, session, contributorsFolder));

		binaryDoc = (Document) session.newRequest("Document.Create").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
				.setInput(resourcesFolder).set("type", binaryDoc.getType()).set("name", binaryDoc.getId())
				.set("properties", binaryDoc).execute();
		if (!file.exists()) {
			System.out.println("Cant import non existing binary will create the document: " + binaryFilename);
		} else {
			FileBlob fb = new FileBlob(file);
			session.newRequest("Blob.Attach").setHeader(Constants.HEADER_NX_VOIDOP, "true").setInput(fb)
					.set("document", binaryDoc).execute();
		}
		uid = binaryDoc.getId();
		imagesCache.put(cacheId, uid);
		return uid;
	}

    public String processWordSamplePhrase(String phraseTitle, String phraseTranslation, String dominantLanguage, Session session, Document dictionaryFolder) {
    	String uid = null;
    	//System.out.println("phraseTitle:" + phraseTitle);
    	//System.out.println("phraseTranslation:" + phraseTranslation);

    	if(phraseTitle.isEmpty() || phraseTitle == null || phraseTranslation.isEmpty() || phraseTranslation == null) {
    		System.out.println("No sample Phrase defined.");
    		return null;
    	}

        try {
        	// If phrase is already in the cache, return its uid
            if (!existingPhrasesCache.containsKey(dictionaryFolder.getId())) {
            	cacheDictionary(session, dictionaryFolder);
            }
            if (existingPhrasesCache.get(dictionaryFolder.getId()).containsKey(phraseTitle)) {
            	System.out.println("Existing Phrase found in cache: " + phraseTitle);
                return existingPhrasesCache.get(dictionaryFolder.getId()).get(phraseTitle);
            }

            // Check if phrase exists within Nuxeo
    		List<String> params = new LinkedList<String>();
    		params.add(phraseTitle);
    		params.add(dictionaryFolder.getId());
    		Documents existingPhraseDocs = (Documents) session.newRequest("Repository.Query")
    				.set("query", "SELECT * FROM FVPhrase WHERE dc:title=? AND ecm:currentLifeCycleState != 'deleted' AND ecm:parentId=?")
    				.set("queryParams", params).execute();

        	Document phrase = null;

        	// Phrase does not exist. Create a new one
            if (existingPhraseDocs.isEmpty()) {
            	System.out.println("No existing Phrase found in Nuxeo. Creating: " + phraseTitle);
            	Document newPhraseDocument = new Document(getId(phraseTitle), "FVPhrase");
            	newPhraseDocument.set("dc:title", phraseTitle);

                // Create literal translation(s)
                if (!phraseTranslation.isEmpty()) {
                    newPhraseDocument.set("fv:literal_translation",
                            "[{\"language\": \"" + dominantLanguage + "\", \"translation\": \"" + phraseTranslation + "\"}]");
                }

            	phrase = (Document) session.newRequest("Document.Create")
            						.setHeader(Constants.HEADER_NX_SCHEMAS, "*").setInput(dictionaryFolder)
                                    .set("type", newPhraseDocument.getType())
                                    .set("name", newPhraseDocument.getId()).set("properties", newPhraseDocument)
                                    .execute();
            	existingPhrasesCache.get(dictionaryFolder.getId()).put(phraseTitle, phrase.getId());
            }
            // Phrase already exists
            else {
            	System.out.println("Existing FVPhrase found in Nuxeo: " + phraseTitle);
                phrase = existingPhraseDocs.get(0);
            }
            existingPhrasesCache.get(dictionaryFolder.getId()).put(phraseTitle, phrase.getId());
            uid = phrase.getId();
	    } catch (IOException e) {
	            e.printStackTrace();
	    }
    	return uid;
    }

}