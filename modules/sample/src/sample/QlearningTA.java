package sample;

import java.util.Random;
import java.util.ArrayList;
import java.util.Collections;
import java.util.*;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;

public class QlearningTA{
  private double alpha;
	private double beta;
	private double gamma;
  private int nb_actions;
  private HashMap<String, ArrayList<Double> > Q;
  private String agentID;
  private boolean learning;
  private boolean continuelearning;
  public QlearningTA(String agentID,int  nb_actions, double alpha,double beta,double gamma,boolean learning,boolean continuelearning){
    /*
    Actions: Unload goToRefuge  Go through n targets  Go through f targets randomwalk
    //State: X someoneOnBoard A at a refuge Y  near interesting targets Z far interesting targets
    */
    this.agentID=agentID;
    this.gamma=gamma;
    this.alpha=alpha;
    this.beta=beta;
    this.nb_actions=nb_actions;
    this.Q = new HashMap<String,ArrayList<Double>>();
    this.learning=learning;
    this.continuelearning=continuelearning;
    initQ(nb_actions);
    if(! this.learning || this.continuelearning){
      loadQ();
    }
  }

  public void update(String state, Integer action, double reward ){
    double old_value,new_value,next_max;
    old_value=Q.get(state).get(action);
    next_max = Collections.max(Q.get(state));
    //update
    new_value = (1 - this.alpha) * old_value + this.alpha * (reward + this.gamma * next_max);
    Q.get(state).set(action, new_value);
    saveQ();
  }

  public int discreteProba(List<Double> p){
    Random rand=new Random();
    Double r=rand.nextDouble();
    Double s=0.0;
    int index=0;
    while(s<r){
       s+=p.get(index);
       index++;
    }
    return index-1;
  }

  public List<Double> softmax(String state){
   List<Double> p=new ArrayList<Double>();
   double sum=0.0;
   for(int i=0;i<this.nb_actions;i++){
     sum+=Math.exp(this.Q.get(state).get(i)*this.beta);
   }
   for(int i=0;i<this.nb_actions;i++){
     p.add(Math.exp(this.Q.get(state).get(i)*this.beta)/sum);
   }

    return p;
 }
  //Init the Q table, 0 everywhere
  private void initQ(int nb_actions){
    for(int x=0;x<2;x++){
  for(int b=0;b<2;b++){
    for(int a=0;a<2;a++){
      for(int i=0;i<2;i++){
        for(int j=0;j<2;j++){
          String state= x+""+b+""+a+""+i+""+j;
          ArrayList actions = new ArrayList<Double>();
          for(int k=0;k<nb_actions ;k++){
            actions.add(0.0);
          }
          this.Q.put(state,actions);
        }
      }}
    }}}

  public int getAction(String state){
    int action = discreteProba(softmax(state));
    /*
    switch(action){
      case 0:
        System.out.println("ACTION: UNLOAD");
        break;
      case 1:
        System.out.println("ACTION: GO TO REFUGE");
        break;
      case 2:
        System.out.println("ACTION: GO TO N TARGET");
        break;
        case 3:
          System.out.println("ACTION: GO TO F TARGERT");
          break;
          case 4:
            System.out.println("ACTION: RANDOM WALK");
            break;
    }
*/
    return discreteProba(softmax(state));
  }


  public void loadQ(){
    String path="/home/asma/Documents/rcrs-server/modules/sample/src/sample/CSV/ambulanceteam.txt";
    BufferedReader csvReader=null;
    try {
    csvReader = new BufferedReader(new FileReader(path));
    } catch (IOException e1) {
      e1.printStackTrace();
    }
    try {
      String row=null;
      while ((row = csvReader.readLine()) != null) {
          String[] data = row.split(";");
          String state=data[0];
          ArrayList<Double> actions = new ArrayList<Double>(Arrays.asList(Arrays.stream(data[1].split(",")).map(Double::valueOf).toArray(Double[]::new)));
          Q.put(state,actions);
      }
      csvReader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void saveQ() {
    FileWriter buffer =null;
    try {
      buffer = new FileWriter("/home/asma/Documents/rcrs-server/modules/sample/src/sample/CSV/ambulanceteam"+this.agentID+".txt");
    } catch (IOException e1) {
      e1.printStackTrace();
    }
      try {
      for (Map.Entry<String,ArrayList<Double>> entry : Q  .entrySet()) {
        ArrayList<Double> t= entry.getValue();
        buffer.append(entry.getKey());
        buffer.append(";");
        for (int j=0;j<t.size();j++) {
          buffer.append(""+t.get(j));
            buffer.append(",");
        }
       buffer.append("\n");
      }
    buffer.flush();
    buffer.close();
  } catch (IOException e) {
    e.printStackTrace();
  }
  }
}
