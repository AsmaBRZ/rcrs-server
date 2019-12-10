package sample;

import static rescuecore2.misc.Handy.objectsToIDs;
import java.util.*;
import java.lang.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Random;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.HashMap;
import rescuecore2.worldmodel.EntityID;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.messages.Command;
import rescuecore2.standard.messages.AKSpeak;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.EntityID;
import rescuecore2.worldmodel.Property;
import rescuecore2.worldmodel.properties.IntProperty;
import rescuecore2.worldmodel.properties.BooleanProperty;
import rescuecore2.standard.entities.StandardEntityConstants;
import sample.QlearningFB;
import java.io.UnsupportedEncodingException;
import org.apache.log4j.Logger;
import java.util.regex.*;
/**
   A sample fire brigade agent.
 */
public class SampleFireBrigade extends AbstractSampleAgent<FireBrigade> {
    public static final EnumSet<StandardEntityConstants.Fieryness> BURNING = EnumSet.of(StandardEntityConstants.Fieryness.HEATING, StandardEntityConstants.Fieryness.BURNING, StandardEntityConstants.Fieryness.INFERNO);
    private static Logger LOG = Logger.getLogger(SampleFireBrigade.class);
    private static final String MAX_WATER_KEY = "fire.tank.maximum";
    private static final String MAX_DISTANCE_KEY = "fire.extinguish.max-distance";
    private static final String MAX_POWER_KEY = "fire.extinguish.max-sum";
    private QlearningFB ql;
    private double epsilon = 0.2;
    private int maxWater;
    private int maxDistance;
    private int maxPower;
    private boolean learning=false;
    private  boolean continuelearning=true;
    private int cpHelping=0;
    private int cpBuilding=0;
    private Collection<EntityID> unexploredBuildings;
    @Override
    public String toString() {
        return "Sample fire brigade";
    }

    @Override
    protected void postConnect() {
        super.postConnect();
        model.indexClass(StandardEntityURN.BUILDING, StandardEntityURN.REFUGE,StandardEntityURN.HYDRANT,StandardEntityURN.GAS_STATION);
        maxWater = config.getIntValue(MAX_WATER_KEY);
        maxDistance = config.getIntValue(MAX_DISTANCE_KEY);
        maxPower = config.getIntValue(MAX_POWER_KEY);
        unexploredBuildings = new HashSet<EntityID>(buildingIDs);
        ql=new QlearningFB(super.getID().toString(),7,0.3,0.9,0.4,this.learning,this.continuelearning) ;
        LOG.info("Sample fire brigade connected: max extinguish distance = " + maxDistance + ", max power = " + maxPower + ", max tank = " + maxWater);
    }

    @Override
    protected void think(int time, ChangeSet changed, Collection<Command> heard) {
         double reward =0.0;
        if (time == config.getIntValue(kernel.KernelConstants.IGNORE_AGENT_COMMANDS_KEY)) {
            // Subscribe to channel 1
            sendSubscribe(time, 1);
        }
        for (Command next : heard) {
            LOG.debug("Heard " + next);
        }
        HashMap<Building,Integer> a =  getBuildings(changed);
        FireBrigade me = me();
        updateUnexploredBuildings(changed);

        //System.out.println(super.getID()+" my water :" +me.getWater() +"\n");
        EntityID fbTarg = someoneCleaning(heard);
        EntityID myTarg=null;

          for (Map.Entry<Building, Integer> next : a.entrySet()) {
            List<Building> result=new ArrayList<Building>();
            result.add(next.getKey());
            Collection<EntityID> list_keys= objectsToIDs(result);
            EntityID building_key=list_keys.iterator().next();
            Integer temperature = next.getValue();
            // System.out.println("A Key = " + building_key +", Value = " + temperature);
            if (model.getDistance(getID(), building_key) <= maxDistance) {
              //System.out.println(super.getID()+"TARGET BURNING "+next.getKey().getFierynessEnum()+ " temperature "+temperature);
              myTarg=building_key;
              break;
            }
        }

        String state = getState(fbTarg,myTarg,changed);
        //System.out.println(super.getID()+" state: " +state);
        Integer action;
        Random rand=new Random();
        if(rand.nextDouble()<this.epsilon){
          //exploration
          Random r=new Random();
          //exploitation
          action=r.nextInt(7);
        }
        else{
          action=ql.getAction(state);
        }
        switch(action){
          case 0:
            reward=headForRefuge(time);
            break;
          case 1:
            reward=fillWater(time);
            break;
          case 2:
            reward=extinguish(time,myTarg);
            break;
          case 3:
            reward=goToFire(time,fbTarg,myTarg,changed);
            break;
          case 4:
            reward=doRandomWalk(time,fbTarg,myTarg);
            break;
          case 5:
            reward=goToHelp(time,fbTarg,myTarg);
          case 6:
            reward=rHP(time);
        }
        if(this.learning || this.continuelearning){
          ql.update(state,action,reward);
        }
        stats(time);
    }
    public void stats(int time) {
        FileWriter buffer1 =null;
        try {
          buffer1 = new FileWriter("/home/asma/Documents/rcrs-server/modules/sample/src/sample/CSV/StatsFBHelping"+super.getID()+".txt",true);
        } catch (IOException e1) {
          e1.printStackTrace();
        }
        try {
            buffer1.append(time+" "+this.cpHelping);
            buffer1.append("\n");
            buffer1.flush();
            buffer1.close();
        } catch (IOException e) {
          e.printStackTrace();
        }

            FileWriter buffer2 =null;
            try {
              buffer2 = new FileWriter("/home/asma/Documents/rcrs-server/modules/sample/src/sample/CSV/StatsFBBuilding"+super.getID()+".txt",true);
            } catch (IOException e1) {
              e1.printStackTrace();
            }
            try {
                buffer2.append(time+" "+this.cpBuilding);
                buffer2.append("\n");
                buffer2.flush();
                buffer2.close();
            } catch (IOException e) {
              e.printStackTrace();
            }
    }


    public double rHP(int time){
        if(me().getHP()>3000){
          return -1.0;
        }
        List<EntityID> path = search.breadthFirstSearch(me().getPosition(), refugeIDs);
        if (path != null) {
            LOG.info("Moving to refuge");
            sendMove(time, path);
            return 1.0;
        }
        return 0.0;
    }
    public EntityID someoneCleaning(Collection<Command> heard){
      String idMe=super.getID().toString();

      for (Command next : heard) {
          String idSender = next.getAgentID().toString();

          if(!idMe.equals(idSender)){
              String message = byteToString(((AKSpeak) next).getContent());
              if(message.length() > 0){
                String[] messages = message.split(" ",2);
                String action = messages[0]; //Extinguishing

                if(action.equals("Extinguishing")){
                    String target = messages[1]; //urn:rescuecore2.standard:entity:blockade (2100749390)
                    Collection<StandardEntity> e = model.getEntitiesOfType(StandardEntityURN.BUILDING);
                    List<EntityID> buildings=new ArrayList<EntityID>();
                    for (StandardEntity n : e) {
                        Building b = (Building)n;
                        if(target.equals(b.getID().toString())){
                          if(( (""+b.getFierynessEnum()).equals("BURNING" ))  && b.getFieryness()!=8){
                            //System.out.println(super.getID()+" Helping "+b.getFierynessEnum());

                            return b.getID();

                          }
                        }
                    }
                  }
              }
            }
      }
      return null;
    }

    private double doRandomWalk(int time,EntityID target, EntityID myTarg){
      if (myTarg!=null) {
          return -1.0;
      }
      // asking for help
      if(target!=null){
        return -0.2;
      }

      List<EntityID> path = search.breadthFirstSearch(me().getPosition(), unexploredBuildings);
      if (path != null) {
          LOG.info("Searching buildings");
          sendMove(time, path);
          return 0.0;
      }
      path = randomWalkR();
      LOG.info("Moving randomly");
      sendMove(time, path);

      return 0.0;
    }

    public String getState(EntityID fbTarg, EntityID myTarg, ChangeSet changed){
      String state="";
      FireBrigade me = me();
      //enough water?
      if (me.isWaterDefined() & me.getWater() == 0) {
        state="0";
      }
      else{
        if (me.isWaterDefined() && me.getWater() > 0) {
          state="1";
        }
      }
      //are we filling water?
      if (me.isWaterDefined() && me.getWater() <= maxWater && location() instanceof Refuge) {
        state+="1";
      }
      else {
          if (!(location() instanceof Refuge)) {
            state+="0";
          }
      }
      //extinguish?

      boolean ext=false;
      if(myTarg!=null){
        state+="1";
      }else{
        state+="0";
      }
      //go to fire?
      // Find all buildings that are on fire
      HashMap<Building,Integer> a =  getBuildings(changed);
      ext=false;

        for (Map.Entry<Building, Integer> next : a.entrySet()) {
          List<Building> result=new ArrayList<Building>();
          result.add(next.getKey());
          Collection<EntityID> list_keys= objectsToIDs(result);
          EntityID building_key=list_keys.iterator().next();
          Integer temperature = next.getValue();
          List<EntityID> path = planPathToFire(building_key);
          if (path != null) {
              state+="1";
              ext=true;
              break;
          }
       }

      if(!ext){
        state+="0";
      }
      // Heard something?
      //System.out.println("Target Sender "+fbTarg);
      if(fbTarg==null){
        state+="0";
      }else{
        state+="1";
      }
      if(me().getHP()>3000){
        state+="1"; //tout va bien
      }else{
        state+="0"; //je vais pas bien
      }
      return state;
    }

    private double goToHelp(int time,EntityID target, EntityID myTarg){

      FireBrigade me = me();
      if(me().getHP()<=3000){
        List<EntityID> path = search.breadthFirstSearch(me().getPosition(), refugeIDs);
        if (path != null) {
          return -0.2;
        }
      }
        if (me.isWaterDefined() && me.getWater() == 0) {
          return -1.0;
        }
        if(target == null){
          return -1.0;
        }
        if (myTarg!=null) {
            return -1.0;
        }

      // Can we extinguish any right now?
      LOG.info("Extinguishing " + target);
      sendExtinguish(time, target, maxPower);
      sendSpeak(time, 1, ("Extinguishing " + target).getBytes());
      this.cpHelping++;
      return 6.0;
    }

    private void updateUnexploredBuildings(ChangeSet changed) {
        for (EntityID next : changed.getChangedEntities()) {
            unexploredBuildings.remove(next);
        }
    }

    public double fillWater(int time){
      FireBrigade me = me();
      if(me().getHP()<=3000){
        List<EntityID> path = search.breadthFirstSearch(me().getPosition(), refugeIDs);
        if (path != null) {
          return -0.2;
        }
      }
      // Are we currently filling with water?
      if (me.isWaterDefined() && me.getWater() < maxWater && location() instanceof Refuge) {
          LOG.info("Filling with water at " + location());
          sendRest(time);
          return 0.0;
      }
      return -0.5;
    }

    public double goToFire(int time, EntityID fbTarg, EntityID myTarg,ChangeSet changed){
      FireBrigade me = me();
      if(me().getHP()<=3000){
        List<EntityID> path = search.breadthFirstSearch(me().getPosition(), refugeIDs);
        if (path != null) {
          return -0.2;
        }
      }
        if (me.isWaterDefined() && me.getWater() == 0) {
          return -1.0;
        }
        if(fbTarg!=null && planPathToFire(fbTarg).size()!=0){
          return -1.0;
        }
        if (myTarg!=null) {
            return -1.0;
        }
        HashMap<Building,Integer>  a = getBuildings(changed);
          for (Map.Entry<Building, Integer> next : a.entrySet()) {
            try{
                List<Building> result=new ArrayList<Building>();
                result.add(next.getKey());
                Collection<EntityID> list_keys= objectsToIDs(result);
                EntityID building_key=list_keys.iterator().next();
                Integer temperature = next.getValue();
                // System.out.println("A Key = " + building_key +", Value = " + temperature);

                if(next!=null){
                  List<EntityID> path = planPathToFire(building_key);
                  if (path != null) {
                    //System.out.println(super.getID()+" GOTOFIRE Burning "+next.getKey().getFierynessEnum()+ " temperature"+temperature);
                      LOG.info("Moving to target");
                      sendMove(time, path);
                      return 1.0;
                  }
                }
            } catch(Exception e) {
                //System.out.print("Nothing\n");
                continue;
            }
         }
      return -1.0;
    }

    public double extinguish(int time, EntityID myTarg){
      FireBrigade me = me();
      if(me().getHP()<=3000){
        List<EntityID> path = search.breadthFirstSearch(me().getPosition(), refugeIDs);
        if (path != null) {
          return -0.2;
        }
      }
        if (me.isWaterDefined() && me.getWater() == 0) {
          return -1.0;
        }
        if(myTarg==null){
          return -1.0;
        }
      // Can we extinguish any right now?
      LOG.info("Extinguishing " + myTarg);
      sendSpeak(time, 1, ("Extinguishing " + myTarg).getBytes());
      sendExtinguish(time, myTarg, maxPower);
      this.cpBuilding++;
      //System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
      //System.out.println(super.getID()+" my target "+model.getEntity(myTarg).toString()+" temperature "+((Building)model.getEntity(myTarg)).getTemperature());
        //System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
      return 3.0;
    }

    private String byteToString(byte[] msg) {
        try {
            return new String(msg, "ISO-8859-1");
        } catch (UnsupportedEncodingException ex) {
            return "";
        }
    }

    public double headForRefuge(int time){
      FireBrigade me = me();
      if(me().getHP()<=3000){
        List<EntityID> path = search.breadthFirstSearch(me().getPosition(), refugeIDs);
        if (path != null) {
          return -0.2;
        }
      }
      // Are we out of water?
      if (me.isWaterDefined() && me.getWater() >0) {
        return -1.0;
      }

      if (me.isWaterDefined() && me.getWater() == 0) {
          // Head for a refuge
          List<EntityID> path = search.breadthFirstSearch(me().getPosition(), refugeIDs);
          if (path != null) {
              LOG.info("Moving to refuge");
              sendMove(time, path);
              return 0.0;
          }
      }
      return 0.0;
    }

    @Override
    protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
        return EnumSet.of(StandardEntityURN.FIRE_BRIGADE);
    }


    private HashMap<Building,Integer> getBuildings(ChangeSet changed)
    {
      Set<EntityID> s= changed.getChangedEntities() ;
      HashMap<Building,Integer> result = new HashMap<Building,Integer>();
      for(EntityID stock : s){
        StandardEntity i = model.getEntity(stock);
        if (i instanceof Building){
          Building b  = (Building) i ;
          boolean t = b.isTemperatureDefined();
          if(t){
              if (b.isOnFire() && b.getFieryness()!=8) {
                result.put(b,b.getTemperature());
              }
          }
        }
       }
       //System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
       HashMap<Building, Integer> hm1 = sortByValue(result);
       return hm1;
    }

    public static HashMap<Building, Integer> sortByValue(HashMap<Building, Integer> hm)
       {
           // Create a list from elements of HashMap
           List<Map.Entry<Building, Integer> > list =
                  new LinkedList<Map.Entry<Building, Integer> >(hm.entrySet());

           // Sort the list
           Collections.sort(list, new Comparator<Map.Entry<Building, Integer> >() {
               public int compare(Map.Entry<Building, Integer> o1,
                                  Map.Entry<Building, Integer> o2)
               {
                   return (o2.getValue()).compareTo(o1.getValue());
               }
           });

           // put data from sorted list to hashmap
           HashMap<Building, Integer> temp = new LinkedHashMap<Building, Integer>();
           for (Map.Entry<Building, Integer> aa : list) {
               temp.put(aa.getKey(), aa.getValue());
           }
           return temp;
       }
       private List<EntityID> planPathToFire(EntityID target) {
           // Try to get to anything within maxDistance of the target
           Collection<StandardEntity> targets = model.getObjectsInRange(target, maxDistance);
           if (targets.isEmpty()) {
               return null;
           }
           return search.breadthFirstSearch(me().getPosition(), objectsToIDs(targets));
       }
}
