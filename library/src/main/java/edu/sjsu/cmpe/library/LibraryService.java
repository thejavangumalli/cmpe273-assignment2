package edu.sjsu.cmpe.library;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.fusesource.stomp.jms.StompJmsConnectionFactory;
import org.fusesource.stomp.jms.StompJmsDestination;
import org.fusesource.stomp.jms.message.StompJmsMessage;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yammer.dropwizard.Service;
import com.yammer.dropwizard.assets.AssetsBundle;
import com.yammer.dropwizard.config.Bootstrap;
import com.yammer.dropwizard.config.Environment;
import com.yammer.dropwizard.views.ViewBundle;

import edu.sjsu.cmpe.library.api.resources.BookResource;
import edu.sjsu.cmpe.library.api.resources.RootResource;
import edu.sjsu.cmpe.library.config.LibraryServiceConfiguration;
import edu.sjsu.cmpe.library.domain.Book;
import edu.sjsu.cmpe.library.domain.Book.Status;
import edu.sjsu.cmpe.library.dto.BookDto;
import edu.sjsu.cmpe.library.repository.BookRepository;
import edu.sjsu.cmpe.library.repository.BookRepositoryInterface;
import edu.sjsu.cmpe.library.ui.resources.HomeResource;

public class LibraryService extends Service<LibraryServiceConfiguration> {
    public static String host;
    public static String port;
    public static String user;
    public static String password;
    public static String destination;
    public static String destination1;
    public static String libraryName;
    public static BookRepositoryInterface bookRepository = new BookRepository();
    
    private final Logger log = LoggerFactory.getLogger(getClass());

    public static void main(String[] args) throws Exception {
    	new LibraryService().run(args);
    	int numThreads = 1;
	    ExecutorService executor = Executors.newFixedThreadPool(numThreads);
 
	    Runnable backgroundTask = new Runnable() {
			
		

		@Override
		public void run() {
			try {
				listener();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	    };
    executor.execute(backgroundTask);
    }
    

    @Override
    public void initialize(Bootstrap<LibraryServiceConfiguration> bootstrap) {
	bootstrap.setName("library-service");
	bootstrap.addBundle(new ViewBundle());
	bootstrap.addBundle(new AssetsBundle());
    }

    @Override
    public void run(LibraryServiceConfiguration configuration,
	    Environment environment) throws Exception {
	// This is how you pull the configurations from library_x_config.yml
	String queueName = configuration.getStompQueueName();
	String topicName = configuration.getStompTopicName();
	host=configuration.getApolloHost();
	port=configuration.getApolloPort();
	user=configuration.getApolloUser();
	password=configuration.getApolloPassword();
	libraryName=configuration.getLibraryName();
	destination=queueName;
	destination1=topicName;
	log.debug("{} - Queue name is {}. Topic name is {}",
		configuration.getLibraryName(), queueName,
		topicName);
	// TODO: Apollo STOMP Broker URL and login

	/** Root API */
	environment.addResource(RootResource.class);
	/** Books APIs */
	
	environment.addResource(new BookResource(bookRepository));

	/** UI Resources */
	environment.addResource(new HomeResource(bookRepository));
    }
    public static void listener() throws JMSException, JSONException, MalformedURLException{
    	StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
    	factory.setBrokerURI("tcp://" + host + ":" + port);

    	Connection connection = factory.createConnection(user, password);
    	connection.start();
    	Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    	Destination dest = new StompJmsDestination(destination1);

    	MessageConsumer consumer = session.createConsumer(dest);
    	System.currentTimeMillis();
    	System.out.println("Waiting for messages...");
    	while(true) {
    	    Message msg = consumer.receive();
    	    if( msg instanceof  TextMessage ) {
    		String body = ((TextMessage) msg).getText();
    		System.out.println("Received message = " + body);
    		body=body.replace("\"","");
    		String [] contents=body.split(":",4);
    		Book book=new Book();
    		Book book1=new Book();
    		book.setIsbn(Long.parseLong(contents[0]));
    		book.setTitle(contents[1]);
    		book.setCategory(contents[2]);
    		URL url=new URL(contents[3]);
    		book.setCoverimage(url);
    		book1=bookRepository.saveBook(book);
      		if(bookRepository.getBookByISBN(book.getIsbn()).getStatus().toString()=="lost")
      		{
      			bookRepository.getBookByISBN(book.getIsbn()).setStatus(Status.available);
      		}
      		BookDto bookResponse = new BookDto(book1);
    		
    		}
    	   else if (msg instanceof StompJmsMessage) {
    		StompJmsMessage smsg = ((StompJmsMessage) msg);
    		String body = smsg.getFrame().contentAsString();
    		System.out.println("Received message = " + body);

    	    }
    		else {
    		System.out.println("Unexpected message type: "+msg.getClass());
    	    }
    	
    	}	
    }
    
    private static String env(String key, String defaultValue) {
    	String rc = System.getenv(key);
    	if( rc== null ) {
    	    return defaultValue;
    	}
    	return rc;
        }
}
