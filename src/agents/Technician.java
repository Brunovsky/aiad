package agents;

import jade.core.Agent;
import jade.core.AID;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAAgentManagement.Property;

public class Technician extends Agent {

  protected void setup() {
  	String serviceName = "unknown";
  	
  	// Read the name of the service to register as an argument
  	Object[] args = getArguments();
  	if (args != null && args.length > 0) {
  		serviceName = (String) args[0];
  	}
  	
  	// Register the service
  	System.out.println("Agent "+getLocalName()+" registering service \""+serviceName+"\" of type \"tech-repairs\"");

  	try {
  		DFAgentDescription dfd = new DFAgentDescription();
  		dfd.setName(getAID());
  		ServiceDescription sd = new ServiceDescription();
  		sd.setName(serviceName);
  		sd.setType("tech-repairs");
  		// Agents that want to use this service need to "know" the tech-repairs-ontology
  		// sd.addOntologies("tech-repairs-ontology");
  		// Agents that want to use this service need to "speak" the FIPA-SL language
  		// sd.addLanguages(FIPANames.ContentLanguage.FIPA_SL);
  		// sd.addProperties(new Property("country", "Italy"));
  		dfd.addServices(sd);
  		
  		DFService.register(this, dfd);
  	}
  	catch (FIPAException fe) {
  		fe.printStackTrace();
  	}
  } 
 
  /*
    Agent Termination
  */
  protected void takeDown(){

    // Removing the registration in the yellow pages
    try{
      DFService.deregister(this);
    }
    catch(FIPAException fe){
      fe.printStackTrace();
    }

    System.out.println("Technician-agent " + getLocalName() + " has terminated!");
  } 
}
