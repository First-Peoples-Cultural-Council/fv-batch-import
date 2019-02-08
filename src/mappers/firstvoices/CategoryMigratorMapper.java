/**
 *
 */
package mappers.firstvoices;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import mappers.CsvMapper;
import mappers.propertyreaders.PropertyReader;

import org.apache.commons.lang.WordUtils;
import org.nuxeo.ecm.automation.client.Constants;
import org.nuxeo.ecm.automation.client.model.Document;

/**
 * @author cstuart
 *
 */
public class CategoryMigratorMapper extends CsvMapper {

    protected static Map<String, Document> cache = null;
    public static int createdCategories = 0;
    public static int updatedCategories = 0;

    public CategoryMigratorMapper() {
        super("FVCategory", Columns.ID);
        // Parent document can either be the "Shared Categories" folder, or a parent category
        parentKey = "parent";
        propertyReaders.add(new PropertyReader(Properties.TITLE, Columns.CODE));
        propertyReaders.add(new PropertyReader(Properties.IMPORT_ID, Columns.ID));

        //subdocuments.add(new PictureMapper());
    }
    @Override
    protected Document getFromCache(Document doc) {
        if (cache.containsKey(doc.getDirties().getString(Properties.IMPORT_ID))) {
            return cache.get(doc.getDirties().getString(Properties.IMPORT_ID));
        }
        return null;
    }

    @Override
    protected void cacheDocument(Document doc) {
        cache.put(doc.getString(Properties.IMPORT_ID), doc);
    }

    @Override
    public void buildCache() throws IOException {
        if (cache != null) {
            return;
        }
        cache = new HashMap<String, Document>();
        String query = "SELECT * FROM FVCategory WHERE ecm:currentLifeCycleState != 'deleted' AND ecm:path STARTSWITH '/FV/Workspaces'";
        loadCache(query);
    }

    @Override
	protected Document createDocument(Document doc, Integer depth) throws IOException {
		Document result = getFromCache(doc);

		// Perform some cleanup on the category title
		String cleanCategoryTitle = cleanupCategoryTitle(doc.getDirties().getString("dc:title"));
		doc.set("title", cleanCategoryTitle);

		// Category doesn't exist - create it
		if (!fakeCreation && result == null) {
			result = (Document) session.newRequest("Document.Create").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
				.setInput(documents.get(parentKey)).set("type", doc.getType()).set("name", doc.getId())
				.set("properties", doc).execute();

            tagAndUpdateCreator(result, doc);

			createdObjects++;
            if (doc.getType().endsWith("Category")) {
                createdCategories++;
            }

			cacheDocument(result);

			// If the parent document exists in the section, go ahead and publish the current document to the section
			//if(documents.get("SECTION_" + parentKey) != null) {
	    		publishDocument(result);
			//}
		}
//		// Category was found in the cache
//		else {
//			ConsoleLogger.out("Category found in cache: " + result.getId() + " - updating.");
//			result = (Document) session.newRequest("Document.Update").setInput(result).set("properties", doc).execute();
//			updatedCategories++;
//		}

		if (depth == 1) {
			documents.put("main", result);
		}
		return result;
	}

    protected String cleanupCategoryTitle(String categoryTitle) {
    	// Remove leading "- " that appears in 2nd level categories
    	String newCategoryTitle = categoryTitle.replace("- ", "");
    	// Convert " / " to "/"
    	newCategoryTitle = newCategoryTitle.replace(" / ", "/");
    	// Capitalize words after " " and after "/"
    	newCategoryTitle = WordUtils.capitalizeFully(newCategoryTitle, new char[]{' ', '/'});
    	return newCategoryTitle;
    }
}
