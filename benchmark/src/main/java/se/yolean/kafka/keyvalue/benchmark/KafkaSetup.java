package se.yolean.kafka.keyvalue.benchmark;

//import io.smallrye.mutiny.Multi;
//import io.smallrye.reactive.messaging.annotations.Broadcast;
import javax.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
//import java.util.Random;
import java.util.HashMap;

@ApplicationScoped
public class KafkaSetup {

    //private Random randomValue = new Random();
    

    @Outgoing("topicname")
    public HashMap<Integer, Integer> createOutputs(){
        HashMap<Integer, Integer> outputList = new HashMap<Integer, Integer>();

        outputList.put(0, 4);

        return outputList;
    }
}
