import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class Nasari {
    private String pathToNasari = "[PATH_TO_NASARI_SUBSET]";
    private HashMap<String, NasariVector> vectors;
    private static Nasari ourInstance = new Nasari();

    public static Nasari getInstance() {
        return ourInstance;
    }

    private Nasari() {
        this.vectors = new HashMap<>();
        try {
            BufferedReader in = new BufferedReader(new FileReader(new File(pathToNasari)));
            String line = "";
            while((line = in.readLine()) != null){
                if(!line.equals("")){
                    try {
                        String[] vector = line.split(";");
                        String id = vector[0];
                        String wikiName = vector[1].toLowerCase();
                        ArrayList<String> pairs = new ArrayList<>();
                        for (int i = 2; i < vector.length; i++) {
                            String vecElement = vector[i];
                            String[] elementPair = vecElement.split("_");
                            if(elementPair.length >= 1)
                                pairs.add(elementPair[0]);
                        }
                        NasariVector v = new NasariVector(vector[1], pairs);
                        vectors.put(wikiName, v);
                    }catch(ArrayIndexOutOfBoundsException aobe){
                        System.out.println("Eccezione causata da "+ aobe.getMessage());
                        aobe.printStackTrace();
                    }
                }
            }
        }catch (IOException ex){
            System.err.println("Error getting Nasari instance: "+ ex.getMessage());
        }
    }

    public HashMap<String, NasariVector> getVectors() {
        return vectors;
    }
}
