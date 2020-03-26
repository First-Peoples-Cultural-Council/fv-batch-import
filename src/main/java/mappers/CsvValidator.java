package mappers;

import com.opencsv.CSVReader;
import org.nuxeo.client.NuxeoClient;
import org.nuxeo.client.cache.ResultCacheInMemory;
import org.nuxeo.client.objects.Document;
import org.nuxeo.client.objects.Documents;
import org.nuxeo.client.objects.Repository;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CsvValidator{

    protected HashMap<String, ArrayList<String>> invalid = new HashMap<>();
    protected List<String> t_or_f = new ArrayList<>();
    protected InputStreamReader fileReader;
    protected CSVReader csvReader;
    protected NuxeoClient client;
    protected static final ArrayList<String> TFVALUES = new ArrayList<String>() {{
        add("1");
        add("0");
        add("true");
        add("false");
    }};
    protected Documents categories;
    protected Documents shared_categories;

    protected Documents words;
    private static final String[] POS_VALUES = new String[]{"basic", "verb", "noun", "pronoun", "adjective", "adverb", "preposition", "conjunction",
            "interjection", "particle", "advanced", "pronoun_personal", "pronoun_reflexive", "pronoun_reciprocal",
            "pronoun_demonstrative", "pronoun_relative", "particle_postposition", "particle_quantifier",
            "particle_article_determiner", "particle_tense_aspect", "particle_modal", "particle_conjunction",
            "particle_auxiliary_verb", "particle_adjective", "particle_adverb", "entity_noun_like_word",
            "event_activity_verb_like_word", "event_activity_verb_like_word_transitive", "event_activity_verb_like_word_intransitive",
            "event_activity_verb_like_word_reflexive", "event_activity_verb_like_word_reciprocal", "question_word", "suffix_prefix",
            "affirmation", "transitive_verb", "intransitive_verb", "connective", "connective_irrealis", "demonstrative", "interrogative",
            "modifier_noun", "modifier_verb", "negation", "number", "plural_marker", "question_marker", "question_word", "tense_aspect"};
    private static final Set<String> POS = new HashSet<>(Arrays.asList(POS_VALUES));


    public CsvValidator(String nuxeoUrl, String nuxeoUser, String nuxeoPassword, String csvFile, String dialectID, String languagePath) throws IOException{
        if (csvFile != null && !csvFile.isEmpty()) {
            fileReader = new InputStreamReader(new FileInputStream(csvFile), "UTF-8");
            csvReader = new CSVReader(fileReader, ',', '"', '\0');
        }

        // Connect to Nuxeo instance
        client = new NuxeoClient.Builder()
                .cache(new ResultCacheInMemory())
                .url(nuxeoUrl)
                .authentication(nuxeoUser, nuxeoPassword)
                .connect();

        t_or_f.add("INCLUDE_IN_GAMES");
        t_or_f.add("CHILD_FRIENDLY");
        t_or_f.add("_SHARED_WITH_OTHER_DIALECTS");
        t_or_f.add("_CHILD_FOCUSED");
        
        // If path to language is given as a parameter then get the ID and set dialectID to that ID
        if ( languagePath != null ) {
            Repository repository = client.repository();
            Document folder = repository.fetchDocumentByPath("/FV/Workspaces/Data/" + languagePath);
            dialectID = folder.getUid();
        }
        
        getData(dialectID);
    }

    public HashMap<String, ArrayList<String>> validate(String path, int limit) throws IOException{

        String header[] = csvReader.readNext();
        String fileTypes[] = {"AUDIO", "VIDEO", "IMG"};
        String nextLine[];
        Map<String, Integer> files_read = new HashMap<>();
        int lineNumber = 0;
        String headerTemp;

        while((nextLine = csvReader.readNext()) != null){
            files_read.clear();
            int wordCount=0;
            lineNumber++;
            for (String word: nextLine) {

                headerTemp= header[wordCount];

                for (String type: fileTypes) {
                    if(header[wordCount].startsWith(type))
                        headerTemp = header[wordCount].substring(type.length());
                }

// Disable if duplicate words need to be added as well as WordMapper line 123 and below
                if(headerTemp.equals("WORD"))
                    checkWordDuplicate(word, lineNumber);

                if(headerTemp.equals("CATEGORIES"))
                    checkCategoryExists(word, lineNumber);

                if(t_or_f.contains(headerTemp) && !word.equals("")){
                    if(!TFVALUES.contains(word)) {
                        addToInvalid("Invalid Types", "Only true/false allowed, but found " + word + " in Column " + header[wordCount] +", " +"Line " + lineNumber);
                    }
                }

                if(header[wordCount].endsWith("_FILENAME") && !word.equals("")){
                    checkFileExists(path+word, header[wordCount], lineNumber, word);
                    if(header[wordCount].matches(".*\\d+.*")){
                        Pattern r = Pattern.compile("(.*)(\\d+)(.*)");
                        Matcher m = r.matcher(header[wordCount]);
                        if(m.matches()) {
                            String title = m.group(1);
                            int num = Integer.parseInt(m.group(2));

//                      Check that the number of previously read files matches the number in the heading
//                      Disable if Team is leaving out AUDIO_FILENAME intentionally
                            if(files_read.get(title) != num-1) {
                                addToInvalid("File number Mismatch", header[wordCount] +" is given without other number files: line " +lineNumber +", " +word);
                            } else {
                                files_read.put(title, num);
                            }
                        }
                    }
                    else
                        files_read.put(header[wordCount].substring(0,header[wordCount].indexOf("FILENAME")), 1);
                }
//                Disable part of speech look-up since it's hardcoded
//                if(headerTemp.equals("PART_OF_SPEECH"))
//                    checkPartsOfSpeech(word, lineNumber);

                if(headerTemp.equals("USERNAME"))
                    checkUserExists(word, lineNumber);

                wordCount++;

            }

            // If limit is reached, skip the next iteration
            if (limit != 0  && lineNumber == limit) {
                break;
            }
        }
        return invalid;
    }

    private void checkFileExists(String path, String header, int line, String word ){
        File temp = new File(path.trim());
        File temp_with_mp3 = new File(path.replace("wav", "mp3"));

        if(!temp.exists()){
            if (temp_with_mp3.exists()){
                addToInvalid("Audio Issues", "Wrong extension for file " + word + " in Column " + header +", " +"Line " + (line+1));
            } else {
                addToInvalid("Audio Issues", "Missing file " + word + " in Column " + header +", " +"Line " + (line+1));
            }
        }
    }

    private void checkPartsOfSpeech(String pos, int line)throws IOException{
        String temppos = pos.toLowerCase();
        if(!temppos.equals(pos)) {
            addToInvalid("Parts of Speech", "Parts of speech must be written in lowercase: line " + (line + 1) + ", "+pos);
        }
        temppos = temppos.replaceAll("/ -(),", "_");
        if(!POS.contains(temppos)) {
            addToInvalid("Parts of Speech", "Only existing parts of speech are supported: line " + (line + 1) + ", "+pos);
        }
    }

    private void checkUserExists(String user, int line) throws IOException{
        if(user.equals("")) {
            addToInvalid("Missing required fields", "Username must be filled in: line " + (line +1) +", " +user);
        }


        //Document doc  = (Document) session.newRequest("Document.Fetch").set("value", "/FV/workspaces/Data/TestLanguageFamily/TestLanguage/TestDialect_1").execute();
        //doc = (Document) session.newRequest("Document.GetUsersAndGroups").input(doc).set("permission", "edit").set("variable name", "usrs").execute();

    }

    private void getData(String dialect) throws IOException {

        // Get the directory id for the dialect categories
        Documents dialect_categories_directory = client.operation("Repository.Query").param("query",  "SELECT * FROM FVCategories " +
                "WHERE fva:dialect = '" + dialect + "' " +
                "AND ecm:path STARTSWITH '/FV/Workspaces/' " +
                "AND dc:title = 'Categories' " +
                "AND ecm:isTrashed = 0 " +
                "AND ecm:isVersion = 0")
                .execute();
        List<Document> documentsList = dialect_categories_directory.streamEntries().collect(Collectors.toList());
        Document categories_directory = documentsList.get(0);
        String categories_directory_id = categories_directory.getId();

        words = client.operation("Repository.Query").param("query", "SELECT * FROM Document WHERE ecm:primaryType = 'FVWord' AND fva:dialect = '" + dialect +"'")
                .execute();

        categories = client.operation("Repository.Query").param("query", "SELECT * FROM FVCategory WHERE ecm:ancestorId = '" + categories_directory_id + "'AND ecm:isTrashed = 0 AND ecm:isVersion = 0")
                .execute();

        shared_categories = client.operation("Repository.Query").param("query", "SELECT * FROM FVCategory WHERE fva:dialect IS NULL AND ecm:isTrashed = 0 AND ecm:isVersion = 0")
                .execute();



    }

    private void checkCategoryExists(String w, int line){
        Boolean match = false;
        String temp[];

        if(w.contains(",")) {
            temp = w.split(",");
            for (String word: temp) {

                // Fail on space between categories
                if (word.startsWith(" ")) {
                    word = word.trim();
                }

                if(!word.isEmpty()){
                    for (Document d:categories.getDocuments()) {
                        String title = d.getTitle();
                        if(word.contentEquals(title)) {
                            match = true;
                            break;
                        }
                    }

                    for (Document d:shared_categories.getDocuments()) {
                        String title = d.getTitle();
                        if(word.contentEquals(title)) {
                            match = true;
                            break;
                        }
                    }

                    if(!match) {
                        addToInvalid("Missing Categories", "Only existing categories allowed: " + word + " in line " + (line + 1));
                    }
                }
            }
        }
        else if(!w.isEmpty()){
            for (Document d:categories.getDocuments()) {
                String title = d.getTitle();
                if(w.contentEquals(title)) {
                    match = true;
                    break;
                }
            }

            for (Document d:shared_categories.getDocuments()) {
                String title = d.getTitle();
                if(w.contentEquals(title)) {
                    match = true;
                    break;
                }
            }

            if(!match) {
                addToInvalid("Missing Categories", "Only existing categories allowed. Check for spaces between categories: " + w + " in line " + (line + 1));
            }
        }
    }

    private void checkWordDuplicate(String word, int line){
        for (Document d:words.getDocuments()) {
            if(d.getTitle().equals(word)){
                addToInvalid("Duplicates", "Cannot upload duplicate words: line " + (line + 1) +", " +d.getTitle());
            }
        }
    }

    public void close()throws IOException{
        csvReader.close();
        client.disconnect();
    }

    private void addToInvalid(String key, String errorValue) {
        if (!invalid.containsKey(key)) {
            invalid.put(key, new ArrayList<>());
        }

        ArrayList<String> currentValuesInKey = invalid.get(key);
        currentValuesInKey.add(errorValue);
        invalid.put(key, currentValuesInKey);
    }

    public void printInvalidEntries() {
        for (Map.Entry<String, ArrayList<String>> entry : invalid.entrySet()) {
            System.out.println(entry.getKey());
            for (String error : entry.getValue()) {
                System.out.println(" -> " + error);
            }
        }
    }

}
