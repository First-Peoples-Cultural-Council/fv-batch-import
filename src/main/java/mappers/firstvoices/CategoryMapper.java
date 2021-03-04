package mappers.firstvoices;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import mappers.propertyreaders.PropertyReader;
import org.nuxeo.client.objects.Document;
import org.nuxeo.client.objects.Documents;

/**
 * @author dyona
 *
 */
public class CategoryMapper extends DictionaryCachedMapper {

  public CategoryMapper() {
    super("FVCategory", Columns.CATEGORIES);
    parentKey = "Categories";
    cacheProperty = Properties.TITLE;
    propertyReaders.add(new PropertyReader(Properties.WORD_CATEGORIES, Columns.CATEGORIES, true));
  }

  @Override
  protected String getCachedProperty(Document doc, boolean fromDirty) {
    String cachedProperty;

    if (fromDirty) {
      cachedProperty = (String) doc.getDirtyProperties().get(cacheProperty);
    } else {
      cachedProperty = doc.getPropertyValue(cacheProperty);
    }

    // For categories, we can normalize to lowercase
    return (cachedProperty == null) ? null : cachedProperty.toLowerCase();
  }

  @Override
  protected String getCacheQuery() {

    Documents dialectCategoriesDirectory = client.operation("Repository.Query")
        .param("query", "SELECT * FROM FVCategories "
            + "WHERE fva:dialect = '" + getDialectID() + "' "
            + "AND ecm:path STARTSWITH '/FV/Workspaces/' "
            + "AND dc:title = 'Categories' "
            + "AND ecm:isTrashed = 0 "
            + "AND ecm:isVersion = 0")
        .execute();
    List<Document> documentsList = dialectCategoriesDirectory.streamEntries().collect(
        Collectors.toList());

    if (!documentsList.isEmpty()) {
      Document categoriesDirectory = documentsList.get(0);
      String categoriesDirectoryId = categoriesDirectory.getId();
      return "SELECT * FROM FVCategory WHERE ecm:ancestorId = '" + categoriesDirectoryId
          + "'AND ecm:isTrashed = 0 AND ecm:isVersion = 0 AND ecm:isProxy = 0";
    }
    /* Else return null */
    return null;

  }

  private void updateMainDocumentReference(ArrayList<String> docIDList) {
    documents.get("current").setPropertyValue(Properties.WORD_CATEGORIES, docIDList);
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
        System.out
            .println("Found category in database. Adding '" + trimmedCategory + "' to array ");
        categoryIDs.add(cachedDoc.getId());
      } else {
        System.out.println("Could not find category " + trimmedCategory);
        throw new IOException("Could not find category " + trimmedCategory);
      }

    }

    System.out.println("Setting category references on word document");
    updateMainDocumentReference(categoryIDs);
    return null;
  }
}
