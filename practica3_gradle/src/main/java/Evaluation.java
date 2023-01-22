import java.io.*;
import java.util.*;

/** Index all text files under a directory.
 * <p>
 * This is a command-line application demonstrating simple Lucene indexing.
 * Run it with no command-line arguments for usage information.
 */
public class Evaluation {

  private Evaluation() {}

  /** Index all text files under a directory. */
  public static void main(String[] args) throws IOException {
    String usage = """
            Evaluation [-qrels QRELS_FILENAME] [-results RESULTS_FILENAME] [-output OUTPUT_FILENAME]
            """;
    String qrelsPath = null;
    String resultsPath = null;
    OutputStreamWriter outputFile = null;
    BufferedReader qrelsReader;
    BufferedReader resultsReader;
    HashMap<String, List<String>> qrelsTable;
    HashMap<String, HashMap<String, Double>> resultsStats;

    for(int i=0;i<args.length;i++) {
      if ("-qrels".equals(args[i])) {
        qrelsPath = args[i+1];
        i++;
      } else if ("-results".equals(args[i])) {
        resultsPath = args[i+1];
        i++;
      } else if ("-output".equals(args[i])) {
        outputFile = new OutputStreamWriter(new FileOutputStream(args[i+1]));
        i++;
      }
    }

    if (qrelsPath == null || resultsPath == null || outputFile == null) {
      System.err.println("Usage: " + usage);
      System.exit(1);
    }

    try {
      qrelsReader = new BufferedReader(new InputStreamReader(new FileInputStream(qrelsPath)));
    } catch (FileNotFoundException fnfe) {
      // at least on Windows, some temporary files raise this exception with an "access denied" message
      // checking if the file can be read doesn't help
      return;
    }

    try {
      resultsReader = new BufferedReader(new InputStreamReader(new FileInputStream(resultsPath)));
    } catch (FileNotFoundException fnfe) {
      // at least on Windows, some temporary files raise this exception with an "access denied" message
      // checking if the file can be read doesn't help
      return;
    }

    // Procesar fichero de juicios
    qrelsTable = readQRels(qrelsReader);

    // Procesar fichero de resultados y calcular estadísticas
    resultsStats = readResults(qrelsTable, resultsReader);

    // Generar fichero de estadísticas
    createOutput(resultsStats, outputFile);
  }

  /***
   * Devuelve un HashMap de documentos relevantes para cada consulta
   * @param qrels : Descriptor del fichero de juicios
   * @return :
   */
  static HashMap<String, List<String>> readQRels(BufferedReader qrels) {
    HashMap<String, List<String>> qrelsTable = new HashMap<>();
    List<String> docList = new ArrayList<>();
    String line = null;
    String oldId = "";

    try {
      line = qrels.readLine();
      oldId = line.split("\t")[0];
    }catch (IOException e){
      e.printStackTrace();
    }

    while(line != null){
      String[] cols = line.split("\t");
      if (!oldId.equals(cols[0])){
        List<String> clone = new ArrayList<>(docList);
        docList.clear();
        qrelsTable.put(oldId, clone);
        oldId = cols[0];
      }
      if (cols[2].equals("1")){
        docList.add(cols[1]);
      }
      try {
        line = qrels.readLine();
      }catch (IOException e){
        e.printStackTrace();
      }
    }
    qrelsTable.put(oldId, docList);
    return qrelsTable;
  }

  /***
   * Devuelve un HashMap con las estadísticas para cada consulta
   * @param qrelsTable : HashMap de documentos relevantes para cada consulta
   * @param results : Descriptor del fichero de resultados del sistema
   * @return :
   */
  static HashMap<String, HashMap<String, Double>> readResults(HashMap<String, List<String>> qrelsTable, BufferedReader results){
    HashMap<String, HashMap<String, Double>> resultsStats = new HashMap<>();
    HashMap<String, Double> stats;
    List<Integer> points = new ArrayList<>();
    String line = null;
    String oldId = "";
    int i = 0;
    int rel = 0;
    int tot = 0;
    int rel10 = 0;

    try {
      line = results.readLine();
      oldId = line.split("\t")[0];
    }catch (IOException e){
      e.printStackTrace();
    }

    while(line != null){
      String[] cols = line.split("\t");
      if(qrelsTable.containsKey(cols[0])) {
        if (!oldId.equals(cols[0])) {
          stats = makeStats(rel, tot, qrelsTable.get(oldId).size(), rel10, points);
          HashMap<String, Double> oldStats = new HashMap<>(stats);
          stats.clear();
          points.clear();
          rel = 0; tot = 0; rel10 = 0;
          resultsStats.put(oldId, oldStats);
          oldId = cols[0];
          i = 0;
        }
        i++;
        if(i <= 45) {
          tot++;
          if (qrelsTable.get(oldId).contains(cols[1])) {
            rel++;
            if (tot <= 10) rel10 = rel;
            points.add(tot);
          }
        }
        try {
          line = results.readLine();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    stats = makeStats(rel, tot, qrelsTable.get(oldId).size(), rel10, points);
    resultsStats.put(oldId,stats);
    return resultsStats;
  }

  /***
   * Devuelve un HashMap con las estadísticas a analizar
   * @param tp : Documentos relevantes detectados
   * @param tpfp : Documentos totales detectados
   * @param tpfn : Documentos relevantes totales
   * @param rel10 : Documentos relevantes encontrados al analizar 10 documentos totales
   * @param puntos : Lista de documentos totales procesados al encontrar cada documento relevante
   * @return :
   */
  private static HashMap<String, Double> makeStats(int tp, int tpfp, int tpfn, int rel10, List<Integer> puntos) {
    HashMap<String, Double> stats = new HashMap<>();
    List<Double> recalls = new ArrayList<>();
    List<Double> precisions = new ArrayList<>();
    int count = 0;
    double ap = 0;
    double precision = (double) tp / (double) tpfp;
    double recall = (double) tp / (double) tpfn;
    double f1 = 0;
    if((precision + recall) != 0) f1 = (2.0 * precision * recall) / (precision + recall);

    stats.put("precision", precision);
    stats.put("recall", recall);
    stats.put("F1", f1);
    stats.put("prec@10", (double) rel10 / 10.0);

    for (int i = 1; i <= puntos.size(); i++) {
      double p = (double) i / (double) puntos.get(i - 1);
      precisions.add(p);
      stats.put("p"+i,p);
      double r = (double) i / (double) tpfn;
      recalls.add(r);
      stats.put("r"+i,r);
    }

    for (double p : precisions) ap += p;
    if(precisions.size() == 0) ap = 0;
    else ap = ap / precisions.size();
    stats.put("average_precision", ap);

    for (double i = 0.000; i < 1.000; i += 0.100) {
      double ip = 0.0;
      for (int j = 0; j < precisions.size(); j++)
        if((recalls.get(j) - i) >= 0 && precisions.get(j) > ip)
          ip = precisions.get(j);

      stats.put("ip"+count, ip);
      count++;
    }

    return stats;
  }

  /***
   * Genera el documento con las estadísticas del sistema evaluado
   * @param resultsStats : HashMap con las estadísticas para cada consulta
   * @param outputFile : Descriptor de fichero de salida
   * @throws IOException :
   */
  private static void createOutput(HashMap<String, HashMap<String, Double>> resultsStats, OutputStreamWriter outputFile) throws IOException {
    StringBuilder stats = new StringBuilder();
    double[] total = new double[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    int k = 0;
    double size = resultsStats.size();

    for (String key : resultsStats.keySet()) {
      int i = 1;
      int id = 0;
      stats.append("INFORMATION_NEED ").append(key).append("\n");
      stats.append("precision ").append(String.format("%,.03f",resultsStats.get(key).get("precision"))).append("\n");
      total[k] += resultsStats.get(key).get("precision"); k++;
      stats.append("recall ").append(String.format("%,.03f",resultsStats.get(key).get("recall"))).append("\n");
      total[k] += resultsStats.get(key).get("recall"); k++;
      stats.append("F1 ").append(String.format("%,.03f",resultsStats.get(key).get("F1"))).append("\n");
      stats.append("prec@10 ").append(String.format("%,.03f",resultsStats.get(key).get("prec@10"))).append("\n");
      total[k] += resultsStats.get(key).get("prec@10"); k++;
      stats.append("average_precision ").append(String.format("%,.03f",resultsStats.get(key).get("average_precision"))).append("\n");
      total[k] += resultsStats.get(key).get("average_precision"); k++;
      Double rec = resultsStats.get(key).get("r"+i);
      stats.append("recall_precision\n");
      while(rec != null) {
        String p = String.format("%,.03f", resultsStats.get(key).get("p"+i));
        String r = String.format("%,.03f", resultsStats.get(key).get("r"+i));
        stats.append(r).append(" ").append(p).append("\n");
        i++;
        rec = resultsStats.get(key).get("r"+i);
      }
      stats.append("interpolated_recall_precision\n");
      for(double j = 0.000; j <= 1.000; j += 0.100) {
        String p = String.format("%,.03f", resultsStats.get(key).get("ip"+id));
        total[k] += resultsStats.get(key).get("ip"+id); k++;
        stats.append(String.format("%,.03f", j)).append(" ").append(p).append("\n");
        id++;
      }
      stats.append("\n");
      k = 0;
    }

    for (k = 0; k < total.length; k++) {
      total[k] = total[k] / 5;
    }
    k = 0;
    stats.append("TOTAL").append("\n");
    stats.append("precision ").append(String.format("%,.03f",total[k])).append("\n"); k++;
    stats.append("recall ").append(String.format("%,.03f",total[k])).append("\n"); k++;
    stats.append("F1 ").append(String.format("%,.03f", (2*total[0]*total[1]) / (total[0]+total[1]))).append("\n");
    stats.append("prec@10 ").append(String.format("%,.03f",total[k])).append("\n"); k++;
    stats.append("MAP ").append(String.format("%,.03f",total[k])).append("\n"); k++;
    stats.append("interpolated_recall_precision\n");
    for(double j = 0.000; j <= 1.000; j += 0.100) {
      String p = String.format("%,.03f", total[k]); k++;
      stats.append(String.format("%,.03f", j)).append(" ").append(p).append("\n");
    }

    outputFile.write(stats.toString());
    outputFile.flush();
  }

}