/**
 *
 */
package mappers.firstvoices;

/**
 * @author dyona
 *
 */
public class VideoMapper extends BinaryMapper {

    public VideoMapper() {
        super("FVVideo", "VIDEO_" + Columns.FILENAME, "VIDEO", Properties.RELATED_VIDEOS);
    }

}
