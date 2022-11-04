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
import java.nio.file.Paths;
import java.util.*;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.util.Span;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer2;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/** Simple command-line based search demo. */
public class SearchFiles {

  private SearchFiles() {}

  /** Simple command-line based search demo. */
  public static void main(String[] args) throws Exception {
    String usage =
      "Usage:\tjava org.apache.lucene.demo.SearchFiles [-index dir] [-infoNeeds file] [-output outputFile]\n\nSee http://lucene.apache.org/core/4_1_0/demo/ for details.";
    if ((args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]))) || args.length < 6) {
      System.out.println(usage);
      System.exit(0);
    }

    String index = "index";
    String field = "contents";
    String infoNeeds = "";
    OutputStreamWriter outputFile = null;
    int hitsPerPage = 9999999;
    
    for(int i = 0;i < args.length - 1;i++) {
      if ("-index".equals(args[i])) {
        index = args[i+1];
        i++;
      } else if ("-infoNeeds".equals(args[i])) {
        infoNeeds = args[i+1];
        i++;
      } else if ("-output".equals(args[i])) {
        outputFile = new OutputStreamWriter(new FileOutputStream(args[i+1]));
      }
    }

    IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
    IndexSearcher searcher = new IndexSearcher(reader);
    Analyzer analyzer = new SpanishAnalyzer2();
    QueryParser parser = new QueryParser(field, analyzer);
    QueryParser parser2 = new QueryParser(field, new WhitespaceAnalyzer());

    FileInputStream fis;
    try {
      fis = new FileInputStream(infoNeeds);
    } catch (FileNotFoundException fnfe) {
      // at least on Windows, some temporary files raise this exception with an "access denied" message
      // checking if the file can be read doesn't help
      return;
    }

    DocumentBuilderFactory dbf = DocumentBuilderFactory.newDefaultInstance();
    DocumentBuilder db = dbf.newDocumentBuilder();
    org.w3c.dom.Document d = db.parse(fis);

    NodeList ids = d.getElementsByTagName("identifier");
    NodeList text = d.getElementsByTagName("text");

    for(int i = 0; i < ids.getLength(); i++){
      System.out.println(ids.item(i).getTextContent());
      Builder query = new BooleanQuery.Builder();
      String need = text.item(i).getTextContent();

      String cleanNeed = cleanNeed(need);

      cleanNeed = parseNames(cleanNeed, query, analyzer);
      cleanNeed = cleanNeed.toLowerCase();
      cleanNeed = parsePublisher(cleanNeed, query, parser);
      cleanNeed = parseType(cleanNeed, query, parser2);
      cleanNeed = parseDate(cleanNeed, query);
      parseText(cleanNeed, query, analyzer);

      System.out.println(query.build());
      doPagingSearch(searcher, query.build(), hitsPerPage, outputFile, ids.item(i).getTextContent());
    }

//    IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
//    IndexSearcher searcher = new IndexSearcher(reader);
//    Analyzer analyzer = new SpanishAnalyzer2();
//
//
//
//    BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(queries), StandardCharsets.UTF_8));
//    QueryParser parser = new QueryParser(field, analyzer);
//    QueryParser parser2 = new QueryParser(field, new WhitespaceAnalyzer());
//    int nQuery = 1;
//    while (true) {
//
//      String line = in.readLine();
//      if (line == null) {
//        break;
//      }
//
//      line = line.trim();
//      if (line.length() == 0) {
//        break;
//      }
//      Query query;
//      Builder queryBuilder = new BooleanQuery.Builder();
//      int startSpatial = line.indexOf("spatial:");
//
//      if (startSpatial != -1) {
//        String spatial = "";
//        int endSpatial = line.indexOf(" ", startSpatial);
//        if (endSpatial == -1)
//          spatial = line.substring(startSpatial);
//        else
//          spatial = line.substring(startSpatial, endSpatial);
//
//        // Spatial query
//        int separator = spatial.indexOf(":");
//        int auxSep = separator;
//        Double[] coords = new Double[4];
//        for(int i = 0; i < 3; i++) {
//          separator = spatial.indexOf(',', auxSep+1);
//          coords[i] = Double.parseDouble(spatial.substring(auxSep+1, separator));
//          auxSep = separator;
//        }
//        coords[3] = Double.parseDouble(spatial.substring(separator+1));
//
//        Query westRangeQuery = DoublePoint.newRangeQuery("west",
//                                  Double.NEGATIVE_INFINITY, coords[1]);
//        Query eastRangeQuery = DoublePoint.newRangeQuery("east",
//                                  coords[0], Double.POSITIVE_INFINITY);
//        Query southRangeQuery = DoublePoint.newRangeQuery("south",
//                                  Double.NEGATIVE_INFINITY, coords[3]);
//        Query northRangeQuery = DoublePoint.newRangeQuery("north",
//                                  coords[2], Double.POSITIVE_INFINITY);
//        BooleanQuery spatialQuery = new BooleanQuery.Builder()
//                .add(westRangeQuery, BooleanClause.Occur.MUST)
//                .add(eastRangeQuery, BooleanClause.Occur.MUST)
//                .add(northRangeQuery, BooleanClause.Occur.MUST)
//                .add(southRangeQuery, BooleanClause.Occur.MUST).build();
//
//        if(endSpatial == -1)
//          line = line.substring(0,startSpatial);
//        else
//          line = line.substring(0,startSpatial) + line.substring(endSpatial+1);
//        if (!line.equals("")) {
//          Query normalQuery = parser.parse(line);
//          query = new BooleanQuery.Builder()
//                  .add(normalQuery, BooleanClause.Occur.SHOULD)
//                  .add(spatialQuery, BooleanClause.Occur.SHOULD).build();
//        }
//        else {
//          query = new BooleanQuery.Builder()
//                  .add(spatialQuery, BooleanClause.Occur.SHOULD).build();
//        }
//      }
//      else {
//        query = parser.parse(line);
//      }
//
////      if (!line.equals("")) {
//////          Query normalQuery = parser.parse(line);
////        String[] simple = line.split(" ");
////        int i = 0;
////        for (String s : simple) {
////          if(s.indexOf(":",0) > 0) {
////            if (s.indexOf("tipo",0) > 0) query = parser2.parse(s);
////            else { query = parser.parse(s); }
////            queryBuilder.add(query, BooleanClause.Occur.SHOULD).build();
////          }
////        }
////        queryBuilder.add(spatialQuery, BooleanClause.Occur.SHOULD);
////        query = queryBuilder.build();
////      }
////      else {
////        query = new BooleanQuery.Builder()
////                .add(spatialQuery, BooleanClause.Occur.SHOULD).build();
////      }
////    }
////      else {
////      String[] simple = line.split(" ");
////      for (String s : simple) {
////        if(s.indexOf(":",0) >= 0) {
////          if (s.indexOf("tipo",0) >= 0) {
////            query = parser2.parse(s);
////          }
////          else {
////            query = parser.parse(s);
////          }
////          queryBuilder.add(query, BooleanClause.Occur.SHOULD).build();
////        }
////      }
////      query = queryBuilder.build();
////    }
//
//      System.out.println("Searching for: " + query.toString(field));
//      System.out.println(query);
//      doPagingSearch(searcher, query, hitsPerPage, outputFile, Integer.toString(nQuery));
//      nQuery++;
//    }
    reader.close();
    assert outputFile != null;
    outputFile.close();
  }

  private static String cleanNeed(String need) {
    String result = need.replace(".", "");
    return result.replace(",", "").replace("¿", "")
            .replace("?", "").replace("¡", "")
            .replace("!", "").replace("(", "")
            .replace(")", "").replace("á", "a")
            .replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u")
            .replace("Á", "A").replace("É", "E")
            .replace("Í", "I").replace("Ó", "O")
            .replace("Ú", "U");
  }

  private static String parseNames(String need, Builder query, Analyzer analyzer) throws ParseException {
    String[] words = need.split(" ");

    String[] fields = {"autor", "director"};
    BooleanClause.Occur[] flags = {BooleanClause.Occur.SHOULD, BooleanClause.Occur.SHOULD};

    TokenNameFinderModel model = null;
    try (InputStream personModel = new FileInputStream("es-ner-person.bin")){
      model = new TokenNameFinderModel(personModel);
    }
    catch(Exception io ) { System.out.println("Error parsing names"); }

    assert model != null;
    NameFinderME nameFinder = new NameFinderME(model);

    Span[] names = nameFinder.find(words);
    String[] names2 = Span.spansToStrings(names, words);
    List<String> namesList = new ArrayList<>(Arrays.asList(names2));

    if (namesList.size() != 0) {
      for (String s : namesList) {
        Query queryPerson = MultiFieldQueryParser.parse(s, fields, flags, analyzer);
        query.add(queryPerson, BooleanClause.Occur.MUST);
        need = need.replace(" " + s, "");
      }
    }
    return need;
  }

  private static String parsePublisher(String need, Builder query, QueryParser parser) throws ParseException {
    String[] words = need.split(" ");
    String field = "departamento:";
    Builder aux = new BooleanQuery.Builder();
    boolean found = false;
    for (int i=0; i<words.length; i++) {
      if(words[i].equals("departamento")) {
        if (words[i + 1].equals("de")) {
          found = true;
          String queryString = field + words[i+2];
          Query queryLine = parser.parse(queryString);
          aux.add(queryLine, BooleanClause.Occur.SHOULD);
        }
      } else if(words[i].equals("area")) {
        if (words[i + 1].equals("de")) {
          found = true;
          String queryString = field + words[i+2];
          Query queryLine = parser.parse(queryString);
          aux.add(queryLine, BooleanClause.Occur.SHOULD);
        }
      }
    }
    if(found)
      query.add(aux.build(), BooleanClause.Occur.MUST);
    return need.replace("departamento ", "").replace("area ", "");
  }

  private static String parseType(String need, Builder query, QueryParser parser) throws ParseException {
    List<String> words = Arrays.stream(need.split(" ")).toList();
    String field = "tipo", queryString = "";
    Builder aux = new BooleanQuery.Builder();
    boolean empty = true;
    if(words.contains("trabajos")) {
      empty = false;
      if (words.contains("grado"))
        aux.add(new TermQuery(new Term(field, "TAZ-TFG")), BooleanClause.Occur.SHOULD);
      if (words.contains("master"))
        aux.add(new TermQuery(new Term(field, "TAZ-TFM")), BooleanClause.Occur.SHOULD);
      if (!words.contains("grado") && !words.contains("master")) {
        aux.add(new TermQuery(new Term(field, "TAZ-TFG")), BooleanClause.Occur.SHOULD);
        aux.add(new TermQuery(new Term(field, "TAZ-TFM")), BooleanClause.Occur.SHOULD);
        aux.add(new TermQuery(new Term(field, "TAZ-PFC")), BooleanClause.Occur.SHOULD);
      }
      System.out.println(aux.build());
    }
    if(words.contains("proyectos")) {
      empty = false;
      aux.add(new TermQuery(new Term(field, "TAZ-PFC")), BooleanClause.Occur.SHOULD);
    }
    if(words.contains("tesis")) {
      empty = false;
      aux.add(new TermQuery(new Term(field, "TESIS")), BooleanClause.Occur.SHOULD);

    }
    if(!empty) query.add(aux.build(), BooleanClause.Occur.MUST);

    return need.replace("trabajos ", "").replace(" grado", "")
            .replace(" master", "").replace("proyectos ", "")
            .replace(" tesis", "");
  }

  private static String parseDate(String need, Builder query) {
    String[] words = need.split(" ");
    String field = "fecha";
    for (int i=0; i<words.length; i++) {
      if (isNumeric(words[i])) {
        String fecha1 = null, fecha2 = null;

        if (words[i-1].equals("desde")) {
          fecha1 = words[i];
          need = need.replace(" " + fecha1, "");
          if (words[i + 1].equals("hasta") && isNumeric(words[i + 2])) {
            fecha2 = words[i + 2];
            need = need.replace(" " + fecha2, "");
        }
        } else if (words[i-1].equals("ultimos")){
          fecha1 = Integer.toString(Calendar.getInstance().get(Calendar.YEAR) - Integer.parseInt(words[i]));
          need = need.replace(" " + words[i], "");
        } else if (words[i-1].equals("entre")){
          if (words[i+1].equals("y") && isNumeric(words[i+2])) {
            fecha1 = words[i];
            fecha2 = words[i + 2];
            need = need.replace(" " + fecha1, "");
            need = need.replace(" " + fecha2, "");
          }
        }
        if (fecha1 != null || fecha2 != null) {
          Query queryLine = TermRangeQuery.newStringRange(field, fecha1, fecha2, true, true);
          query.add(queryLine, BooleanClause.Occur.MUST);
        }
      }
    }
    return need.replace("desde ", "")
            .replace("hasta ", "").replace("ultimos ", "")
            .replace("entre ", "").replace("años ", "");
  }

  private static boolean isNumeric(String word) {
    if (word == null) { return false; }
    try { Integer.parseInt(word); }
    catch (NumberFormatException nfe) { return false; }
    return true;
  }

  private static void parseText(String need, Builder query, Analyzer analyzer) throws ParseException {
    String[] fields = {"descripcion", "pclave", "titulo"};
    BooleanClause.Occur[] flags = {BooleanClause.Occur.SHOULD, BooleanClause.Occur.SHOULD, BooleanClause.Occur.SHOULD};
    Builder aux = new BooleanQuery.Builder();
    POSModel model = null;
    try (InputStream textModel = new FileInputStream("opennlp-es-maxent-pos-es.bin")) {
      model = new POSModel(textModel);
    } catch(Exception io ) { System.out.println("Error parsing names"); }
    assert model != null;
    POSTaggerME tagger = new POSTaggerME(model);

    String[] words = need.split(" ");
    String[] tags = tagger.tag(words);
    for(int i = 0; i < tags.length; i++) {
      if ((i+1 < tags.length) && tags[i].startsWith("N") && tags[i+1].startsWith("A")) {
        Query queryWord = MultiFieldQueryParser.parse("\"" + words[i] + " " + words[i+1] + "\"", fields, flags, analyzer);
        aux.add(queryWord, BooleanClause.Occur.SHOULD);
        i++;
      }
      else if (tags[i].startsWith("N") || tags[i].startsWith("A") || tags[i].startsWith("Z")) {
        Query queryWord = MultiFieldQueryParser.parse(words[i], fields, flags, analyzer);
        aux.add(queryWord, BooleanClause.Occur.SHOULD);
      }
    }
    query.add(aux.build(), BooleanClause.Occur.MUST);
  }

  /**
   * This demonstrates a typical paging search scenario, where the search engine presents 
   * pages of size n to the user. The user can then go to the next page if interested in
   * the next hits.
   * <p>
   * When the query is executed for the first time, then only enough results are collected
   * to fill 5 result pages. If the user wants to page beyond this limit, then the query
   * is executed another time and all hits are collected.
   * 
   */
  public static void doPagingSearch(IndexSearcher searcher, Query query, int hitsPerPage,
                                      OutputStreamWriter outputFile, String nQuery) throws IOException {
    // Collect enough docs to show 5 pages
    TopDocs results = searcher.search(query, 5 * hitsPerPage);
    ScoreDoc[] hits = results.scoreDocs;
    int numTotalHits = Math.toIntExact(results.totalHits.value);
    System.out.println(numTotalHits + " total matching documents");

    for (int i = 0; i < hits.length; i++) {
      Document doc = searcher.doc(hits[i].doc);
      String path = doc.get("path");
      if (path != null) {
        int lastSlash = path.lastIndexOf('\\');
        String out = nQuery + "\t" + path.substring(lastSlash + 1) + "\n";
        outputFile.write(out);
      } else
          System.out.println((i+1) + ". " + "No path for this document");
      outputFile.flush();
    }
  }
}
