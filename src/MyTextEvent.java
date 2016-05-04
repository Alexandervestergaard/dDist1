import java.io.Serializable;

/**
 *
 * @author Jesper Buus Nielsen
 *
 */
public class MyTextEvent implements Serializable, Comparable<MyTextEvent> {
    private int offset;
    private int timeStamp;

    MyTextEvent(int offset, int timeStamp) {
        this.offset = offset;
        this.timeStamp = timeStamp;
    }
    int getOffset() { return offset; }

    @Override
    public int compareTo(MyTextEvent o) {
        if (o.getTimeStamp() > this.timeStamp) {
            return -1;
        }
        else{
            return 1;
        }
    }

    public int getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(int timeStamp) {
        this.timeStamp = timeStamp;
    }
}
