/**
 *
 */
package mappers.inuktitut;

import mappers.propertyreaders.PropertyReader;
import reader.AbstractReader;

import java.util.Arrays;

/**
 * @author loopingz
 *
 */
public class AttachmentPropertyReader extends PropertyReader {

	@Override
    public String getValue(AbstractReader reader) {
		String value = (String) super.getValue(reader);
		if (value == null) {
			return value;
		}
		String[] dict = {"singular", "dual", "plural", "None"};
		for (String key : dict) {
			if (value.contains(key)) {
				return key;
			}
		}
		throw new RuntimeException("Should contain values from : " + String.join(",", Arrays.asList(dict)));
	}

	public AttachmentPropertyReader(String key, Object column) {
		super(key, column);
		// TODO Auto-generated constructor stub
	}

}
