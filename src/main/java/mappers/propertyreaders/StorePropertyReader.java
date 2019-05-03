/**
 *
 */
package mappers.propertyreaders;

import reader.AbstractReader;
import org.nuxeo.client.objects.Document;

/**
 * @author dyona
 *
 */
public class StorePropertyReader extends PropertyReader {
    private String cacheValue = null;

    public StorePropertyReader(Object column) {
        super(null, column);
    }

    @Override
    public void read(Document document, AbstractReader reader) {
        cacheValue = (String) getValue(reader);
    }

    public String getCacheValue() {
        return cacheValue;
    }

}
