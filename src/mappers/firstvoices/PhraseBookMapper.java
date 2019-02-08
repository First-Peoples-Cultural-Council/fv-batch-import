/**
 *
 */
package mappers.firstvoices;

import org.nuxeo.ecm.automation.client.model.Document;

import mappers.propertyreaders.PropertyReader;

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
        documents.get("current").set(Properties.PHRASE_BOOKS, subDocument.getId());
    }

    public PhraseBookMapper() {
        super("FVCategory", Columns.CATEGORY_ID);
        parentKey = "Phrase Books";
        propertyReaders.add(new PropertyReader(Properties.TITLE, Columns.CATEGORY_NAME));
        propertyReaders.add(new PropertyReader(Properties.IMPORT_ID, Columns.CATEGORY_ID));
    }

}
