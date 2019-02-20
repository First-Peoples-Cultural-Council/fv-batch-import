/**
 *
 */
package mappers.firstvoices;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;

import mappers.propertyreaders.MultiValuedReader;
import mappers.propertyreaders.SimpleListPropertyReader;
import org.nuxeo.ecm.automation.client.Constants;
import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.model.Document;

import mappers.CsvMapper;
import mappers.propertyreaders.PropertyReader;
import mappers.propertyreaders.TranslationReader;

/**
 * @author dyona
 *
 */
public class WordMapper extends CsvMapper {

    protected static Map<String, Document> cache = null;

    private void setupProperties() {

        String[] definitionCols = {Columns.DOMINANT_LANGUAGE_DEFINITION, Columns.DOMINANT_LANGUAGE_DEFINITION + "_2", Columns.DOMINANT_LANGUAGE_DEFINITION + "_3", Columns.DOMINANT_LANGUAGE_DEFINITION + "_4", Columns.DOMINANT_LANGUAGE_DEFINITION + "_5"};
        String[] literalTranslationCols = {Columns.DOMINANT_LANGUAGE_WORD_VALUE, Columns.DOMINANT_LANGUAGE_WORD_VALUE + "_2", Columns.DOMINANT_LANGUAGE_WORD_VALUE + "_3", Columns.DOMINANT_LANGUAGE_WORD_VALUE + "_4", Columns.DOMINANT_LANGUAGE_WORD_VALUE + "_5"};
        String[] culturalNoteCols = {Columns.CULTURAL_NOTE, Columns.CULTURAL_NOTE + "_2", Columns.CULTURAL_NOTE + "_3", Columns.CULTURAL_NOTE + "_4", Columns.CULTURAL_NOTE + "_5"};

        propertyReaders.add(new PropertyReader(Properties.TITLE, Columns.WORD_VALUE));
        propertyReaders.add(new PartOfSpeechPropertyReader(Properties.PART_OF_SPEECH_ID, Columns.PART_OF_SPEECH));
        propertyReaders.add(new SimpleListPropertyReader(Properties.CULTURAL_NOTE, culturalNoteCols));
        propertyReaders.add(new PropertyReader(Properties.PHONETIC_INFO, Columns.PHONETIC_INFO));
        propertyReaders.add(new PropertyReader(Properties.ASSIGNED_USR_ID, Columns.ASSIGNED_USR_ID));
        propertyReaders.add(new PropertyReader(Properties.CHANGE_DTTM, Columns.CHANGE_DTTM));
        propertyReaders.add(new PropertyReader(Properties.IMPORT_ID, Columns.WORD_ID));
        propertyReaders.add(new PropertyReader(Properties.REFERENCE, Columns.REFERENCE));
        propertyReaders.add(new PropertyReader(Properties.AVAILABLE_IN_CHILDRENS_ARCHIVE, Columns.AVAILABLE_IN_CHILDRENS_ARCHIVE));
        propertyReaders.add(new PropertyReader(Properties.AVAILABLE_IN_GAMES, Columns.AVAILABLE_IN_GAMES));
        //propertyReaders.add(new PropertyReader(Properties.STATUS_ID, Columns.WORD_STATUS));

        propertyReaders.add(new TranslationReader(Properties.TRANSLATION, Columns.DOMINANT_LANGUAGE, literalTranslationCols));
        //propertyReaders.add(new TranslationReader(Properties.DEFINITION, Columns.DOMINANT_LANGUAGE, Columns.DOMINANT_LANGUAGE_DEFINITION));
        propertyReaders.add(new TranslationReader(Properties.DEFINITION, Columns.DOMINANT_LANGUAGE, definitionCols));

        subdocuments.add(new PhraseMapper());
        subdocuments.add(new CategoryMapper());
        subdocuments.add(new SourcesMapper());

        subdocuments.add(new AudioMapper());
        subdocuments.add(new AudioMapper(2));
        subdocuments.add(new PictureMapper());
        subdocuments.add(new VideoMapper());
    }

    public WordMapper(Session existingSession) {
    	super("FVWord", Columns.WORD_VALUE);
    	session = existingSession;

    	setupProperties();
    }

    public WordMapper() {
        super("FVWord", Columns.WORD_VALUE);

        setupProperties();
    }

    @Override
	protected Document createDocument(Document doc, Integer depth) throws IOException {
		Document result = null;
		if (!fakeCreation) {

		    // Set some defaults for words if they are not defined

            if (doc.getDirties().getBoolean(Properties.AVAILABLE_IN_GAMES) == null) {
                doc.set(Properties.AVAILABLE_IN_GAMES, true);
            }

            if (doc.getDirties().getBoolean(Properties.AVAILABLE_IN_CHILDRENS_ARCHIVE) == null) {
                doc.set(Properties.AVAILABLE_IN_CHILDRENS_ARCHIVE, true);
            }

			result = (Document) session.newRequest("Document.Create").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
				.setInput(documents.get(parentKey)).set("type", doc.getType()).set("name", doc.getId())
				.set("properties", doc).execute();

			tagAndUpdateCreator(result, doc);

            // Set the document state based on the fvl:status_id
            // For now disable setting a document state.
			//result = setDocumentState(result);

			createdObjects++;
			createdWords++;
			//cacheDocument(result);

			// If the parent document exists in the section, go ahead and publish the current document to the section
			/*if(documents.get("SECTION_" + parentKey) != null && "Enabled".equals(doc.getState())) {
	    		publishDocument(result);
			}*/
		}

		if (depth == 1) {
			documents.put("main", result);
		}
		return result;
	}

    @Override
    protected void cacheDocument(Document doc) {
        cache.put(doc.getString(Properties.IMPORT_ID), doc);
    }

    @Override
    public void buildCache() throws IOException {
/*        if (cache != null) {
            return;
        }
        cache = new HashMap<String, Document>();
        String query = "SELECT * FROM FVWord WHERE ecm:currentLifeCycleState != 'deleted' AND ecm:path STARTSWITH '/FV/'";
        loadCache(query);*/

return;
    }
}
