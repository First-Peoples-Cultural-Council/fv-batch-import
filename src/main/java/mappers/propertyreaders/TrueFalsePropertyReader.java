/**
 *
 */

package mappers.propertyreaders;

import org.nuxeo.client.objects.Document;

import java.util.Locale;

/**
 * @author loopingz
 *
 */
public class TrueFalsePropertyReader extends PropertyReader {

  public TrueFalsePropertyReader(String key, Object reader) {
    super(key, reader);
  }

  protected void setProperty(Document doc, String property, String value) {
    boolean realValue = true;

    switch (value.toLowerCase(Locale.ROOT)) {
      case "false":
      case "0":
        realValue = false;
        break;
      default:
        realValue = true;
    }

    System.out.println(
        "   * Setting value: '" + property + "' to '" + realValue + "' on doc '" + doc.getName()
            + "'");
    doc.setPropertyValue(property, realValue);
  }

  protected void setProperty(Document doc, String property, Object value) {
    setProperty(doc, property, value.toString());
  }
}
