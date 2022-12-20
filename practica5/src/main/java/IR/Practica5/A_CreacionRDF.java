package IR.Practica5;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.VCARD;

/**
 * Ejemplo de como construir un modelo de Jena y añadir nuevos recursos 
 * mediante la clase Model
 */
public class A_CreacionRDF {
	
	/**
	 * muestra un modelo de jena de ejemplo por pantalla
	 */
	public static void main (String[] args) {
        Model model = A_CreacionRDF.generarEjemplo();
        // write the model in the standard output
        model.write(System.out); 
    }
	
	/**
	 * Genera un modelo de jena de ejemplo
	 */
	public static Model generarEjemplo(){
		// definiciones
		String personURI1    = "http://somewhere/JohnSmith";
        String givenName1    = "John";
        String familyName1   = "Smith";
        String fullName1     = givenName1 + " " + familyName1;
		String personURI2    = "http://somewhere/AlbertoLardies";
        String givenName2   = "Alberto";
        String familyName2  = "Lardies";
        String fullName2    = givenName2 + " " + familyName2;
		String personURI3    = "http://somewhere/DanielGracia";
        String givenName3    = "Daniel";
        String familyName3   = "Gracia";
        String fullName3     = givenName3 + " " + familyName3;
        String value		= "http://xmlns.com/foaf/0.1/person";

        // crea un modelo vacío
        Model model = ModelFactory.createDefaultModel();

        // le a�ade las propiedades
        Resource johnSmith  = model.createResource(personURI1)
        		.addProperty(RDF.type, value)
        		.addProperty(VCARD.FN, fullName1)
                .addProperty(VCARD.N, 
                         model.createResource()
                              .addProperty(VCARD.Given, givenName1)
                              .addProperty(VCARD.Family, familyName1));
        Resource albertoLardies  = model.createResource(personURI2)
        		.addProperty(RDF.type, value)
        		.addProperty(VCARD.FN, fullName2)
                .addProperty(FOAF.knows, personURI3)
                .addProperty(VCARD.N, 
                         model.createResource()
                              .addProperty(VCARD.Given, givenName2)
                              .addProperty(VCARD.Family, familyName2));
        Resource danielGracia  = model.createResource(personURI3)
                .addProperty(RDF.type, value)
        		.addProperty(VCARD.FN, fullName3)
                .addProperty(FOAF.knows, personURI2)
                .addProperty(VCARD.N, 
                         model.createResource()
                              .addProperty(VCARD.Given, givenName3)
                              .addProperty(VCARD.Family, familyName3));
        return model;
	}
	
	
}
