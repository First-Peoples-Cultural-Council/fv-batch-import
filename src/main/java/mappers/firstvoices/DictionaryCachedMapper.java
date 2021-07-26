package mappers.firstvoices;

import static mappers.CsvMapper.UpdateStrategy.ALLOW_DUPLICATES;

import common.ConsoleLogger;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import mappers.CsvMapper;
import org.nuxeo.client.objects.Document;

/**
 * @author dyona
 *
 */
public abstract class DictionaryCachedMapper extends CsvMapper {

  protected static Map<String, Map<String, Document>> cache = null;
  protected String prefix = "";
  protected String currentCacheId = null;
  protected String cacheProperty = Properties.TITLE;

  protected DictionaryCachedMapper(String type, Object column) {
    super(type, column);
  }

  protected abstract String getCacheQuery();

  protected String getCachedProperty(Document doc, boolean fromDirty) {
    if (fromDirty) {
      return (String) doc.getDirtyProperties().get(cacheProperty);
    }
    return doc.getPropertyValue(cacheProperty);
  }

  @Override
  protected boolean preCreate() {
    return true;
  }

  @Override
  protected Document getFromCache(Document doc) {
    if (updateStrategy.equals(ALLOW_DUPLICATES)) {
      return null;
    }

    currentCacheId = getInstanceCacheKey();
    if (!cache.containsKey(currentCacheId)) {
      return null;
    }
    String cacheKey = getCachedProperty(doc, true);
    if (cacheKey != null && cache.get(currentCacheId).containsKey(cacheKey)) {
      return cache.get(currentCacheId).get(cacheKey);
    }
    return null;
  }

  protected String getInstanceCacheKey() {
    return getClass().getName() + documents.get("Dictionary").getId();
  }

  @Override
  protected void cacheDocument(Document doc) {
    if (!updateStrategy.equals(ALLOW_DUPLICATES)) {
      cache.get(getInstanceCacheKey()).put(getCachedProperty(doc, false), doc);
    }
  }

  @Override
  public void buildCache() throws IOException {
    if (updateStrategy.equals(ALLOW_DUPLICATES)) {
      return;
    }

    if (cache == null) {
      cache = new HashMap<String, Map<String, Document>>();
    }
    if (cache.containsKey(getInstanceCacheKey())) {
      return;
    }
    cache.put(getInstanceCacheKey(), new HashMap<String, Document>());
    loadCache(getCacheQuery());
    ConsoleLogger
        .out("Caching " + cache.get(getInstanceCacheKey()).size() + " " + getInstanceCacheKey());
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

}
