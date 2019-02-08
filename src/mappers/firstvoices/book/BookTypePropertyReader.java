package mappers.firstvoices.book;

import mappers.propertyreaders.PropertyReader;
import reader.AbstractReader;

public class BookTypePropertyReader extends PropertyReader {

    @Override
    public String getValue(AbstractReader reader) {
        switch (Integer.valueOf(reader.getString(column))) {
        case 1:
            return "song";
        case 2:
            return "story";
        default:
            System.out.println("Could not retrieve Nuxeo book type value");
            return "";
        }
    }

    public BookTypePropertyReader(String key, Object column) {
        super(key, column);
    }

}
