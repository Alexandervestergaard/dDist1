
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

    /*
     * En metode der sætter removedTekst variablen. Denne variable skal bruger hvis eventet skal fortrydes.
     */
    public void setRemovedText(String removedText) {
            this.removedText = removedText;
    }
}