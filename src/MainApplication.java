import java.io.*;
import java.text.BreakIterator;
import java.util.*;
import org.annolab.tt4j.TokenHandler;
import org.annolab.tt4j.TreeTaggerWrapper;


public class MainApplication {

    static String pathToStopWords = "[PATH_TO_STOP_WORDS_FILE]";
    static String pathToTexts = "[PATH_TO_INPUT_DOCS_FOLDER]";
    static String firstTextFileName = "Donald-Trump-vs-Barack-Obama-on-Nuclear-Weapons-in-East-Asia.txt";
    static String secondTextFileName = "People-Arent-Upgrading-Smartphones-as-Quickly-and-That-Is-Bad-for-Apple.txt";
    static String thirdTextFileName = "The-Last-Man-on-the-Moon--Eugene-Cernan-gives-a-compelling-account.txt";
    static String logName = "first_p";
    static String pathToOutputFile = "[PATH_TO_LOG_OUTPUT_FOLDER] - "+ logName +".txt";
    static Document document = new Document();
    static Nasari nasariInstance;

    public static void main(String[] args){

        ArrayList<String> textSentences = textToSentences(pathToTexts, firstTextFileName);
        ArrayList<String> lemmedWords = new ArrayList<>();
        ArrayList<String> lemmedWordsTitle = new ArrayList<>();
        ArrayList<ArrayList<String>> contextSentences = new ArrayList<>();

        // fase di preprocessing dell'input
        boolean first = true;
        for(String sentence : textSentences) {
            lemmedWords.clear();
            sentence = cleanSentence(sentence);
            if(first) {
                String[] sentence_words = document.getTitle().split(" ");
                for (String s : sentence_words) {
                    lemmedWordsTitle.add(s.toLowerCase());
                }
                ArrayList<Word> lemmedTitle =treeTag(lemmedWordsTitle);
                for(Word w : lemmedTitle){
                    w.setLemma(w.getLemma().toLowerCase());
                    w.setLessema(w.getLessema().toLowerCase());
                }
                lemmedWordsTitle = filterWords(lemmedTitle);
                System.out.println(lemmedWordsTitle);
                first = false;
            }
            String[] sentence_words = sentence.split(" ");
            for (String s : sentence_words) {
                lemmedWords.add(s);
            }

            ArrayList<Word> lemmedContext = treeTag(lemmedWords);
            NPToLowerCase(lemmedContext);

            ArrayList<String> context = filterWords(lemmedContext);
            contextSentences.add(context);
        }
        //fine preprocessing

        nasariInstance = Nasari.getInstance();
        HashMap<String, NasariVector> nasariVectorMap = nasariInstance.getVectors();
        Set<String> keys = nasariVectorMap.keySet();
        Iterator<String> it = keys.iterator();

        ArrayList<NasariVector> titleVectors = getNasariVectors(lemmedWordsTitle);

        // sentence weights è destinato a contenere i punteggi di overlap del Title method per ogni frase
        ArrayList<Double> sentencesWeights = new ArrayList<>();
        scoreSentenceVectors(contextSentences, titleVectors, sentencesWeights);

        // calcolo dei pesi di weighted overlap interna ad ogni paragrafo
        ArrayList<ArrayList<Double>> internalCohesion = new ArrayList<>();
        HashMap<Integer, HashMap<Integer, String>> paragraphs = document.getDocument();
        int sizeParagraph = 0;
        Set<Integer> paragraphIndexes = paragraphs.keySet();
        int offset = 0;


        ArrayList<Double> sentencesInternalScores = new ArrayList<>();
        ArrayList<Double> totalWeights = new ArrayList<>();
        // versione 1 : coherence con granularità intero documento

        sizeParagraph = document.getSentencesNumber();
        scoreInternalParagraph(contextSentences, sizeParagraph, offset, sentencesInternalScores);
        totalWeights = joinApproaches(sentencesWeights, sentencesInternalScores);
        writeOutputToFile(firstTextFileName, "frasi", totalWeights, 0.33);


        //versione 2 : coherence con granularità intero paragrafo

        for(int i = 0; i < paragraphs.size(); i++) {
            HashMap<Integer, String> sts = paragraphs.get(i);
            sizeParagraph = sts.size();
            scoreInternalParagraph(contextSentences, sizeParagraph, offset, sentencesInternalScores);
            offset += sizeParagraph;
        }
        ArrayList<Double> paragraphsInternalWeights = aggregateScoresToParagraphs(sentencesInternalScores, paragraphs);
        // paragraphsWeights è destinato a contenere i punteggi di overlap relativi ai paragrafi e non alle singole frasi
        ArrayList<Double> paragraphsWeights = aggregateScoresToParagraphs(sentencesWeights, paragraphs);

        // stampa del contentuto dei pesi per ogni paragrafo
        totalWeights = joinApproaches(paragraphsWeights, paragraphsInternalWeights);
        writeOutputToFile(firstTextFileName, "paragrafo", totalWeights, 0.33);

    }


    /**
     * Questo metodo scrive su un file di log l'output della summarization in base ad un fattore di summarization passato in input
     * @param articleName nome dell'articolo
     * @param approach approccio adottato (per frasi / paragrafo)
     * @param totalWeights lista dei pesi di WO calcolati
     * @param summarizationRatio fattore di summarization
     */
    private static void writeOutputToFile(String articleName, String approach, ArrayList<Double> totalWeights, double summarizationRatio) {
        File log = new File(pathToOutputFile);
        ArrayList<Integer> minValues = computeMinIdxValues(totalWeights, summarizationRatio);
        try{
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(log)));
            out.write("NOME ARTICOLO: "+ articleName +"\n");
            out.write("APPROCCIO: "+ approach +"\n");
            out.write("\nTITLE: "+ document.getTitle() +"\n\n");
            if(approach.equalsIgnoreCase("frasi")){
                ArrayList<String> sentences = document.getAllSentences();
                int i = 0;
                for(String sent : sentences) {
                    if(!minValues.contains(i)) {
                        out.write("FRASE "+ (i+1) +": "+ sent + "\n");
                        out.write("PUNTEGGIO [ " + String.valueOf(totalWeights.get(i++)) + " ]\n\n");
                    }else{
                        i++;
                    }
                }
                for(Integer idx : minValues){
                    out.write("\nFRASE ELIMINATA "+ (idx+1) +": " + sentences.get(idx) + "\n");
                    out.write("PUNTEGGIO [ " + String.valueOf(totalWeights.get(idx)) + " ]\n");
                }
                out.write("\n\n===========================================================\n\n");
                out.close();
            }else{
                int i = 1;
                for(int j = 0; j < totalWeights.size(); j++){
                    if(!minValues.contains(j)) {
                        ArrayList<String> sentences = document.getSentencesFromParagraph(j);
                        out.write("\nPARAGRAFO: " + (i++) + "\n");
                        for (String s : sentences) {
                            out.write(s + " ");
                        }
                        out.write("\nPUNTEGGIO [ " + totalWeights.get(j) + " ]\n");
                    }else{i++;}
                }
                for(Integer idx : minValues){
                    ArrayList<String> sentences = document.getSentencesFromParagraph(idx);
                    out.write("\nPARAGRAFO ELIMINATO: " + (idx+1) + "\n");
                    for (String s : sentences) {
                        out.write(s + " ");
                    }
                    out.write("\nPUNTEGGIO [ " + totalWeights.get(idx) + " ]\n");
                }
                out.write("\n\n===========================================================\n\n");
                out.close();
            }
        }catch(IOException ex){
            ex.printStackTrace();
        }
    }

    /**
     * Calcola e restituisce la lista degli indici con valore di WO minori nel documento
     * @param totalWeights lista dei pesi di WO del documento
     * @param summarizationRatio grado di summarization
     * @return lista degli indici con il valore di WO minore
     */
    private static ArrayList<Integer> computeMinIdxValues(ArrayList<Double> totalWeights, double summarizationRatio) {
        Double min = Double.MAX_VALUE;
        Integer indexMin = -1;
        ArrayList<Integer> indexes = new ArrayList<>();
        int indexNumbers = (int) Math.ceil(totalWeights.size()*summarizationRatio);
        for(int i = 0; i < indexNumbers; i++) {
            min = Double.MAX_VALUE;
            for (int j = 0; j< totalWeights.size(); j++) {
                if (totalWeights.get(j) <= min && !indexes.contains(j)) {
                    min = totalWeights.get(j);
                    indexMin = j;
                }
            }
            indexes.add(indexMin);
        }
        return indexes;
    }

    /**
     * Questo metodo aggraga in una struttura dati i punteggi di WO ottenuti con gli approcci di 'title method' ed 'internal coherence'
     * @param titleWeights lista dei punteggi di WO ottenuti attraverso il 'title method'
     * @param internalCoherenceWeights  lista dei punteggi di WO ottenuti attraverso 'internal coherence'
     * @return lista dei punteggi aggregati
     */
    private static ArrayList<Double> joinApproaches(ArrayList<Double> titleWeights, ArrayList<Double> internalCoherenceWeights){
        ArrayList<Double> totalWeights = new ArrayList<>();
        if(titleWeights.size() != internalCoherenceWeights.size()){
            System.out.println("Le dimensioni delle liste di pesi dei due approcci differiscono!");
            return totalWeights;
        }
        for(int i = 0; i < titleWeights.size(); i++){
            Double titleWeight = titleWeights.get(i);
            Double internalCoherenceWeight = internalCoherenceWeights.get(i);
            Double totalWeight = (titleWeight + internalCoherenceWeight)/2;
            totalWeights.add(totalWeight);
        }
        return totalWeights;
    }

    private static void printValues(ArrayList<Double> values){
        for(Double d : values){
            System.out.println("Valore di coesione: " + d);
        }
    }

    /**
     * Produce i pesi di weighted overlap tra le frasi contenute in un paragrafo del testo.
     * @param contextSentences lista contenente le liste delle parole di ogni frase opportunamente preprocessata
     * @param sizeParagraph dimensione in termini di frasi del paragrafo di interesse
     * @param offset indice a partire dal quale bisogna considerare le frasi preprocessate
     * @param internalCohesion lista sulla quale calcolare i punteggi di Weighted overlap
     */
    private static void scoreInternalParagraph(ArrayList<ArrayList<String>> contextSentences, int sizeParagraph, int offset, ArrayList<Double> internalCohesion) {
        ArrayList<ArrayList<String>> ctxParagraph = new ArrayList<>();
        // estrazione contesti relativi alle singole frasi contenute nel paragrafo di riferimento
        if(sizeParagraph != 1) {
            for (int i = offset; i < (sizeParagraph + offset); i++) {
                ArrayList<String> contextSentence = contextSentences.get(i);
                ctxParagraph.add(contextSentence);
            }
            HashMap<Integer, Double> contributesMap = new HashMap<>();
            for (ArrayList<String> ctx : ctxParagraph) {
                ArrayList<NasariVector> sentencePivotVectors = getNasariVectors(ctx);
                ArrayList<Double> partialContributes = new ArrayList<>();
                ArrayList<ArrayList<String>> subCtx = new ArrayList<>();
                int indexCurrentCtx = ctxParagraph.indexOf(ctx);
                for (int i = 0; i < ctxParagraph.size(); i++) {
                    if (i != indexCurrentCtx)
                        subCtx.add(ctxParagraph.get(i));
                    if (!contributesMap.containsKey(i))
                        contributesMap.put(i, 0.0);
                }
                scoreSentenceVectors(subCtx, sentencePivotVectors, partialContributes);
                for (int i = 0; i < ctxParagraph.size(); i++) {
                    if (i != indexCurrentCtx) {
                        Double oldContribute = contributesMap.get(i);
                        Double contribute = partialContributes.remove(0);
                        contributesMap.replace(i, oldContribute + contribute);
                    }
                }
            }
            Set<Integer> keys = contributesMap.keySet();
            Iterator<Integer> it = keys.iterator();
            int normFactor = keys.size() - 1;
            while (it.hasNext()) {
                Double cohesion = contributesMap.get(it.next()) / normFactor;
                System.out.println(cohesion);
                internalCohesion.add(cohesion);
            }
        }else{
            internalCohesion.add(0.0);
        }
    }

    /**
     * Aggrega i punteggi di Weighted Overlap ottenuti per le singole frasi in ogni paragrafo
     * @param sentencesWeights Lista di pesi calcolati per ogni frase
     * @param paragraphs Mappa paragrafi-frasi relativi al testo di riferimento
     * @return lista di pesi associati ad ogni paragrafo del documento
     */
    private static ArrayList<Double> aggregateScoresToParagraphs(ArrayList<Double> sentencesWeights, HashMap<Integer, HashMap<Integer, String>> paragraphs) {
        ArrayList<Double> paragraphsWeights = new ArrayList<>();
        int length = paragraphs.size();
        int offset = 0;
        for(int i = 0; i < length; i++){
            Double weight = 0.0;
            int sentencesNumber = paragraphs.get(Integer.valueOf(i)).size();
            for(int j = offset; j < (sentencesNumber+offset); j++){
                weight += sentencesWeights.get(j);
            }
            offset += (sentencesNumber);
            paragraphsWeights.add(weight);
        }
        return paragraphsWeights;
    }

    /**
     * Questo metodo attribuisce i punteggi di Weighted Overlap per ogni frase del testo passata in input rispetto ad una
     * frase detta Pivot che può essere il titolo oppure una frase dello stesso paragrafo per valutare il grado di coesione
     * all'interno del paragrafo.
     * @param contextSentences Lista di frasi preprocessate
     * @param pivotSentenceVectors lista di vettori di riferimento per il calcolo della 'Weighted Overlap'
     * @param sentencesWeights lista di pesi di 'Weighted Overlap' calcolati rispetto alla lista di pivot
     */
    private static void scoreSentenceVectors(ArrayList<ArrayList<String>> contextSentences, ArrayList<NasariVector> pivotSentenceVectors, ArrayList<Double> sentencesWeights) {
        for(ArrayList<String> ctx : contextSentences){
            ArrayList<NasariVector> nasariVectors = getNasariVectors(ctx);
            //System.out.println("\nVettori per frase: " + ctx + "\n");
            double totalWoSentence = 0.0;
            double maxWo = 0.0;
            for (NasariVector vSentence : nasariVectors) {
                //System.out.println(vSentence.toString());
                double wo = 0.0;
                for (NasariVector vPivot : pivotSentenceVectors) {
                    wo = NasariVector.computeWO(vPivot, vSentence);
                    if (wo >= maxWo)
                        maxWo = wo;
                }
                totalWoSentence += maxWo;
            }
            sentencesWeights.add(totalWoSentence);
        }

    }

    /**
     * Questo metodo imposta il 'lower case' tutti i lemmi e lessemi associati ad ogni oggetto Word nella lista di input
     * avente come PoS tag quello di NP.
     * @param lemmedContext La lista di oggetti Word da processare
     */
    private static void NPToLowerCase(ArrayList<Word> lemmedContext) {
        for(Word w : lemmedContext){
            if(!w.getPos().equals("NP")){
                w.setLemma(w.getLemma().toLowerCase());
                w.setLessema(w.getLessema().toLowerCase());
            }
        }
    }

    /**
     * Questo metodo sfrutta la formattazione dei testi passati in input per segmentare in frasi l'intero
     * contenuto.
     * @param pathToDocument percorso di file system per accedere al documento
     * @param fileName nome del documento
     * @return lista di frasi associate al documento
     */
    static ArrayList<String> textToSentences(String pathToDocument, String fileName){
        ArrayList<String> sentences = new ArrayList<>();
        ArrayList<String> paragraphs = new ArrayList<>();
        try {
            BufferedReader in = new BufferedReader(new FileReader(new File(pathToDocument + fileName)));
            String paragraph = "";
            int indexParagraph = 0;
            while((paragraph = in.readLine()) != null){
                if( !paragraph.contains("#") && !paragraph.equals("") ) {
                    paragraphs.add(paragraph);
                }
            }
            sentences = paragraphsToSentences(paragraphs);
            in.close();
        }catch(IOException ex){
            System.err.println("TextToSentences: "+ ex.getMessage());
        }
        return sentences;
    }

    /**
     * Suddivisione dei paragrafi del testo in una lista di frasi
     * @param paragraphs lista dei paragrafi del testo
     * @return lista di frasi contenute nel testo
     */
    static ArrayList<String> paragraphsToSentences(ArrayList<String> paragraphs){
        ArrayList<String> sentences = new ArrayList<>();
        int indexParagraph = 0;
        boolean first = true;
        int numInserimento = 0;
        int startIndex = 0;
        for(String p : paragraphs){
            BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.ENGLISH);
            String source = p;
            iterator.setText(source);
            int start = iterator.first();
            int end = iterator.next();
            int indexSentence = 0;
            HashMap<Integer, String> sentenceMap = new HashMap<>();
            //sentences.clear();
            while( end != BreakIterator.DONE){
                String sentence = source.substring(start,end);
                start = end;
                end = iterator.next();
                if(first) {
                    document.setTitle(p);
                    first = false;
                }else {
                    if (!first) {
                        sentences.add(sentence);
                        indexSentence++;
                    }
                }
            }
            if(!first) {
                adjustSentences(sentences, startIndex, indexParagraph-1);
                startIndex = sentences.size();
                indexParagraph++;
            }else{
                document.setTitle(p);
                first = false;
            }
        }
        return sentences;
    }

    /**
     * Questo metodo gestisce esclusivamente il caso in cui una frase termina con un espressione del tipo "Mr." non riconosciuta
     * dal BreakIterator.
     * @param sentences
     * @param indexParagraph
     */
    static void adjustSentences(ArrayList<String> sentences, int startIndex, int indexParagraph){
        int c = startIndex;
        HashMap<Integer, String> sentencesMap = new HashMap<>();
        int indexSentenceMap = 0;
        if(!sentences.isEmpty()) {
            while (c < sentences.size()) {
                String sent = sentences.get(c);
                if (sent.endsWith("Mr. ")) {
                    int indexElim = ++c;
                    String nextSentence = sentences.get(c);
                    if (nextSentence.endsWith("Mr. ")) {
                        int indexElim2 = indexElim + 1;
                        String nextSentence2 = sentences.get(indexElim2);
                        nextSentence = nextSentence + nextSentence2;
                        sentences.set(indexElim2 - 1, nextSentence);
                        sentences.remove(indexElim2);
                        sentencesMap.put(indexSentenceMap, nextSentence);
                        sentencesMap.remove(indexElim2);
                    }
                    String newSentence = sent + nextSentence;
                    sentences.set(c - 1, newSentence);
                    sentences.remove(indexElim);
                    sentencesMap.put(indexSentenceMap++, newSentence);
                    sentencesMap.remove(indexElim);
                } else {
                    sentencesMap.put(indexSentenceMap++, sent);
                    c++;
                }

            }
            document.addItem(indexParagraph, sentencesMap);
        }
    }

    /**
     * Questo metodo applica un filtro sui caratteri di punteggiatura o speciali presenti all'interno della frase data
     * in input
     * @param sentence La frase da filtrare
     * @return La frase filtrata
     */
    static String cleanSentence(String sentence){
        String res = sentence.replace(".","").replace(":","")
                .replace(",","").replace("’", " ")
                .replace("(","").replace(")","")
                .replace("”","").replace("“","")
                .replace("-"," ")
                .replace("—"," ").replace("[","")
                .replace("]","");
        //System.out.println("Clean Sentence: " + res);
        return res;
    }

    static void filterStopWords(ArrayList<String> sentences){
        ArrayList<String> sentenceWordsList = new ArrayList<>();
        for (String sentence : sentences) {
            sentenceWordsList.clear();
            String[] sentenceWords = sentence.replace(".","").replace(":","")
                    .replace(",","").replace("’", " ")
                    .replace("(","").replace(")","")
                    .replace("”","").replace("“","")
                    .replace("—"," ").replace("[","")
                    .replace("]","").split(" ");
            for(String s : sentenceWords){
               if(!isStopWord(s))
                   sentenceWordsList.add(s);
            }

            String filteredSentece = "";
            for(String w : sentenceWordsList) {
                if(sentenceWordsList.indexOf(w) == (sentenceWordsList.size()-1))
                    filteredSentece += w;
                else
                    filteredSentece += w + " ";
            }
            sentences.set(sentences.indexOf(sentence),filteredSentece);
        }
    }

    /**
     * Questo metodo applica il filtraggio di stop-words ad una lista di Word lemmatizzate data in input.
     * @param lemmedSentence la lista contentente oggetti Word
     * @return La lista filtrata sottoforma di String
     */
    private static ArrayList<String> filterWords(ArrayList<Word>lemmedSentence){
        ArrayList<String> lemmedSentenceWords = new ArrayList<>();
        try {
            ArrayList<String> filteredWords = new ArrayList<>();
            for(Word l : lemmedSentence) {
                String w = l.getLessema()
                        .replace(" ","")
                        .replace("'","")
                        .toLowerCase();
                Word filteredWord = l;
                filteredWord.setLessema(w);
                lemmedSentenceWords.add(filteredWord.getLemma());
                int index = lemmedSentenceWords.indexOf(filteredWord.getLemma());
                if(!(w.equals(".")) && !w.equals(";") && !w.equals(":") && !w.equals(",")) {
                    BufferedReader in = new BufferedReader(new FileReader(new File(pathToStopWords)));
                    String line = "";
                    while ((line = in.readLine()) != null) {
                        line = line.split(" ")[0];
                        line = line.replace(" ", "");
                        if (!line.equals("") && line.equals(w)) {
                            lemmedSentenceWords.remove(index);
                            in.mark(0);
                            in.reset();
                            //break;
                        }
                    }
                }else{
                    lemmedSentenceWords.remove(filteredWord.getLemma());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lemmedSentenceWords;
    }

    /**
     *
     * @param word La stringa da controllare
     * @return True se la stringa word passata in input è una stop-word, false altrimenti.
     */
    static boolean isStopWord(String word){
        boolean flag = false;
        try{
            BufferedReader in = new BufferedReader(new FileReader(new File(pathToStopWords)));
            String line = "";
            while((line = in.readLine())!= null && !flag){
                if(!line.equals("") && word.equals(line))
                    flag = true;
            }
            in.close();
        }catch (IOException ex){
            System.err.println("IsStopWords: "+ ex.getMessage());
        }
        return flag;
    }

    /**
     * Queto metodo applica la lemmatizzazione ed individuazione dei PoS tag sulle parole che compongono una frase restituendo
     * una lista di Word associate alla frase.
     * @param sentenceWords lista di stringhe rappresentanti le parole all'interno della frase da parsificare
     * @return lista di oggetti Word che comprendono i lemmi e i PoS.
     */
    private static ArrayList<Word> treeTag(ArrayList<String> sentenceWords){
        ArrayList<Word> lemmedWords = new ArrayList<Word>();
        System.setProperty("treetagger.home","/home/alessandro/Documents/TreeTagger");
        TreeTaggerWrapper tt = new org.annolab.tt4j.TreeTaggerWrapper<String>();
        try{
            tt.setModel("english-utf8.par");
            tt.setHandler(new TokenHandler<String>() {
                public void token(String token, String pos, String lemma) {
                    //System.out.println(token+"\t"+pos+"\t"+lemma);
                    lemmedWords.add(new Word(token, lemma, pos));
                }
            });
            tt.process(sentenceWords);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            tt.destroy();
            return lemmedWords;
        }
    }

    /**
     * Crea una lista di vettori Nasari associati ad un elenco di parole. Se la parola non viene trovata nella risorsa
     * NASARI, viene creato ed aggiunto alla lista risultato un vettore fittizio contentente al fondo la parola non trovata.
     * @param lemmatizedTitle lista di parole lemmizzate per cui ricavare i vettori Nasari.
     * @return la lista di vettori nasari associati ad ogni parola contenuta nella lista di input.
     */
    static ArrayList<NasariVector> getNasariVectors(ArrayList<String> lemmatizedTitle){
        ArrayList<NasariVector> vectors = new ArrayList<>();
        for(String word : lemmatizedTitle) {
            NasariVector vector;
            HashMap<String, NasariVector> nasariVectors = nasariInstance.getVectors();
            vector = nasariVectors.get(word);
            if (vector != null){
                vectors.add(vector);
            }
        }
        return vectors;
    }
}
