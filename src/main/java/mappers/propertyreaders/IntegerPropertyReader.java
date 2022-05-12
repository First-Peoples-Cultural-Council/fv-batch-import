/**
 *
 */

package mappers.propertyreaders;

import java.util.Locale;
import org.nuxeo.client.objects.Document;

public class IntegerPropertyReader extends PropertyReader {

  public IntegerPropertyReader(String key, Object reader) {
    super(key, reader);
  }

  protected void setProperty(Document doc, String property, String value) {
    Integer realValue = Integer.parseInt(value);

    System.out.println(
        "   * Setting value: '" + property + "' to " + value + " on doc '" + doc.getName()
            + "'");
    doc.setPropertyValue(property, realValue);
  }

  protected void setProperty(Document doc, String property, Object value) {
    setProperty(doc, property, value.toString());
  }
}
