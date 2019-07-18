package mappers.firstvoices.alphabet;

import mappers.firstvoices.DictionaryCachedMapper;
import mappers.firstvoices.Properties;
import mappers.propertyreaders.PropertyReader;
import org.nuxeo.client.objects.Document;

import java.io.IOException;


public class CharacterMigratorMapper extends DictionaryCachedMapper {

	public static int createdCharacters = 0;
	public static int existingCharacters = 0;

	public CharacterMigratorMapper() {
        super("FVCharacter", "ID");
        parentKey = "Alphabet";
        cacheProperty = "fvl:import_id";

        propertyReaders.add(new PropertyReader(Properties.TITLE, "CHAR"));
		propertyReaders.add(new PropertyReader("fvcharacter:upper_case_character", "CHAR_UPPER_CASE"));
        propertyReaders.add(new PropertyReader("fvcharacter:alphabet_order", "ORDER"));

//        subdocuments.add(new CharacterAudioMapper());
//        subdocuments.add(new CharacterSampleWordMapper());

	}

    @Override
	protected Document createDocument(Document doc, Integer depth) throws IOException {
		Document result = getFromCache(doc);
		// Create new FVCharacter document
		if (!fakeCreation && result == null) {
			result = client.operation("Document.Create").schemas("*")
				.input(documents.get(parentKey)).param("type", doc.getType()).param("name", doc.getId())
				.param("properties", doc).execute();

			tagAndUpdateCreator(result, doc);

			createdObjects++;
			createdCharacters++;
			cacheDocument(result);

			// If the parent document exists in the section, go ahead and publish the current document to the section
			//if(documents.get("SECTION_" + parentKey) != null) {
	    		//publishDocument(result);
			//}
		}
		// If the character document already exists, skip it
		else {
			existingCharacters++;
		}

		if (depth == 1) {
			documents.put("main", result);
		}
		return result;
	}

	@Override
	protected String getCacheQuery() {
        return "SELECT * FROM FVCharacter WHERE ecm:parentId='" + documents.get(parentKey).getId() + "' AND ecm:isTrashed = 0";
	}
}
