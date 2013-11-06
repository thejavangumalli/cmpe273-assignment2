package edu.sjsu.cmpe.procurement.jobs;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.TextMessage;

import org.fusesource.stomp.jms.StompJmsDestination;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.ClientResponse;

import de.spinscale.dropwizard.jobs.Job;
import de.spinscale.dropwizard.jobs.annotations.Every;
import edu.sjsu.cmpe.procurement.ProcurementService;

/**
 * This job will run at every 5 second.
 */
@Every("5mn")
public class ProcurementSchedulerJob extends Job {
	boolean checkMessages=false;
	private final Logger log = LoggerFactory.getLogger(getClass());
    @Override
    public void doJob() throws Exception{
    		String input="{\"id\":\"83832\",\"order_book_isbns\" : [" ;
		String input1;
		long waitUntil = 5000; // wait for 5 sec
		while(true) {
		   Message msg = ProcurementService.consumer.receive(waitUntil);
		    if( msg instanceof  TextMessage ) {
		    		checkMessages=true;
		           String body = ((TextMessage) msg).getText();
		           input=input + body.substring(10) + ",";
		    } else if (msg == null) {
		          
  		          break;
		    } else {
		         System.out.println("Unexpected message type: " + msg.getClass());
		    }
		} // end while loop
				
		int pos=input.lastIndexOf(",");
		input1=input.substring(0,pos)+""+input.substring(pos+1)+"]}";
		if(checkMessages)
		{
		ClientResponse response=ProcurementService.jerseyClient.create().resource(
				"http://54.215.210.214:9000/orders").type("application/json").post(ClientResponse.class,input1);
		System.out.println(response.getEntity(String.class));
		}
		else {
			System.out.println("No new messages. Exiting due to timeout - " + waitUntil / 1000 + " sec");
			}
		
		
		ClientResponse serverResponse=ProcurementService.jerseyClient.create().resource(
				"http://54.215.210.214:9000/orders/83832").accept("application/json").get(ClientResponse.class);
		String output=serverResponse.getEntity(String.class);
		System.out.println(output);
		JSONObject obj=new JSONObject(output);
		
		JSONArray array = obj.getJSONArray("shipped_books");
		
		for(int i = 0 ; i < array.length() ; i++){
			
		 
		   String destination2=ProcurementService.destination1  + array.getJSONObject(i).getString("category");
			
		Destination dest1=new StompJmsDestination(destination2);
		ProcurementService.producer=ProcurementService.session.createProducer(dest1);
		
		String data=array.getJSONObject(i).getString("isbn")+":"+"\""+array.getJSONObject(i).getString("title")+"\":\""+array.getJSONObject(i).getString("category")+"\":\""+array.getJSONObject(i).getString("coverimage")+"\"";
		
		TextMessage msg = ProcurementService.session.createTextMessage(data);
		msg.setLongProperty("id", System.currentTimeMillis());
		ProcurementService.producer.send(msg);
		ProcurementService.producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
		}
		System.out.println("Done");
	
    }
    
}
