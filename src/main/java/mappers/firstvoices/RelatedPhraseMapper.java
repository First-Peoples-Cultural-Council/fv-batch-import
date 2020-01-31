/**
 *
 */
package mappers.firstvoices;

import mappers.propertyreaders.PropertyReader;
import mappers.propertyreaders.TranslationReader;
import org.nuxeo.client.objects.Document;

import java.io.IOException;
import java.util.ArrayList;

/**
 * @author loopingz
 * Class to migrate RELATED_PHRASES from a CSV file into the FirstVoices system.
 */
public class RelatedPhraseMapper extends DictionaryCachedMapper {

	//protected static String column = "";
	public RelatedPhraseMapper() {
		super("FVPhrase", Columns.PHRASE_COLUMN);
		propertyReaders.add(new PropertyReader(Properties.TITLE, Columns.PHRASE_COLUMN));
		propertyReaders.add(new TranslationReader(Properties.TRANSLATION, Columns.DOMINANT_LANGUAGE, Columns.DOMINANT_LANGUAGE_SENTENCE_VALUE));
		propertyReaders.add(new TranslationReader(Properties.DEFINITION, Columns.DOMINANT_LANGUAGE, Columns.DOMINANT_LANGUAGE_SENTENCE_DEFINITION));
		propertyReaders.add(new PropertyReader(Properties.PHRASE_ACKNOWLEDGEMENT, Columns.PHRASE_ACKNOWLEDGEMENT));
		propertyReaders.add(new PropertyReader(Properties.REFERENCE, Columns.PHRASE_REFERENCE));
		subdocuments.add(new AudioMapper(Columns.PHRASE_COLUMN));
		subdocuments.add(new AudioMapper(Columns.PHRASE_COLUMN, 2));
	}

	protected RelatedPhraseMapper(String type, Object column) {
	    super(type, column);
	}

	@Override
    protected String getCacheQuery() {
	    return "SELECT * FROM FVPhrase WHERE ecm:parentId='" + documents.get("Dictionary").getId() + "' AND ecm:isTrashed = 0";
	}

	@Override
    protected Document createDocument(Document doc, Integer depth)
            throws IOException {
	    Document subDocument = super.createDocument(doc, depth);
	    updateMainDocumentReference(subDocument);
        return subDocument;
    }

    protected void updateMainDocumentReference(Document subDocument) {
		ArrayList<String> phrases = new ArrayList<>();
		phrases.add(subDocument.getId());
		documents.get("current").setPropertyValue(Properties.RELATED_PHRASES, phrases);
    }

}
