/**
 *
 */
package mappers;

import common.ConsoleLogger;
import mappers.firstvoices.Columns;
import mappers.firstvoices.Properties;
import mappers.propertyreaders.PropertyReader;
import mappers.propertyreaders.SimpleListPropertyReader;
import org.nuxeo.client.NuxeoClient;
import org.nuxeo.client.objects.Document;
import org.nuxeo.client.objects.Documents;
import reader.AbstractReader;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

/**
 * @author loopingz
 *
 */
public abstract class CsvMapper {
	/**
	 * Complete the document model from those property
	 */
	protected LinkedHashSet<PropertyReader> propertyReaders = new LinkedHashSet<>();
	/**
	 * Use to create other documents from the same CSV line
	 */
	protected LinkedHashSet<CsvMapper> subdocuments = new LinkedHashSet<CsvMapper>();
	/**
	 * Will contains the documents created during process
	 */
	protected static Map<String, Document> documents = null;
	/**
	 * Nuxeo client
	 */
	protected NuxeoClient client = null;
	public static Integer createdObjects = 0;
	public static Integer createdWords = 0;
	/**
	 * The column to map from
	 * Can be Integer or String
	 */
	protected Object column = 0;
	/**
	 * Type of document to create
	 */
	protected String type = "";
	/**
	 * Number of object created
	 */
	protected Integer objectsCount = 0;
	/**
	 * Where the document should be created
	 */
	protected String parentKey = "Dictionary";
	/**
	 * Fake the creation of the document
	 */
	protected Boolean fakeCreation = false;

	protected AbstractReader csvReader = null;

	protected static String tag;

	protected static String globalUsername;

	public void setDialectID(String dialectID) {
		this.dialectID = dialectID;
	}

	public static String getDialectID() {
		return dialectID;
	}

	protected static String dialectID;

	/**
	 * By default not caching anything
	 * @param doc to cache
	 */
	protected void cacheDocument(Document doc) {
		return;
	}

	public Boolean getFakeCreation() {
		return fakeCreation;
	}

	/**
	 * Dont cache by default
	 * @throws IOException
	 */
	public void buildCache() throws IOException {

	}

	protected boolean preCreate() {
	    return false;
	}
	/**
	 * Get the document from the cache
	 * @param doc
	 * @return null if not cached
	 */
	protected Document getFromCache(Document doc) {
		return null;
	}

	public void setFakeCreation(Boolean fakeCreation) {
		this.fakeCreation = fakeCreation;
	}

	protected String getId(String id) {
		return id.replace("/", "_");
	}

	public Document process(Map<String,Document> docs, NuxeoClient client, AbstractReader reader) throws IOException {
	    documents = new HashMap<String, Document>();
	    for (Entry<String, Document> entry : docs.entrySet()) {
            documents.put(entry.getKey(), entry.getValue());
        }
	    Document doc = process(docs, client, reader, 1);
	    return doc;
	}

	protected Boolean skipValue(String value) {
		return value.isEmpty();
	}

	protected Document tagAndUpdateCreator(Document resultDoc, Document localDoc) throws IOException{
		// Tag created document
		Document newResult = client.operation("Services.TagDocument").input(resultDoc).param("tags", getTag()).execute();

//		// Set username
//		String username = (String) localDoc.getDirtyProperties().get(Properties.CREATOR);
//		ArrayList<String> contributors = (ArrayList<String>) localDoc.getDirtyProperties().get(Properties.CONTRIBUTORS);
//
//		// Update created document with creator / lastContributor (has to happen after creation)
//		if (username != null) {
//			resultDoc.setPropertyValue(Properties.CREATOR, username);
//		}
//
//		if (contributors != null) {
//			resultDoc.setPropertyValue(Properties.CONTRIBUTORS, contributors);
//		}

		// Update creator and contributors fields
		if (!resultDoc.getDirtyProperties().isEmpty()) {
			newResult = (Document) client.operation("Document.Update").input(resultDoc).param("properties", resultDoc.getDirtyProperties()).execute();
		}

		return newResult;
	}

	protected Document createDocument(Document doc, Integer depth) throws IOException {
		Document result = getFromCache(doc);

		System.out.println("Creating Document of type '" + doc.getType() + "': " + doc.getName());

		if (!fakeCreation && result == null) {

			result = Document.createWithName(doc.getName(), doc.getType());
			result.setProperties(doc.getProperties());
			result = client.repository().schemas("").createDocumentByPath(documents.get(parentKey).getPath(), result);

			tagAndUpdateCreator(result, doc);

			createdObjects++;
            if (doc.getType().endsWith("Word")) {
                createdWords++;
            }
			cacheDocument(result);
		}
		if (depth == 1) {
			documents.put("main", result);
		}
		return result;
	}

	protected void publishDocument(Document doc) throws IOException {
		//doc.setPropertyValue("ecm:currentLifeCycleState", "Published");
		//Document publishedDoc = (Document) client.newRequest("Document.Publish").input(doc).set("target", parent).execute();

		try {
			client.operation("Document.FollowLifecycleTransition")
			.input(doc)
			.param("value", "Publish")
			.execute();

		} catch (Exception e) {
			System.out.println(e.getCause());
		}
	}

	protected Document enableDocument(Document doc) throws IOException {
		Document enabledDoc = (Document) client.operation("FVEnableDocument").input(doc).execute();
		return enabledDoc;
	}

	protected Document disableDocument(Document doc) throws IOException {
		Document disabledDoc = (Document) client.operation("FVDisableDocument").input(doc).execute();
		return disabledDoc;
	}

	protected Document setDocumentState(Document doc) throws IOException {
		// Document is enabled
		if(doc.getPropertyValue("fvl:status_id") != null && doc.getPropertyValue("fvl:status_id").equals("1")) {
			if(doc.getState().equalsIgnoreCase("New") || doc.getState().equalsIgnoreCase("Disabled")) {
				doc = enableDocument(doc);
			}
		}
		// Document is disabled
		else if(doc.getPropertyValue("fvl:status_id") != null && doc.getPropertyValue("fvl:status_id").equals("2")) {
			if(doc.getState().equalsIgnoreCase("New") || doc.getState().equalsIgnoreCase("Disabled")) {
				doc = enableDocument(doc);
			}
			doc = disableDocument(doc);
		}
		return doc;
	}

	public Set<PropertyReader> getPropertyReaders() {
	    return propertyReaders;
	}

	protected Document process(Map<String,Document> docs, NuxeoClient client, AbstractReader reader, Integer depth) throws IOException {

		if (getCSVReader() == null) {
			setCSVReader(reader);
		}

	    this.client = client;
		if (skipValue(reader.getString(column))) {
			return null;
		}
		if (!documents.containsKey(parentKey)) {
			throw new RuntimeException("Need a parent to create doc");
		}
		buildCache();
		objectsCount++;
		// Display message
		ConsoleLogger.out(type + "-" + column);

		Document doc = Document.createWithName(getId(reader.getString(column)), type);
		ConsoleLogger.increaseDepth();
		for (PropertyReader propertyReader : propertyReaders) {
			propertyReader.read(doc, reader);
		}
		Document currentDoc = documents.get("current");
		for (CsvMapper mapper : subdocuments) {
		    if (!mapper.preCreate()) {
		        continue;
		    }
            documents.put("current", doc);
            mapper.process(docs, client, reader, depth + 1);
        }
		documents.put("current", currentDoc);
		doc = createDocument(doc, depth);

		for (CsvMapper mapper : subdocuments) {
		    if (mapper.preCreate()) {
                continue;
            }
			documents.put("current", doc);
			mapper.process(docs, client, reader, depth + 1);
		}
		documents.put("current", currentDoc);
//		doc = updateDocument(doc, depth);
		ConsoleLogger.decreaseDepth();
		return doc;
	};

	protected Document updateDocument(Document doc, Integer depth) throws IOException {
        return null;
    }

    protected void loadCache(String query) throws IOException {
		Integer page = 0;
		Integer pageSize = 1000;
		System.out.println("Loading cache...");
		while (true) {
			Documents docs = client.schemas("*").operation("Repository.Query")
					.param("query", query)
					.param("currentPageIndex", page)
					.param("pageSize", pageSize)
					.execute();

			for (int i = 0; i < docs.size(); i++) {
				cacheDocument(docs.getDocument(i));
			}
			if (docs.size() < pageSize) {
				break;
			}
			page++;
		}
		System.out.println("Caching Complete.");
	}

	protected CsvMapper(String type, Object column) {
        this.type = type;
	    this.column = column;

	    // Set some default properties
		String[] usernameCols = {Columns.USERNAME, Columns.USERNAME + "_2", Columns.USERNAME + "_3", Columns.USERNAME + "_4", Columns.USERNAME + "_5"};
		propertyReaders.add(new PropertyReader(Properties.CREATOR, Columns.USERNAME));
		propertyReaders.add(new SimpleListPropertyReader(Properties.CONTRIBUTORS, usernameCols));
	}

	protected AbstractReader getCSVReader() {
		return csvReader;
	}

	protected void setCSVReader(AbstractReader reader) {
		csvReader = reader;
	}

	public String getTag() {
		return tag;
	}

	public static void setTag(String tag) {
		CsvMapper.tag = tag;
	}

	public String getGlobalUsername() {
		return globalUsername;
	}

	public static void setGlobalUsername(String username) {
		globalUsername = username;
	}


}
