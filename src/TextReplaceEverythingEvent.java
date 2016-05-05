/**
 * Created by Leander on 05-05-2016.
 */
public class TextReplaceEverythingEvent extends MyTextEvent {

    private String text;

    public TextReplaceEverythingEvent(String text, int timeStamp) {

            super(0, timeStamp);
            this.text = text;
        }
        public String getText() {
            return text;
        }
    }

