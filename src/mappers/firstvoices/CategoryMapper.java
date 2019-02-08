/**
 *
 */
package mappers.firstvoices;

import org.nuxeo.ecm.automation.client.Constants;
import org.nuxeo.ecm.automation.client.model.Document;

import mappers.propertyreaders.PropertyReader;
import org.nuxeo.ecm.automation.client.model.FileBlob;
import reader.AbstractReader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * @author dyona
 *
 */
public class CategoryMapper extends PhraseMapper {
	
    @Override
    protected String getCacheQuery() {
    	// Include all categories in the Shared Data folder
        return "SELECT * FROM FVCategory WHERE ecm:ancestorId='" + documents.get("Shared Data").getId()
        		+ "' OR ecm:parentId='" + documents.get("Categories").getId()
        		+ "' AND ecm:currentLifeCycleState != 'deleted'";
    }

    private void updateMainDocumentReference(String docIDList) {
        documents.get("current").set(Properties.WORD_CATEGORIES, docIDList);
    }

    public CategoryMapper() {
        super("FVCategory", Columns.CATEGORIES);
        parentKey = "Categories";
        cacheProperty = Properties.TITLE;
        propertyReaders.add(new PropertyReader(Properties.WORD_CATEGORIES, Columns.CATEGORIES));
    }

    @Override
    protected Document createDocument(Document doc, Integer depth) throws IOException {

        String[] categories = doc.toString().split(",");
        ArrayList<String> categoryIDs = new ArrayList<>();

        for (String category : categories) {

            String trimmedCategory = category.trim();

            // Nuxeo will convert / to _ - convert it back for lookup
            trimmedCategory = trimmedCategory.replace("_", "/");

            Document fakeLookupDoc = new Document(trimmedCategory, "FVCategory");
            fakeLookupDoc.set("dc:title", trimmedCategory);

            Document cachedDoc = getFromCache(fakeLookupDoc);

            if (cachedDoc != null) {
                categoryIDs.add(cachedDoc.getId());
            } else {
                throw new IOException("Could not find category " + trimmedCategory);
            }

        }

        updateMainDocumentReference(String.join(",", categoryIDs));
        return null;
    }
}
