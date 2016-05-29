import java.io.Serializable;

/**
 * Created by Morten on 23-05-2016.
 */
public class ConnectToEvent extends MyTextEvent implements Unlogable{

    private String newAddress;

    /*
     * Et MyTextEvent som bliver brugt til at få clients til at forbinde til en ny server.
     */
    ConnectToEvent(int offset, int timeStamp, String sender, String newAddress) {
        super(offset, timeStamp, sender);
        this.newAddress = newAddress;
    }

    public String getNewAddress() {
        return newAddress;
    }
}
