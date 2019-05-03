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
    protected Document createDocument(Document doc, Integer depth) throws IOException {
        String title = (String) doc.getDirtyProperties().get(Properties.TITLE);
        String value = "";
        Document remoteDoc = null;

        ArrayList<String> sourcesIds = new ArrayList<String>();

        if (!title.contains("http:") && title.contains("/")) {
            // Multivalued within a string
            String[] sources = title.split("/");
            for (String src : sources) {
                src = src.trim();
                Document sourceDoc = Document.createWithName(src, type);
                sourceDoc.setPropertyValue(Properties.TITLE, src);
                remoteDoc = getFromCache(sourceDoc);
                if (remoteDoc == null) {

                    remoteDoc = Document.createWithName(doc.getName(), doc.getType());
                    remoteDoc.setProperties(sourceDoc.getProperties());
                    remoteDoc = client.repository().createDocumentByPath(documents.get("Contributors").getPath(), remoteDoc);

                    CsvMapper.createdObjects++;
                    cacheDocument(remoteDoc);

                    tagAndUpdateCreator(remoteDoc, doc);
                }
                sourcesIds.add(remoteDoc.getId());
            }
        } else {
            // If not multivalued normal behavior
            remoteDoc = super.createDocument(doc, depth);
            sourcesIds.add(remoteDoc.getId());
        }

        documents.get("current").setPropertyValue(linkKey, sourcesIds);
        return remoteDoc;
    }

    public SourcesMapper() {
        super("FVContributor", Columns.CONTRIBUTER);
        parentKey = "Contributors";
        linkKey = Properties.CONTRIBUTER;
        propertyReaders.add(new PropertyReader(Properties.TITLE, Columns.CONTRIBUTER));
    }

    public SourcesMapper(String linkKey, String column) {
        super("FVContributor", column);
        parentKey = "Contributors";
        this.linkKey = linkKey;
        propertyReaders.add(new PropertyReader(Properties.TITLE, column));
    }

    @Override
    protected String getCacheQuery() {
        return "SELECT * FROM FVContributor WHERE ecm:parentId='" + documents.get("Contributors").getId() + "' AND ecm:currentLifeCycleState != 'deleted'";
    }
}
