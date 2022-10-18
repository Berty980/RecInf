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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.es.SpanishAnalyzer2;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;

/** Simple command-line based search demo. */
public class SearchFiles {

  private SearchFiles() {}

  public static CharArraySet createStopSet2 (){
    String [] stopWords = {"el", "la", "lo", "en"};
    return StopFilter.makeStopSet(stopWords);
  }

  /** Simple command-line based search demo. */
  public static void main(String[] args) throws Exception {
    String usage =
      "Usage:\tjava org.apache.lucene.demo.SearchFiles [-index dir] [-query file] [-output outputFile]\n\nSee http://lucene.apache.org/core/4_1_0/demo/ for details.";
    if ((args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]))) || args.length < 6) {
      System.out.println(usage);
      System.exit(0);
    }

    String index = "index";
    String field = "contents";
    String queries = "";
    OutputStreamWriter outputFile = null;
    int hitsPerPage = 9999999;
    
    for(int i = 0;i < args.length;i++) {
      if ("-index".equals(args[i])) {
        index = args[i+1];
        i++;
      } else if ("-query".equals(args[i])) {
        queries = args[i+1];
        i++;
      } else if ("-output".equals(args[i])) {
        outputFile = new OutputStreamWriter(new FileOutputStream(args[i+1]));
      }
    }
    
    IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
    IndexSearcher searcher = new IndexSearcher(reader);
    Analyzer analyzer = new SpanishAnalyzer2(createStopSet2());

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
      
      Query query = parser.parse(line);
      System.out.println("Searching for: " + query.toString(field));

      doPagingSearch(searcher, query, hitsPerPage, outputFile, nQuery);
      nQuery++;
    }
    reader.close();
    assert outputFile != null;
    outputFile.close();
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
                                      OutputStreamWriter outputFile, int nQuery) throws IOException {
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