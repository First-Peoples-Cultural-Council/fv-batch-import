/**
 *
 */
package mappers.firstvoices;

import mappers.CsvMapper;
import mappers.propertyreaders.PropertyReader;
import org.nuxeo.client.objects.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

/**
 * @author dyona
 *
 */
public class SourcesMapper extends DictionaryCachedMapper {

    private String linkKey;
    private static Map<String, Map<String,Document>> cache = null;

    @Override
    protected String getCacheQuery() {
        // Include all contributors from Dialect
        return "SELECT * FROM FVContributor WHERE ecm:parentId='" + documents.get("Contributors").getId() + "' AND ecm:isTrashed = 0";
    }

    private void updateMainDocumentReference(String linkKey, ArrayList<String> sourcesIds) {
        // Get current sources, and append new values if exists
        if (documents.get("current").getPropertyValue(linkKey) != null) {
            ArrayList<String> existingSourcesIds = documents.get("current").getPropertyValue(linkKey);
            sourcesIds.addAll(existingSourcesIds);
        }
        documents.get("current").setPropertyValue(linkKey, sourcesIds);
    }

    public SourcesMapper(int number) {
        super("FVContributor", Columns.CONTRIBUTOR + "_" + number);
        propertyReaders.add(new PropertyReader(Properties.SOURCE, Columns.CONTRIBUTOR + "_" + number));
    }

    public SourcesMapper() {
        super("FVContributor", Columns.CONTRIBUTOR);
        parentKey = "Contributors";
        linkKey = Properties.SOURCE;
        propertyReaders.add(new PropertyReader(Properties.TITLE, Columns.CONTRIBUTOR));
    }

    public SourcesMapper(String linkKey, String column) {
        super("FVContributor", column);
        parentKey = "Contributors";
        this.linkKey = linkKey;
        propertyReaders.add(new PropertyReader(Properties.TITLE, column));
    }

    @Override
    protected Document createDocument(Document doc, Integer depth) throws IOException {
//        String title = (String) doc.getDirtyProperties().get(Properties.TITLE);
        String title = (String) doc.getPropertyValue("dc:title");
        String value = "";
        Document remoteDoc = null;

        ArrayList<String> sourcesIds = new ArrayList<String>();

        String trimmedTitle = title.trim();

        Document fakeLookupDoc = Document.createWithName(trimmedTitle, "FVContributor");
        fakeLookupDoc.setPropertyValue("dc:title", trimmedTitle);

        Document cachedDoc = getFromCache(fakeLookupDoc);

        if (cachedDoc != null) {
            sourcesIds.add(cachedDoc.getId());
        } else {
            remoteDoc = super.createDocument(doc, depth);
            sourcesIds.add(remoteDoc.getId());
        }

        updateMainDocumentReference(linkKey, sourcesIds);
        return remoteDoc;
    }
}
