
public class TextRemoveEvent extends MyTextEvent {

    private int length;
    private String removedText;

    public TextRemoveEvent(int offset, int length, int timeStamp) {
        super(offset, timeStamp);
        this.length = length;
    }

    public int getLength() { return length; }

    public String getRemovedText() {
        return removedText;
    }

    public void setRemovedText(String removedText) {
            this.removedText = removedText;
    }
}