/**
 *
 */
package mappers.firstvoices;

import mappers.propertyreaders.PropertyReader;
import org.nuxeo.client.objects.Document;

/**
 * @author dyona
 *
 */
public class PhraseBookMapper extends PhraseMapper {

    @Override
    protected String getCacheQuery() {
    	// Include all categories in the Shared Data folder
        return "SELECT * FROM FVCategory WHERE ecm:parentId='" + documents.get("Phrase Books").getId()
        		+ "' AND ecm:currentLifeCycleState != 'deleted'";
    }

    @Override
    protected void updateMainDocumentReference(Document subDocument) {
        documents.get("current").setPropertyValue(Properties.PHRASE_BOOKS, subDocument.getId());
    }

    public PhraseBookMapper() {
        super("FVCategory", Columns.CATEGORY_ID);
        parentKey = "Phrase Books";
        propertyReaders.add(new PropertyReader(Properties.TITLE, Columns.CATEGORY_NAME));
        propertyReaders.add(new PropertyReader(Properties.IMPORT_ID, Columns.CATEGORY_ID));
    }

}
