/**
 *
 */
package mappers.propertyreaders;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import reader.AbstractReader;

/**
 * @author loopingz
 * Read/Set array of property
 */
public class MultiValuedReader extends JsonPropertyReader {

	protected LinkedHashSet<PropertyReader> readers = null;

	public MultiValuedReader(String key, PropertyReader reader) {
		super(key, 0);
		readers = new LinkedHashSet<>();
		if (reader != null) {
			readers.add(reader);
		}
	}
	public MultiValuedReader(String key, LinkedHashSet<PropertyReader> readers) {
		super(key, 0);
		this.readers = readers;
	}

	@Override
	public String getJsonValue(AbstractReader reader) {
		String result = "[";
		Boolean first = true;
		for (PropertyReader propertyReader : readers) {
		    String value = propertyReader.getJsonValue(reader);
		    if (value == null || value.isEmpty() || value.equals("\"\"")) {
		        continue;
		    }
			if (first) {
				first = false;
			} else {
				result += ",";
			}
			result += value;
		}
		result += "]";
		return result;
	}
}
