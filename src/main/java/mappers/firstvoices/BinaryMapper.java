package mappers.firstvoices;

import mappers.propertyreaders.PropertyReader;
import mappers.propertyreaders.StorePropertyReader;
import org.nuxeo.client.objects.Document;
import org.nuxeo.client.objects.Documents;
import org.nuxeo.client.objects.blob.FileBlob;
import org.nuxeo.client.spi.NuxeoClientException;
import reader.AbstractReader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        ArrayList<String> binaryIds = new ArrayList<String>();

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
                doc.setPropertyValue(Properties.TITLE, file.getName());
            }

            // Set some defaults for binary documents if they are not defined

            if (doc.getDirtyProperties().get(Properties.MEDIA_SHARED) == null) {
                doc.setPropertyValue(Properties.MEDIA_SHARED, false);
            }

            if (doc.getDirtyProperties().get(Properties.CHILD_FOCUSED) == null) {
                doc.setPropertyValue(Properties.CHILD_FOCUSED, false);
            }

            try {
                binaryDoc = super.createDocument(doc, depth);
            } catch (NuxeoClientException e) {
                e.printStackTrace();
            }

			// Set the document state based on the fvl:status_id
			//binaryDoc = setDocumentState(binaryDoc);

            FileBlob fb = new FileBlob(file);

            client.operation("Blob.Attach")
                .voidOperation(true)
                .input(fb)
                .param("document", binaryDoc).execute();
        }

        if(getPrefix().matches(".*\\d+.*")){
            Pattern r = Pattern.compile("(.*)(\\d+)(.*)");
            Matcher m = r.matcher(getPrefix());
            int match=0;
            if(m.matches()) {
                match = Integer.parseInt(m.group(2));
            }
            String qu = "SELECT * FROM Document WHERE ecm:primaryType='"+binaryDoc.getType()+"' AND ecm:isTrashed = 0 ORDER BY dc:created DESC";
            Documents ques = client.operation("Repository.Query").schemas("*")
                    .param("query", qu)
                    .execute();
            String id_str = binaryDoc.getId();
            for(int i=1;i<match; i++){
                binaryIds.add(ques.getDocument(i).getId());
            }
        }
        else {
            binaryIds.add(binaryDoc.getId());
        }

        documents.get("current").setPropertyValue(linkKey, binaryIds);

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
        return "SELECT * FROM " + type + " WHERE ecm:parentId='" + documents.get("Resources").getId() + "' AND ecm:isTrashed = 0";
    }

}