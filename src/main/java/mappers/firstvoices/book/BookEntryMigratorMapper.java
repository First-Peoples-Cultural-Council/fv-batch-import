package mappers.firstvoices.book;

import mappers.firstvoices.*;
import mappers.propertyreaders.PropertyReader;
import mappers.propertyreaders.TranslationReader;
import org.nuxeo.client.objects.Document;

import java.io.IOException;

public class BookEntryMigratorMapper extends DictionaryCachedMapper {

	public static int createdBookEntries = 0;
	public static int updatedBookEntries = 0;

	public BookEntryMigratorMapper() {
        super("FVBookEntry", Columns.ID);
        parentKey = "parent";
        cacheProperty = Properties.IMPORT_ID;
        propertyReaders.add(new PropertyReader(Properties.TITLE, "ABORIGINAL_LANGUAGE_TEXT"));

        propertyReaders.add(new PropertyReader(Properties.IMPORT_ID, Columns.ID));
        propertyReaders.add(new PropertyReader("fvbookentry:sort_map", "SORT_MAP"));
        propertyReaders.add(new PropertyReader(Properties.CULTURAL_NOTE, "CULTURAL_NOTE"));

        propertyReaders.add(new TranslationReader("fvbookentry:dominant_language_text", Columns.DOMINANT_LANGUAGE, "DOMINANT_LANGUAGE_TEXT"));
        propertyReaders.add(new TranslationReader("fv:literal_translation", Columns.DOMINANT_LANGUAGE, "DOMINANT_LANGUAGE_TRANSLATION"));

        subdocuments.add(new PictureMapper());
        subdocuments.add(new AudioMapper());
        subdocuments.add(new VideoMapper());

	}

    @Override
	protected Document createDocument(Document doc, Integer depth) throws IOException {
		Document result = getFromCache(doc);

		if (!fakeCreation && result == null) {
			result = client.operation("Document.Create").schemas("*")
				.input(documents.get(parentKey)).param("type", doc.getType()).param("name", doc.getId())
				.param("properties", doc).execute();

			tagAndUpdateCreator(result, doc);

			createdObjects++;
			createdBookEntries++;
			cacheDocument(result);

			// If the parent document exists in the section, go ahead and publish the current document to the section
			//if(documents.get("SECTION_" + parentKey) != null) {
	    		publishDocument(result);
			//}
		}
		// Book Entry was found in the cache
//		else {
//			ConsoleLogger.out("Book Entry found in cache: " + result.getId() + " - updating.");
//			result = (Document) session.newRequest("Document.Update").input(result).param("properties", doc).execute();
//			updatedBookEntries++;
//		}
		if (depth == 1) {
			documents.put("main", result);
		}
		return result;
	}

	@Override
	protected String getCacheQuery() {
	    return "SELECT * FROM FVBookEntry WHERE ecm:ancestorId='" + documents.get("Dialect").getId() + "' AND ecm:currentLifeCycleState != 'deleted'";
	}

}
