package IR.Practica6;

import org.apache.jena.rdf.model.*;
import org.apache.jena.util.FileManager;
import org.apache.jena.vocabulary.RDF;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.DocumentBuilder;

import org.apache.jena.vocabulary.SKOS;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


/** Index all text files under a directory.
 * <p>
 * This is a command-line application demonstrating simple Lucene indexing.
 * Run it with no command-line arguments for usage information.
 */
public class SemanticGenerator {
	public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException {
		String usage = """
            SemanticGenerator -rdf <rdfPath> -skos <skosPath> -owl <owlPath> -docs <docsPath>
            """;

		String rdf = "";
		String skos = "";
		String owl = "";
		String docs = "";
	    for(int i=0;i<args.length;i++) {
	        if ("-rdf".equals(args[i])) {
	        	rdf = args[i+1];
	        } else if ("-skos".equals(args[i])) {
	        	skos = args[i+1];
	        } else if ("-owl".equals(args[i])) {
	        	owl = args[i+1];
	        } else if ("-docs".equals(args[i])) {
	        	docs = args[i+1];
	        }
	    }
	    if (Objects.equals(rdf, "") || Objects.equals(skos, "") || Objects.equals(owl, "") || Objects.equals(docs, "")) {
	    	System.out.println("Usage: " + usage);
	    	System.exit(1);
	    }

	    Model owlModel = FileManager.get().loadModel(owl);
		Model skosModel = FileManager.get().loadModel(skos);
		owlModel.add(skosModel);
		
		//Se separa la indexación del resto del programa por limpieza
		indexar(docs, owlModel);
		
		File rdfSalida = new File(rdf);
		try {
			owlModel.write(new FileOutputStream(rdfSalida));
		} catch (IOException e) {
			System.out.println("Hubo un problema en la creación del fichero rdf de salida.");
			e.printStackTrace();
		}
	    
	}
	
	static void indexar(String docP, Model owl) throws ParserConfigurationException, SAXException, IOException {
		
		File dirDocumentos = new File(docP);	
		if (dirDocumentos.isDirectory()) {
			
			String[] files = dirDocumentos.list();
			assert files != null;
			for (String file : files) {
				indexar((docP + "\\" + file), owl);
			}
	        
		} else {
			
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder= factory.newDocumentBuilder();
	        Document doc = builder.parse(docP);

			Resource trabajo = owl.getResource("http://mydic/trabajo");
	        Property tema = owl.getProperty("http://mydic/tema");
	        
	        Resource docLocal = owl.createResource("http://mydic/documento/" + docP.substring(docP.indexOf('\\') + 1));

        	String tipo = doc.getElementsByTagName("dc:type").item(0).getTextContent();
			tipo = tipo.substring(tipo.indexOf('-') + 1);
	        Resource tipoTrabajo = owl.createResource("http://mydic/trabajo/"+tipo);
			tipoTrabajo.addProperty(RDF.type, trabajo);
        	
        	docLocal.addProperty(RDF.type, tipoTrabajo);

	        String titulo = setProperties("dc:title", doc, owl, docLocal, "titulo");

			setProperties("dc:identifier", doc, owl, docLocal, "identificador");

			String descripcion = setProperties("dc:description", doc, owl, docLocal, "descripcion");

			setProperties("dc:date", doc, owl, docLocal, "fecha");

			setPropertiesWithClass("dc:creator", doc, owl, docLocal, "autor");

			setPropertiesWithClass("dc:contributor", doc, owl, docLocal, "director");

			setPropertiesWithClass("dc:publisher", doc, owl, docLocal, "departamento");

			setSubject("dc:subject", doc, owl, docLocal, tema, "");

			if(!descripcion.isEmpty())
				setSubject("dc:subject", doc, owl, docLocal, tema, descripcion);

			if(!titulo.isEmpty())
				setSubject("dc:subject", doc, owl, docLocal, tema, titulo);
		}
	}

	public static String setProperties(String tagName, Document doc, Model owl, Resource resource, String property) {
		List<String> contenido = getListString(tagName, doc);
		StringBuilder data = new StringBuilder();
		for(String elemento : contenido) {
			data.append(elemento).append(" ");
			Property propiedad = owl.createProperty("http://mydic/" + property);
			resource.addProperty(propiedad, elemento);
		}
		return data.toString();
	}

	public static void setPropertiesWithClass(String tagName, Document doc, Model owl, Resource resource, String property) {
		List<String>contenido = getListString(tagName, doc);
		for (String elemento : contenido) {
			Property propiedad = owl.createProperty("http://mydic/" + property);
			String source, name, urlName;
			if(property.equals("departamento")) {
				source = "entidad";
				String entidad = elemento;
				if(elemento.contains(";")) entidad = elemento.substring(elemento.indexOf(";") + 2);
				urlName = entidad.replace(" ", "_");
				name = "nombre-departamento";
			} else {
				source = "persona";
				urlName = elemento.replace(" ", "_").replace(",", "");
				name = "nombre-persona";
			}
			Resource res = owl.getResource("http://mydic/" + source);

			Resource res2 = owl.createResource("http://mydic/" + source +"/" + urlName);
			res2.addProperty(RDF.type, res);
			Property prop = owl.createProperty("http://mydic/" + name);
			res2.addProperty(prop, elemento);
			resource.addProperty(propiedad, res2);
		}
	}

	public static void setSubject(String tagName, Document doc, Model owl, Resource resource, Property property, String content) {
		List<String> contenido;
		if(content.isEmpty())
			contenido = getListString(tagName, doc);
		else
			contenido = Arrays.stream(content.split(" ")).toList();

		for (String elemento : contenido) {
			Property propiedadP = owl.getProperty(SKOS.prefLabel.getURI());
			Property propiedadA = owl.getProperty(SKOS.altLabel.getURI());
			StmtIterator iteradorP = owl.listStatements(null, propiedadP, elemento, "es");
			StmtIterator iteradorA = owl.listStatements(null, propiedadA, elemento, "es");

			while (iteradorP.hasNext())
				resource.addProperty(property, owl.getResource(iteradorP.next().getSubject().getURI()));

			while (iteradorA.hasNext())
				resource.addProperty(property, owl.getResource(iteradorA.next().getSubject().getURI()));
		}
	}

	public static List<String> getListString(String tagName, Document doc) {
        NodeList lista = doc.getElementsByTagName(tagName);
        List<String> listaLK = new LinkedList<String>();

        for (int i = 0; i < lista.getLength(); i++) {
            NodeList subLista = lista.item(i).getChildNodes();
            if (subLista.getLength() > 0) {
            	//listaLK.add(subLista.item(0).getTextContent().toLowerCase());
            	listaLK.add(stripAccents(subLista.item(0).getTextContent().toLowerCase()));
            }
        }
        return listaLK;
	}
	
	//https://stackoverflow.com/questions/15190656/easy-way-to-remove-accents-from-a-unicode-string
	public static String stripAccents(String s) {
	    s = Normalizer.normalize(s, Normalizer.Form.NFD);
	    s = s.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
	    return s;
	}

}