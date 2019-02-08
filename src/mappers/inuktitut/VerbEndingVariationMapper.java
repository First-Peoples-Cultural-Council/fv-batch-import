/**
 *
 */
package mappers.inuktitut;

import java.io.IOException;
import org.nuxeo.ecm.automation.client.model.Document;

import mappers.CsvMapper;
import mappers.propertyreaders.PropertyReader;

/**
 * @author loopingz
 *
 */
public class VerbEndingVariationMapper extends CsvMapper {


	@Override
	protected Document createDocument(Document doc, Integer depth)
			throws IOException {
		doc.set("inmorphem:kqt_variation", documents.get("main").getId());
		return super.createDocument(doc, depth);
	}

	protected VerbEndingVariationMapper(Integer column) {
		super("INVerbEnding", column);
		propertyReaders.add(new PropertyReader("dc:title", column));
		propertyReaders.add(new PropertyReader("inmorphem:morphology", column+1));
		propertyReaders.add(new PropertyReader("inmorphem:morphology_notes", column+2));

		subdocuments.add(new SampleMapper(column+3));
		subdocuments.add(new SampleMapper(column+6));
		subdocuments.add(new SampleMapper(column+9));
	}


}
