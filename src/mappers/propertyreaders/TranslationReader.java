/**
 *
 */
package mappers.propertyreaders;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import mappers.firstvoices.DominantLanguageReader;
import reader.AbstractReader;

/**
 * @author loopingz
 *
 */
public class TranslationReader extends MultiValuedReader {
    public TranslationReader(String key, PropertyReader reader) {
        super(key, reader);
    }

    public TranslationReader(String key, Object languageColumn, Object valueColumn) {
        super(key, (PropertyReader) null);

        LinkedHashSet<PropertyReader> set = new LinkedHashSet<>();
        set.add(new PropertyReader("translation", valueColumn));
        set.add(new DominantLanguageReader(languageColumn));

        readers.add(new ComplexPropertyReader(valueColumn, set));
    }

    public TranslationReader(String key, Object languageColumn, Object[] valueColumns) {
        super(key, (PropertyReader) null);

        for (Object valueColumn : valueColumns) {
            LinkedHashSet<PropertyReader> set = new LinkedHashSet<>();
            set.add(new PropertyReader("translation", valueColumn));
            set.add(new DominantLanguageReader(languageColumn));

            readers.add(new ComplexPropertyReader(valueColumn, set));
        }
    }
}
