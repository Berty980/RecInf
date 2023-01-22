package IR.Practica6;

import java.io.*;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Scanner;

import org.apache.commons.io.FileUtils;
import org.apache.jena.query.*;
import org.apache.jena.query.text.EntityDefinition;
import org.apache.jena.query.text.TextDatasetFactory;
import org.apache.jena.query.text.TextIndexConfig;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.util.FileManager;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;

/**
 * Ejemplo de lectura de un modelo RDF de un fichero de texto
 * y como acceder con SPARQL a los elementos que contiene
 */
public class SemanticSearcher {
	public static void main(String[] args) throws IOException {
		String usage = """
			SemanticSearcher -rdf <rdfPath> -infoNeeds <infoNeedsFile> -output <resultsFile>
			""";

		String rdf = "";
		String infoNeeds = "";
		String output = "";
	    for(int i=0;i<args.length;i++) {
	        if ("-rdf".equals(args[i])) {
	        	rdf = args[i+1];
	        } else if ("-infoNeeds".equals(args[i])) {
	        	infoNeeds = args[i+1];
	        } else if ("-output".equals(args[i])) {
	        	output = args[i+1];
	        }
	    }
	    if (Objects.equals(rdf, "") || Objects.equals(infoNeeds, "") || Objects.equals(output, "")) {
	    	System.out.println("Usage: " + usage);
	    	System.exit(1);
	    }

		Model model = FileManager.get().loadModel(rdf);
		OutputStreamWriter salida = new OutputStreamWriter(new FileOutputStream(output));

		/*File entrada = new File(infoNeeds);
		Scanner lector = new Scanner(entrada);
		while (lector.hasNextLine()) {
			String linea = lector.nextLine();
			String queryString = linea.substring(linea.indexOf(" ") + 1);
			String id = linea.substring(0, linea.indexOf(" "));
			Query query = QueryFactory.create(queryString);
			try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
				ResultSet results = qexec.execSelect();
				while (results.hasNext()) {
					Resource x = results.nextSolution().getResource("file");
					String file = x.getURI().replace("http://mydic/documento/", "");
					salida.write(id + "\t" + file + "\n");
				}
			}
		}
		salida.close();*/



		EntityDefinition entDef = new EntityDefinition("uri", "identificador", ResourceFactory.createProperty("http://mydic/", "identificador"));
		entDef.set("titulo", ResourceFactory.createProperty("http://mydic/titulo").asNode());
		entDef.set("nombre-departamento", ResourceFactory.createProperty("http://mydic/nombre-departamento").asNode());
		entDef.set("descripcion", ResourceFactory.createProperty("http://mydic/descripcion").asNode());
		TextIndexConfig config = new TextIndexConfig(entDef);
		config.setAnalyzer(new SpanishAnalyzer());
		config.setQueryAnalyzer(new SpanishAnalyzer());
		config.setMultilingualSupport(true);

		//definimos el repositorio indexado todo en disco
		//se borra el repositorio para forzar a que cada vez que lo ejecutamos se cree de cero
		FileUtils.deleteDirectory(new File("repositorio"));
		Dataset ds1 = TDB2Factory.connectDataset("repositorio/tdb2");
		Directory dir =  new MMapDirectory(Paths.get("./repositorio/lucene"));
		Dataset ds = TextDatasetFactory.createLucene(ds1, dir, config);

		// cargamos el fichero deseado y lo almacenamos en el repositorio indexado
		ds.begin(ReadWrite.WRITE);
		RDFDataMgr.read(ds.getDefaultModel(), rdf);
		ds.commit();
		ds.end();

		ds.begin(ReadWrite.READ) ;
		File entrada = new File(infoNeeds);
		Scanner lector = new Scanner(entrada);
		while (lector.hasNextLine()) {
			String linea = lector.nextLine();
			String queryString = linea.substring(linea.indexOf(" ") + 1);
			String id = linea.substring(0, linea.indexOf(" "));
			System.out.println(id);
			Query query = QueryFactory.create(queryString);
			try (QueryExecution qexec = QueryExecutionFactory.create(query, ds)) {
				ResultSet results = qexec.execSelect();
				while (results.hasNext()) {
					QuerySolution soln = results.nextSolution();
					Resource x = soln.getResource("file");
					Literal y = soln.getLiteral("scoretot");
					Literal w = soln.getLiteral("score");
					float score = 0.0f;
					if(y != null) score = y.getFloat();
					Resource z = soln.getResource("type");
					String file = x.getURI().replace("http://mydic/documento/", "");
					System.out.println("\t" + file + " : " + score + " : " + z + " SUM: " + w);
					salida.write(id + "\t" + file + "\n");
				}
			}
		}
		ds.end();
		salida.flush();
	}
}
