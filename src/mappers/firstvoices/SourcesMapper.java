/**
 *
 */
package mappers.firstvoices;

import java.io.IOException;
import java.util.Map;

import org.nuxeo.ecm.automation.client.Constants;
import org.nuxeo.ecm.automation.client.model.Document;

import mappers.CsvMapper;
import mappers.propertyreaders.PropertyReader;

/**
 * @author dyona
 *
 */
public class SourcesMapper extends DictionaryCachedMapper {

    private String linkKey;
    private static Map<String, Map<String,Document>> cache = null;

    @Override
    protected Document createDocument(Document doc, Integer depth) throws IOException {
        String title = doc.getDirties().getString(Properties.TITLE);
        String value = "";
        Document remoteDoc = null;
        if (!title.contains("http:") && title.contains("/")) {
            // Multivalued within a string
            String[] sources = title.split("/");
            for (String src : sources) {
                src = src.trim();
                Document sourceDoc = new Document(src, type);
                sourceDoc.set(Properties.TITLE, src);
                remoteDoc = getFromCache(sourceDoc);
                if (remoteDoc == null) {
                    remoteDoc = (Document) session.newRequest("Document.Create")
                        .setHeader(Constants.HEADER_NX_SCHEMAS, "*")
                        .setInput(documents.get("Contributors"))
                        .set("type", doc.getType())
                        .set("name", doc.getId())
                        .set("properties", sourceDoc).execute();
                    CsvMapper.createdObjects++;
                    cacheDocument(remoteDoc);

                    tagAndUpdateCreator(remoteDoc, doc);
                }
                value += remoteDoc.getId() + ',';
            }
            value = value.substring(0, value.length()-1);
        } else {
            // If not multivalued normal behavior
            remoteDoc = super.createDocument(doc, depth);
            value = remoteDoc.getId();
        }
        documents.get("current").set(linkKey, value);
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
