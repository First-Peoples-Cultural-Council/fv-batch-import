/**
 *
 */
package mappers.firstvoices;

import mappers.propertyreaders.PropertyReader;
import org.nuxeo.client.objects.Document;

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
        return "SELECT * FROM FVCategory WHERE (ecm:parentId='" + documents.get("Shared Categories").getId() + "'"
        		+ " OR ecm:parentId='" + documents.get("Categories").getId()
        		+ "') AND ecm:isTrashed = 0";
    }

    private void updateMainDocumentReference(ArrayList<String> docIDList) {
        documents.get("current").setPropertyValue(Properties.WORD_CATEGORIES, docIDList);
    }

    public CategoryMapper() {
        super("FVCategory", Columns.CATEGORIES);
        parentKey = "Categories";
        cacheProperty = Properties.TITLE;
        propertyReaders.add(new PropertyReader(Properties.WORD_CATEGORIES, Columns.CATEGORIES));
    }

    @Override
    protected Document createDocument(Document doc, Integer depth) throws IOException {

        String[] categories = doc.getPropertyValue("fv-word:categories").toString().split(",");
        ArrayList<String> categoryIDs = new ArrayList<>();

        for (String category : categories) {

            String trimmedCategory = category.trim();

            // Nuxeo will convert / to _ - convert it back for lookup
            trimmedCategory = trimmedCategory.replace("_", "/");

            Document fakeLookupDoc = Document.createWithName(trimmedCategory, "FVCategory");
            fakeLookupDoc.setPropertyValue("dc:title", trimmedCategory);

            Document cachedDoc = getFromCache(fakeLookupDoc);

            if (cachedDoc != null) {
                categoryIDs.add(cachedDoc.getId());
            } else {
                throw new IOException("Could not find category " + trimmedCategory);
            }

        }

        updateMainDocumentReference(categoryIDs);
        return null;
    }
}
