package mappers;

import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.model.*;
import com.opencsv.CSVReader;

import org.nuxeo.ecm.automation.client.jaxrs.impl.HttpAutomationClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Set;


public class CsvValidator{

    protected List<String> invalid = new ArrayList<>();
    protected List<String> t_or_f = new ArrayList<>();
    protected InputStreamReader fileReader;
    protected CSVReader csvReader;
    protected HttpAutomationClient client;
    protected Session session;
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
            "event_activity_verb_like_word_reflexive", "event_activity_verb_like_word_reciprocal", "question_word", "suffix_prefix"};
    private static final Set<String> POS = new HashSet<>(Arrays.asList(POS_VALUES));


    public CsvValidator(String nuxeoUrl, String nuxeoUser, String nuxeoPassword, String csvFile, String dialectID) throws IOException{

        if(!nuxeoUrl.endsWith("/site/automation"))
            nuxeoUrl=nuxeoUrl.concat("/site/automation");

        if (csvFile != null && !csvFile.isEmpty()) {
            fileReader = new InputStreamReader(new FileInputStream(csvFile), "UTF-8");
            csvReader = new CSVReader(fileReader, ',', '"', '\0');
        }
        client = new HttpAutomationClient(nuxeoUrl);
        session = client.getSession(nuxeoUser, nuxeoPassword);

        t_or_f.add("INCLUDE_IN_GAMES");
        t_or_f.add("CHILD_FRIENDLY");
        t_or_f.add("_SHARED_WITH_OTHER_DIALECTS");
        t_or_f.add("_CHILD_FOCUSED");

        getData(dialectID);
    }

    public List<String> validate(String path) throws IOException{

        String header[] = csvReader.readNext();
        String fileTypes[] = {"AUDIO", "VIDEO", "IMG"};
        String nextLine[];
        int lineNumber = 0;
        String headerTemp;
        while((nextLine = csvReader.readNext()) != null){
            Boolean audio_1 = false;
            int wordCount=0;
            lineNumber++;
            for (String word: nextLine) {

                headerTemp= header[wordCount];

                for (String type: fileTypes) {
                    if(header[wordCount].startsWith(type))
                        headerTemp = header[wordCount].substring(type.length());
                }

                if(headerTemp.equals("WORD"))
                    checkWordDuplicate(word, lineNumber);

                if(headerTemp.equals("CATEGORIES"))
                    checkCategoryExists(word, lineNumber);

                if(t_or_f.contains(headerTemp) && !word.equals("")){
                    if(!TFVALUES.contains(word))
                        invalid.add(header[wordCount] + " can only have true or false values: line " +lineNumber +", " +word);
                }

                if(headerTemp.endsWith("_FILENAME") && !word.equals("")){
                    checkFileExists(path+word, header[wordCount], lineNumber, word);
                    if(header[wordCount].equals("AUDIO_FILENAME"))
                        audio_1 = true;
                    if(header[wordCount].startsWith("AUDIO_2") && !audio_1)
                        invalid.add("Audio file 2 is given without audio file 1: line " +lineNumber +", " +word);
                }

                if(headerTemp.equals("PART_OF_SPEECH"))
                    checkPartsOfSpeech(word, lineNumber);

                if(headerTemp.equals("USERNAME"))
                    checkUserExists(word, lineNumber);

                wordCount++;

            }
        }
        return invalid;
    }

    private void checkFileExists(String path, String header, int line, String word ){
        File temp = new File(path);
        if(!temp.exists()){
            invalid.add("Unable to find file: " + header +", " +"line " +line +", " +word);
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
        //doc = (Document) session.newRequest("Document.GetUsersAndGroups").setInput(doc).set("permission", "edit").set("variable name", "usrs").execute();

    }

    private void getData(String dialect) throws IOException {

        words = (Documents) session.newRequest("Repository.Query")
                .set("query", "SELECT * FROM Document WHERE ecm:primaryType = 'FVWord' AND fva:dialect = '" + dialect +"'")
                .execute();

        categories = (Documents) session.newRequest("Repository.Query")
                .set("query", "SELECT * FROM Document WHERE ecm:primaryType = 'FVCategory' AND fva:dialect = '" + dialect+"'")
                .execute();

        shared_categories = (Documents) session.newRequest("Repository.Query")
                .set("query", "SELECT * FROM FVCategory WHERE ecm:path STARTSWITH '/FV/Workspaces/SharedData/Shared Categories'")
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
                    match = false;
                }

                if(!word.isEmpty()){
                    for (Document d:categories) {
                        if(word.equals(d.getTitle())) {
                            match = true;
                            break;
                        }
                    }

                    for (Document d:shared_categories) {
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
            for (Document d:categories) {
                if(w.equals(d.getTitle())) {
                    match = true;
                    break;
                }
            }

            for (Document d:shared_categories) {
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
        for (Document d:words) {
            if(d.getTitle().equals(word)){
                invalid.add("Cannot upload duplicate words: line " + (line) +", " +d.getTitle());
            }
        }
    }

    public void close()throws IOException{
        csvReader.close();
        client.shutdown();
    }

}
