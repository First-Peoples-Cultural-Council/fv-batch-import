package task.standalone_tasks;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
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
import org.apache.commons.lang.StringUtils;
import org.nuxeo.ecm.automation.client.Constants;
import org.nuxeo.ecm.automation.client.RemoteException;
import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.jaxrs.impl.HttpAutomationClient;
import org.nuxeo.ecm.automation.client.model.Document;
import org.nuxeo.ecm.automation.client.model.Documents;
import org.nuxeo.ecm.automation.client.model.FileBlob;
import org.nuxeo.ecm.automation.client.model.PropertyList;
import org.nuxeo.ecm.automation.client.model.PropertyMap;

import common.CsvLogWriter;
import common.SkipRowException;
import mappers.CsvMapper;
import mappers.firstvoices.AudioMapper;
import mappers.firstvoices.BinaryMapper;
import mappers.firstvoices.PhraseMigratorMapper;
import mappers.firstvoices.PictureMapper;
import mappers.firstvoices.VideoMapper;
import mappers.propertyreaders.PropertyReader;
import reader.CsvReader;
import reader.OracleReader;

import org.codehaus.jackson.map.ObjectMapper;



public class ReconcileDeltaPhrases {

	protected CsvLogWriter logWriter = null;
	protected CsvLogWriter logWriterToDo = null;

	static CsvReader reader = null;
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

        options.addOption(
                Option.builder().longOpt("data-path").required().hasArg().desc("Where to get data from").build());
	}

	protected void attachBlob(Document container, String path) throws IOException {

        // Remove leading slash on filename
        if(path.startsWith("/")) {
            path = path.substring(1);
        }

        String binaryFileFullPath = BinaryMapper.getDataPath() + path;
        binaryFileFullPath.replace("/", "_");

        File file = new File(binaryFileFullPath);

        if (path.equals("") || !file.exists()) {

        } else {
            // Set Properties that are derived from other properties
            container.set("dc:title", file.getName());

            // TODO: Set status

            FileBlob fb = new FileBlob(file);

            session.newRequest("Blob.Attach")
                .setHeader(Constants.HEADER_NX_VOIDOP, "true")
                .setInput(fb)
                .set("document", container).execute();

        }

	}

	private String escapeString(String s) {
	    return s.replaceAll("\\\\", "\\\\\\\\").replaceAll("'", "\\\\'").replaceAll("\n", "\\\\n");
	}

	protected Document getParentInput(String title, String dialectId) throws IOException {

      Documents dialectRoot = (Documents) session.newRequest("Repository.Query").setHeader(
      Constants.HEADER_NX_SCHEMAS, "*")
      .set("query", "SELECT * FROM Document WHERE dc:title = '" + escapeString(title) + "' AND fva:dialect = '" + dialectId + "'")
      .set("currentPageIndex", 0).set("pageSize", 1).execute();

	  return dialectRoot.get(0);
	}

	protected String createMedia(String importId, String dialectId, String type) throws IOException, Exception {

	    if (importId == null || importId.isEmpty()) {
	        throw new Exception("Import ID for media is empty. Cannot create.");
	    }

        Documents existingMedia = (Documents) session.newRequest("Repository.Query").setHeader(
        Constants.HEADER_NX_SCHEMAS, "*")
        .set("query", "SELECT * FROM " + type +" WHERE fvl:import_id = " + importId + " AND fva:dialect LIKE '" + dialectId + "'")
        .set("currentPageIndex", 0).set("pageSize", 10).execute();

        // Picture exists in dialect
        if (existingMedia.size() > 0) {
            return existingMedia.get(0).getId();
        }
        // Create new picture
        else {
            BinaryMapper mediaMapper = null;

            int sourceColNum = 0;
            int recorderColNum = 0;
            int blobFileColNum = 0;

            switch (type) {
                case "FVPicture":
                    mediaMapper = new PictureMapper();
                    sourceColNum = 19;
                    recorderColNum = 17;
                    blobFileColNum = 12;
                break;

                case "FVAudio":
                    mediaMapper = new AudioMapper();
                    sourceColNum = 35;
                    recorderColNum = 33;
                    blobFileColNum = 28;
                break;

                case "FVVideo":
                    mediaMapper = new VideoMapper();
                    sourceColNum = 27;
                    recorderColNum = 25;
                    blobFileColNum = 20;
                break;
            }

            mediaMapper.setFakeCreation(true);

            Document newPic = new Document(importId, type);

            for (PropertyReader propertyReader : mediaMapper.getPropertyReaders()) {
                if (propertyReader.getKey() != null) {

                    if (propertyReader.getKey() == "fvm:shared") {
                        newPic.set(propertyReader.getKey(), (propertyReader.getValue(reader).equals("1")) ? true : false);
                    } else {
                        newPic.set(propertyReader.getKey(), propertyReader.getValue(reader));
                    }
                }
            }

            // Attach source and recorder
            String mediaSource = reader.getString(sourceColNum);

            if (mediaSource.contains("http:") || mediaSource.contains("/")) {
                System.out.println("Maunally handle source!");
            } else {
                newPic.set("fvm:source", createSource(reader.getString(sourceColNum), dialectId));
            }

            String mediaRecorder = reader.getString(recorderColNum);

            if (mediaRecorder.contains("http:") || mediaRecorder.contains("/")) {
                System.out.println("Maunally handle recorder!");
            } else {
                newPic.set("fvm:recorder", createSource(reader.getString(recorderColNum), dialectId));
            }

            // Create Picture
            newPic = (Document) session.newRequest("Document.Create").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
                    .setInput(getParentInput("Resources", dialectId)).set("type", type).set("name", importId)
                    .set("properties", newPic).execute();

            // Attach Blob
            attachBlob(newPic, reader.getString(blobFileColNum));

            // Update new Pic (to reflect title change based on blob)
            session.newRequest("Document.Update").setHeader(
            Constants.HEADER_NX_SCHEMAS, "*")
            .setInput(newPic)
            .set("properties", newPic).execute();

            return newPic.getId();
        }
	}

	public void handleMedia(String type, String field, int csvCol, Document existingDoc, Session session, CsvReader reader) throws Exception {
        String mediaID = reader.getString(csvCol);

        String imgIdList = StringUtils.join(existingDoc.getProperties().getList(field).list(), "','");

        if (imgIdList != null && imgIdList.length() > 0) {
            imgIdList = "'" + imgIdList + "'";
            Documents existingMedia = (Documents) session.newRequest("Repository.Query").setHeader(
            Constants.HEADER_NX_SCHEMAS, "*")
            .set("query", "SELECT * FROM " + type + " WHERE ecm:uuid IN (" + imgIdList + ")")
            .set("currentPageIndex", 0).set("pageSize", 1000).execute();

            //System.out.println(mediaID + "=" + existingMedia.get(0).getProperties().getString("fvl:import_id"));
            if (mediaID != null && !mediaID.isEmpty() && !existingMedia.get(0).getProperties().getString("fvl:import_id").equals(mediaID)) {
                //System.out.println(type + "-> PREV: " + existingMedia.get(0).getProperties().getString("fvl:import_id") + " ## NEXT: " + mediaID);

                String pic = createMedia(mediaID, existingDoc.getProperties().getString("fva:dialect"), type);
                existingDoc.set(field, pic);
            }
        } else {
            if (mediaID != null && !mediaID.isEmpty()) {
                    //System.out.println(type + "-> PREV: null ## NEXT: " + mediaID);

                    // Reconcile
                    String pic = createMedia(mediaID, existingDoc.getProperties().getString("fva:dialect"), type);
                    existingDoc.set(field, pic);
            }
        }

	}

	public ReconcileDeltaPhrases(CsvReader reader, CommandLine cmd) throws Exception {

	    try {
            System.setOut(new PrintStream(new FileOutputStream("/Users/dyona/Dev/Workspaces/ImportDemo/csv/deltas/reconcile_delta_log.txt")));
        } catch (FileNotFoundException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

		// Setup log writer
		String csvFile = cmd.getOptionValue("csv-file");
        logWriter = new CsvLogWriter(csvFile.substring(0, csvFile.length()-4) + "_errors.csv");

        // Setup ToDo writer
        logWriterToDo = new CsvLogWriter(csvFile.substring(0, csvFile.length()-4) + "_todo.csv");

        if (cmd.hasOption("limit")) {
            limit = Integer.valueOf(cmd.getOptionValue("limit"));
        }

        String nuxeoPassword = cmd.getOptionValue("nuxeo-password", "Administrator");
        String nuxeoUser = cmd.getOptionValue("nuxeo-user", "Administrator");
        String nuxeoUrl = cmd.getOptionValue("nuxeo-url");
        String domain = cmd.getOptionValue("domain");

        reader.open();

        rootPath = "/" + domain + "/Workspaces/";

        BinaryMapper.setDataPath(cmd.getOptionValue("data-path"));

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

            int skipped = 0;

            // Print the name out
            while (reader.next()) {
                lines++;
                if (limit > 0 && lines > limit) {
                    lines--;
                    break;
                }
                try {

                    // DO STUFF

                    ObjectMapper JSONmapper = new ObjectMapper();

                    Documents existingDocs = null;

                    mapper = new PhraseMigratorMapper();
                    mapper.setFakeCreation(true);

                    String importId = reader.getString(0);

                    // Skip words that were already inserted as "new" entries after freeze.
                    /*if (Integer.parseInt(importId) > 155618) {
                        ++skipped;
                        continue;
                    }*/

                    System.out.println("## Row " + lines + " import_id = " + importId);

                    boolean statusChanged = false;

                    try {

                        existingDocs = (Documents) session.newRequest("Repository.Query").setHeader(
                        Constants.HEADER_NX_SCHEMAS, "*")
                        .set("query", "SELECT * FROM FVPhrase WHERE ecm:path STARTSWITH '/FV/Workspaces' AND fvl:import_id = " + importId)
                        .set("currentPageIndex", 0).set("pageSize", 5).set("sortBy","dc:modified").set("sortOrder", "desc").execute();

                        if (existingDocs.size() == 1) {

                            Document existingDoc = existingDocs.get(0);
                            String dialectId = existingDoc.getProperties().getString("fva:dialect");

                            //System.out.println("##### fvl:change_date -> PREV:" + reader.getString(13) + " ## NEXT: " + existingDoc.getProperties().getString("fvl:change_date"));

                            // Go over the simple properties
                            for (PropertyReader propertyReader : mapper.getPropertyReaders()) {

                                //System.out.println(propertyReader.getKey());

                                // No need to update change date.
                                if (propertyReader.getKey() == "fvl:change_date") {
                                    continue;
                                }

                                String existingValue = null;
                                List<Map> existingValueComplexList = new ArrayList<Map>();
                                List<Map> readerValueComplexList = new ArrayList<Map>();

                                String readerValue = propertyReader.getJsonValue(reader);

                                if (propertyReader.getKey() == "fv:literal_translation" || propertyReader.getKey() == "fv:definitions") {

                                    ArrayList<String> JSONValues = new ArrayList<String>();
                                    PropertyList list = existingDoc.getProperties().getList(propertyReader.getKey());
                                    for (int i = 0; i < list.size(); ++i) {
                                        PropertyMap map = list.getMap(i);

                                        JSONValues.add("{\"language\":\"" + map.getString("language") + "\",\"translation\":\"" + map.getString("translation") + "\"}");
                                    }

                                    existingValue = JSONValues.toString();
                                    existingValueComplexList = Arrays.asList(JSONmapper.readValue(existingValue, Map[].class));
                                }
                                else if (propertyReader.getKey() == "fv:cultural_note") {
                                    PropertyList list = existingDoc.getProperties().getList(propertyReader.getKey());
                                    if (list.size() > 0) {
                                        existingValue = "\"" + StringUtils.join(list.list(), ",") + "\"";

                                    } else {
                                        existingValue = null;
                                    }
                                }
                                else {
                                    existingValue = JSONmapper.writeValueAsString(existingDoc.getProperties().get(propertyReader.getKey()));
                                }

                                // Normalization
                                if (propertyReader.getKey() == "fv:available_in_childrens_archive") {
                                    readerValue = ("\"0\"".equals(readerValue)) ? "\"false\"" : readerValue;
                                    readerValue = ("\"1\"".equals(readerValue)) ? "\"true\"" : readerValue;
                                }

                                if (propertyReader.getKey() == "fv:literal_translation" || propertyReader.getKey() == "fv:definitions") {
                                    //readerValue = JSONmapper.readValue(readerValue, List);
                                    readerValueComplexList = Arrays.asList(JSONmapper.readValue(readerValue, Map[].class));

                                    if (readerValueComplexList.size() > 0) {
                                        if (existingValueComplexList.size() == 0 || !readerValueComplexList.get(0).get("translation").equals(existingValueComplexList.get(0).get("translation"))) {
                                            // System.out.println(propertyReader.getKey() + "-> PREV: " + ((existingValueComplexList.size() == 0) ? "null" : existingValueComplexList.get(0).get("translation")) + " ## NEXT: " + readerValueComplexList.get(0).get("translation"));
                                            existingDoc.set(propertyReader.getKey(), readerValue);
                                        }
                                    }
                                }
                                else {
                                    readerValue = ("\"\"".equals(readerValue)) ? null : readerValue;

                                    if ((existingValue != null && readerValue == null) || (readerValue != null && !readerValue.equals(existingValue))) {

                                        if (propertyReader.getKey().equals("fvl:status_id")) {
                                            statusChanged = true;
                                        }

                                        //System.out.println(propertyReader.getKey() + "-> PREV: " + existingValue + " ## NEXT: " + readerValue);

                                        if (propertyReader.getKey() == "fv:available_in_childrens_archive") {
                                            existingDoc.set(propertyReader.getKey(), (readerValue != null && readerValue.equals("\"true\"")) ? true : false);
                                        } else {
                                            existingDoc.set(propertyReader.getKey(), propertyReader.getValue(reader));
                                        }

                                    }
                                }
                            }

                            // Handle pictures
                            handleMedia("FVPicture", "fv:related_pictures", 13, existingDoc, session, reader);
                            handleMedia("FVVideo", "fv:related_videos", 21, existingDoc, session, reader);
                            handleMedia("FVAudio", "fv:related_audio", 29, existingDoc, session, reader);

                            // Handle source
                            String source = reader.getString(1);

                            if (source != null && !source.isEmpty()) {
                                handleSource("FVContributor", "fv:source", 1, existingDoc, session, reader);
                            }

                            // Handle phrase book
                            String phraseBook = reader.getString(10);

                            if (phraseBook != null && !phraseBook.isEmpty()) {
                                handleCategory("FVCategory", "fv-phrase:phrase_books", 10, existingDoc, session, reader);
                            }

                            // Generate list of manual status changes
                            if (statusChanged) {
                                //System.out.println("fvl:status_id -> PREV: " + existingDoc.getState() + " ## NEXT: " + convertStatus(reader.getString(40)));

                                if (logWriterToDo != null) {
                                    // Save row to another CSV
                                    logWriterToDo.writeLine(reader.getRow(), "fvl:status_id -> PREV: " + existingDoc.getState() + " ## NEXT: " + convertStatus(reader.getString(40)));
                                }
                            }

                            // Save Document

                            if (existingDoc.getDirties().isEmpty()) {
                                System.out.println("## No diff");
                            } else {
                                System.out.println(existingDoc.getDirties().toString());


                                Document saveDoc = (Document) session.newRequest("Document.Update").setHeader(
                                        Constants.HEADER_NX_SCHEMAS, "*")
                                        .setInput(existingDoc)
                                        .set("properties", existingDoc).execute();

                                System.out.println("## Write Complete");

                            }
                        } else if (existingDocs.size() > 1) {
                            logWriter.writeLine(reader.getRow(), "Multiples found!!!!!!!!!!!");
                        } else {
                            logWriter.writeLine(reader.getRow(), "Nothing found!!!!!!!!!!!");
                        }

                        System.out.println(" ");

                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                    // END OF DO STUFF

                } catch (SkipRowException | RemoteException e) {
                    if (logWriter != null) {
                        // Save row to another CSV
                        e.printStackTrace();
                        logWriter.writeLine(reader.getRow(), e.getMessage());
                    }
                }
            }

            System.out.println("******");
            System.out.println("Skipped " + skipped + " Lines.");
            System.out.println("******");

        } finally {
            if (reader != null) {
                reader.close();
            }

            if (logWriter != null) {
                // Save row to another CSV
                logWriter.close();
            }
        }
	}

	private String createSource(String sourceName, String dialectId) throws IOException {
        Documents existingSource = (Documents) session.newRequest("Repository.Query").setHeader(
        Constants.HEADER_NX_SCHEMAS, "*")
        .set("query", "SELECT * FROM FVContributor WHERE dc:title = '" + escapeString(sourceName) + "' AND fva:dialect LIKE '" + dialectId + "'")
        .set("currentPageIndex", 0).set("pageSize", 10).execute();

        // Source exists in dialect
        if (existingSource.size() > 0) {
            return existingSource.get(0).getId();
        }
        // Create new source
        else {
            Document newSourceLocal = new Document(sourceName, "FVContributor");
            newSourceLocal.set("dc:title", sourceName);
            Document newSource = (Document) session.newRequest("Document.Create").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
                    .setInput(getParentInput("Contributors", dialectId)).set("type", "FVContributor").set("name", sourceName)
                    .set("properties", newSourceLocal).execute();
            return newSource.getId();
        }
    }

    public void handleSource(String type, String field, int csvCol, Document existingDoc, Session session, CsvReader reader) throws IOException {
        String sourceName = reader.getString(csvCol);

        String sourceIdList = StringUtils.join(existingDoc.getProperties().getList(field).list(), "','");

        // Manually handle split or http: sources
        if (sourceName.contains("http:") || sourceName.contains("/")) {
            System.out.println("Maunally handle source!");
        } else {
            if (sourceIdList != null && sourceIdList.length() > 0) {
                sourceIdList = "'" + sourceIdList + "'";
                Documents existingSource = (Documents) session.newRequest("Repository.Query").setHeader(
                Constants.HEADER_NX_SCHEMAS, "*")
                .set("query", "SELECT * FROM " + type + " WHERE ecm:uuid IN (" + sourceIdList + ")")
                .set("currentPageIndex", 0).set("pageSize", 1000).execute();

                //System.out.println(mediaID + "=" + existingMedia.get(0).getProperties().getString("fvl:import_id"));
                if (!existingSource.get(0).getProperties().getString("dc:title").equals(sourceName)) {
                    System.out.println(type + "-> PREV: " + existingSource.get(0).getProperties().getString("dc:title") + " ## NEXT: " + sourceName);

                    String newSource = createSource(sourceName, existingDoc.getProperties().getString("fva:dialect"));
                    existingDoc.set(field, newSource);
                }
            } else {
                if (sourceName != null && !sourceName.isEmpty()) {
                        System.out.println(type + "-> PREV: null ## NEXT: " + sourceName);

                        // Reconcile
                        String newSource = createSource(sourceName, existingDoc.getProperties().getString("fva:dialect"));
                        existingDoc.set(field, newSource);
                }
            }
        }
    }

    private String createCategory(String categoryId, String categoryName, String dialectId) throws IOException {
        Documents existingCategory = (Documents) session.newRequest("Repository.Query").setHeader(
        Constants.HEADER_NX_SCHEMAS, "*")
        .set("query", "SELECT * FROM FVCategory WHERE fvl:import_id = " + categoryId + " AND fva:dialect LIKE '" + dialectId + "'")
        .set("currentPageIndex", 0).set("pageSize", 10).execute();

        // Source exists in dialect
        if (existingCategory.size() > 0) {
            return existingCategory.get(0).getId();
        }
        // Create new category
        else {
            Document newCategoryLocal = new Document(categoryId, "FVCategory");
            newCategoryLocal.set("fvl:import_id", categoryId);
            newCategoryLocal.set("dc:title", categoryName);
            Document newSource = (Document) session.newRequest("Document.Create").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
                    .setInput(getParentInput("Phrase Books", dialectId)).set("type", "FVCategory").set("name", categoryId)
                    .set("properties", newCategoryLocal).execute();
            return newSource.getId();
        }
    }

    // Note: This handles related phrases only
    private String createPhrase(String aboriginalPhrase, String englishPhrase, String dialectId) throws IOException {
        Documents existingPhrase = (Documents) session.newRequest("Repository.Query").setHeader(
        Constants.HEADER_NX_SCHEMAS, "*")
        .set("query", "SELECT * FROM FVPhrase WHERE dc:title LIKE '" + escapeString(aboriginalPhrase) + "' AND fv:definitions/*/translation LIKE '" + escapeString(englishPhrase) + "' AND fv:literal_translation/*/translation LIKE '" + escapeString(englishPhrase) + "' AND fva:dialect LIKE '" + dialectId + "'")
        .set("currentPageIndex", 0).set("pageSize", 10).execute();

        // Phrase exists in dialect
        if (existingPhrase.size() > 0) {
                return existingPhrase.get(0).getId();
        }
        // Create new phrase
        else {
            PhraseMigratorMapper phraseMapper = new PhraseMigratorMapper();
            phraseMapper.setFakeCreation(true);

            Document newPhrase = new Document(aboriginalPhrase, "FVPhrase");

            newPhrase.set("fv:literal_translation", "[{\"language\": \"english\", \"translation\": \"" + reader.getString(11) + "\"}]");
            newPhrase.set("dc:title", aboriginalPhrase);

            // Create New Phrase
            newPhrase = (Document) session.newRequest("Document.Create").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
                    .setInput(getParentInput("Dictionary", dialectId)).set("type", "FVPhrase").set("name", aboriginalPhrase)
                    .set("properties", newPhrase).execute();

            return newPhrase.getId();
        }
    }

    public void handleCategory(String type, String field, int csvCol, Document existingDoc, Session session, CsvReader reader) throws IOException {
        String categoryId = reader.getString(csvCol);

        String categoryIdList = StringUtils.join(existingDoc.getProperties().getList(field).list(), "','");

        if (categoryIdList != null && categoryIdList.length() > 0) {
            categoryIdList = "'" + categoryIdList + "'";
            Documents existingCategory = (Documents) session.newRequest("Repository.Query").setHeader(
            Constants.HEADER_NX_SCHEMAS, "*")
            .set("query", "SELECT * FROM " + type + " WHERE ecm:uuid IN (" + categoryIdList + ")")
            .set("currentPageIndex", 0).set("pageSize", 1000).execute();

            if (!existingCategory.get(0).getProperties().getString("fvl:import_id").equals(categoryId)) {
                System.out.println(type + "-> PREV: " + existingCategory.get(0).getProperties().getString("fvl:import_id") + " ## NEXT: " + categoryId);

                String newCategory = createCategory(categoryId, reader.getString(csvCol + 1), existingDoc.getProperties().getString("fva:dialect"));
                existingDoc.set(field, newCategory);
            }
        } else {
            if (categoryId != null && !categoryId.isEmpty()) {
                    System.out.println(type + "-> PREV: null ## NEXT: " + categoryId);

                    // Reconcile
                    String newCategory = createCategory(categoryId, reader.getString(csvCol + 1), existingDoc.getProperties().getString("fva:dialect"));
                    existingDoc.set(field, newCategory);
            }
        }

    }

    public void handleRelatedPhrase(String type, String field, int csvCol, int csvCol2, Document existingDoc, Session session, CsvReader reader) throws IOException {
        String phraseAboriginal = reader.getString(csvCol);
        String phraseEnglish = reader.getString(csvCol2);

        String newPhrase = null;

        String relatedPhrasesIdList = StringUtils.join(existingDoc.getProperties().getList(field).list(), "','");

        if (relatedPhrasesIdList != null && relatedPhrasesIdList.length() > 0) {
            relatedPhrasesIdList = "'" + relatedPhrasesIdList + "'";
            Documents existingPhrase = (Documents) session.newRequest("Repository.Query").setHeader(
            Constants.HEADER_NX_SCHEMAS, "*")
            .set("query", "SELECT * FROM " + type + " WHERE ecm:uuid IN (" + relatedPhrasesIdList + ")")
            .set("currentPageIndex", 0).set("pageSize", 1000).execute();

            // Aboriginal Phrase Changed
            if (!existingPhrase.get(0).getProperties().getString("dc:title").equals(phraseAboriginal)) {
                System.out.println(type + "-> PREV: " + existingPhrase.get(0).getProperties().getString("dc:title") + " ## NEXT: " + phraseAboriginal);
                //System.out.println(type + "-> PREV: " + existingPhrase.get(0).getProperties().getList("fv:definitions").getMap(0).getString("translation") + " ## NEXT: " + phraseEnglish);

                newPhrase = createPhrase(phraseAboriginal, phraseEnglish, existingDoc.getProperties().getString("fva:dialect"));

                //String newCategory = createCategory(categoryId, reader.getString(csvCol + 1), existingDoc.getProperties().getString("fva:dialect"));
                //existingDoc.set(field, newCategory);
            }

            //String existingTranslation = (existingPhrase.get(0).getProperties().getList("fv:definitions").size() > 0) ? existingPhrase.get(0).getProperties().getList("fv:definitions").getMap(0).getString("translation") : "";
            String existingTranslationLiteral = (existingPhrase.get(0).getProperties().getList("fv:literal_translation").size() > 0) ? existingPhrase.get(0).getProperties().getList("fv:literal_translation").getMap(0).getString("translation") : "";


            // Definition changed (Note: not relevant)
            /*if (!existingTranslation.equals(phraseEnglish) && !existingTranslation.isEmpty()) {
                System.out.println("fv-word:related_phrases, definition -> PREV: " + existingTranslation + " ## NEXT: " + phraseEnglish);
                // Reconcile
                newPhrase = createPhrase(phraseAboriginal, phraseEnglish, existingDoc.getProperties().getString("fva:dialect"));
            }*/

            // Literal translation changed (but not aboriginal phrase)
            if (!existingTranslationLiteral.equals(phraseEnglish) && !existingTranslationLiteral.isEmpty() && phraseAboriginal.equals(existingPhrase.get(0).getProperties().getString("dc:title"))) {
                System.out.println("fv-word:related_phrases, only literal -> PREV: " + existingTranslationLiteral + " ## NEXT: " + phraseEnglish);
                //newPhrase = createPhrase(phraseAboriginal, phraseEnglish, existingDoc.getProperties().getString("fva:dialect"));
                // Update phrase

                existingPhrase.get(0).set("fv:literal_translation", "[{\"language\": \"english\", \"translation\": \"" + phraseEnglish + "\"}]");

                Document saveUpdatedPhraseDoc = (Document) session.newRequest("Document.Update").setHeader(
                Constants.HEADER_NX_SCHEMAS, "*")
                .setInput(existingPhrase.get(0))
                .set("properties", existingPhrase.get(0)).execute();
            }

        } else {
            // No related phrase found
            if (phraseAboriginal != null && !phraseAboriginal.isEmpty()) {
                    System.out.println(type + "-> PREV: null ## NEXT: " + phraseAboriginal + " + " + phraseEnglish);

                    // Reconcile
                    newPhrase = createPhrase(phraseAboriginal, phraseEnglish, existingDoc.getProperties().getString("fva:dialect"));
            }
        }

        // Related phrase removed
        if (phraseAboriginal.isEmpty() && existingDoc.getProperties().getList("fv-word:related_phrases").size() != 0) {
            System.out.println(type + "-> PREV: " + existingDoc.getProperties().getList("fv-word:related_phrases").getString(0) + " ## NEXT: null");
            existingDoc.set("fv-word:related_phrases", "");
        }

        if (newPhrase != null) {
            existingDoc.set(field, newPhrase);
        }

    }

    private String convertStatus(String status_id) {
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

		reader = new CsvReader(cmd);
		new ReconcileDeltaPhrases(reader, cmd);
	}
}
