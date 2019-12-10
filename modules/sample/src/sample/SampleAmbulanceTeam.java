package sample;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Random;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import rescuecore2.worldmodel.EntityID;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.messages.Command;

import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.Refuge;

import org.apache.log4j.Logger;

/**
   A sample ambulance team agent.
 */
public class SampleAmbulanceTeam extends AbstractSampleAgent<AmbulanceTeam> {
    private static final Logger LOG = Logger.getLogger(SampleAmbulanceTeam.class);
    private Collection<EntityID> unexploredBuildings;
    private QlearningTA ql;
    private double epsilon = 0.2;
    private boolean learning=true;
    private boolean continuelearning=false;
    private int cp = 0;
    @Override
    public String toString() {
        return "Sample ambulance team";
    }

    @Override
    protected void postConnect() {
        super.postConnect();
        model.indexClass(StandardEntityURN.CIVILIAN, StandardEntityURN.FIRE_BRIGADE, StandardEntityURN.POLICE_FORCE, StandardEntityURN.AMBULANCE_TEAM, StandardEntityURN.REFUGE,StandardEntityURN.HYDRANT,StandardEntityURN.GAS_STATION, StandardEntityURN.BUILDING);
        unexploredBuildings = new HashSet<EntityID>(buildingIDs);
        ql=new QlearningTA(super.getID().toString(),6,0.3,0.9,0.4,this.learning,this.continuelearning) ; //Qlearning(int nb_actions), Double alpha,Double beta,Double gamma,Double epsilon)
    }

    public void stats(int time) {
        FileWriter buffer1 =null;
        try {
          buffer1 = new FileWriter("/home/asma/Documents/rcrs-server/modules/sample/src/sample/CSV/StatsTA"+super.getID()+".txt",true);
        } catch (IOException e1) {
          e1.printStackTrace();
        }
        try {
            buffer1.append(time+" "+this.cp);
            buffer1.append("\n");
            buffer1.flush();
            buffer1.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
  }

    @Override
    protected void think(int time, ChangeSet changed, Collection<Command> heard) {
        if (time == config.getIntValue(kernel.KernelConstants.IGNORE_AGENT_COMMANDS_KEY)) {
            // Subscribe to channel 1
            sendSubscribe(time, 1);
        }
        for (Command next : heard) {
            LOG.debug("Heard " + next);
        }
        updateUnexploredBuildings(changed);
        double reward =0.0;
        String state = getState();
        Integer action;

        Random rand=new Random();
        if(rand.nextDouble()<this.epsilon){
          //exploration
          Random r=new Random();
          //exploitation
          action=r.nextInt(6);
        }
        else{
          action=ql.getAction(state);
        }

        switch(action){
          case 0:
            reward=unload(time);
            break;
          case 1:
           reward=goToRefuge(time);
            break;
          case 2:
            reward =goNTarget(time);
            break;
          case 3:
            reward =goFTarget(time);
            break;
          case 4:
            reward =doRandomWalk(time);
            break;
          case 5:
            reward=rHP(time);
            break;
        }
        if(this.learning || this.continuelearning){
          ql.update(state,action,reward);
        }
        //stats(time);
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

    private String getState(){
      String state="";
      if (someoneOnBoard()) {
        state+="1";
      }else{
        state+="0";
      }
      if (location() instanceof Refuge) {
        state+="1";
      }else{
        state+="0";
      }
      boolean target=false;
      for (Human next : getTargets()) {
          if (next.getPosition().equals(location().getID())) {
              target=true;
          }
      }
      if(target){
        state+="1";
      }else{
        state+="0";
      }
      target=false;
      for (Human next : getTargets()) {
          if (!(next.getPosition().equals(location().getID()))) {
              target=true;
          }
      }
      if(target){
        state+="1";
      }else{
        state+="0";
      }
      if(me().getHP()>3000){
        state+="1"; //tout va bien
      }else{
        state+="0"; //je vais pas bien
      }
      return state;
    }

    private double unload(int time){
      if(me().getHP()<=3000){
        List<EntityID> path = search.breadthFirstSearch(me().getPosition(), refugeIDs);
        if (path != null) {
          return -0.2;
        }
      }
      // Am I transporting a civilian to a refuge?
      if (!someoneOnBoard()) {
        return -1.0;
      }
      // Am I at a refuge?
      if (! (location() instanceof Refuge)) {
        return -1.0;
      }
          // Unload!
          LOG.info("Unloading");
          sendUnload(time);

          return 3.0;
      }

    private double goToRefuge(int time){
      if(me().getHP()<=3000){
        List<EntityID> path = search.breadthFirstSearch(me().getPosition(), refugeIDs);
        if (path != null) {
          return -0.2;
        }
      }
      if(!someoneOnBoard() || (location() instanceof Refuge)){
        return -1.0;
      }
      // Move to a refuge
      List<EntityID> path = search.breadthFirstSearch(me().getPosition(), refugeIDs);
      if (path != null) {
          LOG.info("Moving to refuge");
          sendMove(time, path);
          return 0.5;
      }
      LOG.debug("Failed to plan path to refuge");
      return 0.0;
    }

    private double goNTarget(int time){
      if(me().getHP()<=3000){
        List<EntityID> path = search.breadthFirstSearch(me().getPosition(), refugeIDs);
        if (path != null) {
          return -0.2;
        }
      }
      if(someoneOnBoard()){
        return -1.0;
      }
      // Go through targets (sorted by distance) and check for things we can do
      for (Human next : getTargets()) {
          if (next.getPosition().equals(location().getID())) {
              // Targets in the same place might need rescueing or loading
              if ((next instanceof Civilian) && next.getBuriedness() == 0 && !(location() instanceof Refuge)) {
                  // Load
                  LOG.info("Loading " + next);
                  sendLoad(time, next.getID());
                  this.cp++;
                  return 0.3;
              }
              if (next.getBuriedness() > 0) {
                  // Rescue
                  LOG.info("Rescueing " + next);
                  sendRescue(time, next.getID());
                  this.cp++;
                  return 0.3;
              }
          }
      }
      return -1.0;
    }

    private double goFTarget(int time){
      if(me().getHP()<=3000){
        List<EntityID> path = search.breadthFirstSearch(me().getPosition(), refugeIDs);
        if (path != null) {
          return -0.2;
        }
      }
      if(someoneOnBoard()){
        return -1.0;
      }
      // Go through targets (sorted by distance) and check for things we can do
      for (Human next : getTargets()) {
          if (!(next.getPosition().equals(location().getID()))) {
              // Try to move to the target
              List<EntityID> path = search.breadthFirstSearch(me().getPosition(), next.getPosition());
              if (path != null) {
                  LOG.info("Moving to target");
                  sendMove(time, path);
                  return 0.1;
              }
          }
      }
      return -1.0;
    }

    private double doRandomWalk(int time){
      if(me().getHP()<=3000){
        List<EntityID> path = search.breadthFirstSearch(me().getPosition(), refugeIDs);
        if (path != null) {
          return -0.2;
        }
      }
      List<EntityID> path = search.breadthFirstSearch(me().getPosition(), unexploredBuildings);
      if (path != null) {
          LOG.info("Searching buildings");
          sendMove(time, path);
          return 0.0;
      }
      LOG.info("Moving randomly");
      sendMove(time, randomWalk());
      return 0.0;
    }

    @Override
    protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
        return EnumSet.of(StandardEntityURN.AMBULANCE_TEAM);
    }

    private boolean someoneOnBoard() {
        for (StandardEntity next : model.getEntitiesOfType(StandardEntityURN.CIVILIAN)) {
            if (((Human)next).getPosition().equals(getID())) {
                LOG.debug(next + " is on board");
                return true;
            }
        }
        return false;
    }

    private List<Human> getTargets() {
        List<Human> targets = new ArrayList<Human>();
        for (StandardEntity next : model.getEntitiesOfType(StandardEntityURN.CIVILIAN, StandardEntityURN.FIRE_BRIGADE, StandardEntityURN.POLICE_FORCE, StandardEntityURN.AMBULANCE_TEAM)) {
            Human h = (Human)next;
            if (h == me()) {
                continue;
            }
            if (h.isHPDefined()
                && h.isBuriednessDefined()
                && h.isDamageDefined()
                && h.isPositionDefined()
                && h.getHP() > 0
                && (h.getBuriedness() > 0 || h.getDamage() > 0)) {
                targets.add(h);
            }
        }
        Collections.sort(targets, new DistanceSorter(location(), model));
        return targets;
    }

    private void updateUnexploredBuildings(ChangeSet changed) {
        for (EntityID next : changed.getChangedEntities()) {
            unexploredBuildings.remove(next);
        }
    }
}
