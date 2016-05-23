/**
 * Created by Morten on 23-05-2016.
 */
public class LocalhostEvent extends MyTextEvent implements Unlogable{

    private String localhostAddress;

    LocalhostEvent(int offset, int timeStamp, String sender, String localhostAddress) {
        super(offset, timeStamp, sender);
        this.localhostAddress = localhostAddress;
    }

    public String getLocalhostAddress() {
        return localhostAddress;
    }
}
