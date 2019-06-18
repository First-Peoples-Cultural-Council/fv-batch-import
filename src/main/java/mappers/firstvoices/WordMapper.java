/**
 *
 */
package mappers.firstvoices;

import mappers.CsvMapper;
import mappers.propertyreaders.PropertyReader;
import mappers.propertyreaders.SimpleListPropertyReader;
import mappers.propertyreaders.TranslationReader;
import org.nuxeo.client.NuxeoClient;
import org.nuxeo.client.objects.Document;

import java.io.IOException;
import java.util.Map;

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
        String[] pluralCols = {Columns.WORD_PLURAL, Columns.WORD_PLURAL + "_2", Columns.WORD_PLURAL + "_3", Columns.WORD_PLURAL + "_4", Columns.WORD_PLURAL + "_5"};

        propertyReaders.add(new PropertyReader(Properties.TITLE, Columns.WORD_VALUE));
        propertyReaders.add(new PartOfSpeechPropertyReader(Properties.PART_OF_SPEECH_ID, Columns.PART_OF_SPEECH));
        propertyReaders.add(new SimpleListPropertyReader(Properties.CULTURAL_NOTE, culturalNoteCols));
        propertyReaders.add(new SimpleListPropertyReader(Properties.PLURAL, pluralCols));
        propertyReaders.add(new PropertyReader(Properties.PHONETIC_INFO, Columns.PHONETIC_INFO));
        propertyReaders.add(new PropertyReader(Properties.ASSIGNED_USR_ID, Columns.ASSIGNED_USR_ID));
        propertyReaders.add(new PropertyReader(Properties.CHANGE_DTTM, Columns.CHANGE_DTTM));
        propertyReaders.add(new PropertyReader(Properties.IMPORT_ID, Columns.WORD_ID));
        propertyReaders.add(new PropertyReader(Properties.REFERENCE, Columns.REFERENCE));
        propertyReaders.add(new PropertyReader(Properties.ACKNOWLEDGEMENT, Columns.ACKNOWLEDGEMENT));
        propertyReaders.add(new PropertyReader(Properties.AVAILABLE_IN_CHILDRENS_ARCHIVE, Columns.AVAILABLE_IN_CHILDRENS_ARCHIVE));
        propertyReaders.add(new PropertyReader(Properties.AVAILABLE_IN_GAMES, Columns.AVAILABLE_IN_GAMES));
        //propertyReaders.add(new PropertyReader(Properties.STATUS_ID, Columns.WORD_STATUS));

        //propertyReaders.add(new TranslationReader(Properties.TRANSLATION, Columns.DOMINANT_LANGUAGE, literalTranslationCols));
        //propertyReaders.add(new TranslationReader(Properties.DEFINITION, Columns.DOMINANT_LANGUAGE, Columns.DOMINANT_LANGUAGE_DEFINITION));
        propertyReaders.add(new TranslationReader(Properties.DEFINITION, Columns.DOMINANT_LANGUAGE, definitionCols));

        subdocuments.add(new PhraseMapper());
        subdocuments.add(new CategoryMapper());
        subdocuments.add(new SourcesMapper());
        subdocuments.add(new AudioMapper());
        subdocuments.add(new AudioMapper(2));
        subdocuments.add(new AudioMapper(3));
        subdocuments.add(new PictureMapper());
        subdocuments.add(new PictureMapper(2));
        subdocuments.add(new VideoMapper());
        subdocuments.add(new VideoMapper(2));
    }

    public WordMapper(NuxeoClient client) {
    	super("FVWord", Columns.WORD_VALUE);
        this.client = client;

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

            if (doc.getDirtyProperties().get(Properties.AVAILABLE_IN_GAMES) == null) {
                doc.setPropertyValue(Properties.AVAILABLE_IN_GAMES, true);
            }

            if (doc.getDirtyProperties().get(Properties.AVAILABLE_IN_CHILDRENS_ARCHIVE) == null) {
                doc.setPropertyValue(Properties.AVAILABLE_IN_CHILDRENS_ARCHIVE, true);
            }

            result = Document.createWithName(doc.getName(), doc.getType());
            result.setProperties(doc.getProperties());
            result = client.repository().createDocumentById(documents.get(parentKey).getId(), result);

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
        cache.put(doc.getPropertyValue(Properties.IMPORT_ID), doc);
    }

    @Override
    public void buildCache() throws IOException {
/*        if (cache != null) {
            return;
        }
        cache = new HashMap<String, Document>();
        String query = "SELECT * FROM FVWord WHERE ecm:isTrashed = 0 AND ecm:path STARTSWITH '/FV/'";
        loadCache(query);*/

return;
    }
}