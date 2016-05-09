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

    @Override
    public int compareTo(MyTextEvent o) {
        if (o instanceof TextInsertEvent){
            if (super.getTimeStamp() == o.getTimeStamp()){
                if (this.getText() != null && ((TextInsertEvent) o).getText() != null) {
                    return this.getText().compareTo(((TextInsertEvent) o).getText());
                }
            }
            if (super.getTimeStamp() > o.getTimeStamp()){
                return 1;
            }
            if (super.getTimeStamp() < o.getTimeStamp()){
                return -1;
            }
        }
        return super.compareTo(o);
    }

    public String getText() { return text; }
}

