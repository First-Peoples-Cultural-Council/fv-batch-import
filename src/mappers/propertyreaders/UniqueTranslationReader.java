/**
 *
 */
package mappers.propertyreaders;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author loopingz
 *
 */
public class UniqueTranslationReader extends TranslationReader {
	public UniqueTranslationReader(String language, Object column) {
		super("fv:literal_translation", (PropertyReader) null);
		LinkedHashSet<PropertyReader> set = new LinkedHashSet<>();
		set.add(new PropertyReader("translation", column));
		set.add(new StaticPropertyReader("language", language));
		readers.add(new ComplexPropertyReader(column, set));
	}
}
