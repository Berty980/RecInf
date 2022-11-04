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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.file.Paths;
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
            java org.apache.lucene.demo.Evaluation -qrels <qrelsFileName> -results <resultsFileName> -output <outputFileName>

            This indexes the documents in DOCS_PATH, creating a Lucene indexin INDEX_PATH that can be searched with SearchFiles""";
    String qrelsPath = null;
    String resultsPath = null;
    String outputPath = null;
    boolean create = true;
    for (int i = 0; i < args.length; i++) {
      if ("-qrels".equals(args[i])) {
        qrelsPath = args[i + 1];
        i++;
      } else if ("-results".equals(args[i])) {
        resultsPath = args[i + 1];
        i++;
      } else if ("-output".equals(args[i])) {
        outputPath = args[i + 1];
      }
    }

    if (qrelsPath == null || resultsPath == null || outputPath == null) {
      System.err.println("Usage: " + usage);
      System.exit(1);
    }

    String[] need;
    String[] doc;
    String[] rel;

    File archivo = new File(qrelsPath);
    FileReader fr = null;
    try {
      fr = new FileReader(archivo);
    } catch (FileNotFoundException e) {
      System.err.println("No se ha encontrado el fichero " + qrelsPath);
      System.exit(1);
    }
    BufferedReader br = new BufferedReader(fr);
    File archivo2 = new File(resultsPath);
    FileReader fr2 = null;
    try {
      fr2 = new FileReader(archivo2);
    } catch (FileNotFoundException e) {
      System.err.println("No se ha encontrado el fichero " + qrelsPath);
      System.exit(1);
    }
    BufferedReader br2 = new BufferedReader(fr2);

    String linea = null;
    String key = "";
    List<String> val = new ArrayList<>();
    HashMap<String,List<String>> relevantes = new HashMap<>();
    int i = 0;
    String[] aux;
    // HashMap con las necesidades de información como clave
    // y la lista de documentos relevantes como valor
    while((linea = br.readLine()) != null) {
      aux = linea.split("\t");
      if (i == 0) key = aux[0];
      if (aux[2].equals("1")) {
        if (!aux[0].equals(key)) {
          List<String> clone = new ArrayList<>(val);
          relevantes.put(key, clone);
          val.clear();
          key = aux[0];
        }
        val.add(aux[1]);
      }
      i++;
    }
    relevantes.put(key, val);

    // Primer elemento del valor: doc. totales
    // Segundo elemento del valor: doc. relevantes
    HashMap<String, List<Integer>> est = new HashMap<>();
    key = "";
    //
    while((linea = br2.readLine()) != null) {
      aux = linea.split("\t");
      if(relevantes.containsKey(aux[0])){
        if(!aux[0].equals(key)) {
          List<Integer> l = new ArrayList<>();
          l.add(1); l.add(0);
          est.put(aux[0], l);
          key = aux[0];
        } else { est.get(aux[0]).set(0,est.get(aux[0]).get(0) + 1); }

        if (relevantes.get(aux[0]).contains(aux[1]))
          est.get(aux[0]).set(1,est.get(aux[0]).get(1) + 1);
      }
    }
  }
}