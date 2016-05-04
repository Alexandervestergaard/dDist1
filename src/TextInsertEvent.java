/**
 *
 * @author Jesper Buus Nielsen
 *
 */
public class TextInsertEvent extends MyTextEvent {

    private String text;

    public TextInsertEvent(int offset, String text, int timeStamp) {
        super(offset, timeStamp);
        this.text = text;
    }
    public String getText() { return text; }
}

