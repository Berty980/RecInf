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
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.Span;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.WordlistLoader;
import org.apache.lucene.analysis.es.SpanishAnalyzer2;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static java.lang.Double.NEGATIVE_INFINITY;

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
//    for(int i = 0; i < 1; i++){
      Builder query = new BooleanQuery.Builder();
      String need = text.item(i).getTextContent();
      String cleanNeed = cleanNeed(need);

      cleanNeed = parseNames(cleanNeed, query, analyzer);
      cleanNeed = publisherRecognizer(cleanNeed, query, analyzer, parser);



      doPagingSearch(searcher, query.build(), hitsPerPage, outputFile, ids.item(0).getTextContent());
    }
    
    /*IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
    IndexSearcher searcher = new IndexSearcher(reader);
    Analyzer analyzer = new SpanishAnalyzer2();



    BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(queries), StandardCharsets.UTF_8));
    QueryParser parser = new QueryParser(field, analyzer);
    int nQuery = 1;
    while (true) {

      String line = in.readLine();
      if (line == null) {
        break;
      }

      line = line.trim();
      if (line.length() == 0) {
        break;
      }
      Query query;
      int startSpatial = line.indexOf("spatial:");

      if (startSpatial != -1) {
        String spatial = "";
        int endSpatial = line.indexOf(" ", startSpatial);
        if (endSpatial == -1)
          spatial = line.substring(startSpatial);
        else
          spatial = line.substring(startSpatial, endSpatial);

        // Spatial query
        int separator = spatial.indexOf(":");
        int auxSep = separator;
        Double[] coords = new Double[4];
        for(int i = 0; i < 3; i++) {
          separator = spatial.indexOf(',', auxSep+1);
          coords[i] = Double.parseDouble(spatial.substring(auxSep+1, separator));
          auxSep = separator;
        }
        coords[3] = Double.parseDouble(spatial.substring(separator+1));

        Query westRangeQuery = DoublePoint.newRangeQuery("west",
                                  Double.NEGATIVE_INFINITY, coords[1]);
        Query eastRangeQuery = DoublePoint.newRangeQuery("east",
                                  coords[0], Double.POSITIVE_INFINITY);
        Query southRangeQuery = DoublePoint.newRangeQuery("south",
                                  Double.NEGATIVE_INFINITY, coords[3]);
        Query northRangeQuery = DoublePoint.newRangeQuery("north",
                                  coords[2], Double.POSITIVE_INFINITY);
        BooleanQuery spatialQuery = new BooleanQuery.Builder()
                .add(westRangeQuery, BooleanClause.Occur.MUST)
                .add(eastRangeQuery, BooleanClause.Occur.MUST)
                .add(northRangeQuery, BooleanClause.Occur.MUST)
                .add(southRangeQuery, BooleanClause.Occur.MUST).build();

        if(endSpatial == -1)
          line = line.substring(0,startSpatial);
        else
          line = line.substring(0,startSpatial) + line.substring(endSpatial+1);
        if (!line.equals("")) {
          Query normalQuery = parser.parse(line);
          query = new BooleanQuery.Builder()
                  .add(spatialQuery, BooleanClause.Occur.SHOULD)
                  .add(normalQuery, BooleanClause.Occur.SHOULD).build();
        }
        else {
          query = new BooleanQuery.Builder()
                  .add(spatialQuery, BooleanClause.Occur.SHOULD).build();
        }
      }
      else {
        query = parser.parse(line);
      }

      System.out.println("Searching for: " + query.toString(field));
      doPagingSearch(searcher, query, hitsPerPage, outputFile, nQuery);
      nQuery++;
    }
    reader.close();*/
    assert outputFile != null;
    outputFile.close();
  }

  private static void publisherRecognizer(String cleanNeed, Builder query, Analyzer analyzer, QueryParser parser) throws ParseException {
    String[] words = cleanNeed.split(" ");

    for (int i=0; i<words.length; i++) {
      if(Objects.equals(words[i], "Universidad") || Objects.equals(words[i], "universidad")) {
        if (Objects.equals(words[i + 1], "de") && Objects.equals(words[i + 2], "Zaragoza")) {
          Query queryLine = parser.parse("Universidad de Zaragoza");
          query.add(new BoostQuery(queryLine, (float) 1.5), BooleanClause.Occur.SHOULD);
        }
      }else if(Objects.equals(words[i], "Departamento") || Objects.equals(words[i], "departamento")) {
        String queryString = "Departamento";
        if (Objects.equals(words[i + 1], "de")) {
          queryString = queryString +" de "+words[i+2];
        }
        Query queryLine = parser.parse(queryString);
        query.add(new BoostQuery(queryLine, (float) 1.5), BooleanClause.Occur.SHOULD);

      }else if(Objects.equals(words[i], "Area") || Objects.equals(words[i], "area")) {
        String queryString = "Area";
        if (Objects.equals(words[i + 1], "de")) {
          queryString = queryString +" de "+words[i+2];
        }
        Query queryLine = parser.parse("departamento:"+queryString);
        query.add(new BoostQuery(queryLine, (float) 1.5), BooleanClause.Occur.SHOULD);
      }
    }
  }

  private static String cleanNeed(String need) {
    String result = "";
    result = need.replace(".", "");
    result = result.replace(",", "")
            .replace("¿", "")
            .replace("?", "")
            .replace("¡", "")
            .replace("!", "")
            .replace("(", "")
            .replace(")", "")
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("Á", "A")
            .replace("É", "E")
            .replace("Í", "I")
            .replace("Ó", "O")
            .replace("Ú", "U");
    return result;
  }

  private static String parseNames(String need, Builder query, Analyzer analyzer) throws ParseException {
    String result = need;
    String[] words = result.split(" ");

    String[] fields = {"autor", "director"};
    BooleanClause.Occur[] flags = {BooleanClause.Occur.SHOULD, BooleanClause.Occur.SHOULD};

    TokenNameFinderModel model = null;
    try (InputStream personModel = new FileInputStream("es-ner-person.bin")){
      model = new TokenNameFinderModel(personModel);
    }
    catch(Exception io ) { System.out.println("Error parsing names"); }

    NameFinderME nameFinder = new NameFinderME(model);

    Span[] names = nameFinder.find(words);
    String[] names2 = Span.spansToStrings(names, words);

    List<String> namesList = new ArrayList<>(Arrays.asList(names2));

    if (namesList.size() != 0) {
      for (String s : namesList) {
        Query queryPerson = MultiFieldQueryParser.parse(s, fields, flags, analyzer);
        query.add(new BoostQuery(queryPerson, (float) 1.8), BooleanClause.Occur.SHOULD);
        result = result.replace(s, "");
      }
    }

    return result;
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
      } else {
        System.out.println((i+1) + ". " + "No path for this document");
      }
      outputFile.flush();
    }
  }
}