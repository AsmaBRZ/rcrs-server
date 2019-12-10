package sample;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import rescuecore2.misc.Pair;
import rescuecore2.worldmodel.EntityID;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.messages.Command;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.worldmodel.Property;
import rescuecore2.worldmodel.properties.IntProperty;
import rescuecore2.worldmodel.properties.IntArrayProperty;
import rescuecore2.worldmodel.properties.EntityRefProperty;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.standard.entities.Area;

import org.apache.log4j.Logger;

import sample.QlearningPF;

/**
   A sample police force agent.
 */
public class SamplePoliceForce extends AbstractSampleAgent<PoliceForce> {
    private static final Logger LOG = Logger.getLogger(SamplePoliceForce.class);
    private static final String DISTANCE_KEY = "clear.repair.distance";
    private QlearningPF ql;
    private double epsilon = 0.2;
    private boolean learning=false;
    private boolean continuelearning=true;
    private int distance;
    private int cp=0;
    private Blockade oldTargert=null;
    private int bcp=0;
    @Override
    public String toString() {
        return "Sample police force";
    }

    @Override
    protected void postConnect() {
        super.postConnect();
        model.indexClass(StandardEntityURN.ROAD);
        distance = config.getIntValue(DISTANCE_KEY);
        ql=new QlearningPF(super.getID().toString(),4,0.3,0.9,0.4,this.learning,this.continuelearning) ; //Qlearning(int nb_actions), Double alpha,Double beta,Double gamma,Double epsilon)
    }

    @Override
    protected void think(int time, ChangeSet changed, Collection<Command> heard) {
       //Map<EntityID, Set<EntityID>> nei=super.getNeighbours();
       //nei.forEach((k,v)->System.out.println("Key : " + k.toString() + " Value : " + v.toString()) );
      //System.out.println("Changeset: "+changed);
      double reward =0.0;
        if (time == config.getIntValue(kernel.KernelConstants.IGNORE_AGENT_COMMANDS_KEY)) {
            // Subscribe to channel 1
            sendSubscribe(time, 1);
        }
        for (Command next : heard) {
          //System.out.println("command "+model.getEntity(next.getAgentID())+ " ee "+ super.getID());
            LOG.debug("Heard " + next);
        }
        String state = getState();
        Integer action;

        Random rand=new Random();
        if(rand.nextDouble()<this.epsilon){
          Random r=new Random();
          //exploitation
          action=r.nextInt(4);
        }
        else{
          action=ql.getAction(state);
        }
        switch(action){
          case 0:
            Blockade target = getTargetBlockade();
            reward=clearBlockade(target,time);
            break;
          case 1:
            List<EntityID> path = search.breadthFirstSearch(me().getPosition(), getBlockedRoads());
           reward=goToBlockedArea(path,time);
            break;
          case 2:
            reward =doRandomWalk(time);
            break;
            case 3:
            reward=rHP(time);
            break;
        }
        if(this.learning || this.continuelearning){
          ql.update(state,action,reward,time);
        }
        stats(time);
    }

    public void stats(int time) {
        FileWriter buffer =null;
        try {
          buffer = new FileWriter("/home/asma/Documents/rcrs-server/modules/sample/src/sample/CSV/Statspoliceforce"+super.getID()+".txt",true);
        } catch (IOException e1) {
          e1.printStackTrace();
        }
        try {
            buffer.append(time+" "+this.cp);
            buffer.append("\n");
            buffer.flush();
            buffer.close();
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
    private String getState(){
      String state="";
      // Am I near a blockade?
      Blockade target = getTargetBlockade();

      if (target != null) {
          state="1";
      }
      else{
        state="0";
      }
      // Plan a path to a blocked area
      List<EntityID> path = search.breadthFirstSearch(me().getPosition(), getBlockedRoads());
      if (path != null) {
        state+="1";
      }
      else{
        state+="0";
      }
      if(me().getHP()>3000){
        state+="1"; //tout va bien
      }else{
        state+="0"; //je vais pas bien
      }
      //System.out.println(super.getID()+ " my state :"+state);
      return state;
    }

    private double doRandomWalk(int time){
      if(me().getHP()<=3000){
        List<EntityID> path = search.breadthFirstSearch(me().getPosition(), refugeIDs);
        if (path != null) {
          return -0.2;
        }
      }
      sendMove(time, randomWalkR());
      int nbBlockedRoads=  getBlockedRoads().size();
      if(nbBlockedRoads==0){
        return 0;
      }else{
        return -0.5;
      }
    }

    private double goToBlockedArea(List<EntityID> path, int time){
      if(me().getHP()<=3000){
        List<EntityID> path1 = search.breadthFirstSearch(me().getPosition(), refugeIDs);
        if (path1 != null) {
          return -0.2;
        }
      }
      if(path==null){
        return -1.0;
      }
      LOG.info("Moving to target");
      Road r = (Road)model.getEntity(path.get(path.size() - 1));
      Blockade b = getTargetBlockade(r, -1);
      if(b==null){
        return 0;
      }
      sendMove(time, path, b.getX(), b.getY());
      LOG.debug("Path: " + path);
      LOG.debug("Target coordinates: " + b.getX() + ", " + b.getY());
      return 0.1;
    }

    private double clearBlockade(Blockade target,int time){
      if(me().getHP()<=3000){
        List<EntityID> path = search.breadthFirstSearch(me().getPosition(), refugeIDs);
        if (path != null) {
          return -0.2;
        }
      }
      if(target == null){
        return -1.0;
      }
      if(target==this.oldTargert){
        //System.out.println(super.getID()+" Target"+target+" cp "+this.bcp);
        this.bcp++;
      }
      if(this.bcp>10){
        this.bcp=0;
        randomWalkR();
          //System.out.println(super.getID()+" RW "+target+" cp "+this.bcp);
        this.oldTargert=null;
        return 0.0;
      }
      LOG.info("Clearing blockade " + target);
      sendSpeak(time, 1, ("Clearing " + target).getBytes());
      List<Line2D> lines = GeometryTools2D.pointsToLines(GeometryTools2D.vertexArrayToPoints(target.getApexes()), true);
      double best = Double.MAX_VALUE;
      Point2D bestPoint = null;
      Point2D origin = new Point2D(me().getX(), me().getY());
      for (Line2D next : lines) {
          Point2D closest = GeometryTools2D.getClosestPointOnSegment(next, origin);
          double d = GeometryTools2D.getDistance(origin, closest);
          if (d < best) {
              best = d;
              bestPoint = closest;
          }
      }
      Vector2D v = bestPoint.minus(new Point2D(me().getX(), me().getY()));
      v = v.normalised().scale(1000000);
      sendClear(time, (int)(me().getX() + v.getX()), (int)(me().getY() + v.getY()));
      this.cp++;
      this.oldTargert=target;
      return 3.0;
    }

    @Override
    protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
        return EnumSet.of(StandardEntityURN.POLICE_FORCE);
    }

    private List<EntityID> getBlockedRoads() {

        Collection<StandardEntity> e = model.getEntitiesOfType(StandardEntityURN.ROAD);
        List<EntityID> result = new ArrayList<EntityID>();

        for (StandardEntity next : e) {
            Road r = (Road)next;
            if (r.isBlockadesDefined() && !r.getBlockades().isEmpty()) {
                result.add(r.getID());
            }
        }
        return result;
    }

    private List<EntityID> getPoliceForce(Collection<Command> heard) {
      Collection<StandardEntity> e = model.getEntitiesOfType(StandardEntityURN.POLICE_FORCE);
      List<EntityID> policiers=new ArrayList<EntityID>();
      for (StandardEntity next : e) {
          PoliceForce pf = (PoliceForce)next;
          policiers.add(pf.getID());
      }
      List<EntityID> result = new ArrayList<EntityID>();
      for (Command next : heard) {
        if(next.getAgentID()!=super.getID() && policiers.contains(next.getAgentID())){
            //System.out.println("gardé command "+model.getEntity(next.getAgentID())+ " moi "+ super.getID());
        }
    }
    return result;
  }

    private Map<Civilian, Property > getDamagedCivilians() {

        Collection<StandardEntity> e = model.getEntitiesOfType(StandardEntityURN.CIVILIAN);
        Map<Civilian, Property > result = new HashMap<Civilian, Property >();

        for (StandardEntity next : e) {
            Civilian civilian = (Civilian)next;
            Property  damage =((Human)civilian).getProperty("DAMAGE");
            result.put(civilian,damage);
        }
        //System.out.println(" Civilian damageds " + result);
        return result;
    }

    private Blockade getTargetBlockade() {
        LOG.debug("Looking for target blockade");
        Area location = (Area)location();
        LOG.debug("Looking in current location");
        Blockade result = getTargetBlockade(location, distance);
        if (result != null) {
            return result;
        }
        LOG.debug("Looking in neighbouring locations");
        for (EntityID next : location.getNeighbours()) {
            location = (Area)model.getEntity(next);
            result = getTargetBlockade(location, distance);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private Blockade getTargetBlockade(Area area, int maxDistance) {
        //        Logger.debug("Looking for nearest blockade in " + area);
        if (area == null || !area.isBlockadesDefined()) {
            //            Logger.debug("Blockades undefined");
            return null;
        }
        List<EntityID> ids = area.getBlockades();
        // Find the first blockade that is in range.
        int x = me().getX();
        int y = me().getY();
        for (EntityID next : ids) {
            Blockade b = (Blockade)model.getEntity(next);
            if(b!=null){
              double d = findDistanceTo(b, x, y);
              d+=2;
              //            Logger.debug("Distance to " + b + " = " + d);
              if (maxDistance < 0 || d < maxDistance) {
                  //                Logger.debug("In range");
                  return b;
              }
            }
        }
        //        Logger.debug("No blockades in range");
        return null;
    }

    private int findDistanceTo(Blockade b, int x, int y) {
        //        Logger.debug("Finding distance to " + b + " from " + x + ", " + y);
        List<Line2D> lines = GeometryTools2D.pointsToLines(GeometryTools2D.vertexArrayToPoints(b.getApexes()), true);
        double best = Double.MAX_VALUE;
        Point2D origin = new Point2D(x, y);
        for (Line2D next : lines) {
            Point2D closest = GeometryTools2D.getClosestPointOnSegment(next, origin);
            double d = GeometryTools2D.getDistance(origin, closest);

            //            Logger.debug("Next line: " + next + ", closest point: " + closest + ", distance: " + d);
            if (d < best) {
                best = d;
                //                Logger.debug("New best distance");
            }

        }
        return (int)best;
    }

}
