/*

 */

package mappers.firstvoices;

import static mappers.CsvMapper.UpdateStrategy.ALLOW_DUPLICATES;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import mappers.CsvMapper;
import mappers.propertyreaders.PropertyReader;
import mappers.propertyreaders.SimpleListPropertyReader;
import mappers.propertyreaders.TranslationReader;
import mappers.propertyreaders.TrueFalsePropertyReader;
import org.nuxeo.client.NuxeoClient;
import org.nuxeo.client.objects.Document;

/**
 * @author dyona
 *
 */
public class WordMapper extends CsvMapper {

  protected static Map<String, Document> cache = null;

  /**
   * This constructor is meant for testing
   * @param client
   */
  public WordMapper(NuxeoClient client) {
    super("FVWord", Columns.WORD);
    this.client = client;

    setupProperties(false);
  }

  public WordMapper(Boolean createCategoryPolicy) {
    super("FVWord", Columns.WORD);

    // Set policy on word mapper
    this.setCreateCategoryPolicy(createCategoryPolicy);

    // Pass policy to Category mapper
    setupProperties(createCategoryPolicy);
  }

  private void setupProperties(Boolean createCategoryPolicy) {

    String[] definitionCols = {
        Columns.DOMINANT_LANGUAGE_DEFINITION,
        Columns.DOMINANT_LANGUAGE_DEFINITION + "_2",
        Columns.DOMINANT_LANGUAGE_DEFINITION + "_3",
        Columns.DOMINANT_LANGUAGE_DEFINITION + "_4",
        Columns.DOMINANT_LANGUAGE_DEFINITION + "_5"};
    String[] literalTranslationCols = {
        Columns.DOMINANT_LANGUAGE_VALUE,
        Columns.DOMINANT_LANGUAGE_VALUE + "_2",
        Columns.DOMINANT_LANGUAGE_VALUE + "_3",
        Columns.DOMINANT_LANGUAGE_VALUE + "_4",
        Columns.DOMINANT_LANGUAGE_VALUE + "_5"};
    String[] culturalNoteCols = {
        Columns.CULTURAL_NOTE,
        Columns.CULTURAL_NOTE + "_2",
        Columns.CULTURAL_NOTE + "_3",
        Columns.CULTURAL_NOTE + "_4",
        Columns.CULTURAL_NOTE + "_5"};
    String[] pluralCols = {
        Columns.PLURAL,
        Columns.PLURAL + "_2",
        Columns.PLURAL + "_3",
        Columns.PLURAL + "_4",
        Columns.PLURAL + "_5"};
    String[] spellingCols = {
        Columns.ALTERNATE_SPELLING,
        Columns.ALTERNATE_SPELLING + "_2",
        Columns.ALTERNATE_SPELLING + "_3",
        Columns.ALTERNATE_SPELLING + "_4",
        Columns.ALTERNATE_SPELLING + "_5"};

    propertyReaders.add(new PropertyReader(
        Properties.TITLE, Columns.WORD));
    propertyReaders.add(new PartOfSpeechPropertyReader(
        Properties.PART_OF_SPEECH_ID, Columns.PART_OF_SPEECH));
    propertyReaders.add(new TranslationReader(
        Properties.TRANSLATION, Columns.DOMINANT_LANGUAGE, literalTranslationCols));
    propertyReaders.add(new TranslationReader(
        Properties.DEFINITION, Columns.DOMINANT_LANGUAGE, definitionCols));
    propertyReaders.add(new SimpleListPropertyReader(
        Properties.ALTERNATE_SPELLINGS, spellingCols));
    propertyReaders.add(new SimpleListPropertyReader(
        Properties.CULTURAL_NOTE, culturalNoteCols));
    propertyReaders.add(new SimpleListPropertyReader(
        Properties.PLURAL, pluralCols));
    propertyReaders.add(new PropertyReader(
        Properties.PHONETIC_INFO, Columns.PHONETIC_INFO));
    propertyReaders.add(new PropertyReader(
        Properties.ASSIGNED_USR_ID, Columns.ASSIGNED_USR_ID));
    propertyReaders.add(new PropertyReader(
        Properties.CHANGE_DTTM, Columns.CHANGE_DTTM));
    propertyReaders.add(new PropertyReader(
        Properties.IMPORT_ID, Columns.WORD_ID));
    propertyReaders.add(new PropertyReader(
        Properties.REFERENCE, Columns.REFERENCE));
    propertyReaders.add(new PropertyReader(
        Properties.ACKNOWLEDGEMENT, Columns.ACKNOWLEDGEMENT));
    propertyReaders.add(new PropertyReader(
        Properties.STATUS_ID, Columns.WORD_STATUS));
    propertyReaders.add(new TrueFalsePropertyReader(
        Properties.AVAILABLE_IN_CHILDRENS_ARCHIVE, Columns.AVAILABLE_IN_CHILDRENS_ARCHIVE));
    propertyReaders.add(new TrueFalsePropertyReader(
        Properties.AVAILABLE_IN_GAMES, Columns.AVAILABLE_IN_GAMES));

    // Set create category policy from word mapper
    CategoryMapper categoryMapper = new CategoryMapper();
    categoryMapper.setCreateCategoryPolicy(createCategoryPolicy);

    subdocuments.add(new RelatedPhraseMapper());
    subdocuments.add(categoryMapper);
    subdocuments.add(new SourcesMapper());
    subdocuments.add(new AudioMapper());
    subdocuments.add(new AudioMapper(2));
    subdocuments.add(new AudioMapper(3));
    subdocuments.add(new PictureMapper());
    subdocuments.add(new PictureMapper(2));
    subdocuments.add(new VideoMapper());
    subdocuments.add(new VideoMapper(2));

  }

  @Override
  protected Document createDocument(Document doc, Integer depth) throws IOException {
    Document result = getFromCache(doc);

    System.out.println("Creating Document of type '" + doc.getType() + "': " + doc.getName());

    if (!fakeCreation && result == null) {

      // Set some defaults for words if they are not defined

      if (doc.getDirtyProperties().get(Properties.AVAILABLE_IN_GAMES) == null) {
        doc.setPropertyValue(Properties.AVAILABLE_IN_GAMES, true);
      }

      if (doc.getDirtyProperties().get(Properties.AVAILABLE_IN_CHILDRENS_ARCHIVE) == null) {
        doc.setPropertyValue(Properties.AVAILABLE_IN_CHILDRENS_ARCHIVE, true);
      }

      result = Document.createWithName(doc.getName(), doc.getType());
      result.setProperties(doc.getProperties());
      result = client.repository().createDocumentById(documents.get(parentKey).getId(), result);

      tagAndUpdateCreator(result, doc);

      createdObjects++;
      createdWords++;

    } else if (updateStrategy.equals(UpdateStrategy.FILL_EMPTY)) {
      fillEmptyStrategy(doc, result);
    } else if (updateStrategy.equals(UpdateStrategy.DANGEROUS_OVERWRITE)) {
      dangerousOverwriteStrategy(doc, result);
    } else if (updateStrategy.equals(UpdateStrategy.OVERWRITE_AUDIO)) {
      overwriteAudioStrategy(doc, result);
    } else {
      throw new IOException("Skipped - Entry already exists in database.");
    }

    if (depth == 1) {
      documents.put("main", result);
    }
    return result;
  }

  @Override
  protected void cacheDocument(Document doc) {
    if (!updateStrategy.equals(ALLOW_DUPLICATES)) {
      cache.put(doc.getPropertyValue(Properties.TITLE), doc);
    }
  }

  @Override
  protected Document getFromCache(Document doc) {
    if (updateStrategy.equals(ALLOW_DUPLICATES)) {
      return null;
    }

    String cacheKey = doc.getPropertyValue("dc:title");
    if (cache.containsKey(cacheKey)) {
      return cache.get(cacheKey);
    }
    return null;
  }

  @Override
  public void buildCache() throws IOException {
    if (cache != null || updateStrategy.equals(ALLOW_DUPLICATES)) {
      return;
    }
    cache = new HashMap<String, Document>();
    String query =
        "SELECT * FROM FVWord WHERE ecm:isTrashed = 0 "
            + "AND ecm:isProxy = 0 "
            + "AND ecm:isVersion = 0 "
            + "AND fva:dialect = '" + getDialectID() + "' ORDER BY dc:created";
    loadCache(query);

  }
}
