
public class TextRemoveEvent extends MyTextEvent {

    private int length;

    public TextRemoveEvent(int offset, int length, int timeStamp) {
        super(offset, timeStamp);
        this.length = length;
    }

    public int getLength() { return length; }
}