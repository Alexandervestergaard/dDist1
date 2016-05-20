/**
 *
 * @author Jesper Buus Nielsen
 *
 */
public class TextInsertEvent extends MyTextEvent {

    private String text;

    public TextInsertEvent(int offset, String text, int timeStamp, String sender) {
        super(offset, timeStamp, sender);
        this.text = text;
    }

    @Override
    public int compareTo(MyTextEvent o) {
        if (o instanceof TextInsertEvent){
            if (super.getTimeStamp() == o.getTimeStamp()){
                if (this.getText() != null && ((TextInsertEvent) o).getText() != null) {
                    return this.getText().compareTo(((TextInsertEvent) o).getText());
                }
            }
        }
        return super.compareTo(o);
    }

    public String getText() { return text; }
}

