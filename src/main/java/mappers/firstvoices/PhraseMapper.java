/**
 *
 */
package mappers.firstvoices;

import mappers.propertyreaders.PropertyReader;
import mappers.propertyreaders.TranslationReader;
import org.nuxeo.client.objects.Document;

import java.io.IOException;

/**
 * @author loopingz
 *
 */
public class PhraseMapper extends DictionaryCachedMapper {

	//protected static String column = "";
	public PhraseMapper() {
		super("FVPhrase", Columns.PHRASE_COLUMN);
		propertyReaders.add(new PropertyReader(Properties.TITLE, Columns.PHRASE_COLUMN));
		propertyReaders.add(new TranslationReader(Properties.TRANSLATION, Columns.DOMINANT_LANGUAGE, Columns.DOMINANT_LANGUAGE_SENTENCE));
		propertyReaders.add(new TranslationReader(Properties.DEFINITION, Columns.DOMINANT_LANGUAGE, Columns.DOMINANT_LANGUAGE_SENTENCE_DEFINITION));
		subdocuments.add(new AudioMapper(Columns.PHRASE_COLUMN));
		subdocuments.add(new AudioMapper(Columns.PHRASE_COLUMN, 2));
	}

	protected PhraseMapper(String type, Object column) {
	    super(type, column);
	}

	@Override
    protected String getCacheQuery() {
	    return "SELECT * FROM FVPhrase WHERE ecm:parentId='" + documents.get("Dictionary").getId() + "' AND ecm:currentLifeCycleState != 'deleted'";
	}

	@Override
    protected Document createDocument(Document doc, Integer depth)
            throws IOException {
	    Document subDocument = super.createDocument(doc, depth);
	    updateMainDocumentReference(subDocument);
        return subDocument;
    }

    protected void updateMainDocumentReference(Document subDocument) {
        documents.get("current").setPropertyValue(Properties.RELATED_PHRASES, subDocument.getId());
    }

}
