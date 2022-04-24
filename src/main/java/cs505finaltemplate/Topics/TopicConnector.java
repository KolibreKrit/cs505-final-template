package cs505finaltemplate.Topics;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import cs505finaltemplate.Launcher;
import cs505finaltemplate.graphDB.GraphDBEngine;
import io.siddhi.query.api.expression.condition.In;
import com.orientechnologies.orient.core.record.OVertex;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class TopicConnector {

    private Gson gson;

    final Type typeOfListMap = new TypeToken<List<Map<String,String>>>(){}.getType();
    final Type typeListTestingData = new TypeToken<List<TestingData>>(){}.getType();

    //private String EXCHANGE_NAME = "patient_data";
    Map<String,String> config;

    public TopicConnector(Map<String,String> config) {
        gson = new Gson();
        this.config = config;
    }

    public void connect() {

        try {

            //create connection factory, this can be used to create many connections
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(config.get("hostname"));
            factory.setUsername(config.get("username"));
            factory.setPassword(config.get("password"));
            factory.setVirtualHost(config.get("virtualhost"));

            //create a connection, many channels can be created from a single connection
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();

            patientListChannel(channel);
            hospitalListChannel(channel);
            vaxListChannel(channel);

        } catch (Exception ex) {
            System.out.println("connect Error: " + ex.getMessage());
            ex.printStackTrace();
        }
}

    private void patientListChannel(Channel channel) {
        try {

            System.out.println("Creating patient_list channel");

            String topicName = "patient_list";

            channel.exchangeDeclare(topicName, "topic");
            String queueName = channel.queueDeclare().getQueue();

            channel.queueBind(queueName, topicName, "#");


            System.out.println(" [*] Patient List Waiting for messages. To exit press CTRL+C");

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                Launcher.graphDBEngine.db.activateOnCurrentThread();

                String message = new String(delivery.getBody(), "UTF-8");


                List<TestingData> incomingList = gson.fromJson(message, typeListTestingData);
                for (TestingData testingData : incomingList) {

                    //Data to send to CEP
                    Map<String,String> zip_entry = new HashMap<>();
                    if (testingData.patient_status == 1) {
                        zip_entry.put("zip_code",String.valueOf(testingData.patient_zipcode));
                    }
                    String testInput = gson.toJson(zip_entry);
                    //uncomment for debug
                    //System.out.println("testInput: " + testInput);

                    //insert into CEP
                    Launcher.cepEngine.input("testInStream",testInput);

                    //do something else with each record
                    OVertex patient_1;
                    if (Launcher.graphDBEngine.isPatient(testingData.patient_mrn)) {
                        patient_1 = Launcher.graphDBEngine.getPatient(testingData.patient_mrn);
                    }
                    else {
                        patient_1 = Launcher.graphDBEngine.createPatient(testingData.patient_mrn);
                    }
                    if (testingData.contact_list != null) {
                        for (String contact : testingData.contact_list) {
                            OVertex patient_2;
                            if (Launcher.graphDBEngine.isPatient(contact)) {
                                patient_2 = Launcher.graphDBEngine.getPatient(contact);
                            }
                            else {
                                patient_2 = Launcher.graphDBEngine.createPatient(contact);
                            }
                            Launcher.graphDBEngine.createContact(patient_1, patient_2);
                        }
                    }
                    if (testingData.event_list != null) {
                        for (String event_id : testingData.event_list) {
                            OVertex event;
                            if (Launcher.graphDBEngine.isEvent(event_id)) {
                                event = Launcher.graphDBEngine.getEvent(event_id);
                            }
                            else {
                                event = Launcher.graphDBEngine.createEvent(event_id);
                            }
                            Launcher.graphDBEngine.createAttend(patient_1, event);
                        }
                    }
                }
            };

            channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {
            });

        } catch (Exception ex) {
            System.out.println("patientListChannel Error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void hospitalListChannel(Channel channel) {
        try {

            String topicName = "hospital_list";

            System.out.println("Creating hospital_list channel");

            channel.exchangeDeclare(topicName, "topic");
            String queueName = channel.queueDeclare().getQueue();

            channel.queueBind(queueName, topicName, "#");

            System.out.println(" [*] Hospital List Waiting for messages. To exit press CTRL+C");

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {

                //new message
                String message = new String(delivery.getBody(), "UTF-8");

                //convert string to class
                List<Map<String,String>> incomingList = gson.fromJson(message, typeOfListMap);
                for (Map<String,String> hospitalData : incomingList) {
                    int hospital_id = Integer.parseInt(hospitalData.get("hospital_id"));
                    String patient_name = hospitalData.get("patient_name");
                    String patient_mrn = hospitalData.get("patient_mrn");
                    int patient_status = Integer.parseInt(hospitalData.get("patient_status"));
                    //do something with each each record.
                    //look up data

                    //insert data
                    String insertQuery = "INSERT INTO hospitals VALUES ('" + hospital_id + "','" + patient_mrn + "'," + patient_status + "," + 0 + ")";
                    Launcher.embedded.executeUpdate(insertQuery);
                }

            };

            channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {
            });

        } catch (Exception ex) {
            System.out.println("hospitalListChannel Error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void vaxListChannel(Channel channel) {
        try {

            String topicName = "vax_list";

            System.out.println("Creating vax_list channel");

            channel.exchangeDeclare(topicName, "topic");
            String queueName = channel.queueDeclare().getQueue();

            channel.queueBind(queueName, topicName, "#");


            System.out.println(" [*] Vax List Waiting for messages. To exit press CTRL+C");

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {

                String message = new String(delivery.getBody(), "UTF-8");

                //convert string to class
                List<Map<String,String>> incomingList = gson.fromJson(message, typeOfListMap);
                for (Map<String,String> vaxData : incomingList) {
                    int vaccination_id = Integer.parseInt(vaxData.get("vaccination_id"));
                    String patient_name = vaxData.get("patient_name");
                    String patient_mrn = vaxData.get("patient_mrn");
                    //do something with each each record.
                    String updateQuery = "UPDATE hospitals SET vax_status = 1 WHERE patient_mrn = '" + patient_mrn + "'";
                    Launcher.embedded.executeUpdate(updateQuery);
                }

            };

            channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {
            });

        } catch (Exception ex) {
            System.out.println("vaxListChannel Error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

}
