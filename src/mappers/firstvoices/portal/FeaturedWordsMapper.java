/**
 *
 */
package mappers.firstvoices.portal;

import java.io.IOException;

import mappers.firstvoices.DictionaryCachedMapper;
import mappers.firstvoices.Properties;
import mappers.propertyreaders.PropertyReader;

import org.nuxeo.ecm.automation.client.model.Document;

public class FeaturedWordsMapper extends DictionaryCachedMapper {

	
	protected FeaturedWordsMapper(String wordColumn) {
		super("FVWord", wordColumn);
        cacheProperty = "fvl:import_id";
        
        // If the referenced word isn't already in the dictionary, don't try to create it
        fakeCreation = true;
        
		propertyReaders.add(new PropertyReader(Properties.IMPORT_ID, wordColumn));
	}

	@Override
    protected String getCacheQuery() {
	    return "SELECT * FROM FVWord WHERE ecm:parentId='" + documents.get("Dictionary").getId() + "' AND ecm:currentLifeCycleState != 'deleted'";
	}

	@Override
    protected Document createDocument(Document doc, Integer depth)
            throws IOException {
	    Document subDocument = super.createDocument(doc, depth);
	    updateMainDocumentReference(subDocument);
        return subDocument;
    }

    protected void updateMainDocumentReference(Document subDocument) {
    	if(subDocument != null) {
    		
    		Document currentDoc = documents.get("current");
    		
    		// Add the FVWord uuid doc reference to the list (comma-separated)
    		String featuredWordsCsvList = currentDoc.getDirties().getString("fv-portal:featured_words");
    		if(featuredWordsCsvList == null || featuredWordsCsvList.isEmpty()) {
    			currentDoc.set("fv-portal:featured_words", subDocument.getId());
    		} else {
    			featuredWordsCsvList += "," + subDocument.getId();
    			currentDoc.set("fv-portal:featured_words", featuredWordsCsvList);
    		}  		
    	}	
    }

}
