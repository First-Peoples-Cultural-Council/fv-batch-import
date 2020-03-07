package mappers;

import com.opencsv.CSVReader;
import org.nuxeo.client.NuxeoClient;
import org.nuxeo.client.cache.ResultCacheInMemory;
import org.nuxeo.client.objects.Document;
import org.nuxeo.client.objects.Documents;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CsvValidator{

    protected List<String> invalid = new ArrayList<>();
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


    public CsvValidator(String nuxeoUrl, String nuxeoUser, String nuxeoPassword, String csvFile, String dialectID) throws IOException{

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

        getData(dialectID);
    }

    public List<String> validate(String path, int limit) throws IOException{

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
                    if(!TFVALUES.contains(word))
                        invalid.add(header[wordCount] + " can only have true or false values: line " +lineNumber +", " +word);
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
                            if(files_read.get(title) != num-1)
                                invalid.add(header[wordCount] +" is given without other number files: line " +lineNumber +", " +word);
                            else
                                files_read.put(title, num);
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
        File temp = new File(path);
        File temp_with_mp3 = new File(path.replace("wav", "mp3"));

        if(!temp.exists()){
            if (temp_with_mp3.exists()){
                invalid.add("Wrong extension for file: " + header +", " +"line " +line +", " +word);
            } else {
                invalid.add("Unable to find file: " + header +", " +"line " +line +", " +word);
            }
        }
    }

    private void checkPartsOfSpeech(String pos, int line)throws IOException{
        String temppos = pos.toLowerCase();
        if(!temppos.equals(pos))
            invalid.add("Parts of speech must be written in lowercase: line " + line + ", "+pos);
        temppos = temppos.replaceAll("/ -(),", "_");
        if(!POS.contains(temppos))
            invalid.add("Only existing parts of speech are supported: line " + line + ", "+pos);

    }

    private void checkUserExists(String user, int line) throws IOException{
        if(user.equals(""))
            invalid.add("Username must be filled in: line " +line +", " +user);

        //Document doc  = (Document) session.newRequest("Document.Fetch").set("value", "/FV/workspaces/Data/TestLanguageFamily/TestLanguage/TestDialect_1").execute();
        //doc = (Document) session.newRequest("Document.GetUsersAndGroups").input(doc).set("permission", "edit").set("variable name", "usrs").execute();

    }

    private void getData(String dialect) throws IOException {

        words = client.operation("Repository.Query").param("query", "SELECT * FROM Document WHERE ecm:primaryType = 'FVWord' AND fva:dialect = '" + dialect +"'")
                .execute();

        categories = client.operation("Repository.Query").param("query", "SELECT * FROM Document WHERE ecm:primaryType = 'FVCategory' AND fva:dialect = '" + dialect+"'")
                .execute();

        shared_categories = client.operation("Repository.Query").param("query", "SELECT * FROM FVCategory WHERE fva:dialect IS NULL AND ecm:isTrashed = 0 AND ecm:isVersion = 0")
                .execute();

    }

    private void checkCategoryExists(String w, int line){
        Boolean match;
        String temp[];

        if(w.contains(",")) {
            temp = w.split(",");
            for (String word: temp) {
                match = false;

                // Fail on space between categories
                if (word.startsWith(" ")) {
                    word = word.trim();
                }

                if(!word.isEmpty()){
                    for (Document d:categories.getDocuments()) {
                        if(word.equals(d.getTitle())) {
                            match = true;
                            break;
                        }
                    }

                    for (Document d:shared_categories.getDocuments()) {
                        String title = d.getTitle();
                        if(word.equals(d.getTitle())) {
                            match = true;
                            break;
                        }
                    }

                    if(!match)
                        invalid.add("Only existing categories are supported: line " + (line) +", " +word);
                }
            }
        }
        else if(!w.isEmpty()){
            match = false;
            for (Document d:categories.getDocuments()) {
                if(w.equals(d.getTitle())) {
                    match = true;
                    break;
                }
            }

            for (Document d:shared_categories.getDocuments()) {
                String title = d.getTitle();
                if(w.equals(d.getTitle())) {
                    match = true;
                    break;
                }
            }

            if(!match)
                invalid.add("Only existing categories are supported, ensure no spaces between categories: line " + (line) +", " +w);
        }
    }

    private void checkWordDuplicate(String word, int line){
        for (Document d:words.getDocuments()) {
            if(d.getTitle().equals(word)){
                invalid.add("Cannot upload duplicate words: line " + (line) +", " +d.getTitle());
            }
        }
    }

    public void close()throws IOException{
        csvReader.close();
        client.disconnect();
    }

}
