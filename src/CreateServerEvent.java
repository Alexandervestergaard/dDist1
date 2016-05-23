/**
 * Created by Morten on 23-05-2016.
 */
public class CreateServerEvent extends MyTextEvent implements Unlogable{

    CreateServerEvent(int offset, int timeStamp, String sender) {
        super(offset, timeStamp, sender);
    }
}
