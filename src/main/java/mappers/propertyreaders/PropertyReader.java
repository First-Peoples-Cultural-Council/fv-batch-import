package mappers.propertyreaders;

import org.nuxeo.client.objects.Document;
import reader.AbstractReader;

/**
 * @author loopingz
 *
 */
public class PropertyReader {

  protected Object column;
  protected String key;
  protected boolean ignoreCase = false;
  protected boolean stripNewLines = false;

  public PropertyReader(String key, Object column) {
    this.column = column;
    this.key = key;
  }

  public PropertyReader(String key, Object column, boolean ignoreCase) {
    this.column = column;
    this.key = key;
    this.ignoreCase = ignoreCase;
  }

  public PropertyReader(String key, Object column, boolean ignoreCase, boolean stripNewLines) {
    this.column = column;
    this.key = key;
    this.ignoreCase = ignoreCase;
    this.stripNewLines = stripNewLines;
  }

  public String getKey() {
    return key;
  }

  public Object getValue(AbstractReader reader) {
    String value = reader.getString(column);

    if (ignoreCase) {
      value = value.toLowerCase();
    }

    if (stripNewLines) {
      value = value.replace("<br />", "");
    }

    return value;
  }

  protected void setProperty(Document doc, String property, String value) {
    if (value == null || value.isEmpty()) {
      return;
    }
    System.out.println(
        "   * Setting value: '" + property + "' to '" + value + "' on doc '" + doc.getName()
            + "'");
    doc.setPropertyValue(property, value);
  }

  protected void setProperty(Document doc, String property, Object value) {
    if (value == null || value.toString().isEmpty()) {
      return;
    }
    System.out.println(
        "   * Setting value: '" + property + "' to '" + value.toString() + "' on doc '" + doc
            .getName() + "'");
    doc.setPropertyValue(property, value);
  }

  public String getJsonValue(AbstractReader reader) {
    return "\"" + ((String) getValue(reader)).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  public void read(Document document, AbstractReader reader) {
    setProperty(document, key, getValue(reader));
  }
}
