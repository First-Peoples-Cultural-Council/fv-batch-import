/**
 *
 */
package mappers.firstvoices;

/**
 * @author dyona
 *
 */
public class AudioMapper extends BinaryMapper {

    public AudioMapper() {
        super("FVAudio","AUDIO_" + Columns.FILENAME, "AUDIO", Properties.RELATED_AUDIO);
    }

}
