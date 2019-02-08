/**
 *
 */
package mappers.firstvoices;

import java.io.IOException;
import java.util.Map;

import mappers.propertyreaders.PropertyReader;
import mappers.propertyreaders.TranslationReader;

import org.nuxeo.ecm.automation.client.Constants;
import org.nuxeo.ecm.automation.client.model.Document;

import common.ConsoleLogger;

/**
 * @author cstuart
 *
 */
public class PhraseMigratorMapper extends DictionaryCachedMapper {

    protected static Map<String, Document> cache = null;
    public static int createdPhrases = 0;
    public static int updatedPhrases = 0;
    public static int cachedPhrases = 0;

    public PhraseMigratorMapper() {
        super("FVPhrase", "PHRASE_ID");
        propertyReaders.add(new PropertyReader(Properties.TITLE, Columns.PHRASE));
        propertyReaders.add(new PropertyReader(Properties.CULTURAL_NOTE, Columns.CULTURAL_NOTE));
        propertyReaders.add(new PropertyReader(Properties.ASSIGNED_USR_ID, Columns.ASSIGNED_USR_ID));
        propertyReaders.add(new PropertyReader(Properties.CHANGE_DTTM, Columns.CHANGE_DTTM));
        propertyReaders.add(new PropertyReader(Properties.IMPORT_ID, Columns.PHRASE_ID));
        propertyReaders.add(new PropertyReader(Properties.REFERENCE, Columns.REFERENCE));
        propertyReaders.add(new PropertyReader(Properties.AVAILABLE_IN_CHILDRENS_ARCHIVE, Columns.AVAILABLE_IN_CHILDRENS_ARCHIVE));
        propertyReaders.add(new PropertyReader(Properties.STATUS_ID, Columns.PHRASE_STATUS));
        propertyReaders.add(new TranslationReader(Properties.DEFINITION, Columns.DOMINANT_LANGUAGE, Columns.DOMINANT_LANGUAGE_PHRASE));

        subdocuments.add(new SourcesMapper());

        subdocuments.add(new AudioMapper());
        subdocuments.add(new PictureMapper());
        subdocuments.add(new VideoMapper());

        subdocuments.add(new PhraseBookMapper());
    }

    @Override
	protected Document createDocument(Document doc, Integer depth) throws IOException {
		Document result = getFromCache(doc);
		if (!fakeCreation && result == null) {
			result = (Document) session.newRequest("Document.Create").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
				.setInput(documents.get(parentKey)).set("type", doc.getType()).set("name", doc.getId())
				.set("properties", doc).execute();

			tagAndUpdateCreator(result, doc);

			createdObjects++;

			// Set the document state based on the fvl:status_id
			result = setDocumentState(result);

            if (doc.getType().endsWith("Phrase")) {
                createdPhrases++;
            }
			cacheDocument(result);

			// If the parent document exists in the section, go ahead and publish the current document to the section
			//if(documents.get("SECTION_" + parentKey) != null) {
	    		publishDocument(result);
			//}
		}
		// Phrase was found in the cache
		else {
			ConsoleLogger.out("Phrase found in cache: " + doc.getDirties().getString("dc:title"));
			String existingPhraseImportId = result.getProperties().getString("fvl:import_id");
			// If importId is null, the phrase was imported previously as part of a word import - update it
			if(existingPhraseImportId == null || existingPhraseImportId.isEmpty()) {
				ConsoleLogger.out("Phrase import_id is null - updating existing phrase document.");
				result = (Document) session.newRequest("Document.Update").setInput(result).set("properties", doc).execute();
				updatedPhrases++;

				// If the parent document exists in the section, go ahead and publish the current document to the section
				//if(documents.get("SECTION_" + parentKey) != null) {
		    		publishDocument(result);
				//}
			} else {
				String newPhraseImportId = doc.getDirties().getString("fvl:import_id");
				if(!newPhraseImportId.equals(existingPhraseImportId)) {
					// Phrase title found in the cache, but with a different import_id - a duplicate in the CSV. We need to create it
					result = (Document) session.newRequest("Document.Create").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
							.setInput(documents.get(parentKey)).set("type", doc.getType()).set("name", doc.getId())
							.set("properties", doc).execute();
					createdPhrases++;

					tagAndUpdateCreator(result, doc);

					// If the parent document exists in the section, go ahead and publish the current document to the section
					//if(documents.get("SECTION_" + parentKey) != null) {
			    		publishDocument(result);
					//}
				} else {
					// If the import ids are the same, return the existing phrase
					cachedPhrases++;
				}
			}
		}

		if (depth == 1) {
			documents.put("main", result);
		}
		return result;
	}

	@Override
	protected String getCacheQuery() {
        return "SELECT * FROM FVPhrase WHERE ecm:parentId='" + documents.get("Dictionary").getId() + "' AND ecm:currentLifeCycleState != 'deleted'";
	}
}
