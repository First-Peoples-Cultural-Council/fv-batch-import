/**
 *
 */
package mappers.firstvoices.alphabet;

import java.io.IOException;

import mappers.firstvoices.DictionaryCachedMapper;
import mappers.firstvoices.Properties;
import mappers.propertyreaders.PropertyReader;

import org.nuxeo.ecm.automation.client.model.Document;

public class CharacterSampleWordMapper extends DictionaryCachedMapper {
	
	protected CharacterSampleWordMapper() {
		super("FVWord", "SAMPLE_WORD");
        cacheProperty = "dc:title";
        
        // If the referenced word isn't already in the dictionary, don't try to create it
        fakeCreation = true;   
        
		propertyReaders.add(new PropertyReader(Properties.TITLE, "SAMPLE_WORD"));

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
    		String sampleWordCsvList = currentDoc.getDirties().getString("fvcharacter:related_words");
    		if(sampleWordCsvList == null || sampleWordCsvList.isEmpty()) {
    			currentDoc.set("fvcharacter:related_words", subDocument.getId());
    		} else {
    			sampleWordCsvList += "," + subDocument.getId();
    			currentDoc.set("fvcharacter:related_words", sampleWordCsvList);
    		}  		
    	}	
    }

}
