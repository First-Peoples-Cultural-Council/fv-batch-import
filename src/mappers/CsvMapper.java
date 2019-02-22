/**
 *
 */
package mappers;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import mappers.firstvoices.Columns;
import mappers.firstvoices.Properties;
import mappers.propertyreaders.SimpleListPropertyReader;
import org.nuxeo.ecm.automation.client.Constants;
import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.model.Document;
import org.nuxeo.ecm.automation.client.model.Documents;

import common.ConsoleLogger;
import mappers.propertyreaders.PropertyReader;
import reader.AbstractReader;

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
	 * Nuxeo session
	 */
	protected Session session = null;
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
	 * @param docs
	 * @param session
	 * @throws IOException
	 */
	public void buildCache() throws IOException {

	}

	protected boolean preCreate() {
	    return false;
	}
	/**
	 * Get the document from the cache
	 * @param cache
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

	public Document process(Map<String,Document> docs, Session session, AbstractReader reader) throws IOException {
	    documents = new HashMap<String, Document>();
	    for (Entry<String, Document> entry : docs.entrySet()) {
            documents.put(entry.getKey(), entry.getValue());
        }
	    Document doc = process(docs, session, reader, 1);
	    return doc;
	}

	protected Boolean skipValue(String value) {
		return value.isEmpty();
	}

	protected Document tagAndUpdateCreator(Document resultDoc, Document localDoc) throws IOException{
		// Tag created document
		session.newRequest("Services.TagDocument").setInput(resultDoc).set("tags", getTag()).execute();

		// Set username
		String username = localDoc.getDirties().getString(Properties.CREATOR);
		String contributors = localDoc.getDirties().getString(Properties.CONTRIBUTORS);

		// Update created document with creator / lastContributor (has to happen after creation)
		resultDoc.set(Properties.CREATOR, (username != null) ? username : getGlobalUsername());
		resultDoc.set(Properties.CONTRIBUTORS, (contributors != null) ? contributors : getGlobalUsername());

		// Update creator and contributors fields
		Document newResult = (Document) session.newRequest("Document.Update").setInput(resultDoc).set("properties", resultDoc).execute();

		return newResult;
	}

	protected Document createDocument(Document doc, Integer depth) throws IOException {
		Document result = getFromCache(doc);

		System.out.println("Creating Document of type '" + doc.getType() + "': " + doc.getId());

		if (!fakeCreation && result == null) {
			result = (Document) session.newRequest("Document.Create").setHeader(Constants.HEADER_NX_SCHEMAS, "*")
				.setInput(documents.get(parentKey)).set("type", doc.getType()).set("name", doc.getId())
				.set("properties", doc).execute();

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
		//doc.set("ecm:currentLifeCycleState", "Published");
		//Document publishedDoc = (Document) session.newRequest("Document.Publish").setInput(doc).set("target", parent).execute();

		try {
			session.newRequest("Document.FollowLifecycleTransition")
			.setInput(doc)
			.set("value", "Publish")
			.execute();

		} catch (Exception e) {
			System.out.println(e.getCause());
		}
	}

	protected Document enableDocument(Document doc) throws IOException {
		Document enabledDoc = (Document) session.newRequest("FVEnableDocument").setInput(doc).execute();
		return enabledDoc;
	}

	protected Document disableDocument(Document doc) throws IOException {
		Document disabledDoc = (Document) session.newRequest("FVDisableDocument").setInput(doc).execute();
		return disabledDoc;
	}

	protected Document setDocumentState(Document doc) throws IOException {
		// Document is enabled
		if(doc.getString("fvl:status_id") != null && doc.getString("fvl:status_id").equals("1")) {
			if(doc.getState().equalsIgnoreCase("New") || doc.getState().equalsIgnoreCase("Disabled")) {
				doc = enableDocument(doc);
			}
		}
		// Document is disabled
		else if(doc.getString("fvl:status_id") != null && doc.getString("fvl:status_id").equals("2")) {
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

	protected Document process(Map<String,Document> docs, Session session, AbstractReader reader, Integer depth) throws IOException {

		if (getCSVReader() == null) {
			setCSVReader(reader);
		}

	    this.session = session;
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

		Document doc = new Document(getId(reader.getString(column)), type);
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
            mapper.process(docs, session, reader, depth + 1);
        }
		documents.put("current", currentDoc);
		doc = createDocument(doc, depth);

		for (CsvMapper mapper : subdocuments) {
		    if (mapper.preCreate()) {
                continue;
            }
			documents.put("current", doc);
			mapper.process(docs, session, reader, depth + 1);
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
			Documents docs = (Documents) session.newRequest("Repository.Query").setHeader(
			        Constants.HEADER_NX_SCHEMAS, "*")
					.set("query", query)
					.set("currentPageIndex", page).set("pageSize", pageSize).execute();
			for (int i = 0; i < docs.size(); i++) {
				cacheDocument(docs.get(i));
			}
			if (docs.size() < pageSize) {
				break;
			}
			page++;
		}
		//System.out.println("Caching Complete.");
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
