package mappers.firstvoices;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.nuxeo.ecm.automation.client.Constants;
import org.nuxeo.ecm.automation.client.model.*;

import mappers.propertyreaders.PropertyReader;
import mappers.propertyreaders.StorePropertyReader;
import reader.AbstractReader;

public abstract class BinaryMapper extends DictionaryCachedMapper {

    protected static String dataPath;
    protected String prefix = null;

    public static String getDataPath() {
        return dataPath;
    }

    public static void setDataPath(String dataPath) {
        BinaryMapper.dataPath = dataPath;
    }

    private StorePropertyReader binaryPathReader;

    private String linkKey;

    @Override
    protected String getId(String id) {
        // Remove file extension for files
        id = id.replaceAll("(\\.\\w{3,4}$)", "");

        // Replace subset of special chars
        id = id.replaceAll("([$&+,:;=?@#|'<>.^*()%!\\/-])+", "_");

        return id;
    }

    @Override
    protected Document createDocument(Document doc, Integer depth) throws IOException {
        Document binaryDoc = getFromCache(doc);

        if (binaryDoc == null) {
            // Wait to create the source first

        	String binaryFileRelativePath = binaryPathReader.getCacheValue();
        	// Remove leading slash on filename
        	if(binaryFileRelativePath.startsWith("/")) {
        		binaryFileRelativePath = binaryFileRelativePath.substring(1);
        	}

            String binaryFileFullPath = getDataPath() + binaryFileRelativePath;
            binaryFileFullPath.replace("/", "_");

            File file = new File(binaryFileFullPath);

            if (binaryFileRelativePath.equals("") || !file.exists()) {
                throw new IOException(binaryFileRelativePath + " File not found");
                //return null;
            }

            // Get CSV Reader
            AbstractReader reader = getCSVReader();

            // Set Properties that are derived from other properties, if _TITLE is not specified
            if (reader.getString(getPrefix() + "_" + Columns.TITLE).isEmpty()) {
                System.out.println("Setting value: '" + Properties.TITLE + "' to '" + file.getName() + "'");
                doc.set(Properties.TITLE, file.getName());
            }

            // Set some defaults for binary documents if they are not defined

            if (doc.getDirties().getBoolean(Properties.MEDIA_SHARED) == null) {
                doc.set(Properties.MEDIA_SHARED, false);
            }

            if (doc.getDirties().getBoolean(Properties.CHILD_FOCUSED) == null) {
                doc.set(Properties.CHILD_FOCUSED, false);
            }

            binaryDoc = super.createDocument(doc, depth);

			// Set the document state based on the fvl:status_id
			//binaryDoc = setDocumentState(binaryDoc);

            FileBlob fb = new FileBlob(file);

            session.newRequest("Blob.Attach")
                .setHeader(Constants.HEADER_NX_VOIDOP, "true")
                .setInput(fb)
                .set("document", binaryDoc).execute();
        }

        if(getPrefix().equals("AUDIO_2")) {
            String qu = "SELECT * FROM Document WHERE ecm:primaryType='FVAudio' AND ecm:currentLifeCycleState != 'deleted' ORDER BY dc:created DESC";
            Documents ques = (Documents) session.newRequest("Repository.Query").setHeader(
                    Constants.HEADER_NX_SCHEMAS, "*")
                    .set("query", qu)
                    .execute();

            documents.get("current").set(linkKey, ques.get(1).getId()+","+ binaryDoc.getId());
        }
        else
            documents.get("current").set(linkKey, binaryDoc.getId());

        System.out.println("Setting value: '" + linkKey + "' to doc: '" + binaryDoc.getTitle() + "'");

        return binaryDoc;
    }

    protected BinaryMapper(String type, Object column, String prefix, String linkKey) {
        super(type, column);

        setPrefix(prefix);

        parentKey = "Resources";
        this.linkKey = linkKey;
        cacheProperty = prefix + "_" + Columns.FILENAME;

        binaryPathReader = new StorePropertyReader(prefix + "_" + Columns.FILENAME);
        propertyReaders.add(binaryPathReader);
        //propertyReaders.add(new PropertyReader(Properties.IMPORT_ID, prefix + "_" + Columns.ID));
        propertyReaders.add(new PropertyReader(Properties.TITLE, prefix + "_" + Columns.TITLE));
        propertyReaders.add(new PropertyReader(Properties.DESCR, prefix + "_" + Columns.DESCR));
        //propertyReaders.add(new PropertyReader(Properties.MEDIA_STATUS, prefix + "_" + Columns.STATUS));
        propertyReaders.add(new PropertyReader(Properties.MEDIA_SHARED, prefix + "_" + Columns.SHARED));
        propertyReaders.add(new PropertyReader(Properties.CHILD_FOCUSED, prefix + "_" + Columns.CHILD_FOCUSED));

        subdocuments.add(new SourcesMapper(Properties.MEDIA_SOURCE, prefix + "_" + Columns.SOURCE));
        subdocuments.add(new SourcesMapper(Properties.MEDIA_RECORDER, prefix + "_" + Columns.RECORDER));
    }

    @Override
    protected String getCacheQuery() {
        return "SELECT * FROM " + type + " WHERE ecm:parentId='" + documents.get("Resources").getId() + "' AND ecm:currentLifeCycleState != 'deleted'";
    }

}
