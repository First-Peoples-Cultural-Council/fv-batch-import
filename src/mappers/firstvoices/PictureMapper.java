/**
 *
 */
package mappers.firstvoices;

/**
 * @author dyona
 *
 */
public class PictureMapper extends BinaryMapper {

    public PictureMapper() {
        super("FVPicture", "IMG_" + Columns.FILENAME, "IMG", Properties.RELATED_PICTURES);
    }

    public PictureMapper(int number) {
        super("FVPicture", "IMG_" + number +"_"+ Columns.FILENAME, "IMG_"+number, Properties.RELATED_PICTURES);
    }

}
