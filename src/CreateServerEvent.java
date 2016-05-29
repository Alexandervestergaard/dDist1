/**
 * Created by Morten on 23-05-2016.
 */
public class CreateServerEvent extends MyTextEvent implements Unlogable{

    /*
     * Et MyTextEvent som bliver brugt til at få clients til at lave en server
     */
    CreateServerEvent(int offset, int timeStamp, String sender) {
        super(offset, timeStamp, sender);
    }
}
