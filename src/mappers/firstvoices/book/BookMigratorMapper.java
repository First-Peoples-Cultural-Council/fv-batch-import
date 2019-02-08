package mappers.firstvoices.book;

import java.io.IOException;

import mappers.firstvoices.AudioMapper;
import mappers.firstvoices.Columns;
import mappers.firstvoices.DictionaryCachedMapper;
import mappers.firstvoices.PictureMapper;
import mappers.firstvoices.Properties;
import mappers.firstvoices.SourcesMapper;
import mappers.firstvoices.VideoMapper;
import mappers.propertyreaders.PropertyReader;
import mappers.propertyreaders.TranslationReader;

import org.nuxeo.ecm.automation.client.Constants;
import org.nuxeo.ecm.automation.client.model.Document;


public class BookMigratorMapper extends DictionaryCachedMapper {

	public static int createdBooks = 0;
	public static int updatedBooks = 0;

	public BookMigratorMapper() {
        super("FVBook", "ID");
        parentKey = "Stories & Songs";
        cacheProperty = "fvl:import_id";

        propertyReaders.add(new PropertyReader(Properties.TITLE, "ABORIGINAL_LANGUAGE_TITLE"));
        propertyReaders.add(new PropertyReader("fvbook:introduction", "ABORIGINAL_LANGUAGE_INTRO"));
        propertyReaders.add(new PropertyReader(Properties.IMPORT_ID, "ID"));
        propertyReaders.add(new PropertyReader(Properties.CULTURAL_NOTE, "CULTURAL_NOTE"));
        propertyReaders.add(new PropertyReader(Properties.ASSIGNED_USR_ID, Columns.ASSIGNED_USR_ID));
        propertyReaders.add(new PropertyReader(Properties.AVAILABLE_IN_CHILDRENS_ARCHIVE, Columns.AVAILABLE_IN_CHILDRENS_ARCHIVE));
        propertyReaders.add(new PropertyReader("fvbook:sort_map", "SORT_MAP"));
        propertyReaders.add(new PropertyReader(Properties.STATUS_ID, "STATUS_ID"));

        propertyReaders.add(new TranslationReader("fvbook:title_literal_translation", Columns.DOMINANT_LANGUAGE, "DOMINANT_LANGUAGE_TITLE"));
        propertyReaders.add(new TranslationReader("fvbook:introduction_literal_translation", Columns.DOMINANT_LANGUAGE, "DOMINANT_LANGUAGE_INTRO"));
        propertyReaders.add(new TranslationReader("fvbook:dominant_language_translation", Columns.DOMINANT_LANGUAGE, "DOMINANT_LANGUAGE_TRANSLATION"));

        propertyReaders.add(new BookTypePropertyReader("fvbook:type", "SSTYPE_ID"));

        subdocuments.add(new SourcesMapper("fvbook:author", "AUTHOR"));
        subdocuments.add(new SourcesMapper("fvcore:source", "CONTRIBUTER"));

        subdocuments.add(new PictureMapper());
        subdocuments.add(new AudioMapper());
        subdocuments.add(new VideoMapper());

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
			createdBooks++;

			// Set the document state based on the fvl:status_id
			result = setDocumentState(result);

			cacheDocument(result);

			// If the parent document exists in the section, go ahead and publish the current document to the section
			//if(documents.get("SECTION_" + parentKey) != null) {
	    		publishDocument(result);
			//}
		}
		// Book was found in the cache
//		else {
//			ConsoleLogger.out("Book found in cache: " + result.getId() + " - updating.");
//			result = (Document) session.newRequest("Document.Update").setInput(result).set("properties", doc).execute();
//			updatedBooks++;
//		}
		if (depth == 1) {
			documents.put("main", result);
		}
		return result;
	}

	@Override
	protected String getCacheQuery() {
        return "SELECT * FROM FVBook WHERE ecm:ancestorId='" + documents.get("Dialect").getId() + "' AND ecm:currentLifeCycleState != 'deleted'";
	}
}
