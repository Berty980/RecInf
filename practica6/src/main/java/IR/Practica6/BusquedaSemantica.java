package IR.Practica6;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Objects;
import java.util.Scanner;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.util.FileManager;

/**
 * Ejemplo de lectura de un modelo RDF de un fichero de texto
 * y como acceder con SPARQL a los elementos que contiene
 */
public class BusquedaSemantica {

	public static class Consulta{
		String id;
		String conString;
	}

	public static Consulta[] consultas;

	public static void main(String[] args) throws IOException {

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
	    	System.out.println("Error de parámetros, ejecución: IndexacionSemantico -rdf <rdfPath> -infoNeeds <infoNeedsPath> -output <outputPath>");
	    	System.exit(1);
	    }


		Model model = FileManager.get().loadModel(rdf);

		FileWriter salida = new FileWriter(output);

		consultas = new Consulta[2];
		leerConsultas(infoNeeds);
		Consulta consulta = consultas[0];
		//for (Consulta consulta : consultas) {
		
			String queryString = consulta.conString;
			System.out.println(queryString);
			Query query = QueryFactory.create(queryString);
			try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
				ResultSet results = qexec.execSelect();
				while (results.hasNext()) {
					QuerySolution soln = results.nextSolution();
					Resource x = soln.getResource("file");
					String file = x.getURI().replace("http://urldic/documento/recordsdc\\", "");
					//String file = x.getURI();
					System.out.println("Hay resultados " + file);
					salida.write(consulta.id + "\t" + file + "\n");
				}
			}

		//}
		salida.close();
	}

	static void leerConsultas(String infoNeedsFile) {
		//consultas.emptyList();
		File file = new File(infoNeedsFile);
		try {
			int position = 0;
			Scanner lector = new Scanner(file);
			while (lector.hasNextLine()) {
				String data = lector.nextLine();
				String[] dataS = data.split("\t");
				consultas[position] = new Consulta();
				consultas[position].id = dataS[0];
				consultas[position].conString = dataS[1];
				position++;
			}
			lector.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

}
