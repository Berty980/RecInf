package org.apache.lucene.demo;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


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
  public static void main(String[] args) throws FileNotFoundException {
    String usage = """
            Evaluation [-qrels QRELS_FILENAME] [-results RESULTS_FILENAME] [-output OUTPUT_FILENAME]
            """;
    String qrelsPath = null;
    String resultsPath = null;
    OutputStreamWriter outputFile = null;
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

    BufferedReader qrelsReader;
    try {
      qrelsReader = new BufferedReader(new InputStreamReader(new FileInputStream(qrelsPath)));
    } catch (FileNotFoundException fnfe) {
      // at least on Windows, some temporary files raise this exception with an "access denied" message
      // checking if the file can be read doesn't help
      return;
    }

    BufferedReader resultsReader;
    try {
      resultsReader = new BufferedReader(new InputStreamReader(new FileInputStream(resultsPath)));
    } catch (FileNotFoundException fnfe) {
      // at least on Windows, some temporary files raise this exception with an "access denied" message
      // checking if the file can be read doesn't help
      return;
    }

    HashMap<String, List<String>> qrelsTable;
    HashMap<String, HashMap<String, Double>> resultsStats;

    //Procesar fichero de relevancias
    qrelsTable = readQRels(qrelsReader);
    //Procesar fichero de resultados
    resultsStats = readResults(qrelsTable, resultsReader);
    //Calcular estadisticas
//    makeStats(resultsStats, outputFile);

//    createOutput(stats, outputFile);
  }

  static HashMap<String, List<String>> readQRels(BufferedReader qrels) {
    HashMap<String, List<String>> qrelsTable = new HashMap<>();

    String line = null;
    String oldId = "";
    List<String> docList = new ArrayList<String>();
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

  static HashMap<String, HashMap<String, Double>> readResults(HashMap<String, List<String>> qrelsTable, BufferedReader results){
    HashMap<String, HashMap<String, Double>> resultsStats = new HashMap<>();

    String line = null;
    String oldId = "";
    HashMap<String, Double> stats = new HashMap<>();
    int rel = 0;
    int tot = 0;
    int rel10 = 0;
    List<Integer> points = new ArrayList<>();
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
        }
        tot++;
        if (qrelsTable.get(oldId).contains(cols[1])) {
          rel++;
          if (tot <= 10) rel10 = rel;
          points.add(tot);
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
    System.out.println(resultsStats);
    return resultsStats;
  }

  private static HashMap<String, Double> makeStats(int tp, int tpfp, int tpfn, int rel10, List<Integer> puntos) {
    HashMap<String, Double> stats = new HashMap<>();
    double precision = (double) tp / (double) tpfp;
    stats.put("precision", precision);
    double recall = (double) tp / (double) tpfn;
    stats.put("recall", recall);
    double f1 = (2.0 * precision * recall) / (precision + recall);
    stats.put("F1", f1);
    stats.put("prec@10", (double) rel10 / 10.0);
    List<Double> recalls = new ArrayList<>();
    List<Double> precisions = new ArrayList<>();
    for (int i = 1; i <= puntos.size(); i++) {
      double p = (double) i / (double) puntos.get(i - 1);
      precisions.add(p);
      stats.put("p"+i,p);
      double r = (double) i / (double) tpfn;
      recalls.add(r);
      stats.put("r"+i,r);
    }

    double ap = 0;
    for (double p : precisions){
      ap += p;
    }
    ap = ap / precisions.size();
    stats.put("average_precision", ap);

    return stats;
  }

//  private static void makeStats(HashMap<String, List<Integer>> resultsStats,
//                                HashMap<String, List<String>> qrelsTable, OutputStreamWriter out) {
//    HashMap<String, HashMap<String, Double>> fullStats = new HashMap<>();
//    for (String key : resultsStats.keySet()) {
//      HashMap<String, Double> stats = new HashMap<>();
//      double tp = resultsStats.get(key).get(0);
//      double tpfp = resultsStats.get(key).get(1);
//      double precision = tp / tpfp;
//      stats.put("precision", precision);
//      double tpfn = qrelsTable.get(key).size();
//      double recall = tp / tpfn;
//      stats.put("recall", recall);
//      double f1 = (2.0 * precision * recall) / (precision + recall);
//      stats.put("F1", f1);
//      stats.put("prec@10", resultsStats.get(key).get(0) / 10.0);
//
//
//      fullStats.put(key, stats);
//      System.out.println(fullStats);
//    }
//  }

}