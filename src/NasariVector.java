import java.util.ArrayList;
import java.util.Iterator;

public class NasariVector {
    private String wikiTitle;
    private ArrayList<String> relatedWords;
    private final int vectorSize = 15;

    public NasariVector(String wikipageName) {
        this.wikiTitle = wikipageName.substring(0,1).toUpperCase() + wikipageName.substring(1).toLowerCase();
        // inizializzo la mappa delle parole correlate con un valore fittizio pari a 15 che è la dimensione adottata
        this.relatedWords = new ArrayList<>();
        //fillGhostVectorWords(this.wikiTitle);
    }

    public NasariVector(String wikiTitle, ArrayList<String> relatedWords) {
        this.wikiTitle = wikiTitle.substring(0,1).toUpperCase() + wikiTitle.substring(1).toLowerCase();
        this.relatedWords = relatedWords;
    }



    public ArrayList<String> getRelatedWords() {
        return relatedWords;
    }

    @Override
    public String toString() {
        String relatedw = "";
        for(String s : this.relatedWords){
            relatedw += s+" ";
        }
        return "NasariVector{" + wikiTitle +
                "; " + relatedw +
                '}';
    }

    /**
     * Computes the overlap dimension between two Nasari vectors
     * @param v1 First Nasari vector to be compared
     * @param v2 Second Nasari vector to be compared
     */
    private static ArrayList<String> computeO(NasariVector v1, NasariVector v2){
        ArrayList<String> overlapList = new ArrayList<>();
        for(String wordV1 : v1.getRelatedWords()){
            for(String wordV2 : v2.getRelatedWords()){
                if(wordV1.equals(wordV2))
                    overlapList.add(wordV1);
            }
        }
        return overlapList;
    }

    private static int computeRank(NasariVector v, String s){
        ArrayList<String>relatedWordsList = v.getRelatedWords();
        Iterator<String> iterator = relatedWordsList.iterator();
        int res = 0;
        for(int i = 0; i < relatedWordsList.size(); i++){
            String relWord = iterator.next();
            if(relWord.equals(s))
                return (i+1);
        }
        return res;
    }

    public static double computeWO(NasariVector v1, NasariVector v2) {
        double wo = 0.0;
        ArrayList<String> O = computeO(v1, v2);
        double normalizedFactor = 0.0;
        for (int i = 1; i <= O.size(); i++) {
            double den = 2*i;
            double expr = (1.0 / den);
            normalizedFactor = normalizedFactor + expr;
        }
        double sum = 0.0;
        for (int i = 0; i < O.size(); i++) {
            String q = O.get(i);
            sum += 1.0/((computeRank(v1, q) + computeRank(v2, q)));
        }
        if (sum > 0.0 && normalizedFactor > 0.0){
            return sum / normalizedFactor;
        }
        return 0.0;
    }

    private void fillGhostVectorWords(String word){
        for(int i = 0; i < this.vectorSize; i++){
            if(i == (this.vectorSize-1))
                relatedWords.add(word.toLowerCase());
            else
                relatedWords.add("");
        }
    }
}
