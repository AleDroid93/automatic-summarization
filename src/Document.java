import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

public class Document {
    private String title;
    private HashMap<Integer, HashMap<Integer, String>>  document;
    private int sentencesNumber;
    public Document() {
        this.title = "";
        this.document = new HashMap<>();
        this.sentencesNumber = 0;
    }

    public String getTitle() {
        return title;
    }

    public HashMap<Integer, HashMap<Integer, String>> getDocument() {
        return document;
    }

    public Document(String title) {
        this.title = title;
        this.document = new HashMap<>();
        this.sentencesNumber = 0;   
    }


    public Document(String title, HashMap<Integer, HashMap<Integer, String>> document) {
        this.title = title;
        this.document = document;
        Set<Integer> numParagraphs = document.keySet();
        Iterator iterator = numParagraphs.iterator();
        this.sentencesNumber = 0;    
        while(iterator.hasNext()){
            HashMap<Integer, String> sentences = document.get(iterator.next());
            this.sentencesNumber += sentences.size();
        }
    }

    public ArrayList<String> getAllSentences(){
        ArrayList<String> sentences = new ArrayList<>();
        Set<Integer> keys = document.keySet();
        Iterator<Integer> it = keys.iterator();
        while(it.hasNext()){
            int indexPar = it.next();
            Set<Integer> keySentences = document.get(indexPar).keySet();
            Iterator<Integer> itSentences = keySentences.iterator();
            while(itSentences.hasNext()){
                int indexSentence = itSentences.next();
                sentences.add(document.get(indexPar).get(indexSentence));
            }
        }
        return sentences;
    }


    public ArrayList<String> getSentencesFromParagraph(Integer paragraphNumber){
        ArrayList<String> sentences = new ArrayList<>();
        HashMap<Integer, String> paragraph = document.get(paragraphNumber);
        Set<Integer> keys = paragraph.keySet();
        Iterator<Integer> itSentences = keys.iterator();
        while(itSentences.hasNext()) {
            int indexSentence = itSentences.next();
            sentences.add(paragraph.get(indexSentence));
        }
        return sentences;
    }

    public void removeSentence(int indexParagraph, int indexSentence){
        String removedSentence = document.get(indexParagraph).remove(indexSentence);
        // System.err.println("Frase eliminata: "+ removedSentence);
        this.sentencesNumber--;
    }

    public void addItem(int indexParagraph, HashMap<Integer,String> paragraphContent){
        document.put(indexParagraph, paragraphContent);
        this.sentencesNumber += paragraphContent.size();
    }

    public int getSentencesNumber() {
        return sentencesNumber;
    }

    public void setTitle(String title) {
        this.title = title;
    }


}
