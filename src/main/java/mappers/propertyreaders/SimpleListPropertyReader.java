/**
 *
 */
package mappers.propertyreaders;

import java.util.ArrayList;
import org.nuxeo.client.objects.Document;
import reader.AbstractReader;

/**
 * @author loopingz
 *
 */
public class SimpleListPropertyReader extends PropertyReader {

  protected String[] columns;

  public SimpleListPropertyReader(String key, String[] columns) {
    super(key, columns[0]);
    this.columns = columns;
  }

  public ArrayList<Object> getValue(AbstractReader reader) {

    ArrayList<Object> values = new ArrayList<>();

    for (int i = 0; i < columns.length; ++i) {
      String value = reader.getString(columns[i]).replace(",", "\\,");

      if (value != null && !value.isEmpty() && !value.equals("")) {
        values.add(value);
      }
    }

    return values;
  }
//
//    public String getJsonValue(AbstractReader reader) {
//
//        return getValue(reader);
//    }

  public void read(Document document, AbstractReader reader) {
    setProperty(document, key, getValue(reader));
  }
}
