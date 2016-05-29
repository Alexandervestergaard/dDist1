/**
 * Created by Morten on 23-05-2016.
 */
public class LocalhostEvent extends MyTextEvent implements Unlogable{

    private String localhostAddress;

    /*
     * Et event der bliver sendt som det første når en client forbinder sig til en server.
     * Muliggør elections.
     */
    LocalhostEvent(int offset, int timeStamp, String sender, String localhostAddress) {
        super(offset, timeStamp, sender);
        this.localhostAddress = localhostAddress;
    }

    public String getLocalhostAddress() {
        return localhostAddress;
    }
}
