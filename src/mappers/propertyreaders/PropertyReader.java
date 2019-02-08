/**
 *
 */
package mappers.propertyreaders;

import org.nuxeo.ecm.automation.client.model.Document;

import org.nuxeo.ecm.automation.client.model.PropertyList;
import reader.AbstractReader;

/**
 * @author loopingz
 *
 */
public class PropertyReader {
	protected Object column;
	protected String key;

	public PropertyReader(String key, Object column) {
		this.column = column;
		this.key = key;
	}

    public String getKey() {
        return key;
    }

	public String getValue(AbstractReader reader) {
		return reader.getString(column);
	}

	protected void setProperty(Document doc, String property, String value) {
		if (value == null || value.isEmpty()) {
			return;
		}
		System.out.println("Setting value: '" + property + "' to '" + value + "' on doc '" + doc.getId() + "'");
		doc.set(property, value);

	}

	public String getJsonValue(AbstractReader reader) {
		return "\"" + getValue(reader).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	public void read(Document document, AbstractReader reader) {
		setProperty(document, key, getValue(reader));
	}
}
