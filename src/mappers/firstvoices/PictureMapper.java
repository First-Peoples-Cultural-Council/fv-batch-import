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

}
