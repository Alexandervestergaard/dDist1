import java.io.Serializable;

/**
 *
 * @author Jesper Buus Nielsen
 *
 */
public class MyTextEvent implements Serializable, Comparable<MyTextEvent> {
    private final int offset;
    private int timeStamp;
    //Et ID som bliver sendt fra texteditoren
    private final String sender;

    MyTextEvent(int offset, int timeStamp, String sender) {
        this.offset = offset;
        this.timeStamp = timeStamp;
        this.sender = sender;
    }
    int getOffset() { return offset; }

    @Override
    public int compareTo(MyTextEvent o) {
        if (o.getTimeStamp() < this.timeStamp) {
            return 1;
        }
        else if (o.getTimeStamp() == this.timeStamp) {
            return 0;
        }
        else{
            return -1;
        }
    }

    public int getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(int timeStamp) {
        this.timeStamp = timeStamp;
    }

    public String getSender() {
        return sender;
    }
}
